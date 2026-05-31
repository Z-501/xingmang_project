package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class MultipartUploadInitDTO {

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件总大小（字节）
     */
    private Long fileSize;

    /**
     * 整个文件的MD5
     */
    private String fileMd5;

    /**
     * 总分片数
     */
    private Integer totalChunks;
}
