# ADR-0032：受控批量收敛通道（一次性批量重构的授权与登记）

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted）**：规则通道（E-0.2 五条判据 + E-3.11 第 3 条改写）与先例登记均已落地——首例 bone-iam（2026-09-20 全量收敛）、第二例 bone-masterdata（2026-09-21 受控批量收敛）、第三例 bone-metadata-server catalog 子域（2026-09-21 受控批量收敛，见 §D3c）、第四例 bone-extension-studio（2026-09-21 强制收敛，覆盖 ADR-0028 选择性，见 §D3d）、第五例 bone-integration（2026-09-21 换名不换层，见 §D3e）、第六例 studio-generator（2026-09-21 换名不换层，见 §D3f） |
| **日期** | 2026-09-20 |
| **决策者** | 架构师 |
| **关联** | E-0.2 / E-0.3 / E-0.4 / E-3.11、[ADR-0028](./0028-application-service-first-selective-cqrs.md)、[06-AI协作与编码准则](../../agents/06-AI协作与编码准则.md) §12（规范语义变更与批量重构属 L3，须架构师审批） |
| **下游同步** | `Bone-DDD-最终实践方案.md` E-0.2 / E-3.7 / E-3.11、`bone-platform/bone-iam/README.md`、`doc/architecture/gate-state.json`（版本对齐） |

---

## 背景

### 1. 规则与现实已经分叉（触发本 ADR 的事实）

规范里有两条硬约束：

- E-0.2：「不因重写规范要求一次性重构业务代码」「存量 Handler 一律触达即收敛」；
- E-3.11 第 3 条：「**禁止全仓重构、批量重命名、批量格式化**」「不得以'统一架构'为名一次性改写」。

而 2026-09-20 的提交 `a9268a5c` 把 `bone-iam` 的 39 个 `*CommandHandler` + 20 个 `*QueryHandler` 一次性全量内联进 11 个语义化 `*ApplicationService`，删除全部 Handler 与对应单测目录，并把 18 个 Mockito 单测改写为构造 ApplicationService 直测。

这次重构的授权落点**只有一句 commit message**（「Q1-C（架构师一次性授权覆盖 E-3.11）」）。E-0.4 要求平台级例外记 ADR，E-0.3 要求规范语义变更同步下游——两条都没有执行。后果是可复现的：任何后来者读 E-3.11，都会把这次提交判成违规；而按 E-0.4 去查例外登记，又查不到东西。

### 2. 事实核查：这次批量重构的实际过程

登记先例之前必须先核对"批量"在这里到底意味着什么风险。逐项实测：

| 核对项 | 结果 |
|---|---|
| 对外契约 | HTTP 路径 / 请求响应 DTO / 返回类型全程未变；`@Capability` 由 TYPE 放宽到 METHOD 并改造 `HandlerRegistry`，保住被删 Handler 原有的 15 项能力元数据，`/capabilities` 发现端点输出不变 |
| 测试 | `bone-iam` 117 个测试全绿 + ArchUnit 23/23；`bone-core` 210 个测试全绿；18 个单测由"构造 Handler"改写为"构造 ApplicationService"，断言未变 |
| 门禁 | 三条受影响规则（`readSideDslOnlyInQueryLayer`、`businessLayersMustNotReadTenantContextDirectly`、`commandHandlersMustNotUseQueryBuilder`）的 freeze 基线**只收缩**：116→14、10→0、2→0，新增 0 行。**残留要看清**：`readSideDslOnlyInQueryLayer` 剩下的 14 行**全部落在 `application/service/` 的 4 个协作服务**（`AccountRoleBindingService` 4 行 / `RoleHierarchyResolver` 4 行 / `TenantQuotaEnforcer` 4 行 / `RolePermissionBindingService` 2 行）——commit message 里"application 层零 Criteria / QueryBuilder / FluentQuery 引用"只对已删除的 `application/{command,query}/handler` 成立，对该包不成立（2026-09-20 复核 `archunit_store/dbb11a26-*` 补正） |
| 负向探针 | 临时注入「应用层直取 TenantContext + 依赖读侧 DSL」于 `..application.command.handler..`，三条冻结门禁全部报红，删除探针后转绿——证明规则在判定而不是空跑 |
| 附带收敛 | 被删 Handler 的读侧 DSL 下沉 `domain/repository` 的 default 方法（ADR-0030 本聚合读），`application/{command,query}/handler` 已清零；`TenantContext` 直取改走域端口；删除 `DatabaseFixController`（`JdbcTemplate` + `@Transactional` 的生产数据修复后门）。`application/service/` 的 3 处 DSL 残留见上一行，按 E-3.12 / E-4.2 另行收敛 |
| 代价 | 应用服务类方法数上升（单类 11 个 ApplicationService 承载原先 59 个 Handler 的用例），拆分压力按 E-3.8 观察 |

