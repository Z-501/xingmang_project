# Upload Relay A/B Benchmark

该分支用于复现大文件上传链路的本地 A/B 测试，核心问题是：**业务服务器是否需要承担大文件数据面的接收与转发。**

## 对照链路

Relay：

```text
Client -> Spring Boot -> MinIO
```

Direct：

```text
Client -> MinIO
          ^
          |
     Presigned URL
          |
     Spring Boot
```

Direct 模式下，Spring Boot 只负责鉴权、生成 Presigned URL 和少量控制面请求；大文件字节不经过应用服务器。

该 Benchmark 不重新实现正式上传业务。项目正式 `/files` 链路中的 MD5 秒传、临时分片对象、Redis 断点续传状态、ComposeObject 合并等逻辑保持不变。Benchmark Direct 模式只测 Presigned URL 数据面，以减少数据库、MD5、Redis 和分片合并对结果的干扰。

## 指标边界

测试记录：

- `transferMs`：实际文件传输耗时。
- `endToEndMs`：包含 Direct URL 获取在内的端到端耗时。
- `throughputMiBPerSec`：文件大小 / 传输时间。
- `heapPeakDeltaMb`：本轮应用进程 Heap 峰值相对起始值的增量。
- `processCpuUtilizationPct`：进程 CPU 时间 / wall time / 逻辑处理器数，归一化到整机逻辑 CPU 容量。
- `gcCountDelta` / `gcTimeDeltaMs`：本轮 GC 变化。
- `appFileIngressBytes` / `appFileEgressBytes`：Spring Boot 实际处理的大文件应用层字节量。

`appFileIngressBytes` / `appFileEgressBytes` 不是 Windows 网卡流量计数器，也不包含 HTTP Header、JSON 控制请求等小流量。

对于 Relay，应用层会接收文件并再次向 MinIO 发送同一批字节；对于 Direct，文件数据面绕过 Spring Boot，因此这两个大文件字节计数应为 0。

## Benchmark Endpoint

```text
PUT    /benchmark/upload/relay
POST   /benchmark/upload/direct-url
DELETE /benchmark/upload/object?objectName=benchmark/...
POST   /benchmark/upload/metrics/start
POST   /benchmark/upload/metrics/stop
```

清理接口只允许删除 `benchmark/` 前缀对象，避免误删正式对象。

## 准备固定大小测试文件

```powershell
python .\prepare_file.py `
  --size-mb 1024 `
  --output .\benchmark-1gb.bin
```

生成的 `.bin` 文件已加入 `.gitignore`，不会进入公开仓库。

## 运行

安装依赖：

```powershell
python -m pip install -r .\requirements.txt
```

运行：

```powershell
python .\upload_benchmark.py `
  --access-token "<ACCESS_TOKEN>" `
  --file ".\benchmark-1gb.bin" `
  --warmup 1 `
  --rounds 5
```

如 Spring Boot 不在默认 `http://127.0.0.1:8082`，使用 `--base-url` 指定。

## 顺序偏差

当前脚本每轮按：

```text
Relay -> Direct
```

执行。固定顺序可能受到文件缓存、JIT、磁盘缓存、MinIO 状态等顺序效应影响，因此：

- 架构层面的应用数据面字节差异可以直接解释。
- 对精确的 P95、吞吐量倍数等时间指标，应通过增加轮次或交替执行顺序进一步验证。
- 不应把单次本地 A/B 的精确百分比解释为生产 SLA。

## 结果文件

脚本生成：

```text
benchmark_upload/results/upload_benchmark_results.csv
benchmark_upload/results/upload_benchmark_detail.json
```

结果目录已加入 `.gitignore`。公开分支只保留 Benchmark 代码、指标定义和复现方法，不提交原始结果。
