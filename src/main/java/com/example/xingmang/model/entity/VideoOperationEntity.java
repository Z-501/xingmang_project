package com.example.xingmang.model.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_video_operation")
public class VideoOperationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 行为类型：1-点赞 2-投币 3-收藏
     */
    private Integer operationType;

    /**
     * 行为分值：点赞1 投币2 收藏6
     */
    private Integer score;

    private LocalDateTime createTime;
}

