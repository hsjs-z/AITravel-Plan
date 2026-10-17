---
description: "Task list for AI 旅行平台 v2"
---

# Tasks: AI 旅行平台 v2

**Input**: `specs/002-ai-travel-platform-v2/` 下的 spec、plan、research、data-model、
contracts 与 quickstart。

**Tests**: 用户要求验收优先，因此本清单包含装箱、白名单、种子、管理员、
门票边界和 v1 回归测试。每个任务一个 commit；完成代码后先提交验收证据，
用户确认后才把任务改为 `[X]`。

**Format**: `- [ ] TNNN [P?] [US?] Description with exact file path`

## Phase 1: Setup (V2-01)

**Purpose**: 只写 v2 规范、ADR 和任务清单；不碰业务代码。

- [X] T001 创建 v2 规范、计划、研究、数据模型和契约文档，
  文件 `specs/002-ai-travel-platform-v2/spec.md`、
  `specs/002-ai-travel-platform-v2/plan.md`、
  `specs/002-ai-travel-platform-v2/research.md`、
  `specs/002-ai-travel-platform-v2/data-model.md`、
  `specs/002-ai-travel-platform-v2/contracts/`
- [X] T002 新增景点可预订与依赖基线 ADR，
  文件 `docs/adr/0005-attraction-as-bookable-resource.md`、
  `docs/adr/0006-v2-dependency-baseline.md`
- [X] T003 更新 Spec Kit 当前 feature 指针，
  文件 `.specify/feature.json`
- [X] T004 生成规格质量清单并完成自检，
  文件 `specs/002-ai-travel-platform-v2/checklists/requirements.md`

**Checkpoint**: V2-01 文档待用户验收；业务代码、pom、迁移、前端均未修改。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 依赖基线、用户持久化/管理员、共享 schema。

**⚠️ CRITICAL**: V2-02 的 v1 全量回归 + Prometheus/Tempo 门禁未通过前，
不得开始 V2-03 及后续任务。

- [ ] T005 升级根依赖管理到 Boot 3.5.15、Cloud 2025.0.3、
  SCA 2025.0.0.0、Spring AI 1.0.9，
  文件 `pom.xml`
- [ ] T006 [P] 对齐各模块 pom，不引入业务行为，
  文件 `services/ai-planning/pom.xml`、`services/inventory/pom.xml`、
  `services/user-auth/pom.xml`、`services/itinerary/pom.xml`、
  `services/booking-order/pom.xml`、`services/payment/pom.xml`、
  `services/notification/pom.xml`、`gateway/pom.xml`、`common/pom.xml`
- [ ] T007 执行 V2-02 硬门禁：`mvn clean install`、v1 主链路回归、
  Prometheus 指标、Tempo trace，记录证据到
  `specs/002-ai-travel-platform-v2/quickstart.md`
- [ ] T008 将 user-auth 用户存储迁到 MySQL，
  文件 `services/user-auth/src/main/resources/application.yml`、
  `services/user-auth/src/main/resources/db/migration/V1__init.sql`、
  `services/user-auth/src/main/java/com/travelplatform/userauth/infrastructure/`
- [ ] T009 保留可测试的内存实现但默认关闭，
  文件 `services/user-auth/src/main/java/com/travelplatform/userauth/domain/InMemoryUserRepository.java`
- [ ] T010 增加用户角色与管理员环境变量引导，
  文件 `services/user-auth/src/main/java/com/travelplatform/userauth/domain/User.java`、
  `services/user-auth/src/main/java/com/travelplatform/userauth/domain/JwtService.java`、
  `services/user-auth/src/main/java/com/travelplatform/userauth/api/AuthController.java`、
  `services/user-auth/src/main/java/com/travelplatform/userauth/AdminAccountBootstrap.java`
- [ ] T011 网关解析 role、注入 `X-User-Role`、增加 `/admin/**` 路由，
  文件 `gateway/src/main/java/com/travelplatform/gateway/JwtAuthFilter.java`、
  `gateway/src/main/resources/application.yml`
- [ ] T012 [P] 新增 inventory v2 schema：ATTRACTION、resource 字段、唯一键和索引，
  文件 `services/inventory/src/main/resources/db/migration/V3__resource_attraction_v2.sql`
- [ ] T013 [P] 新增 itinerary 行程项时段/排序 schema，
  文件 `services/itinerary/src/main/resources/db/migration/V3__itinerary_item_slots.sql`
- [ ] T014 [P] 新增 ai-planning pace/budget/preferences schema，
  文件 `services/ai-planning/src/main/resources/db/migration/V3__planning_pace.sql`
- [ ] T015 回填 v1 云南资源的 province/city，
  文件 `services/inventory/src/main/resources/db/migration/V3__resource_attraction_v2.sql`

**Checkpoint**: 依赖、账号、管理员、schema 就绪；V2-02 门禁有证据。

