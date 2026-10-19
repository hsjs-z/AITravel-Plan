# Travel Platform（AI 旅行规划与预订平台）

一个 AI-First 的旅行规划与预订平台，覆盖「自然语言行程规划 → 一键预订 → 支付 → 通知」完整闭环。用户输入目的地、天数、人数、预算等需求，由 AI 生成可交易行程，支持酒店/门票/车票的联合预订、库存锁定、支付与自动退款。

## 技术栈

- **后端**：Java 17、Spring Boot 3.5、Spring Cloud Alibaba（Nacos）、Spring AI、Spring Cloud Gateway、OpenFeign
- **中间件**：MySQL 8、Redis 7、Kafka 3.7、Nacos 2.3
- **可观测性**：Prometheus、Grafana、Tempo（分布式追踪）
- **前端**：Vue 3、TypeScript、Vite、Element Plus、Axios
- **构建**：Maven（多模块 monorepo）

## 项目结构

```
travel-platform/
├── common/                  # 公共模块（Outbox、通用工具等）
├── gateway/                 # API 网关（JWT 鉴权、路由）
├── services/
│   ├── user-auth/           # 用户认证（注册、登录、JWT）
│   ├── ai-planning/         # AI 行程规划（DeepSeek + 工具调用）
│   ├── itinerary/           # 行程管理（CRUD、状态机）
│   ├── inventory/           # 资源库存（锁定、释放、防超卖）
│   ├── booking-order/       # 订单管理（Saga 编排、状态机）
│   ├── payment/             # 支付与退款
│   └── notification/        # 通知（站内信、邮件）
├── frontend/                # 前端（Vue 3）
├── deploy/                  # 中间件 Docker Compose 配置
│   ├── docker-compose.yml
│   ├── init/                # 数据库初始化脚本
│   ├── seed/                # 初始数据
│   └── observability/       # Prometheus/Grafana/Tempo 配置
└── specs/                   # 需求规格与设计文档
```

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Docker | 20.10+（含 Docker Compose v2） |

## 快速启动

### 1. 配置环境变量

复制环境变量模板并按需修改：

```bash
cp .env.example .env
```

关键变量说明：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | `root` |
| `NACOS_SERVER` | Nacos 地址 | `127.0.0.1:8848` |
| `JWT_SECRET` | JWT 签名密钥 | 需自行设置 |
| `JWT_TTL_MINUTES` | JWT 过期时间（分钟） | `1440` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key（不填则使用 Mock 规划） | 空 |
| `AMAP_WEB_SERVICE_KEY` | 高德地图 Web 服务 Key | 空 |
| `VITE_AMAP_JS_KEY` | 高德地图 JS API Key（前端） | 空 |

> 注意：`.env` 已被 `.gitignore` 排除，不会提交到仓库。

### 2. 启动中间件

所有中间件通过 Docker Compose 一键启动：

```bash
cd deploy
docker compose up -d
```

启动的服务及端口：

| 中间件 | 端口 | 说明 |
|--------|------|------|
| MySQL | 3307 | 数据库，自动创建 7 个服务库并执行初始化脚本 |
| Redis | 6379 | 缓存与分布式锁 |
| Kafka | 9092 | 消息队列 |
| Nacos | 8848 | 注册与配置中心 |
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 可视化看板（匿名访问） |
| Tempo | 4318 / 3200 | 分布式追踪 |

等待容器健康检查通过（约 30 秒）。

### 3. 启动后端服务

回到项目根目录，构建并启动各服务：

```bash
# 构建（跳过测试可加快速度）
mvn clean install -DskipTests
```

然后按以下顺序启动各服务（端口见下表），推荐使用 IDE 启动或命令行 `java -jar`：

| 服务 | 端口 | 启动类 |
|------|------|--------|
| user-auth | 8081 | `UserAuthApplication` |
| inventory | 8082 | `InventoryApplication` |
| itinerary | 8083 | `ItineraryApplication` |
| ai-planning | 8084 | `AiPlanningApplication` |
| payment | 8085 | `PaymentApplication` |
| booking-order | 8086 | `BookingOrderApplication` |
| notification | 8087 | `NotificationApplication` |
| gateway | 8080 | `GatewayApplication` |

> 建议先启动 user-auth 和 inventory，再启动其余服务，最后启动 gateway。各服务通过 Nacos 自动注册，无需手动配置服务发现地址。

各服务启动时会通过 Flyway 自动执行数据库迁移脚本建表。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端启动后访问 http://localhost:5173 ，API 请求通过 Vite 代理转发到网关 `http://localhost:8080`。

## 访问地址

| 应用 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| API 网关 | http://localhost:8080 |
| Nacos 控制台 | http://localhost:8848/nacos |
| Grafana 看板 | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

## 可观测性

后端服务已集成 Prometheus 指标与 OTLP 分布式追踪：

- 指标端点：各服务 `/actuator/prometheus`
- 追踪数据通过 OTLP 上报到 Tempo（`http://127.0.0.1:4318`）
- Grafana 已预配置 Prometheus 数据源与旅行平台看板

## 常见问题

**Q: 启动报错连接不上 Nacos/MySQL？**
A: 确认 Docker 容器已健康运行：`docker compose ps`，等待 health 状态为 healthy。

**Q: AI 规划返回的是 Mock 数据？**
A: 未配置 `DEEPSEEK_API_KEY` 时默认使用 Mock 规划客户端。在 `.env` 中填入真实 Key 后重启 ai-planning 服务即可。

**Q: 端口冲突怎么办？**
A: 修改各服务 `application.yml` 中的 `server.port`，或修改 `deploy/docker-compose.yml` 中的端口映射。

**Q: 如何重置数据库？**
A: 删除 MySQL 容器的数据卷后重启：`docker compose down -v && docker compose up -d`。
