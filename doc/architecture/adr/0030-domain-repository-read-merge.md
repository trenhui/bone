# ADR-0030：单一仓储 + 外置 `.sql` 优先 + `@TenantScope` 自动租户注入

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted）· P1（SDK `@TenantScope` 通道）、P2（blueprint 单一仓储试点）与 P3（规范 + 门禁 R1–R6 + `doc/_generated` 重算）均已落地；P4（可选 jsqlparser、其他模块按 E-0.2 触达收敛）待办**——本 ADR 的"优化"已按批准执行（见 §10 实现记录） |
| **日期** | 2026-09-19 |
| **决策者** | 架构师（本轮方向由架构师拍板；机制来自 [design-single-repository-tenant-scope.md](../design-single-repository-tenant-scope.md)，边界判据与门禁替代来自本 ADR 初稿） |
| **单一真源** | 本 ADR 是"单一仓储 + `@TenantScope`"的唯一真源；`design-single-repository-tenant-scope.md` 已并入本文，只作机制论证留档，不再单独维护 |
| **关联** | CORE-05、P-3.5、E-4.1 / E-4.2 / E-4.4 / E-13.1 / E-13.3、反模式 #5、[ADR-0028](./0028-application-service-first-selective-cqrs.md)、[ADR-0029](./0029-sdk-auto-tenant-filter.md)、SDK 最佳实践 §6.8 |
| **下游同步** | `Bone-DDD-最终实践方案.md`、`bone-framework/bone-architecture-test`（规则集）、`bone-engine/bone-metadata-sdk`、`bone-blueprint/README.md`、`doc/_generated/*` 重算、`doc/agents/03-架构分层规范.md` |

---

## 0. 为什么可以合并（根因与前提）

此前"读写仓储物理分离"被当成硬约束，**根因只有一条**：`@Sql` / 外置 `.sql` 通道完全不经过 `TenantFilterInjector`，而 Criteria 通道自动注入租户与软删（ADR-0029）——同模块内两种**相反的安全默认值**。一旦 `@Sql` 通道获得 fail-closed 的自动注入，"自动 vs 手动"的差别消失，物理分离就不再是安全护栏，可以按工程偏好合并。

七条已核实的事实（决定本方案形状）：

1. `@Sql` 走 `SqlMethodHandler → SqlProcessor → SqlExecutor`，**不经过** `TenantFilterInjector`（安全债根因）。
2. `SqlConfigProperties.template.loadPriority` 支持 `annotation-first`（默认）/ `classpath-first`，配合 `fallbackEnabled`。
3. 外置路径固定为 `classpath:/sql/<package>/<RepoClass>/<method>.sql` —— **接口所在包被写进资源路径**。
4. `SmartRowMapper` 用 `BeanUtils.instantiateClass` + 字段反射 ⇒ 读模型需**无参构造 + 非 final 字段**。
5. `RepositoryFactoryBean#shouldSkipMethod` 只跳过 `Object` 与 `BaseRepository` 的 public 方法 ⇒ 接口里的 **`static` 辅助方法会导致启动失败**（`TemplateNotFoundException`）；blueprint 已踩过并移除 static，SDK 侧仍应加防御。
6. 代理生成的模板 ID 恒为 `{接口全限定名}.{方法名}`（`module/method` 简化格式存在于 loader，但代理不会用）。
7. `domain.repository` 已在 `@EnableSqlRepositories(basePackages = {...})` 内（blueprint 实测），域仓储直接承载 SQL 无需改应用配置。

## 1. 决策

### 1.1 结构与边界

