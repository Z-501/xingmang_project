package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;
import java.util.List;

import java.time.LocalDateTime;

@Data
@Builder
public class VideoDetailVO {

    private Long id;

    private Long userId;

    private Long fileId;

    private Long coverFileId;

    private String title;

    private String description;

    private Integer duration;

    private Integer status;

    private LocalDateTime publishTime;

    /**
     * 视频播放地址
     */
    private String videoUrl;

    /**
     * 封面访问地址
     */
    private String coverUrl;

    /**
     * 查询详情的标签
     */
    private List<String> tagNames;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 收藏数
     */
    private Long collectCount;

    /**
     * 投币数
     */
    private Long coinCount;

    /**
     * 当前用户是否已点赞
     */
    private Boolean liked;

    /**
     * 当前用户是否已收藏
     */
    private Boolean collected;

    /**
     * 当前用户是否已投币
     */
    private Boolean coined;

    private LocalDateTime createTime;
}