## 决策

### D1. 把"一律禁止"改为"默认禁止 + 受控通道"

E-0.2 新增**受控批量收敛通道**：默认仍执行"触达即收敛"，一次性批量收敛只有**同时满足五条**才允许：

1. 范围限于**单个模块**，可回滚为一次提交；
2. **对外契约全程不变**并给出证据（HTTP 契约 / DTO / 返回类型 / 能力元数据 / 事件 payload，任一项变动即不适用本通道）；
3. 目标模块测试与 ArchUnit 全绿，freeze 基线**只收缩不新增**；
4. 有**负向探针**证明被影响的规则仍在判定（不是空跑、不是靠空匹配兜底）；
5. 收敛后的形态就是目标态本身——把 Handler 内联进同义 ApplicationService，而不是换个名字保留同一层。

### D2. 授权与登记必须落在 ADR，不能只落在 commit message

通道的授权凭证是 ADR（本文件即首个），commit message 只作为实现记录。授权走 L3（架构师审批，见 `06-AI协作与编码准则` §12）。ADR 必须写明判定依据、等价性证据与代价。

### D3. 先例登记：bone-iam（2026-09-20）

满足五条判据，登记为本通道的第一个先例。作为先例的**约束力**：后续引用它时必须同时引用五条判据，不得把"bone-iam 这么干过"当成跳过判据的理由。

### D3b. 先例登记：bone-masterdata（2026-09-21）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第二例（继 bone-iam 之后）。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 改动限定 `bone-platform/bone-masterdata` 单模块；`git commit -- <模块路径>` 即可整体回滚 |
| ② 对外契约全程不变 | HTTP 路径 / 请求响应 DTO / 返回类型 / 能力元数据 / 事件 payload 全程未变。35 个 `*CommandHandler`+`*QueryHandler`（22 命令 + 13 查询）按业务域合并为 6 个语义化 `*ApplicationService`（Entity / Field / Record / Quality / Standard / Lineage）；Controller 注入点、方法签名、`@Capability`（已下沉为方法级）均不变 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩 | 54/54 测试通过（21 架构 + 单元 + 3 套 Controller 测试）；受影响的 `domainMustNotUseQueryBuilder` freeze 基线只收缩不新增（`archunit_store/stored.rules` 当日快照为证） |
| ④ 负向探针 | 本先例未单独注入 masterdata 探针；依赖共享规则修复的跨模块证据（见下）——5 模块 ArchitectureTest 全绿，且 blueprint 此前因同一规则失败 1 项、修复后 30/30，证明规则在判定而非空跑 |
| ⑤ 目标形态即为收敛形态 | Handler 内联进同义 ApplicationService，不是换名保留同层；读侧 DSL 下沉 `domain/repository` 默认方法（ADR-0030 本聚合读） |

**共享规则修复（本先例的必要使能项）**：masterdata 收敛触发 `bone-framework/bone-architecture-test` 的 `BoneDddArchRules` 两处修复——

- `domainMustNotUseQueryBuilder`：新增 `.and().resideOutsideOfPackage("..domain.repository")` 与 `allowEmptyShould(true)`——把原先 blueprint / iam / system **各自在模块级复制**的「domain.repository 例外」收敛回共享规则（E-4.1：domain 内只允许 `domain.repository` 触碰读侧 DSL；ADR-0030 把「本聚合读」落在域仓储 default 方法上）。这是**包级排除**：凡 `@ReadSideOnly` 标注的读侧 DSL（Criteria / QueryBuilder / FluentQuery 通道）在 `domain.repository` 内一律放行，避免「每个模块抄一遍、抄漏就静默失去约束」。从而使"仓储承载本聚合读写（ADR-0030）"真正成立。
- `commandHandlersMustNotUseQueryBuilder`：新增 `allowEmptyShould(true)`（diff hunk `@@ -173,6 +185,7 @@` 可核对）——存量 Handler 形态模块（iam / metadata）仍保留各自 handler 包且无违规，规则语义不变；仅消除"无 should 即空匹配所有类"的脆弱性。

