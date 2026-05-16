# Bone 项目 Code Wiki

## 1. 项目概述

**Bone**（口号：Build Once, Natively Everywhere）是一个企业级全栈开源快速开发平台，采用**元数据驱动**架构，目标是通过配置减少重复编码，实现一次构建、多端运行。

### 1.1 项目定位

- **前后端分离**的**微服务/模块化单体**混合架构
- **后端**：Java 17 + Spring Boot 3.2 多模块 Maven 工程
- **前端**：React 18 + TypeScript 微前端（Qiankun）工程，使用 npm workspaces 管理

### 1.2 四大核心引擎

1. **智能元数据引擎**（Smart Metadata）— 动态建模、代码生成
2. **企业主数据平台**（Master Data）— 主数据治理与质量管控
3. **ExtPoint 扩展引擎**（Extension）— 插件化扩展点机制
4. **集成引擎**（Integration）— 多协议连接器与流程编排

### 1.3 引擎协同流程

```
智能元数据引擎 → 定义数据骨架与基础业务规则（回答"是什么"）
        ↓
企业主数据平台 → 确保核心数据一致可信（保障"数据质量"）
        ↓  
ExtPoint扩展引擎 → 注入个性化业务逻辑（解决"怎么做"）
        ↓
   集成引擎 → 连接外部系统与数据生态（实现"和谁交互"）
        ↓
   企业级数字化平台（支撑全场景业务运行）
```

---

## 2. 技术栈

### 2.1 后端技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 构建工具 | Maven | 3.8+ |
| 基础框架 | Spring Boot | 3.2.5 |
| 微服务生态 | Spring Cloud | 2023.0.3 |
| 微服务生态 | Spring Cloud Alibaba | 2023.0.1.2 |
| 数据库 | MySQL | 8.0.33 |
| 连接池 | HikariCP | 5.1.0 |
| ORM | JPA（Hibernate）+ MyBatis | 混合使用 |
| 缓存 | Redis | 7.x |
| 缓存客户端 | Redisson | 3.27.2 |
| 认证授权 | SA-Token | 1.39.0 |
| API 文档 | SpringDoc OpenAPI | 2.3.0 |
| 可观测性 | SkyWalking | 9.7.0 |
| 可观测性 | Spring Boot Admin | 3.0.0 |
| 工具类 | Lombok | 1.18.30 |
| 工具类 | MapStruct | 1.5.5.Final |
| 表达式引擎 | Aviator | 5.4.1 |
| 消息队列 | RocketMQ | 5.2 |
| 分布式事务 | Seata | 2.0 |
| 数据库迁移 | Flyway | 10.11.0 |

