# Bone — AI Agent 项目指南

> 本文件供 AI 编程助手阅读。假设读者对项目一无所知，所有信息均基于实际代码与配置，不做臆测。

---

## 1. 项目概述

**Bone**（口号：Build Once, Natively Everywhere）是一个企业级全栈开源快速开发平台，采用**元数据驱动**架构，目标是通过配置减少重复编码，实现一次构建、多端运行。

项目为**前后端分离**的**微服务/模块化单体**混合架构：
- **后端**：Java 17 + Spring Boot 3.2 多模块 Maven 工程
- **前端**：React 18 + TypeScript 微前端（Qiankun）工程，使用 npm workspaces 管理

业务定位覆盖四大引擎：
1. **智能元数据引擎**（Smart Metadata）— 动态建模、规则/表达式；**代码生成**见 `studio-generator`（与 engine 混合，见 [元数据能力对照](doc/design/modules/元数据能力-实现映射与竞品对照.md)）
2. **企业主数据平台**（Master Data）— 主数据治理与质量管控
3. **ExtPoint 扩展引擎**（Extension）— 插件化扩展点机制
4. **集成引擎**（Integration）— 多协议连接器与流程编排

---

## 2. 技术栈

### 2.1 后端

| 层级 | 技术 |
|------|------|
| 语言 | Java 17 |
| 构建工具 | Maven 3.8+ |
| 基础框架 | Spring Boot 3.2.5 |
| 微服务生态 | Spring Cloud 2023.0.3 + Spring Cloud Alibaba 2023.0.1.2 |
| 数据库 | MySQL 8.0.33 |
| 连接池 | HikariCP 5.1.0 |
| ORM | bone-metadata-sdk（自研 Spring JDBC 仓储抽象，`@EnableSqlRepositories`） |
| 缓存 | Redis 7.x + Redisson 3.27.2 + Caffeine |
| 认证授权 | Spring Security 6 + JWT（JJWT 0.12.x）；SA-Token 1.39.0 仅 BOM 声明 |
| API 文档 | SpringDoc OpenAPI 2.3.0（Swagger UI） |
| 可观测性 | SkyWalking 9.7.0、Spring Boot Admin 3.0.0、Micrometer Prometheus |
| 工具类 | Lombok 1.18.30、MapStruct 1.5.5.Final、Jackson |
| 表达式引擎 | Aviator 5.4.1（扩展引擎使用） |
| 消息队列 | RocketMQ 5.2（部分模块） |
| 分布式事务 | Seata 2.0（支持 AT/TCC） |

### 2.2 前端

| 层级 | 技术 |
|------|------|
| 框架 | React 18 + TypeScript 5.2+ |
| 构建工具 | Vite 5.0+ |
| UI 组件库 | Ant Design 5.12 + @ant-design/pro-components |
| 路由 | React Router 6.20+ |
| 状态管理 | Redux Toolkit 2.0 + React Redux 9.0+ |
| HTTP 客户端 | Axios 1.6+ |
| 微前端 | Qiankun 2.10+ + vite-plugin-qiankun |
| 代码检查 | ESLint 8 + Prettier 3.1+ |
| 包管理 | npm workspaces（兼容 pnpm-workspace.yaml） |

---

## 3. 模块结构

### 3.1 后端模块（Maven 多模块）

根 POM（`pom.xml`）为聚合模块，所有版本与依赖在 `bone-parent/pom.xml` 中统一管理。

