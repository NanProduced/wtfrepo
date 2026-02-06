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

## 环境变量 (占位)

- `NEXTAUTH_URL`
- `NEXTAUTH_SECRET`
- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`
- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `BACKEND_BASE_URL`

## 文档与需求

需求与设计文档位于仓库 `local-docs/`，请从 `local-docs/00-Index.md` 开始阅读。
