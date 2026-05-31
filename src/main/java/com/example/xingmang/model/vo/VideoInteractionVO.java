package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VideoInteractionVO {

    /**
     * 视频ID
     */
    private Long videoId;

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
    private Boolean currentUserLiked;

    /**
     * 当前用户是否已收藏
     */
    private Boolean currentUserCollected;

    /**
     * 当前用户是否已投币
     */
    private Boolean currentUserCoined;

}