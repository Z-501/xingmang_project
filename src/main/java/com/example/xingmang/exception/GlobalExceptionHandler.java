package com.example.xingmang.exception;

import com.example.xingmang.model.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常捕获器
 * 拦截所有 Controller 抛出的异常，并转为 Result 格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        if (e instanceof ConditionException) {
            Integer code = ((ConditionException) e).getCode();
            String msg = e.getMessage();
            return Result.error(code, msg);
        }
        log.error("Unhandled server exception", e);
        return Result.error(500, "服务器内部错误，请稍后再试");
    }
}