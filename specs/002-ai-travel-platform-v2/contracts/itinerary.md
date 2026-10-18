# itinerary v2

## GET /itineraries

按 `X-User-Id` 返回当前用户的行程摘要，按 `createdAt` 倒序：

```json
[
  {
    "id": "it-...",
    "destination": "丽江",
    "startDate": "2026-10-20",
    "endDate": "2026-10-22",
    "partySize": 2,
    "status": "CONFIRMED",
    "hasOrder": true,
    "activeOrderCount": 1,
    "completedOrderCount": 0,
    "createdAt": "2026-10-18T10:00:00Z"
  }
]
```

- 只返回当前用户的数据；不得通过查询参数指定其他用户。
- `GET /itineraries/{id}` 的 owner 校验不变：他人行程返回 `404`，不暴露存在性。
- 该列表用于前端“我的行程”导航页，点击进入既有行程详情页。
- `activeOrderCount` / `completedOrderCount` 来自 booking-order 的只读订单摘要：
  - 活动订单：`CREATED/BOOKED/PAID`。
  - 已完成订单：`CONFIRMED`。
  - `CANCELLED/FAILED` 不计入任一计数。
- `GET /itineraries/{id}` 同样返回这两个字段；创建/更新等写响应也返回当前快照。

### 列表状态展示

前端按下述优先级展示，不得只根据行程状态猜测订单：

1. `activeOrderCount > 0` -> “预订中”。
2. `completedOrderCount > 0` -> “已出行”。
3. `status=CONFIRMED` 且两个计数均为 0 -> “已确认 · 未预订”。
4. `status=DRAFT` -> “草稿”；`LOCKED` -> “已锁定”；`COMPLETED` -> “已出行”。

取消/退款完成后行程保持 `CONFIRMED`（不进入“已拒绝”终态），订单计数归零后
列表显示“已确认 · 未预订”，允许改期后重新预订。

## Item fields

`GET /itineraries/{itineraryId}` 的每个 item 增加：

```json
{
  "id": "item-...",
  "dayDate": "2026-10-20",
  "name": "秦始皇兵马俑",
  "description": "世界第八大奇迹，建议预留半天。",
  "suggestedHours": 4.0,
  "timeSlot": "MORNING",
  "sortOrder": 1,
  "resourceType": "ATTRACTION",
  "resourceId": "r-...",
  "specId": "s-...",
  "source": "AI"
}
```

未匹配：

- `resourceId=null`
- `specId=null`
- `resourceType` 可为 `ATTRACTION`
- `description` 为简介

免费：

- `resourceId` 有值
- `specId=null`
- `resourceType=ATTRACTION`

旧 v1 item：

- `name=null`，前端回退 `description`
- `timeSlot=null`
- `sortOrder=0`

`name`、`suggestedHours`、`ticketPrice` 是写入时的展示快照。手动添加时由
inventory 资源详情回填，不随后续票价变化回写。

## Warnings

`POST /itineraries`、`PUT /itineraries/{itineraryId}` 接受可选
`warnings: string[]`；`GET /itineraries/{itineraryId}` 与创建/更新响应原样返回：

```json
{
  "warnings": ["第1天：玉龙雪山（4.0h）超出当日节奏容量，未安排"]
}
```

warnings 是行程级字段，与 items 一起返回，供结果页展示；服务端不得只写日志。

## POST /itineraries/{itineraryId}/items

用于结果页草稿态从库存添加。

Request:

```json
{
  "dayDate": "2026-10-20",
  "name": "华清宫",
  "description": "唐代皇家温泉行宫",
  "suggestedHours": 3,
  "timeSlot": "AFTERNOON",
  "resourceType": "ATTRACTION",
  "resourceId": "r-...",
  "specId": "s-...",
  "source": "MANUAL"
}
```

Rules:

- 仅 `DRAFT` 可添加。
- owner 不匹配返回 404。
- 时段已满 3 个返回 409。
- `sortOrder` 由服务端追加到该时段末尾。
- `resourceId` 必须存在于 inventory 且 `type=ATTRACTION`；否则 400。
- `specId` 若提供，必须属于该资源且 `specType=TICKET_TYPE`；付费景区必须提供，
  免费景区可为空。校验失败返回 400。
- 该路径是用户显式选择库存资源，**不做 AI 白名单校验**；AI 白名单只约束
  AI 输出。
- `name/suggestedHours/ticketPrice` 由服务端按 inventory 详情写入快照。
- `CONFIRMED/LOCKED/COMPLETED` 增删返回 `409` 统一错误体，不能用 404 表示
  状态不允许。

## DELETE /itineraries/{itineraryId}/items/{itemId}

- 仅 `DRAFT` 可删除。
- owner 不匹配返回 404。
- 删除后同一时段后续排序重新编号。
- 已确认/锁定/已完成返回 409。

## 既有 PUT

`PUT /itineraries/{itineraryId}` 保留；请求 item 可带 v2 字段。
编辑仍使 `CONFIRMED` 回 `DRAFT`，该 v1 行为不变。

## Read-only rule

- `DRAFT`：结果页可添加/删除；文案编辑仍在 itinerary 编辑页。
- `CONFIRMED/LOCKED/COMPLETED`：结果页隐藏添加/删除，接口拒绝修改。

## DELETE /itineraries/{itineraryId}

- owner 不匹配返回 `404`。
- `DRAFT` 可直接删除。
- `CONFIRMED` 且 `activeOrderCount=0` 可删除；已有 `CONFIRMED` 订单不阻止删除。
- `LOCKED`、`COMPLETED` 或存在活动订单（`CREATED/BOOKED/PAID`）返回 `409`。
- 删除成功后同时删除该行程的行程项；列表页和详情页均提供二次确认入口。
- 删除是物理删除，不可恢复。
