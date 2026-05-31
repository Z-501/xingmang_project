package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DanmuMessageVO {

    /**
     * 当前房间对应的视频ID
     */
    private Long videoId;

    /**
     * 发送者用户ID
     */
    private Long userId;

    /**
     * 弹幕内容
     */
    private String content;

    /**
     * 弹幕时间轴位置
     */
    private Double danmuTime;

    /**
     * 弹幕颜色
     */
    private String color;

    /**
     * 弹幕模式：1滚动 2顶部 3底部
     */
    private Integer mode;

    /**
     * 字号：1小 2中 3大
     */
    private Integer fontSize;

    /**
     * 服务端广播时间
     */
    private LocalDateTime createTime;
}
