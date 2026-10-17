# user-auth v2

## Persistence

- 用户从进程内存迁到 MySQL `travel_user_auth.app_user`。
- 注册/登录行为不变；重启不丢用户。
- 密码仍只存散列。

## Role

- `USER`：默认角色。
- `ADMIN`：可访问 `/admin/**`。
- JWT 增加 `role` claim。
- gateway 解析后注入 `X-User-Role`。
- 旧 token 无 `role` 时按 `USER`。

## Admin bootstrap

环境变量：

- `ADMIN_EMAIL`
- `ADMIN_PASSWORD`

启动时若管理员不存在则创建；明文密码不入库、不入仓。

## Endpoints

保持：

- `POST /auth/register`
- `POST /auth/login`
- `GET /users/me`

响应兼容增加：

```json
{
  "token": "...",
  "userId": "...",
  "role": "USER"
}
```

`GET /users/me` 增加 `role`。

## Admin access

- 未登录访问 `/admin/**`：401。
- 已登录非管理员：404。
- 管理员：放行到 inventory。
