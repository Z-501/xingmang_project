package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class UploadIdDTO {
    private String uploadId; // MinIO 返回的分片上传任务 ID
    private String key;      // 文件在桶中的路径
}