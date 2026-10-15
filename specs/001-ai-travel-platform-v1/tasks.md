---
description: "Task list for AI 旅行规划平台 v1"
---

# Tasks: AI 旅行规划平台 v1

**Input**: Design documents from `specs/001-ai-travel-platform-v1/`（plan.md、spec.md、
research.md、data-model.md、contracts/、quickstart.md）

**Tests**: 本特性含强制测试任务——宪章原则 X 与 spec FR-044 要求订单状态机、Saga、
幂等、库存扣减必须有自动化测试覆盖，未通过不得视为完成。

**Organization**: 按用户故事分阶段；每个任务标注所属服务模块与文件路径。

## Format: `[ID] [P?] [Story] Description`

- `[P]`: 可并行（不同文件、无未完成依赖）
- `[Story]`: 所属用户故事（US1–US8，映射 spec.md）
- 描述含精确文件路径

> 修订说明：T073（user-auth Controller）与 T074（检索压测）为 analyze 后追加，
> ID 采用追加编号；任务实际执行顺序以所在 Phase 与依赖关系为准。
>
> M2 执行说明（2026-10-15）：进入 M2 时核查发现 M1 的事件发布任务
> T023/T029/T039/T045/T052 未落地（全仓库无 Kafka 依赖、无 Outbox 投递器、
> notification 模块为空骨架），而 US7 的独立验证依赖这些事件。因此 M2 实际执行顺序为：
> 先补 Outbox + Kafka 事件底座与上述 5 个事件发布任务，再实现 US7；US6 不受阻塞先行。

## 服务启动顺序与依赖

运行时启动顺序（编排见 `deploy/docker-compose.yml`）：

1. MySQL、Redis、Kafka、Nacos（基础设施）
2. common（编译期依赖，非运行时服务）
3. user-auth（账号/JWT，其余服务鉴权依赖）
4. inventory（资源目录 + 库存原语，被规划与预订查询）
5. itinerary（行程，被 ai-planning / booking-order 调用）
6. ai-planning（依赖 inventory + itinerary）
7. payment（被 booking-order 调用，先于 booking-order 注册）
8. booking-order（依赖 inventory + itinerary + payment）
9. notification（消费各服务事件，可晚于上游启动）
10. gateway（统一入口，最后就绪即可）
11. frontend（依赖 gateway）

## 里程碑映射

| 里程碑 | 内容 | 覆盖阶段 |
|--------|------|----------|
| M0 骨架与基础设施 | 聚合工程 + 中间件 + 公共能力 + 认证 + 网关 | Phase 1–2 |
| M1 主线闭环（P1） | 规划→行程→预订→库存→支付端到端 | Phase 3–7（US1–US5） |
| M2 检索与通知（P2） | 资源检索 + 异步通知 | Phase 8–9（US6–US7） |
| M3 内容与前端体验（P3） | 内容资源 + Web 主流程页面 | Phase 10–11 |
| M4 质量收尾 | 可观测性 + 全量测试 + 端到端验证 + 合规复检 | Phase 11（收尾任务） |

---

## Phase 1: Setup (M0 项目初始化)

**Purpose**: 聚合工程、模块骨架、中间件与前端脚手架

- [X] T001 创建根聚合 `pom.xml`：module 列表 + dependencyManagement 锁定宪章 II 全部栈版本
  （JDK17/Boot3/SCA/Spring AI/DeepSeek/Kafka/Redis/MySQL），文件 `pom.xml`
- [X] T002 [P] 创建 `common` 公共模块骨架与 `pom.xml`，包根 `com.travelplatform.common`，
  文件 `common/pom.xml`、`common/src/main/java/com/travelplatform/common/`
- [X] T003 [P] 创建 `gateway` 模块骨架与 `pom.xml`，文件 `gateway/pom.xml`
- [X] T004 [P] 创建 7 个业务服务空模块与 `pom.xml`（user-auth、inventory、itinerary、
  ai-planning、payment、booking-order、notification），每个含
  `services/<svc>/pom.xml` 与 `src/main/java/com/travelplatform/<svc>/`
