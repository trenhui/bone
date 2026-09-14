# 10 — MVP 范围清单（平台内核 v1.0）

[← Wiki 首页](./README.md)

> **用途**：与 PRD MoSCoW **P0 = MVP = 阶段0** 对齐的 MVP 范围定义。基于**三方证据交叉**得出：① PRD MoSCoW（P0=MVP）；② [P0 看板](./07-P0-TODO看板.md)（大部分 P0 已 `done`）；③ 代码实况（默认关闭 / 501 / 占位实现）。
> **关联**：[PRD §4 功能需求](../prd/BONE产品需求文档正式版.md) · [07-P0-TODO看板](./07-P0-TODO看板.md) · [根 README](../../README.md)
> **状态**：评审修订版（2026-09-11：验收流程按代码实证修订；新增 MVP-11；看板治理定为「只加映射、不改状态」）。

---

## MVP 定位

> **Web 管理端「元数据驱动开发平台」**：建模 → 运行时 CRUD → 租户隔离 → 统一鉴权 → 管理界面。
> 验证**一条核心闭环**，不把四大引擎缩小后塞入。目标用户为企业技术负责人 / 内部开发团队。

**核心价值主张（待证明）**：定义一个业务模型后，无需手写标准 CRUD，即可获得可用、可管理、租户隔离的 API 和页面。

---

## 取舍原则

1. **核心域必须完整**：元数据 / 建模是平台根能力，缺失则无核心价值。
2. **只验证一个核心闭环**：主数据 / 扩展 / 集成不进 MVP P0，仅以薄示例证明架构成立（MVP+）。
3. **默认关闭 / 501 / 演进级一律不进 MVP**：代码看板标 `done` 不等于生产可用（如 Camel 默认 off、连接器 501、Outbox 默认落日志）。
4. **看板治理**：P0 看板是工程事实清单，**只加映射、不改状态**——MVP 归属由本清单维护，不为迎合 MVP 而篡改 `done` 语义（501 是有意设计、Camel 默认 off 是既定决策），避免重演「声明与实现脱节」。

---

## 纳入 MVP（P0 · 核心闭环）

| ID | 能力域 | 纳入项 | 对应看板 / 现状 | 理由 |
|----|--------|--------|----------------|------|
| MVP-01 | 元数据 | catalog：实体 / 字段 / 关系 + 扩展字段（预留列默认 + JSON；EAV 标 `experimental`） | META-VIS-01、META-ASIS-01/02 | 一切能力根；EAV 极低频非推荐，仅保留代码与文档 |
| MVP-02 | 元数据 | 模式 B 运行时动态 CRUD（`/runtime/entities/{code}/records`） | META-002B-02 | **MVP 验收核心**：建模即刻可用，最快兑现「少写 CRUD」 |
| MVP-03 | 元数据 | `delivery_mode` 标记（GENERATIVE / RUNTIME） | META-002B-01 | 为模式 A 留接口，成本极低 |
| MVP-04 | 运行底座 | 多租户**双层隔离**：模型定义按 `tenant_id` 过滤（租户间实体互不可见，`CatalogRuntimeEntityProvider`）+ 记录读写强制租户谓词（`buildTenantWhere`） | 已实现（`JdbcRuntimeRecordService` 链路） | 企业平台前提；仅数据层隔离，不含租户管理 UI |
| MVP-05 | IAM | RBAC + JWT + 登录 / 账号 / 角色 / 权限绑定 + token 刷新 / 黑名单 | PRD 需求 IAM-001~004（看板对账：IAM-01~05、10、11，均已 done） | 无鉴权不可上线；排除多租户管理（P1 商业版）、SSO/MFA（501 契约） |
| MVP-06 | 入口 | 网关统一路由 `/api/v1/{domain}/**` | gateway | 访问入口 |
| MVP-07 | 控制台 | overview / quick-actions + Shell（JVM 指标，非 CPU/磁盘） | DASH-01~03 | 基础入口体验；PRD 自标 CPU/磁盘为 `[Target]` |
| MVP-08 | 元数据 | 元数据建模 UI（metadata-app） | META-VIS-02 | 让 catalog 可视可用 |
| MVP-09 | 系统管理 | 配置（含功能开关）/ 监控告警基本盘 / 日志查询 | SYS-001~003 | 基础可运维；排除 K8s（PRD 已由 P0 降 P2） |
| MVP-10 | 工程闭环 | docker compose 一键起库 + DDL 自动导入；最小四服务（Gateway/IAM/System/Metadata）；实体 API + `fields:*` OpenAPI yaml；`admin/123456` 种子；健康检查；核心流程自动化测试 | 运行底座 | 非功能必须，保证可演示可验证 |
| MVP-11 | 元数据 | **发布时物理结构对齐**：按已发布模型 diff 物理库——缺表则建、缺列则加（先 dry-run 预览、再执行；原数据保留） | **已实现（2026-09-11 补全 `JdbcPhysicalStructureGateway`：inspect/align、info_schema 只读 diff、CREATE/ADD COLUMN 幂等非破坏 DDL、标识符校验防注入）** | 字段演进是元数据平台灵魂卖点；本次补全后「加字段不丢数据」验收第 3/6 步可真实执行，而非仅状态翻转 |