| # | 决策 | 说明 |
|---|------|------|
| D1 | **单一仓储**：`domain/{ctx}/{aggregate}/repository/*Repository` 同时承载写方法与**本聚合**的读形态 | 接口留在 domain ⇒ **HC-002 与依赖方向不动**（application → domain 端口，infrastructure 出代理实现） |
| D2 | **读侧归属用一条可判定规则裁定**：`FROM`/`JOIN` 表集合 ⊆ 本聚合拥有的表集合（E-1.2 声明）⇒ 域仓储；⊄ 或需要**独立路由/独立存储** ⇒ `application/query/port/*QueryPort` | 取代"跨聚合/报表/搜索/独立读库"这种枚举。"搜索"与"单聚合统计"从枚举删除（本就属本聚合读形态） |
| D3 | **两条底线不可取消**：① 跨上下文读表（E-1.1 硬约束）② 需要独立路由/存储的读（只读库、物化视图、ES、独立缓存） | 否则"报表改走只读库"这类基础设施变更会反向推入 domain |
| D4 | 读模型（`*Projection`）**下沉到 domain**，落 `domain/{ctx}/{aggregate}/projection/`，保持后缀、**不带任何框架/SDK 注解** | 域仓储返回它，若仍留在 `application` 则 domain 依赖 application，`domainMustNotDependOnOuterLayers` 立刻红 |
| D5 | 域仓储读方法**不得**以 SDK `Criteria` 作参数或返回 | `Criteria` 是 `@ReadSideOnly`，会扩大 P0-5 的豁免面；参数用 domain 查询对象/显式标量 |
| D6 | 应用层**直接注入域仓储**（方案 X）：本聚合读不再经 `OrderQueryPort`；将来该读需要独立路由/读库时再抽 QueryPort（推迟到需要） | 端口可 mock，测试缝不丢；少一层 |

### 1.2 分页

| 形态 | 用法 | 取舍 |
|---|---|---|
| **β（新代码，推荐）** | 域内值对象 `domain/{ctx}/common/Page<T>`（`record`：`records`/`total`/`page`/`size`）；由 `default` 方法从 `count*` + `select*(limit/offset)` 组装；application/adapter 各做一次 `PageResult.of(...)` | 域契约不携带 Jackson/传输语义 |
| **α（存量兼容）** | 直接复用 `com.bone.core.model.PageResult`（SDK 继承的 `pageByCriteria` / `queryPage` 已返回它，且 `PageResult#map` 可直接映射） | 零新类型，但域签名带上 `@JsonAlias`、`nextCursor` 等序列化语义 |

硬约束：① `@Sql` 方法**不得**直接声明分页返回类型（代理无分页分支，E-4.4 第 6 条）；② `count` 与 `select` 必须**同源同通道**（同一查询对象派生、同为 Criteria 或同为自定义 SQL）；③ 两条语句之间的并发窗口须在方法 javadoc 声明，报表/导出场景允许近似或改用 `COUNT(*) OVER()`。`ApiResponse` 仍禁止出现在域仓储签名上。

### 1.3 租户安全（本方案的关键机制）