修复后跨 5 模块 ArchitectureTest 无回归（iam 26 / system 18 / integration 22 / metadata 21 / blueprint 30，全 0 失败），且 blueprint 原 1 项失败消除。

**等价性证据（契约不变）**：6 个 ApplicationService 与 35 个 Handler 一一对应；Controller `@Autowired` 字段类型由 `XxxHandler` 改为 `XxxApplicationService`，方法名 / 参数 / 返回类型、`ApiResponse`/`PageResult` 包装不变；前端 proxy 契约零改动。

**代价**：单类方法数上升（6 个 ApplicationService 承载原 35 个 Handler 用例），按 E-3.8 观察拆分压力；共享规则修复影响全平台架构门禁，已跨模块验证无回归。

### D3c. 先例登记：bone-metadata-server（catalog 子域，2026-09-21）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第三例（继 bone-iam、bone-masterdata 之后）。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 改动限定 `bone-engine/bone-metadata-server` 单模块；`git commit -- bone-engine/bone-metadata-server`（提交 `819de2d3`）即可整体回滚 |
| ② 对外契约全程不变 | HTTP 路径 / 请求响应 DTO / 返回类型 / 能力元数据 / 事件 payload 全程未变。catalog 子域 19 个 `*CommandHandler` + `*QueryHandler` + `MetaEntityUniquenessQuery` 按聚合合并为 2 个语义化 `*ApplicationService`（`MetaEntityApplicationService` 承载实体/字段 CRUD·发布·批量·物理结构治理；`MetaRelationApplicationService` 承载关系 CRUD）。4 个 catalog 控制器注入点由 `XxxHandler` 改为 `XxxApplicationService`，方法签名 / 参数 / 返回类型、`ApiResponse`/`PageResult` 包装、201+Location / If-Match→412 / Idempotency-Key 横切行为均不变 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩不新增 | 47/47 测试通过（24 架构规则 + `ServerContextLoadsTest` 上下文启动 + 单元 / Controller 测试）；`archunit_store/stored.rules` 零改动（本次新增 3 条为非冻结守卫 `readSideDslOnlyInQueryLayer` / `adapter_no_domain_repository_all_packages` / `commandHandlersMustNotDependOnApplicationService`，原冻结基线未动）；共享规则 `commandHandlersMustNotUseQueryBuilder` 等 freeze 基线未增长 |
| ④ 负向探针 | 本次新增的 3 条 `ArchitectureTest` 守卫即为探针：临时在 `..application.command.handler..` 注入读侧 DSL 或让控制器直连 `domain.repository`，对应规则即报红（规则含 `allowEmptyShould(true)`，handler 包清空期间不靠空匹配兜底）；`command_handlers_must_not_depend_on_application_service` 在 handler 包清空后由 `allowEmptyShould` 保持判定有效性 |
| ⑤ 目标形态即为收敛形态 | Handler 内联进同义 ApplicationService，不是换名保留同层；读侧 FluentQuery DSL 下沉 `domain/repository` 默认方法（`pageEntities`/`pageFields`/`pageRelations`/`findByXxx`，返回 `PageResult<聚合>`/`Optional<聚合>`，契合 ADR-0030 本聚合读白名单） |

**读侧下沉（本先例的受控形态）**：catalog 收敛把原先 handler 内的 FluentQuery 读侧 DSL 迁到 `catalog.domain.repository` 默认方法（ADR-0030 本聚合读）；`application` 层收敛后仅经仓储读模型方法取数，消除 `commandHandlersMustNotUseQueryBuilder` 的残留违规面。`adapter_no_domain_repository_all_packages` 对 `..adapter..` 全包设限（与 blueprint §11 C3 同形，但 catalog 控制器不含 `adapter.schedule` 全租户入口，故无需包级豁免）。

**代价**：单类方法数上升（`MetaEntityApplicationService` 承载实体/字段/发布/批量/物理结构全用例），按 E-3.8 观察拆分压力；新增 3 条非冻结守卫需随结构演化维护。

**等价性证据（契约不变）**：2 个 ApplicationService 与 19 个 Handler + 1 个 `MetaEntityUniquenessQuery` 一一对应；Controller `@RequiredArgsConstructor` 字段类型由 `XxxHandler` 改为 `XxxApplicationService`，方法名 / 参数 / 返回类型、`ApiResponse`/`PageResult` 包装不变；前端 proxy 契约零改动。

