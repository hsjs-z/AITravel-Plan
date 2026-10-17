# Research & Decisions: AI 旅行平台 v2

**Created**: 2026-10-17

本文件记录 v2 的关键技术决策、版本核实证据与备选方案。版本核实日期为
2026-10-17，来源为 Maven Central `repo1.maven.org`。

## R-001 Spring AI 与框架基线

**Decision**: v2 使用 Spring AI `1.0.9`，并将基线升级为 Spring Boot `3.5.15`、
Spring Cloud `2025.0.3`、Spring Cloud Alibaba `2025.0.0.0`。

**Maven Central evidence**:

| Artifact | Version | HTTP status | URL |
|---|---|---|---|
| `org.springframework.boot:spring-boot-dependencies` | `3.5.15` | 200 | `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.15/spring-boot-dependencies-3.5.15.pom` |
| `org.springframework.cloud:spring-cloud-dependencies` | `2025.0.3` | 200 | `https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.0.3/spring-cloud-dependencies-2025.0.3.pom` |
| `com.alibaba.cloud:spring-cloud-alibaba-dependencies` | `2025.0.0.0` | 200 | `https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-alibaba-dependencies/2025.0.0.0/spring-cloud-alibaba-dependencies-2025.0.0.0.pom` |
| `org.springframework.ai:spring-ai-bom` | `1.0.9` | 200 | `https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/1.0.9/spring-ai-bom-1.0.9.pom` |
| `org.springframework.ai:spring-ai-starter-model-deepseek` | `1.0.9` | 200 | `https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-starter-model-deepseek/1.0.9/spring-ai-starter-model-deepseek-1.0.9.pom` |
| `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery` | `2025.0.0.0` | 200 | `https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-starter-alibaba-nacos-discovery/2025.0.0.0/spring-cloud-starter-alibaba-nacos-discovery-2025.0.0.0.pom` |

**Compatibility notes**:

- Spring AI `1.0.9` 的 DeepSeek starter POM 声明 `spring-boot-starter:3.5.15`。
- Spring Cloud `2025.0.3` 的父 POM 为 `spring-cloud-dependencies-parent:4.3.4`。
- Spring Cloud Alibaba `2025.0.0.0` 的父 POM 为
  `spring-cloud-dependencies-parent:4.3.0`，属于 Spring Cloud 2025.0.x 线。
- Spring AI 1.0.x 最新 GA 为 `1.0.9`；Maven Central 上整体最新 GA 已到 2.x，
  但用户明确要求 1.0 GA，因此不采用 2.x。

**Rationale**: 只升级 Spring AI BOM 会留下 Boot 3.2.12 与 Spring AI 1.0.9 的
运行时不兼容风险。把 Boot/Cloud/SCA/Spring AI 作为同一基线升级，能保持 Nacos、
Gateway、OpenFeign、Micrometer/OTel 的兼容组合。

**Alternatives considered**:

- Spring AI `1.0.0` + Boot `3.4.5`：可匹配 Spring AI 1.0.0，但与当前 SCA 2023
  及 Spring Cloud 2023 组合不一致，需要更大范围的降级/混搭，风险更高。
- 只加 Spring AI `1.0.9`，保留 Boot `3.2.12`：不可接受，starter 以 Boot 3.5.15
  编译，API 与自动配置不保证兼容。
- Spring AI `2.0.1`：超出用户指定的 1.0 GA 范围，且会进一步抬高 Boot 基线。

**Gate**: V2-02 升级后，先全量回归 v1 主链路和 Prometheus/Tempo，通过后才允许
进入 V2-03。该门禁写入 `plan.md` 与 `tasks.md`。

## R-002 景区资源类型

**Decision**: `resource.type` 增加 `ATTRACTION` 作为第三种一级类型；
`TRADABLE`、`CONTENT` 保持原语义。

**Rationale**: v2 要求景区本身有坐标、图片、级别、时长和真实门票价，并可直接
被规划工具返回、被行程项绑定、被订单锁定。继续把景区塞进 `CONTENT` 会让
“内容不可下单”的 v1 规则和 v2 的“匹配景区可预订”互相冲突。

**Consequences**:

- 景区记录写 `type=ATTRACTION`，同时在 `attributes.category` 写 `ATTRACTION`
  供旧检索兼容。
- 付费景区拥有默认 `TICKET_TYPE` 规格；免费景区不创建交易规格。
- 旧 `TRADABLE` 景点/门票记录不做删除；云南旧数据只回填 province/city。

