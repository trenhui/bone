# 开发环境配置（集中管理）

## 原则

> 完整约定见 [doc/architecture/Bone-配置与环境规范.md](../../doc/architecture/Bone-配置与环境规范.md)。

| 做法 | 说明 |
|------|------|
| **单一入口** | 仓库根目录 [`.env`](../../.env)（本地，不提交）← 由 [`.env.example`](../../.env.example) 复制 |
| **零明文进 Git** | 密码、JWT、Token 只写在 `.env`；`application.yml` 仅 `${BONE_*}` 占位 |
| **启动前加载** | `source scripts/dev/load-env.sh` 或 IDE「EnvFile」指向根目录 `.env` |
| **Docker** | `docker compose --env-file .env up` 与本地共用同一文件 |

## 快速开始

```bash
# 1. 生成本地配置（首次）
cp .env.example .env
# 编辑 .env，设置 BONE_DB_PASSWORD 等

# 2. 加载环境变量（当前 shell）
source scripts/dev/load-env.sh

# 3. 校验数据库连通与表概况
./scripts/dev/db-verify.sh

# 4. 启动服务（示例）
cd bone-platform/bone-iam && mvn spring-boot:run
```

## 变量说明

| 变量 | 用途 |
|------|------|
| `BONE_DB_URL` | JDBC URL（各 Spring Boot 模块 `spring.datasource.url`） |
| `BONE_DB_USERNAME` | 数据库用户 |
| `BONE_DB_PASSWORD` | 数据库密码 |
| `BONE_REDIS_HOST` / `BONE_REDIS_PORT` / `BONE_REDIS_PASSWORD` | Redis |
| `BONE_JWT_SECRET` | IAM JWT 签名（开发可弱，生产必须轮换） |
| `SPRING_PROFILES_ACTIVE` | 默认 `dev` |
| `BONE_INTEGRATION_ALERT_ENABLED` | 集成服务领域事件是否走 `bone-notification`（默认 `true`；通道未开仍只打日志） |
| `BONE_INTEGRATION_JWT_ENABLED` | 集成 API 是否校验 IAM JWT（生产 `true`） |
| `BONE_ALERT_DINGTALK_ENABLED` | 钉钉告警通道 |
| `DINGTALK_WEBHOOK` / `DINGTALK_SECRET` | 钉钉机器人地址与加签密钥 |
| `BONE_ALERT_MAIL_ENABLED` | 邮件告警通道（需 `spring.mail.*`） |
| `BONE_ALERT_MAIL_FROM` / `BONE_ALERT_MAIL_TO` | 发件人 / 收件人 |

完整列表见 [`.env.example`](../../.env.example)。

## 与初始化 SQL

- 运行时 DDL + 种子：[bone-init.sql](../../bone-init.sql)（含扩展 Studio 表 `exts_*`）  
  - **开发默认登录**：`admin` / `123456`（仅本地；生产必须改密）
- 规范：[doc/architecture/数据库开发规范.md](../../doc/architecture/数据库开发规范.md)

本地库表若与脚本不一致，先运行 `./scripts/dev/db-verify.sh`；**全量重建**（会 DROP 库）：

```bash
./scripts/dev/db-init.sh
```

### 扩展 Studio（8088）连 MySQL

```bash
source scripts/dev/load-env.sh
cd bone-engine/bone-extension-engine/bone-extension-studio
mvn spring-boot:run -Dspring-boot.run.profiles=metadata-mysql -Dmaven.test.skip=true
```

勿对 MySQL 再跑 `schema-mysql.sql`；表结构以 `bone-init.sql` 为准。

集成测试（需 Docker）：`mvn test -pl bone-engine/bone-extension-engine/bone-extension-studio -Dtest=StudioMetadataMysqlIT`

### 开发期路由

| 入口 | 目标 |
|------|------|
| `http://localhost:3000/api/v1/extension/**` | `bone-extension-studio` `:8088`（见 `bone-shell/vite.config.ts`） |
| `http://localhost:3008/api/v1/extension/**` | 扩展微应用 dev server 直连 Studio |
| `http://localhost:8888/api/v1/extension/**` | **bone-gateway** → Studio `:8088` |
| `http://localhost:8888/api/v1/iam/**` | **bone-gateway** → IAM（默认 `:8081`，见网关 `BONE_IAM_URI`） |
| `http://localhost:8888/api/v1/masterdata/**` | **bone-gateway** → 主数据 |
| `http://localhost:8888/api/v1/system/**` | **bone-gateway** → 系统 `:8083` |
| `http://localhost:8888/api/v1/console/**` | **bone-gateway** → 系统（控制台 API） |
| `http://localhost:8888/api/v1/integration/**` | **bone-gateway** → 集成 |
| `http://localhost:8888/api/v1/generator/**` | **bone-gateway** → 代码生成 |
| `http://localhost:8888/api/v1/metadata/**` | **bone-gateway** → metadata-server `:9001` |

启动网关：`cd bone-platform/bone-gateway && mvn spring-boot:run`

Swagger：`http://localhost:8088/swagger-ui.html`（本地 `in-memory` / `metadata` profile 免 JWT）。

### 集成服务告警（bone-platform-integration :8085）

连接器/流程领域事件（INT-06）在持久化后触发 Handler；`BONE_INTEGRATION_ALERT_ENABLED=true` 时委托 `bone-notification`。

**开发默认**：只写结构化日志，不向外部发消息（各 `alert.channels.*.enabled=false`）。

**启用钉钉（示例）**——在 `.env` 中增加后 `source scripts/dev/load-env.sh` 再启动集成服务：

```bash
BONE_INTEGRATION_ALERT_ENABLED=true
BONE_ALERT_DINGTALK_ENABLED=true
DINGTALK_WEBHOOK=https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN
# 若机器人启用了加签：
# DINGTALK_SECRET=SEC...
```

```bash
source scripts/dev/load-env.sh
cd bone-platform/bone-integration && mvn spring-boot:run
```

触发验证：创建连接器 `POST /api/v1/integration/connectors`，或连接器测试失败 `POST .../connectors/{id}/test`（HIGH 级告警）。日志关键字：`integration event` / `integration alert`。

邮件通道需额外配置 `spring.mail.host` 等（见模块 `application.yml`）；未配置 SMTP 时请保持 `BONE_ALERT_MAIL_ENABLED=false`。

## IDE

- **IntelliJ**：Run Configuration → Environment → 勾选「EnvFile」或「Load from path」→ 选仓库根 `.env`
- **VS Code**：Java/Spring 扩展可使用 `envFile` 指向 `${workspaceFolder}/.env`
