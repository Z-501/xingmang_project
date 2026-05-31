package com.example.xingmang.util;

/**
 * 利用 ThreadLocal 保证每个请求线程都有独立的存储空间
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_HOLDER.set(userId);
    }

    public static Long getCurrentUserId() {
        return USER_HOLDER.get();
    }

    public static void remove() {
        USER_HOLDER.remove();
    }
}