- [X] T005 [P] 初始化前端 Vue 3 + Vite + TypeScript 脚手架（含 Element Plus），文件 `frontend/`
- [X] T006 编写本地中间件编排与初始化脚本（MySQL、Redis、Kafka、Nacos；每个服务独立 schema），
  文件 `deploy/docker-compose.yml`、`deploy/init/`
- [X] T007 [P] 编写 `.gitignore` 与 `.env.example`（数据库口令、DeepSeek API Key、支付凭据仅占位），
  文件 `.gitignore`、`.env.example`

---

## Phase 2: Foundational (M0 阻塞性前提)

**Purpose**: 所有用户故事开始前必须就绪的公共能力

- [X] T008 在 common 实现统一响应体、错误码与业务异常 DTO，
  文件 `common/src/main/java/com/travelplatform/common/api/`
- [X] T009 [P] 在 common 封装 OpenTelemetry + traceId 传播（HTTP header `X-Trace-Id`、
  Kafka header `traceId`），文件 `common/src/main/java/com/travelplatform/common/observability/`
- [X] T010 [P] 在 common 实现 Outbox 抽象（OutboxMessage、OutboxPublisher、轮询投递器），
  文件 `common/src/main/java/com/travelplatform/common/outbox/`
- [X] T011 [P] 在 common 实现 Saga 基础抽象（SagaStep、Compensation），
  文件 `common/src/main/java/com/travelplatform/common/saga/`
- [X] T012 [P] 在 common 实现幂等键工具与 Kafka 消费去重基础，
  文件 `common/src/main/java/com/travelplatform/common/idempotency/`
- [X] T013 [P] 在 user-auth 实现 User 实体、Repository 与 JWT 签发/校验，
  文件 `services/user-auth/src/main/java/com/travelplatform/userauth/`
- [X] T073 实现 user-auth 注册/登录/当前用户 Controller 与 OpenAPI 文档
  （POST /auth/register、POST /auth/login、GET /users/me），
  文件 `services/user-auth/src/main/java/com/travelplatform/userauth/api/AuthController.java`
- [X] T014 在 gateway 实现路由配置、JWT 校验过滤器、`userId` 透传与 Sentinel 入口限流，
  文件 `gateway/src/main/java/com/travelplatform/gateway/`
- [X] T015 为各服务编写 Nacos bootstrap 配置与本地 profile，
  文件 `services/*/src/main/resources/application.yml`

**Checkpoint**: 基础就绪，各用户故事可并行开工

---

## Phase 3: User Story 1 - 用自然语言获得完整行程方案 (Priority: P1) 🎯 MVP

**Goal**: 提交规划需求 → 秒级返回任务编号 → 状态查询 → 成功产出草稿行程与三维推荐

**Independent Test**: 登录后提交「云南 5 天 4 晚、双人、预算 8000」，任务经历
`PENDING → RUNNING → SUCCEEDED` 并得到草稿行程；失败可重试且不产生重复方案。

### Implementation for User Story 1

- [X] T016 [US1] 实现 PlanningTask 实体与 Repository（字段含 status、
  `failureReason`、`retryOf`、`itineraryId`），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/domain/PlanningTask.java`
- [X] T017 [US1] 实现规划任务状态机 `PENDING → RUNNING → SUCCEEDED | FAILED`（FAILED 可重试回 PENDING），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/domain/PlanningTaskStateMachine.java`
- [X] T018 [US1] 实现 DeepSeek 客户端（DeepSeek OpenAI 兼容端点，异步执行，禁止同步阻塞），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/DeepSeekClient.java`
- [X] T019 [US1] 实现推荐工具：经 inventory candidates 接口获取候选清单（spec/价格/库存），
  注入 DeepSeek 令其输出 resourceId/specId，并做服务端校验拒绝编造 ID，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/RecommendationTool.java`
