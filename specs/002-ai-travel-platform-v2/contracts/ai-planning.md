# ai-planning v2

## POST /planning/tasks

请求：

```json
{
  "destination": "西安",
  "days": 3,
  "partySize": 2,
  "pace": "BALANCED",
  "preferences": "亲子，喜欢历史，预算约 5000 元，节奏不要太赶",
  "budget": null
}
```

字段：

| Field | Required | Rule |
|---|---|---|
| `destination` | yes | 16 热门城市或中国国内省份/城市 |
| `days` | yes | v2 前端 1–10；服务端保留 1–30 兼容旧客户端 |
| `partySize` | yes | 1–20，前端默认 1 |
| `pace` | no | `RELAXED/BALANCED/VIBRANT`，缺省 `BALANCED` |
| `preferences` | no | 自由文本，最多 2000 字符 |
| `budget` | no | v1 兼容字段；v2 前端不再发送 |

响应：

```json
{"taskId": "..."}
```

## GET /planning/tasks/{taskId}

响应 `result` 为归一化 JSON 字符串。`PENDING/RUNNING` 时可为空。

```json
{
  "id": "...",
  "status": "SUCCEEDED",
  "itineraryId": "...",
  "failureReason": null,
  "result": "{...}"
}
```

## 模型原始输出契约

AI 只输出以下结构：

```json
{
  "destination": "西安",
  "totalDays": 3,
  "days": [
    {
      "day": 1,
      "theme": "古都初见",
      "attractions": [
        {
          "name": "秦始皇兵马俑",
          "suggestedHours": 4,
          "description": "世界第八大奇迹，建议预留半天。",
          "ticketPrice": 120,
          "resourceId": "r-...",
          "specId": "s-..."
        }
      ]
    }
  ],
  "tips": ["提前预约热门景点门票。"]
}
```

Rules:

- `days` 按 1..totalDays 有序。
- 每项 `attractions` 是有序列表。
- `resourceId/specId` 只能来自本次 `searchAttractions` 结果；未匹配为 `null`。
- `ticketPrice` 对未匹配项无意义，服务端忽略。
- `tips` 3–8 条。

## 归一化结果契约

```json
{
  "destination": "西安",
  "totalDays": 3,
  "partySize": 2,
  "pace": "BALANCED",
  "days": [
    {
      "day": 1,
      "date": "2026-10-20",
      "theme": "古都初见",
      "slots": {
        "MORNING": [
          {
            "name": "秦始皇兵马俑",
            "summary": "世界第八大奇迹，建议预留半天。",
            "suggestedHours": 4,
            "resourceId": "r-...",
            "specId": "s-...",
            "matched": true,
            "bookable": true,
            "ticketPrice": 120,
            "priceLabel": "¥120",
            "imageUrl": null,
            "latitude": 34.384,
            "longitude": 109.278,
            "coordinatePending": false,
            "timeSlot": "MORNING",
            "sortOrder": 1
          }
        ],
        "AFTERNOON": [],
        "EVENING": []
      }
    }
  ],
  "warnings": [],
  "tips": ["..."]
}
```

状态字段：

| 状态 | `matched` | `bookable` | `resourceId/specId` | `priceLabel` |
|---|---|---|---|---|
| 匹配付费 | true | true | 都有 | `¥{spec.price}` |
| 匹配免费 | true | false | `resourceId` 有，`specId` 为空 | `免费/无需购票` |
| 未匹配 | false | false | 都为空 | `以官方为准` |

`unavailableReason` 可选，用于解释免费/售罄/未匹配。

`warnings` 是字符串数组，每个元素是一条可直接展示的中文提示，例如：

```json
["第1天：玉龙雪山（4.0h）超出当日节奏容量，未安排"]
```

规则：

- 超过每日景点上限而截断的景点必须写一条 warning。
- 因当前及后续时段容量/数量不足而未安排的景点，必须写 warning。
- `warnings` 随草稿行程一起持久化，并由 itinerary 的创建/查询响应返回；
  不能只写日志。

## 节奏与容量

| Pace | Daily max | Daily hours | Morning | Afternoon | Evening |
|---|---:|---:|---:|---:|---:|
| `RELAXED` | 2 | 4 | 3 | 1 | 0 |
| `BALANCED` | 4 | 8 | 4 | 4 | 0 |
| `VIBRANT` | 6 | 12 | 4 | 5 | 3 |

每时段硬上限 3 个。

## 装箱规则

1. 截断当日 AI 列表到 daily max。
2. 景点是不可拆分块。
3. 从当前时段指针开始，找第一个剩余时长足够且数量 <3 的时段。
4. 当前时段不足时允许向后溢出；溢出后不得回填更早时段。
5. 全部后续时段都不足时，从该景点起停止当天装箱，剩余写入 `warnings`。
6. 首个 4h 景点在上午 4h 容量且上午为空时必须进入上午。
7. `RELAXED` 晚上容量 0；页面仍显示空晚上区块。

## Tool and whitelist

- 工具名：`searchAttractions(destination)`。
- 只返回 `ON_SHELF` 的 `ATTRACTION`。
- 工具通过 Feign 调 `GET /inventory/attractions`；结果项字段：
  `resourceId/name/province/city/level/honorTitle/summary/suggestedHours/`
  `ticketPrice/free/latitude/longitude/coordinateStatus/specId/specName/price/available`。
- 服务端只接受本次规划运行工具结果内的 `(resourceId, specId)`。
- 非法对必须同时置空；不得只清一个。
- 同一任务内多次工具调用的结果必须合并为累积白名单，后一次不得覆盖前一次。

## Mock mode

- 配置：`planning.planner=${PLANNER_PLANNER:mock}`。
- mock 输出同一 JSON 契约。
- mock 必须按任务目的地选择候选，不得固定云南。
- `DEEPSEEK_API_KEY` 为空时，即使配置了 `deepseek` 也自动回退 mock。

## Degradation

- `planning.deepseek-timeout-ms`（默认 30000）是单次 DeepSeek HTTP 请求超时。
- `planning.generate-timeout-ms`（默认 120000）是包含工具往返的整次生成总超时。
- 两种超时的 `failureReason` 必须可区分：请求超时与任务总超时。
- 工具调用失败同样置任务 `FAILED` 并写 `failureReason`，不得挂死。