### 2.2 前端技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18 |
| 类型系统 | TypeScript | 5.2+ |
| 构建工具 | Vite | 5.0+ |
| UI 组件库 | Ant Design | 5.12 |
| UI 组件库 | @ant-design/pro-components | - |
| 路由 | React Router | 6.20+ |
| 状态管理 | Redux Toolkit | 2.0 |
| 状态管理 | React Redux | 9.0+ |
| HTTP 客户端 | Axios | 1.6+ |
| 微前端 | Qiankun | 2.10+ |
| 微前端插件 | vite-plugin-qiankun | - |
| 代码检查 | ESLint | 8 |
| 格式化 | Prettier | 3.1+ |
| 测试 | Vitest | 2.0 |

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
│   ├── bone-metadata/         # 元数据引擎
│   ├── bone-metadata-sdk/     # 元数据 SDK（含 @EnableSqlRepositories 自定义仓储机制）
│   ├── bone-smartmeta/        # 智能元数据引擎（engine + starter）
│   ├── bone-extension-engine/ # 扩展引擎
│   │   ├── bone-extension-sdk/
│   │   └── bone-extension-studio/
│   ├── bone-integration/      # 集成引擎
│   ├── bone-workflow/         # 工作流引擎
│   └── bone-procurement/      # 采购/供应链相关引擎
├── bone-platform/             # 企业共享平台服务
│   ├── bone-iam/              # 身份与访问管理（端口 8080）
│   ├── bone-gateway/          # API 网关
│   ├── bone-masterdata/       # 主数据服务
│   ├── bone-system/           # 系统管理（端口 8083）
│   ├── bone-file/             # 文件服务
│   ├── bone-notification/     # 通知服务
│   └── bone-integration/      # 平台级集成服务
├── bone-business/             # 业务域模块
│   ├── bone-admin/
│   ├── bone-trade/
│   └── tpa-saas/              # TPA SaaS 业务
│       ├── bone-auth/
│       ├── bone-auth-sdk/
│       ├── bone-core/
│       ├── bone-dependencies/
│       ├── bone-log-sdk/
│       ├── bone-lowcode/
│       ├── bone-platform/
│       └── bone-tpa-saas/
│           ├── bone-tpa/
│           ├── bone-tpa-sdk/
│           ├── bone-tpa-push/
│           ├── bone-tpa-intelligent-adjustment/
│           ├── bone-tpa-core/
│           ├── bone-tpa-api/
│           └── bone-tpa-facade/
├── bone-sdk/                  # 客户端 SDK
│   ├── bone-client-sdk/
│   └── bone-openapi-sdk/
├── bone-tool/                 # 开发工具
│   └── bone-codegen/          # 代码生成器（同样遵循 DDD 分层）
└── bone-blueprint/            # 项目蓝图示例
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
│   └── bone-generator-app/    # 代码生成器微应用（端口 3009）
└── packages/
    ├── core/
    │   └── event-bus/         # @bone/core/event-bus（独立构建 dist/）
    ├── shared-components/     # @bone/shared-components
    ├── shared-utils/          # @bone/shared-utils
    ├── shared-services/       # @bone/shared-services
    ├── shared-types/          # @bone/shared-types
    └── ui/
        └── design-system/
```

---

## 4. 关键类与函数说明

### 4.1 核心框架类（bone-framework）

#### 4.1.1 bone-core

| 类名 | 说明 | 关键方法 | 文件路径 |
|------|------|----------|----------|
| `AbstractEntity<ID>` | 实体基类，包含审计字段和逻辑删除 | - | [AbstractEntity.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/domain/entity/AbstractEntity.java) |
| `TenantAbstractEntity` | 多租户基础实体，增加 `tenantId`、`bizIdentityCode` | - | [TenantAbstractEntity.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/tenant/TenantAbstractEntity.java) |
| `TenantContext` | 线程级租户上下文传递 | - | [TenantContext.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/tenant/context/TenantContext.java) |
| `ApiResponse<T>` | 统一 REST 响应包装 | `success(T data)`, `error(String message)` | [ApiResponse.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/result/ApiResponse.java) |
| `PageResult<T>` | 统一分页结果包装 | - | [PageResult.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/result/PageResult.java) |
| `BizException` | 业务异常 | - | [BizException.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/exception/BizException.java) |
| `DistributedIdGenerator` | 分布式 ID 生成器 | - | [DistributedIdGenerator.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/util/DistributedIdGenerator.java) |
| `AggregateRoot` | 聚合根标记接口 | - | [AggregateRoot.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/domain/AggregateRoot.java) |
| `DomainEvent` | 领域事件基类 | - | [DomainEvent.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/domain/DomainEvent.java) |
| `TransmittableThreadLocal` | 可传递的线程本地变量 | - | [TransmittableThreadLocal.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/threadlocal/TransmittableThreadLocal.java) |
| `UseCaseExecutor` | 用例执行器 | - | [UseCaseExecutor.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/usecase/UseCaseExecutor.java) |

#### 4.1.2 关键注解

| 注解名 | 说明 | 文件路径 |
|--------|------|----------|
| `@Id` | 标识主键字段 | [Id.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/annotation/Id.java) |
| `@Deleted` | 标识逻辑删除字段 | [Deleted.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/annotation/Deleted.java) |
| `@Version` | 标识乐观锁版本字段 | [Version.java](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-framework/bone-core/src/main/java/com/bone/core/annotation/Version.java) |

### 4.2 核心引擎类

#### 4.2.1 bone-extension-engine

| 类名 | 说明 | 文件路径 |
|------|------|----------|
| `ExtensionPoint` | 扩展点注解 | - |
| （待补充更多） | - | - |

#### 4.2.2 bone-metadata-sdk

| 类名 | 说明 | 关键特性 | 文件路径 |
|------|------|----------|----------|
| `@EnableSqlRepositories` | 启用自定义元数据仓储机制 | - | - |
| `BaseRepository` | 基础仓储接口 | - | - |
| `SqlBuilder` | SQL 构建器 | - | - |

### 4.3 业务模块类

（业务模块具体类待补充，主要遵循 DDD 分层架构）

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
    ├── persistence/           # JPA / MyBatis Mapper、PO、仓储实现
    └── ...
```

