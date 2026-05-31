package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 *  返回给前端上传地址。
 */
@Data
@Builder
public class FileUploadVO {

    /**
     * 是否秒传命中
     */
    private Boolean existed;

    /**
     * 秒传命中时返回已有 fileId
     */
    private Long fileId;

    /**
     * MinIO 对象名
     */
    private String objectName;

    /**
     * 预签名上传 URL
     */
    private String uploadUrl;

    /**
     * 过期时间（秒）
     */
    private Integer expireSeconds;
}