```
bone/                          # 根聚合模块
├── bone-parent/               # BOM / 依赖管理 / 插件配置
├── bone-framework/            # 原子核心能力
│   ├── bone-core/             # 基础实体、注解、统一响应、工具类、租户上下文
│   ├── bone-utils/            # 通用工具
│   ├── bone-datasource/       # 数据源抽象与动态数据源
│   ├── bone-security/         # 安全组件
│   └── bone-web/              # Web 层封装（Spring Web、校验、AOP、全局异常）
├── bone-engine/               # 四大引擎与核心中间件
│   ├── bone-metadata-sdk/     # 平台数据面（P0 持久化 + EAV）
│   ├── bone-metadata-server/  # 扩展字段 REST（:9001，选配）
│   ├── bone-metadata-engine/  # 智能元数据引擎（core + starter，选配）
│   ├── bone-extension-engine/ # 扩展引擎
│   │   ├── bone-extension-sdk/
│   │   └── bone-extension-studio/
│   ├── go-engine/             # Bone Metadata Go（Go 版元数据引擎，与 Java 版并存，定位待定）
│   ├── bone-workflow/         # 工作流引擎（规划中）
│   └── bone-procurement/      # 采购/供应链相关引擎（规划中）
├── bone-platform/             # 企业共享平台服务
│   ├── bone-iam/              # 身份与访问管理（端口 8081）
│   ├── bone-gateway/          # API 网关（端口 8888，骨架：目前仅 traceId 透传 filter）
│   ├── bone-masterdata/       # 主数据服务
│   ├── bone-system/           # 系统管理（端口 8083）
│   ├── bone-file/             # 文件服务（规划中，暂无实现）
│   ├── bone-notification/     # 通知服务（目前仅告警通道 Alert）
│   └── bone-integration/      # 唯一集成服务（:8085，/api/v1/integration）
├── bone-sdk/                  # 客户端 SDK
│   ├── bone-client-sdk/
│   └── bone-openapi-sdk/
├── bone-blueprint/            # DDD 参考实现（端口 8082，订单示例）
└── bone-engine/studio-generator/  # Studio 代码生成（DDD 分层，替代原 bone-tool/bone-codegen）
```

### 3.2 前端模块（npm workspaces）

```
bone-frontend/
├── apps/
│   ├── bone-shell/            # Qiankun 主应用（端口 3000）
│   ├── bone-iam-app/          # IAM 微应用（端口 3003）
│   ├── bone-metadata-app/     # 元数据微应用（端口 3004）
│   ├── bone-masterdata-app/   # 主数据微应用（端口 3005）
│   ├── bone-integration-app/  # 集成微应用（端口 3006）
│   ├── bone-system-app/       # 系统管理微应用（端口 3007）
│   ├── bone-extension-app/    # 扩展引擎微应用（端口 3008）
│   └── bone-generator-app/    # Studio 代码生成微应用
└── packages/
    ├── shared-components/     # @bone/shared-components
    ├── shared-utils/          # @bone/shared-utils
    ├── shared-services/       # @bone/shared-services
    ├── shared-types/          # @bone/shared-types
    └── core/event-bus/        # @bone/core/event-bus（独立构建 dist/）
```

---

## 4. 构建与运行命令

### 4.1 后端（Maven）

项目使用 **Aliyun Maven** 镜像加速依赖下载。

```bash
# 全量编译并安装到本地仓库
mvn clean install

# 跳过测试
mvn clean install -DskipTests=true

# 指定环境 profile（dev 为默认）
mvn clean install -Pprod

# 代码格式化（Google Java Format）
mvn spotless:apply

# 仅运行测试
mvn test

# 执行静态分析 + 测试 + 覆盖率（在绑定了质量插件的模块中）
mvn verify
```

### 4.2 前端

项目根目录和各子应用均独立管理依赖。推荐按应用单独启动。

```bash
# 进入 bone-frontend 后，为所有 workspace 安装依赖
# 项目提供了辅助脚本：
cd bone-frontend
bash setup.sh                 # 为每个 app/package 单独执行 npm install

# 启动主应用（Shell）
npm run dev                   # 仅启动 bone-shell

# 在单个微应用目录下启动
npm run dev                   # 启动 Vite 开发服务器
npm run build                 # tsc && vite build
npm run lint                  # ESLint
npm run preview               # Vite preview
```

批量启动所有前后端应用的脚本：
- `bone-frontend/restart-all-apps.sh` — 停止并重启所有 8 个前端应用，日志输出到 `logs/` 目录。
- 详细用法见 `bone-frontend/SCRIPT_USAGE.md`。

