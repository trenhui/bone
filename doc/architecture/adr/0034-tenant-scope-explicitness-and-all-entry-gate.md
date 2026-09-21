# ADR-0034：租户隔离显式性 — SQL 通道启动期 fail-fast + 全租户入口单一判据

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted）**：SDK fail-fast、接口级默认策略、三条共享门禁、8 个应用模块接入与首次运行发现的问题处置均已落地 |
| **日期** | 2026-09-20 |
| **决策者** | 架构师 |
| **关联** | E-2（多租户）、E-4.4（`@Sql` 通道六条硬约束）、CORE-05、[ADR-0029](./0029-sdk-auto-tenant-filter.md)、[ADR-0030](./0030-domain-repository-read-merge.md)、[ADR-0031](./0031-sdk-optimistic-lock-and-async-tenant-context.md)、[ADR-0032](./0032-controlled-batch-convergence.md) |
| **下游同步** | `bone-engine/bone-metadata-sdk`（`TenantScope` / `RepositoryFactoryBean` / 测试夹具）、`bone-framework/bone-architecture-test`（`BoneDddArchRules`）、8 个应用模块的 `ArchitectureTest` 与域仓储、`Bone-DDD-最终实践方案.md` E-2 / E-4.1 / E-4.4 / G-1.1 / G-1.5、`Bone-多租户规范.md` §4 / §7、`doc/architecture/gate-state.json`、**新增** `scripts/check-tenant-entity-declaration.py` + `doc/architecture/tenant-entity-baseline.json`（并入 `scripts/ci-check.sh` `[8/8]`） |

---

## 背景

### 1. "忘写注解"过去是静默失败，而规范声称它有保护

三条实测事实：

1. `TenantScope.value()` 的默认值是 `TenantScopeMode.MANUAL`，其 JavaDoc 明写"现有未标注 `TenantScope` 的 `@Sql` 方法等同此模式，行为不变"；
2. `TenantSqlRewriter.rewrite(...)` 对 `MANUAL` / `ALL` / `BYPASS` **原样返回、不做任何注入**，只有 `AUTO` 分支才计算租户条件；
3. `RepositoryFactoryBean` 只在读到 `AUTO` 时才注入，**没有任何"缺注解即拒绝注册"的校验**。

于是：**`@Sql` 方法忘写注解 = 不带任何租户条件执行 = 跨租户裸读，且构建期与启动期都不报错**。而 E-4.4 原文写的是"缺标注或锚点缺失一律拒绝执行，不会静默放行"——规范给了一个当时并不存在的保护承诺（fail-closed 实际只覆盖"标了 AUTO 但复杂查询缺锚点"）。

### 2. 全租户（ALL）有两套实现，旧门禁只看得见一套

| 通道 | 事实所在 | 旧门禁能否看见 |
|---|---|---|
| SQL 通道（`@Sql` / 外置模板） | `@TenantScope(ALL)`，`TenantSqlRewriter` 真读它 | 能（旧门禁按注解判定） |
| Criteria 通道（`domain/repository` 的 `default` 方法） | `Criteria.disableTenantFilter()`——`TenantFilterInjector` **不读注解** | **不能**（注解在这些方法上只是"给门禁看的标记"） |

实测：`PaymentRepository` 的两条扫描方法同时写了 `disableTenantFilter()` 与 `@TenantScope(ALL)`（好习惯，但无强制）；而 `infrastructure/messaging/outbox/OrderOutboxRelayPortAdapter` 用 `disableTenantFilter()` 做跨租户扫描，**没有任何注解与之配对**，全仓也没有任何门禁检查 `disableTenantFilter` 的使用面。结论：**只用 `disableTenantFilter()` 就能绕过当时的两道门禁**。

### 3. 命名约定写在规范里，代码里却不存在

E-4.4 与 5.5.13c 版本行都写"全租户扫描方法名后缀 `AllTenants`"，但全仓**没有任何方法叫 `*AllTenants`**（方法名是 `findExpiredOrders` / `findExpiredPayments` / `findSuccessPaymentsBefore`）。同时 blueprint 旧的模块级门禁在代码上按注解判定、注释里却按后缀解释（同一处两段互斥注释并存），"改名派"与"注解派"因此长期各说各话。

