# user-auth（账号服务）

职责：注册、登录、签发 JWT；提供当前用户信息给 gateway 透传。

| 方法 | 路径 | 说明 | 关键请求/响应 |
|------|------|------|----------------|
| POST | /auth/register | 邮箱注册 | `{email, password}` → `{userId}` |
| POST | /auth/login | 登录 | `{email, password}` → `{token, userId}` |
| GET | /users/me | 当前用户 | → `{userId, email, phone?}` |

约束：密码只存散列；token 由 gateway 校验并透传 `userId`；其余服务不得自行解析密码。