1. **新增 `@TenantScope`（`domain.annotation`）**：`AUTO`（注入 tenant_id，fail-closed，无上下文即抛）/ `MANUAL`（调用方手写，向后兼容）/ `ALL`（全租户扫描，不注入不抛）/ `BYPASS`（平台运维逃生舱）。
2. **默认 `MANUAL`（向后兼容，不注入），强制点落在 `AUTO`**：`@TenantScope` 缺省为 `MANUAL`，既存 `@Sql` 方法不强制补注解即不报错（避免一次性击碎存量，是相对原草案"缺注解即启动期报错"的**实现取舍**）。真正的强制点在 `AUTO` 模式——要求作者放锚点 `/*bone:tenant*/`（联表须带别名），JOIN 无锚点一律失败关闭；`ALL`/`BYPASS` 须登记授权（E-2）。由此"静默漏注"只剩"作者主动选 `MANUAL` 且手写漏写租户条件"这一种，由 R4 lint 兜底。
3. **AUTO 语义与 Criteria 通道同一不变量**：`@Sql` 通道由 `TenantSqlRewriter` 实现（与 Criteria 通道的 `TenantFilterInjector` 平行，共享"可信上下文优先 / 失败关闭 / `MissingTenantContextException`"不变量）；AUTO 方法**不再收 `tenantId` 参数**，SQL 内**不得再手写** `tenant_id` 条件，改放锚点 `/*bone:tenant*/`。
4. **`ALL` / `BYPASS` 受治理**：必须登记（模块 README / E-2）+ 授权（`platform:*`）+ 审计，并在后台线程显式声明（定时线程无上下文）。
5. **注入实现（已落地 · 锚点标记法，非原草案的 MyBatis 占位符 / jsqlparser）**：`TenantSqlRewriter` 对 `@Sql` 通道最终 SQL 做 fail-closed 注入，**未引入 jsqlparser、也未复用 MyBatis 标签 AST**——这是相对原草案 P1/P2 的**实现取舍**，理由见 §10：
   - **锚点标记（联表必备）**：作者在 SQL 中放置 `/*bone:tenant*/`，SDK 替换为 `<column> = :__boneTenantId__`（联表须自带别名，如 `"o.tenant_id"`）；
   - **简单查询启发式**：无锚点且无 JOIN 的单表查询自动注入；
   - **JOIN 无锚点一律抛异常**（别名歧义无法安全注入）——失败关闭，绝不静默漏注；
   - 与 ADR-0029 Criteria 通道同一不变量：缺可信 `TenantContext` 即抛 `MissingTenantContextException`。
   - **P2 · 语法级重写（可选，未建）**：仅在锚点法被证明不够用时再引入 `jsqlparser`，仍须 fail-closed + 别名限定 + CTE/UNION/子查询覆盖。
6. **软删**：自动注入仅**显式 opt-in**（`@TenantScope(autoDelete = true, table = "...")`，不自猜表名）；手写的 `deleted = 0` 由 lint 强制（**含 JOIN 的每个子表**——blueprint 已因此漏过一次）。

### 1.4 外置 `.sql` 优先

1. 默认 `loadPriority` 翻为 **`classpath-first`**（外置优先、`@Sql` 兜底/覆盖）。当前全仓主代码仅 blueprint 2 个 `@Sql` 方法、无外置文件 ⇒ 翻默认零影响。
2. **两源禁止并存**：同一方法不得同时存在 `@Sql` 与同名 `.sql`（SDK 命中即用、另一个永不加载且不报错 = 影子 SQL）；`@Sql` 不得为空值。由 **R3** lint 强制——有了它，翻默认才不会引入歧义。
3. 模板 ID 与包路径**解耦**（如 `@SqlSource("order/findOrderWithItems")`）：**推荐但不阻塞**——不做该能力时的失败是启动期响亮失败，不是静默错误。
4. `static` 辅助方法：SDK 侧 `shouldSkipMethod` 增加 static 跳过（防御）；同时规范写明"仓储接口不写 static，逻辑放 `default`"。

## 2. 目标形态（blueprint 试点）

```text
domain/order/
├── Order.java · OrderItem.java · OrderStatus.java
├── event/
└── projection/                        # 读模型（domain 拥有，无框架注解）
    ├── OrderWithItemsProjection.java
    └── OrderHeadProjection.java
domain/repository/
├── OrderRepository.java               # 唯一仓储：写 + 本聚合读
└── OrderItemRepository.java           # 已登记的受控形态，不变
resources/sql/com/bone/blueprint/domain/repository/OrderRepository/
├── findOrderWithItems.sql
└── findCreatedExpiredBeforeAllTenants.sql
```

```java
public interface OrderRepository extends Repository<Order, Long> {

  @TenantScope(AUTO)                                   // 自动注入 tenant_id（fail-closed）
  List<OrderWithItemsProjection> findOrderWithItems(@Param("orderId") long orderId);

  @TenantScope(ALL)                                    // 定时 Job：全租户，已登记授权
  List<OrderHeadProjection> findCreatedExpiredBeforeAllTenants(@Param("before") Timestamp before);

  default Page<OrderHeadProjection> pageOrders(OrderSearchQuery q) {   // β：域内分页
    long total = countOrders(q);
    return Page.of(selectOrderPage(q, q.offset(), q.limit()), total, q.page(), q.size());
  }
}
```

