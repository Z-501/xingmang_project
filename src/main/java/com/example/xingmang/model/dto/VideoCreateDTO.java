package com.example.xingmang.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class VideoCreateDTO {

    /**
     * 视频文件ID（必填）
     */
    private Long fileId;

    /**
     * 封面文件ID（可选）
     */
    private Long coverFileId;

    /**
     * 标题（必填）
     */
    private String title;

    /**
     * 简介（可选）
     */
    private String description;

    /**
     * 视频时长（秒）
     */
    private Integer duration;

    /**
     * 标签名称列表
     * 例如：["Java","Spring Boot","微服务"]
     */
    private List<String> tagNames;
}