## 决策

### D1. SQL 通道缺 `@TenantScope` → **启动期拒绝注册**（fail-fast）

`RepositoryFactoryBean#createMethodHandler` 在注册 SQL 通道方法（非 `default`、非 `BaseObject`/`BaseRepository` 方法）时校验注解，缺失即抛 `IllegalStateException`，报错点名"接口#方法"并列出四种模式的写法。理由：把"最常走的那条路"从默认裸奔变成默认安全；成本实测**极低**——全仓生产代码 `@Sql(` 注解 0 处、外置模板 2 处且都已标注，未标注的 25 处全在 SDK 测试夹具。

### D2. 注解支持**接口级默认策略**

`@TenantScope` 的 `@Target` 扩展为 `{METHOD, TYPE}`：方法级注解优先，其次取仓储接口上的注解。动机是让 D1 可以低成本满足——整仓同构（如全部走 `MANUAL` 的复杂报表仓）写一次接口级注解即可，否则 fail-fast 会逼团队在"逐方法标注"与"关掉校验"之间二选一。

### D3. 全租户入口的**单一事实判据**

以「`@TenantScope(ALL)` ∨ 方法体出现 `Criteria.disableTenantFilter()`」为唯一事实（含接口级 `@TenantScope(ALL)`）。两条通道机制不同，只按注解或只按方法名判定都会漏。

### D4. 三条共享门禁（从模块级提升到 `BoneDddArchRules`）

1. `allTenantEntryPointsOnlyCalledBy(pkg, AllTenantCallers…)`：全租户入口只允许**已登记调用方**调用；
2. `allTenantScanMethodsOnlyCalledBySchedule(pkg)`：上面那条的包装（只允许 `adapter.schedule`）；
3. `allTenantEntryPointsMustBeNamedAllTenants(pkg)`：命名与事实**双向绑定**——是入口就必须叫 `*AllTenants`，叫 `*AllTenants` 就必须真是入口。

`AllTenantCallers` 支持包模式与**类全名**两级登记（ArchUnit 的包匹配器不匹配类名，实测 `..service.AuthService` 不会排除该类）。8 个应用模块（blueprint / iam / integration / masterdata / system / extension-studio / metadata-server / studio-generator）全部接入；无 `adapter.schedule` 的模块以 `allowEmptyShould(true)` 合法空匹配。

### D5. 命名与事实绑定后，`*AllTenants` 从"文档承诺"变成"可执行约定"

blueprint 三个全租户方法改名（含外置模板文件名，模板 ID 由方法名派生）：`findExpiredOrders` → `findExpiredOrdersAllTenants`、`findExpiredPayments` → `findExpiredPaymentsAllTenants`、`findSuccessPaymentsBefore` → `findSuccessPaymentsBeforeAllTenants`；调用方（3 个 schedule Job）与测试同步。

### D6. 平台级延伸：新增「租户表 ↔ 实体声明」门禁

上述发现不是单模块问题——按同一判据全量扫描 `bone-init.sql`：**56 张含 `tenant_id` 的表里，12 张的 `@Table` 实体没有声明 `tenantId`**（bone-integration 5、bone-system 6、metadata-engine 1）。故把探针固化成门禁：

`scripts/check-tenant-entity-declaration.py` + `doc/architecture/tenant-entity-baseline.json`（并入 `ci-check.sh` `[8/8]`）：新增表命中即失败；存量 12 张登记在基线且**只可收缩**，每条带 `classification`（`pending` = 已达 per-tenant 结论待补 / `needs-owner-decision` = 性质待裁决 / `by-design` = 确为平台全局表）、`owner` 与修法说明。负向探针验证过（临时实体 → 报红并点名表 / 实体 / 文件）。

分类依据（2026-09-21 复核）：

