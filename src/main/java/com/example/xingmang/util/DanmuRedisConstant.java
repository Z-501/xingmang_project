package com.example.xingmang.util;

/**
 * 弹幕缓存常量
 */
public final class DanmuRedisConstant {

    private DanmuRedisConstant() {
    }

    /**
     * 近期弹幕缓存 key 前缀
     */
    public static final String DANMU_RECENT_KEY_PREFIX = "danmu:video:";

    /**
     * 单个视频近期弹幕最多缓存多少条
     */
    public static final long DANMU_RECENT_MAX_COUNT = 300L;

    /**
     * 近期弹幕缓存保留时长（小时）
     */
    public static final long DANMU_RECENT_TTL_HOURS = 6L;

    /**
     * 初始化接口默认返回条数
     */
    public static final int DANMU_RECENT_DEFAULT_LIMIT = 50;

    /**
     * 初始化接口单次最多返回条数
     */
    public static final int DANMU_RECENT_MAX_LIMIT = 100;

    public static String buildRecentDanmuKey(Long videoId) {
        return DANMU_RECENT_KEY_PREFIX + videoId;
    }

    /**
     * 弹幕持久化 Topic
     */
    public static final String TOPIC_DANMU_PERSIST = "topic_danmu_persist";

    /**
     * 弹幕持久化 Consumer Group
     */
    public static final String GROUP_DANMU_PERSIST = "group_danmu_persist";
}