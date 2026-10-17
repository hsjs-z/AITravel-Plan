# Quickstart Validation: AI 旅行平台 v2

本文件用于实现后的验收，不是实现代码。所有命令从仓库根目录执行。

## Prerequisites

1. 读取根 `.env` 并配置：

```text
DEEPSEEK_API_KEY=...
AMAP_WEB_SERVICE_KEY=...
VITE_AMAP_JS_KEY=...
ADMIN_EMAIL=...
ADMIN_PASSWORD=...
MYSQL_ROOT_PASSWORD=...
```

2. 启动 MySQL、Redis、Kafka、Nacos、Prometheus、Grafana、Tempo。
3. 先执行 V2-02 的依赖升级门禁，通过后才进入 V2-03。

## V2-02 hard gate

```bash
mvn clean install
```

Expected:

- 所有模块编译和测试通过。
- user-auth、inventory、itinerary、ai-planning、booking-order、payment、
  notification、gateway 均可启动。
- 既有 v1 主链路回归通过。
- Prometheus 能查询关键接口指标。
- Tempo 能还原 AI → 订单 → 支付 trace。

**Important**: 此门禁未通过，不得开始 V2-03 的 user-auth 代码修改。

## Admin import

1. 管理员登录：

```text
POST /auth/login
```

2. 触发导入：

```text
POST /admin/seed/scenic-spots
Authorization: Bearer <admin-token>
```

Expected: `202`，返回 `runId`。

3. 轮询：

```text
GET /admin/seed/scenic-spots/{runId}
```

Expected:

- `status` 最终为 `SUCCEEDED` 或 `PARTIAL_FAILED`。
- 报告有 6 个批次。
- 覆盖 34 个省级行政区，约 204 条。
- 16 个热门城市至少各命中 1 个上架景区。
- 坐标缺失记录为 `coordinate_status=PENDING`。
- 重跑后 `(name, city, type)` 无重复，人工修改字段未被覆盖；
  `load-test-resource` 的 `province/city` 仍为 NULL。

4. 鉴权：

- 无 token：401。
- 普通用户 token：404。

## Planning

### Mock

```text
PLANNER_PLANNER=mock
POST /planning/tasks
```

Request example:

```json
{
  "destination": "西安",
  "days": 3,
  "partySize": 2,
  "pace": "BALANCED",
  "preferences": "亲子，喜欢历史"
}
```

Expected:

- 任务 `PENDING → RUNNING → SUCCEEDED`。
- 结果每天有有序景点列表。
- mock 不再固定云南。

### DeepSeek

```text
PLANNER_PLANNER=deepseek
```

Expected:

- 日志或观测中能看到 `searchAttractions` 工具调用。
- AI 结果只使用工具返回的 `resourceId/specId`。
- 编造 ID 被置空，景点仍以未匹配状态显示。

## Slot packing boundaries

必须覆盖：

1. 4h 景点，上午空，BALANCED：进入上午。
2. 4h 景点，上午已有 1h：进入下午。
3. RELAXED：晚上为空，但仍返回 `EVENING` 数组。
4. RELAXED 4h 景点：无法整块放入任何时段，不落库并写 warning。
5. 每时段第 4 个：被拒绝。

## Result page

1. 草稿态：

- 每个时段有“添加”。
- 每个景点有“删除”。
- 添加从库存检索，≤3。
- 免费显示“免费/无需购票”，可加入。
- 未匹配显示“暂不可预订/票价以官方为准”，无 ID。

2. 确认/锁定：

- 添加、删除都隐藏。
- 页面只读。

3. 地图：

- 有坐标：点击定位。
- 无坐标：显示“坐标待补”。
- 缺少 JS key：显示配置提示，不白屏。

4. 底部：

- 显示 AI 旅行建议。
- 显示“数据由 AI 生成，仅供参考”。
- 无分享按钮。
- 无“地名”板块。

## Ticket-only booking

1. 已确认行程只含门票。
2. 一键预订。
3. 默认数量 = 出行人数。
4. 请求中伪造酒店/车票资源，锁库存被拒绝。
5. 原 Saga、库存补偿、幂等、支付终态测试全部通过。

## User persistence

1. 注册用户。
2. 重启 user-auth。
3. 用同一邮箱/密码登录。
4. 管理员重启后仍可访问 `/admin/**`；普通用户仍返回 404。

## Observability

- Prometheus：关键接口 QPS/P99 可查询。
- Grafana：现有面板仍能加载。
- Tempo：规划、导入、订单、支付 trace 可还原。
- 日志不输出 API key、密码、支付要素。
