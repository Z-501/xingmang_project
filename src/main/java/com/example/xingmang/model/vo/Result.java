package com.example.xingmang.model.vo;

/**
 * 通用接口层
 * 统一接口返回结果 Record
 * @author Zjx
 * @param <T> 响应数据的泛型类型
 */
public record Result<T>(
        Integer code,    // 状态码：200-成功，500-失败等
        String msg,      // 提示信息
        T data           // 具体数据内容
) {
    /** 快捷成功响应：不带数据 **/
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 快捷成功响应：带数据 **/
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 快捷失败响应：默认错误码 500 **/
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /** 快捷失败响应：自定义错误码 **/
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}