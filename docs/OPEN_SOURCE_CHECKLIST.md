# 开源发布检查清单

发布到公开 GitHub 仓库前，请逐项确认：

- [ ] 本地执行 `./mvnw test` 通过。
- [ ] 仓库中不存在 `.env`、真实数据库密码、对象存储密钥、JWT Secret、第三方 API Key。
- [ ] `application-dev.yml` 与 `application-prod.yml` 只读取环境变量或占位配置。
- [ ] 已删除 `target/`、`.idea/`、`.vscode/`、HTTP 响应缓存和临时日志文件。
- [ ] `docs/http/*.http` 只包含占位 Token 和示例参数。
- [ ] README 中的架构图、核心流程图、启动方式和测试说明可以正常阅读。
- [ ] 如果真实密钥曾进入 Git 历史，已经完成密钥轮换。
- [ ] 面试前已按 README 跑通核心演示链路。
