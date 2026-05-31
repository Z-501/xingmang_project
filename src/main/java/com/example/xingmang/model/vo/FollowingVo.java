package com.example.xingmang.model.vo;

import com.example.xingmang.model.entity.UserInfo;
import lombok.Data;

@Data
public class FollowingVo {
    // 基础关注信息
    private Long id;
    private Long userId;
    private Long followingId;
    private Long groupId;

    // 冗余博主详细信息
    private UserInfo userInfo;
}
