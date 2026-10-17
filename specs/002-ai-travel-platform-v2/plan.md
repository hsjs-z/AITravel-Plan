# Implementation Plan: AI 旅行平台 v2

**Branch**: `codex/v2-ai-travel-platform` | **Date**: 2026-10-17 |
**Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/002-ai-travel-platform-v2/spec.md`

## Summary

v2 在 v1 已验收闭环上增加四组能力：全国景区种子数据、Spring AI 真工具调用、
规划表单与结果页重做、门票交易收敛。resource 成为规划与预订的唯一事实源；
AI 只负责按天生成有序景点列表，服务端负责白名单、时段装箱、免费/未匹配状态和落草稿。

## Technical Context

**Language/Version**: JDK 17

**Primary Dependencies**:

- Spring Boot `3.5.15`
- Spring Cloud `2025.0.3`
- Spring Cloud Alibaba `2025.0.0.0`
- Spring AI `1.0.9`
- Spring AI DeepSeek starter `1.0.9`
- Vue 3 + Vite + TypeScript + Element Plus（保持现有底座）
- 高德 Web JS API 2.0 + 高德 Web 服务地理编码 API

**Version verification (2026-10-17)**:

All candidate coordinates were checked against Maven Central and returned HTTP 200:

- `spring-boot-dependencies:3.5.15`
- `spring-cloud-dependencies:2025.0.3`
- `spring-cloud-alibaba-dependencies:2025.0.0.0`
- `spring-ai-bom:1.0.9`
- `spring-ai-starter-model-deepseek:1.0.9`
- `spring-cloud-starter-alibaba-nacos-discovery:2025.0.0.0`

The exact URLs and compatibility notes are recorded in [research.md](./research.md) R-001.
Spring AI 1.0.9's DeepSeek starter declares `spring-boot-starter:3.5.15`;
SCA 2025.0.0.0 is on the Spring Cloud 2025.0.x line.

**Storage**: MySQL 8.0（每服务独立 schema，Flyway 管理）；Redis；Kafka

**Testing**: JUnit 5 + Mockito + Spring Boot Test；前端 `vue-tsc -b && vite build`；
真实 HTTP 冒烟；Prometheus/Tempo 证据

**Target Platform**: 本地 Docker 中间件 + 本机运行 9 个 Spring Boot 服务 + Vite 前端

**Project Type**: Monorepo；Spring Cloud 微服务 + Vue Web 前端

**Performance Goals**:

- 检索路径保持 v1 的 p95 < 1s、错误率 < 1% 基线。
- 规划任务仍为异步，提交接口不等待 AI。
- 景区导入异步执行，HTTP 触发立即返回 `202`；单批报告可查询。
- 结果页地图定位为前端交互，不新增同步后端阻塞。

**Constraints**:

- 只做 v2；不把酒店/车票重新引入前端行程与订单。
- 不回退越权 404、支付终态守卫、幂等、Outbox、AI 白名单、
  `@Qualifier("applicationTaskExecutor")` 和可观测性三件套。
- 高德 JS key 与服务端 Web 服务 key 只从 `.env` 读取，禁止硬编码。
- 种子重跑 insert-or-skip，不覆盖人工修改。

**Scale/Scope**:

- 34 个省级行政区 × 6 个景区 ≈ 204 条景区。
- 16 个固定热门城市。
- v2 表单天数 1–10；服务端保留 1–30 旧请求兼容；每日最多 6 个景点；
  每时段最多 3 个。
- 结果页至少覆盖 10 天 × 3 时段的折叠结构。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. 微服务工程结构 | 仍使用 `common` + 9 个独立模块；跨服务只走 OpenFeign/Kafka | PASS |
| II. 技术栈锁定 | Boot/Cloud/SCA/Spring AI 基线升级，按治理流程记录 ADR-0006 | PASS with ADR |
| III. 核心链路优先 | 规划→行程→预订→支付→通知闭环不变；只收敛为门票 | PASS |
| IV. 分布式一致性与可靠事件 | Saga、库存补偿、Outbox、消费幂等保持不动 | PASS |
| V. 支付抽象与幂等 | `PaymentGateway`、Mock、幂等和终态守卫不动 | PASS |
| VI. AI 规划异步化与任务状态机 | 保持异步和 `PENDING→RUNNING→SUCCEEDED/FAILED` | PASS |
| VII. 并发安全与库存原子扣减 | 门票锁定仍走 Redis Lua + MySQL 兜底；新增类型守卫不改变原子性 | PASS |
| VIII. 可观测性 | 三件套保留；新增导入与工具调用纳入指标/链路 | PASS |
| IX. 代码标准与分层 | 新代码继续 Controller/Service/Repository；接口文档同步 | PASS |
| X. 质量底线 | 新增装箱、白名单、种子、管理员、门票边界测试；全量回归 | PASS |

No unresolved constitution violations. The dependency baseline upgrade is authorized by
the user's approval and must be recorded as ADR-0006 before code changes.

## Project Structure

### Documentation

```text
specs/002-ai-travel-platform-v2/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── README.md
│   ├── ai-planning.md
│   ├── inventory.md
│   ├── itinerary.md
│   ├── booking-order.md
│   ├── user-auth.md
│   └── admin-seed.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code

```text
common/
gateway/
services/
├── user-auth/
├── inventory/
├── itinerary/
├── ai-planning/
├── booking-order/
├── payment/
└── notification/
frontend/
├── src/api/
├── src/components/planning/       # new
├── src/views/planning/            # form + result
├── src/views/itinerary/           # slot/order display and draft edit
├── src/views/order/               # ticket-only create
└── src/router/
docs/adr/
deploy/
```

