package com.example.xingmang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.xingmang.model.entity.UserMoment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户动态持久层接口
 * 继承 BaseMapper 即可获得全套 CRUD 能力
 */
@Mapper
public interface UserMomentsMapper extends BaseMapper<UserMoment> {
    // 严谨点：如果后续需要复杂的 SQL（如：根据关注列表关联查询动态详情），
    // 可以在这里定义方法并在 XML 中实现。
}