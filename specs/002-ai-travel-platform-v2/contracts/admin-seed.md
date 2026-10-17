# admin-seed v2

## POST /admin/seed/scenic-spots

鉴权：JWT + `role=ADMIN`。

响应：`202 Accepted`

```json
{
  "runId": "...",
  "status": "RUNNING"
}
```

Rules:

- 不等待 AI/地理编码完成。
- 同一时间只允许一个 `RUNNING`；重复触发返回 409。
- 缺少 `DEEPSEEK_API_KEY` 或 `AMAP_WEB_SERVICE_KEY` 时失败并给出明确配置错误。

## GET /admin/seed/scenic-spots/{runId}

响应：

```json
{
  "runId": "...",
  "status": "PARTIAL_FAILED",
  "startedAt": "2026-10-17T10:00:00Z",
  "finishedAt": "2026-10-17T10:04:00Z",
  "summary": {
    "provinces": 34,
    "batches": 6,
    "generated": 204,
    "inserted": 198,
    "skippedExisting": 2,
    "skippedInvalid": 3,
    "failed": 1,
    "coordinatePending": 5
  },
  "batches": [
    {
      "index": 1,
      "provinces": ["北京", "天津", "河北", "山西", "内蒙古", "辽宁"],
      "attempt": 2,
      "mappingFailed": false,
      "generated": 36,
      "inserted": 35,
      "skippedExisting": 0,
      "skippedInvalid": 1,
      "failed": 0,
      "coordinatePending": 2,
      "errors": []
    }
  ]
}
```

Status:

- `RUNNING`
- `SUCCEEDED`
- `PARTIAL_FAILED`
- `FAILED`

## Import rules

- 34 个省级行政区，按 6,6,6,6,6,4 分批。
- 每省 6 个景区；约 204 条。
- JSON 映射失败整批重试 1 次。
- 字段不合格丢弃并计入 `skippedInvalid`。
- 高德地理编码失败：入库，`coordinate_status=PENDING`。
- `(name, city)` 已存在：`skippedExisting`，不更新任何字段。
- 付费景区创建默认成人票规格与未来 365 天库存。
- `image_url` 永不被种子覆盖。

## Report and audit

- 报告持久化到 `scenic_seed_run.report_json`。
- 记录 `requested_by` 管理员 userId。
- 不新增 Kafka 事件；报告通过查询获取。