### 4.3 开发环境要求

- **JDK**：17+
- **Maven**：3.8+
- **Node.js**：18+（推荐，与 Vite 5 兼容）
- **数据库**：MySQL 8.0+
- **缓存**：Redis 7.x

---

## 5. 代码组织与架构规范

### 5.1 分层架构（强制）

后端严格遵循 **DDD + CQRS + 整洁架构/六边形架构（4 层）**。每个业务/平台模块内部包结构如下：

```
com.bone.{module}
├── adapter/web/               # 适配器层（入站）
│   ├── controller/            # REST Controller
│   ├── converter/             # DTO <-> Command/Query 组装器
│   └── dto/
│       ├── req/               # 请求 DTO
│       └── resp/              # 响应 DTO
├── application/               # 应用层（用例编排）
│   ├── command/
│   │   ├── cmd/               # 命令对象
│   │   └── handler/           # 命令处理器（@Transactional 边界）
│   ├── query/
│   │   ├── qry/               # 查询对象
│   │   ├── handler/           # 查询处理器（只读）
│   │   └── dto/               # 查询结果 DTO
│   └── event/                 # 应用事件
├── domain/                    # 领域层（**纯净，不允许依赖框架**）
│   ├── model/
│   │   ├── aggregate/         # 聚合根
│   │   ├── entity/            # 实体
│   │   ├── valueobject/       # 值对象
│   │   └── event/             # 领域事件
│   ├── service/               # 领域服务
│   └── repository/            # 仓储接口（写侧）
├── common/                    # 模块级公共工具
│   ├── exception/
│   ├── result/
│   └── util/
└── infrastructure/            # 基础设施层（出站适配器）
    ├── persistence/           # 基于 bone-metadata-sdk 的仓储实现、PO
    └── ...
```

### 5.2 依赖规则（不可违反）

```
adapter/web → application → domain ← infrastructure
```

- `domain` 层**禁止**依赖任何外部框架（Spring、MyBatis、JPA 注解除外仅在 PO 中使用，但领域模型本身应保持纯净）。
- `application` 层**禁止**直接调用 `infrastructure`。
- 跨模块调用通过 `domain.repository` 接口或应用层服务完成。

### 5.3 核心基础类

| 类 | 位置 | 作用 |
|---|---|---|
| `AbstractEntity` | `bone-core` | 全局基础实体，包含 `createdAt`、`createdBy`、`updatedAt`、`updatedBy`、`deleted`（软删） |
| `TenantAbstractEntity` | `bone-core` | 多租户基础实体，增加 `tenantId` |
| `TenantContext` | `bone-core` | 线程级租户上下文传递 |
| `ApiResponse<T>` | `bone-core` | 统一 REST 响应包装 |
| `PageResult<T>` | `bone-core` | 统一分页结果包装 |

### 5.4 关键设计模式

- **CQRS 物理分离**：`command` 包与 `query` 包在同一模块内分离，命令走写模型（带事务），查询走读模型（只读）。
- **富领域模型**：聚合根使用 `AggregateRoot` / `TenantAggregateRoot`（ADR-0011）+ 工厂方法；读侧 DSL（`QueryBuilder`/`FluentQuery`）须 `@ReadSideOnly`，禁止在 `domain` 与 CommandHandler 使用。
- **仓储模式**：接口定义在 `domain.repository`，实现放在 `infrastructure.persistence`。
- **自定义元数据仓储**：`bone-metadata-sdk` 提供 `@EnableSqlRepositories` 机制，类似 Spring Data 但为自研实现。**该项目唯一持久化方案，禁止引入 MyBatis-Plus、JPA/Hibernate、MyBatis 等其他 ORM 框架。**
- **多租户**：表均含 `tenant_id`（租户隔离），配合 `TenantContext` 实现数据隔离；`biz_identity_code` 仅在部署 SQL（`doc/deployment/sql/`）中存在，`bone-init.sql` 未包含。
- **软删除**：全局逻辑删除字段 `deleted`（TINYINT）。

