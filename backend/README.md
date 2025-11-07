# YunBQ Backend API

## Notes API

### GET `/api/notes`
- Query params:
  - `page` (default: `1`)
  - `size` (default: `10`)
  - `q` (optional, search in title/content/tags)
  - `archived` (optional, `true` or `false`)
  - `isPublic` (optional, `true` or `false`)
- Response:
```
{
  "items": [
    {
      "id": 1,
      "userId": 100,
      "title": "...",
      "content": "...",
      "tags": "tag1,tag2",
      "archived": false,
      "isPublic": true,
      "createdAt": "2025-10-26T12:34:56",
      "updatedAt": "2025-10-26T12:35:00",
      "likeCount": 3,
      "likedByMe": true
    }
  ],
  "total": 42,
  "page": 1,
  "size": 10
}
```

### POST `/api/notes/{id}/like`
- Returns `{ "count": number, "likedByMe": true }`

### POST `/api/notes/{id}/unlike`
- Returns `{ "count": number, "likedByMe": false }`

### GET `/api/notes/{id}/likes`
- Returns `{ "count": number, "likedByMe": boolean }`

## Notes Model
`NoteItem` fields in list responses:
- `id`, `userId`, `authorName`, `title`, `content`, `tags`
- `archived`, `isPublic`, `createdAt`, `updatedAt`
- `likeCount`, `likedByMe`

## Auth
- Public endpoints:
  - `POST /api/auth/register` — 用户注册（username、password、nickname、email）
  - `POST /api/auth/login` — 用户名登录（username、password）
  - `POST /api/auth/login/email` — 邮箱登录（email、password）
  - `POST /api/auth/forgot` — 找回密码（校验验证码，发送重置码到邮箱）
  - `POST /api/auth/reset` — 使用邮箱 + 重置码重置密码
- Protected endpoints:
  - 需要携带 `Authorization: Bearer <token>`，例如：`GET /api/account/me`

### 邮箱登录接口说明

- 路径：`POST /api/auth/login/email`
- 请求体示例：
  ```json
  { "email": "user@example.com", "password": "your_password" }
  ```
- 响应体示例：
  ```json
  { "token": "<JWT>", "userId": 1, "username": "user1", "nickname": "昵称", "role": "USER" }
  ```
- 异常与安全：
  - 当邮箱不存在或密码错误时将返回 4xx 并带中文提示；
  - 密码校验使用 `PasswordEncoder.matches`，禁止明文比较；
  - 建议在生产环境启用登录限流与审计日志提升安全性。

## Logging
- Tables:
  - `audit_logs`: business audit entries, fields: `id,user_id,level,message,created_at`.
  - `request_logs`: request metrics, fields: `id,method,uri,query,ip,user_agent,status,duration_ms,user_id,created_at`.
  - `auth_logs`: authentication events, fields: `id,user_id,username,success,reason,ip,user_agent,created_at`.
  - `error_logs`: unhandled exceptions, fields: `id,user_id,path,exception,message,stack_trace,created_at`.

- Admin API:
  - GET `/api/admin/logs` — paginate `audit_logs` with optional `level`.
  - GET `/api/admin/request-logs` — paginate `request_logs` with optional `uri`, `status`.
  - GET `/api/admin/auth-logs` — paginate `auth_logs` with optional `success`, `username`.
  - GET `/api/admin/error-logs` — paginate `error_logs` with optional `exception`.

- Write paths:
  - Request logs: `RequestLoggingFilter` records method/URI/status/duration and persists via `LogService`.
  - Auth logs: `JwtAuthenticationFilter` records success/failure of JWT verification via `LogService`.
  - Error logs: `GlobalExceptionHandler` catches unhandled exceptions and persists via `LogService`.
  - Audit logs: use `LogService.logAudit(userId, level, message)` in business paths as needed.