package com.example.xingmang.service;
import com.example.xingmang.model.vo.DanmuMessageVO;
import java.util.List;
/**
 * 弹幕服务
 */
public interface DanmuService {

    /**
     * 把实时弹幕写入 Redis 近期缓存
     */
    void cacheRecentDanmu(DanmuMessageVO danmuMessageVO);

    /**
     * 查询某个视频的近期弹幕（只查 Redis）
     */
    List<DanmuMessageVO> listRecentDanmus(Long videoId, Integer limit);

    /**
     * 播放器初始化弹幕查询：
     * 先查 Redis，若不够再查 MySQL 补齐
     */
    List<DanmuMessageVO> listInitDanmus(Long videoId, Integer limit);

    /**
     * 按视频时间轴区间查询历史弹幕（查 MySQL）
     */
    List<DanmuMessageVO> listHistoryDanmus(Long videoId, Double from, Double to, Integer limit);
}