### 5.5 详细规范文档

- `doc/architecture/Bone-DDD-最终实践方案.md` — **DDD 与分层门禁唯一权威**
- `doc/architecture/Bone-API-规范.md` — **API + 错误码 + 日志** 统一契约（§4 台账、§10 日志）
- `doc/architecture/数据库开发规范.md` — DDL 与表结构（独立，不并入 API 文档）
- `doc/architecture/BONE-总体架构设计方案.md` — 平台总体架构、NFR、安全与数据策略
- `doc/architecture/bone-前端架构.md` — 前端微前端与工程约定（UI 见 `doc/architecture/frontend/frontend-ui-spec.md`）
- `doc/design/modules/` — 模块详细设计（控制台、元数据、主数据、集成、扩展、IAM、系统、Generator、SmartMeta）
- `doc/prd/BONE产品需求文档正式版.md` — 主 PRD；索引见 `doc/prd/README.md`
- `doc/README.md` — 全库文档索引；历史 `doc/DDD/`、`doc/arch/`、`doc/前端规范/`、`doc/数据库/` 已移除，架构见 `doc/architecture/`

---

## 6. 测试策略

### 6.1 后端测试

| 工具 | 版本 | 用途 |
|---|---|---|
| JUnit 5（Jupiter） | Spring Boot 自带 | 单元与集成测试 |
| Mockito | 5.11.0 | Mock |
| Maven Surefire | 3.2.5 | 测试执行 |
| ArchUnit | 1.2.1 | **架构规则静态校验**（重点） |

- 测试类命名：`*Test.java`、`*Tests.java`
- 排除：`Abstract*.java`
- 已发现使用 ArchUnit 的模块（共 8 个）：`bone-blueprint`、`bone-iam`、`bone-masterdata`、`bone-metadata-server`、`studio-generator`、`bone-extension-studio`、`bone-integration`、`bone-system`，通过 `ArchitectureTest.java` + `FreezingArchRule` 强制校验分层依赖。
- 其他测试覆盖：扩展引擎（PromotionServiceTest 等）、SmartMeta 引擎（ExpressionEngineTest、BusinessRuleEngineTest 等）。
- **现状**：多数模块已建立 `src/test/java` 目录，但覆盖率总体偏稀疏。

### 6.2 前端测试

- 框架：**Vitest 2.0**（README 提及，但实际配置以各应用为准）
- 运行命令：`npm run test`（workspace 级别）

---

## 7. 代码质量

### 7.1 格式化

- **Spotless** 2.43.0 + **Google Java Format** 1.17.0
- 执行：`mvn spotless:apply`（格式化）/ `mvn spotless:check`（校验）
- 在 `bone-parent/pom.xml` 中绑定到 `validate` 阶段（`spotless:check`），所有继承 bone-parent 的模块构建时自动执行格式校验；CI（`.github/workflows/ci.yml`）亦显式调用 `mvn spotless:check`。

### 7.2 静态分析（按模块配置）

仅以下两个模块在 `pom.xml` 中配置了静态分析工具（其余平台模块未配置）：

| 模块 | 工具 | 版本 | 阶段 | 说明 |
|---|---|---|---|---|
| `bone-metadata-sdk` | Checkstyle | 3.3.0（maven 插件） | 显式调用 | `google_checks.xml`，`failsOnError=false` |
| `bone-metadata-sdk` | SpotBugs | 4.7.3.0 | 显式调用 | `effort=Max`，`threshold=Low`，`failOnError=false` |
| `bone-metadata-sdk` | JaCoCo | 0.8.10 | `prepare-package` | PACKAGE 级 LINE 覆盖率 ≥ 80% |
| `bone-metadata-sdk` | OWASP dependency-check | 8.4.0 | 显式调用 | `failBuildOnCVSS=7` |
| `bone-extension-sdk` | Checkstyle | 3.3.0（maven 插件） | 显式调用 | 自定义 `checkstyle.xml`，`failsOnError=false` |
| `bone-extension-sdk` | SpotBugs | 4.6.0.0 | `verify` | `effort=Max`，`threshold=Low` |
| `bone-extension-sdk` | PMD | 3.21.0（maven 插件） | 显式调用 | 仅声明版本，规则文件按需配置 |

