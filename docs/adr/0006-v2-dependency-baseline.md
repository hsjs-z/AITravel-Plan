# v2 依赖基线：Boot 3.5.15 + Cloud 2025.0.3 + SCA 2025.0.0.0 + Spring AI 1.0.9

v1 因 `spring-ai-bom:0.8.1` 不可解析而临时使用 `RestTemplate` 直连 DeepSeek。
v2 要还清该技术债，必须选择可解析、可运行、与项目锁定栈一致的 GA 组合。

**Decision**:

v2 依赖基线锁定为：

| Component | Version |
|---|---|
| Spring Boot | `3.5.15` |
| Spring Cloud | `2025.0.3` |
| Spring Cloud Alibaba | `2025.0.0.0` |
| Spring AI | `1.0.9` |
| Spring AI DeepSeek starter | `1.0.9` |

**Maven Central verification (2026-10-17)**:

以下坐标均返回 HTTP 200：

- `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.15/spring-boot-dependencies-3.5.15.pom`
- `https://repo1.maven.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.0.3/spring-cloud-dependencies-2025.0.3.pom`
- `https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-alibaba-dependencies/2025.0.0.0/spring-cloud-alibaba-dependencies-2025.0.0.0.pom`
- `https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/1.0.9/spring-ai-bom-1.0.9.pom`
- `https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-starter-model-deepseek/1.0.9/spring-ai-starter-model-deepseek-1.0.9.pom`
- `https://repo1.maven.org/maven2/com/alibaba/cloud/spring-cloud-starter-alibaba-nacos-discovery/2025.0.0.0/spring-cloud-starter-alibaba-nacos-discovery-2025.0.0.0.pom`

Compatibility evidence:

- Spring AI 1.0.9 DeepSeek starter declares `spring-boot-starter:3.5.15`.
- Spring Cloud 2025.0.3 uses `spring-cloud-dependencies-parent:4.3.4`.
- Spring Cloud Alibaba 2025.0.0.0 uses `spring-cloud-dependencies-parent:4.3.0`
  and is on the 2025.0.x line.

**Consequences**:

- 依赖升级跨所有模块，必须单独一个 commit。
- 升级后必须先全量回归 v1 主链路和 Prometheus/Tempo，通过后才允许进入 V2-03。
- `RestTemplate` 直连 DeepSeek 的代码在 V2-07 替换；V2-02 只做基线升级。
- 如果升级导致 Nacos/OpenFeign/OTel 兼容问题，优先修复版本对齐，不回退到
  Boot 3.2.12 + Spring AI 1.0.9 的不受支持组合。

**Alternatives considered**:

- Spring AI `1.0.0` + Boot `3.4.5`：与当前 SCA/Cloud 线组合更复杂，不选。
- 只加 Spring AI BOM：运行时不兼容，不选。
- Spring AI `2.0.1`：超出用户指定的 1.0 GA 范围，不选。

**Status**: accepted

**Date**: 2026-10-17