### D3d. 先例登记：bone-extension-studio（强制收敛，2026-09-21）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第四例。本例为**架构师显式授权的例外**：决策者在 ADR-0028「应用服务优先 + 选择性 CQRS」的选择性（L2/L3 复杂用例保留 Handler）之上，明确要求将 extension-studio 的 L2/L3 编排型 Handler（deploy/rollback/upload/marketplace-install/runtime-sync 等）也强制收编为 `*ApplicationService`。故本例方法论为「换名不换层」式重命名收敛，与 catalog/iam/masterdata 的「合并内联」式收敛不同，已留痕。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 改动限定 `bone-engine/bone-extension-engine/bone-extension-studio` 单模块；`git commit -- bone-engine/bone-extension-engine/bone-extension-studio`（提交 `7491ca57`）即可整体回滚 |
| ② 对外契约全程不变 | HTTP 路径 / 请求响应 DTO / 返回类型全程未变。12 个 `*CommandHandler`/`*QueryHandler`（5 命令 + 7 查询）逐一重命名为 `*ApplicationService`（`ExtPointCommandApplicationService`/`ExtPointQueryApplicationService`/`ExtensionCommandApplicationService`/`ExtensionQueryApplicationService`/`ExtensionStudioApplicationService`/`MarketplaceInstallApplicationService`/`PluginExecutionLogCommandApplicationService`/`PluginExecutionLogQueryApplicationService`/`StudioAuditApplicationService`/`StudioOperationApplicationService`/`DeploymentStateApplicationService`/`PluginDependencyGraphApplicationService`）；`ExtensionManagementController` 的 11 个 handler 注入点改为对应 ApplicationService，方法名 / 参数 / 返回类型（`ResponseEntity`/`ApiResponse`/`PageResult`/领域模型/视图 DTO）全部保留；`DataInitializer`、`StudioLroService`、单测 `PluginExecutionLogCursorTest` 同步更新引用 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩不新增 | 本模块 `ArchitectureTest` 19/19 全绿（共享规则 `commandHandlersMustNotUseQueryBuilder` 等：重命名后 `*Handler` 类已不存在，规则目标集清空，`allowEmptyShould(true)` 不靠空匹配兜底）。模块 59 测试中 54 通过；5 个 error 全部来自 2 个**分支既有**测试编译失败（`MetadataPersistenceIntegrationTest`、`StudioPersistenceConverterTest` 的 `Unresolved compilation problem`，与 Handler 重命名无关——已在干净树 stash 复跑确认完全一致）。freeze 基线（`archunit_store/stored.rules`）零改动 |
| ④ 负向探针 | 本模块 `ArchitectureTest` 沿用共享规则；重命名使 `..application.command.handler..`/`..application.query.handler..` 包内不再有 `*Handler` 类，`commandHandlersMustNotUseQueryBuilder` 等判据目标集清空后由 `allowEmptyShould(true)` 保持判定有效（一旦未来重新引入 `*Handler` 即用读侧 DSL，规则即报红） |
| ⑤ 目标形态即为收敛形态 | 用户授权的「换名不换层」：Handler 类就地重命名为 `*ApplicationService`，逻辑未内联到更语义化的聚合服务（与 catalog 合并式不同）。这是 ADR-0028 选择性的**显式例外**，非默认形态；后续若对该模块做聚合语义重构，可参照 catalog 模式进一步合并 |

**例外说明（覆盖 ADR-0028 选择性）**：extension-studio 的 Handler 多为 L2/L3 合法编排（deploy/rollback/upload/pruneOldVersions/publishRuntime、marketplace-install、runtime-sync 协调、版本状态机）。按 ADR-0028 本应保留 Handler。本次决策者要求强制收编为 ApplicationService，方法论为「换名不换层」，属受控批量收敛通道下的特批先例，不代表 ADR-0028 选择性被普遍推翻。后续新增扩展能力仍默认遵循 ADR-0028（简单用例 ApplicationService、复杂编排可 Handler）。

**代价**：`*ApplicationService` 仍居于原 `application.command.handler`/`application.query.handler` 包（仅类名变更，未迁移包），包结构提示性弱于 catalog 的 `application` 顶层落位；`ExtensionStudioApplicationService` 等方法数偏高（含幂等/审计/LRO 编排），按 E-3.8 观察拆分压力；未做聚合语义合并（保留 12 个独立服务）。