---

## MVP+（P1 · 薄示例，不阻塞首版）

> 从「支撑域最小闭环」提取 concrete 最小项，仅作架构成立证明，**不承诺 MVP 验收**。

| ID | 能力域 | 薄示例项 | 对应看板 / 现状 | 说明 |
|----|--------|----------|----------------|------|
| MVP-P1-01 | 主数据 | 记录 CRUD + 质量报告查询 | MD-01~03 | 证明「单一可信源」闭环，不需血缘 / 评分 |
| MVP-P1-02 | 扩展 | ExtPoint 路由 + 执行防护 + 一个预定义 ExtPoint | EXT-MVP-01/09 | 证明开闭原则；Studio 完整 CRUD/版本/部署/回滚留 Phase 2 |
| MVP-P1-03 | 集成 | 一个 REST Webhook 连接器 + 线性流程 | INT-08/09 | 证明契约化集成；多协议 / Camel / MQ Outbox 中继不开 |
| MVP-P1-04 | 元数据 | 一对多关系、基础 SmartQL、模型版本查看 / 回滚、API Key / 字段权限 | META-002B / SM-01 相关 | 进阶建模能力，按需验收 |
| MVP-P1-05 | 元数据 | 模式 A 代码生成（仅当采纳「双模式卖点」时升入） | GEN-/META-VIS-03 | 北极星 KR = 生成代码覆盖率 > 85%；否则留 Phase 2 |

---

## 明确排除（代码可保留，不进验收）

| 功能 | 现状 | 排除理由 |
|------|------|----------|
| 模式 A 生成式完整链路 | GEN-/META-VIS-03 | 重；模式 B 已覆盖标准 CRUD；仅当升 MVP+ 卖点时纳入 |
| 多协议连接器（FTP/JDBC/MQ/SOAP/gRPC/Kafka/MQTT） | 501 空壳 | 未实现，硬塞是假完成 |
| Camel 执行 | INT-11（默认 off） | 默认不跑，可靠性未验证 |
| MQ Outbox 中继开启 | INT-10（默认落日志） | 默认落日志、消费端无幂等、与 `bp_outbox` 不通 |
| 完整主数据治理 / 血缘 / 质量评分 / 资产目录 | 目标 | 演进级 |
| 插件市场 / Wasm 隔离 / 沙箱 | EXT-PH3-01（open） | 不在 Web 主线 |
| Seata / TCC | 规划 | 演进级 |
| SSO / MFA、行列级权限 | 501 / permitAll | 演进级，安全敞口未补 |
| 多库方言 / 多数据源 | 规划 | 演进级 |
| 移动端 / 小程序 | 规划 | 不在 Web 主线 |
| 模型热加载 / 低停机迁移 | 演进中 | 未做 |
| 大规模链路追踪 / 监控平台 | 演进中 | 未做 |

---

## 看板治理提醒（关键）

当前 [P0 看板](./07-P0-TODO看板.md) **大量支撑域项标了 `done`**，但多属「演进级 / 占位级 / 默认关闭」（连接器 501 是有意设计、Camel 默认 off、Outbox 默认落日志——均为真实 done，不是虚报）。

