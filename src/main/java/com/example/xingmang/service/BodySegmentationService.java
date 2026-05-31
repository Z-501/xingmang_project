package com.example.xingmang.service;

import java.awt.image.BufferedImage;

public interface BodySegmentationService {

    /**
     * 对单帧图片做人像分割，返回 PNG 格式的黑白二值遮罩图字节数组
     * 黑色 = 背景
     * 白色 = 人像主体
     */
    byte[] generateBodyOutlineMask(BufferedImage bufferedImage);
}