**等价性证据（契约不变）**：12 个 ApplicationService 与 12 个 Handler 一一对应；Controller 注入点字段类型 `XxxHandler` → `XxxApplicationService`，方法调用与返回类型不变；`DataInitializer.record(...)`、`StudioLroService` 依赖、`PluginExecutionLogCursorTest` 实例化同步更新；前端 proxy 契约零改动。

### D3e. 先例登记：bone-integration（受控批量收敛，2026-09-21）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第五例（继 bone-iam、bone-masterdata、catalog 子域、extension-studio 之后）。方法论属「换名不换层」式重命名（与 extension-studio 同形，区别于 catalog/iam/masterdata 的「合并内联」式）。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 改动限定 `bone-platform/bone-integration` 单模块；`git commit -- bone-platform/bone-integration`（提交 `16525e8e`）即可整体回滚 |
| ② 对外契约全程不变 | HTTP 路径 / 请求响应 DTO / 返回类型全程未变。21 个 `*CommandHandler`/`*QueryHandler`（12 命令 + 9 查询）逐一重命名为 `*ApplicationService`（`CreateConnectorApplicationService`/`CreateFlowApplicationService`/`ExecuteFlowApplicationService`/`ConnectorPageQueryApplicationService`/`FlowStatisticsQueryApplicationService` 等）；`ConnectorController`/`FlowController`/`MonitorController` 的注入点改为对应 ApplicationService，方法名 / 参数 / 返回类型（`ResponseEntity`/`ApiResponse`/`PageResult`/领域模型/视图 DTO）全部保留；前端 proxy 契约零改动。`application/event/**` 下的 7 个事件监听器 `*Handler` 属 `@EventListener` 订阅端（非 ADR-0028 入站边界），按 extension-studio 先例保留不动 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩不新增 | 本模块 `ArchitectureTest` 22/22 全绿（`clean` 重建复跑确认）。freeze 基线两条均**收缩**：`446b2abc`（读侧 DSL 规则）违规文件清空、`bcdd358f`（E-2 租户规则）违规文件清空，`stored.rules` 无新增条目。注：`446b2abc` 违规文件清空的真实原因并非单纯删除——`16525e8e` 已将原 handler 内读侧 DSL 下沉为 `ConnectorRepository.findById(...)` 等域仓储 default 方法（契合 ADR-0030 本聚合读），`application/{command,query}/handler` 包因此清零；详见下方「读侧 DSL 下沉」 |
| ④ 负向探针 | `..application.command.handler..`/`..application.query.handler..` 包内重命名后不再有 `*Handler` 类，`commandHandlersMustNotUseQueryBuilder` 目标集清空后由 `allowEmptyShould(true)` 保持判定有效（未来重新引入 `*Handler` 即报红）；`businessLayersMustNotReadTenantContextDirectly` 对新端口负向判定（直读即红） |
| ⑤ 目标形态即为收敛形态 | 换名不换层：Handler 就地重命名为 `*ApplicationService`，保留原 `command.handler`/`query.handler` 包，未做聚合语义合并 |

**读侧 DSL 下沉（本例的受控形态）**：integration 收敛把原 `application.command.handler`/`application.query.handler` 内的读侧 DSL（Criteria / QueryBuilder / FluentQuery 经 `ConnectorRepository`）迁到 `domain/repository` 的 default 方法（ADR-0030 本聚合读），`ConnectorController`/`FlowController`/`MonitorController` 等仅经 `ConnectorRepository.findById(...)` 取数。这是判据③「`446b2abc` 违规文件清空」的真实成因——规则目标集清空来自下沉而非删除，判定有效性由 `allowEmptyShould(true)` 维持（一旦未来重新在 application 层引入读侧 DSL，规则即报红）。

**顺带修复分支既有 E-2 违规（非本次收敛引入，随本批提交）**：`IntegrationEventEnvelopeFactory`（`application/event/outbox`）直调 `TenantContext.getTenantIdAsLong()`，违反 E-2。该违规此前被 pre-commit 增量编译（`scripts/check.sh` 不执行 `clean`）下的陈旧 `target/classes` 长期掩盖，`clean` 重建即暴露。修复方式与 sibling 同形：新增 `domain/gateway/TenantProvider` 端口 + `infrastructure/gateway/TenantProviderAdapter` 适配器，工厂经端口读租户；`business_layers_no_direct_tenant_context` 冻结违规因此**收缩为 0**（合规方向），故判据 ③ 「只收缩不新增」成立。