删除清单：`infrastructure/query/OrderReadRepository`、`OrderQueryPort`；`PaymentQueryAdapter` 在第二阶段同模式折叠（试点只做 Order，先摸 R1–R6 误报）。

## 3. 规范改动（同批，缺一不可）

| 条文 | 现在 | 改为 |
|------|------|------|
| CORE-05 | "Repository 不做报表/Join/投影；非聚合本身的查询一律走 QueryPort" | "读侧归属按 **D2 的表集合判据**；域仓储不得成为跨聚合查询桶" |
| P-3.5 | "不承担分页、报表、Join 或 UI 投影" | "不承担**跨聚合**分页/报表/Join；本聚合读形态允许" |
| E-4.1 | 返回类型白名单：聚合 / `Optional<聚合>` / boolean / void | 增加 domain `*Projection`（及 `List`/`Optional`）、`long` 计数、`Page<*Projection>`；写入"读方法必须显式声明通道（`default` / `@Sql`+`@TenantScope`）" |
| E-4.2 | 读侧投影必须 `infrastructure/query` 物理分离 | 改为**可选**：可同仓储声明（推荐），也可保留独立读仓储；**无论哪种，读方法必须租户安全** |
| E-4.4 | `@Sql` 不注入租户；默认 `annotation-first` | 补 `@TenantScope` 四模式与强制显式标注；默认改 `classpath-first`；两源禁并存；软删 opt-in |
| E-13.1 | `application/query/projection/` → `*Projection` | `domain/{ctx}/{aggregate}/projection/` → `*Projection` |
| E-13.3 | `*Repository` = domain 写侧仓储 | 明确"域仓储含本聚合读方法"；`*QueryPort` 专属跨聚合/独立路由读 |
| 反模式 #5 | "仓储接口出现 findPage / statistics / search / 多表 Join = DAO 化" | "**跨聚合**查询/报表进域仓储 = DAO 化"（否则与本 ADR 自相矛盾） |
| ADR-0029 | 租户注入仅限 Criteria 通道 | 扩至 `@Sql`/外置 `.sql`（`@TenantScope`），关闭原缺口 |
| SDK 最佳实践 §6.8 | "复杂 SQL 放 infrastructure，不散落在 domain" | "域仓储可持有本聚合读 SQL；**跨聚合** SQL 仍在 infrastructure" |
| G-1.6 | CORE-05 由 `commandHandlersMustNotUseQueryBuilder` 守护 | 更新为 R1–R6 组合 |

## 4. 门禁（替代而不是删除）

`domainRepositoriesShouldOnlyDeclareWhitelistedMethods` 在合并后失去主语，必须换成下面这组——**只做减法就等于把"写仓储不 DAO 化"从机器判定退回人工评审**（CORE-08）：

| # | 规则 | 拦什么 |
|---|------|--------|
| R1 | 域仓储中返回非聚合类型的方法，必须是 `default`，或带 `@Sql`/外置模板 | 无通道标记的裸读方法 |
| R2 | 域仓储返回类型白名单：聚合 / `Optional<聚合>` / `boolean` / `void` / domain `*Projection` 及其 `List`·`Optional`·`Page` / `long`；禁 `ApiResponse`、application、infrastructure、协议类型 | 跨层与响应型类型泄漏 |
| R3 | `@Sql` 与同 ID 的 `.sql` 不得并存；`@Sql` 不得为空 | 影子 SQL（SDK 静默取其一） |
| R4 | 租户表相关的 `@Sql`/外置方法必须有 `@TenantScope`；`AUTO` 方法 SQL 内不得再手写 `tenant_id`；手写软删必须覆盖 JOIN 的每个子表 | 静默跨租户越权 / 软删明细被 join |
| R5 | `oneAggregatePerTransaction` **按写方法语义改造**（排除 `default` 与 `@Sql` 读方法） | 读写同接口后按 Repository 类型计数失真 |
| R6 | 域仓储自定义 SQL 的 `FROM`/`JOIN` 表集合必须 ⊆ 该聚合拥有的表集合（取自 E-1.2） | 跨聚合/跨上下文读被顺手塞进域仓储（补上"D2 判据"的机器判定） |

