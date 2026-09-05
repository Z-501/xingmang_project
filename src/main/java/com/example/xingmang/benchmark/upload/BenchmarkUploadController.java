package com.example.xingmang.benchmark.upload;

import com.example.xingmang.config.MinioProperties;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.model.vo.Result;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/benchmark/upload")
public class BenchmarkUploadController {

    private static final String BENCHMARK_PREFIX = "benchmark/";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final UploadBenchmarkMetrics metrics;

    public BenchmarkUploadController(MinioClient minioClient,
                                     MinioProperties minioProperties,
                                     UploadBenchmarkMetrics metrics) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.metrics = metrics;
    }

    @PutMapping("/relay")
    public Result<Map<String, Object>> relayUpload(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength <= 0L) {
            throw new ConditionException(400, "Content-Length 必须大于 0");
        }

        long startNs = System.nanoTime();
        String objectName = "benchmark/relay/" + UUID.randomUUID() + ".bin";

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(request.getInputStream(), contentLength, -1)
                            .contentType(getContentType(request))
                            .build()
            );

            metrics.addAppFileTraffic(contentLength, contentLength);

            Map<String, Object> result = new HashMap<>();
            result.put("mode", "relay");
            result.put("objectName", objectName);
            result.put("fileSizeBytes", contentLength);
            result.put("serverElapsedMs", nsToMs(System.nanoTime() - startNs));
            return Result.success(result);
        } catch (Exception e) {
            cleanupPartialObject(objectName);
            throw new ConditionException(500, "Benchmark Relay 上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/direct-url")
    public Result<Map<String, Object>> directUrl() {
        String objectName = "benchmark/direct/" + UUID.randomUUID() + ".bin";

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(minioProperties.getUploadUrlExpireSeconds())
                            .build()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("mode", "direct");
            result.put("objectName", objectName);
            result.put("uploadUrl", uploadUrl);
            result.put("expireSeconds", minioProperties.getUploadUrlExpireSeconds());
            return Result.success(result);
        } catch (Exception e) {
            throw new ConditionException(500, "生成 Benchmark 直传预签名 URL 失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/object")
    public Result<Map<String, Object>> deleteObject(@RequestParam("objectName") String objectName) {
        validateBenchmarkObjectName(objectName);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );

            Map<String, Object> result = new HashMap<>();
            result.put("objectName", objectName);
            result.put("deleted", true);
            return Result.success(result);
        } catch (Exception e) {
            throw new ConditionException(500, "删除 Benchmark 对象失败: " + e.getMessage());
        }
    }

    @PostMapping("/metrics/start")
    public Result<Map<String, Object>> metricsStart() {
        return Result.success(metrics.start());
    }

    @PostMapping("/metrics/stop")
    public Result<Map<String, Object>> metricsStop(@RequestBody Map<String, String> body) {
        String sessionId = body == null ? null : body.get("sessionId");
        if (!StringUtils.hasText(sessionId)) {
            throw new ConditionException(400, "sessionId 不能为空");
        }
        return Result.success(metrics.stop(sessionId));
    }

    private String getContentType(HttpServletRequest request) {
        String contentType = request.getContentType();
        return StringUtils.hasText(contentType) ? contentType : DEFAULT_CONTENT_TYPE;
    }

    private void validateBenchmarkObjectName(String objectName) {
        if (!StringUtils.hasText(objectName) || !objectName.startsWith(BENCHMARK_PREFIX)) {
            throw new ConditionException(403, "只允许删除 benchmark/ 下的对象");
        }
    }

    private void cleanupPartialObject(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception ignored) {
            // 清理失败不能掩盖原始上传异常。
        }
    }

    private double nsToMs(long ns) {
        return ns / 1_000_000.0;
    }
}
