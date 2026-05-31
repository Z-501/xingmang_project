package com.example.xingmang.controller;

import com.example.xingmang.model.dto.VideoMaskGenerateDTO;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.model.vo.VideoMaskFrameVO;
import com.example.xingmang.service.VideoMaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoMaskController {

    private final VideoMaskService videoMaskService;

    /**
     * 手动触发某个视频的遮罩帧生成
     */
    @PostMapping("/{videoId}/mask-frames/generate")
    public Result<Void> generateMaskFrames(@PathVariable Long videoId,
                                           @RequestBody(required = false) VideoMaskGenerateDTO dto) {
        Integer frameStep = dto == null ? null : dto.getFrameStep();
        videoMaskService.generateMaskFrames(videoId, frameStep);
        return Result.success();
    }

    /**
     * 查询某个视频的全部遮罩帧
     */
    @GetMapping("/{videoId}/mask-frames")
    public Result<List<VideoMaskFrameVO>> listMaskFrames(@PathVariable Long videoId) {
        return Result.success(videoMaskService.listMaskFrames(videoId));
    }

    /**
     * 按时间范围查询某个视频的遮罩帧
     */
    @GetMapping("/{videoId}/mask-frames/range")
    public Result<List<VideoMaskFrameVO>> listMaskFramesByTimeRange(@PathVariable Long videoId,
                                                                    @RequestParam Double from,
                                                                    @RequestParam Double to) {
        return Result.success(videoMaskService.listMaskFramesByTimeRange(videoId, from, to));
    }
}