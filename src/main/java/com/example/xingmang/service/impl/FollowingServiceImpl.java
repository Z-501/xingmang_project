package com.example.xingmang.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.FollowingGroupMapper;
import com.example.xingmang.mapper.UserFollowingMapper;
import com.example.xingmang.mapper.UserInfoMapper;
import com.example.xingmang.mapper.UserMapper;
import com.example.xingmang.model.entity.FollowingGroup;
import com.example.xingmang.model.entity.User;
import com.example.xingmang.model.entity.UserFollowing;
import com.example.xingmang.model.entity.UserInfo;
import com.example.xingmang.model.vo.FollowingVo;
import com.example.xingmang.service.FollowingService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注关系、粉丝列表与关注分组业务实现
 */
@Service
public class FollowingServiceImpl implements FollowingService {

    @Autowired
    private UserFollowingMapper userFollowingMapper;

    @Autowired
    private FollowingGroupMapper followingGroupMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 新增或更新关注关系。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFollowing(UserFollowing userFollowing) {
        // 不能关注当前登录用户自己。
        if (userFollowing.getUserId().equals(userFollowing.getFollowingId())) {
            throw new ConditionException(400, "提示：你不能关注你自己");
        }

        // 校验被关注用户是否存在。
        User targetUser = userMapper.selectById(userFollowing.getFollowingId());
        if (targetUser == null) {
            throw new ConditionException(400, "关注的用户不存在！");
        }

        // 删除已有关系，保证重复关注或切换分组时只有一条有效记录。
        QueryWrapper<UserFollowing> queryWrapper = new QueryWrapper<>();
        // 当前用户 ID
        queryWrapper.eq("user_Id", userFollowing.getUserId());
        // 被关注用户 ID
        queryWrapper.eq("following_Id", userFollowing.getFollowingId());
        // 清理旧关系后重新写入。
        userFollowingMapper.delete(queryWrapper);

        // 未指定分组时归入默认关注分组。
        // 关注分组类型 0特别关注 1悄悄关注 2默认分组 3用户自定义分组
        if (userFollowing.getGroupId() == null) {
            // type=2 表示默认分组。
            FollowingGroup defaultGroup = followingGroupMapper.selectOne(
                    new QueryWrapper<FollowingGroup>()
                            .eq("user_Id", userFollowing.getUserId())
                            .eq("type", "2")
            );
            if (defaultGroup != null) {
                userFollowing.setGroupId(defaultGroup.getId());
            } else {
                throw new ConditionException(500, "未找到默认关注分组，请联系系统管理员");
            }
        }

        // 写入新的关注关系。
        userFollowing.setCreateTime(LocalDateTime.now());
        userFollowingMapper.insert(userFollowing);
    }

    /**
     * 获取关注列表
     */
    @Override
    public List<FollowingGroup> getUserFollowings(Long userId) {
        // 查询当前用户的关注关系。
        List<UserFollowing> followingList = userFollowingMapper.selectList(
                new QueryWrapper<UserFollowing>().eq("user_Id", userId)
        );

        // 批量查询被关注用户资料，避免循环查询。
        Set<Long> followingIdSet = followingList.stream()
                .map(UserFollowing::getFollowingId)
                .collect(Collectors.toSet());
        List<UserInfo> userInfoList = new ArrayList<>();
        if (!followingIdSet.isEmpty()) {
            userInfoList = userInfoMapper.selectList(
                    new QueryWrapper<UserInfo>().in("user_Id", followingIdSet)
            );
        }
        // 转换为 Map 以便 O(1) 组装资料。
        Map<Long, UserInfo> userInfoMap = userInfoList.stream()
                .collect(Collectors.toMap(UserInfo::getUserId, userInfo -> userInfo));

        // 查询粉丝集合，用于判断互关状态。
        List<UserFollowing> fanList = userFollowingMapper.selectList(
                new QueryWrapper<UserFollowing>().eq("following_Id", userId)
        );
        // 粉丝 ID 去重后用于快速判断。
        Set<Long> fanIdSet = fanList.stream().map(UserFollowing::getUserId).collect(Collectors.toSet());

        // 组装关注关系与用户资料。
        List<FollowingVo> followingVoList = new ArrayList<>();
        for (UserFollowing rel : followingList) {
            FollowingVo vo = new FollowingVo();
            BeanUtils.copyProperties(rel, vo);
            UserInfo userInfo = userInfoMap.get(rel.getFollowingId());
            if (userInfo != null) {
                // 标记互关状态。
                userInfo.setFollowed(fanIdSet.contains(rel.getFollowingId()));
                vo.setUserInfo(userInfo);
            }
            followingVoList.add(vo);
        }

        // 按关注分组组织返回结构。
        List<FollowingGroup> groupList = followingGroupMapper.selectList(
                new QueryWrapper<FollowingGroup>().eq("user_Id", userId)
        );
        // 额外构造“全部关注”分组，便于客户端统一展示。
        FollowingGroup allGroup = new FollowingGroup();
        allGroup.setName("全部关注");
        allGroup.setFollowingVoList(followingVoList);
        // 将关注用户归入对应分组。
        for (FollowingGroup group : groupList) {
            List<FollowingVo> infoList = new ArrayList<>();
            for (FollowingVo vo : followingVoList) {
                if (group.getId().equals(vo.getGroupId())) {
                    infoList.add(vo);
                }
            }
            group.setFollowingVoList(infoList);
        }

        // 返回包含“全部关注”和各业务分组的结果。
        List<FollowingGroup> result = new ArrayList<>();
        result.add(allGroup);
        result.addAll(groupList);
        return result;
    }