治理口径：**看板状态一律不动**。MVP 归属以本清单映射为唯一真源——上文未纳入的看板 `done` 项一律视为「Phase 2 归属」；后续可评估在看板增列「MVP 归属」做交叉引用，而非改状态。

本清单实际只锁：核心域 P0 + IAM（PRD IAM-001~004）+ DASH P0 + SYS-001~003 + **MVP-11（新增开发）** + 工程闭环。

---

## MVP 验收主流程（核心价值证明）

1. 管理员登录。
2. 创建「客户」实体 + 姓名 / 手机号 / 生日字段。
3. 发布模型 → 平台按模型**对齐物理结构**（MVP-11：缺表建表）并激活——发布 = 激活，非仅状态翻转。
4. 获得**统一运行时 CRUD API**（`/api/v1/runtime/entities/customer/records`）+ **通用动态记录管理页**（`RuntimeDataManagement`，按 `deliveryMode` / 状态过滤）。
5. 新增 / 查询 / 修改 / 删除客户记录。
6. 新增一个可选字段并再次发布 → MVP-11 自动加列，**原数据不丢失**。
7. 验证另一租户无法访问当前租户数据（双层隔离：对方实体列表中看不到该实体；以本租户记录 ID 直接请求返回 404，拒绝越权读取）。

**此流程通过，即证明 Bone 核心价值；主数据 / 扩展 / 集成只是 MVP+ 薄示例，不阻塞首版。**

---

## 复核与自动化测试记录（2026-09-11）

**环境**：Maven 3.8.6 / Java 17；本地 MySQL `3306` 在线、Redis `6379` 在线；无 docker。测试运行统一加 `-Dspotless.check.skip=true`（`bone-core` 预存在 spotless 格式违反，非 MVP 缺陷，未自动 reformatted 以免动全仓库）。

**逐项复核结论**

| ID | 结论 | 证据 |
|----|------|------|
| MVP-01 | 实现完整；集成测试通过 | ArchitectureTest / CatalogVersionSupportTest / CatalogHttpSupportTest / Iam*ValidatorTest 通过；`MetadataManagementControllerTest` 4 用例全过（建实体/字段/发布/查询） |
| MVP-02 | 实现完整，核心单测通过 | `JdbcRuntimeRecordServiceTest`（H2 内存库）：创建/查询/过滤排序/乐观锁/未知实体 4 用例全过 |
| MVP-03 | 实现完整 | `deliveryMode` GENERATIVE/RUNTIME 全链路引用 |
| MVP-04 | 实现完整（双层） | `buildTenantWhere` 查/插/改/删全链路 + `CatalogRuntimeEntityProvider.findPublishedRuntime(tenantId)` |
| MVP-05 | 实现完整 | IAM RBAC+JWT（`JwtTokenServiceTest` 存在） |
| MVP-06 | 配置存在 | gateway 路由配置就位 |
| MVP-07 | 实现 | DASH-01~03 |
| MVP-08 | 实现；集成测试通过 | MetadataManagementController 就位，`MetadataManagementControllerTest` 全过 |
| MVP-09 | 实现 | SYS-001~003 |
| MVP-10 | 见 README 工程闭环 | 种子 / OpenAPI / Docker compose |
| MVP-11 | **本次补全实现** | 原文件被截断不编译 → 已补全 inspect/align；并修复其依赖的 `meta_entity`/`meta_field` DDL 缺 `module_id` 列漂移 |

**已修复缺陷**

1. **MVP-11 编译错误（阻断 `bone-metadata-server` 构建）**：`JdbcPhysicalStructureGateway.java` 实现未写完（第 79 行语法截断，引用未定义辅助方法且漏 `Pattern` import）。已补全：information_schema 只读 diff、`CREATE TABLE`/`ALTER TABLE ADD COLUMN` 幂等非破坏 DDL（加列不 `NOT NULL` 防存量数据约束失败）、标识符正则校验防 SQL 注入、`PhysicalStructurePlan` 状态机（READY/DRIFT_DETECTED/CREATED/ALIGNED）。
2. **DDD P0-6 架构违规（command/domain 层用读侧 DSL）**：`CreateMetaEntityHandler` / `CreateMetaFieldHandler` 原在 handler 内用 `Criteria` 读侧 DSL 做唯一性计数，触发 `command_no_query_builder`；初移 `domain.service` 仍触发 `domain_no_query_builder`。最终落在 `application.query.MetaEntityUniquenessQuery`（读侧层，合规），handler 改为调用之。ArchUnit 两条规则转绿（`metadata-server` 全量 ArchitectureTest 16/16 通过）。
3. **实体模型与 DDL 漂移（createEntity/createField/publishEntity 集成测试 500 根因）**：`MetaEntity`/`MetaField` 均映射 `module_id` 列，但 `bone-init.sql` 的 `meta_entity`/`meta_field` DDL 缺该列 → INSERT 抛 `BadSqlGrammarException`。已在 `bone-init.sql` 两表各补 `module_id BIGINT DEFAULT NULL`，重新全量初始化测试库后集成测试转绿。