### 5.2 依赖规则（不可违反）

```
adapter/web → application → domain ← infrastructure
```

- `domain` 层**禁止**依赖任何外部框架（Spring、MyBatis、JPA 注解除外仅在 PO 中使用，但领域模型本身应保持纯净）
- `application` 层**禁止**直接调用 `infrastructure`
- 跨模块调用通过 `domain.repository` 接口或应用层服务完成

### 5.3 关键设计模式

- **CQRS 物理分离**：`command` 包与 `query` 包在同一模块内分离，命令走写模型（带事务），查询走读模型（只读）
- **富领域模型**：聚合根使用工厂方法构造，例如 `User.register(...)`、`Username.of(...)`
- **仓储模式**：接口定义在 `domain.repository`，实现放在 `infrastructure.persistence`
- **自定义元数据仓储**：`bone-metadata-sdk` 提供 `@EnableSqlRepositories` 机制，类似 Spring Data 但为自研实现
- **多租户**：表均含 `tenant_id` 与 `biz_identity_code`，配合 `TenantContext` 实现数据隔离
- **软删除**：全局逻辑删除字段 `deleted`（TINYINT）

### 5.4 命名约定

| 元素类型 | 命名规则 | 示例 |
|----------|----------|------|
| 聚合根 | `{名词}` | `User` |
| 值对象 | `{名词}` | `Username` |
| 命令 | `{动作}{对象}Command` | `CreateUserCommand` |
| 查询 | `{对象}{条件}Qry` | `UserByIdQry` |
| 命令处理器 | `{命令名}Handler` | `CreateUserCommandHandler` |
| 查询处理器 | `{查询名}Handler` | `UserByIdQryHandler` |

---

## 6. 构建与运行

### 6.1 后端构建与运行

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

# 运行特定服务
java -jar bone-iam/target/bone-iam-1.0.0.jar
```

### 6.2 前端构建与运行

项目根目录和各子应用均独立管理依赖。推荐按应用单独启动。

```bash
# 进入 bone-frontend 后，为所有 workspace 安装依赖
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

批量启动所有前端应用的脚本：
- `bone-frontend/restart-all-apps.sh` — 停止并重启所有 7 个前端应用，日志输出到 `logs/` 目录
- 详细用法见 [SCRIPT_USAGE.md](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-frontend/SCRIPT_USAGE.md)

### 6.3 开发环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+（推荐，与 Vite 5 兼容） |
| MySQL | 8.0+ |
| Redis | 7.x |

### 6.4 已知服务端口

| 服务 | 端口 |
|------|------|
| bone-iam | 8080 |
| bone-system | 8083 |
| bone-integration（引擎） | 30888 |
| bone-shell（前端主应用） | 3000 |
| bone-iam-app | 3003 |
| bone-metadata-app | 3004 |
| bone-masterdata-app | 3005 |
| bone-integration-app | 3006 |
| bone-system-app | 3007 |
| bone-extension-app | 3008 |

