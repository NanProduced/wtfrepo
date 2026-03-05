# OAuth 授权登录实现文档

## 概述

本文档描述了 admin-lite 前端 OAuth 授权登录流程，以及后端需要实现的 API 端点。

## 前端实现

### 已完成的组件

1. **OAuthLoginPanel** (`modules/admin/components/oauth-login-panel.tsx`)
   - 基于 Efferd auth-2 block 设计
   - 使用 ui-ux-pro-max 设计系统（Fira Code + Fira Sans 字体，Indigo/Purple 配色）
   - FloatingPaths 动画背景
   - 响应式布局，支持中英双语
   - 无障碍访问（WCAG AA 标准）

2. **AuthCallbackPage** (`app/auth/callback/page.tsx`)
   - 处理 OAuth 回调
   - 显示加载/成功/错误状态
   - 自动跳转到 dashboard

3. **API Route** (`app/api/admin/session/exchange/route.ts`)
   - 支持 `mode: "oauth"` 模式
   - 调用后端 OAuth token 交换端点

### 认证流程

```
1. 用户点击"使用主站账号登录"按钮
   ↓
2. 跳转到主站授权页
   URL: ${MAIN_SITE_URL}/auth/admin-authorize?redirect_uri=${admin-lite}/auth/callback
   ↓
3. 用户在主站确认授权
   ↓
4. 主站回调 admin-lite
   URL: ${admin-lite}/auth/callback?code=AUTHORIZATION_CODE
   ↓
5. admin-lite 调用后端 token 交换
   POST /api/v1/admin/platform/oauth/token
   Body: { "code": "AUTHORIZATION_CODE" }
   ↓
6. 后端验证 code，返回管理员 token
   Response: { "accessToken": "ADMIN_TOKEN", ... }
   ↓
7. admin-lite 存储 token 到 httpOnly cookie
   ↓
8. 跳转到 /dashboard
```

## 后端需要实现的端点

### 1. 主站授权页面

**端点**: `GET /auth/admin-authorize`

**查询参数**:
- `redirect_uri` (required): admin-lite 的回调 URL

**功能**:
1. 检查用户是否已登录主站
   - 未登录 → 跳转到登录页，登录后返回授权页
   - 已登录 → 显示授权确认页面

2. 授权确认页面显示:
   - 应用名称: "WTF-Repo Admin Lockroom"
   - 请求的权限: "管理员访问权限"
   - 用户信息: 当前登录用户的昵称/邮箱
   - 两个按钮: "授权" 和 "取消"

3. 用户点击"授权":
   - 生成临时授权码 (code)
   - 存储 code 与用户 ID 的映射关系（有效期 5 分钟）
   - 重定向到 `redirect_uri?code=AUTHORIZATION_CODE`

4. 用户点击"取消":
   - 重定向到 `redirect_uri?error=access_denied`

**安全要求**:
- 验证 `redirect_uri` 是否在白名单中
- 授权码只能使用一次
- 授权码 5 分钟后过期

### 2. OAuth Token 交换端点

**端点**: `POST /api/v1/admin/platform/oauth/token`

**请求头**:
```
Content-Type: application/json
X-Request-Id: <uuid>
X-Idempotency-Key: <uuid>
```

**请求体**:
```json
{
  "code": "AUTHORIZATION_CODE"
}
```

**响应** (成功 200):
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "userId": "user_123",
  "role": "ADMIN"
}
```

**响应** (失败):
- `400 Bad Request`: 缺少 code 或 code 无效
  ```json
  {
    "message": "Invalid or expired authorization code."
  }
  ```

- `403 Forbidden`: 用户没有管理员权限
  ```json
  {
    "message": "admin_role_required"
  }
  ```

**功能**:
1. 验证授权码 (code)
   - 检查 code 是否存在且未过期
   - 检查 code 是否已被使用
   - 获取 code 对应的用户 ID

2. 验证用户权限
   - 检查用户是否有 ADMIN 或 MANAGER 角色
   - 如果没有权限，返回 403 错误

3. 生成管理员 token
   - 调用现有的 `adminPlatformService.login()` 方法
   - 或直接生成 JWT token（包含用户 ID 和管理员角色）

4. 标记 code 为已使用
   - 防止重放攻击

5. 返回 token 和用户信息

**安全要求**:
- 授权码只能使用一次
- 验证请求来源（可选：检查 Referer 或 Origin）
- 记录审计日志（用户 ID、IP、时间戳）

## 环境变量

### admin-lite (.env.local)

```env
# 主站 URL（用于 OAuth 跳转）
NEXT_PUBLIC_MAIN_SITE_URL=http://localhost:3000