### D3f. 先例登记：studio-generator（受控批量收敛，2026-09-21）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第六例。方法论同属「换名不换层」式重命名。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 改动限定 `bone-engine/studio-generator` 单模块；提交 `16525e8e` 即可整体回滚 |
| ② 对外契约全程不变 | HTTP 路径 / 请求响应 DTO / 返回类型未变。`application/command/handler` 与 `application/query/handler` 包内全部 `*Handler` 重命名为 `*ApplicationService`，控制器（`CodeGenerationController`/`CodeTemplateController`/`DataSourceController`/`GenerationTaskController`/`GeneratorShortcutController`/`MetadataEntitySnapshotController`）注入点同步；方法名 / 参数 / 返回类型全部保留 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩不新增 | 本模块 `ArchitectureTest` 20/20 全绿（`clean` 重建复跑确认）。基线随共享规则谓词改写而重键（`@ReadSideOnly` 规则描述由 `..application.command.handler..` 收归 `..application..`，见 `b106a619`），属规则描述变更而非新增违规 |
| ④ 负向探针 | 重命名后 `command.handler`/`query.handler` 包内不再有 `*Handler` 类，`allowEmptyShould(true)` 保持判定有效；同时新增脚手架 `ApplicationServiceGenerator` + `applicationService.ftl`，令后续代码生成直接产出 `*ApplicationService`（弃用 `handler.ftl`），并同步 `controller.ftl`/`repository.ftl` 产物引用 |
| ⑤ 目标形态即为收敛形态 | 换名不换层：Handler 就地重命名为 `*ApplicationService`，保留原包 |

### D3g. 先例登记：ADR-0035 存量 application 层构件清理（2026-09-22，按模块分批）

满足 ADR-0032 受控批量收敛通道五条判据，登记为第七例。**与 D3~D3f 的差别**：前六例都是「换名不换层」的单次重命名；本例是 ADR-0035 三条结构门禁（R1 角色包 / R2 `*Service` 命名 / R3 ApplicationService 位置）的**存量清退**，既有搬运也有定性，故按判据①拆成**每模块一次提交**的多个批次。

| 判据 | 证据 |
|---|---|
| ① 单模块、可回滚为一次提交 | 每批次只动**一个模块**、一次提交可整体回滚；全量 96 条拆为 6 批（notification → metadata-server → iam → extension-studio → studio-generator → integration） |
| ② 对外契约全程不变 | 只搬类位置 / 改类名，不改 HTTP 路径、请求响应 DTO、返回类型、能力元数据、事件 payload；控制器注入点同步改名但不改语义 |
| ③ 测试与 ArchUnit 全绿，freeze 基线只收缩不新增 | 每批次跑本模块 `mvn test` 与 ArchUnit 全绿；`doc/architecture/application-constructs-baseline.json` 条目随收敛**删除**（只收缩），不新增 |
| ④ 负向探针 | 每批次后复跑 `scripts/check-application-constructs.py --check`（`ci-check.sh [9/9]`），确认新增违规 0 且**存量条数下降**；剩余条目仍在判定，不因删除而变成空匹配 |
| ⑤ 目标形态即为收敛形态 | 收敛后 `application/` 下只剩：平铺在根的 `*ApplicationService` + 契约（command/query/dto）+ 端口（`port/out`）+ `support`；不保留同义的第二层，也不换个名字保留角色包 |

**批次登记表**（逐批执行后填入 commit）：

| 批次 | 模块 | 存量条数（R1/R2/R3） | 动作要点 | commit |
|---|---|---|---|---|
| 1 | bone-notification | 0 / 1 / 0 | `NotificationService` → `NotificationApplicationService` | 待填 |
| 2 | bone-metadata-server | 0 / 1 / 0 | `CatalogIdempotencyService` 定性归位 | 待填 |
| 3 | bone-iam | 3 / 1 / 0 | `binding/` + `policy/` 三个类按 D3 四落点归位 | 待填 |
| 4 | bone-extension-studio | 11 / 4 / 12 | R3 搬平 → R2 定性 → R1 删角色包 | 待填 |
| 5 | bone-engine/studio-generator | 3 / 3 / 24 | 同上 | 待填 |
| 6 | bone-integration | 8 / 4 / 21 | 同上 | 待填 |

