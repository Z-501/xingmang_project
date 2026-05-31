package com.example.xingmang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.FileMapper;
import com.example.xingmang.mapper.VideoMapper;
import com.example.xingmang.model.dto.VideoCreateDTO;
import com.example.xingmang.model.entity.FileEntity;
import com.example.xingmang.model.entity.UserMoment;
import com.example.xingmang.model.entity.VideoEntity;
import com.example.xingmang.model.vo.VideoCardVO;
import com.example.xingmang.model.vo.VideoDetailVO;
import com.example.xingmang.model.vo.VideoPageVO;
import com.example.xingmang.service.FileService;
import com.example.xingmang.service.UserMomentsService;
import com.example.xingmang.service.VideoInteractService;
import com.example.xingmang.service.VideoService;
import com.example.xingmang.util.UserContext;
import com.example.xingmang.model.vo.VideoInteractionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StreamUtils;
import java.io.InputStream;
import com.example.xingmang.mapper.TagMapper;
import com.example.xingmang.mapper.VideoTagMapper;
import com.example.xingmang.model.entity.TagEntity;
import com.example.xingmang.model.entity.VideoTagEntity;
import com.example.xingmang.mapper.VideoOperationMapper;
import com.example.xingmang.model.entity.VideoOperationEntity;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;


@Service
public class VideoServiceImpl implements VideoService {

    private final VideoMapper videoMapper;
    private final FileMapper fileMapper;
    private final FileService fileService;
    private final UserMomentsService userMomentsService;
    private final VideoInteractService videoInteractService;
    private final MinioClient minioClient;
    private final TagMapper tagMapper;
    private final VideoTagMapper videoTagMapper;
    private final VideoOperationMapper videoOperationMapper;


