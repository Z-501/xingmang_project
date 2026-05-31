package com.example.xingmang.controller;

import com.example.xingmang.model.dto.VideoCreateDTO;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.model.vo.VideoDetailVO;
import com.example.xingmang.model.vo.VideoPageVO;
import com.example.xingmang.service.VideoService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/videos")
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    /**
     * 创建视频草稿
     */
    @PostMapping
    public Result<Long> createVideo(@RequestBody VideoCreateDTO dto) {
        return Result.success(videoService.createVideo(dto));
    }

    /**
     * 发布视频
     */
    @PostMapping("/{videoId}/publish")
    public Result<Void> publishVideo(@PathVariable Long videoId) {
        videoService.publishVideo(videoId);
        return Result.success();
    }

    /**
     * 查询视频详情
     */
    @GetMapping("/{videoId}")
    public Result<VideoDetailVO> getVideoDetail(@PathVariable Long videoId) {
        return Result.success(videoService.getVideoDetail(videoId));
    }

    /**
     * 首页视频流 / 瀑布流数据
     */
    @GetMapping("/home")
    public Result<VideoPageVO> pagePublishedVideos(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(videoService.pagePublishedVideos(pageNum, pageSize));
    }

    /**
     * 个性化推荐视频
     */
    @GetMapping("/recommend")
    public Result<VideoPageVO> recommendVideos(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(videoService.recommendVideos(pageNum, pageSize));
    }

    /**
     * 作者作品列表
     */
    @GetMapping("/user/{userId}")
    public Result<VideoPageVO> pagePublishedVideosByUser(@PathVariable Long userId,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(videoService.pagePublishedVideosByUser(userId, pageNum, pageSize));
    }

    /**
     * 视频在线播放（支持 Range / 206）
     */
    @GetMapping("/{videoId}/play")
    public void playVideo(@PathVariable Long videoId,
                          @RequestHeader(value = "Range", required = false) String rangeHeader,
                          HttpServletResponse response) {
        videoService.playVideo(videoId, rangeHeader, response);
    }
}