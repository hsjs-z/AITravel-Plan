# Research & Decisions: AI 旅行规划平台 v1

技术栈由宪章锁定，无未决技术选型阻塞设计；本文件记录关键技术决策、理由与备选，
供实现与评审引用。格式：Decision / Rationale / Alternatives。

## 1. 服务边界与主线编排

- **Decision**: 9 个模块——gateway、common、user-auth、ai-planning、itinerary、
  booking-order、payment、inventory、notification。主线
  `ai-planning → itinerary → booking-order → payment`，booking-order 是
  「一键预订」Saga 的编排器（orchestrator）。
- **Rationale**: 宪章 I 要求业务模块独立、只依赖 common；把联合预订的编排逻辑
  集中在一个聚合根（订单）所在的 booking-order，状态集中、可追踪、补偿可控。
- **Alternatives**: 事件编排（choreography）——各服务各自监听事件，状态分散、
  排障困难；单一大服务——违反宪章 I。

## 2. 跨服务一致性与 Saga

- **Decision**: 自研 Saga 编排 + 失败补偿；每个正向动作（锁定某资源库存）对应
  一个幂等的释放补偿动作，记录在 booking-order 的编排日志（BookingSettlementLog）。
  Seata 组件引入但**不**用于跨服务强一致。
- **Rationale**: 宪章 IV 明确「Saga 编排 + 失败补偿」为默认，Seata 仅限明确声明场景；
  自研编排器避免 Seata AT 全局锁带来的锁粒度与云数据库兼容性问题。
- **Alternatives**: Seata AT 全局事务——强一致但长事务锁粒度大、与分库/云 MySQL
  兼容性风险高；Seata Saga——可用，但引入额外状态机学习成本，v1 自研编排更可控。

## 3. 事件可靠投递（Outbox）

- **Decision**: 事务性 Outbox：业务写入与事件记录同事务落库，由轮询发布器
  （Polling Publisher）投递到 Kafka；消费端按事件 `id` 幂等去重。
- **Rationale**: 满足宪章 IV「不丢不重」；轮询发布器无额外组件、运维简单。
- **Alternatives**: Debezium/CDC——零侵入但需额外运维组件，v1 引入成本高于收益；
  先发消息后落库（无 Outbox）——会丢或重复，违反宪章。

## 4. 库存原子扣减与锁定

- **Decision**: 库存占用与释放走 Redis Lua 原子脚本（check-and-decrement 与
  check-and-increment 都在一次原子执行内完成）；MySQL 库存行用乐观锁/唯一约束兜底；
  锁定按「资源 + 日期 + 规格」三维，带 `purpose`（PLANNING 短 TTL 软占位 /
  BOOKING 硬占用）与幂等释放。
- **Rationale**: 宪章 VII 禁止查-判-改；Redis Lua 保证原子性且吞吐高；
  三维锁定落实 ADR-0002 的映射约束。
- **Alternatives**: 数据库悲观锁——正确但吞吐低；先查再改——违反宪章，拒绝。

## 5. AI 规划与 DeepSeek

- **Decision**: Spring AI 调用 DeepSeek，异步执行 + 任务状态机；模型的「预订工具」
  通过 inventory 服务的 `query` / `locks` 接口按「资源 + 日期 + 规格」查询并以
  `purpose=PLANNING` 短 TTL 锁定候选库存，确保推荐能落到可预订单元。
- **Rationale**: 宪章 VI 禁止同步阻塞；规划推荐必须满足 ADR-0002 的三维约束。
  规划期软占位用短 TTL 限制囤货风险，真正的扣减发生在预订阶段。
- **Alternatives**: 同步调用 DeepSeek——违反宪章 VI；推荐只到「资源」粒度——
  违反 ADR-0002。

## 6. 支付抽象与 Mock

- **Decision**: `PaymentGateway` 接口 + `MockPaymentGateway` 实现，预留
  `WechatPaymentGateway` 实现位；支付/退款以幂等键幂等，回调做金额+订单一致性对账。
