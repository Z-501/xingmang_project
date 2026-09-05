package com.example.xingmang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.xingmang.model.entity.UserFollowing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserFollowingMapper extends BaseMapper<UserFollowing> {

    @Select("""
            SELECT user_id
            FROM t_user_following
            WHERE following_id = #{userId}
            """)
    List<Long> selectFanIdsByFollowingId(@Param("userId") Long userId);
}
