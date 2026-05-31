package com.example.xingmang.model.dto;

import lombok.Data;
/**
    作用：前端上传完成后，通知后端落库确认。
 */
@Data
public class FileCompleteUploadDTO {

    /**
     * MinIO 对象名
     */
    private String objectName;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件 md5
     */
    private String fileMd5;
}