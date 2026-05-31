package com.example.xingmang.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 密码加密工具类
 * 现代方案：加密后的字符串已包含盐值，无需额外存储 salt 字段
 */
public class BCryptUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对原始密码进行加密
     * @param rawPassword 明文密码
     * @return 加密后的密文（长度约 60 字符）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与密文是否匹配
     * @param rawPassword 明文密码
     * @param encodedPassword 数据库存储的密文
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
