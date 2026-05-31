package com.example.xingmang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.VideoCoinMapper;
import com.example.xingmang.mapper.VideoCollectMapper;
import com.example.xingmang.mapper.VideoLikeMapper;
import com.example.xingmang.mapper.VideoMapper;
import com.example.xingmang.model.entity.VideoCoinEntity;
import com.example.xingmang.model.entity.VideoCollectEntity;
import com.example.xingmang.model.entity.VideoEntity;
import com.example.xingmang.model.entity.VideoLikeEntity;
import com.example.xingmang.model.vo.VideoInteractionVO;
import com.example.xingmang.service.VideoInteractService;
import com.example.xingmang.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.xingmang.mapper.VideoOperationMapper;
import com.example.xingmang.model.entity.VideoOperationEntity;


@Service
public class VideoInteractServiceImpl implements VideoInteractService {

    private final VideoMapper videoMapper;
    private final VideoLikeMapper videoLikeMapper;
    private final VideoCollectMapper videoCollectMapper;
    private final VideoCoinMapper videoCoinMapper;
    private final VideoOperationMapper videoOperationMapper;
    private static final int OPERATION_TYPE_LIKE = 1;
    private static final int OPERATION_TYPE_COIN = 2;
    private static final int OPERATION_TYPE_COLLECT = 3;

    private static final int SCORE_LIKE = 1;
    private static final int SCORE_COIN = 2;
    private static final int SCORE_COLLECT = 6;