**注意**：
- 平台模块（`bone-iam`、`bone-system`、`bone-masterdata`、`bone-integration` 等）**未配置**上述静态分析工具，仅依赖 Spotless 格式校验与 ArchUnit 架构校验。
- 上述工具均设置 `failsOnError=false` / `failOnError=false`，不会阻断构建。

### 7.3 前端代码质量

- ESLint 配置位于 `bone-frontend/.eslintrc.json`
- Prettier 3.1+ 格式化

---

## 8. 数据库与初始化

- **数据库**：MySQL（InnoDB，utf8mb4，utf8mb4_unicode_ci）
- **Schema 名**：`bone`
- **初始化脚本**：
  - `bone-init.sql`（项目根目录，676 行）— 包含表结构 + 初始数据
  - `doc/deployment/sql/` — 额外的部署 SQL（`bone-midplatform.sql`、`bonecore.sql` 等）

### 8.1 表设计共性

所有业务表均包含：
- `tenant_id`（租户隔离）
- `created_at`、`created_by`、`updated_at`、`updated_by`（审计）
- `deleted` TINYINT（软删除）
- JSON 类型字段（灵活Schema）

> **注意**：`biz_identity_code` 字段在 `bone-metadata-sdk` 代码和部署 SQL（`doc/deployment/sql/bonecore.sql`）中存在，但 `bone-init.sql`（DDL 唯一真源）中尚未包含。

### 8.2 主要表域

| 域 | 代表表 |
|---|---|
| 保险业务 | `ic_insurer`、`ic_policyholder`、`ic_proposal`、`ic_policy`、`ic_product_config` 等 |
| IAM | `iam_user`、`iam_role`、`iam_permission`、`iam_user_role`、`iam_audit_log` |
| 系统 | `sys_config`、`sys_log`、`sys_monitor` |
| 集成 | `int_connector`、`int_flow`、`int_flow_node`、`int_flow_connection`、`int_execution_log`、`int_dead_letter`、`int_template` |

### 8.3 初始数据

- 默认租户
- 管理员账号 `admin` / 密码（BCrypt 加密，默认 `123456`）
- 基础角色与权限
- 示例保险公司与产品数据

---

## 9. 部署与运维

### 9.1 部署方式

- **Docker**：`docker/Dockerfile` 提供多阶段构建（Maven 构建 + JRE alpine 运行时），非 root 用户运行，含 healthcheck；`docker-compose.yml` 编排 mysql + redis + 各业务服务。
- **CI/CD**：`.github/workflows/` 下含 6 个 GitHub Actions 工作流：
  - `ci.yml` — 后端质量检查（spotless:check + verify）+ 前端构建
  - `iam-gateway.yml` — IAM 与网关模块
  - `blueprint.yml` — bone-blueprint 模块
  - `generator-studio.yml` — studio-generator 模块
  - `extension-studio.yml` — 扩展引擎 studio 模块
  - `docs-compliance.yml` — 文档合规检查
- 部署产物为 **Spring Boot 可执行 JAR**（`spring-boot-maven-plugin` repackage）。
- 启动方式：
  ```bash
  java -jar bone-iam/target/bone-iam-1.0.0.jar
  ```

### 9.2 环境配置

- 环境 profile：`dev`（默认）、`test`、`prod`
- 每个 Spring Boot 模块提供 `application.yml` 与 `application-{profile}.yml`
- 支持的环境变量：
  - `BONE_DB_URL`
  - `BONE_DB_USERNAME`
  - `BONE_DB_PASSWORD`
  - `BONE_SERVER_PORT`

### 9.3 已知服务端口

