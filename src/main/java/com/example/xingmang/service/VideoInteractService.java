package com.example.xingmang.service;
import com.example.xingmang.model.vo.VideoInteractionVO;

public interface VideoInteractService {

    /**
     * 点赞 / 取消点赞
     */
    VideoInteractionVO toggleLike(Long videoId);

    /**
     * 收藏 / 取消收藏
     */
    VideoInteractionVO toggleCollect(Long videoId);

    /**
     * 投币（一次性，不允许重复）
     */
    VideoInteractionVO coin(Long videoId);

    /**
     * 查询视频三连状态与计数
     */
    VideoInteractionVO getInteractionInfo(Long videoId);
}
