package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user_following")
public class UserFollowing {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("following_id")
    private Long followingId;

    @TableField("group_id")
    private Long groupId;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField(exist = false)
    private UserInfo userInfo;
}