    public VideoServiceImpl(VideoMapper videoMapper,
                            FileMapper fileMapper,
                            TagMapper  tagMapper,
                            VideoTagMapper videoTagMapper,
                            VideoOperationMapper videoOperationMapper,
                            FileService fileService,
                            UserMomentsService userMomentsService,
                            VideoInteractService videoInteractService,
                            MinioClient minioClient) {
        this.videoMapper = videoMapper;
        this.fileMapper = fileMapper;
        this.tagMapper = tagMapper;
        this.videoTagMapper = videoTagMapper;
        this.videoOperationMapper = videoOperationMapper;
        this.fileService = fileService;
        this.userMomentsService = userMomentsService;
        this.videoInteractService = videoInteractService;
        this.minioClient = minioClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVideo(VideoCreateDTO dto) {
        Long userId = UserContext.getCurrentUserId();

        if (dto.getFileId() == null) {
            throw new ConditionException("视频文件不能为空");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new ConditionException("视频标题不能为空");
        }

        FileEntity videoFile = fileMapper.selectById(dto.getFileId());
        if (videoFile == null) {
            throw new ConditionException("视频文件不存在");
        }

        if (dto.getCoverFileId() != null) {
            FileEntity coverFile = fileMapper.selectById(dto.getCoverFileId());
            if (coverFile == null) {
                throw new ConditionException("封面文件不存在");
            }
        }

        VideoEntity videoEntity = new VideoEntity();
        videoEntity.setUserId(userId);
        videoEntity.setFileId(dto.getFileId());
        videoEntity.setCoverFileId(dto.getCoverFileId());
        videoEntity.setTitle(dto.getTitle());
        videoEntity.setDescription(dto.getDescription());
        videoEntity.setDuration(dto.getDuration());
        videoEntity.setStatus(0);

        videoMapper.insert(videoEntity);
        saveVideoTags(videoEntity.getId(), dto.getTagNames());
        return videoEntity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishVideo(Long videoId) {
        Long userId = UserContext.getCurrentUserId();

        VideoEntity videoEntity = videoMapper.selectById(videoId);
        if (videoEntity == null) {
            throw new ConditionException("视频不存在");
        }
        if (!videoEntity.getUserId().equals(userId)) {
            throw new ConditionException(403, "无权发布该视频");
        }
        if (videoEntity.getStatus() != 0) {
            throw new ConditionException("只有草稿状态的视频才能发布");
        }

        videoEntity.setStatus(1);
        videoEntity.setPublishTime(LocalDateTime.now());
        videoMapper.updateById(videoEntity);

        UserMoment userMoment = new UserMoment();
        userMoment.setUserId(userId);
        userMoment.setType("0");
        userMoment.setContentId(videoEntity.getId());

        userMomentsService.addUserMoments(userMoment);
    }

    @Override
    public VideoDetailVO getVideoDetail(Long videoId) {
        Long currentUserId = UserContext.getCurrentUserId();

        VideoEntity videoEntity = videoMapper.selectById(videoId);
        if (videoEntity == null) {
            throw new ConditionException("视频不存在");
        }

        if (videoEntity.getStatus() != 1 && !videoEntity.getUserId().equals(currentUserId)) {
            throw new ConditionException(403, "无权查看该视频");
        }

        String videoUrl = fileService.getViewUrl(videoEntity.getFileId());
        String coverUrl = null;
        if (videoEntity.getCoverFileId() != null) {
            coverUrl = fileService.getViewUrl(videoEntity.getCoverFileId());
        }

        VideoInteractionVO interactionVO = videoInteractService.getInteractionInfo(videoId);
        List<String> tagNames = getTagNamesByVideoId(videoId);

        return VideoDetailVO.builder()
                .tagNames(tagNames)
                .id(videoEntity.getId())
                .userId(videoEntity.getUserId())
                .fileId(videoEntity.getFileId())
                .coverFileId(videoEntity.getCoverFileId())
                .title(videoEntity.getTitle())
                .description(videoEntity.getDescription())
                .duration(videoEntity.getDuration())
                .status(videoEntity.getStatus())
                .publishTime(videoEntity.getPublishTime())
                .videoUrl(videoUrl)
                .coverUrl(coverUrl)
                .likeCount(interactionVO.getLikeCount())
                .collectCount(interactionVO.getCollectCount())
                .coinCount(interactionVO.getCoinCount())
                .liked(interactionVO.getCurrentUserLiked())
                .collected(interactionVO.getCurrentUserCollected())
                .coined(interactionVO.getCurrentUserCoined())
                .createTime(videoEntity.getCreateTime())
                .build();
    }

    @Override
    public VideoPageVO pagePublishedVideos(Integer pageNum, Integer pageSize) {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 30) {
            pageSize = 30;
        }

        Page<VideoEntity> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<VideoEntity> wrapper = new LambdaQueryWrapper<VideoEntity>()
                .eq(VideoEntity::getStatus, 1)
                .orderByDesc(VideoEntity::getPublishTime)
                .orderByDesc(VideoEntity::getId);

        Page<VideoEntity> resultPage = videoMapper.selectPage(page, wrapper);

        List<VideoCardVO> records = resultPage.getRecords().stream().map(video -> {
            String videoUrl = fileService.getViewUrl(video.getFileId());
            String coverUrl = null;
            if (video.getCoverFileId() != null) {
                coverUrl = fileService.getViewUrl(video.getCoverFileId());
            }

            return VideoCardVO.builder()
                    .id(video.getId())
                    .userId(video.getUserId())
                    .fileId(video.getFileId())
                    .coverFileId(video.getCoverFileId())
                    .title(video.getTitle())
                    .description(video.getDescription())
                    .duration(video.getDuration())
                    .publishTime(video.getPublishTime())
                    .videoUrl(videoUrl)
                    .coverUrl(coverUrl)
                    .build();
        }).toList();

        return VideoPageVO.builder()
                .pageNum((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.getCurrent() * resultPage.getSize() < resultPage.getTotal())
                .records(records)
                .build();
    }

    @Override
    public VideoPageVO recommendVideos(Integer pageNum, Integer pageSize) {
        pageNum = normalizePageNum(pageNum);
        pageSize = normalizePageSize(pageSize);

        Long currentUserId = UserContext.getCurrentUserId();

        List<VideoOperationEntity> operationList = videoOperationMapper.selectList(
                new LambdaQueryWrapper<VideoOperationEntity>()
                        .eq(VideoOperationEntity::getUserId, currentUserId)
                        .orderByDesc(VideoOperationEntity::getCreateTime)
        );

        // 冷启动：直接走最新发布兜底
        if (operationList == null || operationList.isEmpty()) {
            return buildRecommendFallbackPage(currentUserId, pageNum, pageSize);
        }

        Set<Long> interactedVideoIds = operationList.stream()
                .map(VideoOperationEntity::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (interactedVideoIds.isEmpty()) {
            return buildRecommendFallbackPage(currentUserId, pageNum, pageSize);
        }

        // 一次性查出用户历史交互视频
        List<VideoEntity> interactedVideos = videoMapper.selectBatchIds(interactedVideoIds);
        Map<Long, VideoEntity> interactedVideoMap = interactedVideos.stream()
                .filter(video -> video.getId() != null)
                .collect(Collectors.toMap(VideoEntity::getId, video -> video));

        // 一次性查出这些视频的标签
        Map<Long, List<Long>> tagIdsMapByVideoIds = getTagIdsMapByVideoIds(interactedVideoIds);

        // 聚合用户偏好
        Map<Long, Integer> tagPreferenceMap = new HashMap<>();
        Map<Long, Integer> authorPreferenceMap = new HashMap<>();

        for (VideoOperationEntity operation : operationList) {
            Long videoId = operation.getVideoId();
            if (videoId == null) {
                continue;
            }

            int score = operation.getScore() == null ? 0 : operation.getScore();

            VideoEntity video = interactedVideoMap.get(videoId);
            if (video != null && video.getUserId() != null) {
                authorPreferenceMap.merge(video.getUserId(), score, Integer::sum);
            }

            List<Long> tagIds = tagIdsMapByVideoIds.getOrDefault(videoId, Collections.emptyList());
            for (Long tagId : tagIds) {
                tagPreferenceMap.merge(tagId, score, Integer::sum);
            }
        }

        Set<Long> preferredTagIds = tagPreferenceMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<Long> preferredAuthorIds = authorPreferenceMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // 三路召回
        List<VideoEntity> tagRecallList = recallVideosByTags(preferredTagIds, currentUserId, interactedVideoIds, 150);
        List<VideoEntity> authorRecallList = recallVideosByAuthors(preferredAuthorIds, currentUserId, interactedVideoIds, 100);
        List<VideoEntity> latestRecallList = recallLatestVideos(currentUserId, interactedVideoIds, 100);

        // 合并去重
        List<VideoEntity> candidateList = mergeAndDistinctVideos(tagRecallList, authorRecallList, latestRecallList);

        if (candidateList.isEmpty()) {
            return buildRecommendFallbackPage(currentUserId, pageNum, pageSize);
        }

        // 候选视频标签一次性查出
        Set<Long> candidateVideoIds = candidateList.stream()
                .map(VideoEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, List<Long>> candidateTagMap = getTagIdsMapByVideoIds(candidateVideoIds);

        // 打分
        Map<Long, Double> scoreMap = new HashMap<>();
        for (VideoEntity video : candidateList) {
            double score = calculateRecommendScore(video, tagPreferenceMap, authorPreferenceMap, candidateTagMap);
            scoreMap.put(video.getId(), score);
        }

        // 排序
        candidateList.sort((a, b) -> {
            double scoreA = scoreMap.getOrDefault(a.getId(), 0D);
            double scoreB = scoreMap.getOrDefault(b.getId(), 0D);

            int scoreCompare = Double.compare(scoreB, scoreA);
            if (scoreCompare != 0) {
                return scoreCompare;
            }

            LocalDateTime publishTimeA = a.getPublishTime() == null ? LocalDateTime.MIN : a.getPublishTime();
            LocalDateTime publishTimeB = b.getPublishTime() == null ? LocalDateTime.MIN : b.getPublishTime();

            int timeCompare = publishTimeB.compareTo(publishTimeA);
            if (timeCompare != 0) {
                return timeCompare;
            }

            return Long.compare(b.getId(), a.getId());
        });

        return buildPageFromSortedList(candidateList, pageNum, pageSize);
    }

    private double calculateRecommendScore(VideoEntity video,
                                             Map<Long, Integer> tagPreferenceMap,
                                             Map<Long, Integer> authorPreferenceMap,
                                             Map<Long, List<Long>> candidateTagMap) {
        double totalScore = 0D;

        // 1. 作者偏好分
        Integer authorScore = authorPreferenceMap.get(video.getUserId());
        if (authorScore != null) {
            totalScore += authorScore * 1.2D;
        }

        // 2. 标签匹配分
        List<Long> candidateTagIds = candidateTagMap.getOrDefault(video.getId(), Collections.emptyList());
        for (Long tagId : candidateTagIds) {
            Integer tagScore = tagPreferenceMap.get(tagId);
            if (tagScore != null) {
                totalScore += tagScore * 2.5D;
            }
        }

        // 3. 新鲜度分
        if (video.getPublishTime() != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(video.getPublishTime(), LocalDateTime.now()));
            double freshnessScore = Math.max(15 - days, 0) * 0.2D;
            totalScore += freshnessScore;
        }

        return totalScore;
    }

    /**
     * 冷启动兜底：没有互动行为时，返回“排除自己视频”的最新发布流
     */
    private VideoPageVO buildRecommendFallbackPage(Long currentUserId, Integer pageNum, Integer pageSize) {
        Page<VideoEntity> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<VideoEntity> wrapper = new LambdaQueryWrapper<VideoEntity>()
                .eq(VideoEntity::getStatus, 1)
                .ne(VideoEntity::getUserId, currentUserId)
                .orderByDesc(VideoEntity::getPublishTime)
                .orderByDesc(VideoEntity::getId);

        Page<VideoEntity> resultPage = videoMapper.selectPage(page, wrapper);

        return VideoPageVO.builder()
                .pageNum((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.getCurrent() * resultPage.getSize() < resultPage.getTotal())
                .records(buildVideoCardList(resultPage.getRecords()))
                .build();
    }

    /**
     * 对已排好序的候选列表做内存分页
     */
    private VideoPageVO buildPageFromSortedList(List<VideoEntity> sortedList, Integer pageNum, Integer pageSize) {
        int total = sortedList == null ? 0 : sortedList.size();
        int fromIndex = (pageNum - 1) * pageSize;

        if (fromIndex >= total) {
            return VideoPageVO.builder()
                    .pageNum(pageNum)
                    .pageSize(pageSize)
                    .total((long) total)
                    .hasMore(false)
                    .records(Collections.emptyList())
                    .build();
        }

        int toIndex = Math.min(fromIndex + pageSize, total);
        List<VideoEntity> pageList = sortedList.subList(fromIndex, toIndex);

        return VideoPageVO.builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total((long) total)
                .hasMore(toIndex < total)
                .records(buildVideoCardList(pageList))
                .build();
    }

    /**
     * 基础版推荐打分：
     * 1. 标签匹配分
     * 2. 作者偏好分
     * 3. 新鲜度分
     */
    private double calculateRecommendScore(VideoEntity video,
                                           Map<Long, Integer> tagPreferenceMap,
                                           Map<Long, Integer> authorPreferenceMap) {
        double totalScore = 0D;

        // 1. 作者偏好分
        Integer authorScore = authorPreferenceMap.get(video.getUserId());
        if (authorScore != null) {
            totalScore += authorScore * 1.5D;
        }

        // 2. 标签匹配分
        List<Long> candidateTagIds = getTagIdsByVideoId(video.getId());
        for (Long tagId : candidateTagIds) {
            Integer tagScore = tagPreferenceMap.get(tagId);
            if (tagScore != null) {
                totalScore += tagScore * 2.0D;
            }
        }

        // 3. 新鲜度分：越新的视频，额外加一点点分
        if (video.getPublishTime() != null) {
            long days = Math.max(0, ChronoUnit.DAYS.between(video.getPublishTime(), LocalDateTime.now()));
            double freshnessScore = Math.max(30 - days, 0) * 0.1D;
            totalScore += freshnessScore;
        }

        return totalScore;
    }

    @Override
    public VideoPageVO pagePublishedVideosByUser(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null) {
            throw new ConditionException("作者ID不能为空");
        }
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 30) {
            pageSize = 30;
        }

        Page<VideoEntity> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<VideoEntity> wrapper = new LambdaQueryWrapper<VideoEntity>()
                .eq(VideoEntity::getUserId, userId)
                .eq(VideoEntity::getStatus, 1)
                .orderByDesc(VideoEntity::getPublishTime)
                .orderByDesc(VideoEntity::getId);

        Page<VideoEntity> resultPage = videoMapper.selectPage(page, wrapper);

        List<VideoCardVO> records = buildVideoCardList(resultPage.getRecords());

        return VideoPageVO.builder()
                .pageNum((int) resultPage.getCurrent())
                .pageSize((int) resultPage.getSize())
                .total(resultPage.getTotal())
                .hasMore(resultPage.getCurrent() * resultPage.getSize() < resultPage.getTotal())
                .records(records)
                .build();
    }

    /**
     * 统一构造视频卡片列表（批量查询）
     */
    private List<VideoCardVO> buildVideoCardList(List<VideoEntity> videoList) {
        if (videoList == null || videoList.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 收集所有需要用到的 fileId（视频文件 + 封面文件）
        Set<Long> fileIdSet = new HashSet<>();
        for (VideoEntity video : videoList) {
            if (video.getFileId() != null) {
                fileIdSet.add(video.getFileId());
            }
            if (video.getCoverFileId() != null) {
                fileIdSet.add(video.getCoverFileId());
            }
        }

        // 2. 一次性批量查询并生成 URL
        Map<Long, String> fileUrlMap = fileService.getViewUrlMap(fileIdSet);

        // 3. 组装返回结果
        return videoList.stream().map(video -> VideoCardVO.builder()
                .id(video.getId())
                .userId(video.getUserId())
                .fileId(video.getFileId())
                .coverFileId(video.getCoverFileId())
                .title(video.getTitle())
                .description(video.getDescription())
                .duration(video.getDuration())
                .publishTime(video.getPublishTime())
                .videoUrl(fileUrlMap.get(video.getFileId()))
                .coverUrl(video.getCoverFileId() == null ? null : fileUrlMap.get(video.getCoverFileId()))
                .build()
        ).toList();
    }

    @Override
    public void playVideo(Long videoId, String rangeHeader, HttpServletResponse response) {
        Long currentUserId = UserContext.getCurrentUserId();

        VideoEntity videoEntity = videoMapper.selectById(videoId);
        if (videoEntity == null) {
            throw new ConditionException("视频不存在");
        }

        // 非作者只能播放已发布视频
        if (videoEntity.getStatus() != 1 && !videoEntity.getUserId().equals(currentUserId)) {
            throw new ConditionException(403, "无权播放该视频");
        }

        FileEntity fileEntity = fileMapper.selectById(videoEntity.getFileId());
        if (fileEntity == null) {
            throw new ConditionException("视频文件不存在");
        }

        try {
            StatObjectResponse statObjectResponse = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(fileEntity.getBucketName())
                            .object(fileEntity.getObjectName())
                            .build()
            );

            long fileSize = statObjectResponse.size();
            long[] range = parseRange(rangeHeader, fileSize);
            long start = range[0];
            long end = range[1];
            long contentLength = end - start + 1;

            response.setHeader("Accept-Ranges", "bytes");
            response.setContentType(fileEntity.getFileType() != null ? fileEntity.getFileType() : "video/mp4");
            response.setHeader("Content-Length", String.valueOf(contentLength));

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }

            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(fileEntity.getBucketName())
                            .object(fileEntity.getObjectName())
                            .offset(start)
                            .length(contentLength)
                            .build()
            )) {
                StreamUtils.copy(inputStream, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            throw new RuntimeException("视频播放失败", e);
        }
    }

    /**
     * 解析 Range 请求头
     * 支持：
     * bytes=0-1023
     * bytes=1024-
     * bytes=-1024
     */
    private long[] parseRange(String rangeHeader, long fileSize) {
        if (!StringUtils.hasText(rangeHeader) || !rangeHeader.startsWith("bytes=")) {
            return new long[]{0, fileSize - 1};
        }

        String rangeValue = rangeHeader.substring(6).trim();
        String[] parts = rangeValue.split("-", 2);

        try {
            long start;
            long end;

            // bytes=-1024
            if (!StringUtils.hasText(parts[0])) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0) {
                    throw new ConditionException(416, "请求范围不合法");
                }
                start = Math.max(fileSize - suffixLength, 0);
                end = fileSize - 1;
                return new long[]{start, end};
            }

            // bytes=1024- 或 bytes=1024-2048
            start = Long.parseLong(parts[0]);
            if (start >= fileSize) {
                throw new ConditionException(416, "请求范围超出文件大小");
            }

            if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
                end = fileSize - 1;
            } else {
                end = Long.parseLong(parts[1]);
                if (end >= fileSize) {
                    end = fileSize - 1;
                }
            }