- [X] T020 [US1] 实现 itinerary 创建草稿行程的 Feign 客户端（规划成功即落为草稿行程），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/ItineraryClient.java`
- [X] T021 [US1] 实现规划服务（提交/查询/重试；同一需求重试只保留最后成功草稿），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningService.java`
- [X] T022 [US1] 实现 Controller 与 OpenAPI 文档（POST /planning/tasks、GET /planning/tasks/{id}、
  POST /planning/tasks/{id}/retry），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/api/PlanningController.java`
- [X] T023 [US1] 实现 PlanningSucceeded/PlanningFailed 事件（Outbox 发布），
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningEventPublisher.java`

**Checkpoint**: US1 可独立演示（MVP）

---

## Phase 4: User Story 2 - 查看、编辑与确认行程 (Priority: P1)

**Goal**: 查看/编辑草稿行程、确认后可预订、owner 隔离、已下单不可删

**Independent Test**: 预置行程可独立验证查看/编辑/确认/删除与越权拒绝（无需真实规划任务）。

### Implementation for User Story 2

- [X] T024 [US2] 实现 Itinerary 与 ItineraryItem 实体 + Repository（字段与约束按 data-model.md），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/`
- [X] T025 [US2] 实现行程状态机 `DRAFT → CONFIRMED → LOCKED → COMPLETED`（无订单时编辑回 DRAFT），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryStateMachine.java`
- [X] T026 [US2] 实现行程服务（创建/查看/编辑/确认/删除，全部按 userId 隔离），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryService.java`
- [ ] T027 [US2] 实现生命周期服务（订单支付成功锁定已订项/解锁未订项；取消/失败回 CONFIRMED），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryLifecycleService.java`
- [X] T028 [US2] 实现 Controller（OpenAPI 文档统一在收尾阶段接入），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/api/ItineraryController.java`
- [X] T029 [US2] 实现 ItineraryConfirmed/ItineraryChanged 事件（Outbox，已下单行程修改才发 Changed），
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryEventPublisher.java`

**Checkpoint**: US1+US2 均独立可用

---

## Phase 5: User Story 3 - 一键联合预订多个资源 (Priority: P1)

**Goal**: 一键预订（默认全选、可勾选子集）→ Saga 逐项锁定 → 失败补偿释放 → 订单状态机

**Independent Test**: 预置资源 + 确认行程验证全成功/中途失败回滚/重复提交只产一张订单。

### Tests for User Story 3（强制，先写先失败）

- [X] T030 [P] [US3] 订单状态机单测：`CREATED→BOOKED→PAID→CONFIRMED` 与
  `CANCELLED/FAILED` 终态、非法流转拒绝，
  文件 `services/booking-order/src/test/java/com/travelplatform/bookingorder/domain/OrderStateMachineTest.java`
- [X] T031 [P] [US3] Saga 编排与补偿单测：部分资源失败时已锁定项被幂等释放、不虚增库存，
  文件 `services/booking-order/src/test/java/com/travelplatform/bookingorder/application/SagaOrchestratorTest.java`

### Implementation for User Story 3

- [X] T032 [US3] 实现 Order、OrderItem、BookingSettlementLog 实体 + Repository，
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/domain/`
- [X] T033 [US3] 实现订单状态机，
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/domain/OrderStateMachine.java`
- [X] T034 [US3] 实现 inventory 锁定/释放与 itinerary 生命周期调用的 Feign 客户端，
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/infrastructure/`
- [X] T035 [US3] 实现 Saga 编排器（逐项 `purpose=BOOKING` 锁定；任一失败对已锁定项幂等释放补偿），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/SagaOrchestrator.java`
- [X] T075 [US3] 实现补偿重试器：定时扫描 FAILED/CANCELLED 订单中仍 BOOKED 且带 lockId 的明细，
  重试 release 直至 COMPENSATED（承接 FR-016/FR-017 的「补偿最终完成」），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/CompensationRetryScheduler.java`
- [X] T036 [US3] 实现一键预订服务（幂等键，同一行程重复提交只产生一张订单），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderService.java`
- [X] T037 [US3] 实现未支付超时处理（15 分钟 → 自动 `CANCELLED` + 释放库存），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderTimeoutHandler.java`
- [X] T038 [US3] 实现 Controller（POST /orders、GET /orders、GET /orders/{id}、
  POST /orders/{id}/cancel），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/api/OrderController.java`
