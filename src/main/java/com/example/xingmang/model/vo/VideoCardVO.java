package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VideoCardVO {

    /**
     * 视频ID
     */
    private Long id;

    /**
     * 作者ID
     */
    private Long userId;

    /**
     * 视频文件ID
     */
    private Long fileId;

    /**
     * 封面文件ID
     */
    private Long coverFileId;

    /**
     * 标题
     */
    private String title;

    /**
     * 简介
     */
    private String description;

    /**
     * 时长（秒）
     */
    private Integer duration;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 视频播放地址
     */
    private String videoUrl;

    /**
     * 封面地址
     */
    private String coverUrl;
}