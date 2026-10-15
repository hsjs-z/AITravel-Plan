# Implementation Plan: AI 旅行规划平台 v1

**Branch**: `001-ai-travel-platform-v1` | **Date**: 2026-10-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-ai-travel-platform-v1/spec.md`

## Summary

用户以自然语言获得多日行程（异步任务 + DeepSeek），把行程中的酒店/门票/车票
一键联合预订（Saga 编排 + 失败补偿），经 Mock 支付完成闭环，并异步收到通知。
技术方案为单仓库 Maven 聚合的微服务：Spring Cloud Gateway 统一入口，Nacos 注册与配置，
服务间用 OpenFeign（同步）与 Kafka（Outbox 异步）协作，Redis 原子扣减库存，
OpenTelemetry 全链路追踪。领域模型与关键决策见 `GLOSSARY.md` 与 `docs/adr/0001..0003`。

## Technical Context

**Language/Version**: JDK 17

**Primary Dependencies**: Spring Boot 3、Spring Cloud Alibaba（Nacos、Spring Cloud Gateway、
Sentinel、Seata、OpenFeign）、Spring AI + DeepSeek、Kafka、Redis、MySQL

**Storage**: MySQL（每个服务独立 schema）；Redis（库存原子操作 / 分布式锁 / 缓存）

**Testing**: JUnit 5 + Mockito + Spring Boot Test；核心逻辑（订单状态机、Saga、幂等、
库存扣减）单测必覆盖；Testcontainers 用于集成测试

**Target Platform**: Linux 服务端（容器化）；浏览器 Web 前端

**Project Type**: web-service（微服务后端 + Web 前端）

**Performance Goals**: 资源检索 95% < 1s；规划任务 95% < 60s；主线闭环
（查看行程 → 一键预订 → 完成付款）< 3 分钟（不含 AI 生成等待）

**Constraints**: 库存原子扣减不超卖；支付/退款幂等；traceId 跨服务透传（含 Kafka 与异步任务）；
关键接口产出 QPS / P99；敏感配置仅环境变量注入

**Scale/Scope**: v1 支撑 1000 并发浏览/检索；单一目的地；MVP 模块集

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 落实方式 |
|------|------|----------|
| I 微服务工程结构 | ✅ | 根 pom + common + 各业务模块；业务模块仅依赖 common；OpenFeign / Kafka 通信 |
| II 技术栈锁定 | ✅ | JDK17 + Boot3 + SCA + Spring AI + DeepSeek + Kafka + Redis + MySQL |
| III 核心链路优先 | ✅ | ai-planning → itinerary → booking-order → payment 为主线；商家结算/后台延后 |
| IV 一致性/可靠事件 | ✅ | 联合预订 Saga 编排（booking-order 为编排器）+ 失败补偿；事件 Outbox；Seata 不用于跨服务强一致 |
| V 支付抽象/幂等 | ✅ | PaymentGateway 接口 + Mock 实现 + 预留真实实现位；支付/退款幂等键；回调对账 |
| VI AI 异步化 | ✅ | 规划任务状态机 PENDING/RUNNING/SUCCEEDED/FAILED + 查询与重试 |
| VII 并发安全 | ✅ | 库存占用/释放走 Redis Lua 原子操作；禁止查-判-改 |
| VIII 可观测性 | ✅ | OpenTelemetry 全服务接入 + traceId 透传 + QPS/P99 指标 |
| IX 代码标准 | ✅ | Controller/Service/Repository 分层；OpenAPI 文档；密钥仅环境变量 |
| X 质量底线 | ✅ | 订单状态机/Saga/幂等/库存扣减单测必过，未测不得完成 |

> 复检（M4 T072 收尾，2026-10-15）：I–X 逐项复核满足，例外仅一处——原则 II 的
> 「Spring AI」按 research §11 以 RestTemplate 直连 DeepSeek 临时替代（技术债已记录，
> 非静默偏离）。VIII 的 OTel/QPS/P99 由 T068/T069 落地（Tempo + Prometheus/Grafana）；
> IX 的接口文档由 `contracts/*.md` 等价说明 + 端点索引承担（未引入 springdoc）；
> GLOSSARY 与 ADR 无漂移，M3/M4 新增决策（前端 CORS、可观测性选型）已在 research §17/§21 记录。

## Project Structure

### Documentation (this feature)

```text
specs/001-ai-travel-platform-v1/
├── plan.md              # 本文件
├── research.md          # Phase 0 技术决策
├── data-model.md        # Phase 1 数据模型与状态机
├── quickstart.md        # Phase 1 端到端验证指南
├── contracts/           # Phase 1 服务接口与事件契约
└── tasks.md             # Phase 2（$speckit-tasks，本命令不生成）
```

### Source Code (repository root)

```text
travel-platform/
├── pom.xml                 # 聚合工程 + dependencyManagement
├── common/                 # 公共模块：DTO、异常、幂等/Outbox/Saga 基础抽象、可观测封装
├── gateway/                # Spring Cloud Gateway 统一入口（路由 + 鉴权透传 + Sentinel 入口）
├── services/
│   ├── user-auth/          # 账号：注册/登录（FR-041，邮箱注册 + 密码登录）
│   ├── ai-planning/        # AI 规划：任务状态机 + DeepSeek 调用 + 推荐工具（资源+日期+规格）
│   ├── itinerary/          # 行程：行程/行程项 + 状态机（草稿/行程确认/只读/已完成）
│   ├── booking-order/      # 预订/订单：一键预订 + Saga 编排 + 失败补偿
│   ├── payment/            # 支付：PaymentGateway(Mock) + 幂等 + 回调对账
│   ├── inventory/          # 资源目录（可交易+内容）+ 规格 + 库存原子锁定
│   └── notification/       # 通知：站内信/邮件 + 未读已读
├── frontend/               # Web 前端（Vue 3 + Vite + TypeScript）
├── deploy/                 # 本地可复现的容器编排（MySQL/Redis/Kafka/Nacos）
├── GLOSSARY.md
└── docs/adr/
```

**Structure Decision**: 单仓库 Maven 聚合。每个业务服务一个模块，只依赖 `common`；
主线四服务（ai-planning → itinerary → booking-order → payment）为必交付，
user-auth / inventory / notification 支撑闭环，商家结算与后台管理延后。
前端独立于后端模块，不与后端 Maven 互相耦合。

## Complexity Tracking

> 无违规项，无需论证复杂度。