- [X] T039 [US3] 实现 OrderCreated/Booked/Paid/Confirmed/Cancelled/Failed 事件（Outbox），
  文件 `services/booking-order/src/main/java/com/travelplatform/bookingorder/application/OrderEventPublisher.java`

**Checkpoint**: 联合预订闭环可用（不含支付，下一故事衔接）

---

## Phase 6: User Story 4 - 库存占用准确、绝不超卖 (Priority: P1)

**Goal**: 库存单元=资源+日期+规格；原子锁定/幂等释放；售罄≠下架；并发不超卖

**Independent Test**: 对同一库存单元并发大量预订，成功数 ≤ 可用库存；重复释放不虚增。

### Tests for User Story 4（强制，先写先失败）

- [X] T040 [P] [US4] 库存锁定/释放单测：重复释放幂等、库存不足拒绝（并发扣减由 Redis Lua 保证），
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/application/InventoryLockServiceTest.java`

### Implementation for User Story 4

- [X] T041 [US4] 实现 Resource、Spec、InventoryUnit、InventoryLock 实体 + Repository
  （Resource.type=TRADABLE/CONTENT；Spec 含 occupancy 属性），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/domain/`
- [X] T042 [US4] 实现 Redis Lua 原子锁定与幂等释放（check-and-decrement/check-and-increment），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/infrastructure/InventoryLockRedis.java`
- [X] T043 [US4] 实现库存服务（query/lock/release，按资源+日期+规格，purpose 区分 PLANNING/BOOKING），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
- [X] T044 [US4] 实现 Controller（GET /inventory/query、POST /inventory/locks、
  POST /inventory/locks/{id}/release），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/api/InventoryController.java`
- [X] T045 [US4] 实现 InventoryLocked/Released/SoldOut 事件（Outbox），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryEventPublisher.java`

**Checkpoint**: 库存原语就绪，US3 的锁依赖真实可用

---

## Phase 7: User Story 5 - 用模拟支付完成付款闭环 (Priority: P1)

**Goal**: PaymentGateway 抽象 + Mock 实现；支付/退款幂等；回调对账；支付成功自动 CONFIRMED

**Independent Test**: 预置订单验证支付成功/重复提交/回调重复乱序/退款，不依赖真实通道。

### Tests for User Story 5（强制，先写先失败）

- [X] T046 [P] [US5] 支付/退款幂等单测：重复支付返回首次结果、重复退款只生效一次、金额≤实付，
  文件 `services/payment/src/test/java/com/travelplatform/payment/application/PaymentIdempotencyTest.java`

### Implementation for User Story 5

- [X] T047 [US5] 实现 PaymentGateway 接口、MockPaymentGateway 与微信实现位（不得改调用方），
  文件 `services/payment/src/main/java/com/travelplatform/payment/application/PaymentGateway.java`
- [X] T048 [US5] 实现 Payment、Refund、CallbackRecord 实体 + Repository（含 idempotencyKey 唯一），
  文件 `services/payment/src/main/java/com/travelplatform/payment/domain/`
- [X] T049 [US5] 实现支付服务（创建/确认/回调对账/幂等；金额不一致不置已支付并记异常），
  文件 `services/payment/src/main/java/com/travelplatform/payment/application/PaymentService.java`
- [X] T050 [US5] 实现退款服务（幂等、金额≤实付，并入 PaymentService.refund），
  文件 `services/payment/src/main/java/com/travelplatform/payment/application/RefundService.java`
- [X] T051 [US5] 实现 Controller（POST /payments、POST /payments/{id}/confirm、
  POST /payments/callback、POST /refunds），
  文件 `services/payment/src/main/java/com/travelplatform/payment/api/`
- [X] T052 [US5] 实现 PaymentSucceeded/Failed/RefundSucceeded 事件（Outbox），
  文件 `services/payment/src/main/java/com/travelplatform/payment/application/PaymentEventPublisher.java`

**Checkpoint**: P1 主线（规划→行程→预订→库存→支付）端到端跑通

---

## Phase 8: User Story 6 - 浏览与检索可交易资源 (Priority: P2)

**Goal**: 按目的地/日期/价格检索酒店/门票/车票/景点，展示价格与可预订状态

**Independent Test**: 预置资源验证检索/筛选/排序/空结果与详情展示。

### Implementation for User Story 6

- [X] T053 [US6] 实现资源检索/筛选/排序查询（目的地、日期、价格区间），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/ResourceSearchService.java`
- [X] T054 [US6] 实现 Controller 与 OpenAPI 文档（GET /resources/search、GET /resources/{id}），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/api/ResourceController.java`
- [X] T055 [US6] 在资源详情 DTO 中按 type 返回可交易/内容标识，售罄或下架不提供下单入口，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/api/ResourceResponse.java`