- **Rationale**: 宪章 V 强制；接口抽象使真实通道接入不改变调用方。
- **Alternatives**: 硬编码 Mock——违反宪章 V；直接接微信——v1 明确不做。

## 7. 认证与数据隔离

- **Decision**: user-auth 服务负责注册/登录，签发 JWT；gateway 校验并透传 userId；
  各服务以 userId 做数据隔离（所有查询强制带 owner 过滤）。
- **Rationale**: 规格 FR-013 / FR-041 要求账号与隔离；集中认证避免各服务重复实现。
- **Alternatives**: 各服务各自认证——重复且易漏隔离。

## 8. 前端技术栈

- **Decision**: Vue 3 + Vite + TypeScript + Element Plus。
- **Rationale**: 宪章未锁前端；中文生态、组件库成熟、上手快，适合 v1 快速交付。
- **Alternatives**: React + Next.js——同样可行，团队偏好优先。

## 9. 可观测性

- **Decision**: 所有服务接 OpenTelemetry（自动埋点 + Micrometer/Prometheus 指标），
  traceId 通过 HTTP 头与 Kafka 消息头透传，异步任务与消息消费恢复上下文。
- **Rationale**: 宪章 VIII 强制；traceId 贯穿 AI → 订单 → 支付。
- **Alternatives**: 仅日志无链路——无法满足宪章。

## 10. 配置与密钥

- **Decision**: Nacos 管理非敏感配置与 Sentinel 规则；数据库口令、DeepSeek API Key、
  支付凭据等敏感项仅经环境变量注入，禁止入库/入仓。
- **Rationale**: 宪章 IX。
- **Alternatives**: 全部放 Nacos 明文——敏感信息暴露，拒绝。

## 11. 技术债与临时偏离：Spring AI 暂以直连端点替代

- **现状**: `spring-ai-bom:0.8.1` 已过时，且本机 Maven 镜像（alimaven）无法解析该构件；
  T018 因此临时改用 Spring `RestTemplate` 直连 DeepSeek 的 OpenAI 兼容端点
  `https://api.deepseek.com/chat/completions`，未引入 Spring AI 依赖。
- **偏离说明**: 这与本文件第 5 节「Spring AI 调用 DeepSeek」的原始决定存在临时偏离，
  特此记录，不做静默偏离。
- **待办**: 主线闭环后评估 Spring AI 1.0.x GA（需匹配 Spring Boot 版本且镜像可解析）；
  若可解析则切换回 Spring AI 客户端，切换须保持 `PlannerClient` 接口不变，
  仅替换 `DeepSeekPlannerClient` 实现。

## 12. 技术债：回调对账缺少 FAILED 终态守卫

- **现状**: `PaymentService.reconcileCallback` 只校验金额一致与回调状态，
  不校验支付当前状态；一笔已 `FAILED` 的支付可能被「金额一致 + SUCCESS」的
  回调翻成 `SUCCEEDED`。
- **要求**: 接入真实支付通道前必须修复——为 `FAILED`（以及未来的终态）加
  状态机守卫，终态不可逆；重复/乱序回调只能对 `PENDING` 生效。

## 13. Schema 演进方案：Flyway 替代 ddl-auto=update

- **结论**: 引入 Flyway。每个服务以 `db/migration/V1__init.sql` 为基线迁移，
  并将 `spring.jpa.hibernate.ddl-auto` 置为 `none`，Schema 完全由 Flyway 管理。
- **枚举演进规则**: 以后新增/删除 Java 枚举值，必须同步提交一条 Flyway 迁移
  `ALTER TABLE ... MODIFY COLUMN <col> enum(...)`，禁止只改 Java 枚举。
- **理由**: `ddl-auto=update` 不会修改已存在的 ENUM 列，导致 Java 枚举与库不一致；
  `order_item.status` 缺 `RELEASED` 即由此产生，并在取消时造成
  「退款已提交、库存已释放、订单事务回滚」的三方不一致。Flyway 版本化迁移
  使 Schema 变更可审计、可复现，杜绝此类漂移。
