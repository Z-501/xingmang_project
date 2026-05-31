package com.example.xingmang.controller;

import com.example.xingmang.model.entity.UserMoment;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.service.UserMomentsService;
import com.example.xingmang.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * UserMomentsController 是动态模块的接口层控制器
 * 负责对外暴露“发布动态”和“查询动态时间线”两个 RESTful 接口。
 * 该类本质上承担的是请求接入职责：
 * 负责接收前端请求、获取当前登录用户信息、做基础登录状态校验
 * 将具体业务下沉到 Service 层处理，不直接承担数据库操作、消息发送或缓存查询等核心逻辑。
 */
@RestController
public class UserMomentsController {
    @Autowired
    private UserMomentsService userMomentsService;

    /**
     * 发布动态
     * 使用 POST 请求，并通过 RequestBody 接收 JSON 数据
     */
    @PostMapping("/user-moments")
    public Result<String> addUserMoments(@RequestBody UserMoment userMoment) {
        // 1. 获取当前登录用户 ID
        Long userId = UserContext.getCurrentUserId();
        // 如果拦截器没拦住（配置漏了），这里也能及时阻断
        if (userId == null) {
            return Result.error("用户未登录或登录已过期");
        }
        userMoment.setUserId(userId);

        // 2. 调用 Service 执行“落库 + 事务后置 MQ 发送”逻辑
        userMomentsService.addUserMoments(userMoment);

        // 3. 返回统一成功响应
        return Result.success("发布成功");
    }

    /**
     * 查询用户关注的动态（时间线/Feed流）
     * @param lastTime 上一次查询最后一条动态的时间戳（第一次传 null 或当前时间）
     * @param pageSize 每页拉取数量
     */
    @GetMapping("/user-moments")
    public Result<List<UserMoment>> getUserMoments(@RequestParam(required = false) Long lastTime,
                                                   @RequestParam Integer pageSize) {
        // 1. 获取当前登录用户 ID
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error("用户未登录");
        }

        // 2. 调用 Service 层进行推拉结合的查询
        List<UserMoment> list = userMomentsService.getUserMoments(userId, pageSize, lastTime);

        return Result.success(list);
    }
}
