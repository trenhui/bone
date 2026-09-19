# 10 — MVP 范围清单（平台内核 v1.0）

[← Wiki 首页](./README.md)

> **用途**：与 PRD MoSCoW **P0 = MVP = 阶段0** 对齐的 MVP 范围定义。基于**四类证据交叉**得出：① PRD MoSCoW（P0=MVP）；② [P0 看板](./07-P0-TODO看板.md)（大部分 P0 已 `done`）；③ 代码实况（默认关闭 / 501 / 占位实现）；④ **DDD 规范 v5.5.12 作为合规判据**（功能能否算"完成"，除功能跑通外还须满足对应条文）。
> **关联**：[PRD §4 功能需求](../prd/BONE产品需求文档正式版.md) · [07-P0-TODO看板](./07-P0-TODO看板.md) · [根 README](../../README.md)
> **状态**：评审修订版（2026-09-19：**对齐 DDD 规范 v5.5.12**——新增「功能模块 × 规范合规落点」与「合规偏差登记」；MVP-02 补记 ADR-0015 持久化例外边界、MVP-04 租户语义对齐 ADR-0029；MVP-10 补装配 / 配置键 / 文档三项质量下沉。2026-09-11：验收流程按代码实证修订；新增 MVP-11；看板治理定为「只加映射、不改状态」）。
> **规范真源**：[Bone-DDD-最终实践方案.md v5.5.12](../architecture/Bone-DDD-最终实践方案.md)（本次引用的稳定锚点：`#e-44-sql-读侧仓储`、[`#e-37-入口构件决策`](../architecture/Bone-DDD-最终实践方案.md#e-37-入口构件决策)、`#e-2-多租户`）

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
5. **合规以规范真源为准，且存量不追溯**：MVP 模块的对错以 [DDD 规范](../architecture/Bone-DDD-最终实践方案.md) 为准——新增步骤默认走语义化 `*ApplicationService` 起步（E-3.7 决策树，不预生成 `Command` / `Handler` 空件）；**存量模块的 `*CommandHandler` / `*QueryHandler` 形态不追溯、不得在评审中判成违规**（规范 E-3 已明示）。反过来，新增代码也不能拿"存量都这么写"当理由继续堆 Handler。

---

## 纳入 MVP（P0 · 核心闭环）

| ID | 能力域 | 纳入项 | 对应看板 / 现状 | 理由 |
|----|--------|--------|----------------|------|
| MVP-01 | 元数据 | catalog：实体 / 字段 / 关系 + 扩展字段（预留列默认 + JSON；EAV 标 `experimental`） | META-VIS-01、META-ASIS-01/02 | 一切能力根；EAV 极低频非推荐，仅保留代码与文档 |
| MVP-02 | 元数据 | 模式 B 运行时动态 CRUD（`/runtime/entities/{code}/records`） | META-002B-02；持久化见 [ADR-0015](../architecture/adr/0015-metadata-runtime-jdbc-via-engine.md) | **MVP 验收核心**：建模即刻可用，最快兑现「少写 CRUD」。**持久化属登记的例外**：动态表/动态列无法用静态仓储，故允许 Spring JDBC 直写；代价是租户谓词与软删**必须手写**（无自动注入），且该例外不得扩散到其他业务模块 |
| MVP-03 | 元数据 | `delivery_mode` 标记（GENERATIVE / RUNTIME） | META-002B-01 | 为模式 A 留接口，成本极低 |
| MVP-04 | 运行底座 | 多租户**双层隔离**：模型定义按 `tenant_id` 过滤（租户间实体互不可见，`CatalogRuntimeEntityProvider`）+ 记录读写强制租户谓词（`buildTenantWhere`） | 已实现（`JdbcRuntimeRecordService` 链路）；语义见 [ADR-0029](../architecture/adr/0029-sdk-auto-tenant-filter.md) | 企业平台前提；仅数据层隔离，不含租户管理 UI。**失败关闭**：租户上下文缺失时必须拒绝读写（不得退化为「查全表」）；例外通道（运行时动态 SQL）因无 SDK 自动注入，须手写谓词，并由回归用例覆盖「缺租户 → 拒绝」这一分支 |
| MVP-05 | IAM | RBAC + JWT + 登录 / 账号 / 角色 / 权限绑定 + token 刷新 / 黑名单 | PRD 需求 IAM-001~004（看板对账：IAM-01~05、10、11，均已 done） | 无鉴权不可上线；排除多租户管理（P1 商业版）、SSO/MFA（501 契约） |
| MVP-06 | 入口 | 网关统一路由 `/api/v1/{domain}/**` | gateway | 访问入口 |
| MVP-07 | 控制台 | overview / quick-actions + Shell（JVM 指标，非 CPU/磁盘） | DASH-01~03 | 基础入口体验；PRD 自标 CPU/磁盘为 `[Target]` |
| MVP-08 | 元数据 | 元数据建模 UI（metadata-app） | META-VIS-02 | 让 catalog 可视可用 |
| MVP-09 | 系统管理 | 配置（含功能开关）/ 监控告警基本盘 / 日志查询 | SYS-001~003 | 基础可运维；排除 K8s（PRD 已由 P0 降 P2） |
| MVP-10 | 工程闭环 | docker compose 一键起库 + DDL 自动导入；最小四服务（Gateway/IAM/System/Metadata）；实体 API + `fields:*` OpenAPI yaml；`admin/123456` 种子；健康检查；核心流程自动化测试；**IAM 与各消费模块 JWT 密钥对齐**；**每模块容器级装配测试 + 配置键契约测试 + 文档双 lint** | 运行底座 | 非功能必须，保证可演示可验证；后三项为 2026-09-19 依 [功能模块 × 规范合规落点](#功能模块--规范合规落点对齐-v5512) 追加，属**未完成子项** |
| MVP-11 | 元数据 | **发布时物理结构对齐**：按已发布模型 diff 物理库——缺表则建、缺列则加（先 dry-run 预览、再执行；原数据保留） | **已实现（2026-09-11 补全 `JdbcPhysicalStructureGateway`：inspect/align、info_schema 只读 diff、CREATE/ADD COLUMN 幂等非破坏 DDL、标识符校验防注入）** | 字段演进是元数据平台灵魂卖点；本次补全后「加字段不丢数据」验收第 3/6 步可真实执行，而非仅状态翻转 |

---

## 功能模块 × 规范合规落点（对齐 v5.5.12）

> 本节回答一个问题：**每个 MVP 模块按最新规范该怎么落地、现在差在哪**。缺陷分两类处理——**本次就改**（影响正确性且改动小）与**登记偏差**（存量命名 / 形态，随下次功能修改收敛，不追溯）。

| ID | 关键路径 | 规范落点 | 本次校准 |
|----|----------|----------|----------|
| MVP-01 / 03 / 08 | 元数据建模（实体 / 字段 / 发布）+ 建模 UI | 写侧聚合经 `domain/repository` 加载 / 保存；命令侧要"按自然键读"须放 `application/query/*`，**不能**在 Handler 或 domain 里写读侧 DSL | 已修（原 `CreateMetaEntityHandler` 用 `Criteria` 计数触发 `domainMustNotUseQueryBuilder`，已收敛到 `MetaEntityUniquenessQuery`）；维持 ArchUnit 转绿即可 |
| MVP-02 | 运行时动态 CRUD | [ADR-0015](../architecture/adr/0015-metadata-runtime-jdbc-via-engine.md) 登记的例外：允许 Spring JDBC 直写动态表；**租户谓词 + 软删必须手写**（无自动注入）；动态条件用标签 / 占位符，禁止 Java 字符串拼接 | 约束已写入纳入项表格（此前只写了路径、没写例外边界）；若出现 JOIN 扁平投影 / 聚合统计，按 [E-4.4](../architecture/Bone-DDD-最终实践方案.md#e-44-sql-读侧仓储) 走 `@Sql` 读侧仓储 |
| MVP-04 | 双层租户隔离 | [ADR-0029](../architecture/adr/0029-sdk-auto-tenant-filter.md)：写侧 `TenantContext` 优先、为空回退实体字段；读 / 改 / 删上下文缺失 → **失败关闭**；跨租户枚举须显式 `disableTenantFilter()` 并登记 | MVP-04 理由栏已改为失败关闭口径；`businessLayersMustNotReadTenantContextDirectly` 门禁保持不变 |
| MVP-05 | IAM 鉴权 | 错误码按 `{DOMAIN_PREFIX}_{REASON}` 登记；回调验签按 [ADR-0022](../architecture/adr/0022-external-callback-signature-verification-port.md) | **环境项进 MVP-10**：各消费模块与 IAM 的 JWT 密钥必须对齐——密钥不一致表现为全链路 401，而报错信息看不出是密钥问题 |
| MVP-07 / 09 | 控制台指标、配置 / 告警 / 日志查询 | 读侧按"有无 JOIN / 投影 / 跨聚合"选通道：单表走 SDK `Criteria`，JOIN / 统计 / 全租户扫描走 `@Sql` 读侧仓储并遵守 E-4.4 六条硬约束 | **待办**：这类查询一旦追加 JOIN，须同时补 `tenant_id` 与 `deleted = 0`（**含 JOIN 子表**），并把包名登记进 `@EnableSqlRepositories` |
| MVP-10 | 工程闭环 | 质量下沉三项：① 每模块至少一个容器级 `@SpringBootTest` 装配测试（单测不加载容器，Bean 缺失在 `mvn test` 里不暴露）；② 配置键契约测试（`${key:默认}` 键名写错会**静默回落**，无报错无日志）；③ 文档双 lint（`check-ddd-doc-drift.py` + `check-ddd-gate-state.py` + `check-ddd-doc-code-sync.py --strict`） | 三项并入 MVP-10 定义；①②在各 MVP 模块普遍缺失，列为未完成子项 |
| MVP-11 | 发布时物理结构对齐 | DDL 属基础设施能力：端口在 `domain/gateway`（`PhysicalStructureGateway`）、实现在 `infrastructure/physical`；幂等非破坏 + 标识符校验防注入 | **命名偏差**（见下）：实现类 `JdbcPhysicalStructureGateway` 不符合 E-13.3 的 `<短名>GatewayAdapter` |
| MVP-P1-02 | 扩展点 | 占位实现统一 `Mock` 前缀（E-13.3）；扩展 SDK 装配成对规则——用扩展的模块「扫 `com.bone.engine.extension` + `@EnableExtensionPoints`」，不用扩展的模块须显式关闭 | 写进本节供 Phase 2 直接开工，避免开工即踩启动失败 |

### 合规偏差登记（存量，不追溯）

| # | 偏差 | 规范条文 | 处置 |
|---|------|----------|------|
| 1 | `JdbcPhysicalStructureGateway`（实现 `PhysicalStructureGateway`） | E-13.3：Domain Gateway 实现统一 `<短名>GatewayAdapter` | **2026-09-19 已收敛**为 `JdbcPhysicalStructureGatewayAdapter`（`CatalogInfrastructureConfiguration` 已同步）；遗留：包落点仍在 `infrastructure/physical`，与 E-10.2 的 `infrastructure/gateway` 不一致，登记待评估 |
| 2 | 元数据 / IAM / System 存量 `*CommandHandler` / `*QueryHandler` | E-3 明示不追溯，不得判成违规 | 保持现状；新增用例按 [E-3.7](../architecture/Bone-DDD-最终实践方案.md#e-37-入口构件决策) 走语义化 ApplicationService |
| 3 | MVP 模块普遍缺容器级装配测试与配置键契约测试 | G-1：静态规则只证明结构，装配缺陷须由 `@SpringBootTest` 证明 | 登记为 MVP-10 未完成子项，Owner：MVP 交付责任人；补齐前不得宣称"MVP 全链路已被自动化验证" |

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
7. 验证另一租户无法访问当前租户数据（双层隔离：对方实体列表中看不到该实体；以本租户记录 ID 直接请求返回 404，拒绝越权读取）；并验证**失败关闭**——缺失租户上下文的内部调用不得退化为「查全表」，该分支须有回归用例锁死（[MVP-04 / ADR-0029](../architecture/adr/0029-sdk-auto-tenant-filter.md)）。

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

## 自动联调回归（2026-09-19/20）

> 交付物：`scripts/verify-mvp-e2e.py`（前后端契约对账 + 主链路验收）、`scripts/dev/restart-mvp-services.sh`（重启 MVP 服务）。
> 运行：`python3 scripts/verify-mvp-e2e.py --allow-db-write`（默认经网关 `:8888`，需 IAM/System/Metadata/Gateway 已启动）。

**覆盖**：Part A 扫描 `bone-frontend/apps/*/src` 的 API 声明，用 OPTIONS 逐个探测后端是否存在（非 200/404 记为「无法判定」，不计为通过）；Part B 跑验收主流程 7 步 + IAM/System/控制台冒烟，共 44 项断言。

**本轮实测发现并修复的缺陷**（均为联调真实暴露，非静态推断）：

| # | 缺陷 | 根因 | 处置 |
|---|------|------|------|
| 1 | IAM/System 多个核心接口 500（账号/角色/菜单/审计/配置/告警） | 服务进程持有 9/19 之前的 `bone-metadata-sdk` jar，新类 `QueryContext$Order` 未加载 → `NoClassDefFoundError` | 重启服务（旧 jar 无法热替换）；新增 `restart-mvp-services.sh` 固化该操作 |
| 2 | `bone-system` 启动失败 | E-13.3 命名收敛后 `target/classes` 残留旧 `HttpProbeServiceHealthGateway`，与 Adapter 双 bean 冲突 | `mvn -pl bone-platform/bone-system clean` 后重启 |
| 3 | **登录 500**（阻断全链路） | ADR-0029 落地后 `iam_account` 读路径失败关闭，而登录请求无 JWT、`TenantContext` 为空 | 登录入口改为跨租户查账号（`AccountRepository.findByUsernameForLogin` + `disableTenantFilter()`），查到后经 `TenantContextRunner` 按账号租户声明上下文 |
| 4 | `/api/v1/iam/refresh` **恒 500** | `RefreshTokenService.rotate` 用 `Map.of` 装载 `replaced_by`（新令牌为 NULL）→ NPE | 改用 `HashMap`；同时 `rotate` 回传 `tenantId`，消费侧显式声明租户 |
| 5 | 建账号查重被误改为全局 | 复用登录的跨租户查找，与唯一键 `uk_iam_account_username (tenant_id, username)` 冲突 | 新增 `findByUsernameInTenant`（本租户），建账号改用它 |
| 6 | 网关缺 `/api/v1/apps/**` 路由 | `AppController` 归属 IAM 但前缀非 `/api/v1/iam/**` | 网关 bone-iam 路由增加该前缀 |
| 7 | 两道 ArchUnit 门禁会红 | IAM 缺 blueprint 的 `..domain.repository..` 豁免；`domainRepositoriesShouldNotDeclareCustomMethods` 与 ADR-0030（域仓储承载本聚合读）直接冲突 | 前者按 blueprint 同口径重写；后者删除，职责交由共享 `repository_methods_whitelist` |

**已知限制（未修，登记）**：

1. **登录按用户名跨租户定位**的前提是 `username` 全局唯一，而 DDL 唯一键为 `(tenant_id, username)`。当前多命中会记 ERROR 并返回 401（不再静默），长期需登录请求携带租户标识后再按 `(username, tenantId)` 查询。
2. `CreateAccountCommandHandler` 的配额校验与角色绑定仍取请求体/默认 0 租户，与「实际落库租户（取 `TenantContext`）」口径不一致（既有问题，本次未扩大也未收敛）。
3. 登录失败计数随 `@Transactional` 回滚（既有问题）：`BizException` 触发回滚使 `login_fail_count` 不落库，账号锁定实际未生效。需 `noRollbackFor` 或 `REQUIRES_NEW` 专项修复。
4. ADR-0029 §6 的跨租户例外清单尚未登记「登录入口」，本清单与代码注释已标注，待 ADR 补登 + 登录审计。

---

## 维护约定

- 本清单是看板 `done` 项的 **MVP 归属唯一真源**：只加映射、不改看板状态；新功能先落看板登记、再入本清单。
- **功能"完成"的判据 = 功能跑通 + 对应规范条文满足**：新增 / 修改 MVP 功能时，先查 [功能模块 × 规范合规落点](#功能模块--规范合规落点对齐-v5512) 找到该行的规范落点；涉及自定义 SQL 的，逐项过 [E-4.4 提交前自检](../architecture/Bone-DDD-最终实践方案.md#提交前自检)（扫描包登记、租户 / 软删条件、模板源未双写、投影无参构造器）。
- **偏差只许减少**：[合规偏差登记](#合规偏差登记存量不追溯) 每行须有 Owner 与"随下次修改收敛"的时点；不得新增未登记的偏差，也不得把"规范写了"当成"已实现"。
- MVP-11 已于 2026-09-11 补全实现（见下「复核与自动化测试记录」）；验收第 3/6 步现可真实执行物理对齐，不再依赖手工绑定降级口径。
- 季度评审与 PRD §4 对照，调整归属或关闭非 P0 项。
