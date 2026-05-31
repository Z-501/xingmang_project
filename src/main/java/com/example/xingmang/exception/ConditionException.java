package com.example.xingmang.exception;

/**
 * 业务异常类：用于手动抛出我们可以预见的错误
 */
public class ConditionException extends RuntimeException {
    private final Integer code;

    public ConditionException(Integer code, String name) {
        super(name);
        this.code = code;
    }

    public ConditionException(String name) {
        super(name);
        // 默认业务错误码
        this.code = 500;
    }

    public Integer getCode() {
        return code;
    }
}