---

## 7. 数据库与初始化

### 7.1 数据库配置

- **数据库**：MySQL（InnoDB，utf8mb4，utf8mb4_unicode_ci）
- **Schema 名**：`bone`
- **初始化脚本**：
  - `bone-init.sql`（项目根目录）— 包含表结构 + 初始数据
  - `doc/deployment/sql/` — 额外的部署 SQL

### 7.2 表设计共性

所有业务表均包含：
- `tenant_id` + `biz_identity_code`（租户隔离）
- `create_time`、`create_by`、`update_time`、`update_by`（审计）
- `deleted` TINYINT（软删除）
- JSON 类型字段（灵活 Schema）

### 7.3 主要表域

| 域 | 代表表 |
|------|--------|
| 保险业务 | `ic_insurer`、`ic_policyholder`、`ic_proposal`、`ic_policy`、`ic_product_config` |
| IAM | `iam_user`、`iam_role`、`iam_permission`、`iam_user_role`、`iam_audit_log` |
| 系统 | `sys_config`、`sys_log`、`sys_monitor` |
| 集成 | `int_connector`、`int_flow`、`int_flow_execution` |

### 7.4 初始数据

- 默认租户
- 管理员账号 `admin` / 密码（BCrypt 加密，默认 `123456`）
- 基础角色与权限
- 示例保险公司与产品数据

---

## 8. 测试策略

### 8.1 后端测试

| 工具 | 版本 | 用途 |
|------|------|------|
| JUnit 5（Jupiter） | Spring Boot 自带 | 单元与集成测试 |
| Mockito | 5.11.0 | Mock |
| Maven Surefire | 3.2.5 | 测试执行 |
| ArchUnit | 1.2.1 | 架构规则静态校验 |

- 测试类命名：`*Test.java`、`*Tests.java`
- 排除：`Abstract*.java`
- 已发现使用 ArchUnit 的模块：`bone-iam`、`bone-masterdata`、`bone-integration`，通过 `ArchitectureTest.java` 强制校验分层依赖

### 8.2 前端测试

- 框架：**Vitest 2.0**
- 运行命令：`npm run test`（workspace 级别）

---

## 9. 代码质量

### 9.1 格式化

- **Spotless** 2.43.0 + **Google Java Format** 1.17.0
- 执行：`mvn spotless:apply`
- 当前仅在 `pluginManagement` 中定义，**未自动绑定到所有模块生命周期**，需显式调用

### 9.2 静态分析

`bone-tool/pom.xml` 绑定了以下工具到 `validate` 阶段：

| 工具 | 版本 | 配置 | 说明 |
|------|------|------|------|
| Checkstyle | 10.12.7 | `checkstyle.xml` | 120 字符行宽、禁止 Tab、命名规范 |
| PMD | 6.55.0 | `pmd-ruleset.xml` | EmptyCatchBlock、EqualsNull、UseEqualsToCompareStrings、NullAssignment |
| SpotBugs | 4.2.3 | Max effort / Medium threshold | Bug 模式检测 |
| JaCoCo | 0.8.11 | - | 行覆盖率 ≥ 70%，分支覆盖率 ≥ 60%；排除 domain/entity、config、enums、DTO |

注意：上述质量工具**并非所有模块都继承激活**，主要集中在 `bone-tool` 及少量显式配置模块。

### 9.3 前端代码质量

- ESLint 配置位于 [.eslintrc.json](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-frontend/.eslintrc.json)
- Prettier 3.1+ 格式化

---

## 10. 部署与运维

### 10.1 部署方式