**Structure Decision**: 沿用现有 monorepo 模块与包结构。新增前端结果页和
`components/planning/` 组件目录；不新建独立前端工程，不替换现有 axios 请求层。

## Phase 0: Research Outcomes

- **Spring AI baseline**: Boot 3.5.15 + Cloud 2025.0.3 + SCA 2025.0.0.0 +
  Spring AI 1.0.9；Maven Central 200 证据见 `research.md` R-001。
- **Resource model**: 增加 `ATTRACTION`；付费景区有默认门票规格，免费景区无交易规格。
- **Free/unmatched**: 两种状态分开建模，免费可加入行程但不支付；未匹配无 ID。
- **Slot packing**: 不可拆分、按顺序、可向后溢出、不可回填；慵懒晚上容量 0。
- **Seed import**: 异步 `202 + runId`；6 批；insert-or-skip；报告持久化。
- **Admin auth**: role + JWT claim + 环境变量引导；非管理员 404。
- **Budget**: 前端删除，API/DB 保留可空兼容。

## Phase 1: Design & Contracts

### Data model

见 [data-model.md](./data-model.md)。核心迁移：

- inventory：`V3__resource_attraction_v2.sql`、`V4__scenic_seed_run.sql`
- ai-planning：`V3__planning_pace.sql`
- itinerary：`V3__itinerary_item_slots.sql`
- user-auth：`V1__init.sql`

### Contracts

- [contracts/ai-planning.md](./contracts/ai-planning.md)
- [contracts/inventory.md](./contracts/inventory.md)
- [contracts/itinerary.md](./contracts/itinerary.md)
- [contracts/booking-order.md](./contracts/booking-order.md)
- [contracts/user-auth.md](./contracts/user-auth.md)
- [contracts/admin-seed.md](./contracts/admin-seed.md)

### Validation

见 [quickstart.md](./quickstart.md)。V2-02 的强制门禁为：

1. `mvn clean install` 全量通过。
2. v1 主链路回归通过。
3. Prometheus 指标可查询。
4. Tempo 能还原 AI → 订单 → 支付 trace。
5. 只有以上全部通过，才允许进入 V2-03。

## Implementation Phases

### V2-01 Documentation and ADR

- 新增 `specs/002-ai-travel-platform-v2/` 全套文档与任务清单。
- 新增 `docs/adr/0005-attraction-as-bookable-resource.md`。
- 新增 `docs/adr/0006-v2-dependency-baseline.md`。
- 更新 `.specify/feature.json` 指向 v2 规格目录。
- 不改业务代码、依赖、数据库或部署配置。

### V2-02 Framework Baseline

- 修改根 `pom.xml` 的版本属性与 Spring AI BOM。
- 修改各模块 `pom.xml` 只做版本对齐，不引入新业务行为。
- 重跑完整 v1 测试、主链路 HTTP 验收、Prometheus 查询与 Tempo 链路。
- **硬门禁**：未通过不得进入 V2-03。

### V2-03 User Persistence and Admin Role

- user-auth 增加 JPA/MySQL/Flyway。
- `app_user` 角色、JWT role claim、gateway `X-User-Role`。
- 管理员环境变量引导。
- 保留注册/登录/当前用户行为。

### V2-04 Resource ATTRACTION and Ticket Model

- `ResourceType.ATTRACTION`、resource 新字段、唯一键与索引。
- 付费景区默认门票规格与库存；免费景区无交易规格。
- inventory 锁定边界只接受门票规格。
- 旧云南数据回填 province/city。

### V2-05 Scenic Spot Seed Pipeline

- 管理员异步导入接口与报告。
- 6 批 DeepSeek 结构化输出、整批重试、字段校验。
- 高德 Web 服务地理编码与坐标待补。
- insert-or-skip，不覆盖人工修改。

### V2-06 Inventory Attraction Search

- `type=ATTRACTION` 检索、城市/省份匹配、价格/坐标/图片/规格。
- 内部 `searchAttractions` 工具查询。

### V2-07 Spring AI Tool Calling

- `ChatClient` + DeepSeek starter，删除 `RestTemplate` 直连。
- `searchAttractions(destination)` 工具。
- 白名单、mock 新结构、`PLANNER_PLANNER` 切换。

### V2-08 Planning Output and Slot Packing

- 归一化 AI JSON、节奏、每日上限、时段装箱、警告。
- 草稿行程项带时段与排序。

### V2-09 Itinerary Item APIs

- 行程项名称/时长/时段/排序。
- 草稿态添加/删除接口；每时段≤3。
- 确认/锁定后只读。

### V2-10 Frontend Form and Result Page

- 规划表单重做。
- 结果页、地图、AI 建议、免费/未匹配文案。
- 16 城按钮和自由输入。

### V2-11 Ticket-only Booking

- 前端只列门票；默认数量=出行人数。
- 服务端拒绝酒店/车票锁定。
- Saga、幂等、支付终态回归。

### V2-12 Full Regression and Deployment

- 全量测试、fat jar 校验、新端点真实 HTTP 探活。
- Prometheus/Grafana/Tempo 证据。
- 8 个既有页面行为回归。

## Complexity Tracking

| Item | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Boot/Cloud/SCA/Spring AI 同步升级 | Spring AI 1.0.9 以 Boot 3.5.15 编译，SCA 2025 使用 Cloud 2025 线 | 只加 BOM 会形成不受支持的运行时组合 |
| 新增 `ATTRACTION` 类型 | v2 景区需要坐标、图片、时长、门票价并可被直接绑定 | 继续 `CONTENT` 会与“内容不可下单”冲突 |
| 异步种子运行 + 报告表 | 6 次 AI + ~200 次地理编码不适合同步 HTTP | 同步 POST 易超时且无法查询批次明细 |
