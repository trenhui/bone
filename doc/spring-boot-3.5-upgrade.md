# Spring Boot 3.5 升级专项草案（L3 · 待架构师签字 · 未执行）

> 状态：父属性**已 apply**（见第 2 节），但仅改版本号、未完成 `bone-security` 等代码迁移、未经 CI 验证，属 L3 待架构师签字。工作树当前不可离线构建（BOM 无缓存）。
> 关联：清理类改动见仓库根 `PR_DESCRIPTION.md`；MySQL 8.4 服务端迁移（L4）见 `scripts/mysql-84-upgrade.sh`。

## 1. 目标与边界
- 将 Spring Boot `3.2.5` → `3.5.x`（仍在 Java 17 兼容线，**不要求先升 JDK**）。
- Boot / Cloud / SCA **三者必须同批、同兼容矩阵**升级，否则自动装配错位。
- 本专项**不含**代码迁移实施——仅给属性改动 + 风险清单 + 验证计划。代码迁移（第 4 节）由负责人在 apply 后实施。
- MySQL 8.4 **服务端**数据目录迁移属 **L4，本草案不执行**，仅引用脚本。

## 2. 父属性改动清单（待 apply，需 CI 确认版本三元组）
`bone-parent/pom.xml`：

| 属性 | 当前 | 建议目标 | 备注 |
|---|---|---|---|
| `spring-boot.version` | 3.2.5 | **3.5.5** | 取 3.5.x 稳定线；确切 patch 以 CI 为准 |
| `spring-cloud.version` | 2023.0.3 | **2024.0.4** | 对应 Spring Cloud 2024.0.x 列车 |
| `spring-cloud-alibaba.version` | 2023.0.1.2 | **2023.0.3.2** | 须与上面两者匹配，查官方兼容矩阵 |

> 注：上述三元组为最佳估计。**未经 CI 验证前不要提交**；若报矩阵不匹配，微调 SCA patch 即可。

## 3. 必做的耦合升级（同批）
- `springdoc.version` 2.3.0 → **2.8.0**（旧版 UI 不兼容 Boot 3.5）
- `mysql-connector.version` 8.0.33 → **8.4.0**（连接器；服务端迁移见 L4）
- `redisson` 3.30.0 / `rocketmq` 2.3.0 已先行升，确认与 Boot 3.5 starter 匹配
- `skywalking` 9.7.0 → 与部署侧 agent 版本对齐（apm-toolkit 耦合，另行评审，**不**在本专项强绑）

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
- **JDK 17 → 21 = L3 独立风险**：需同步 `lombok 1.18.30→1.18.38+`、JaCoCo 已就绪；建议与本专项解耦，单独评审。

## 7. 回滚
- 保留当前分支；Boot 升级建议拆为「父属性三连改」+「代码迁移」两个 commit，便于二分定位。
- 服务端回滚依赖 MySQL 备份（见 L4 脚本）。
