package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultipartUploadUrlVO {

    /**
     * 分片编号
     */
    private Integer partNumber;

    /**
     * 分片对象名
     */
    private String chunkObjectName;

    /**
     * 预签名上传地址
     */
    private String uploadUrl;

    /**
     * 过期时间（秒）
     */
    private Integer expireSeconds;
}

