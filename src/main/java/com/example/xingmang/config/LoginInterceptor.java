package com.example.xingmang.config;

import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.util.JwtUtil;
import com.example.xingmang.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. 从 Header 获取 AccessToken
        String token = request.getHeader("accessToken");

        // 2. 解析 Token
        Long userId = JwtUtil.parseToken(token);
        if (userId == null) {
            // Token 无效或过期，抛出自定义异常，由全局异常处理器接管
            throw new ConditionException(401, "用户信息已过期，请重新登录");
        }

        // 3. 存入上下文，方便后续使用
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束后必须清理 ThreadLocal，防止内存泄漏
        UserContext.remove();
    }
}
