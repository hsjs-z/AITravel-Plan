# V2-02 Acceptance Evidence

**Date**: 2026-10-17

**Status**: PASS, awaiting user acceptance. V2-03 has not started.

## 1. Baseline versions

Verified against Maven Central on 2026-10-17. All candidate POMs returned HTTP 200.

| Component | Version |
|---|---|
| Spring Boot | `3.5.15` |
| Spring Cloud | `2025.0.3` |
| Spring Cloud Alibaba | `2025.0.0.0` |
| Spring AI BOM | `1.0.9` |
| Spring AI DeepSeek starter | `1.0.9` |
| SCA Nacos discovery starter | `2025.0.0.0` |

## 2. Build

Command:

```text
mvn clean install
```

Result:

```text
Reactor Summary:
travel-platform  SUCCESS
common           SUCCESS
gateway          SUCCESS
user-auth        SUCCESS
inventory        SUCCESS
itinerary        SUCCESS
ai-planning      SUCCESS
payment          SUCCESS
booking-order    SUCCESS
notification     SUCCESS

BUILD SUCCESS
Total time: 05:07 min
```

### Known pre-existing warning

`InventoryConcurrencyIntegrationTest` logs `Data too long for column 'aggregate_id'`.
The same warning exists in the v1 build log `logs/t070-build.log`; it is not introduced
by this upgrade. Tests still pass. This remains a v1 test-fixture/outbox-length issue,
not a V2-02 dependency regression.

## 3. Fat jar manifests

All eight executable jars contain:

```text
Main-Class: org.springframework.boot.loader.launch.JarLauncher
```

| Jar | Start-Class |
|---|---|
| gateway | `com.travelplatform.gateway.GatewayApplication` |
| user-auth | `com.travelplatform.userauth.UserAuthApplication` |
| inventory | `com.travelplatform.inventory.InventoryApplication` |
| itinerary | `com.travelplatform.itinerary.ItineraryApplication` |
| ai-planning | `com.travelplatform.aiplanning.AiPlanningApplication` |
| payment | `com.travelplatform.payment.PaymentApplication` |
| booking-order | `com.travelplatform.bookingorder.BookingOrderApplication` |
| notification | `com.travelplatform.notification.NotificationApplication` |

## 4. Startup and Nacos

All eight services were started one at a time. Health checks returned `{"status":"UP"}`.
Nacos instance list showed one healthy instance for each:

| Service | Port | Nacos |
|---|---:|---|
| gateway | 8080 | healthy |
| user-auth | 8081 | healthy |
| inventory | 8082 | healthy |
| itinerary | 8083 | healthy |
| ai-planning | 8084 | healthy |
| payment | 8085 | healthy |
| booking-order | 8086 | healthy |
| notification | 8087 | healthy |

Gateway was upgraded from deprecated `spring-cloud-starter-gateway` to
`spring-cloud-starter-gateway-server-webflux`, and routes moved to
`spring.cloud.gateway.server.webflux.routes`. The deprecation warning is gone.

## 5. v1 regression

| Scenario | Result |
|---|---|
| Register/login through gateway | PASS |
| GET `/users/me` with JWT | PASS |
| Unauthenticated `/users/me` | 401 |
| Mock planning task | `SUCCEEDED`, itinerary created |
| Draft itinerary | `DRAFT` |
| Confirm itinerary | `CONFIRMED` |
| One-click order | `BOOKED` |
| Pay order | `PAID` |
| Auto-confirm scheduler | `CONFIRMED` |
| Paid order cancel | `CANCELLED` |
| Refund query | `SUCCEEDED` |
| Notification list/unread count | PASS |
| Cross-user itinerary access | HTTP 404 |

## 6. Observability

### Prometheus

- Targets API: active targets `8`, all `health=up`.
- Query `sum(rate(http_server_requests_seconds_count[1m])) by (service)`:
  returns all eight services.
- Query `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, service))`:
  returns all eight services.
- Gateway exposes `spring_cloud_gateway_requests_seconds`,
  `spring_cloud_gateway_routes_count`, and standard HTTP/JVM/process metrics.
- Existing Grafana dashboard queries still resolve against the new runtime.

### Tempo

New trace IDs were resolved through `http://127.0.0.1:3200/api/traces/{traceId}`:

- Planning trace `882bb5f9d24212ebe8460c6150fe5291`:
  `gateway → ai-planning → inventory → itinerary`.
- Order trace `e72e112546598cc891dc56184afbe6e1`:
  `gateway → booking-order → itinerary → inventory → payment → notification`.
- Payment trace `a21470dff9482e49328c5660a7cc714e`:
  `gateway → booking-order → payment → notification`.
- Post-restart resource trace `e224ab5bee5608502f4441beda6285bf`:
  `gateway → inventory`.

## 7. Gate decision

V2-02 passes the required `mvn clean install`, v1 main-chain regression,
Prometheus 8/8 targets, and Tempo cross-service trace checks. V2-03 may start only
after user acceptance of this evidence.
