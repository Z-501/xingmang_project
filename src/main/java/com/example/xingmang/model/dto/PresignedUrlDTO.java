package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class PresignedUrlDTO {
    private Integer partNumber; // 分片编号
    private String url;         // 该分片的预签名上传地址
}
