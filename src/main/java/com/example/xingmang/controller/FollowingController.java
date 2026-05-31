package com.example.xingmang.controller;

import com.example.xingmang.model.entity.FollowingGroup;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.model.entity.UserFollowing;
import com.example.xingmang.service.FollowingService;
import com.example.xingmang.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/follow")
public class FollowingController {

    @Autowired
    private FollowingService followingService;
    /**
     * 添加/更新关注
     * POST /follow/add
     */
    @PostMapping("/add")
    public Result<String> addFollowing(@RequestBody UserFollowing userFollowing) {
        // 从 ThreadLocal 中获取当前登录用户的 ID
        Long user_Id = UserContext.getCurrentUserId();
        userFollowing.setUserId(user_Id);
        followingService.addFollowing(userFollowing);

        return Result.success("关注成功");
    }

    /**
     * 获取当前用户的分组关注列表
     * GET /follow/list
     */
    @GetMapping("/list")
    public Result<List<FollowingGroup>> getUserFollowings() {
        // 获取当前登录用户 ID
        Long userId = UserContext.getCurrentUserId();
        // 调用聚合逻辑（含博主信息、互粉状态、分组归类）
        List<FollowingGroup> result = followingService.getUserFollowings(userId);

        return Result.success(result);
    }

    /**
     * 获取当前用户的粉丝列表
     * GET /follow/fans
     */
    @GetMapping("/fans")
    public Result<List<UserFollowing>> getUserFans() {
        // 从工具类获取当前登录用户 ID
        Long user_Id = UserContext.getCurrentUserId();

        // 调用 Service 层获取带有用户信息和回粉状态的粉丝列表
        List<UserFollowing> result = followingService.getUserFans(user_Id);

        return Result.success(result);
    }

    /**
     * 获取用户所有的关注分组
     * GET /follow/groups
     */
    @GetMapping("/groups")
    public Result<List<FollowingGroup>> getFollowingGroups() {
        Long user_Id = UserContext.getCurrentUserId();
        List<FollowingGroup> list = followingService.getFollowingGroups(user_Id);
        return Result.success(list);
    }

    /**
     * 添加自定义关注分组
     * POST /follow/groups
     */
    @PostMapping("/groups")
    public Result<Long> addUserFollowingGroups(@RequestBody FollowingGroup followingGroup) {
        Long user_Id = UserContext.getCurrentUserId();
        followingGroup.setUserId(user_Id);
        Long group_Id = followingService.addUserFollowingGroups(followingGroup);
        return Result.success(group_Id);
    }

    /**
     * 删除自定义关注分组
     * DELETE /follow/groups/{groupId}
     */
    @DeleteMapping("/groups/{group_Id}")
    public Result<String> deleteFollowingGroup(@PathVariable Long group_Id) {
        Long user_Id = UserContext.getCurrentUserId();

        followingService.deleteFollowingGroup(group_Id, user_Id);

        return Result.success("分组删除成功，组内成员已移至默认分组");
    }
}