- **pending 7 张**：integration 5 张（模块详设明确"流程列表与执行日志严格按 `tenant_id` 隔离"）、`sys_config`（详设写"`tenant_id` + `config_key` 唯一"）、`meta_field`（ADR-0016 记录 catalog 侧 `MetaEntity`/`MetaField` 自声明 `tenantId`；缺的是引擎侧第二份映射 `PlatformMetaField`）。
- **needs-owner-decision 5 张**：`sys_dict`、`sys_log`、`sys_schedule_task`、`sys_alert_rule`、`sys_alert_event`——详设表清单未提租户，须由模块 Owner 判定是平台运维视图还是 per-tenant（判为全局则改 `by-design` 并写理由）。

### D7. 两处收敛（同一轮内踩到同一面墙）

1. **统计路径显式化**：integration 的 `FlowStatisticsJob` 改走显式 `*AllTenants` 入口（`IntegrationFlowRepository.findForStatisticsAllTenants` + `IntegrationLogRepository.countByFlow*AllTenants` + `FlowMonitorService.getExecutionCountAllTenants` 等），并在该模块 `ArchitectureTest` 把 `FlowMonitorService` 登记为允许调用方（平台汇总口径）。租户可见路径（`MonitorController` 监控页）**保持租户内口径不变**——两组方法刻意分开命名，避免"顺手复用"把跨租户数据带进租户页面。该模块原先的冻结条目随之**清零**（规则本身已满足），模块级租户缺口改由 D6 的门禁基线跟踪。
2. **`domain.repository` 豁免回归单源**：`domainMustNotUseQueryBuilder` 的豁免此前由 blueprint / iam / system **各自本地重写**（IAM 注释还写明"不改共享规则以免一次性放宽所有模块"）。本轮复核证伪了该顾虑——用严格规则的模块在 domain 里本就没有 DSL 依赖，相关冻结基线全为 0 字节——而 integration（本轮）与 masterdata（同期在途改动）又先后撞上同一面墙。故把豁免固化进共享规则（`..domain.repository..` 放行，其余 domain 包照旧禁止），blueprint / iam 的本地副本同步收敛（system 待其收敛完成后再收）。

## 首次运行即发现的两处真问题（本 ADR 的价值证据）

| 模块 | 发现 | 处置 |
|---|---|---|
| bone-iam | `AccountRepository.findByUsernameForLogin` 用 `disableTenantFilter()` 跨租户定位账号——**未命名、未登记**；调用方是 `AuthService`（application 层，非 schedule） | 改名 `findByUsernameForLoginAllTenants` + 在 IAM `ArchitectureTest` 登记 `AuthService` 为允许调用方（登录前置：认证前租户未知，属合法跨租户；该方法的 JavaDoc 已限定"只限登录入口"，查到账号后的写与权限查询必须经 `TenantContextRunner` 声明租户，ADR-0031 D3） |
| bone-integration | `FlowStatisticsJob.execute()`（`@Scheduled`）调用 `IntegrationFlowRepository.findByCriteria(Criteria.create())`。**追查后确认这不是"任务要补 ALL 入口"，而是模块级缺口**：本模块 5 个聚合全部 `extends AggregateRoot`（无 `tenantId`），而 `int_*` 表都是 `tenant_id NOT NULL`、模块详设要求严格隔离——SDK 按**实体字段**判定租户表，实体不声明 ⇒ `TenantFilterInjector` 直接返回 ⇒ **该模块所有 Criteria / QueryBuilder 读都没有租户过滤**，用户可达的 `MonitorController`（`/executions`、`/executions/{id}`、`/statistics`）同样受影响 | 按 G-2 **冻结登记**并**改正根因描述**（不静默白名单、也不伪装成单个任务的租户模型问题）；缺口与拆除条件登记到 `bone-platform/bone-integration/README.md`。修法需数据归属迁移（L4，AI 不执行），故留待模块 Owner 决策 |

**负向探针**：临时删除 `OrderRepository#findExpiredOrdersAllTenants` 的 `@TenantScope(ALL)` 后，`schedule_only_calls_all_tenants_repository_methods` 与 `all_tenant_entry_points_must_be_named_all_tenants` **同时报红**，恢复后转绿——证明规则在判定而不是空跑（G-3 对规则准入的要求）。

## 后果

### 正面

