package com.example.xingmang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 *  把 application-dev.yml 里的 minio.xxx 自动映射到 Java 对象
 */
@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务端地址（后端访问用）
     */
    private String endpoint;

    /**
     * MinIO 对外访问地址（前端访问预签名 URL 用）
     */
    private String publicEndpoint;

    /**
     * 账号
     */
    private String accessKey;

    /**
     * 密码
     */
    private String secretKey;

    /**
     * 默认桶名
     */
    private String bucketName;

    /**
     * 上传预签名 URL 过期时间（秒）
     */
    private Integer uploadUrlExpireSeconds = 3600;

    /**
     * 访问预签名 URL 过期时间（秒）
     */
    private Integer viewUrlExpireSeconds = 3600;
}
