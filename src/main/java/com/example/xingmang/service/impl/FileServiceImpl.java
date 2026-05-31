package com.example.xingmang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.xingmang.config.MinioProperties;
import com.example.xingmang.mapper.FileMapper;
import com.example.xingmang.model.dto.FileCompleteUploadDTO;
import com.example.xingmang.model.dto.FileUploadRequestDTO;
import com.example.xingmang.model.entity.FileEntity;
import com.example.xingmang.model.vo.FileCheckVO;
import com.example.xingmang.model.vo.FileUploadVO;
import com.example.xingmang.service.FileService;
import com.example.xingmang.util.UserContext;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.model.dto.MultipartUploadCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadInitDTO;
import com.example.xingmang.model.dto.MultipartUploadPartCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadUrlDTO;
import com.example.xingmang.model.vo.MultipartUploadCheckVO;
import com.example.xingmang.model.vo.MultipartUploadInitVO;
import com.example.xingmang.model.vo.MultipartUploadUrlVO;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.example.xingmang.model.entity.FileEntity;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;


@Service
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final FileMapper fileMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private static final String MULTIPART_META_KEY_PREFIX = "file:multipart:meta:";
    private static final String MULTIPART_PARTS_KEY_PREFIX = "file:multipart:parts:";
    private static final long MULTIPART_EXPIRE_HOURS = 24L;

    // 初始化构造器
    public FileServiceImpl(MinioClient minioClient,
                               MinioProperties minioProperties,
                               FileMapper fileMapper,
                               StringRedisTemplate stringRedisTemplate) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.fileMapper = fileMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }
    // 秒传判断：根据文件的MD5值判断是否存在相同的文件
    @Override
    public FileCheckVO checkFile(String fileMd5) {
        // 空值校验，如果MD5为空，直接返回VO对象，flase表示文件不存在
        if (!StringUtils.hasText(fileMd5)) {
            return FileCheckVO.builder()
                    .existed(false)
                    .build();
        }
        // 通过 fileMd5 在数据库中查找文件
        FileEntity fileEntity = fileMapper.selectOne(
                new LambdaQueryWrapper<FileEntity>()
                        // 根据文件的 MD5 值查找
                        .eq(FileEntity::getFileMd5, fileMd5)
                        // 只查找 上传状态为 1 的文件，这个状态值意味着文件已经成功上传
                        .eq(FileEntity::getUploadStatus, 1)
                        // 在查询结果中只取 第一条，防止查到多条数据（如果有重复文件）
                        .last("limit 1")
        );
        // 检查查询结果 fileEntity 是否为 null
        if (fileEntity == null) {
            return FileCheckVO.builder()
                    .existed(false)
                    .build();
        }
        // 如果查询到文件，返回文件的相关信息。
        return FileCheckVO.builder()
                .existed(true)
                .fileId(fileEntity.getId())
                .objectName(fileEntity.getObjectName())
                .build();
    }

    @Override
    public FileUploadVO generateUploadUrl(FileUploadRequestDTO dto) {
        // 调用秒传，判断文件是否存在，也就是是否重复
        FileCheckVO checkVO = checkFile(dto.getFileMd5());
        if (Boolean.TRUE.equals(checkVO.getExisted())) {
            return FileUploadVO.builder()
                    .existed(true)
                    .fileId(checkVO.getFileId())
                    .objectName(checkVO.getObjectName())
                    .expireSeconds(minioProperties.getUploadUrlExpireSeconds())
                    .build();
        }
        // 文件不存在生成URL
        Long userId = UserContext.getCurrentUserId();
        String objectName = buildObjectName(userId, dto.getFileName());

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(minioProperties.getUploadUrlExpireSeconds())
                            .build()
            );
            // 返回前端信息
            return FileUploadVO.builder()
                    .existed(false)
                    .objectName(objectName)
                    .uploadUrl(uploadUrl)
                    .expireSeconds(minioProperties.getUploadUrlExpireSeconds())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("生成上传预签名URL失败", e);
        }
    }

    /**
     *  处理文件上传完成后的操作。它首先检查文件是否已经上传过（通过 MD5 判断）
     *  如果文件已经上传过，就返回已存在文件的 ID；如果文件没有上传过
     *  它会创建一个新的文件记录并将文件的相关信息插入到数据库中，然后返回新文件的 ID
     * @param dto
     * @return
     */
    @Override
    public Long completeUpload(FileCompleteUploadDTO dto) {
        Long userId = UserContext.getCurrentUserId();

        FileEntity existedFile = null;
        if (StringUtils.hasText(dto.getFileMd5())) {
            existedFile = fileMapper.selectOne(
                    new LambdaQueryWrapper<FileEntity>()
                            .eq(FileEntity::getFileMd5, dto.getFileMd5())
                            .eq(FileEntity::getUploadStatus, 1)
                            .last("limit 1")
            );
        }

        if (existedFile != null) {
            return existedFile.getId();
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setUserId(userId);
        fileEntity.setBucketName(minioProperties.getBucketName());
        fileEntity.setObjectName(dto.getObjectName());
        fileEntity.setOriginalFileName(dto.getOriginalFileName());
        fileEntity.setFileType(dto.getFileType());
        fileEntity.setFileSize(dto.getFileSize());
        fileEntity.setFileMd5(dto.getFileMd5());
        fileEntity.setUploadStatus(1);

        fileMapper.insert(fileEntity);
        return fileEntity.getId();
    }

    @Override
    public String getViewUrl(Long fileId) {
        FileEntity fileEntity = fileMapper.selectById(fileId);
        if (fileEntity == null) {
            throw new RuntimeException("文件不存在");
        }

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(fileEntity.getBucketName())
                            .object(fileEntity.getObjectName())
                            .expiry(minioProperties.getViewUrlExpireSeconds())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("生成访问预签名URL失败", e);
        }
    }

    @Override
    public Map<Long, String> getViewUrlMap(Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<FileEntity> fileEntityList = fileMapper.selectBatchIds(fileIds);
        if (fileEntityList == null || fileEntityList.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> urlMap = new HashMap<>();

        for (FileEntity fileEntity : fileEntityList) {
            if (fileEntity == null || fileEntity.getId() == null) {
                continue;
            }
            try {
                String viewUrl = minioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(fileEntity.getBucketName())
                                .object(fileEntity.getObjectName())
                                .expiry(minioProperties.getViewUrlExpireSeconds())
                                .build()
                );
                urlMap.put(fileEntity.getId(), viewUrl);
            } catch (Exception e) {
                throw new RuntimeException("批量生成访问预签名URL失败", e);
            }
        }

        return urlMap;
    }

    @Override
    public InputStream openFileStream(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new ConditionException(400, "fileId 不合法");
        }

        FileEntity fileEntity = fileMapper.selectById(fileId);
        if (fileEntity == null) {
            throw new ConditionException("文件不存在");
        }

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(fileEntity.getBucketName())
                            .object(fileEntity.getObjectName())
                            .build()
            );
        } catch (Exception e) {
            throw new ConditionException("获取文件流失败：" + e.getMessage());
        }
    }

    @Override
    public Long saveGeneratedFile(byte[] bytes,
                                  String objectNamePrefix,
                                  String originalFileName,
                                  String contentType,
                                  Long userId) {
        if (bytes == null || bytes.length == 0) {
            throw new ConditionException(400, "生成文件内容不能为空");
        }

        try {
            String safePrefix = (objectNamePrefix == null || objectNamePrefix.isBlank())
                    ? "generated"
                    : objectNamePrefix;

            String objectName = safePrefix + "/" + UUID.randomUUID() + "-" + originalFileName;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );

            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserId(userId);
            fileEntity.setBucketName(minioProperties.getBucketName());
            fileEntity.setObjectName(objectName);
            fileEntity.setOriginalFileName(originalFileName);
            fileEntity.setFileType(contentType);
            fileEntity.setFileSize((long) bytes.length);
            fileEntity.setFileMd5(DigestUtils.md5DigestAsHex(bytes));
            fileEntity.setUploadStatus(1);

            fileMapper.insert(fileEntity);
            return fileEntity.getId();
        } catch (Exception e) {
            throw new ConditionException("保存生成文件失败：" + e.getMessage());
        }
    }

    /**
     * 构造 MinIO 对象名
     * 示例：video/10001/uuid.mp4
     */
    private String buildObjectName(Long userId, String fileName) {
        String suffix = getFileSuffix(fileName);
        return "video/" + userId + "/" + UUID.randomUUID() + suffix;
    }

    /**
     * 提取文件后缀
     */
    private String getFileSuffix(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    @Override
    public MultipartUploadInitVO initMultipartUpload(MultipartUploadInitDTO dto) {
        if (dto.getTotalChunks() == null || dto.getTotalChunks() < 1) {
            throw new ConditionException("总分片数不能为空且必须大于0");
        }
        if (!StringUtils.hasText(dto.getFileName())) {
            throw new ConditionException("文件名不能为空");
        }
        if (dto.getFileSize() == null || dto.getFileSize() <= 0) {
            throw new ConditionException("文件大小不合法");
        }

        // 先做秒传判断
        FileCheckVO checkVO = checkFile(dto.getFileMd5());
        if (Boolean.TRUE.equals(checkVO.getExisted())) {
            return MultipartUploadInitVO.builder()
                    .existed(true)
                    .fileId(checkVO.getFileId())
                    .objectName(checkVO.getObjectName())
                    .totalChunks(dto.getTotalChunks())
                    .build();
        }

        Long userId = UserContext.getCurrentUserId();
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String objectName = buildObjectName(userId, dto.getFileName());

        String metaKey = buildMultipartMetaKey(uploadId);
        String partsKey = buildMultipartPartsKey(uploadId);

        stringRedisTemplate.opsForHash().put(metaKey, "userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().put(metaKey, "bucketName", minioProperties.getBucketName());
        stringRedisTemplate.opsForHash().put(metaKey, "objectName", objectName);
        stringRedisTemplate.opsForHash().put(metaKey, "originalFileName", dto.getFileName());
        stringRedisTemplate.opsForHash().put(metaKey, "fileMd5", dto.getFileMd5() == null ? "" : dto.getFileMd5());
        stringRedisTemplate.opsForHash().put(metaKey, "fileSize", String.valueOf(dto.getFileSize()));
        stringRedisTemplate.opsForHash().put(metaKey, "contentType", dto.getContentType() == null ? "" : dto.getContentType());
        stringRedisTemplate.opsForHash().put(metaKey, "totalChunks", String.valueOf(dto.getTotalChunks()));

        stringRedisTemplate.expire(metaKey, MULTIPART_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.expire(partsKey, MULTIPART_EXPIRE_HOURS, TimeUnit.HOURS);

        return MultipartUploadInitVO.builder()
                .existed(false)
                .uploadId(uploadId)
                .objectName(objectName)
                .totalChunks(dto.getTotalChunks())
                .build();
    }

    @Override
    public MultipartUploadCheckVO checkMultipartUpload(String uploadId) {
        String metaKey = buildMultipartMetaKey(uploadId);
        ensureMultipartTaskExists(metaKey);
        validateMultipartOwner(metaKey);

        String objectName = getHashValue(metaKey, "objectName");
        String partsKey = buildMultipartPartsKey(uploadId);

        Set<String> members = stringRedisTemplate.opsForSet().members(partsKey);
        List<Integer> uploadedParts = new ArrayList<>();
        if (members != null) {
            for (String member : members) {
                uploadedParts.add(Integer.parseInt(member));
            }
            uploadedParts.sort(Integer::compareTo);
        }

        return MultipartUploadCheckVO.builder()
                .uploadId(uploadId)
                .objectName(objectName)
                .uploadedParts(uploadedParts)
                .build();
    }

    @Override
    public MultipartUploadUrlVO getMultipartUploadUrl(MultipartUploadUrlDTO dto) {
        if (!StringUtils.hasText(dto.getUploadId())) {
            throw new ConditionException("uploadId不能为空");
        }
        if (dto.getPartNumber() == null || dto.getPartNumber() < 1) {
            throw new ConditionException("分片编号不合法");
        }

        String metaKey = buildMultipartMetaKey(dto.getUploadId());
        ensureMultipartTaskExists(metaKey);
        validateMultipartOwner(metaKey);

        Integer totalChunks = Integer.parseInt(getHashValue(metaKey, "totalChunks"));
        if (dto.getPartNumber() > totalChunks) {
            throw new ConditionException("分片编号超出范围");
        }

        String originalFileName = getHashValue(metaKey, "originalFileName");
        String chunkObjectName = buildChunkObjectName(dto.getUploadId(), dto.getPartNumber(), originalFileName);

        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(chunkObjectName)
                            .expiry(minioProperties.getUploadUrlExpireSeconds())
                            .build()
            );

            return MultipartUploadUrlVO.builder()
                    .partNumber(dto.getPartNumber())
                    .chunkObjectName(chunkObjectName)
                    .uploadUrl(uploadUrl)
                    .expireSeconds(minioProperties.getUploadUrlExpireSeconds())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("生成分片上传预签名URL失败", e);
        }
    }

    @Override
    public void confirmMultipartPart(MultipartUploadPartCompleteDTO dto) {
        if (!StringUtils.hasText(dto.getUploadId())) {
            throw new ConditionException("uploadId不能为空");
        }
        if (dto.getPartNumber() == null || dto.getPartNumber() < 1) {
            throw new ConditionException("分片编号不合法");
        }

        String metaKey = buildMultipartMetaKey(dto.getUploadId());
        ensureMultipartTaskExists(metaKey);
        validateMultipartOwner(metaKey);

        Integer totalChunks = Integer.parseInt(getHashValue(metaKey, "totalChunks"));
        if (dto.getPartNumber() > totalChunks) {
            throw new ConditionException("分片编号超出范围");
        }

        String originalFileName = getHashValue(metaKey, "originalFileName");
        String chunkObjectName = buildChunkObjectName(dto.getUploadId(), dto.getPartNumber(), originalFileName);

        try {
            // 确认分片对象真实存在
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(chunkObjectName)
                            .build()
            );

            String partsKey = buildMultipartPartsKey(dto.getUploadId());
            stringRedisTemplate.opsForSet().add(partsKey, String.valueOf(dto.getPartNumber()));
            stringRedisTemplate.expire(partsKey, MULTIPART_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            throw new RuntimeException("确认分片上传失败，对象不存在或不可访问", e);
        }
    }

    @Override
    public Long completeMultipartUpload(MultipartUploadCompleteDTO dto) {
        if (!StringUtils.hasText(dto.getUploadId())) {
            throw new ConditionException("uploadId不能为空");
        }

        String metaKey = buildMultipartMetaKey(dto.getUploadId());
        String partsKey = buildMultipartPartsKey(dto.getUploadId());

        ensureMultipartTaskExists(metaKey);
        validateMultipartOwner(metaKey);

        String objectName = getHashValue(metaKey, "objectName");
        String originalFileName = getHashValue(metaKey, "originalFileName");
        String fileMd5 = getHashValue(metaKey, "fileMd5");
        Long fileSize = Long.parseLong(getHashValue(metaKey, "fileSize"));
        String contentType = getHashValue(metaKey, "contentType");
        Integer totalChunks = Integer.parseInt(getHashValue(metaKey, "totalChunks"));

        // 再做一次秒传判断，防止重复提交
        if (StringUtils.hasText(fileMd5)) {
            FileEntity existedFile = fileMapper.selectOne(
                    new LambdaQueryWrapper<FileEntity>()
                            .eq(FileEntity::getFileMd5, fileMd5)
                            .eq(FileEntity::getUploadStatus, 1)
                            .last("limit 1")
            );
            if (existedFile != null) {
                cleanupMultipartTask(dto.getUploadId(), totalChunks, originalFileName);
                return existedFile.getId();
            }
        }

        Set<String> members = stringRedisTemplate.opsForSet().members(partsKey);
        if (members == null || members.size() != totalChunks) {
            throw new ConditionException("仍有分片未上传完成，不能合并");
        }

        // 确保 1..totalChunks 都存在
        for (int i = 1; i <= totalChunks; i++) {
            if (!members.contains(String.valueOf(i))) {
                throw new ConditionException("分片不完整，缺少第 " + i + " 片");
            }
        }

        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 1; i <= totalChunks; i++) {
            String chunkObjectName = buildChunkObjectName(dto.getUploadId(), i, originalFileName);
            sources.add(
                    ComposeSource.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(chunkObjectName)
                            .build()
            );
        }

        try {
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .sources(sources)
                            .build()
            );

            FileEntity fileEntity = new FileEntity();
            fileEntity.setUserId(UserContext.getCurrentUserId());
            fileEntity.setBucketName(minioProperties.getBucketName());
            fileEntity.setObjectName(objectName);
            fileEntity.setOriginalFileName(originalFileName);
            fileEntity.setFileType(contentType);
            fileEntity.setFileSize(fileSize);
            fileEntity.setFileMd5(fileMd5);
            fileEntity.setUploadStatus(1);

            fileMapper.insert(fileEntity);

            cleanupMultipartTask(dto.getUploadId(), totalChunks, originalFileName);
            return fileEntity.getId();
        } catch (Exception e) {
            throw new RuntimeException("分片合并失败", e);
        }
    }

    private String buildMultipartMetaKey(String uploadId) {
        return MULTIPART_META_KEY_PREFIX + uploadId;
    }

    private String buildMultipartPartsKey(String uploadId) {
        return MULTIPART_PARTS_KEY_PREFIX + uploadId;
    }

    private void ensureMultipartTaskExists(String metaKey) {
        Boolean exists = stringRedisTemplate.hasKey(metaKey);
        if (Boolean.FALSE.equals(exists)) {
            throw new ConditionException("上传任务不存在或已过期，请重新初始化上传");
        }
    }

    private void validateMultipartOwner(String metaKey) {
        Long currentUserId = UserContext.getCurrentUserId();
        String taskUserId = getHashValue(metaKey, "userId");
        if (!Objects.equals(String.valueOf(currentUserId), taskUserId)) {
            throw new ConditionException(403, "无权操作该上传任务");
        }
    }

    private String getHashValue(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        if (value == null) {
            throw new ConditionException("上传任务数据不完整，请重新初始化上传");
        }
        return value.toString();
    }

    /**
     * 构造临时分片对象名
     * 同一个 partNumber 永远对应同一个对象名，重复上传会覆盖，不会生成重复分片
     */
    private String buildChunkObjectName(String uploadId, Integer partNumber, String fileName) {
        String suffix = getFileSuffix(fileName);
        return "temp/chunk/" + uploadId + "/" + String.format("%05d", partNumber) + suffix;
    }

    private void cleanupMultipartTask(String uploadId, Integer totalChunks, String originalFileName) {
        try {
            for (int i = 1; i <= totalChunks; i++) {
                String chunkObjectName = buildChunkObjectName(uploadId, i, originalFileName);
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .object(chunkObjectName)
                                .build()
                );
            }
        } catch (Exception ignored) {
            // 分片清理失败不影响主流程结果
        }

        stringRedisTemplate.delete(buildMultipartMetaKey(uploadId));
        stringRedisTemplate.delete(buildMultipartPartsKey(uploadId));
    }

}
