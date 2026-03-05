# WTF-Repo Admin Lite (Next.js)

轻量后台管理前端，默认运行在 `http://localhost:3001`，通过 Next.js Route Handler 代理调用后端 `admin` 接口。

## 本地开发

```bash
npm install
npm run dev
```

默认脚本：

- `npm run dev` -> `next dev -p 3001`
- `npm run build`
- `npm run start` -> `next start -p 3001`

## 环境变量

最小建议配置：

- `NEXT_PUBLIC_API_URL`：后端 API 地址（建议 `http://localhost:8080/api/v1`）
- `NEXT_PUBLIC_MAIN_SITE_URL`：主站地址（建议 `http://localhost:3000`）

可选变量：

- `BACKEND_BASE_URL`：仅服务端使用，优先级高于 `NEXT_PUBLIC_API_URL`（建议不带 `/api/v1`，如 `http://localhost:8080`）
- `NEXT_PUBLIC_ADMIN_ALLOW_DIRECT_TOKEN`：生产环境是否允许“直接 token 登录”入口（默认关闭）

## OAuth 登录链路说明

1. 在 `admin-lite` 点击“使用主站账号登录”。
2. 跳转主站 `/{locale}/auth/admin-authorize` 完成授权（无 locale 路径会被主站 i18n proxy 自动补全）。
3. 返回 `admin-lite` 的 `/auth/callback`，再由 `admin-lite` 服务端调用后端 `/api/v1/admin/platform/oauth/token` 完成会话交换。

## 启动前自检

```bash
npm run lint
npx tsc --noEmit --incremental false
npm run build
```
