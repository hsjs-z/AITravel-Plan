# itinerary v2

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
- `resourceId/specId` 必须来自 inventory 的有效景点/门票规格。

## DELETE /itineraries/{itineraryId}/items/{itemId}

- 仅 `DRAFT` 可删除。
- owner 不匹配返回 404。
- 删除后同一时段后续排序重新编号。
- 已确认/锁定返回 409。

## 既有 PUT

`PUT /itineraries/{itineraryId}` 保留；请求 item 可带 v2 字段。
编辑仍使 `CONFIRMED` 回 `DRAFT`，该 v1 行为不变。

## Read-only rule

- `DRAFT`：结果页可添加/删除；文案编辑仍在 itinerary 编辑页。
- `CONFIRMED/LOCKED/COMPLETED`：结果页隐藏添加/删除，接口拒绝修改。
