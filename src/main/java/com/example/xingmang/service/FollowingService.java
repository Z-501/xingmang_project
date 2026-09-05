package com.example.xingmang.service;

import com.example.xingmang.model.entity.FollowingGroup;
import com.example.xingmang.model.entity.UserFollowing;
import java.util.List;

public interface FollowingService {

    /** 添加关注 */
    void addFollowing(UserFollowing userFollowing);

    /** 获取用户的关注列表（可按分组过滤）*/
    List<FollowingGroup> getUserFollowings(Long userId);

    /** 获取用户的粉丝列表 */
    List<UserFollowing> getUserFans(Long userId);

    /**
     * 获取用户的粉丝 ID 列表。
     * 用于内部批量分发，仅查询粉丝 ID，不加载用户资料和互关状态。
     */
    List<Long> getFanIds(Long userId);

    /** 用户自定义分组 */
    Long addUserFollowingGroups(FollowingGroup followingGroup);

    /** 获取当前用户的所有关注分组 */
    List<FollowingGroup> getFollowingGroups(Long userId);

    /** 删除当前用户分组 */
    void deleteFollowingGroup(Long groupId, Long userId);
}