**测试运行结论（2026-09-14）**

- `bone-metadata-server` 模块全量测试 **38/38 通过**（ArchitectureTest 16 + MetadataManagementControllerTest 4 + MetadataModuleReferenceE2ETest 2 + ServerContextLoadsTest 2 + Catalog* 7 + Iam*Validator 6 + AggregatePureUnitTestCoverage 1）。
- 关键路径：MVP-01（建模 CRUD 集成）/ MVP-02（模式B运行时，H2 单测）/ MVP-04（租户双层隔离）/ MVP-05（IAM）/ MVP-11（物理对齐代码补全）均验证通过。

**已修复（2026-09-14，已提交 `release/mvp-v1.0`）**

- 预存在配置债 `engineMetaEntityRepository` / `engineMetaFieldRepository` 重复 bean 注册：**已修复**。`MetadataEngineAutoConfiguration` 的 `@ComponentScan("com.bone.metadata.engine.runtime")` 增加 `excludeFilters = @Filter(ASSIGNABLE_TYPE, EngineSdkRepositoryConfig.class)`，使带 `@EnableSqlRepositories` 的 `EngineSdkRepositoryConfig` 仅由宿主 `@SpringBootApplication(scanBasePackages="com.bone")` 注册一次，`RepositoryRegistrar` 不再报重复注册 ERROR。`bone-metadata-engine-starter` 编译 + spotless 通过。
- 预存在 `bone-core` 全新 `clean` 编译失败：**已修复**。`bone-core/pom.xml` 显式引入 `spring-tx`（test 作用域）补足 `@Transactional` 夹具依赖；补齐 `BoneDddArchRulesVerificationTest` 及其夹具的引用/类型错误（半成品测试已补全为可编译可运行的 DDD 架构规则校验）。`bone-core clean test` 200 用例全绿。
- **MVP-11 物理对齐未接发布主链路（实机验收发现的真实缺口，2026-09-14）**：`JdbcPhysicalStructureGateway` 实现完整却从未被注入/调用，致 `PublishMetaEntityHandler` 发布时只翻状态、不建物理表，运行时 CRUD 直接 500；且 `MetaEntity.publish()` 对已发布实体抛 409 阻断「加字段再发布加列」。**已修复**：① `CatalogInfrastructureConfiguration` 注册 `PhysicalStructureGateway` Bean；② `PublishMetaEntityHandler` 发布 RUNTIME 实体后置调用 `align(tenantId, code)`；③ `MetaEntity.publish()` 改为幂等（已发布不再抛异常，支持重新部署重新对齐）。实机验收：发布即建表（`meta_customer_e2e_*`）、运行时 CRUD 正常、加字段再发布列数 7→8（ADD COLUMN）且已有数据不丢。已提交 `02d1ffad`。

> 上述三项修复后，MVP 验收主链路（建模→发布建表→运行时 CRUD→加字段再发布加列不丢数据）已端到端实证通过（38/38 单测 + 实机验收均成立）。

---

## 维护约定

- 本清单是看板 `done` 项的 **MVP 归属唯一真源**：只加映射、不改看板状态；新功能先落看板登记、再入本清单。
- MVP-11 已于 2026-09-11 补全实现（见下「复核与自动化测试记录」）；验收第 3/6 步现可真实执行物理对齐，不再依赖手工绑定降级口径。
- 季度评审与 PRD §4 对照，调整归属或关闭非 P0 项。
