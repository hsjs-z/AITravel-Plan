# ai-planning（AI 规划服务）

职责：自然语言需求 → 异步任务状态机 → DeepSeek 生成草稿行程与推荐。

| 方法 | 路径 | 说明 | 关键请求/响应 |
|------|------|------|----------------|
| POST | /planning/tasks | 提交规划 | `{destination, days, partySize, budget, preferences?}` → `{taskId}` |
| GET | /planning/tasks/{taskId} | 查询状态/结果 | → `{status, itineraryId?, failureReason?}` |
| POST | /planning/tasks/{taskId}/retry | 重试 | → `{taskId}` |

状态机：`PENDING → RUNNING → SUCCEEDED | FAILED`；失败可重试。

下游调用（OpenFeign）：

- itinerary：`POST /itineraries` 创建草稿行程（成功后回填 `itineraryId`）。
- inventory：`GET /inventory/query` 按「资源 + 日期 + 规格」查询可用库存；
  `POST /inventory/locks`（`purpose=PLANNING`，短 TTL）软占位候选库存。

事件：`PlanningSucceeded` / `PlanningFailed`。
