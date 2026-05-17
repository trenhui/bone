# bone-platform-integration

平台侧**集成编排**服务（连接器、流程、执行监控），Maven 构件 ID：`bone-platform-integration`。

> 与 `bone-engine/bone-integration`（Camel 运行时引擎，默认端口 30888）**不是同一模块**，勿混用依赖坐标。

## 构建

```bash
mvn -pl :bone-platform-integration -am compile -DskipTests
mvn -pl :bone-platform-integration test
```

## API 前缀

- REST：`/integration/**`（流程 `/integration/flows`、连接器 `/integration/connectors`、监控 `/integration/executions`）
- 统一响应：`com.bone.core.model.ApiResponse` / `PageResult`

## 数据库

DDL 真源：仓库根目录 [`bone-init.sql`](../../bone-init.sql)（`int_*` 表）。禁止模块内 Flyway，见 [`doc/architecture/数据库开发规范.md`](../../doc/architecture/数据库开发规范.md)。

## 安全

- 开发默认：`bone.integration.security.jwt-enabled=false`（`/integration/**` 放行）
- 生产：`export BONE_INTEGRATION_JWT_ENABLED=true`，与 IAM 共用 `bone.iam.jwt.secret-key` / `Authorization: Bearer`

## 领域事件告警（INT-06）

| 变量 | 说明 |
|------|------|
| `BONE_INTEGRATION_ALERT_ENABLED` | 是否接入 `bone-notification`（默认 `true`） |
| `BONE_ALERT_DINGTALK_ENABLED` + `DINGTALK_WEBHOOK` | 钉钉通道 |
| `BONE_ALERT_MAIL_ENABLED` + `BONE_ALERT_MAIL_*` | 邮件通道（需 SMTP） |

通道未启用时**仅结构化日志**，不假成功。配置步骤见 [config/env/README.md](../../config/env/README.md) § 集成服务告警。
