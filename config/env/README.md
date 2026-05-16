# 开发环境配置（集中管理）

## 原则

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

完整列表见 [`.env.example`](../../.env.example)。

## 与初始化 SQL

- 运行时 DDL + 种子：[bone-init.sql](../../bone-init.sql)  
  - **开发默认登录**：`admin` / `123456`（仅本地；生产必须改密）
- 规范与文档化 DDL：[doc/architecture/数据库开发规范.md](../../doc/architecture/数据库开发规范.md)、[初始脚本.sql](../../doc/architecture/初始脚本.sql)

本地库表若与脚本不一致，先运行 `./scripts/dev/db-verify.sh` 查看差异；**全量重建**（会 DROP 表）需显式执行 `./scripts/dev/db-init.sh`。

## IDE

- **IntelliJ**：Run Configuration → Environment → 勾选「EnvFile」或「Load from path」→ 选仓库根 `.env`
- **VS Code**：Java/Spring 扩展可使用 `envFile` 指向 `${workspaceFolder}/.env`
