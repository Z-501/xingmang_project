package com.example.xingmang.service;

import com.example.xingmang.model.vo.VideoMaskFrameVO;

import java.util.List;

public interface VideoMaskService {

    /**
     * Generate mask frames for a video.
     */
    void generateMaskFrames(Long videoId, Integer frameStep);

    /**
     * List all generated mask frames for a video.
     */
    List<VideoMaskFrameVO> listMaskFrames(Long videoId);

    /**
     * List mask frames within a playback time range.
     */
    List<VideoMaskFrameVO> listMaskFramesByTimeRange(Long videoId, Double from, Double to);
}
