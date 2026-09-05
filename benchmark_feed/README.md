# Feed Fan-out Benchmark

该分支用于复现星芒 Feed 分发链路的本地工程测试。目标不是证明生产容量，而是验证发布链路与粉丝分发是否解耦，并观察粉丝规模增长后 Redis Timeline 更新的变化。

## 测试链路

```text
POST /user-moments
  -> MySQL INSERT
  -> transaction commit
  -> RocketMQ
  -> MomentsConsumer
  -> getFanIds(authorId)
  -> Redis ZSet Timeline
```

正式业务代码只为每个粉丝维护近期动态 ID 的 ZSet 时间线；完整动态内容仍以 MySQL 为数据源。

## 主要指标

- **Publish latency**：客户端调用 `POST /user-moments` 到接口返回的时间，统计 P50 / P95 / P99。
- **Fan-out latency**：接口返回后，到目标粉丝 Timeline 全部出现本轮 `momentId` 的时间。
- **Total request-to-fanout**：从发布请求开始，到全部目标 Timeline 更新完成的总时间。
- **Correctness**：每轮验证 `visible_followers == followers`，避免只比较耗时而忽略漏分发。

百分位采用线性插值。测试结果只代表当前本地环境，不应解释为生产 SLA。

## 环境与依赖

典型本地环境：

```text
Spring Boot  127.0.0.1:8082
MySQL       127.0.0.1:3306
Redis       127.0.0.1:6379
RocketMQ    127.0.0.1:9876
```

安装依赖：

```powershell
python -m pip install -r .\requirements.txt
```

所有密码和 Token 都通过命令行参数传入，不应写入仓库。

## 生成合成粉丝

仅在本地或专用 Benchmark 数据库使用：

```powershell
python .\prepare_followers.py `
  --author-id <AUTHOR_ID> `
  --followers 20000 `
  --mysql-password "<MYSQL_PASSWORD>"
```

脚本会创建 synthetic `t_user`、`t_user_info` 和 `t_user_following` 数据，并在 `results/feed_benchmark_manifest.json` 记录本轮创建的用户 ID，供后续精确清理。

如果本地 `t_user_following.group_id` 为 NOT NULL，脚本会主动停止，避免在未知表约束下生成脏数据。

## 单规模测试

```powershell
python .\feed_benchmark.py `
  --access-token "<ACCESS_TOKEN>" `
  --author-id <AUTHOR_ID> `
  --mysql-password "<MYSQL_PASSWORD>" `
  --followers 20000 `
  --rounds 20 `
  --poll-interval-ms 100
```

`--author-id` 必须与 `accessToken` 解析得到的登录用户一致。

## 多规模矩阵

```powershell
.\run_matrix.ps1 `
  -AccessToken "<ACCESS_TOKEN>" `
  -AuthorId <AUTHOR_ID> `
  -MySqlPassword "<MYSQL_PASSWORD>"
```

默认测试 1k / 5k / 10k / 20k 粉丝规模。

## Pipeline 与逐条 Redis 写入对照

业务版本使用：

```java
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    // 相同 ZADD + ZREMRANGE 循环
});
```

严格对照组仅将外层调用改为：

```java
redisTemplate.execute((RedisCallback<Object>) connection -> {
    // 相同 ZADD + ZREMRANGE 循环
});
```

其余条件保持一致：粉丝 ID 查询、Redis Key、ZADD、ZREMRANGE、Timeline 保留数量、MQ 消费方式和线程模型均不改变。该对照用于观察 Pipeline 减少客户端与 Redis 重复往返的效果，而不是并行执行 Redis 命令。

## Observer Effect

完整正确性验证会轮询每个目标粉丝的 ZSet。对于较慢的逐条 Redis 写入场景，频繁轮询本身可能产生大量 Redis 读请求并与写入竞争，因此会放大观测到的总耗时。

因此：

- 端到端正确性验证适合证明“所有粉丝最终都收到 Timeline 更新”。
- 若要做更干净的 Redis Pipeline / no-Pipeline 性能对照，应降低轮询频率，或增加单一完成标记后再做一次完整正确性扫描。
- 不应把受到观察者效应影响的倍数直接写成生产性能提升。

## 清理

```powershell
python .\cleanup_followers.py `
  --mysql-password "<MYSQL_PASSWORD>"
```

脚本只读取当前 manifest 中记录的 synthetic user ID，并删除对应关注关系、用户资料、用户记录以及 `moments:timeline:{fanId}`。

## 结果文件

测试脚本会在 `benchmark_feed/results/` 生成 CSV / JSON / manifest 等产物。该目录已加入 `.gitignore`，公开仓库只保留 `.gitkeep`，不提交原始测试结果。
