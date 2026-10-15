# itinerary（行程服务）

职责：行程与行程项 CRUD、状态机、确认。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /itineraries | 创建草稿行程（供 ai-planning 调用） |
| GET | /itineraries/{id} | 查看行程与每日明细（owner 隔离） |
| PUT | /itineraries/{id} | 编辑（无订单时；编辑使 CONFIRMED 回 DRAFT） |
| POST | /itineraries/{id}/confirm | 行程确认 → 可预订 |
| DELETE | /itineraries/{id} | 删除（已产生订单的行程拒绝删除） |
| POST | /itineraries/{id}/lock | 订单预订时锁定行程（由 booking-order 触发） |
| POST | /itineraries/{id}/release | 订单取消/失败后回到 CONFIRMED（由 booking-order 触发） |
| POST | /itineraries/{id}/complete | 全部项已支付后置为 COMPLETED（由 booking-order 触发） |

状态机：`DRAFT → CONFIRMED → LOCKED → COMPLETED`；取消/失败回 `CONFIRMED`。

事件：`ItineraryConfirmed` / `ItineraryChanged`（已下单行程被修改时触发，见 GLOSSARY）。