**Alternatives considered**:

- 继续 `CONTENT` + 独立门票 `TRADABLE`：更贴近 v1 词汇，但工具要返回两个独立
  资源 ID，行程项绑定和前端卡片会增加一层间接映射。
- 把所有景区改成 `TRADABLE`：会破坏内容资源页语义。

## R-003 免费与未匹配状态

**Decision**:

- `ticket_price=0` 表示免费。
- 免费景区可加入行程，但 `bookable=false`、`priceLabel=免费/无需购票`，
  不创建支付订单。
- 未匹配景区可加入行程，`resourceId/specId` 为空，`priceLabel=以官方为准`，
  文案为“暂不可预订”。
- 两种状态分别由 `matched`、`priceLabel`、`unavailableReason` 区分。

**Rationale**: 当前支付接口要求金额为正数，零元订单需要新增“免支付”状态路径，
会扩大交易链路改动面。用户已确认免费景点只展示并可加入行程，不进入支付链路。

## R-004 时段装箱规则

**Decision**: 代码按 AI 顺序装箱，具体规则如下：

1. 先按节奏截断当天 AI 列表到每日上限。
2. 每个景点是一个不可分割的时长块。
3. 维护一个“当前时段指针”，从上午开始。
4. 对每个景点，从当前指针开始找第一个同时满足“剩余时长足够”和“已放景点 < 3”的时段。
5. 若当前时段放不下，允许按顺序溢出到后续时段；溢出后不得回填更早时段。
6. 若所有后续时段都放不下，从该景点起停止当天装箱，剩余景点写入 `warnings`。
7. 首个 4h 景点在上午容量 4h 且上午为空时，必须进入上午。
8. 慵懒档晚上容量固定为 0；页面仍渲染空晚上区块，这是产品取舍。

**Rationale**: 该规则避免“4h 景点被无理由甩到下午”的反直觉行为，同时保证
单景点不被拆分、AI 顺序不被任意重排。

**Examples**:

- 散步档：上午空，首个 4h 景点 → 上午。
- 散步档：上午已有 1h 景点，再来 4h 景点 → 上午只剩 3h，进入下午。
- 慵懒档：上午 3h、下午 1h、晚上 0h；4h 景点无法整体放入任何单一时段，
  按规则不放置并写警告。

## R-005 数据导入与幂等

**Decision**: 采用后台运行 + `202 + runId` + 报告查询；`(name, city)` 唯一；
已存在记录 insert-or-skip，绝不覆盖人工修改。

**Rationale**: 6 次 AI 调用加约 200 次地理编码不适合同步 HTTP；批次报告需要
可追踪状态。insert-or-skip 是最简单的“重跑不插重且不覆盖人工修改”实现。

**Consequences**:

- `scenic_seed_run` 保存运行状态和报告 JSON。
- 并发运行返回 409。
- 旧云南资源先回填 province/city，再参与查重。
- 付费景区默认生成门票规格与未来 365 天库存。

## R-006 管理员鉴权

**Decision**: `app_user.role` + JWT `role` claim + 网关 `X-User-Role` +
服务端二次检查；管理员由 `ADMIN_EMAIL/ADMIN_PASSWORD` 环境变量引导创建。

**Rationale**: 当前没有角色模型，管理员接口不能只靠“已登录”判断。

**Consequences**:

- 普通用户访问 `/admin/**` 返回 404，未登录返回 401。
- 注册默认 `USER`，登录/注册响应兼容增加 `role`。
- 旧 token 无 role 时按 `USER` 处理。

## R-007 预算字段的 v2 口径

**Decision**: 前端删除独立预算字段；API 保留可选预算以兼容旧客户端；
`planning_task.budget`、`itinerary.budget` 允许为空；新 AI 结果不输出估算预算。

**Rationale**: 直接删除列会破坏 v1 数据和旧调用；保留可空列同时满足 v2 表单。

## R-008 前端底座

**Decision**: 继续使用 Vue 3 + Vite + TypeScript + Element Plus + 现有 axios 请求层；
高德地图通过动态脚本加载，不更换路由、请求层或组件库。

**Rationale**: 用户只要求视觉向参考图靠齐，没有要求更换底座；保持现有 8 个页面
的行为和路由最稳。

**Consequences**:

- Vite `envDir` 指向仓库根，读取根 `.env` 的 `VITE_AMAP_JS_KEY`。
- 服务端使用 `AMAP_WEB_SERVICE_KEY`；两个 key 都不硬编码。