- 规范里"缺标注不会静默放行"从**假承诺**变成**真机制**（启动期阻断），且迁移成本在还能承受的时点被支付；
- 全租户入口第一次有了单一、可判定的判据与可见的调用面，Criteria 通道的逃生舱不再是门禁盲区；
- `*AllTenants` 命名从文档承诺变成有门禁的双向绑定，调用点能一眼看出跨租户；
- 三条规则进入共享库后，新模块无需各写一份（此前 blueprint 的两条只服务一个模块）。

### 负面 / 风险

- **白名单可能被滥用**：`allTenantEntryPointsOnlyCalledBy` 的登记是评审决定，门禁只保证"没有未登记调用方"，不判断"该不该跨租户"。当前每个登记点都写了理由，新增时须逐条评审。
- **规则只看直接调用**：`adapter.schedule` 调用的**应用服务内部**再做租户内读（如 integration 的 `FlowMonitorService`）不在该规则视野内——本次只在冻结说明里登记，静态覆盖仍有缺口。
- **接口级 `@TenantScope` 会放大命名要求**：整接口标 `ALL` 时该接口所有 SQL 方法都需带 `*AllTenants` 后缀，这是刻意的摩擦（提醒"整仓跨租户"是异常形态），但需要在评审中解释。
- **启动期校验会打断既有懒加载习惯**：任何新增 SQL 方法漏标注解从"运行时才炸"变成"起不来"，属于刻意的失败前移。

## 备选方案

| 方案 | 未采纳原因 |
|---|---|
| A. 只把跨租户方法改名成 `*All`（并把门禁改回按后缀判定） | 后缀是可随意起名的装饰，注解 / `disableTenantFilter` 才是运行时事实；按后缀判定等于把门禁降级为命名规范，且 `*All` 与 `publishAll` / `deleteAll` / `findAll` 等通用命名冲突 |
| B. 只做默认值 fail-fast，不动 ALL 判据 | Criteria 通道的绕过口仍在（本轮实测存在），等于修了半个问题 |
| C. 全局按后缀禁止非 schedule 调用 `*All` | 全局后缀扫描会误伤无关命名；且真正该收口的是"事实 + 调用面"，不是字符串 |
| D. 把 integration 的违规直接加进白名单让门禁转绿 | 那会把一个真实缺陷伪装成已批准例外；按 G-2 冻结才能既保住构建又留住债务可见性 |

## 合规与迁移

- **SDK**：`TenantScope`（`@Target` 扩展 + 明确"必须显式声明"与接口级默认）、`RepositoryFactoryBean`（`requireTenantScope` fail-fast + 启动期解析策略）、测试夹具两处接口级 `@Manual`、新增 `RepositoryFactoryBeanTenantScopeTest`（3 个用例：缺注解拒绝 / 接口级默认生效 / 方法级覆盖）。
- **共享规则**：`BoneDddArchRules` 新增 `allTenantEntryPoint` / `allTenantEntryPointsOnlyCalledBy` / `allTenantScanMethodsOnlyCalledBySchedule` / `allTenantEntryPointsMustBeNamedAllTenants` 与 `AllTenantCallers`；不引入对 `bone-metadata-sdk` 的编译期依赖（注解按名字匹配）。
- **模块**：8 个应用模块接入三条规则；blueprint 三个方法 + 外置模板改名；IAM 登录入口改名并登记调用方；integration 冻结登记一条真实债务。
- **规范**：E-2 增加全租户入口判据与调用面条款；E-4.4 首段与硬约束表第 3 条改写（去掉与实现不符的表述，写明启动期 fail-fast）；G-1.5 登记三条新规则及其证明边界。
- **验证方式与已知限制**：ArchUnit 规则在 8 个模块实测通过（含负向探针）；SDK 单元测试通过。**本沙箱内无法运行 Spring 上下文测试**（Mockito inline mock maker 需要 JVM self-attach，被沙箱阻止），因此 `RepositoryFactoryBean` 的启动期行为只在单元层验证——模块侧需在开发机执行 `mvn -pl bone-engine/bone-metadata-sdk test` 与各模块 Spring 测试补齐。
