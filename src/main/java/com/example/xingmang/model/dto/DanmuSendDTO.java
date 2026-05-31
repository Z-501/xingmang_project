package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class DanmuSendDTO {

    /**
     * 弹幕内容
     */
    private String content;

    /**
     * 弹幕出现在视频时间轴的秒数，例如 12.5 表示视频播放到 12.5s 时显示
     */
    private Double danmuTime;

    /**
     * 颜色，前期直接传十六进制字符串，例如 #FFFFFF
     */
    private String color;

    /**
     * 弹幕模式：
     * 1 = 滚动
     * 2 = 顶部
     * 3 = 底部
     */
    private Integer mode;

    /**
     * 字号，前期可以简单约定：
     * 1 = 小
     * 2 = 中
     * 3 = 大
     */
    private Integer fontSize;
}