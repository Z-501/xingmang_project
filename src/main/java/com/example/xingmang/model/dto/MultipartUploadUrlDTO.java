package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class MultipartUploadUrlDTO {

    /**
     * 上传任务ID
     */
    private String uploadId;

    /**
     * 分片编号，从1开始
     */
    private Integer partNumber;
}