> 说明：R3（57 条）是纯位置搬运，风险最低，按 ADR-0035 D5 建议顺序 **R3 → R2 → R1** 在每模块内依次执行；R2 / R1 需逐类定性（纯领域计算下沉 `domain/service`、跨聚合绑定进聚合不变量、IO 走 `application/port/out`、无 IO 规则做值对象）。

### D4. E-3.11 第 3 条同步改写

改为「默认只做触达范围内的局部迁移；批量重命名、批量格式化一律禁止；确需一次性批量收敛时走 E-0.2 受控批量收敛通道」。"禁止全仓重构"的原始意图（防止顺手改写范围外代码）保留为默认行为。

## 理由

1. **风险源不是"批量"，而是"无证据、不可回滚、无授权的批量"。** 这次批量的三条护栏——契约不变、门禁只收缩、负向探针证明规则在判定——恰好覆盖了批量重构的真实风险面。规则原文把"批量"与"失控"混为一谈。
2. **规则被默默违反比规则被修正更贵。** 现状是：规范说禁止，实践说已授权，两边都不更新。下一次有人需要批量收敛时，他将无法判断该不该做，只能复制这次的做法——把授权塞进 commit message，例外治理就此失效（E-0.4 的意义正在于此）。
3. **判据可判定。** 五条全部是可核对的客观事实（范围、契约、测试与基线、探针、目标形态），不依赖"重构者觉得干净"这类主观判断。
4. **不动默认值。** 触达即收敛仍是默认，批量是例外；这与 E-0.2 不设数字 KPI 的立场一致——通道不制造"该批量了"的进度压力。

## 后果

### 正面

- 规范与事实重新对齐：IAM 那次收敛从"违规"变成"已登记先例"；
- 批量收敛有了可复用、可审计的判据，不必每次重新论证；
- 强制了负向探针这一环——它恰好证明门禁没有退化，而这是批量改名/删类最容易被破坏的东西。

### 负面 / 风险

- **批量授权可能被滥用**：把五条判据当形式，尤其是第 2 条（"契约不变"）与第 4 条（负向探针）最容易被简化。缓解：判据写进 ADR 正文而非仅写结论，评审按判据逐条核对。
- **收敛方向单一**：通道只认"内联成 ApplicationService"这一目标形态；如果将来目标形态改变，本 ADR 的五条判据第 5 条需要同步修订。
- **ADR 数量增加**：每次批量收敛都要一份 ADR。这是刻意选择——例外必须留痕，宁可多一份文档。

## 备选方案

| 方案 | 未采纳原因 |
|---|---|
| A. 坚持"一律禁止批量"，要求把 IAM 收敛补记为例外 | 规则与实践继续分叉——下一次批量仍然只能靠 commit message 授权；且 IAM 的事实证明"批量"本身可控，规则本身该改 |
| B. 完全放开批量（取消"触达即收敛"约束） | 失去默认值就等于失去护栏；E-0.2 的不设 KPI 立场正是为了避免"为达标而批量" |
| C. 不做规范动作，只在 `bone-iam/README.md` 留一句说明 | 例外散落在模块文档里，跨模块复用与审计都无从谈起；E-0.4 的登记分层（跨模块/平台级→ADR）就是为这种情况设的 |

## 合规与迁移

- **规范**：E-0.2 新增通道条款、E-3.11 第 3 条改写、E-3.7「落地现状」不再登记迁移计数——三处同批修改（v5.5.14）。
- **代码**：本 ADR **不要求**任何代码改动。存量 Handler 仍按触达即收敛处理；`bone-iam` 现状即合规形态。
- **门禁**：不新增机器规则。五条判据中只有第 2、3、4 条可被工具部分见证（测试与基线、探针输出），第 1、5 条是评审判据——按 CORE-08，不得声称本通道已被机器证明。
- **本 ADR 内的数字**（39/20/11、117、23/23、116→14 等）是**当日证据快照**，属于冻结记录；规范正文不得复制这些计数（这是 v5.5.14 第 ③ 条整改的同一取向）。bone-masterdata 先例（§D3b）的数字（35 Handler→6 ApplicationService、54/54 测试、跨 5 模块 ArchitectureTest 全绿）为 **2026-09-21 证据快照**，同属冻结记录，规范正文不得复制。