- **当前仓库未包含 Dockerfile 或 docker-compose.yml**，也未发现 CI/CD 流水线（GitHub Actions / GitLab CI）
- 部署产物为 **Spring Boot 可执行 JAR**（`spring-boot-maven-plugin` repackage）
- 启动方式：
  ```bash
  java -jar bone-iam/target/bone-iam-1.0.0.jar
  ```

### 10.2 环境配置

- 环境 profile：`dev`（默认）、`test`、`prod`
- 每个 Spring Boot 模块提供 `application.yml` 与 `application-{profile}.yml`
- 支持的环境变量：
  - `BONE_DB_URL`
  - `BONE_DB_USERNAME`
  - `BONE_DB_PASSWORD`
  - `BONE_SERVER_PORT`

---

## 11. 安全注意事项

- **默认密码**：`admin` / `123456`，首次部署后**必须**修改
- **SA-Token**：用于认证与 SSO，配置需关注 token 有效期与签名密钥
- **数据库密码**：部分 `application.yml` 中硬编码了明文密码，生产环境**必须**改为环境变量或配置中心注入
- **Redis 密码**：同样存在明文配置，需通过外部化配置处理
- **API 文档**：SpringDoc 在生产环境建议关闭或增加认证拦截（`knife4j` 或 Spring Security）
- **CORS**：前端微应用开发服务器开启了跨域头，生产环境需收紧为明确域名白名单

---

## 12. 开发最佳实践

### 12.1 给开发者的关键提示

1. **不要破坏分层依赖**：修改代码时，`domain` 层不能引入 Spring/MyBatis 等框架依赖；`application` 层不能直接调用 `infrastructure` 实现类
2. **保持 CQRS**：写操作使用 `*CommandHandler` 并在方法上加 `@Transactional`；读操作使用 `*QueryHandler`，保持只读
3. **统一响应格式**：Controller 返回统一使用 `ApiResponse<T>` 或 `PageResult<T>`，避免裸返回领域对象
4. **租户与审计字段**：新增实体应继承 `TenantAbstractEntity`（若需多租户）或 `AbstractEntity`；不要遗漏 `tenantId` 与审计字段的填充
5. **前端微应用约束**：
   - 微应用使用 `vite-plugin-qiankun` 打包为 UMD，需配置 `fastRefresh: false`
   - Shell 不直接使用 `vite-plugin-qiankun`，运行时通过 Qiankun JS API 加载微应用
6. **格式化**：修改 Java 文件后，建议执行 `mvn spotless:apply` 保持格式一致
7. **文档语言**：项目注释与文档以**中文**为主，新增代码注释请使用中文

### 12.2 详细规范文档

- [doc/architecture/Bone-DDD-最终实践方案.md](doc/architecture/Bone-DDD-最终实践方案.md) — DDD 与分层门禁（唯一权威）
- [doc/architecture/BONE-总体架构设计方案.md](doc/architecture/BONE-总体架构设计方案.md) — 平台总体架构
- [doc/design/modules/](doc/design/modules/) — 模块详细设计
- [doc/prd/BONE产品需求文档正式版.md](doc/prd/BONE产品需求文档正式版.md) — 主 PRD
- [doc/README.md](doc/README.md) — `doc/` 总索引

---

## 13. 参考索引

| 文件/目录 | 内容 |
|-----------|------|
| [README.md](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/README.md) | 项目营销概览、快速开始 |
| [AGENTS.md](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/AGENTS.md) | AI 助手项目指南 |
| [doc/wiki/07-P0-TODO看板.md](doc/wiki/07-P0-TODO看板.md) | 平台未完成项与工程债 |
| [bone-parent/pom.xml](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-parent/pom.xml) | 依赖版本锁定与全局插件配置 |
| [bone-init.sql](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-init.sql) | 数据库初始化脚本 |
| [bone-frontend/package.json](file:///Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-frontend/package.json) | 前端项目配置与工作区定义 |

---

*本文档基于 Bone 项目的当前状态生成，随着项目的演进，内容可能会有所变化。*
