package com.example.xingmang.model.dto;

import lombok.Data;

/**
 * 专门用于接收前端注册和登录请求的 DTO
 */
@Data
public class UserDTO {
    private String phone;
    private String password;
}
