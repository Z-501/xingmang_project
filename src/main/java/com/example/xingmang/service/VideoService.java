package com.example.xingmang.service;

import com.example.xingmang.model.dto.VideoCreateDTO;
import com.example.xingmang.model.vo.VideoDetailVO;
import com.example.xingmang.model.vo.VideoPageVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface VideoService {

    /**
     * 创建视频草稿
     */
    Long createVideo(VideoCreateDTO dto);

    /**
     * 发布视频
     */
    void publishVideo(Long videoId);

    /**
     * 查询视频详情
     */
    VideoDetailVO getVideoDetail(Long videoId);

    /**
     * 首页视频流（分页）
     */
    VideoPageVO pagePublishedVideos(Integer pageNum, Integer pageSize);

    /**
     * 个性化推荐视频
     */
    VideoPageVO recommendVideos(Integer pageNum, Integer pageSize);

    /**
     * 查询某个作者的已发布视频
     */
    VideoPageVO pagePublishedVideosByUser(Long userId, Integer pageNum, Integer pageSize);

    /**
     *  视频在线播放
     * @param videoId
     * @param rangeHeader
     * @param response
     */
    void playVideo(Long videoId, String rangeHeader, HttpServletResponse response);
}