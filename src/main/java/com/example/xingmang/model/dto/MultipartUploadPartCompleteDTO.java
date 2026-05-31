package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class MultipartUploadPartCompleteDTO {

    /**
     * 上传任务ID
     */
    private String uploadId;

    /**
     * 分片编号
     */
    private Integer partNumber;
}