    /**
     * 获取粉丝列表
     */
    @Override
    public List<UserFollowing> getUserFans(Long userId) {
        // 查询当前用户的粉丝关系。
        List<UserFollowing> fanList = userFollowingMapper.selectList(
                new QueryWrapper<UserFollowing>().eq("following_Id", userId)
        );

        // 批量查询粉丝资料。
        Set<Long> fanIdSet = fanList.stream()
                .map(UserFollowing::getUserId)
                .collect(Collectors.toSet());

        List<UserInfo> userInfoList = new ArrayList<>();
        if (!fanIdSet.isEmpty()) {
            userInfoList = userInfoMapper.selectList(
                    new QueryWrapper<UserInfo>().in("user_Id", fanIdSet)
            );
        }
        // 转换为 Map 以便组装结果。
        Map<Long, UserInfo> userInfoMap = userInfoList.stream()
                .collect(Collectors.toMap(UserInfo::getUserId, userInfo -> userInfo));

        // 查询当前用户的关注集合，用于判断是否互关。
        List<UserFollowing> myFollowingList = userFollowingMapper.selectList(
                new QueryWrapper<UserFollowing>().eq("user_Id", userId)
        );
        // 使用 Set 提升互关判断效率。
        Set<Long> myFollowingIdSet = myFollowingList.stream()
                .map(UserFollowing::getFollowingId)
                .collect(Collectors.toSet());

        // 组装粉丝资料与互关状态。
        for (UserFollowing fan : fanList) {
            UserInfo userInfo = userInfoMap.get(fan.getUserId());
            if (userInfo != null) {
                // 当前用户也关注该粉丝时，标记为互关。
                userInfo.setFollowed(myFollowingIdSet.contains(fan.getUserId()));
                fan.setUserInfo(userInfo);
            }
        }
        return fanList;
    }

    /**
     * 获取关注分组
     */
    @Override
    public List<FollowingGroup> getFollowingGroups(Long userId) {
        return followingGroupMapper.selectList(
                new QueryWrapper<FollowingGroup>().eq("user_Id", userId)
        );
    }

    /**
     * 创建用户自定义关注分组
     */
    @Override
    public Long addUserFollowingGroups(FollowingGroup followingGroup) {
        // 设置分组创建时间。
        followingGroup.setCreateTime(LocalDateTime.now());
        // type=3 表示用户自定义分组。
        followingGroup.setType("3");

        // 业务校验：同一个用户下不能有同名的分组
        FollowingGroup oldGroup = followingGroupMapper.selectOne(
                new QueryWrapper<FollowingGroup>()
                        .eq("user_Id", followingGroup.getUserId())
                        .eq("name", followingGroup.getName())
        );
        if (oldGroup != null) {
            throw new ConditionException("分组名称已存在！");
        }

        // 写入分组并返回主键。
        followingGroupMapper.insert(followingGroup);
        return followingGroup.getId();
    }

    /**
     * 删除用户自定义关注分组。
     *
     * @param groupId 分组 ID
     * @param userId 当前用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFollowingGroup(Long groupId, Long userId) {
        // 校验分组存在且归属当前用户。
        FollowingGroup group = followingGroupMapper.selectOne(
                new QueryWrapper<FollowingGroup>()
                        .eq("id", groupId)
                        .eq("user_Id", userId)
        );
        if (group == null) {
            throw new ConditionException("分组不存在！");
        }

        // 系统默认分组不允许删除。
        if ("2".equals(group.getType())) {
            throw new ConditionException("系统默认分组不可删除！");
        }

        // 查询默认分组，用于承接被删除分组下的关注关系。
        FollowingGroup defaultGroup = followingGroupMapper.selectOne(
                new QueryWrapper<FollowingGroup>()
                        .eq("user_Id", userId)
                        .eq("type", "2")
        );

        // 删除前先迁移关注关系，避免产生无分组数据。
        if (defaultGroup != null) {
            UserFollowing updateRelation = new UserFollowing();
            updateRelation.setGroupId(defaultGroup.getId());

            userFollowingMapper.update(updateRelation,
                    new UpdateWrapper<UserFollowing>()
                            .eq("user_Id", userId)
                            .eq("group_Id", groupId)
            );
        }

        // 删除分组记录。
        followingGroupMapper.deleteById(groupId);
    }

}