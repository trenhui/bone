# Bone — Build Once, Natively Everywhere

**企业级全栈开源原生快速开发平台**

![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![License](https://img.shields.io/badge/license-MIT-yellow.svg)
![JDK](https://img.shields.io/badge/JDK-17+-orange.svg)

> **100% 开源免费 · 元数据驱动 · 四大引擎协同**  
> **「构建可复用的系统，创造可持续的价值。」** —— 梅山

---

## 阅读指引

| 你想了解… | 建议阅读 |
|-----------|----------|
| **产品理念、四大引擎与未来能力** | 本文（愿景与路线图） |
| **工程实现、API、DDL、本地调试** | [doc/README.md](doc/README.md) → [架构规范](doc/architecture/README.md) · [本地构建](doc/wiki/03-本地开发与构建.md) |
| **AI 助手 / 贡献者门禁** | [AGENTS.md](AGENTS.md) · [CONTRIBUTING.md](CONTRIBUTING.md) |

**说明**：本文侧重 **理念正确性与演进方向**；具体接口、表结构、端口以仓库内 `doc/` 与 OpenAPI 为准，并随版本持续落地。

---

## 目录

- [项目概述](#项目概述)
- [设计原则](#设计原则)
- [核心价值](#核心价值)
- [四大核心引擎](#四大核心引擎)
- [协同架构与代码生成](#协同架构与代码生成)
- [技术路线](#技术路线)
- [演进路线](#演进路线)
- [快速体验](#快速体验)
- [目标效能（规划方向）](#目标效能规划方向)
- [文档与社区](#文档与社区)
- [常见问题](#常见问题)

---

## 项目概述

Bone 以 **「Build Once, Natively Everywhere」** 为长期愿景：用**统一元数据**描述业务，用**标准化引擎**承载主数据、扩展与集成，让企业应用从「项目制重复建设」走向「平台化持续演进」。

当前仓库是这一愿景的 **开源参考实现与持续迭代载体**——部分能力已可运行，更多能力在 [演进路线](#演进路线) 中按阶段交付。

---

## 设计原则

以下原则指导架构与产品决策，**不因某一版本实现进度而削弱**：

1. **元数据先行（Metadata-First）**  
   业务实体、字段、关系、规则应尽量可配置、可版本化，减少硬编码 CRUD 与散落 DDL。

2. **开闭原则（ExtPoint）**  
   核心流程稳定；行业差异、客户定制通过扩展点与插件注入，避免 fork 主干。

3. **单一可信数据源（Master Data）**  
   客户、组织、物料等核心对象集中治理，对外以 API/SDK 消费，消除多系统副本不一致。

4. **契约化集成（Integration）**  
   异构系统通过连接器与可编排流程对接；协议适配、幂等、重试、可观测性为一等公民。

5. **契约化 API（API-First）**  
   对外统一 `/api/v1/{domain}/**`；错误码、日志、多租户见 [Bone-API-规范](doc/architecture/Bone-API-规范.md)。

6. **工程诚实（Implement in Public）**  
   愿景写在 README/PRD；落地状态见 [P0 看板](doc/wiki/07-P0-TODO看板.md) 与各模块详设中的 As-Is 标注。

---

## 核心价值

### 开发效率

- **元数据驱动**：一次建模，驱动数据结构与访问行为；减少重复 CRUD 与样板工程。
- **双模式交付**（智能元数据引擎核心主张，见下节）：
  - **模式 A · 生成式**：`sdk` + `server` + [studio-generator](bone-engine/studio-generator/) → 可编译源码进 Git（对标 OutSystems / JHipster）。
  - **模式 B · 运行时**：`sdk` + [bone-metadata-engine](bone-engine/bone-metadata-engine/) → 按元数据直接提供动态数据访问，**标准场景可不再生成业务 CRUD 代码**（对标 Salesforce 声明式运行时 / Mendix 默认解释执行）。
- **多端统一（愿景）**：一次建模，适配 Web 管理端、移动端 App、小程序——**Web 微前端（Qiankun）为当前主线**，移动端为规划路线。

### 企业级能力

- **RBAC + 多租户**：行列级权限、租户隔离、SSO（按阶段交付）。
- **分布式一致性**：跨服务场景采用 Seata（AT/TCC）等方案（平台化集成中）。
- **可观测性**：链路追踪、指标、审计日志（SkyWalking / Micrometer 等为技术选型方向）。

---

## 四大核心引擎

Bone 以 **「数据 → 质量 → 功能 → 生态」** 组织四大引擎。下列能力为 **产品目标全集**；实现节奏见各引擎仓库与 [元数据能力对照](doc/design/modules/元数据能力-实现映射与竞品对照.md)。

### 智能元数据引擎（Smart Metadata Engine）

**定位**：平台的「数字大脑」——用 **统一元模型（catalog + EAV）** 描述业务，并支持 **两种等价交付哲学**（可并存、按场景选型）：

| 模式 | 一句话 | 主要模块 | 业界参照 |
|------|--------|----------|----------|
| **A · 生成式交付** | 元数据 → **生成** 可审计源码 → 编译部署 | `bone-metadata-sdk` + `bone-metadata-server` + **studio-generator** | OutSystems、JHipster、Salesforce DX |
| **B · 运行时元数据面** | 元数据 → **解释执行** 动态 CRUD/API，无需为每个实体生成 Controller | `bone-metadata-sdk` + **bone-metadata-engine**（+ server 作控制面） | Salesforce 标准对象运行时、Mendix 默认运行时、Directus / Hasura 元数据 API |

```text
                    ┌── 模式 A：生成式（当前主线）
  meta_* 模型 ──────┤     server(catalog) → generator → Java/TS 工程 → sdk 仓储绑定物理表
                    │
                    └── 模式 B：运行时（战略方向）
                          server(catalog) → engine → 动态 REST / SmartQL → sdk 直接读写库
```

**模式 A（生成式）** — 适合：信创/审计要求 **源码入库**、复杂领域逻辑在 Java 中手写、ArchUnit 分层门禁、与 ExtPoint 深度织入。

**模式 B（运行时）** — 适合：运营后台、配置型实体、多租户 SaaS 标准对象；**引擎成熟后，标准 CRUD 不再依赖 studio-generator**；生成器收窄为「逃逸舱」（DDD 骨架、集成桩、一次性迁移）。

**共享能力（两模式共用）**：

| 能力方向 | 说明 |
|----------|------|
| 平台数据面 | **bone-metadata-sdk**：仓储、EAV 扩展字段、多数据源方言（模式 B 的 JDBC 执行底座） |
| 建模控制面 | **bone-metadata-server**：catalog REST + 扩展字段 API（`:9001`） |
| 规则与动态访问 | **bone-metadata-engine**：表达式/校验/SmartQL/动态 CRUD（模式 B 核心，逐步替代「为每张表生成代码」） |
| 权限与多租户 | 元数据绑定行级租户、列级规则（两模式统一策略） |
| 运行时演进 | 模型发布、热加载、低停机变更（引擎 + 发布流程分阶段实现） |

**工程锚点**：`bone-metadata-sdk` · `bone-metadata-server` · `bone-metadata-engine` · `studio-generator`（模式 A）· `bone-metadata-app` · `bone-generator-app`

> 能力对照与竞品：[元数据能力-实现映射与竞品对照](doc/design/modules/元数据能力-实现映射与竞品对照.md) §1.3、§3.3

---

### 企业主数据平台（Master Data Platform）

**定位**：核心主数据的 **唯一可信源（Single Source of Truth）**。

| 能力方向 | 说明 |
|----------|------|
| 全品类治理 | 客户、供应商、物料、组织、产品等标准编码与字典 |
| 数据血缘 | 来源、转换、消费全链路可视化与追溯 |
| 质量管控 | 格式/唯一性/关联性校验，清洗与质量评分 |
| 服务化交付 | 标准 REST/SDK，避免各系统冗余落库 |
| 数据资产目录 | 资产全景、归属、用途与合规标注 |

**工程锚点**：`bone-platform/bone-masterdata` · `bone-masterdata-app`

---

### ExtPoint 扩展引擎（Extension Engine）

**定位**：在 **不修改核心代码** 的前提下生长业务能力。

| 能力方向 | 说明 |
|----------|------|
| 标准化扩展点 | 前置/后置/环绕；订单、审批、支付等关键钩子 |
| 插件生命周期 | 注册、安装、升级、卸载与热部署 |
| 依赖与隔离 | 版本治理、类加载沙箱 |
| 扩展可观测 | 调用量、耗时、异常与链路 |
| 动态参数 | 运行时调整规则阈值与流程节点 |

**典型场景**：促销叠加、政务会签、行业 BOM 等。

**工程锚点**：`bone-extension-sdk` · `bone-extension-studio` · `bone-extension-app`

---

### 集成引擎（Integration Engine）

**定位**：打通 ERP / CRM / OA / 协作与消息系统，构建企业数字生态链。

| 能力方向 | 说明 |
|----------|------|
| 多协议 | REST、gRPC、SOAP、Kafka、MQTT、JDBC 等 |
| 连接器工厂 | SAP、Salesforce、钉钉、企微、用友等标准化连接器 |
| 可视化映射 | 字段映射、格式转换、清洗与 ETL |
| 流程编排 | 分支、并行、循环与长事务集成链路 |
| 可靠性 | TCC/重试/幂等/熔断/死信 |

**工程锚点**：`bone-engine/bone-integration` · `bone-platform/bone-integration` · `bone-integration-app`

---

## 协同架构

四大引擎在逻辑上形成闭环；智能元数据引擎在 **模式 A（生成）** 与 **模式 B（运行时）** 之间提供两条「从模型到可运行系统」的路径：

```text
智能元数据引擎 ──► catalog / EAV 元模型（「是什么」）
        │
        ├─[模式 A]─► studio-generator ──► 源码工程（Git 可审计）
        │
        └─[模式 B]─► metadata-engine ──► 动态 API / SmartQL（免生成标准 CRUD）
        │
        ▼
企业主数据平台 ──► 核心数据一致与质量（「准、全、可信」）
        │
        ▼
ExtPoint 扩展引擎 ──► 个性化逻辑插件化（「怎么差异化」）
        │
        ▼
集成引擎 ──► 连接外部系统（「和谁交互」）
        │
        ▼
企业级数字化平台（Web 控制台 · 未来多端）
```

- **数据层**：元数据定结构，**sdk** 统一持久化；主数据保质量。  
- **交付层**：简单域走 **模式 B**；强定制/合规域走 **模式 A**；可混合（同一企业不同系统）。  
- **功能层**：扩展引擎承载变化，减少主干重构。  
- **生态层**：集成引擎消除孤岛。

---

## 技术路线

采用 **「主线已选型 + 平台化演进」** 表述，避免将规划中的组件写成已全部落地。

### 后端（主线 · 持续强化）

| 领域 | 技术方向 |
|------|----------|
| 应用框架 | Java 17 · Spring Boot 3.2 · DDD + CQRS 分层 |
| 数据访问 | **Bone Metadata SDK**（动态仓储/EAV）+ 按需 JPA/MyBatis |
| 缓存 | Redis · Redisson · Caffeine |
| 安全 | Spring Security · JWT · 多租户上下文 |
| API | REST · OpenAPI · 统一 `/api/v1/{domain}` |
| 网关 | Spring Cloud Gateway（`bone-gateway`） |

### 后端（平台化 · 规划集成）

| 领域 | 技术方向 |
|------|----------|
| 服务治理 | Nacos（注册/配置）· Sentinel（流控/熔断） |
| 消息 | RocketMQ（事务消息、异步解耦） |
| 分布式事务 | Seata（AT/TCC） |
| 可观测 | SkyWalking · Micrometer · Prometheus |

### 前端（主线 · 持续强化）

| 领域 | 技术方向 |
|------|----------|
| 管理端 | React 18 · TypeScript · Vite 5 · Ant Design 5 |
| 微前端 | Qiankun · `bone-shell` + 领域微应用 |
| 质量 | ESLint · Prettier · Vitest |

### 前端（多端 · 规划）

| 领域 | 技术方向 |
|------|----------|
| 移动端 | React Native · Expo（与元数据模型共享） |
| 小程序 | 基于统一 API 的轻量端（规划） |

---

## 演进路线

| 阶段 | 定位 | 重点 |
|------|------|------|
| **现在** | Build Once, Natively Everywhere | 元数据 **模式 A**（sdk + server + generator）+ 四大引擎骨架；catalog/EAV、IAM、主数据、扩展、集成可运行 |
| **1–2 年** | Business Oriented Native Engine | **模式 B** 落地：engine 驱动标准动态 CRUD，生成器收窄；连接器、血缘、热更新、行业模板 |
| **2–3 年** | 行业方案规模化 | 政务/制造/零售等套件；多端生成与运行时；生态插件市场 |
| **长期** | Base of Next Enterprise | 企业级开发事实标准；开放联盟与认证体系 |

交付节奏与工程债：[doc/wiki/07-P0-TODO看板.md](doc/wiki/07-P0-TODO看板.md)。

---

## 快速体验

面向贡献者与评估者的 **最小路径**（完整端口见 [本地开发与构建](doc/wiki/03-本地开发与构建.md)）。

### 环境要求

JDK 17+ · Maven 3.8+ · Node.js 18+ · MySQL 8+ · Git（Redis 按模块需要）

### 环境变量（可选）

`BONE_DB_URL` · `BONE_DB_USERNAME` · `BONE_DB_PASSWORD` · `BONE_SERVER_PORT` · `BONE_GATEWAY_PORT`

### 步骤

```bash
git clone https://gitee.com/meishan315/bone.git
cd bone

# 数据库（DDL 真源：bone-init.sql）
mysql -u root -p < bone-init.sql

# 后端
mvn clean install -DskipTests

# IAM（示例）
cd bone-platform/bone-iam && mvn spring-boot:run

# 前端 Shell（新终端）
cd bone-frontend && npm ci && npm run dev
```

可选：`docker compose up -d` 启动 MySQL/Redis（见根目录 `docker-compose.yml`）。

### 默认入口（本地）

| 入口 | 地址 |
|------|------|
| 管理后台 Shell | http://localhost:3000 |
| API 网关（推荐） | http://localhost:8888/api/v1/... |
| IAM | http://localhost:8081 |
| 元数据服务 | http://localhost:9001 |

- 默认账号：`admin` / `123456`（首次登录请修改；与 `bone-init.sql` 一致）
- DDL 策略：改 [bone-init.sql](bone-init.sql) 后重建库，见 [数据库开发规范](doc/architecture/数据库开发规范.md)
- 仓库中**无** `bone-admin` 模块；在线演示环境以社区公告为准

---

## 目标效能（规划方向）

以下为 **产品设计目标**，用于对齐建设优先级，非对外 SLA 承诺；落地后将以客户案例与基准测试补充数据。

| 维度 | 传统模式痛点 | Bone 目标 |
|------|----------------|-----------|
| 新功能交付 | 2–3 周编码+联调 | 元数据配置 + 生成，**天级** 可演示 |
| 多端 | 多套代码分别维护 | **一次建模**，多端生成与同步 |
| 集成 | 点对点定制、成本高 | 连接器 + 可视化编排，显著降低人天 |
| 数据治理 | 分散核对、质量不可控 | 统一主数据与质量规则 |
| 架构演进 | 大版本重构、停服风险 | 元数据驱动渐进式演进 |

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

## 平台愿景

Bone 致力于成为企业数字化转型的 **核心基础设施**：以四大引擎协同，覆盖从数据治理、业务构建到系统集成与扩展的全链路，帮助企业获得 **稳定、可演进、可生态化** 的架构底座。

> **「好的架构，让复杂归于简单；好的开源，让价值自由流动。」** —— 梅山

*元数据驱动 · 一次构建 · 原生体验*

---

## 常见问题

**Q: README 写的功能和代码不一致？**  
A: 本文描述 **目标架构与路线图**；当前迭代范围见 [P0 看板](doc/wiki/07-P0-TODO看板.md) 与各模块详设。欢迎通过 Issue 参与共建。

**Q: 启动失败？**  
A: 确认 JDK 17+、MySQL 已导入 `bone-init.sql`、端口无冲突；详见 [本地开发与构建](doc/wiki/03-本地开发与构建.md)。

**Q: API 路径规范？**  
A: 统一 `/api/v1/{domain}/**`，见 [Bone-API-规范](doc/architecture/Bone-API-规范.md)。

**Q: 如何开发扩展插件？**  
A: 见 [扩展引擎使用指南](bone-engine/bone-extension-engine/docs/使用指南.md) 与 `bone-extension-sdk` 示例。

**Q: 生产部署？**  
A: 各模块 Spring Boot 可执行 JAR + 网关；环境变量见 [config/env/README.md](config/env/README.md)。完整生产指引将随 `doc/deployment/` 持续补充。