**Checkpoint**: US6 独立可用

---

## Phase 9: User Story 7 - 收到订单与行程变更通知 (Priority: P2)

**Goal**: 消费业务事件生成站内信/邮件；未读/已读与未读数；去重与补齐

**Independent Test**: 构造预订/支付事件验证通知生成、去重、补齐与未读/已读。

### Implementation for User Story 7

- [X] T056 [US7] 实现 Notification 实体 + Repository（type/channel/readStatus/dedupKey 唯一），
  文件 `services/notification/src/main/java/com/travelplatform/notification/domain/Notification.java`
- [X] T057 [US7] 实现事件消费者（按事件 id 去重；站内信必达、邮件可选；下游不可用补齐），
  文件 `services/notification/src/main/java/com/travelplatform/notification/application/NotificationConsumer.java`
- [X] T058 [US7] 实现通知服务（列表/未读数/标记已读，owner 隔离），
  文件 `services/notification/src/main/java/com/travelplatform/notification/application/NotificationService.java`
- [X] T059 [US7] 实现 Controller 与 OpenAPI 文档（GET /notifications、GET /notifications/unread-count、
  POST /notifications/{id}/read），
  文件 `services/notification/src/main/java/com/travelplatform/notification/api/NotificationController.java`

**Checkpoint**: US7 独立可用

---

## Phase 10: User Story 8 - 浏览与检索内容资源 (Priority: P3)

**Goal**: 美食/文化体验等内容资源展示与检索，不参与交易

**Independent Test**: 预置内容数据验证展示、检索与「无下单入口」隔离。

### Implementation for User Story 8

- [X] T060 [US8] 预置内容资源数据（景点、线路、美食、文化体验），
  文件 `deploy/seed/content-resources.sql`
