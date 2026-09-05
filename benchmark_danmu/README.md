# Danmu Async Persistence A/B Benchmark

该分支用于复现星芒弹幕链路的本地单机 A/B 测试。目标是验证：**把 MySQL 持久化从 WebSocket 实时处理链路中解耦后，会如何影响持续消息场景下的处理占用、消息送达和积压。**

这不是生产 SLA、公共网络测试、分布式 WebSocket 集群容量测试，也不是独立真实用户数量证明。文中的 connection 指已经成功建立的模拟 WebSocket Session。

## 严格对照链路

ASYNC_MQ：

```text
WebSocket receive
-> validate
-> broadcastToRoom
-> Redis recent cache
-> RocketMQ producer send
-> realtime processing returns
-> RocketMQ Consumer
-> MySQL
```

SYNC_DB：

```text
WebSocket receive
-> validate
-> broadcastToRoom
-> Redis recent cache
-> current WebSocket processing thread inserts MySQL
-> realtime processing returns
```

两组保持 WebSocket endpoint、JWT 握手、videoId、DTO 校验、广播顺序、Redis 缓存、消息格式和 MySQL 表一致，**只替换最后的持久化阶段**。

ASYNC_MQ 中数据库插入由 MQ Consumer 后续完成，因此相对于 WebSocket 实时链路是异步持久化；当前 Producer 使用 `convertAndSend`，Producer 与 Broker 的发送调用本身仍发生在实时处理线程中，不能描述为完全 fire-and-forget。

## Benchmark 消息隔离

测试消息统一使用：

```text
BM:<runId>:<sequence>
```

服务端指标只记录当前 active run 的消息。MySQL 和 Redis 清理也只匹配当前 `videoId + runId`，不会删除整个弹幕表或整个 Redis Key。

`runId` 不允许包含 `%`、`_` 或 `:`，避免 LIKE 模式和前缀边界产生歧义。

## 连接模型

Python 客户端先建立全部连接，再开始正式发送。

- 一个连接作为 sender。
- 其余连接与 sender 一起接收房间广播。
- 多个连接可以复用同一个测试 Token，因为服务端房间管理按 WebSocket sessionId 管理连接。
- 因此应写“模拟 WebSocket 连接”，不要写成“独立用户”。

连接结果单独记录：

```text
connectionSuccessRate = connectedConnections / requestedConnections
scenarioValid = connectedConnections == requestedConnections
```

只有 `scenarioValid=true` 的一轮才能直接按请求的连接数量描述。

## 消息送达指标

### Per-delivery latency

发送端在 `await websocket.send(...)` 前记录单调时钟；每个模拟连接收到匹配 `runId + sequence` 的广播后记录延迟。

一条源弹幕广播给 N 个已连接 Session，会产生 N 次应用层消息送达。

### All-clients latency

对于每条源弹幕，取所有预期连接收到该条消息的最大延迟；随后再对全部源弹幕统计 P50 / P95 / P99。

这个指标回答的是：

> 一条弹幕多久送达到本轮测试房间中的所有已连接 Session？

### Delivery rate

```text
expectedDeliveries = connectedConnections * messages
deliveryRatePct = receivedDeliveries / expectedDeliveries
```

它只描述本轮成功建立连接后的应用层接收情况，不代表生产环境可靠性保证。

## 服务端阶段指标

Benchmark 将单条弹幕服务端处理拆成：

```text
broadcast
Redis recent cache
persistence stage
whole server-side processing
```

代码中的 `handlerTotal` 是内部字段名，对外解释时应理解为“单条弹幕服务端处理耗时”。

ASYNC_MQ 的 `persistStage` 测的是 RocketMQ Producer 发送调用占用实时链路的时间；SYNC_DB 测的是当前线程执行 MySQL INSERT 的时间。

因此不能把 ASYNC_MQ 的 `persistStage` 理解成“数据库已经在这段时间内落库完成”。

## 持久化一致性

正式发送最后一条消息后，测试立即并行轮询 MySQL：

```text
persistedCount
expectedPersistedCount
persistenceComplete
persistenceExact
duplicatePersistedRows
persistenceDrainMs
```

其中：

- `persistenceComplete`：落库数量 >= 期望数量。
- `persistenceExact`：落库数量 == 期望数量。
- `duplicatePersistedRows`：超出期望数量的记录数。
- `persistenceDrainMs`：最后一条正式消息发送完成后，到首次观察到全部期望记录落库的时间。

## 清理边界

接口：

```text
POST   /benchmark/danmu/metrics/start
POST   /benchmark/danmu/metrics/stop
GET    /benchmark/danmu/persisted-count
DELETE /benchmark/danmu/data
```

MySQL 只删除：

```text
video_id = <videoId>
AND content LIKE 'BM:<runId>:%'
```

Redis 则读取近期弹幕 ZSet，解析 JSON，只移除 `content` 属于当前 `BM:<runId>:` 前缀的成员，不删除整个 Key。

## 安装

```powershell
python -m pip install -r .\requirements.txt
```

Token 只通过命令行传入：

```powershell
python .\danmu_benchmark.py `
  --access-token "<ACCESS_TOKEN>" `
  --video-id <VIDEO_ID> `
  --mode async `
  --connections 100 `
  --messages 1000 `
  --warmup-messages 10 `
  --interval-ms 20
```

同步写库对照只需要改：

```text
--mode sync
```

## 矩阵测试

`run_matrix.ps1` 使用交替模式顺序，例如：

```text
100: async -> sync
300: sync  -> async
500: async -> sync
```

用于减少固定执行顺序带来的 JVM、OS 缓存和资源状态偏差。

连接数、消息数和发送间隔可以根据本机能力调整。高负载进入排队区后，尾延迟会非线性增长；这时应同时检查服务端广播、Redis、持久化阶段，而不是把变化全部归因于 MQ。

当前房间广播实现会同步遍历 Session 并逐个 `sendMessage`，因此在连接数较大时，广播阶段本身可能成为瓶颈。

## 关于发送速率字段

脚本保留 `actualSendDurationMs` 和 `actualMessagesPerSecond` 作为诊断字段。`--interval-ms` 表示每次发送后的配置等待，并不应只凭一个速率字段证明稳定输入吞吐；使用该字段前应检查其与配置节奏、发送阻塞和本地事件循环行为是否一致。

Benchmark 的核心结论优先使用连接成功、消息送达、阶段耗时和持久化一致性等可直接核对的指标。

## 结果文件

脚本生成：

```text
benchmark_danmu/results/danmu_benchmark_results.csv
benchmark_danmu/results/danmu_benchmark_detail_<mode>_<connections>_<runId>.json
```

如已有 CSV Schema 与当前版本不一致，会保留旧文件并写入 `danmu_benchmark_results_v2.csv`。

`results/` 已加入 `.gitignore`。公开分支只保留测试代码、测试方法和复现说明，不提交原始结果。