---

## Phase 3: User Story 1 - 基于真实库存生成可执行行程 (P1) 🎯 MVP

**Goal**: Spring AI 真工具调用 + 白名单 + 新输出结构。

**Independent Test**: mock/deepseek 各跑一次规划；工具外 ID 绑定率为 0。

### Tests for User Story 1

- [ ] T016 [P] [US1] 白名单测试：工具外 `resourceId/specId` 必须同时置空，
  文件 `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/AttractionWhitelistTest.java`
- [ ] T017 [P] [US1] mock 目的地适配与装箱边界测试：覆盖 4h 首个景点、
  时段剩余不足、慵懒档晚上 0、无法整体放入，文件
  `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/MockPlannerClientTest.java`、
  `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/TimeSlotAllocatorTest.java`

### Implementation for User Story 1

- [ ] T018 [US1] 增加 inventory 景点工具查询接口，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/api/InventoryController.java`、
  `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
- [ ] T019 [US1] 扩展 ai-planning InventoryClient，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/InventoryClient.java`
- [ ] T020 [US1] 实现 `searchAttractions(destination)` 工具，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionSearchTool.java`
- [ ] T021 [US1] 用 Spring AI `ChatClient` 替换 RestTemplate 直连 DeepSeek，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/DeepSeekPlannerClient.java`
- [ ] T022 [US1] 增加新输出解析与归一化，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningOutputParser.java`
- [ ] T023 [US1] 实现时段装箱器，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/TimeSlotAllocator.java`
- [ ] T024 [US1] 接入工具结果、白名单、节奏与草稿落库，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/PlanningService.java`
- [ ] T025 [US1] 适配 mock 新结构与目的地候选，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/MockPlannerClient.java`
- [ ] T026 [US1] 扩展 planning task pace 与 Controller 契约，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/domain/PlanningTask.java`、
  `services/ai-planning/src/main/java/com/travelplatform/aiplanning/api/PlanningController.java`
- [ ] T027 [US1] 更新规划落库测试，
  文件 `services/ai-planning/src/test/java/com/travelplatform/aiplanning/application/PlanningServiceMaterializeTest.java`

**Checkpoint**: US1 可独立验证；mock 与 deepseek 都输出新契约。

---

## Phase 4: User Story 2 - 结构化规划表单 (P1)

**Goal**: 1–10 天、16 城/自由输入、节奏、偏好、人数默认 1。

**Independent Test**: 前端表单和请求载荷无需 AI 即可验证。

- [ ] T028 [P] [US2] 定义 16 个热门地区常量，
  文件 `frontend/src/views/planning/hotDestinations.ts`
- [ ] T029 [US2] 重做规划表单，
  文件 `frontend/src/views/planning/PlanningView.vue`
- [ ] T030 [US2] 更新前端规划类型与 API，
  文件 `frontend/src/api/types.ts`、`frontend/src/api/planning.ts`
- [ ] T031 [US2] 更新规划请求校验与兼容字段，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/api/PlanningController.java`
- [ ] T032 [US2] 增加前端构建与表单行为验收，
  文件 `frontend/src/views/planning/PlanningView.vue`

**Checkpoint**: 表单可独立演示；预算字段不再出现。

---

## Phase 5: User Story 3 - 草稿结果页、添加/删除与地图 (P1)

**Goal**: 左侧按天/时段、草稿增删、右侧地图、底部建议。

**Independent Test**: 草稿与确认行程分别打开，验证增删可见性和只读。

### Tests for User Story 3

- [ ] T033 [P] [US3] 测试每时段最多 3 个与添加/删除状态，
  文件 `services/itinerary/src/test/java/com/travelplatform/itinerary/application/ItinerarySlotCapacityTest.java`
- [ ] T034 [P] [US3] 测试确认/锁定后拒绝添加/删除，
  文件 `services/itinerary/src/test/java/com/travelplatform/itinerary/application/ItineraryReadOnlyTest.java`

### Implementation for User Story 3

- [ ] T035 [US3] 扩展 ItineraryItem 实体与查询排序，
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryItem.java`、
  `services/itinerary/src/main/java/com/travelplatform/itinerary/domain/ItineraryItemRepository.java`
- [ ] T036 [US3] 实现草稿添加/删除行程项接口，
  文件 `services/itinerary/src/main/java/com/travelplatform/itinerary/api/ItineraryController.java`、
  `services/itinerary/src/main/java/com/travelplatform/itinerary/application/ItineraryService.java`
- [ ] T037 [US3] 扩展 ItineraryClient Item 字段，
  文件 `services/ai-planning/src/main/java/com/travelplatform/aiplanning/infrastructure/ItineraryClient.java`
- [ ] T038 [US3] 新增结果页主视图，
  文件 `frontend/src/views/planning/PlanningResultView.vue`