## 5. SDK 工作项

| # | 工作项 | 必需性 | 备注 |
|---|--------|--------|------|
| W1 | `loadPriority` 默认 `classpath-first` + 两源互斥 lint（R3） | 必需（P1） | 零风险，不碰业务代码 |
| W2 | `@TenantScope` + 强制显式标注 + 占位符 `TenantFilterNode` | 必需（P1） | 先占位符后语法重写 |
| W3 | `RepositoryFactoryBean` 跳过 `static` 方法 | 必需（P1） | 防启动期崩溃 |
| W4 | `SmartRowMapper` 支持构造器 / `record` 绑定 | 推荐（P2） | 否则 domain 读模型被迫"无参构造 + 非 final" |
| W5 | `jsqlparser` 语法级重写（fail-closed，别名限定） | 可选（P4） | 仅在 W2 占位符被证明不够用时上 |

## 6. 迁移阶段

| 阶段 | 内容 | 产出 | 状态 |
|---|---|---|---|
| **P1** | W1 + W2 + W3 + R3 lint + 治理检查 | SDK 租户安全通道与治理就绪 | **已完成**（2026-09-19：`@TenantScope` 四模式 + `TenantSqlRewriter` 锚点注入 + `RepositoryFactoryBean` static 跳过；默认 `classpath-first` 经 SDK 既有配置） |
| **P2** | blueprint 试点：`OrderRepository` 合并读方法、projection 迁 `domain/order/projection/`、外置 `.sql` 落地、删 `OrderReadRepository`/`OrderQueryPort`、application 改注入、domain 内 `Page<T>`；补读路径租户隔离测试 | 单一仓储试点 | **已完成**（2026-09-19：编译 + `ArchitectureTest` 27/27 通过 + `spotless:check` 通过；DB 集成测试需本地跑） |
| **P3** | 规范 + 门禁（R1–R6）+ 本 ADR 定稿 + `compliance.json`/`doc/_generated` 重算，**同批提交** | 文档与代码同批 | **已完成**（2026-09-19：`Bone-DDD-最终实践方案.md` 9 条文 + G-1.6 同步；R5 落 ArchUnit、R1/R3/R4/R6 落 `SqlTemplateGovernanceTest`；三道 DDD 门禁复绿） |
| **P4** | 可选：W5 语法级注入；`PaymentQueryAdapter` 等同模式折叠；其他模块按 E-0.2 触达即收敛 | 全平台统一（不做批量重构） | 待办 |

## 7. 验收

1. SDK 单测：`@TenantScope` 四模式（AUTO 注入且 fail-closed / MANUAL 原样 / ALL、BYPASS 不注入）、占位符展开、缺标注启动报错、static 方法不再触发模板加载。
2. blueprint：`mvn -o -pl bone-blueprint test` 全绿（含 ArchUnit、`RepositoryTenantIsolationTest`、新增读路径隔离测试）；`spotless:check` 通过。
3. 外置加载实证：`findOrderWithItems.sql` 在 `classpath-first` 下被加载（日志 `成功加载模板 ... source=classpath:/sql/...`）。
4. 三道治理检查全绿（doc-drift / doc-code-sync --strict / gate-state generate --check）；`doc/_generated/*` 重算后 `--check` 通过。
5. **反例先行**：跨租户不可见、软删明细不回归、`@Sql` 与文件并存被拦——三类各有一个可复现反例测试。

## 8. 风险

