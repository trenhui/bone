# Bone — Build Once, Natively Everywhere

**企业级全栈原生快速开发平台**

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-BSL%201.1-blue.svg)
![JDK](https://img.shields.io/badge/JDK-21+-orange.svg)

> **个人学习免费 · 商业使用需授权 · 元数据驱动 · 四大引擎协同**
> **「构建可复用的系统，创造可持续的价值。」** —— 梅山

---

> **授权说明（2026-10-11 起生效）**：本仓库自 2026-10-11 起采用 [BSL 1.1](LICENSE) 授权——
> **个人学习、研究、教学、非商业评估、开发/测试** 免费；**企业生产与商业使用需购买商业授权**。
> 该日期之前已公开发布的版本仍按 [MIT](LICENSE-MIT) 授权（不可撤销）。
> 详见 [License](#license) 章节。

**Bone 是什么**：以统一元模型驱动的企业级全栈开发平台——一次建模，既可用生成式产出可审计源码（模式 A），也可运行时动态提供 CRUD/API（模式 B）；配套主数据、扩展点、集成三大引擎，支撑企业应用从"项目制重复建设"走向"平台化持续演进"。完整能力状态以 [P0 看板](doc/wiki/07-P0-TODO看板.md) 为准。

## 阅读指引

| 你想了解… | 建议阅读 |
|-----------|----------|
| **产品定位、当前能力与演进方向** | 本文 |
| **工程实现、API、DDL、本地调试** | [doc/README.md](doc/README.md) → [架构规范](doc/architecture/README.md) · [本地构建](doc/wiki/03-本地开发与构建.md) |
| **AI 助手 / 贡献者门禁** | [AGENTS.md](AGENTS.md) · [CONTRIBUTING.md](CONTRIBUTING.md) |

**说明**：本文用于快速了解项目，不代替详细设计。具体接口、表结构、端口和实现状态以 `doc/`、OpenAPI 与代码为准。

---

## 目录

- [项目概述](#项目概述)
- [设计原则](#设计原则)
- [核心价值](#核心价值)
- [四大引擎](#四大引擎)
- [协同架构](#协同架构)
- [技术路线](#技术路线)
- [演进路线](#演进路线)
- [快速体验](#快速体验)
- [文档与社区](#文档与社区)
- [常见问题](#常见问题)
- [License](#license)

---

## 项目概述

Bone 以 **「Build Once, Natively Everywhere」** 为长期愿景：用**统一元数据**描述业务，用**标准化引擎**承载主数据、扩展与集成，让企业应用从「项目制重复建设」走向「平台化持续演进」。

产品层面对外称“四大引擎”；DDD 战略分类上，**元数据 / 建模是当前核心域**，主数据、扩展和集成是支撑域。产品能力与 DDD 投资分类用途不同，并不矛盾。

当前仓库是这一愿景的开源参考实现：元数据双模式、IAM、主数据、扩展管理和集成等能力已有代码和启动入口，完整度各不相同；尚未落地的能力在本文中明确标为“规划”或“目标”。

---

## 设计原则

以下原则用于指导架构与产品决策：

1. **元数据先行（Metadata-First）**  
   业务实体、字段、关系和规则尽量可配置、可版本化；复杂领域逻辑仍保留显式代码，避免为“可配置”牺牲可维护性。

2. **开闭原则（ExtPoint）**  
   核心流程稳定；行业差异、客户定制通过扩展点与插件注入，避免 fork 主干。

3. **单一可信数据源（Master Data）**  
   客户、组织、物料等核心对象形成权威主数据和统一视图，通过标准 REST API 分发并治理副本一致性。

4. **契约化集成（Integration）**  
   异构系统通过连接器与可编排流程对接；协议适配、幂等、重试、可观测性为一等公民。

5. **契约化 API（API-First）**  
   对外统一 `/api/v1/{domain}/**`；错误码、日志、多租户见 [Bone-API-规范](doc/architecture/Bone-API-规范.md)。

6. **实现状态可追溯**
   README 只做概览；落地状态以 [P0 看板](doc/wiki/07-P0-TODO看板.md)、模块详设和测试为准。

---

## 核心价值

### 开发效率

- **元数据驱动**：一次建模，驱动数据结构与访问行为；减少重复 CRUD 与样板工程。
- **双模式交付**：按场景选择 **模式 A · 生成式**（源码进 Git，可审计）或 **模式 B · 运行时**（动态 CRUD/API，减少标准代码生成）。详细对比见 [智能元数据引擎](#智能元数据引擎smart-metadata-engine)。
- **多端统一（愿景）**：一次建模，适配 Web 管理端、移动端 App、小程序——**Web 微前端（Qiankun）为当前主线**，移动端为规划路线。

### 企业级能力

- **身份与隔离**：RBAC、JWT 和租户上下文已形成基础能力；列级权限、SSO/MFA 按阶段完善。
- **一致性策略**：优先使用本地事务、幂等和 Outbox；确需跨服务强一致时再评估 Seata（AT/TCC）。
- **可观测性**：审计日志和部分指标已落地；链路追踪与统一监控仍在演进。

---

## 四大引擎

四大引擎是产品能力视图，不等同于四个 DDD 核心域。下文同时标注当前实现与演进目标，避免把路线图当成现状。模块关系见 [元数据能力对照](doc/design/modules/元数据能力-实现映射与竞品对照.md)。

### 智能元数据引擎（Smart Metadata Engine）

**定位**：平台核心域。用 **统一元模型（catalog）+ 可插拔的扩展字段存储** 描述业务，并提供两种互补的交付模式：

> 术语速览：**catalog**＝实体、字段和关系的登记册；**扩展字段**＝实体的动态扩展属性，SDK 提供三种存储模式——**预留列（默认推荐）**、**JSON 列**、**EAV 键值对**（极低频场景，非推荐首选）；**SmartQL**＝面向元模型的查询语言；**逃逸舱**＝标准能力覆盖不到时改用生成代码或手写代码。

| 模式 | 处理方式 | 适用场景 | 主要模块 |
|------|----------|----------|----------|
| **A · 生成式交付** | 元数据 → 生成可审计源码 → 编译部署 | 复杂领域逻辑、源码审计、深度定制 | `bone-metadata-sdk` + `bone-metadata-server` + `studio-generator` |
| **B · 运行时元数据面** | 元数据 → 运行时解释 → 动态 CRUD/API | 配置型实体、运营后台、SaaS 标准对象 | `bone-metadata-sdk` + `bone-metadata-engine` + `bone-metadata-server` |

```text
                    ┌── 模式 A：生成式（当前主线）
  meta_* 模型 ──────┤     server(catalog) → generator → Java/TS 工程 → sdk 仓储绑定物理表
                    │
                    └── 模式 B：运行时（MVP 已交付，能力演进中）
                          server(catalog) → engine → 动态 REST / SmartQL → sdk 直接读写库
```

**模式 A（生成式）** — 适合要求源码入库、包含复杂领域逻辑或需要与 ExtPoint 深度结合的场景。

**模式 B（运行时）** — 适合运营后台、配置型实体和多租户 SaaS 标准对象。**当前状态**：动态记录读写 MVP 已交付（`JdbcRuntimeRecordService` + `/api/v1/runtime/entities/{code}/records`，网关路由已配置，见 [P0 看板 META-002B](doc/wiki/07-P0-TODO看板.md)）；模型发布、热加载和低停机变更仍在演进。模式 B 成熟后，标准 CRUD 可不依赖生成器；生成器继续承担 DDD 骨架和深度定制代码的生成。

**共享能力（两模式共用）**：

| 能力方向 | 说明 |
|----------|------|
| 平台数据面 | **bone-metadata-sdk**：仓储、扩展字段三模式（预留列/JSON/EAV，默认预留列，分配失败自动降级 JSON）、多数据源方言（模式 B 的 JDBC 执行底座） |
| 建模控制面 | **bone-metadata-server**：catalog REST + 扩展字段 API（`:9001`） |
| 规则与动态访问 | **[bone-metadata-engine](bone-engine/bone-metadata-engine/)**：表达式/校验/SmartQL/动态 CRUD（模式 B 核心，逐步替代「为每张表生成代码」） |
| 权限与多租户 | 元数据绑定行级租户、列级规则（两模式统一策略） |
| 运行时演进 | 模型发布、热加载、低停机变更（引擎 + 发布流程分阶段实现） |
| 交付模式标注 | `meta_entity.delivery_mode`：`GENERATIVE` / `RUNTIME`（当前已落库） |

**相关代码**：[`bone-engine/bone-metadata-sdk`](bone-engine/bone-metadata-sdk/) · [`bone-engine/bone-metadata-server`](bone-engine/bone-metadata-server/) · [`bone-engine/bone-metadata-engine`](bone-engine/bone-metadata-engine/)（模式 B） · [`bone-engine/studio-generator`](bone-engine/studio-generator/)（模式 A） · 前端 [bone-metadata-app](bone-frontend/apps/bone-metadata-app/) · [bone-generator-app](bone-frontend/apps/bone-generator-app/)

> 能力对照与竞品：[元数据能力-实现映射与竞品对照](doc/design/modules/元数据能力-实现映射与竞品对照.md) §1.3、§3.3

---

### 企业主数据平台（Master Data Platform）

**定位**：形成核心主数据的权威记录与统一视图。

| 状态 | 能力 |
|------|------|
| **当前实现** | 主数据实体与记录管理、业务实体转换、JSON 导出、质量报告查询 |
| **演进目标** | 多品类治理、血缘追踪、清洗与质量评分、数据资产目录、标准 REST API 分发 |

**相关代码**：[`bone-platform/bone-masterdata`](bone-platform/bone-masterdata/) · [`bone-masterdata-app`](bone-frontend/apps/bone-masterdata-app/)

---

### ExtPoint 扩展引擎（Extension Engine）

**定位**：通过预定义扩展点注入差异化逻辑，尽量不修改核心流程代码。

| 状态 | 能力 |
|------|------|
| **当前实现** | 扩展点路由与缓存、表达式匹配、执行防护；Studio CRUD、版本、部署与回滚 |
| **演进目标** | 更完整的插件生命周期、制品隔离、沙箱执行、调用链观测和动态参数治理 |

**典型场景**：促销叠加、政务会签、行业 BOM 等。

**相关代码**：[`bone-engine/bone-extension-engine/bone-extension-sdk`](bone-engine/bone-extension-engine/bone-extension-sdk/) · [`bone-engine/bone-extension-engine/bone-extension-studio`](bone-engine/bone-extension-engine/bone-extension-studio/) · 前端 [bone-extension-app](bone-frontend/apps/bone-extension-app/)

---

### 集成引擎（Integration Engine）

**定位**：打通 ERP / CRM / OA / 协作与消息系统，构建企业数字生态链。

| 能力方向 | 目标 | 当前状态 |
|----------|------|-------------------|
| 多协议 | 按业务需要扩展协议连接器 | **REST/HTTP** 已实现；FTP/JDBC/MQ 返回 501 |
| 连接器工厂 | 标准化连接器 | CRUD + 连接测试（`int_connector`） |
| 流程编排 | 分支、并行、循环 | **线性** START→HTTP→END（INT-09） |
| 可靠性 | 重试、幂等、死信与最终一致性 | 执行日志 + 领域事件 + **MQ Outbox 中继**（INT-10 已交付，默认落日志，开启 `BONE_INTEGRATION_OUTBOX_MQ_ENABLED` 中继 RocketMQ） |
| Camel 编排 | 可视化 DSL 执行 | **Camel 编译器已交付**（INT-11）：`int_flow_node` 图 DSL → Camel 路由（choice/multicast）；默认执行仍为线性（INT-09），Camel 执行经 `integration.camel.execution-enabled` 启用 |

**相关代码**（唯一集成服务，勿与已移除的 engine 模块混淆）：

- 后端：[`bone-platform/bone-integration`](bone-platform/bone-integration/)（Maven 构件 `bone-platform-integration`，默认 `:8085`）
- 前端：[`bone-integration-app`](bone-frontend/apps/bone-integration-app/)（`:3006`，经 Shell `:3000` 加载）

交付进度：[P0 看板 · 集成](doc/wiki/07-P0-TODO看板.md#集成引擎bone-platformbone-integration) · 运维说明：[平台集成 README](bone-platform/bone-integration/README.md) · 收敛决策：[ADR-集成单模块](doc/architecture/adr/0037-integration-engine-single-module.md)

---

## 协同架构

- **元数据引擎**提供统一模型、仓储和两种交付路径。
- **主数据平台**基于元数据能力治理权威记录和数据质量。
- **扩展引擎**与**集成引擎**是可按场景组合的支撑能力，不构成必须串行经过的处理链路。

物理部署不要求四个独立进程。例如，集成能力由 `bone-platform/bone-integration` 单一进程承载，扩展 SDK 则嵌入业务宿主。

---

## 技术路线

| 层面 | 当前主线 | 演进方向 |
|------|----------|----------|
| 后端 | Java 21、Spring Boot 3.5、DDD + Application Service | 持续强化模块边界与契约测试 |
| 数据 | Bone Metadata SDK、MySQL、Redis | 元数据发布、热加载与低停机变更 |
| API 与安全 | REST、OpenAPI、Spring Security、JWT、Gateway | 更细粒度权限与统一治理 |
| 集成与可靠性 | HTTP、Camel 编译器、Outbox；RocketMQ 可选 | 更多连接器、流程运行时和可观测性 |
| 前端 | React 18、TypeScript、Vite 5、Ant Design 5、Qiankun | 模型驱动复用与多端适配 |
| 工程质量 | Spotless、ArchUnit、ESLint、Prettier、Vitest | 提升自动化测试和基准覆盖 |

Nacos、Sentinel、Seata、SkyWalking 等保留为按部署规模评估的技术选项，不作为所有场景的默认依赖。

---

## 演进路线

| 阶段 | 定位 | 重点 |
|------|------|------|
| **当前基线** | Build Once, Web First | 模式 A + 模式 B 运行时 MVP；catalog/扩展字段、IAM、主数据、扩展、集成已有实现 |
| **下一里程碑** | 强化运行时与工程闭环 | 模型发布/热加载、连接器扩展、数据血缘、统一可观测性和端到端验收 |
| **后续方向** | 行业与多端复用 | 行业模板、多端适配、插件生态；以真实项目验证后逐步交付 |

具体排期不在 README 承诺；交付状态与工程债见 [P0 看板](doc/wiki/07-P0-TODO看板.md) 和各模块详设。

---

## 快速体验

面向贡献者与评估者的 **最小路径**（完整端口见 [本地开发与构建](doc/wiki/03-本地开发与构建.md)）。

**开发环境拓扑**（端口真源为各模块 `application.yml`；最小登录路径见图下注释）：

![运行时拓扑](doc/design/assets/readme-runtime-topology.svg)

### 环境要求

JDK 21+ · Maven 3.8+ · Node.js 18+ · MySQL 8+ · Redis 7.x（IAM 登录必需） · Git

### 常用环境变量

`BONE_DB_URL` · `BONE_DB_USERNAME` · `BONE_DB_PASSWORD` · `BONE_SERVER_PORT` · `BONE_GATEWAY_PORT`

> `BONE_DB_PASSWORD` 需与 MySQL root 口令一致。IAM、元数据服务等数据库客户端需要该变量；网关不连接数据库。环境变量只在当前终端及其子进程中生效，另开终端时需重新设置。

### 步骤

```bash
git clone https://gitee.com/meishan315/bone.git
cd bone

# 0. 统一 MySQL root 口令（docker compose 与各后端共用，必须一致；仅当前终端生效）
export BONE_DB_PASSWORD=<你的MySQL-root口令>      # 与 MySQL root 一致；docker compose 不设置时回退 bone_root_pass

# 1. 数据库 + 缓存（docker compose 会在启动时读取上面的 BONE_DB_PASSWORD 初始化 MySQL）
docker compose up -d mysql redis
#    自备 MySQL/Redis 时：mysql -u root -p < bone-init.sql

# 2. 后端全量构建（仅验证集成可用则改为下一行子集）
mvn clean install -DskipTests
# mvn clean install -pl bone-platform/bone-integration -am -DskipTests

# 3. IAM（新终端；环境变量不跨终端，需重新 export 同一个值）
export BONE_DB_PASSWORD=<你的MySQL-root口令>
cd bone-platform/bone-iam && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 网关（新终端；Shell 前端代理指向 :8888，不启动则登录失败）
cd bone-platform/bone-gateway && mvn spring-boot:run

# 5. 系统服务（新终端；Shell 首页概览和快捷操作依赖 :8083）
export BONE_DB_PASSWORD=<你的MySQL-root口令>
cd bone-platform/bone-system && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 6. 元数据服务（新终端；网关路由依赖 :9001，建议启动）
export BONE_DB_PASSWORD=<你的MySQL-root口令>
cd bone-engine/bone-metadata-server && mvn spring-boot:run

# 7. 集成（可选，新终端；经网关访问见下表）
# export BONE_DB_PASSWORD=<你的MySQL-root口令>
# cd bone-platform/bone-integration && mvn spring-boot:run

# 8. 前端 Shell（新终端；仅启动 Shell，功能页需再启动对应微应用）
cd bone-frontend && npm ci && npm run dev
```

> **后端一键启动（可选）**：仓库 `docker-compose.yml` 已编排 `mysql · redis · iam · system · masterdata · gateway · metadata-server · integration`，镜像经 `docker/Dockerfile` 多阶段构建，首次启动会编译各模块，耗时较长；`BONE_DB_PASSWORD` 须在 `docker compose up` 前导出（见上方步骤 0）。前端仍需本地 `cd bone-frontend && npm run dev`。
> **已知缺口（L3，待架构师确认）**：`iam · system · masterdata · integration` 均通过 `spring.config.import` 拉取 Nacos 配置（默认 `127.0.0.1:8848`），但 compose 尚未编排 Nacos 服务、也未设 `BONE_NACOS_ADDR`，故这 4 个服务在容器内启动即失败；当前能干净拉起的是 `mysql · redis · gateway · metadata-server`（二者不依赖 Nacos）。要打通全栈，二选一：① 在 compose 中补充 `nacos` 服务并设 `BONE_NACOS_ADDR`；② 将各模块 **dev** profile 的 nacos import 改为 `optional:nacos:...`（仅影响 dev，prod 仍强制）。integration 另依赖 RocketMQ（默认 `localhost:9876`），启用消息投递时需自行提供 `ROCKETMQ_NAME_SERVER` 或补充 `rocketmq` 服务。

### 默认入口（启动对应服务后）

| 入口 | 地址 |
|------|------|
| 管理后台 Shell | http://localhost:3000 |
| 集成微前端 | http://localhost:3006（需单独启动 `bone-integration-app`，亦可从 Shell 菜单进入） |
| API 网关（推荐） | http://localhost:8888/api/v1/... |
| IAM | http://localhost:8081 |
| 系统服务 | http://localhost:8083 |
| 集成 API（直连） | http://localhost:8085/api/v1/integration/... |
| 元数据服务 | http://localhost:9001 |
| Studio 生成器 | http://localhost:8086（模式 A 代码生成） |

> 注：IAM、系统服务以 `dev` profile 启动；网关、元数据服务、集成以默认 `local` profile 启动，端口见上表。

- **端口速查**：integration `:8085` · studio-generator `:8086` · metadata-server `:9001`（local/dev 均为 `:9001`；`:8885` 已弃用，会导致网关转发熔断），无占用即可并行启动；各模块端口真源为各自 `application.yml` 与 [本地开发与构建](doc/wiki/03-本地开发与构建.md)
- 默认账号：`admin` / `123456`（首次登录请修改；与 `bone-init.sql` 一致）
- **口令对齐**：`BONE_DB_PASSWORD` 同时注入 docker compose（初始化 MySQL root）与各后端。务必在 `docker compose up` **之前**设置，且所有终端使用同一个值；若数据卷已用旧口令初始化，需先 `docker compose down -v` 重建数据卷再启动。
- DDL 策略：改 [bone-init.sql](bone-init.sql) 后重建库，见 [数据库开发规范](doc/architecture/数据库开发规范.md)
- 仓库中**无** `bone-admin` 模块；在线演示环境以社区公告为准

---

## 文档与社区

### 文档（仓库内 · 推荐）

- [doc/README.md](doc/README.md) — 总索引  
- [BONE 总体架构](doc/architecture/BONE-总体架构设计方案.md)  
- [Bone-API-规范](doc/architecture/Bone-API-规范.md)  
- [元数据三模块对照](doc/design/modules/元数据能力-实现映射与竞品对照.md)  
- [主 PRD](doc/prd/BONE产品需求文档正式版.md)  
- [引擎索引](bone-engine/README.md)

### 参与贡献

- [CONTRIBUTING.md](CONTRIBUTING.md) — 提交规范与 CI 说明  
- Issue / PR：[Gitee 仓库](https://gitee.com/meishan315/bone)  
- 欢迎：核心能力、连接器、插件、文档与翻译

### 社区与支持

- **代码仓库**：[https://gitee.com/meishan315/bone](https://gitee.com/meishan315/bone)  
- **微信公众号**：梅山见道（MeishanInsight）  
- **交流**：添加微信 `meishan-bone` 加入技术群  

[![Star on Gitee](https://gitee.com/meishan315/bone/badge/star.svg)](https://gitee.com/meishan315/bone)

---

## 常见问题

**Q: 如何判断某项能力是否已经实现？**
A: 先看本文的“当前状态”，再以 OpenAPI、代码和测试为准；迭代范围见 [P0 看板](doc/wiki/07-P0-TODO看板.md)。

**Q: 启动失败？**  
A: 确认 JDK 21+、MySQL 已导入 `bone-init.sql`、端口无冲突；详见 [本地开发与构建](doc/wiki/03-本地开发与构建.md)。

**Q: API 路径规范？**  
A: 统一 `/api/v1/{domain}/**`，见 [Bone-API-规范](doc/architecture/Bone-API-规范.md)。

**Q: 集成服务在哪个目录？**  
A: 仅 **`bone-platform/bone-integration`**（构件 `bone-platform-integration`）；前端为 **`bone-integration-app`**。历史 `bone-engine/bone-integration` 已移除，见 [ADR-集成单模块](doc/architecture/adr/0037-integration-engine-single-module.md)。

**Q: 如何开发扩展插件？**  
A: 见 [扩展引擎使用指南](bone-engine/bone-extension-engine/docs/使用指南.md) 与 `bone-extension-sdk` 示例。

**Q: 有可参考的 DDD 工程样板吗？**  
A: [`bone-blueprint/`](bone-blueprint/) 是官方 DDD 参考实现（订单示例，`:8082`，包含 ArchUnit 门禁），新模块建议对照组织代码；另有 Go 对照实现 [`bone-engine/go-engine/`](bone-engine/go-engine/)（实验性）。

**Q: 生产部署？**  
A: 各模块 Spring Boot 可执行 JAR + 网关；环境变量见 [config/env/README.md](config/env/README.md)。完整生产指引将随 `doc/deployment/` 持续补充。

---

## License

本仓库采用 **双轨授权**：个人学习免费，企业商业使用需购买授权。

### 1. 免费使用（无需授权，亦无需付费）

自 2026-10-11 起，在 [BSL 1.1](LICENSE) 的 **Additional Use Grant** 下，以下用途可**免费**使用本软件（含生产部署）：

判定依据是「**用来干什么**」，不是「你是谁」：公司员工个人学习同样免费；
个人 / 个体工商户用于经营性活动同样需要授权。

| 场景 | 是否免费 | 限制条件 |
|------|----------|----------|
| 个人学习、自学、练习、学术研究 | 免费 | 由个人实施 |
| 经认证教育机构的教学活动 | 免费 | — |
| 技术评估、POC、功能演示、非商业验证 | 免费 | **≤ 90 天**，且不得服务 / 处理评估者以外终端用户的数据 |
| 开发、联调、测试、CI | 免费 | 环境不得承载真实业务流量或真实终端用户数据 |
| 其他任何非商业目的的使用 | 免费 | 「商业目的」＝为利润或其他对价进行的活动，主体性质不限 |

### 2. 需购买商业授权

以下情形**必须**先向 Licensor 取得**书面**商业授权：

- 企业、组织、个体工商户或个人，将本软件用于**生产环境**支撑经营性活动（含内部业务系统底座）；
- 对外提供产品 / 服务：SaaS、外包交付、二次销售、OEM 集成、以本软件为基础分发闭源产品；
- 需要移除 BSL 限制，或需要赔偿条款（indemnification）、SLA 与技术支持保障。

商业授权的范围（按主体 / 按实例 / 按席位 / 有效期）不在 LICENSE 中约定，以双方签署的书面协议为准。

商业授权咨询与购买请联系 Licensor（**梅山 / Meishan**）。

> ⚠️ **待补（Licensor 自行完成）**：正式商业化前请在此处填入真实的联系邮箱 / 报价页——
> 「企业要购买授权」只在对方找得到你时才成立。另：LICENSE 以花名 `梅山 (Meishan)` 署名，
> 但**商业授权合同与发票须以法定姓名签署**；日后成立公司，建议将 Licensor 改为公司全称。

### 3. 许可文件与生效边界

| 文件 | 适用对象 |
|------|----------|
| [LICENSE](LICENSE) | 2026-10-11 及之后发布的版本 —— **BSL 1.1**，Change License 为 **Mozilla Public License 2.0**，Change Date 为 **2030-10-11**（届时自动转为 MPL 2.0，回归 OSI 开源） |
| [LICENSE-MIT](LICENSE-MIT) | 2026-10-11 **之前**已公开发布的版本及其历史快照 —— **MIT**（不可撤销，原授权持续有效） |
| [NOTICE](NOTICE) | 品牌与著作权主体的关系、商标使用边界、第三方依赖与 AI 辅助产出的权利归属 |

> BSL 1.1 不是 OSI 定义的开源许可，属「源代码可得（Source Available）」。因此本仓库在宣传口径上不再自称「开源软件」，
> 而表述为「**源代码开放：个人学习免费，商业使用需授权**」。BSL 保证每个版本在 Change Date 后自动回归 MPL 2.0 开源许可。
>
> **为什么 Change License 选 MPL 2.0**：BSL 的 Covenant 要求 Change License 必须是 GPLv2 或其兼容许可；
> 而本项目重度依赖 Apache-2.0（Spring Boot 生态），Apache-2.0 与 **GPLv2 不兼容**，只与 GPLv3 兼容。
> MPL 2.0 与 Apache-2.0、GPLv2+ **均可共存**（文件级 copyleft，HashiCorp BUSL 同选），是唯一既满足 Covenant、
> 又不与本项目依赖冲突的选择。
>
> 中文释义仅为便于理解，**条款以 LICENSE 英文正文为准**。
> 完整中文说明（FAQ、历史版本边界、Licensor 待办清单）见 [LICENSE.zh-CN.md](LICENSE.zh-CN.md)。
>
> ⚠️ **建议给授权切换点打 tag**（如 `v1.0.0-mit`）固化「哪些版本是 MIT」的边界，便于日后举证。
> 打 tag 属 L4 操作，AI 不得执行，需你本人操作。