- [ ] T039 [P] [US3] 新增按天折叠与时段组件，
  文件 `frontend/src/components/planning/DayAccordion.vue`
- [ ] T040 [P] [US3] 新增景点卡片与免费/未匹配文案，
  文件 `frontend/src/components/planning/AttractionCard.vue`
- [ ] T041 [P] [US3] 新增库存景点选择弹窗，
  文件 `frontend/src/components/planning/AttractionPicker.vue`
- [ ] T042 [P] [US3] 新增高德地图面板，
  文件 `frontend/src/components/planning/AmapPanel.vue`
- [ ] T043 [P] [US3] 新增 AI 旅行建议组件，
  文件 `frontend/src/components/planning/TravelTips.vue`
- [ ] T044 [US3] 更新路由与前端 API，
  文件 `frontend/src/router/index.ts`、`frontend/src/api/itinerary.ts`、
  `frontend/src/api/resources.ts`、`frontend/src/api/types.ts`
- [ ] T045 [US3] 增加前端环境声明与 Vite envDir，
  文件 `frontend/src/env.d.ts`、`frontend/vite.config.ts`
- [ ] T046 [US3] 更新行程编辑页以显示时段/排序，
  文件 `frontend/src/views/itinerary/ItineraryView.vue`

**Checkpoint**: 草稿增删、只读、地图、建议、文案状态全部可独立验收。

---

## Phase 6: User Story 4 - 只做门票交易 (P1)

**Goal**: 前端不再产生酒店/车票；服务端锁定边界只接受门票。

**Independent Test**: 门票订单成功；酒店/车票订单被拒绝；v1 交易回归通过。

- [ ] T047 [US4] 在 inventory 锁定边界增加门票类型守卫，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/InventoryService.java`
- [ ] T048 [US4] 前端预订页只列门票并默认数量=出行人数，
  文件 `frontend/src/views/order/OrderCreateView.vue`
- [ ] T049 [US4] 测试酒店/车票无法锁定，
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/application/InventoryServiceTest.java`
- [ ] T050 [US4] 回归 Saga、幂等、支付终态，
  文件 `services/booking-order/src/test/java/com/travelplatform/bookingorder/application/SagaOrchestratorTest.java`、
  `services/booking-order/src/test/java/com/travelplatform/bookingorder/application/OrderServiceIdempotencyTest.java`、
  `services/payment/src/test/java/com/travelplatform/payment/application/PaymentIdempotencyTest.java`

**Checkpoint**: v2 交易只出现门票明细。

---

## Phase 7: User Story 5 - 管理员导入全国景区 (P2)

**Goal**: 202 异步导入、6 批、重试、坐标、报告、insert-or-skip。

**Independent Test**: 管理员触发并轮询；检查 34 省/每省 6 条/16 城覆盖/重跑跳过。

### Tests for User Story 5

- [ ] T051 [P] [US5] 字段校验测试，
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/application/seed/ScenicSpotValidatorTest.java`
- [ ] T052 [P] [US5] 批次重试与 insert-or-skip 测试，
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/application/seed/ScenicSpotSeedServiceTest.java`

### Implementation for User Story 5

- [ ] T053 [US5] 新增种子运行表与状态，
  文件 `services/inventory/src/main/resources/db/migration/V4__scenic_seed_run.sql`、
  `services/inventory/src/main/java/com/travelplatform/inventory/domain/ScenicSeedRun.java`、
  `services/inventory/src/main/java/com/travelplatform/inventory/domain/ScenicSeedRunRepository.java`
- [ ] T054 [US5] 新增管理员种子 Controller，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/api/AdminSeedController.java`
- [ ] T055 [US5] 新增 34 省目录与 6 批规划，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ProvinceCatalog.java`、
  `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotBatchProcessor.java`
