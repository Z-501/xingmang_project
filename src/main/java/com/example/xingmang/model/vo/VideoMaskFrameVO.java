package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoMaskFrameVO {

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 帧序号
     */
    private Integer frameIndex;

    /**
     * 该帧对应的视频时间点（秒）
     */
    private Double frameTime;

    /**
     * 遮罩图文件ID
     */
    private Long maskFileId;

    /**
     * 遮罩图访问地址
     */
    private String maskUrl;

    /**
     * 遮罩图宽度
     */
    private Integer width;

    /**
     * 遮罩图高度
     */
    private Integer height;
}