- [X] T061 [US8] 扩展内容资源检索（复用 ResourceSearch，type=CONTENT，关键词/目的地），
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/ResourceSearchService.java`
- [X] T062 [US8] 实现前端内容资源展示页（无下单/支付入口），
  文件 `frontend/src/views/content/`

**Checkpoint**: 全部用户故事独立可用

---

## Phase 11: Polish & Cross-Cutting Concerns (M3–M4)

**Purpose**: 前端主流程、可观测性、全量测试与端到端验证

- [X] T063 [P] 实现前端 API 客户端与登录/注册页，
  文件 `frontend/src/api/`、`frontend/src/views/auth/`
- [X] T064 [P] 实现前端规划任务提交/状态轮询页，
  文件 `frontend/src/views/planning/`
- [X] T065 [P] 实现前端行程查看/编辑/确认页，
  文件 `frontend/src/views/itinerary/`
- [X] T066 [P] 实现前端一键预订（子集勾选/数量调整）与订单列表/详情页，
  文件 `frontend/src/views/order/`
- [X] T067 [P] 实现前端支付页（Mock）与通知中心（未读/已读），
  文件 `frontend/src/views/payment/`、`frontend/src/views/notification/`
- [X] T068 接入关键接口 QPS/P99 指标（Micrometer/Prometheus + actuator，各服务），
  文件 `common/src/main/java/com/travelplatform/common/metrics/` 与 `services/*/src/main/resources/`
- [X] T069 验证全链路 OpenTelemetry：一次操作按 traceId 还原 AI→订单→支付（含 Kafka 头透传），
  文件 `deploy/observability/`
- [ ] T070 补齐并全量通过核心逻辑单测（订单状态机/Saga/幂等/库存扣减），
  文件 `services/booking-order/src/test/`、`services/payment/src/test/`、`services/inventory/src/test/`
- [ ] T071 按 quickstart.md 跑 S1–S7 全场景端到端验证并修复问题，
  文件 `specs/001-ai-travel-platform-v1/quickstart.md`
- [X] T074 [US6] 实现检索路径性能/负载验证：95% 检索 <1s、1000 并发错误率 <1%
  （阈值写入断言），
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/performance/SearchLoadTest.java`
- [ ] T072 执行最终 Constitution Check 复检（原则 I–X）并同步 OpenAPI/GLOSSARY/ADR 文档

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1（Setup）：无前置，立即开始
- Phase 2（Foundational）：依赖 Phase 1，阻塞所有用户故事
- Phase 3–10（用户故事）：依赖 Phase 2；P1（US1–US5）优先，P2（US6–US7）次之，
  P3（US8）最后
- Phase 11（Polish）：依赖目标故事完成

### User Story Dependencies

- US1（P1）：Phase 2 后即可开始，无故事间依赖
- US2（P1）：Phase 2 后即可开始；US1 依赖其「创建草稿行程」接口（T020），故 US2 需先于 US1 联调
- US3（P1）：依赖 US4 的库存原语（T043/T044）与 US2 的行程接口
- US4（P1）：Phase 2 后即可开始，无故事间依赖
- US5（P1）：依赖 US3 的订单（支付挂订单）
- US6（P2）：复用 US4 的 inventory 服务
- US7（P2）：依赖各服务 Outbox 事件（尤其 US3/US5）
- US8（P3）：复用 US6 的检索能力

### 服务间调用依赖（实现时按此顺序联调）

- ai-planning → inventory、itinerary
- booking-order → inventory、itinerary、payment
- notification ← 消费 inventory/itinerary/booking-order/payment 事件
- gateway → 全部服务

### Within Each User Story

- 测试（强制项）先写并先失败，再实现
- 实体/状态机 → 服务 → Controller → 事件

### Parallel Opportunities

- Phase 1 的 T002–T005、T007 可并行
- Phase 2 的 T009–T013 可并行
- US4（T041–T045）可与 US1/US2 并行；US6 可与 US3/US5 并行
- 每个故事内的实体类任务、测试任务可并行

## Implementation Strategy

### MVP First（先 US1）

1. Phase 1 + Phase 2（M0）
2. Phase 3（US1）→ 独立验证：AI 规划能出草稿行程即 MVP

### 主线闭环（M1）

3. 按依赖顺序补齐 US2 → US4 → US3 → US5，每步用独立测试验证
4. 停止并验证 quickstart S1–S7 中主线部分（S1–S5）端到端跑通

### 增量交付

5. 加 US6 → US7（检索与通知），加 US8 + 前端主流程（内容与体验）
6. 收尾：可观测性 + 全量测试 + 合规复检

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- [Story] 标签映射 spec.md 用户故事，便于追踪
- 强制测试任务（T030/T031/T040/T046/T070）对应宪章 X 与 FR-044，未通过不得标记完成
- 每完成一个任务或逻辑组即提交
- 每个 checkpoint 可停下来独立验证
