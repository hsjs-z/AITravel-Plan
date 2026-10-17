# 景点作为可预订资源：新增 ATTRACTION 类型

v1 将景点定义为内容资源，门票才是可交易资源。v2 要求景区本身携带级别、称号、
建议游玩时长、票价、坐标和图片，并被 AI 规划工具直接返回、被行程项直接绑定。
继续把景区放在 `CONTENT` 会让“内容资源恒不可下单”的 v1 契约与 v2 冲突。

**Decision**:

- `resource.type` 增加 `ATTRACTION`，保留 `TRADABLE` 与 `CONTENT`。
- `ATTRACTION` 是 v2 景区的一级资源类型。
- 付费景区拥有默认 `TICKET_TYPE` 规格，实际成交价来自 `spec.price`。
- 免费景区 `ticket_price=0`，可加入行程，但不创建交易规格、不进入支付链路。
- 未匹配景区没有 `resourceId/specId`，仍可展示和加入行程，标记“暂不可预订”。
- 旧 v1 资源不删除；云南旧数据回填 province/city 后参与查重。

**Consequences**:

- v2 的规划工具可以直接返回一个景区资源及其门票 specId。
- 交易链路仍需满足“库存单元 = 资源 + 日期 + 规格”；`ATTRACTION` 不能绕过库存锁定。
- inventory 锁定边界必须校验 `resource.type=ATTRACTION` 且 `spec.specType=TICKET_TYPE`。
- `GLOSSARY.md` 中“景点是内容”的 v1 定义需要在 v2 文档中显式标注为历史语义。
- 内容资源页和旧 `CONTENT` 检索行为保持不变。

**Alternatives considered**:

- 景区继续 `CONTENT`，另建可交易门票 `TRADABLE`：更贴近 v1 词汇，但工具、行程项、
  订单和前端需要在两个资源 ID 之间做间接映射，白名单也更复杂。
- 所有景区改为 `TRADABLE`：会破坏内容资源页“仅展示、不下单”的语义。

**Status**: accepted

**Date**: 2026-10-17
