package com.example.xingmang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.VideoMaskFrameMapper;
import com.example.xingmang.mapper.VideoMapper;
import com.example.xingmang.model.entity.VideoEntity;
import com.example.xingmang.model.entity.VideoMaskFrameEntity;
import com.example.xingmang.model.vo.VideoMaskFrameVO;
import com.example.xingmang.service.FileService;
import com.example.xingmang.service.VideoMaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import com.example.xingmang.service.BodySegmentationService;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


@Service
@RequiredArgsConstructor
public class VideoMaskServiceImpl implements VideoMaskService {

    private final VideoMapper videoMapper;
    private final VideoMaskFrameMapper videoMaskFrameMapper;
    private final FileService fileService;
    private final BodySegmentationService bodySegmentationService;

    @Override
    public void generateMaskFrames(Long videoId, Integer frameStep) {
        if (videoId == null || videoId <= 0) {
            throw new ConditionException(400, "videoId 不合法");
        }

        VideoEntity videoEntity = videoMapper.selectById(videoId);
        if (videoEntity == null) {
            throw new ConditionException("视频不存在");
        }

        if (videoEntity.getFileId() == null) {
            throw new ConditionException("视频文件不存在");
        }

        int finalFrameStep = normalizeFrameStep(frameStep);

        // 重新生成时，先删除旧的遮罩帧元数据（当前阶段先只删元数据，不清理旧文件）
        videoMaskFrameMapper.delete(
                new LambdaQueryWrapper<VideoMaskFrameEntity>()
                        .eq(VideoMaskFrameEntity::getVideoId, videoId)
        );

        Path tempVideoPath = null;
        FFmpegFrameGrabber grabber = null;
        Java2DFrameConverter converter = new Java2DFrameConverter();

        try (InputStream videoInputStream = fileService.openFileStream(videoEntity.getFileId())) {
            // 1. 先把 MinIO 中的视频对象流写到本地临时文件
            tempVideoPath = Files.createTempFile("video-mask-" + videoId + "-", ".mp4");
            Files.copy(videoInputStream, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);

            // 2. 使用 JavaCV 打开视频
            grabber = new FFmpegFrameGrabber(tempVideoPath.toFile());
            grabber.start();

            double frameRate = grabber.getFrameRate();
            if (frameRate <= 0) {
                frameRate = 25.0;
            }

            int imageFrameIndex = 0;
            Frame frame;

            while ((frame = grabber.grabImage()) != null) {
                if (imageFrameIndex % finalFrameStep != 0) {
                    imageFrameIndex++;
                    continue;
                }

                BufferedImage bufferedImage = converter.convert(frame);
                if (bufferedImage == null) {
                    imageFrameIndex++;
                    continue;
                }

                // 3. 生成人像二值遮罩图
                byte[] maskImageBytes = bodySegmentationService.generateBodyOutlineMask(bufferedImage);
                if (maskImageBytes == null || maskImageBytes.length == 0) {
                    imageFrameIndex++;
                    continue;
                }

                // 4. 上传遮罩图到 MinIO / t_file
                String originalFileName = "video_" + videoId + "_mask_" + imageFrameIndex + ".png";
                Long maskFileId = fileService.saveGeneratedFile(
                        maskImageBytes,
                        "video-mask/" + videoId,
                        originalFileName,
                        "image/png",
                        videoEntity.getUserId()
                );

                // 5. 保存遮罩帧元数据
                VideoMaskFrameEntity frameEntity = new VideoMaskFrameEntity();
                frameEntity.setVideoId(videoId);
                frameEntity.setFrameIndex(imageFrameIndex);
                frameEntity.setFrameTime(BigDecimal.valueOf(imageFrameIndex / frameRate)
                        .setScale(3, RoundingMode.HALF_UP));
                frameEntity.setMaskFileId(maskFileId);
                frameEntity.setWidth(bufferedImage.getWidth());
                frameEntity.setHeight(bufferedImage.getHeight());

                videoMaskFrameMapper.insert(frameEntity);

                imageFrameIndex++;
            }

        } catch (ConditionException e) {
            throw e;
        } catch (Exception e) {
            throw new ConditionException("生成视频遮罩帧失败：" + e.getMessage());
        } finally {
            try {
                if (grabber != null) {
                    grabber.stop();
                    grabber.close();
                }
            } catch (Exception ignored) {
            }

            try {
                if (tempVideoPath != null) {
                    Files.deleteIfExists(tempVideoPath);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public List<VideoMaskFrameVO> listMaskFrames(Long videoId) {
        if (videoId == null || videoId <= 0) {
            return Collections.emptyList();
        }

        List<VideoMaskFrameEntity> frameEntities = videoMaskFrameMapper.selectList(
                new LambdaQueryWrapper<VideoMaskFrameEntity>()
                        .eq(VideoMaskFrameEntity::getVideoId, videoId)
                        .orderByAsc(VideoMaskFrameEntity::getFrameTime)
                        .orderByAsc(VideoMaskFrameEntity::getFrameIndex)
        );

        if (frameEntities == null || frameEntities.isEmpty()) {
            return Collections.emptyList();
        }

        return frameEntities.stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public List<VideoMaskFrameVO> listMaskFramesByTimeRange(Long videoId, Double from, Double to) {
        if (videoId == null || videoId <= 0) {
            throw new ConditionException(400, "videoId 不合法");
        }
        if (from == null || from < 0) {
            throw new ConditionException(400, "from 参数不合法");
        }
        if (to == null || to < from) {
            throw new ConditionException(400, "to 参数不合法");
        }

        List<VideoMaskFrameEntity> frameEntities = videoMaskFrameMapper.selectList(
                new LambdaQueryWrapper<VideoMaskFrameEntity>()
                        .eq(VideoMaskFrameEntity::getVideoId, videoId)
                        .ge(VideoMaskFrameEntity::getFrameTime, from)
                        .le(VideoMaskFrameEntity::getFrameTime, to)
                        .orderByAsc(VideoMaskFrameEntity::getFrameTime)
                        .orderByAsc(VideoMaskFrameEntity::getFrameIndex)
        );

        if (frameEntities == null || frameEntities.isEmpty()) {
            return Collections.emptyList();
        }

        return frameEntities.stream()
                .map(this::convertToVO)
                .toList();
    }

    private VideoMaskFrameVO convertToVO(VideoMaskFrameEntity entity) {
        String maskUrl = null;
        if (entity.getMaskFileId() != null) {
            maskUrl = fileService.getViewUrl(entity.getMaskFileId());
        }

        return VideoMaskFrameVO.builder()
                .videoId(entity.getVideoId())
                .frameIndex(entity.getFrameIndex())
                .frameTime(entity.getFrameTime() == null ? null : entity.getFrameTime().doubleValue())
                .maskFileId(entity.getMaskFileId())
                .maskUrl(maskUrl)
                .width(entity.getWidth())
                .height(entity.getHeight())
                .build();
    }

    private int normalizeFrameStep(Integer frameStep) {
        if (frameStep == null || frameStep <= 0) {
            return 16;
        }
        return Math.min(frameStep, 120);
    }
}
