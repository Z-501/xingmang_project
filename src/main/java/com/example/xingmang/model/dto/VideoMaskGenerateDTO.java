package com.example.xingmang.model.dto;

import lombok.Data;

@Data
public class VideoMaskGenerateDTO {

    /**
     * 抽帧步长：每隔多少帧处理一次
     * 例如 16 表示每 16 帧抽一帧
     */
    private Integer frameStep;
}