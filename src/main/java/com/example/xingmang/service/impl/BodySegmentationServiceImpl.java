package com.example.xingmang.service.impl;

import com.baidu.aip.bodyanalysis.AipBodyAnalysis;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.service.BodySegmentationService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class BodySegmentationServiceImpl implements BodySegmentationService {

    private final AipBodyAnalysis aipBodyAnalysis;

    @Override
    public byte[] generateBodyOutlineMask(BufferedImage bufferedImage) {
        if (bufferedImage == null) {
            throw new ConditionException(400, "待分割帧不能为空");
        }

        try {
            // 1. 把当前帧转成 PNG 二进制
            ByteArrayOutputStream imageOutputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", imageOutputStream);
            byte[] imageBytes = imageOutputStream.toByteArray();

            // 2. 设置只返回 labelmap，节省带宽
            HashMap<String, String> options = new HashMap<>();
            options.put("type", "labelmap");

            // 3. 调用百度官方 SDK
            JSONObject res = aipBodyAnalysis.bodySeg(imageBytes, options);

            if (res == null) {
                throw new ConditionException("百度人体分割返回为空");
            }

            if (res.has("error_code")) {
                String errorCode = res.optString("error_code");
                String errorMsg = res.optString("error_msg");
                throw new ConditionException("百度人体分割调用失败，error_code=" + errorCode + ", error_msg=" + errorMsg);
            }

            String labelmapBase64 = res.optString("labelmap");
            if (!StringUtils.hasText(labelmapBase64)) {
                throw new ConditionException("百度人体分割未返回 labelmap");
            }

            // 4. 解码 labelmap
            byte[] bytes = Base64.getDecoder().decode(labelmapBase64);
            BufferedImage labelImage = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (labelImage == null) {
                throw new ConditionException("解析 labelmap 图像失败");
            }

            // 5. 按原始帧尺寸 resize，避免和视频帧尺寸不一致
            BufferedImage resizedLabelImage = resize(labelImage, bufferedImage.getWidth(), bufferedImage.getHeight());

            // 6. 转成真正可用的黑白二值图：
            //    人像主体 => 白色
            //    背景 => 黑色
            BufferedImage binaryMask = new BufferedImage(
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight(),
                    BufferedImage.TYPE_BYTE_BINARY
            );

            for (int x = 0; x < resizedLabelImage.getWidth(); x++) {
                for (int y = 0; y < resizedLabelImage.getHeight(); y++) {
                    int rgb = resizedLabelImage.getRGB(x, y);

                    // 取低8位灰度值
                    int gray = rgb & 0xFF;

                    // 只要大于0就视为前景（人体），映射成白色；否则黑色
                    int binaryRgb = gray > 0 ? Color.WHITE.getRGB() : Color.BLACK.getRGB();
                    binaryMask.setRGB(x, y, binaryRgb);
                }
            }

            // 7. 输出 PNG 字节数组，供后续上传 MinIO
            ByteArrayOutputStream maskOutputStream = new ByteArrayOutputStream();
            ImageIO.write(binaryMask, "png", maskOutputStream);
            return maskOutputStream.toByteArray();

        } catch (ConditionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConditionException("生成人像遮罩图失败：" + e.getMessage());
        }
    }

    private BufferedImage resize(BufferedImage img, int newW, int newH) {
        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = resized.createGraphics();
        try {
            g2d.drawImage(tmp, 0, 0, null);
        } finally {
            g2d.dispose();
        }

        return resized;
    }
}