    public VideoInteractServiceImpl(VideoMapper videoMapper,
                                    VideoLikeMapper videoLikeMapper,
                                    VideoCollectMapper videoCollectMapper,
                                    VideoCoinMapper videoCoinMapper,
                                    VideoOperationMapper videoOperationMapper) {
        this.videoMapper = videoMapper;
        this.videoLikeMapper = videoLikeMapper;
        this.videoCollectMapper = videoCollectMapper;
        this.videoCoinMapper = videoCoinMapper;
        this.videoOperationMapper = videoOperationMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoInteractionVO toggleLike(Long videoId) {
        Long userId = UserContext.getCurrentUserId();
        VideoEntity videoEntity = checkVideoCanInteract(videoId);
        if (videoEntity.getUserId().equals(userId)) {
            throw new ConditionException("不能给自己的视频执行该操作");
        }

        VideoLikeEntity existed = videoLikeMapper.selectOne(
                new LambdaQueryWrapper<VideoLikeEntity>()
                        .eq(VideoLikeEntity::getVideoId, videoId)
                        .eq(VideoLikeEntity::getUserId, userId)
                        .last("limit 1")
        );

        if (existed != null) {
            videoLikeMapper.deleteById(existed.getId());
        } else {
            VideoLikeEntity entity = new VideoLikeEntity();
            entity.setVideoId(videoId);
            entity.setUserId(userId);
            videoLikeMapper.insert(entity);
            // 只在点赞成功时写日志，取消点赞不写
            saveOperationLog(userId, videoId, OPERATION_TYPE_LIKE, SCORE_LIKE);
        }

        return buildInteractionVO(videoId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoInteractionVO toggleCollect(Long videoId) {
        Long userId = UserContext.getCurrentUserId();
        checkVideoCanInteract(videoId);

        VideoCollectEntity existed = videoCollectMapper.selectOne(
                new LambdaQueryWrapper<VideoCollectEntity>()
                        .eq(VideoCollectEntity::getVideoId, videoId)
                        .eq(VideoCollectEntity::getUserId, userId)
                        .last("limit 1")
        );

        if (existed != null) {
            videoCollectMapper.deleteById(existed.getId());
        } else {
            VideoCollectEntity entity = new VideoCollectEntity();
            entity.setVideoId(videoId);
            entity.setUserId(userId);
            videoCollectMapper.insert(entity);
            saveOperationLog(userId, videoId, OPERATION_TYPE_COLLECT, SCORE_COLLECT);
        }

        return buildInteractionVO(videoId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoInteractionVO coin(Long videoId) {
        Long userId = UserContext.getCurrentUserId();
        VideoEntity videoEntity = checkVideoCanInteract(videoId);
        if (videoEntity.getUserId().equals(userId)) {
            throw new ConditionException("不能给自己的视频执行该操作");
        }

        VideoCoinEntity existed = videoCoinMapper.selectOne(
                new LambdaQueryWrapper<VideoCoinEntity>()
                        .eq(VideoCoinEntity::getVideoId, videoId)
                        .eq(VideoCoinEntity::getUserId, userId)
                        .last("limit 1")
        );

        if (existed != null) {
            throw new ConditionException("该视频已投币，请勿重复操作");
        }

        VideoCoinEntity entity = new VideoCoinEntity();
        entity.setVideoId(videoId);
        entity.setUserId(userId);
        videoCoinMapper.insert(entity);
        saveOperationLog(userId, videoId, OPERATION_TYPE_COIN, SCORE_COIN);
        return buildInteractionVO(videoId, userId);
    }

    @Override
    public VideoInteractionVO getInteractionInfo(Long videoId) {
        Long userId = UserContext.getCurrentUserId();
        checkVideoExists(videoId);
        return buildInteractionVO(videoId, userId);
    }

    /**
     * 查询视频是否存在
     */
    private VideoEntity checkVideoExists(Long videoId) {
        VideoEntity videoEntity = videoMapper.selectById(videoId);
        if (videoEntity == null) {
            throw new ConditionException("视频不存在");
        }
        return videoEntity;
    }

    /**
     * 校验视频是否允许互动：必须已发布
     */
    private VideoEntity checkVideoCanInteract(Long videoId) {
        VideoEntity videoEntity = checkVideoExists(videoId);
        if (videoEntity.getStatus() == null || videoEntity.getStatus() != 1) {
            throw new ConditionException("该视频暂不可互动");
        }
        return videoEntity;
    }

    /**
     * 构造互动统计结果
     */
    private VideoInteractionVO buildInteractionVO(Long videoId, Long userId) {
        Long likeCount = videoLikeMapper.selectCount(
                new LambdaQueryWrapper<VideoLikeEntity>()
                        .eq(VideoLikeEntity::getVideoId, videoId)
        );

        Long collectCount = videoCollectMapper.selectCount(
                new LambdaQueryWrapper<VideoCollectEntity>()
                        .eq(VideoCollectEntity::getVideoId, videoId)
        );

        Long coinCount = videoCoinMapper.selectCount(
                new LambdaQueryWrapper<VideoCoinEntity>()
                        .eq(VideoCoinEntity::getVideoId, videoId)
        );

        boolean liked = videoLikeMapper.selectCount(
                new LambdaQueryWrapper<VideoLikeEntity>()
                        .eq(VideoLikeEntity::getVideoId, videoId)
                        .eq(VideoLikeEntity::getUserId, userId)
        ) > 0;

        boolean collected = videoCollectMapper.selectCount(
                new LambdaQueryWrapper<VideoCollectEntity>()
                        .eq(VideoCollectEntity::getVideoId, videoId)
                        .eq(VideoCollectEntity::getUserId, userId)
        ) > 0;

        boolean coined = videoCoinMapper.selectCount(
                new LambdaQueryWrapper<VideoCoinEntity>()
                        .eq(VideoCoinEntity::getVideoId, videoId)
                        .eq(VideoCoinEntity::getUserId, userId)
        ) > 0;

        return VideoInteractionVO.builder()
                .videoId(videoId)
                .likeCount(likeCount)
                .collectCount(collectCount)
                .coinCount(coinCount)
                .currentUserLiked(liked)
                .currentUserCollected(collected)
                .currentUserCoined(coined)
                .build();
    }

    private void saveOperationLog(Long userId, Long videoId, Integer operationType, Integer score) {
        VideoOperationEntity entity = new VideoOperationEntity();
        entity.setUserId(userId);
        entity.setVideoId(videoId);
        entity.setOperationType(operationType);
        entity.setScore(score);
        videoOperationMapper.insert(entity);
    }

}