# WTF-Repo Frontend (Next.js)

WTF-Repo 前端应用，负责 SEO、页面交互、BFF 代理与认证入口。

## 技术栈

- Next.js (App Router)
- NextAuth (OAuth)
- Tailwind / Shadcn UI (规划)
- Vercel 部署

## 认证与 BFF 代理

- OAuth 由 NextAuth 完成
- 前端通过 BFF (Next.js Route Handlers) 代理请求后端 API
- BFF 会将 NextAuth 身份信息换取后端 Access Token，再转发请求
- 浏览器不直接跨域调用后端，减少 CORS/第三方 Cookie 问题

## 本地开发

```bash
npm install
npm run dev
```

## 环境变量

`frontend` 当前使用 NextAuth v5 的 `AUTH_*` 命名，建议至少配置以下变量：

- `AUTH_URL`：前端站点地址（本地通常是 `http://localhost:3000`）
- `AUTH_SECRET`：NextAuth 会话签名密钥
- `AUTH_GITHUB_ID`
- `AUTH_GITHUB_SECRET`
- `AUTH_GOOGLE_ID`
- `AUTH_GOOGLE_SECRET`
- `API_URL`：后端 API 基地址（本地通常是 `http://localhost:8080/api/v1`）

可选变量：

- `NEXT_PUBLIC_WS_URL`：SSE/流地址，默认 `"/api/stream"`（经由 BFF 代理）
- `AUTH_IDENTITY_PROOF_ISSUER`
- `AUTH_IDENTITY_PROOF_AUDIENCE`
- `AUTH_IDENTITY_PROOF_SECRET`
- `AUTH_IDENTITY_PROOF_TTL_SECONDS`
- `AUTH_ENABLE_EXCHANGE_MOCK_FALLBACK`（默认关闭）
- `BFF_ENABLE_MOCK_FALLBACK`（默认关闭）

联调前建议执行：

```bash
npm run lint
npm run typecheck
npm run build
```

## 文档与需求

需求与设计文档位于仓库 `local-docs/`，请从 `local-docs/00-Index.md` 开始阅读。

私有规范与专有说明位于 `frontend/.docs/`（不纳入 Git）。
