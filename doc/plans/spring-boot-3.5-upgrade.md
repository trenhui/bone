# Spring Boot 3.5 升级记录

> 状态（2026-10-13）：父 POM **已经**落到 Java 21、Spring Boot **3.5.16**、Spring Cloud **2025.0.3**、Spring Cloud Alibaba **2025.0.0.0**、springdoc **2.9.1**、mysql-connector-j **8.4.0**。真源是 `bone-parent/pom.xml`。下文表格里的 `3.2.5 → 3.5.5` 是起草时的目标，不是当前版本。
> 仍开放：第 4 节代码级迁移是否都已消化，以及架构师对「升级完成」的签字。本文件不再表示版本号未改。
> 关联：MySQL 8.4 **服务端**迁移（L4）见 `scripts/mysql-84-upgrade.sh`。

## 1. 目标与边界
- 起草目标是 Spring Boot `3.2.5` → `3.5.x`。父属性已超过该草案（见文首状态），并已升到 Java 21。
- Boot / Cloud / SCA **三者必须同批、同兼容矩阵**升级，否则自动装配错位。
- 本专项**不含**代码迁移实施——仅给属性改动 + 风险清单 + 验证计划。代码迁移（第 4 节）由负责人在 apply 后实施。
- MySQL 8.4 **服务端**数据目录迁移属 **L4，本草案不执行**，仅引用脚本。

## 2. 父属性（已落到 `bone-parent/pom.xml`）

| 属性 | 起草时 | 草案目标 | 当前 pom |
|---|---|---|---|
| `java.version` | 17 | （当时建议与 Boot 解耦） | **21** |
| `spring-boot.version` | 3.2.5 | 3.5.5 | **3.5.16** |
| `spring-cloud.version` | 2023.0.3 | 2024.0.4 | **2025.0.3** |
| `spring-cloud-alibaba.version` | 2023.0.1.2 | 2023.0.3.2 | **2025.0.0.0** |
| `springdoc.version` | 2.3.0 | 2.8.0 | **2.9.1** |
| `mysql-connector.version` | 8.0.33 | 8.4.0 | **8.4.0** |

## 3. 同批耦合升级
- `springdoc.version` 与 `mysql-connector.version` 已按上表落地（草案目标分别是 2.8.0 / 8.4.0，当前更高或已对齐）。
- `redisson` / `rocketmq` 起草时已先行升，仍需与 Boot 3.5 starter 匹配。
- `skywalking` 与部署侧 agent 对齐不在本专项强绑。

## 4. 代码级迁移风险（3.3 / 3.4 / 3.5 破坏性变更）
- `META-INF/spring.factories` 早已移除（3.0 起），确认无遗漏的 `AutoConfiguration.imports` 缺失
- HTTP 客户端默认行为变化（`RestClient` / `RestTemplate` Builder）
- `spring-boot-starter-actuator` 端点与安全默认值变化
- 若 `spring-security` 随 Boot 升级：`bone-security` 的 `AbstractJwtAuthenticationFilter` 等过滤器链 API 可能需改
- Jakarta 命名空间已在 3.0 完成，3.5 无新增，但需复检 import
- 配置属性重命名/弃用（运行 `spring-boot-properties-migrator` 辅助定位）

## 5. 验证计划
1. 本地：`mvn -pl <模块> -am test` + `./scripts/check.sh`（需 MySQL / Redis 可用）
2. 测试环境：跑 `bone-init.sql` + 接口回归 + 网关/IAM 全链路
3. 灰度：先非核心（`studio-generator` / `metadata-sdk`），再核心（`iam` / `system` / `gateway`）

## 6. 越权 / 高风险（不在本专项落地）
- **MySQL 8.0.33 → 8.4 服务端 + 数据目录 = L4**：禁止 AI 执行。脚本草案 `scripts/mysql-84-upgrade.sh`（DBA 受控窗口）。
- **Redis 镜像 `7-alpine` → `7.4-alpine` = L3（Docker）**：改 `docker-compose.yml` 镜像标签，需架构师签字。
- **JDK 17 → 21**：`java.version` 已是 21。本节保留为当时的风险说明，不再表示「尚未升 JDK」。

## 7. 回滚
- 保留当前分支；Boot 升级建议拆为「父属性三连改」+「代码迁移」两个 commit，便于二分定位。
- 服务端回滚依赖 MySQL 备份（见 L4 脚本）。