            if (start > end) {
                throw new ConditionException(416, "请求范围不合法");
            }

            return new long[]{start, end};
        } catch (NumberFormatException e) {
            throw new ConditionException(416, "请求范围格式不正确");
        }
    }

    /**
     *  保存视频标签
     */
    private void saveVideoTags(Long videoId, List<String> tagNames) {
        if (videoId == null || tagNames == null || tagNames.isEmpty()) {
            return;
        }

        Set<String> normalizedTagNames = new LinkedHashSet<>();
        for (String tagName : tagNames) {
            if (StringUtils.hasText(tagName)) {
                normalizedTagNames.add(tagName.trim());
            }
        }

        if (normalizedTagNames.isEmpty()) {
            return;
        }

        for (String tagName : normalizedTagNames) {
            TagEntity tagEntity = tagMapper.selectOne(
                    new LambdaQueryWrapper<TagEntity>()
                            .eq(TagEntity::getName, tagName)
                            .last("limit 1")
            );

            if (tagEntity == null) {
                tagEntity = new TagEntity();
                tagEntity.setName(tagName);
                tagMapper.insert(tagEntity);
            }

            VideoTagEntity videoTagEntity = new VideoTagEntity();
            videoTagEntity.setVideoId(videoId);
            videoTagEntity.setTagId(tagEntity.getId());
            videoTagMapper.insert(videoTagEntity);
        }
    }

    /**
     * 查询某个视频的标签 ID 列表
     */
    private List<Long> getTagIdsByVideoId(Long videoId) {
        List<VideoTagEntity> videoTagList = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTagEntity>()
                        .eq(VideoTagEntity::getVideoId, videoId)
        );

        if (videoTagList == null || videoTagList.isEmpty()) {
            return Collections.emptyList();
        }

        return videoTagList.stream()
                .map(VideoTagEntity::getTagId)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, List<Long>> getTagIdsMapByVideoIds(Collection<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<VideoTagEntity> videoTagList = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTagEntity>()
                        .in(VideoTagEntity::getVideoId, videoIds)
        );

        if (videoTagList == null || videoTagList.isEmpty()) {
            return Collections.emptyMap();
        }

        return videoTagList.stream()
                .filter(item -> item.getVideoId() != null && item.getTagId() != null)
                .collect(Collectors.groupingBy(
                        VideoTagEntity::getVideoId,
                        Collectors.mapping(VideoTagEntity::getTagId, Collectors.toList())
                ));
    }

    private List<VideoEntity> recallVideosByTags(Set<Long> preferredTagIds,
                                                 Long currentUserId,
                                                 Set<Long> interactedVideoIds,
                                                 Integer limit) {
        if (preferredTagIds == null || preferredTagIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<VideoTagEntity> matchedVideoTags = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTagEntity>()
                        .in(VideoTagEntity::getTagId, preferredTagIds)
        );

        if (matchedVideoTags == null || matchedVideoTags.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> candidateVideoIds = matchedVideoTags.stream()
                .map(VideoTagEntity::getVideoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (candidateVideoIds.isEmpty()) {
            return Collections.emptyList();
        }

        return videoMapper.selectList(
                new LambdaQueryWrapper<VideoEntity>()
                        .eq(VideoEntity::getStatus, 1)
                        .ne(VideoEntity::getUserId, currentUserId)
                        .notIn(!interactedVideoIds.isEmpty(), VideoEntity::getId, interactedVideoIds)
                        .in(VideoEntity::getId, candidateVideoIds)
                        .orderByDesc(VideoEntity::getPublishTime)
                        .orderByDesc(VideoEntity::getId)
                        .last("limit " + limit)
        );
    }

    private List<VideoEntity> recallVideosByAuthors(Set<Long> preferredAuthorIds,
                                                    Long currentUserId,
                                                    Set<Long> interactedVideoIds,
                                                    Integer limit) {
        if (preferredAuthorIds == null || preferredAuthorIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<VideoEntity> authorVideos = videoMapper.selectPublishedVideosByAuthorIds(preferredAuthorIds, limit);

        if (authorVideos == null || authorVideos.isEmpty()) {
            return Collections.emptyList();
        }

        return authorVideos.stream()
                .filter(video -> !Objects.equals(video.getUserId(), currentUserId))
                .filter(video -> !interactedVideoIds.contains(video.getId()))
                .toList();
    }

    private List<VideoEntity> recallLatestVideos(Long currentUserId,
                                                 Set<Long> interactedVideoIds,
                                                 Integer limit) {
        return videoMapper.selectList(
                new LambdaQueryWrapper<VideoEntity>()
                        .eq(VideoEntity::getStatus, 1)
                        .ne(VideoEntity::getUserId, currentUserId)
                        .notIn(!interactedVideoIds.isEmpty(), VideoEntity::getId, interactedVideoIds)
                        .orderByDesc(VideoEntity::getPublishTime)
                        .orderByDesc(VideoEntity::getId)
                        .last("limit " + limit)
        );
    }

    private List<VideoEntity> mergeAndDistinctVideos(List<VideoEntity>... videoLists) {
        Map<Long, VideoEntity> videoMap = new LinkedHashMap<>();

        for (List<VideoEntity> list : videoLists) {
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (VideoEntity video : list) {
                if (video != null && video.getId() != null) {
                    videoMap.putIfAbsent(video.getId(), video);
                }
            }
        }

        return new ArrayList<>(videoMap.values());
    }

    /**
     *  查询视频标签名称列表
     */
    private List<String> getTagNamesByVideoId(Long videoId) {
        List<VideoTagEntity> videoTagList = videoTagMapper.selectList(
                new LambdaQueryWrapper<VideoTagEntity>()
                        .eq(VideoTagEntity::getVideoId, videoId)
        );

        if (videoTagList == null || videoTagList.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tagNames = new ArrayList<>();
        for (VideoTagEntity videoTag : videoTagList) {
            TagEntity tagEntity = tagMapper.selectById(videoTag.getTagId());
            if (tagEntity != null && StringUtils.hasText(tagEntity.getName())) {
                tagNames.add(tagEntity.getName());
            }
        }

        return tagNames;
    }

    private Integer normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    private Integer normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 30);
    }
}
