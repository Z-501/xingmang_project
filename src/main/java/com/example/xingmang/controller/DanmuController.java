package com.example.xingmang.controller;

import com.example.xingmang.model.vo.DanmuMessageVO;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.service.DanmuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class DanmuController {

    private final DanmuService danmuService;

    /**
     * 查询某个视频的近期弹幕（只查 Redis）
     */
    @GetMapping("/{videoId}/danmus/recent")
    public Result<List<DanmuMessageVO>> listRecentDanmus(@PathVariable Long videoId,
                                                         @RequestParam(required = false) Integer limit) {
        return Result.success(danmuService.listRecentDanmus(videoId, limit));
    }

    /**
     * 播放器初始化弹幕查询：
     * 先查 Redis，若不够再查 MySQL 补齐
     */
    @GetMapping("/{videoId}/danmus/init")
    public Result<List<DanmuMessageVO>> listInitDanmus(@PathVariable Long videoId,
                                                       @RequestParam(required = false) Integer limit) {
        return Result.success(danmuService.listInitDanmus(videoId, limit));
    }

    /**
     * 按视频时间轴区间查询历史弹幕（拖动进度条使用）
     */
    @GetMapping("/{videoId}/danmus/history")
    public Result<List<DanmuMessageVO>> listHistoryDanmus(@PathVariable Long videoId,
                                                          @RequestParam Double from,
                                                          @RequestParam Double to,
                                                          @RequestParam(required = false) Integer limit) {
        return Result.success(danmuService.listHistoryDanmus(videoId, from, to, limit));
    }
}