- [ ] T056 [US5] 用 Spring AI 调 DeepSeek 结构化输出，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/infrastructure/DeepSeekScenicSpotClient.java`
- [ ] T057 [US5] 实现字段校验与丢弃，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotValidator.java`
- [ ] T058 [US5] 实现高德 Web 服务地理编码与坐标待补，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/infrastructure/AmapGeocodingClient.java`
- [ ] T059 [US5] 实现资源/门票规格/库存 upsert 与人工修改保护，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotSeedService.java`
- [ ] T060 [US5] 实现报告汇总与查询，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotSeedReport.java`
- [ ] T061 [US5] 配置 Spring AI/高德 key/导入参数，
  文件 `services/inventory/src/main/resources/application.yml`、`.env.example`
- [ ] T062 [US5] 验证 16 城覆盖与云南省级/丽江城市检索，
  文件 `services/inventory/src/test/java/com/travelplatform/inventory/application/ResourceSearchServiceTest.java`

**Checkpoint**: 导入可异步运行；报告可查询；重跑安全。

---

## Phase 8: User Story 6 - 用户持久化与管理员角色 (P2)

**Goal**: 重启不丢用户；管理员/普通用户权限边界。

- [ ] T063 [P] [US6] 用户持久化集成测试，
  文件 `services/user-auth/src/test/java/com/travelplatform/userauth/AuthPersistenceIntegrationTest.java`
- [ ] T064 [P] [US6] 管理员引导与角色测试，
  文件 `services/user-auth/src/test/java/com/travelplatform/userauth/AdminRoleTest.java`
- [ ] T065 [US6] 注册/登录/当前用户回归，
  文件 `services/user-auth/src/test/java/com/travelplatform/userauth/AuthControllerTest.java`
- [ ] T066 [US6] 网关 401/404/管理员放行测试，
  文件 `gateway/src/test/java/com/travelplatform/gateway/JwtAuthFilterTest.java`

**Checkpoint**: 用户跨重启保留，管理员接口不可被普通用户访问。

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T067 [P] 增加导入与工具调用指标/链路，
  文件 `services/inventory/src/main/java/com/travelplatform/inventory/application/seed/ScenicSpotSeedService.java`、
  `services/ai-planning/src/main/java/com/travelplatform/aiplanning/application/AttractionSearchTool.java`
- [ ] T068 [P] 更新 v2 契约与 GLOSSARY 中的术语，
  文件 `GLOSSARY.md`、`specs/002-ai-travel-platform-v2/contracts/`
- [ ] T069 全量回归后端与前端，
  文件 `pom.xml`、`frontend/package.json`、`specs/002-ai-travel-platform-v2/quickstart.md`
- [ ] T070 fat jar 校验与全部新端点真实 HTTP 探活，
  文件 `specs/002-ai-travel-platform-v2/quickstart.md`
- [ ] T071 Prometheus/Grafana/Tempo 三件套验收，
  文件 `deploy/observability/`、`specs/002-ai-travel-platform-v2/quickstart.md`
- [ ] T072 确认 8 个既有页面行为、越权 404、Outbox、幂等、支付终态与
  `@Qualifier("applicationTaskExecutor")` 不回退，并记录验收结果，
  文件 `specs/002-ai-travel-platform-v2/quickstart.md`
- [ ] T073 最终文档同步与任务状态更新，
  文件 `specs/002-ai-travel-platform-v2/tasks.md`、`docs/domain-modeling-state.md`

---

## Dependencies & Execution Order

### Phase dependencies

1. Phase 1（V2-01）完成并获用户确认。
2. Phase 2 的 T005–T007 是硬门禁；T007 未通过不得进入 T008。
3. T008–T015 完成后再进入用户故事。
4. US1–US6 可并行；交易链路 US4 依赖 US3 的行程项结构完成。
5. Polish 依赖目标用户故事完成。

### User story dependencies

- US1：依赖 T012 的 ATTRACTION、T018/T019 的库存工具查询、T005 的 Spring AI 基线。
- US2：依赖 T014 的 pace 字段。
- US3：依赖 T013 的行程项字段、T036 的增删接口、T045 的地图配置。
- US4：依赖 T012 的 ATTRACTION 和 T047 的门票守卫。
- US5：依赖 T008–T012、T021 的 Spring AI 客户端模式和 T053–T061 的种子链。
- US6：依赖 T008–T011。

### Commit discipline

- 每个 T 任务单独 commit。
- V2-01 commit 只允许包含 v2 文档与 ADR；`.specify/feature.json` 是本机忽略文件，
  不进入 commit。
- 不允许把 `.specify/*` 行尾变化或 `PlanningService.java` 行尾变化混入 v2 commit。
- 当前这些无关改动已保存在 `git stash`：
  `pre-v2 local-only line-ending changes 2026-10-17`。

## Parallel Opportunities

- T006 各模块 pom 对齐可并行。
- T012/T013/T014 三个服务迁移可并行。
- US1 的 T016/T017 测试可并行；T039–T043 前端组件可并行。
- US5 的 T051/T052 测试可并行；T055–T060 可按模块拆分并行。
- US6 的 T063/T064 可并行。

## Implementation Strategy

### MVP first

先完成 V2-01 → V2-02 门禁 → US1/US2/US3 的最小规划体验，
再补 US5 数据管道和 US4 交易收敛。

### Incremental delivery

1. 文档与依赖基线。
2. 用户持久化/管理员 + ATTRACTION schema。
3. 规划工具调用、装箱、表单、结果页。
4. 只做门票交易。
5. 全国种子导入。
6. 全量回归、fat jar、HTTP 探活、可观测性。

## Notes

- 每个任务完成后先给验收证据，不直接标 `[X]`。
- 用户确认后才把任务改为 `[X]`。
- V2-02 的回归证据必须包含 Prometheus 与 Tempo，不能只看 health UP。
