package com.example.xingmang.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.xingmang.exception.ConditionException;
import com.example.xingmang.mapper.FollowingGroupMapper;
import com.example.xingmang.mapper.UserFollowingMapper;
import com.example.xingmang.mapper.UserInfoMapper;
import com.example.xingmang.mapper.UserMapper;
import com.example.xingmang.model.entity.FollowingGroup;
import com.example.xingmang.model.entity.User;
import com.example.xingmang.model.entity.UserFollowing;
import com.example.xingmang.model.entity.UserInfo;
import com.example.xingmang.model.vo.PageResult;
import com.example.xingmang.service.UserService;
import com.example.xingmang.util.BCryptUtil;
import com.example.xingmang.util.JwtUtil;
import com.example.xingmang.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * User account service implementation.
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private FollowingGroupMapper followingGroupMapper;

    @Autowired
    private UserFollowingMapper userFollowingMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;
    private static final String REDIS_REFRESH_TOKEN_KEY = "refresh_token:";

    /**
     * 用户注册逻辑：支持 BCrypt 加密 + 双表联动
     * @Transactional: 开启事务。保证 t_user 和 t_user_info 要么同时成功，要么同时失败回滚。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(User user) {
        String phone = user.getPhone();

        // 1. 业务校验：手机号非空与格式校验（使用简单正则）
        if (!StringUtils.hasText(phone) || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new ConditionException(400, "手机号格式不正确");
        }

        // 2. 业务校验：手机号查重 (利用 MyBatis-Plus 的 QueryWrapper)
        User existingUser = userMapper.selectOne(new QueryWrapper<User>().eq("phone", phone));
        if (existingUser != null) {
            throw new ConditionException(400, "该手机号已被注册");
        }

        // 3. 安全逻辑：使用 BCrypt 密码加密
        if (!StringUtils.hasText(user.getPassword())) {
            throw new ConditionException(400, "密码不能为空");
        }
        // 向数据库写入用户密码和创建时间
        user.setPassword(BCryptUtil.encode(user.getPassword()));
        user.setCreateTime(LocalDateTime.now());

        // 4. 数据持久化：插入主表 t_user
        // MyBatis-Plus 插入成功后，会自动将生成的自增 id 回写到 user 对象中
        userMapper.insert(user);

        // 5. 双表联动：初始化用户详情 t_user_info
        UserInfo userInfo = new UserInfo();
        // 拿到刚刚生成的 userId
        userInfo.setUserId(user.getId());
        // 随机生成一个默认昵称
        userInfo.setNick("星芒用户_" + System.currentTimeMillis());
        // 设置默认简介
        userInfo.setSign("欢迎来到星芒社区");
        // 默认性别：未知
        userInfo.setGender("2");
        userInfo.setCreateTime(LocalDateTime.now());
        userInfoMapper.insert(userInfo);

        // 6. 初始化默认关注分组 (t_following_group)
        FollowingGroup defaultGroup = new FollowingGroup();
        defaultGroup.setUserId(user.getId());
        defaultGroup.setName("默认分组");
        defaultGroup.setType("2");
        defaultGroup.setCreateTime(LocalDateTime.now());
        followingGroupMapper.insert(defaultGroup);
    }

    /**
     * 用户登录逻辑
     */
    @Override
    public Map<String, Object> login(String phone, String password) {
        // 1. 业务校验：参数非空
        if (!StringUtils.hasText(phone) || !StringUtils.hasText(password)) {
            throw new ConditionException(400, "手机号或密码不能为空");
        }

        // 2. 存在性校验：通过手机号查询用户
        User dbUser = userMapper.selectOne(new QueryWrapper<User>().eq("phone", phone));
        if (dbUser == null) {
            throw new ConditionException(400, "用户不存在或手机号错误");
        }

        // 3. 安全校验：BCrypt 密码比对
        // 注意顺序：前端传来的明文 password 在前，数据库查出的密文 dbUser.getPassword() 在后
        if (!BCryptUtil.matches(password, dbUser.getPassword())) {
            throw new ConditionException(400, "密码错误");
        }

        // 4. 生成双 Token 机制
        String accessToken = JwtUtil.createAccessToken(dbUser.getId());
        String refreshToken = JwtUtil.createRefreshToken(dbUser.getId());
        // 将 RefreshToken 存入 Redis，Key 为 "refresh_token:用户ID"，实现登录状态保持
        // 设置 7 天过期，与 JWT 载荷中的过期时间保持同步
        try {
            redisTemplate.opsForValue().set(
                    REDIS_REFRESH_TOKEN_KEY + dbUser.getId(),
                    refreshToken,
                    7, TimeUnit.DAYS
            );
        } catch (Exception e) {
            log.warn("Failed to persist refresh token for userId={}", dbUser.getId(), e);
        }

        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);
        return tokenMap;
    }

    /**
     * 无感刷新 Token 逻辑
     */
    @Override
    public Map<String, Object> refresh(String refreshToken) {
        // 1. 解析 RefreshToken 拿到 user_Id
        Long user_Id = JwtUtil.parseToken(refreshToken);
        if (user_Id == null) {
            throw new ConditionException(401, "刷新令牌已失效，请重新登录");
        }

        // 2. 校验 Redis 中是否存在该用户的 RefreshToken
        String redisToken = redisTemplate.opsForValue().get(REDIS_REFRESH_TOKEN_KEY + user_Id);
        if (!refreshToken.equals(redisToken)) {
            throw new ConditionException(401, "令牌不一致，请重新登录");
        }

        // 3. 校验通过，生成一对新的 Token（实现无感续期）
        String newAccess = JwtUtil.createAccessToken(user_Id);
        String newRefresh = JwtUtil.createRefreshToken(user_Id);

        // 4. 更新 Redis 中的长效 Token
        redisTemplate.opsForValue().set(REDIS_REFRESH_TOKEN_KEY + user_Id, newRefresh, 7, TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", newAccess);
        result.put("refreshToken", newRefresh);
        return result;
    }

    /**
     * 用户登出逻辑
     * 此处不需要手动处理 ThreadLocal，因为拦截器的 afterCompletion 已经处理了
     * @param user_Id 当前登录用户的ID
     */
    @Override
    public void logout(Long user_Id) {
        // 构造存储在 Redis 中的 Key
        String key = REDIS_REFRESH_TOKEN_KEY + user_Id;

        // 从 Redis 中删除该 Key，使用户无法再进行 Token 刷新
        redisTemplate.delete(key);
    }

    /**
     * 分页查询功能
     */
    @Override
    public PageResult<UserInfo> pageListUserInfos(JSONObject params) {
        // 1. 获取参数
        Integer no = params.getInteger("no");
        Integer size = params.getInteger("size");
        String nick = params.getString("nick");
        Long currentUserId = UserContext.getCurrentUserId();

        // 2. 执行分页查询
        Page<UserInfo> page = new Page<>(no, size);
        QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
        if (nick != null && !nick.isEmpty()) {
            queryWrapper.like("nick", nick);
        }
        IPage<UserInfo> resultPage = userInfoMapper.selectPage(page, queryWrapper);
        List<UserInfo> userInfoList = resultPage.getRecords();

        // 3. 关注状态校验，如果没有被关注，才可以关注
        if (resultPage.getTotal() > 0 && currentUserId != null) {
            // 提取搜索结果中所有博主的 ID
            Set<Long> targetUserIds = userInfoList.stream()
                    .map(UserInfo::getUserId)
                    .collect(Collectors.toSet());

            // 直接查数据库：当前用户关注了哪些目标博主
            List<UserFollowing> followingList = userFollowingMapper.selectList(
                    new QueryWrapper<UserFollowing>()
                            .eq("userId", currentUserId)
                            .in("followingId", targetUserIds)
            );
            // 转换成 Set 方便对比
            Set<Long> followedIdSet = followingList.stream()
                    .map(UserFollowing::getFollowingId)
                    .collect(Collectors.toSet());
            // 回填状态
            for (UserInfo userInfo : userInfoList) {
                userInfo.setFollowed(followedIdSet.contains(userInfo.getUserId()));
            }
        }
        return new PageResult<>(resultPage.getTotal(), userInfoList);
    }
}

