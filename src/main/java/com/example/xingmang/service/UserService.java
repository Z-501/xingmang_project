package com.example.xingmang.service;

import com.alibaba.fastjson.JSONObject;
import com.example.xingmang.model.vo.PageResult;
import com.example.xingmang.model.entity.User;
import com.example.xingmang.model.entity.UserInfo;

import java.util.Map;

public interface UserService {
    /**
     * 用户注册
     */
    void register(User user);

    /**
     * 用户登录 (返回包含 accessToken 和 refreshToken 的 Map)
     */
    Map<String, Object> login(String phone, String password);

    Map<String, Object> refresh(String refreshToken);

    /**
     * 登出接口
     */
    void logout(Long userId);

    /**
     * 分页查询
     */
    PageResult<UserInfo> pageListUserInfos(JSONObject params);
}
