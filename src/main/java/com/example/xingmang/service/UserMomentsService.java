package com.example.xingmang.service;

import com.example.xingmang.model.entity.UserMoment;
import java.util.List;

/**
 * 用户动态业务接口
 */
public interface UserMomentsService {

    /**
     * 发布/添加用户动态
     * 核心逻辑：1.落库 2.异步发MQ推送给粉丝
     * @param userMoment 动态实体
     */
    void addUserMoments(UserMoment userMoment);

    /**
     * 分页查询用户关注的动态 （Feed流）
     * @param userId 当前登录用户ID
     * @param size 每页记录数
     * @param lastTime 上一次查询最后一条动态的时间戳（毫秒）
     * @return 动态列表
     */
    List<UserMoment> getUserMoments(Long userId, Integer size, Long lastTime);
}
