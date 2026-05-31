package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video")
public class VideoEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 投稿用户ID
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
     * 视频标题
     */
    private String title;

    /**
     * 视频简介
     */
    private String description;

    /**
     * 视频时长（秒）
     */
    private Integer duration;

    /**
     * 状态：0-草稿，1-已发布，2-已下架
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
