package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户动态实体类
 * 对应数据库表 t_user_moments
 */
@Data
@TableName("t_user_moments")
public class UserMoment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（发布者）
     */
    private Long userId;

    /**
     * 内容类型：0视频 1直播 2专栏
     * 严谨性：使用不同类型方便消费者在分发时进行差异化处理
     */
    private String type;

    /**
     * 内容详情ID（如视频ID）
     */
    private Long contentId;

    /**
     * 创建时间
     * 高并发下的排序基准：在 Redis ZSet 中作为 score 使用
     */
    private Date createTime;

    /**
     *  更新时间
     */
    private Date updateTime;
}
