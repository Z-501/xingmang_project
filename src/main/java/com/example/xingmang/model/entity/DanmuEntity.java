package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_danmu")
public class DanmuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 发送用户ID
     */
    private Long userId;

    /**
     * 弹幕内容
     */
    private String content;

    /**
     * 弹幕出现在视频中的时间点（秒）
     */
    private BigDecimal danmuTime;

    /**
     * 弹幕颜色
     */
    private String color;

    /**
     * 弹幕模式：1滚动 2顶部 3底部
     */
    private Integer mode;

    /**
     * 字体大小：1小 2中 3大
     */
    private Integer fontSize;

    /**
     * 状态：1正常 0删除/屏蔽
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