| 服务 | 端口 |
|---|---|
| bone-iam | 8081 |
| bone-blueprint | 8082 |
| bone-system | 8083 |
| bone-masterdata | 8084 |
| bone-integration | 8085 |
| studio-generator | 8086 |
| bone-extension-studio | 8088 |
| bone-gateway | 8888 |
| bone-metadata-server | 9001 |
| bone-shell（前端主应用） | 3000 |
| bone-iam-app | 3003 |
| bone-metadata-app | 3004 |
| bone-masterdata-app | 3005 |
| bone-integration-app | 3006 |
| bone-system-app | 3007 |
| bone-extension-app | 3008 |

---

## 10. 安全注意事项

- **默认密码**：`admin` / `123456`，首次部署后**必须**修改。
- **认证**：Spring Security 6 + JWT（JJWT 0.12.x），需关注 token 有效期与签名密钥；SA-Token 仅在 BOM 中声明，未实际启用。
- **数据库密码**：部分 `application.yml` 中硬编码了明文密码，生产环境**必须**改为环境变量或配置中心注入。
- **Redis 密码**：同样存在明文配置，需通过外部化配置处理。
- **API 文档**：SpringDoc 在生产环境建议关闭或增加认证拦截（`knife4j` 或 Spring Security）。
- **CORS**：前端微应用开发服务器开启了跨域头，生产环境需收紧为明确域名白名单。

---

## 11. 给 AI 助手的关键提示

1. **不要破坏分层依赖**：修改代码时，`domain` 层不能引入 Spring/MyBatis 等框架依赖；`application` 层不能直接调用 `infrastructure` 实现类。
2. **保持 CQRS（v4.2）**：写操作使用 `*CommandHandler` + `@Transactional`；读操作使用 `*QueryHandler`（只读）。**禁止** `application/usecase`、`*UseCase`、自造 `@UseCase`；`com.bone.core.usecase.*` **已从 bone-core 删除**；AI/Flow 能力发现用 `com.bone.core.capability.@Capability`。Controller **直接注入 Handler**（**禁止**直注 `application/service`、`domain/service`（领域服务）、`domain/repository`）；满足 [DDD §14.3.2](doc/architecture/Bone-DDD-最终实践方案.md) F1/F2/F3 条件时可注入 `*Facade`。`application/service` 仅允许 [DDD §14.3.1](doc/architecture/Bone-DDD-最终实践方案.md) 约束（S1/S2/S3）。模块是否适用全量 DDD 看 **性质**（`bone-extension-studio`、`studio-generator` 属应用模块），见 DDD §14.4。
3. **统一响应格式**：Controller 返回统一使用 `ApiResponse<T>` 或 `PageResult<T>`，避免裸返回领域对象。
4. **租户与审计字段**：新增实体应继承 `TenantAbstractEntity`（若需多租户）或 `AbstractEntity`；不要遗漏 `tenantId` 与审计字段的填充。
5. **命名约定**（分层见 [DDD §23.1](doc/architecture/Bone-DDD-最终实践方案.md)）：
   - 聚合根：`{名词}`
   - 值对象：`{名词}`（如 `Username`）
   - 应用层命令：`{动作}{对象}Command`（如 `CreateUserCommand`）
   - 应用层查询：`{对象}{条件}Query`（如 `UserByIdQuery`）
   - 处理器：`*CommandHandler` / `*QueryHandler`
   - adapter 入参 DTO：`*Req` / `*Qry`（仅 adapter 层，与 application `*Query` 区分）
6. **前端微应用约束**：
   - 微应用使用 `vite-plugin-qiankun` 打包为 UMD，需配置 `fastRefresh: false`。
   - Shell 不直接使用 `vite-plugin-qiankun`，运行时通过 Qiankun JS API 加载微应用。
