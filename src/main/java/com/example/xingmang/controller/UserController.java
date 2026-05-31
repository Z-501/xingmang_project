package com.example.xingmang.controller;

import com.alibaba.fastjson.JSONObject;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.model.entity.User;
import com.example.xingmang.model.entity.UserInfo;
import com.example.xingmang.model.dto.UserDTO;
import com.example.xingmang.model.vo.PageResult;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.service.UserService;
import com.example.xingmang.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户认证与用户检索接口。
 *
 * <p>Controller 层只负责请求入口、基础参数接收与统一响应封装，
 * 具体认证、Token 刷新和登出逻辑由 Service 层完成。</p>
 */
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册接口
     * 接收手机号和密码，完成双表初始化
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody UserDTO userRequest) {
        User user = new User();
        user.setPhone(userRequest.getPhone());
        user.setPassword(userRequest.getPassword());

        userService.register(user);
        return Result.success("注册成功");
    }

    /**
     * 用户登录接口
     * 验证 BCrypt 密码并下发双 Token
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody UserDTO userRequest) {
        Map<String, Object> tokens = userService.login(
                userRequest.getPhone(),
                userRequest.getPassword()
        );
        return Result.success(tokens);
    }

    /**
     * 刷新 Token 接口
     * AccessToken 过期后，客户端可通过 RefreshToken 获取新的令牌对
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (!StringUtils.hasText(refreshToken)) {
            throw new ConditionException(400, "刷新令牌不能为空");
        }

        // 校验 Redis 中的刷新令牌，并生成新的令牌对。
        Map<String, Object> newTokens = userService.refresh(refreshToken);
        return Result.success(newTokens);
    }

    /**
     * 用户登出接口
     * 必须通过登录拦截器校验，从 UserContext 获取 userId
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        // 从登录拦截器写入的线程上下文中获取当前用户 ID。
        Long userId = UserContext.getCurrentUserId();

        // 使当前用户的刷新令牌失效。
        userService.logout(userId);

        return Result.success("登出成功");
    }

    /**
     * 分页查询用户（搜索功能）
     * 根据条件分页检索用户信息
     */
    @PostMapping("/search")
    public Result<PageResult<UserInfo>> pageListUserInfos(@RequestBody JSONObject params) {
        PageResult<UserInfo> result = userService.pageListUserInfos(params);
        return Result.success(result);
    }
}