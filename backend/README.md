# WTF-Repo Backend (Spring Boot)

WTF-Repo 后端服务，提供核心业务 API、排行榜计算、Bug 经济、评论系统与轻量 Admin 能力。

## 技术栈

- Java 21 / Spring Boot 3.5
- PostgreSQL
- Redis (缓存 + 轻量异步队列)
- SSE (实时通知)
- JWT (无状态认证)

## 架构原则

- 无状态 API，水平扩展优先
- Redis 用于热点缓存与削峰填谷的异步处理
- 允许弱一致，配合“坦诚式交互协议”的前端提示

## 认证方案 (最终决策)

- 前端使用 NextAuth 完成 OAuth
- 前端通过 BFF 代理调用后端
- 后端提供 Token Exchange: `/auth/exchange`
  - 校验 NextAuth JWT
  - 签发后端 Access Token (JWT)
  - 后端仅信任自己签发的 Token

## 本地开发 (占位)

```bash
./mvnw spring-boot:run
```

> 运行前需准备 PostgreSQL / Redis，配置将通过环境变量或 `application-*.yml` 完成。

## 文档与需求

需求与设计文档位于仓库 `local-docs/`，请从 `local-docs/00-Index.md` 开始阅读。
