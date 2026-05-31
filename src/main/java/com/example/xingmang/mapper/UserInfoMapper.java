package com.example.xingmang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.xingmang.model.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
/**
 继承 BaseMapper,自动拥有了增删改查的所有能力
*/
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}