7. **格式化**：修改 Java 文件后，建议执行 `mvn spotless:apply` 保持格式一致。
8. **文档语言**：项目注释与文档以**中文**为主，新增代码注释请使用中文。
9. **Docs-as-Code（模块合规）**：平台模板见 [Docs-as-Code-模块合规模板](doc/architecture/Docs-as-Code-模块合规模板.md)。**扩展**：`tools/extension-compliance-collector` + 详设附录 A/C；**blueprint**：`tools/blueprint-compliance-collector` + `doc/_generated/blueprint/`。已落地能力**勿**写入各模块 `backlog.yaml`；PR 须提交对应 `_generated` 并通过 `collect.py --check`。
10. **持久化方案唯一性（强制）**：所有数据访问**必须**使用自研 `bone-metadata-sdk`（`@EnableSqlRepositories`），**禁止**引入 MyBatis-Plus、JPA / Hibernate、MyBatis 等其他 ORM 框架。新增模块的 `pom.xml` 不得添加上述框架依赖；仓储实现统一基于 `BaseRepository` / `SqlBuilder` 等 SDK 能力。

---

## 12. AI 工程协作体系（V6.1）

> 本节定义 AI 编码工具的协作约束、执行流程和自主权边界。

### 12.1 硬约束清单（CI 强制执行）

| ID | 约束 | 校验方式 | 违反后果 |
|----|------|---------|---------|
| HC-001 | 禁止 MyBatis-Plus / JPA / Hibernate / MyBatis | pom.xml 依赖扫描 + grep（排除 MapStruct） | CI 阻断 |
| HC-002 | domain 层零依赖 Spring/外部框架 | ArchUnit `domainMustNotDependOnOuterLayers` | CI 阻断 |
| HC-003 | Controller 返回必须用 `ApiResponse<T>` 或 `PageResult<T>` | ArchUnit `controllerMustReturnApiResponse` | CI 阻断 |
| HC-004 | 禁止硬编码密钥/密码/Token | Gitleaks（`.gitleaks.toml`） | CI 阻断 |
| HC-005 | 核心模块测试覆盖率 ≥ 70%（初始目标，逐步提到 80%） | JaCoCo `jacoco:check` | CI 阻断 |
| HC-006 | 数据库访问必须通过 bone-metadata-sdk Repository | pom 依赖扫描 + ArchUnit | CI 阻断 |
| HC-007 | API 实现与 `doc/architecture/openapi/*-v1.yaml` 一致 | oasdiff（代码生成 vs 设计态） | PR 阻断 |
| HC-008 | 新增表必须含 `tenant_id` + `created_at` + `updated_at` + `deleted` | DDL 审查（scripts/ci-check.sh） | CI 阻断 |

### 12.2 动态上下文加载路由

AI 执行任务前，按修改路径加载对应上下文文档：

| 修改路径 | 必须加载的上下文 |
|---------|----------------|
| `**/domain/**` | `doc/architecture/Bone-DDD-最终实践方案.md` + `doc/design/modules/` 对应模块详设 |
| `**/adapter/web/**` | `doc/architecture/Bone-API-规范.md` + `doc/architecture/openapi/` 对应 YAML |
| `**/infrastructure/**` | `doc/architecture/数据库开发规范.md` + `bone-engine/bone-metadata-sdk/README.md` |
| `**/application/**` | `doc/architecture/Bone-DDD-最终实践方案.md` §14（CQRS） |
| `bone-frontend/**` | `doc/architecture/bone-前端架构.md` §6（API错误处理/状态管理/微前端通信） |
| 任何文件 | 本文件（AGENTS.md）§5 + §11 |

### 12.3 AI 自主权分级

| 等级 | 范围 | 审查要求 |
|------|------|---------|
| **L0** | 格式化、注释、局部变量重命名、添加 import | 无需审查 |
| **L1** | 编写单元测试、生成 DTO/Req/Resp、补充 OpenAPI | 必须 `./scripts/check.sh` 通过 |
| **L2** | 新增 Controller/Handler 方法、修改业务逻辑 | 需 PR 双人 Review |
| **L3** | 修改 DDL、删除已有代码、升级依赖版本、修改 CI 脚本 | 必须架构师审批 |
| **L4** | 生产数据库迁移、密钥/证书管理、发布打 tag、修改本节内容 | **完全禁止 AI 执行** |

