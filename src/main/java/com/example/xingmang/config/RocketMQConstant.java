package com.example.xingmang.config;

/**
 * RocketMQConstant 是消息队列模块的全局常量配置类
 * 主要负责统一管理 RocketMQ 中使用到的 Topic、Consumer Group 和 Tag
 * 该类本质上属于消息队列体系的“统一命名中心”，为动态分发、弹幕推送、点赞收藏异步处理等场景提供统一的消息主题定义。
 * 用于统一管理消息队列的主题和消费组，避免硬编码导致的对齐错误
 */
public class RocketMQConstant {

    /**
     * 视频动态提醒主题
     * 场景：博主发布视频后，通过此 Topic 推送给订阅的粉丝
     */
    public static final String TOPIC_MOMENTS = "topic-moments";

    /**
     * 视频动态提醒消费者组
     */
    public static final String GROUP_MOMENTS = "group-moments";

    /**
     * 弹幕推送主题
     * 场景：用户发送弹幕后，实时分发给在线观看的用户
     */
    public static final String TOPIC_DANMUS = "topic-danmus";

    /**
     * 弹幕消费者组
     */
    public static final String GROUP_DANMUS = "group-danmus";

    /**
     * 点赞/收藏消息主题
     * 场景：异步持久化点赞数据，减轻数据库瞬时写压力
     */
    public static final String TOPIC_STATS = "topic-stats";

    /**
     * 标签常量：用于更精细的消息过滤
     */
    public static class Tags {
        public static final String VIDEO_PUBLISH = "tag-video-publish"; // 视频发布标签
        public static final String VIDEO_LIKE = "tag-video-like";       // 视频点赞标签
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
