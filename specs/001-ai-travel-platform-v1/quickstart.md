# Quickstart: AI 旅行规划平台 v1 端到端验证

本指南用于验证主线「规划 → 行程 → 预订 → 支付 → 通知」端到端跑通。
实现细节见 `data-model.md` 与 `contracts/`；本文件只描述可运行的验证路径。

## 前置条件

- JDK 17、Maven 3.9+、Docker（用于本地中间件）
- 本地中间件：MySQL、Redis、Kafka、Nacos（`deploy/` 提供编排文件）

## 启动步骤

1. 启动中间件：在 `deploy/` 执行容器编排，确认 MySQL/Redis/Kafka/Nacos 就绪。
2. 设置环境变量：各服务的数据库口令、DeepSeek API Key、支付凭据（Mock 可为空）等
   仅经环境变量注入，禁止写入代码或配置文件。
3. 构建并启动：根目录 `mvn -pl <module> spring-boot:run`，按
   `gateway → inventory → user-auth → ai-planning → itinerary → booking-order →
   payment → notification` 顺序启动；Nacos 中注册成功即视为服务可用。

## 验证场景

### S1 注册与登录（user-auth）

`POST /auth/register` 用邮箱+密码注册 → `POST /auth/login` 取得 token。
期望：返回 `userId` 与 token；后续请求经 gateway 携带 token 并透传 `userId`。

### S2 AI 生成行程（ai-planning → itinerary → inventory）

`POST /planning/tasks` 提交「云南 5 天 4 晚、双人、预算 8000」。

- 期望：秒级返回 `taskId`；任务状态可查询，依次经历
  `PENDING → RUNNING → SUCCEEDED`（或 FAILED + 重试）。
- 成功时 `itineraryId` 指向一份草稿行程；推荐项按「资源 + 日期 + 规格」落地，
  推荐期间以 `purpose=PLANNING` 短 TTL 软占位候选库存。
- 对同一需求重试：只保留最后成功的草稿行程，不产生重复方案。

### S3 查看与确认行程（itinerary）

`GET /itineraries/{id}` 查看 → 编辑 → `POST /itineraries/{id}/confirm`。

- 期望：编辑使状态回 DRAFT；确认后状态为 CONFIRMED、可发起一键预订。
- 越权访问他人行程返回 403 且不泄露内容。

### S4 一键联合预订 + 库存（booking-order → inventory）

`POST /orders` 对确认行程发起一键预订（默认全选，可取消勾选子集）。

- 期望：创建一张订单与多个明细（数量默认规则见 GLOSSARY「一键预订」）。
- 全成功 → `BOOKED`；任一资源失败 → 已占用库存自动释放、订单 `FAILED`、用户可见失败资源。
- 同一行程重复提交只产生一张订单；并发对同一库存单元不超卖。

### S5 支付与幂等（booking-order → payment）

`POST /payments` 后 `POST /payments/{id}/confirm`（Mock）。

- 期望：订单 `PAID → CONFIRMED`（自动触发，ADR-0001）。
- 重复支付返回首次结果、不重复扣款；重复/乱序回调不改变订单最终状态；
  金额不一致的回调不置已支付并记异常。

### S6 通知与未读（notification）

支付成功后，站内信生成。

- 期望：`GET /notifications/unread-count` 未读数增加；`POST /notifications/{id}/read`
  后未读数减少；同一事件不产生重复站内信；下游短暂不可用后投递补齐。

### S7 取消与退款（booking-order → payment → inventory）

对已支付订单 `POST /orders/{id}/cancel`。

- 期望：自动全额退款（幂等、金额 ≤ 实付），订单 `CANCELLED`，库存释放，
  行程回到 `CONFIRMED` 可再次预订。未支付超时（默认 15 分钟）→ 订单自动 `CANCELLED`、无退款。

## 观测验证

任一次端到端操作，在观测后台可按同一 `traceId` 还原 AI 规划 → 订单 → 支付全链路，
并查询关键接口的 QPS 与 P99 延迟。