### 12.4 熔断机制（机器强制）

- Pre-commit hook 记录 `scripts/check.sh` 连续失败次数
- 第 3 次失败后，hook **物理拒绝执行**，输出：`[MELTDOWN] 架构约束连续失败 3 次，需人工介入。`
- 清除计数：`rm .git/hooks/.check-fail-count`
- 这是**机器级熔断**，不依赖 AI 自觉遵守

### 12.5 标准交付流程

```
P1 需求理解 → 确认所属模块和 Bounded Context
    ↓
P2 上下文加载 → 按 §12.2 路由表加载文档
    ↓
P3 契约对齐 → 修改/确认 doc/architecture/openapi/ 设计态 YAML
    ↓ （人工审批：契约变更）
P4 代码实现 → 按 DDD 分层 + bone-metadata-sdk 实现
    ↓
P5 本地自检 → ./scripts/check.sh（≤3 次重试，超过则熔断）
    ↓
P6 提交 PR → CI 全量门禁（ArchUnit + JaCoCo + Gitleaks + oasdiff）
    ↓
P7 人工 Review → 双人审查 + 架构师审批（L3 变更）
    ↓
P8 合并 → 触发 Docs-as-Code 合规收集器更新 _generated/
```

### 12.6 跨工具入口适配

各 AI 工具的入口文件均薄引用本文件：

| 工具 | 入口文件 | 内容 |
|------|---------|------|
| 通用 | `AGENTS.md` | 本文件（唯一真源） |
| Claude Code | `CLAUDE.md` | `@AGENTS.md`（薄引用） |
| Cursor | `.cursorrules` | `Read AGENTS.md first.` |
| Copilot | `.github/copilot-instructions.md` | `See AGENTS.md.` |

### 12.7 与已有资产的关系

| 已有资产 | 定位 | 与本节关系 |
|---------|------|-----------|
| `.claude/agents/` | Claude Code 专用 agent 定义 | 引用本节约束，不重复定义 |
| `.claude/contracts/` | L1/L2 功能契约模板 | 修正 `mapper` → `repository`，引用 HC-001~008 |
| `.claude/hooks/` | Claude Code 专用钩子 | 保留，与 `scripts/check.sh` 互补 |
| `tools/*-compliance-collector/` | Docs-as-Code CI 派生 | 保留，在 P8 阶段执行 |
| `doc/architecture/adr/` | 21 个架构决策 | 约束的决策依据，HC 条目引用 ADR 编号 |
| ArchUnit `*ArchitectureTest` | 8 个模块的架构测试 | HC-002/003/006 的执行载体 |

---

## 13. 参考索引

| 文件/目录 | 内容 |
|---|---|
| `README.md` | 项目营销概览、快速开始 |
| `CODE_WIKI.md` | 项目知识库：四大引擎说明、关键类、依赖树、运行说明 |
| `doc/architecture/Bone-DDD-最终实践方案.md` | DDD 与分层门禁（必读） |
| `doc/architecture/README.md` | 架构文档索引 |
| `doc/wiki/07-P0-TODO看板.md` | 平台未完成项与工程债 |
| `doc/README.md` | `doc/` 总索引 |
| `bone-frontend/SCRIPT_USAGE.md` | 前端批量启动脚本说明 |
| `bone-init.sql` | 数据库初始化脚本 |
| `bone-parent/pom.xml` | 依赖版本锁定与全局插件配置 |
| `bone-engine/bone-metadata-sdk/` | 默认持久化 SDK（[README](bone-engine/bone-metadata-sdk/README.md) + [doc/](bone-engine/bone-metadata-sdk/doc/)） |
| `doc/design/modules/元数据能力-实现映射与竞品对照.md` | sdk / server / engine 定义、协作、竞品 |
| `bone-engine/README.md` | 引擎层模块索引 |