# 后端 API URL
NEXT_PUBLIC_BACKEND_URL=http://localhost:8080
```

### 后端 (application.yml)

```yaml
wtf-repo:
  admin:
    oauth:
      # 允许的回调 URL 白名单
      allowed-redirect-uris:
        - http://localhost:3001/auth/callback
        - https://admin.wtf-repo.com/auth/callback
      # 授权码有效期（秒）
      code-expiration: 300
```

## 数据库表设计（可选）

如果需要持久化授权码，可以创建表：

```sql
CREATE TABLE admin_oauth_codes (
  code VARCHAR(64) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  redirect_uri VARCHAR(512) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE,
  used_at TIMESTAMP,
  INDEX idx_user_id (user_id),
  INDEX idx_expires_at (expires_at)
);
```

或者使用 Redis 存储（推荐）：

```
Key: oauth:code:{code}
Value: {
  "userId": 123,
  "redirectUri": "http://localhost:3001/auth/callback",
  "createdAt": 1234567890
}
TTL: 300 seconds
```

## 测试场景

### 正常流程
1. 用户访问 admin-lite 登录页
2. 点击"使用主站账号登录"
3. 跳转到主站授权页
4. 确认授权
5. 回调到 admin-lite
6. 成功登录，跳转到 dashboard

### 错误场景
1. **用户取消授权**: 显示"授权被拒绝"错误，3 秒后返回登录页
2. **授权码过期**: 显示"授权码已过期"错误，3 秒后返回登录页
3. **用户无权限**: 显示"当前账号没有管理权限"错误，3 秒后返回登录页
4. **网络错误**: 显示"登录失败，请重试"错误，3 秒后返回登录页

## 迁移策略

### 阶段 1: 并行运行（推荐）
- 保留现有的 token 粘贴登录方式
- 添加 OAuth 登录作为新选项
- 用户可以选择任一方式登录

### 阶段 2: 逐步迁移
- OAuth 登录设为默认方式
- token 粘贴方式标记为"备用方式"或"调试模式"

### 阶段 3: 完全迁移
- 移除 token 粘贴方式
- 仅保留 OAuth 登录

## UI/UX 特性

### 设计系统
- **字体**: Fira Code (标题) + Fira Sans (正文)
- **配色**: Indigo (#6366F1) + Purple (#8B5CF6) + Emerald (#10B981)
- **动画**: FloatingPaths SVG 路径动画（36 条路径，20-30 秒循环）
- **布局**: 左侧品牌展示 + 右侧登录表单
- **响应式**: 移动端堆叠布局，桌面端双栏布局

### 无障碍特性
- WCAG AA 色彩对比度（4.5:1）
- 键盘导航支持
- 屏幕阅读器友好
- Focus 状态可见
- aria-label 和 role 属性
- 加载状态提示

### 国际化
- 中文/英文双语支持
- 通过 `useAdminI18n` hook 实现
- 所有文案都有中英文版本

## 后续优化建议

1. **记住设备**: 添加"记住此设备 30 天"选项，减少频繁授权
2. **单点登出**: 主站登出时同步登出 admin-lite
3. **Token 刷新**: 实现 refresh token 机制，延长会话时间
4. **多因素认证**: 为管理员账号添加 2FA 支持
5. **审计日志**: 记录所有管理员登录活动
6. **会话管理**: 允许用户查看和撤销活跃会话

## 参考资料

- [OAuth 2.0 Authorization Code Flow](https://oauth.net/2/grant-types/authorization-code/)
- [WCAG 2.1 AA Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [Next.js Authentication](https://nextjs.org/docs/authentication)