| 风险 | 级别 | 缓解 |
|---|---|---|
| 翻 `loadPriority` 默认致同名外置覆盖 `@Sql` | P2 | 当前无外置文件，影响面仅新增；R3 lint 禁并存 |
| 语法级重写误改/漏改（JOIN、子查询、CTE） | P1 | 先占位符；上 W5 时 fail-closed + 别名限定 + 覆盖测试 |
| `@TenantScope` 遗漏 | P0→已防 | 启动期报错 + CI 扫描双拦 |
| `AUTO` 无上下文导致查询失败 | 预期行为 | 与 Criteria 一致；定时 Job 用 `ALL` |
| 软删自猜表名误注入 | P1 | 仅显式 `autoDelete=true, table=`；手写由 R4 强制 |
| domain 出现持久化塑形类型 | P2 | W4（record 绑定）未做前登记为偏差 |
| 跨聚合读被顺手塞进域仓储 | P1 | R6 机器判定 + 语义评审兜底 |
| 分页形态二选一未定 | P2 | 新代码 β、存量 α（本 ADR 已定） |

## 9. 不采纳的备选

- **接口搬到 `infrastructure`**（换依赖方向省一层）：要动 HC-002，放弃"端口由内层拥有"，代价大于收益。
- **连 D2 判据一起取消**（跨聚合读也挂某聚合）：等于改 P-2/E-1.1 战略边界，须同时认下"放弃表 Owner 可声明性 + 放弃独立读存储 + 报表变更走域层评审"。
- **只改文档不动实现**：制造"文档允许、代码还没有"的漂移，与 G-1.7 判据双向约束冲突。
- **直接上 jsqlparser 全自动**：Bone 的 `@Sql` 专用场景恰是复杂 JOIN，"全自动"风险面最大；先用可控占位符。

## 10. 实现记录（2026-09-19）

> 本 ADR 经架构组采纳后，P1（SDK）+ P2（blueprint 试点）已落地。以下记录"实际落地形态"与"原草案 §1.3.5 的差异"，避免文档再度漂移。

### 10.1 实际落地形态

- **SDK（P1）**：`bone-metadata-sdk` 新增 `@TenantScope`（四模式 `AUTO/MANUAL/ALL/BYPASS`）+ `TenantSqlRewriter`（锚点标记 `/*bone:tenant*/` 注入）+ `RepositoryFactoryBean` 接入（含 `static` 方法跳过防御）。`loadPriority` 默认 `classpath-first` 复用 SDK 既有配置，翻默认零影响。
- **blueprint（P2）**：`OrderRepository` 合并写 + 本聚合读（`findOrderWithItems` `@Sql`+`MANUAL` 显式 `tenantId`、`findCreatedExpiredBeforeAllTenants` `@TenantScope(ALL)`、`findStatusById`/`findOrderPage` 走 Criteria）；投影迁 `domain/order/projection/`；外置 `.sql` 落地 `resources/sql/.../OrderRepository/`；删除 `OrderReadRepository` 与 `OrderQueryPort`，application 改直注域仓储。
- **验证**：`bone-blueprint` 编译通过；`ArchitectureTest` 27/27 通过（P0-4 白名单已放宽容纳域读模型，见 `BoneDddArchRules#returnsAggregateRootOrScalar`）；`spotless:check` 通过；`archunit_store` 冻结基线未变。DB 集成测试（真 `@Sql` 执行）需本地有库环境运行。

### 10.2 与原草案（§1.3.5）的差异 · 实现取舍

| 原草案 §1.3.5 | 实际落地 | 取舍理由 |
|---|---|---|
| P1 = MyBatis `<tenant-filter/>` 占位符（标签 AST） | **锚点标记 `/*bone:tenant*/`**（`TenantSqlRewriter` 字符串替换） | 零新依赖、锚点位置由作者掌控、CI 可 grep 校验；不必引入 MyBatis 标签 AST |
| P2 = jsqlparser 语法级重写 | **未建**；作为可选升级保留 | Bone `@Sql` 场景以复杂 JOIN 为主，全自动风险面最大；锚点法已满足"fail-closed + 别名限定" |
| "缺 `@TenantScope` ⇒ 启动期报错"（强制显式） | 默认 `MANUAL`（向后兼容，不注入） | 既存 `@Sql` 方法一次性补注解会击碎存量；强制点改落 `AUTO`（锚点 + 失败关闭）+ R4 lint 兜底 |
| AUTO"复用 `TenantFilterInjector`" | AUTO 由独立 `TenantSqlRewriter` 实现（与 Criteria 通道平行） | 两通道共享"可信上下文优先 / 失败关闭 / `MissingTenantContextException`"不变量，但实现分离，便于各自演进 |

