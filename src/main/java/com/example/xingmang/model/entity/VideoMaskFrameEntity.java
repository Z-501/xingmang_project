package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_video_mask_frame")
public class VideoMaskFrameEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 抽取的帧序号
     */
    private Integer frameIndex;

    /**
     * 该帧对应的视频时间点（秒）
     */
    private BigDecimal frameTime;

    /**
     * 遮罩图文件ID，对应 t_file.id
     */
    private Long maskFileId;

    /**
     * 遮罩图宽度
     */
    private Integer width;

    /**
     * 遮罩图高度
     */
    private Integer height;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
