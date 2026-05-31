# 测试说明

本项目的测试分为自动化测试和手动接口场景测试两类。由于项目依赖 MySQL、Redis、RocketMQ、MinIO 和第三方 AI SDK，部分测试需要本地基础设施完整启动后再执行。

## 1. 自动化测试

执行：

```bash
./mvnw test
```

Windows PowerShell：

```powershell
.\mvnw.cmd test
```

当前自动化测试重点覆盖：

- Spring Boot 应用上下文能否正常启动。
- 核心 Bean 是否可以完成基础装配。
- 不依赖外部服务的业务逻辑可逐步补充单元测试。

## 2. 集成测试策略

涉及数据库、Redis、MinIO 或 RocketMQ 的测试默认不强制在 CI 中执行，原因是这些测试依赖外部服务状态。建议采用以下策略：

1. 本地开发阶段通过 Docker Compose 或本地服务启动依赖。
2. 对核心 Service 编写 Mock 单元测试，覆盖参数校验和业务分支。
3. 对关键链路编写集成测试，例如登录、发布视频、发送弹幕、Feed 分发。
4. 后续可引入 Testcontainers，降低集成测试对本机环境的依赖。

## 3. 手动接口场景测试

接口调试文件位于：

```text
docs/http/*.http
```

这些文件用于本地联调和面试演示前的链路验证，包括：

- 用户注册、登录、刷新 Token
- 视频上传与播放
- 点赞、收藏、投币
- Feed 发布与读取
- WebSocket 弹幕
- AI 视频遮罩生成与查询

执行前请替换文件中的占位参数，例如：

```text
<ACCESS_TOKEN_A>
<ACCESS_TOKEN_B>
<VIDEO_ID>
<FILE_ID>
```