### 10.3 P3 落地记录（2026-09-19 收官）

- **门禁 R1–R6 已全部有机器载体**（原为"仅 R2 落地、其余 Planned"）：
  - **R2**（域仓储返回类型白名单）→ ArchUnit `domainRepositoriesShouldOnlyDeclareWhitelistedMethods`（P0-4 放宽，见 §10.1）。
  - **R5**（`oneAggregatePerTransaction` 排除读方法）→ 同一 ArchUnit 规则**按写方法语义改造**：`default`（有方法体）与 `@Sql` 标注的仓储方法不再计入持久化计数（`isReadSideRepositoryMethod`），否则"写 + 本聚合读"合并进同一接口后会把同一聚合的读形态误算成第二个聚合写入。
  - **R1 / R3 / R4 / R6** → blueprint 模块级治理测试 `SqlTemplateGovernanceTest`。**为什么不落 ArchUnit 规则**：这四条要读的东西不在字节码里——外置 `resources/sql/**.sql`、SQL 文本的 `FROM`/`JOIN` 表集合、README 的 E-1.2 数据所有权声明，以及"同一模板 ID 是否同时存在 `@Sql` 与 `.sql`"这类跨 Java 与资源的判定。它是**机器门禁**（`mvn test` 即执行），不是把门禁降级为人工评审。
    - R1：返回非聚合类型的方法必须带通道标记（`default` / `@Sql` / 同名外置 `.sql`）。
    - R3：模板 ID 取 `<接口全限定名>.<方法名>`，外置集合与 `@Sql` 集合取交集须为空；`@Sql` 不得为空值。
    - R4：自定义 SQL 方法必须有 `@TenantScope`；`AUTO` 的 SQL 不得再手写 `tenant_id`；手写软删须覆盖每个 `JOIN` 子表别名。
    - R6：`FROM`/`JOIN` 表集合 ⊆ 该聚合拥有的表——表集合取自 blueprint `README.md` 的 E-1.2 声明（本轮补建，原本缺失），聚合取自 `Repository<T, ?>` 的 `T`。
  - 该测试自带一组**判据函数用例**（合成 SQL 输入：两源并存、`AUTO` 手写 `tenant_id`、`JOIN` 子表缺软删、单表无别名写法），避免判据失效时因兼容输入而静默变绿。
  - **规范 `Bone-DDD-最终实践方案.md` 的 G-1.6 以「门禁①…⑥」转述本条 R1–R6**——规范正文禁用 v4 的 R 加数字编号（见 `check-ddd-doc-drift.py`），故用圆圈序号，二者按序对应；G-1.6 的载体列直接写真实载体名（ArchUnit 规则名或 `SqlTemplateGovernanceTest`），不写未实现的规则名。
- **规范同步**：`Bone-DDD-最终实践方案.md` 的 CORE-05 / P-3.5 / E-4.1 / E-4.2 / E-4.4 / E-13.1 / E-13.3 / 反模式 #5 / G-1.6 已按本 ADR §3 同批改写；`doc/_generated/*` 重算；三道 DDD 门禁（`check-ddd-doc-drift` / `check-ddd-doc-code-sync --strict` / `check-ddd-gate-state`）复绿。
- **ADR-0029 扩面**：ADR-0029 原为 Criteria 通道；本 ADR 的 `@TenantScope`/`TenantSqlRewriter` 将其扩展到 `@Sql`/外置 `.sql` 通道，关闭原"@Sql 不经过 TenantFilterInjector"的缺口——已在 ADR-0029 实现状态补充。
- **仍未做**：R1–R6 只在 blueprint（试点）接线，其余模块按 E-0.2 触达即收敛（P4）；DB 集成测试需在有库环境执行。
