# 接口场景测试说明

`docs/http` 目录中的文件用于本地接口联调与演示验证。它们不是生产配置，也不会包含真实 Token 或真实密钥。

## 使用方式

1. 启动 MySQL、Redis、RocketMQ、MinIO 等依赖。
2. 启动 Spring Boot 应用。
3. 通过登录接口获取 Access Token。
4. 替换 `.http` 文件中的占位符。
5. 按业务顺序执行接口。

## 占位符约定

| 占位符 | 含义 |
| --- | --- |
| `<ACCESS_TOKEN>` | 当前登录用户的访问令牌 |
| `<ACCESS_TOKEN_A>` | 用户 A 的访问令牌 |
| `<ACCESS_TOKEN_B>` | 用户 B 的访问令牌 |
| `<VIDEO_ID>` | 已发布视频 ID |
| `<FILE_ID>` | 已上传文件 ID |
| `<UPLOAD_ID>` | 分片上传任务 ID |
| `<OBJECT_NAME>` | MinIO 对象名 |

## 建议验证顺序

1. 用户注册与登录。
2. 文件上传或分片上传。
3. 创建并发布视频。
4. 验证视频播放与 Range 请求。
5. 验证点赞、收藏、投币。
6. 验证动态 Feed 分发。
7. 验证 WebSocket 弹幕。
8. 验证视频遮罩生成与查询。
