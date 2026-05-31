package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.xingmang.model.vo.FollowingVo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("t_following_group")
public class FollowingGroup {
    @TableId(type = IdType.AUTO)
    private Long id;           // 主键 id
    private Long userId;       // 所属用户 userId
    private String name;       // 分组名 name
    private String type;       // 分组类型 type
    private LocalDateTime createTime; // 创建时间
    private LocalDateTime updateTime; // 更新时间

    // 使用 exist = false 告知 MyBatis-Plus 忽略此字段映射
    @TableField(exist = false)
    private List<FollowingVo> followingVoList;
}
