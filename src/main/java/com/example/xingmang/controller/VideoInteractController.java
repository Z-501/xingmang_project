package com.example.xingmang.controller;

import com.example.xingmang.model.vo.Result;
import com.example.xingmang.model.vo.VideoInteractionVO;
import com.example.xingmang.service.VideoInteractService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/videos")
public class VideoInteractController {

    private final VideoInteractService videoInteractService;

    public VideoInteractController(VideoInteractService videoInteractService) {
        this.videoInteractService = videoInteractService;
    }

    /**
     * 点赞 / 取消点赞
     */
    @PostMapping("/{videoId}/like")
    public Result<VideoInteractionVO> toggleLike(@PathVariable Long videoId) {
        return Result.success(videoInteractService.toggleLike(videoId));
    }

    /**
     * 收藏 / 取消收藏
     */
    @PostMapping("/{videoId}/collect")
    public Result<VideoInteractionVO> toggleCollect(@PathVariable Long videoId) {
        return Result.success(videoInteractService.toggleCollect(videoId));
    }

    /**
     * 投币
     */
    @PostMapping("/{videoId}/coin")
    public Result<VideoInteractionVO> coin(@PathVariable Long videoId) {
        return Result.success(videoInteractService.coin(videoId));
    }

    /**
     * 查询互动信息
     */
    @GetMapping("/{videoId}/interaction")
    public Result<VideoInteractionVO> getInteractionInfo(@PathVariable Long videoId) {
        return Result.success(videoInteractService.getInteractionInfo(videoId));
    }
}