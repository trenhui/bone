# ADR-0033：应用层协作服务（`application/service`）的定位与落点判据

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted）**：E-10.2 三层对照表、E-13.2「四种含义」、E-13.4 后缀裁决同批落地 |
| **日期** | 2026-09-20 |
| **决策者** | 架构师 |
| **关联** | E-10 / E-10.1 / E-10.2、E-13.2 / E-13.4、E-3.2 / E-3.5 / E-3.7 AS-01、[ADR-0028](./0028-application-service-first-selective-cqrs.md)、[ADR-0032](./0032-controlled-batch-convergence.md) |
| **下游同步** | `Bone-DDD-最终实践方案.md` E-10.2 / E-13.2 / E-13.4、`bone-platform/bone-iam/README.md` |

---

## 背景

### 1. 一类构件在规范里无家可归

E-10.2 目前只区分两种非用例入口构件：

- **语义化 `*ApplicationService`**：平铺在 `application/` 根目录，持有写事务，只编排；
- **技术编排类**（`application/support/`）：**只**依赖 `application/port/out`，**不得碰 `domain`**。

而 `bone-platform/bone-iam` 自 2026-05 起就有 **9 个 `application/service/*Service`**（`AccountRoleBindingService`、`TenantQuotaEnforcer`、`RoleHierarchyResolver`、`PasswordPolicyValidator`、`RolePermissionBindingService`、`AuthService`、`RoleService`、`PermissionService`、`AuditService`）。抽取其中三个逐个核对依赖：

| 类 | 注入的 domain 依赖 | 是否属"技术编排类" |
|---|---|---|
| `AccountRoleBindingService` | `domain.account.AccountRole`、`domain.repository.AccountRoleRepository`、`domain.gateway.AccountAuthorityCache` | 否——它碰 domain |
| `TenantQuotaEnforcer` | `domain.repository.TenantRepository` / `AccountRepository` / `RoleRepository`、`domain.tenant.Tenant` | 否——它碰 domain |
| `PasswordPolicyValidator` | 无 domain 依赖（纯校验） | 否——它是纯业务规则，本该在 `domain/service` |

于是这类构件同时不满足三个既有定义：不是用例入口（不被 adapter 调用）、不是技术编排类（碰 domain）、不是领域服务（在 application 层）。E-13.2 又把 `*Service` 后缀指派给 `domain/service`，E-13.4 明文写"`*Service` 只出现在 `domain/service`"。

### 2. 后果：规范不响，评审靠猜

E-13（命名）与 E-10（包结构）的相关部分都是 Advisory，没有 ArchUnit 规则；`check-ddd-doc-drift.py` 与 `check-ddd-doc-code-sync.py` 都只校验符号、链接与命名样例，不校验包结构语义。因此这 9 个类既不会报红，也不被任何条文承认。照 E-10.2 现文与 E-13.4 的字面读法，它们全部越界；照 E-13 是 Advisory 的阅读方式，又无人有权判定。这正是 E-0.4 想避免的状态：**例外没有登记处，判据散落在每个评审者的记忆里**。

## 决策

### D1. 承认第三类构件：应用层协作服务

**应用层协作服务**（落点 `application/service/`）指：**被多个用例复用的应用级协作逻辑**——跨聚合 / 跨仓储的编排片段、应用级配额与校验、多个 QueryPort 的组合读取。

### D2. 依赖与边界（五条硬边界）

| # | 边界 | 判据 |
|---|------|------|
| B1 | 可依赖 `domain`（仓储 / 聚合 / 领域服务）与 `application/port/out`、`application/query/port` | 与 ApplicationService 的依赖面相同 |
| B2 | **只由 application 层构件调用**，不得被 adapter 直接注入 | 被 adapter 注入即等于用例入口，应升格为 ApplicationService |
| B3 | **不持有事务边界** | 需要独立事务说明它其实是一个用例，应回落 ApplicationService（E-3.5） |
| B4 | **不承载聚合不变量** | 出现状态迁移判断即回落聚合或领域服务（E-3.2、E-6.4） |
| B5 | **不得依赖读侧 DSL**（`QueryBuilder` / `Criteria` / SQL） | 与其余 application 构件同规则（E-4.2，`readSideDslOnlyInQueryLayer`）：取数下沉 `domain/repository` 的 `default` 方法（ADR-0030）或 `infrastructure/query`。"协作服务"不是绕开该门禁的口子 |

### D3. 命名按包分层

`*Service` 成为**唯一按包分层的后缀**：`domain/service` 是纯业务规则（无 IO、无事务），`application/service` 是应用层协作服务。同一个模块内必须靠包路径区分，不得在同一层混用两种语义。E-13.2 的「三种含义」相应改为「四种含义」，E-13.4 的裁决条目同步改写。

### D4. 落点判据（防止它长成第二个万能桶）

新增一个这样的类之前，按顺序问：

1. 纯业务规则且无 IO，**且失败语义可用 `DomainException` 表达**（异常转换留给应用层，E-5.3.1）→ 下沉 `domain/service`；
2. 只协调技术端口、不碰 domain → 归 `support/`；
3. 两者都不是，且**不是单点透传**——满足任一条才留在 `application/service/`，并在模块 README 登记用途：① 被多个用例（含同一用例族内的多个方法）复用；② 被多个应用服务或模块内公共工具调用；③ 承载单一用例内**可独立测试**的协作步骤（算法、规则组合），内联会把两件不同的事挤进一个方法；
4. **没有调用方的直接删除**——这里不是留死代码的地方（E-3.11 第 2 条：答不上"没有它哪个独立问题解决不了"就不建）。

数量保持少量；方法名用调用它的用例的语言（如 `bindRoles`、`enforceQuota`），不用 `handle` / `process` 这类入口构件词汇。

### D5. 不新增机器门禁

本形态没有可稳定静态判别的判据（"是否被多个用例复用""是否承载不变量"都是语义判断）。因此本决策**不引入新的 ArchUnit 规则**，只落规范条文 + README 登记 + 评审。按 CORE-08，不得声称本形态已被机器证明。唯一例外是 B5：`readSideDslOnlyInQueryLayer` 是既有 Hard gate，对 `application/service` 同样生效，无需新增规则。

### D6. 复核记录：bone-iam 逐类落点（2026-09-20）

落地时按 D4 逐类核对了 `bone-platform/bone-iam/src/main/java/com/bone/iam/application/service/`（调用方按 `src/main` 现算）：

| 类 | 调用方（main） | 落点判定 |
|---|---|---|
| `AccountRoleBindingService` | `AccountApplicationService`（4 处，覆盖创建 / 更新 / 删除 / 回读） | 留（D4-3①）；**待收敛**：`Criteria`（`deleteByCriteria` 写侧 + `findByCriteria` 读侧）出现在 application，命中 B5 |
| `RolePermissionBindingService` | `AccountApplicationService` | 留（D4-3③：与上者同构的绑定替换步骤，含权限缓存失效）；**待收敛**：同上（`Criteria` 删除条件） |
| `AuthService` | `AccountApplicationService`、`AuthApplicationService` | 留（D4-3②） |
| `AuditService` | `common/util/AuditUtils` | 留（D4-3②：被模块内公共工具调用） |
| `TenantQuotaEnforcer` | `AccountApplicationService`、`RoleApplicationService` | 留（D4-3②）；**待收敛**：`Criteria` + `countByCriteria` 出现在 application，命中 B5 |
| `PasswordPolicyValidator` | `AccountApplicationService`、`AuthApplicationService` | **暂留**：无 IO 的纯规则（形式命中 D4-1），但失败路径依赖 `IamErrors` 的「业务码 → HTTP 状态」配对工厂（`BizException`）；迁 `domain/service` 须先改造成 `DomainException` + 应用层转换（E-5.3.1），属独立变更，按触达即收敛处理 |
| `RoleHierarchyResolver` | `AuthApplicationService` | **留但违规**：命中 B5——在 application 内直接使用 `QueryBuilder` / `FluentQuery`，目前仅由 `archunit_store` 冻结基线兜住（未报红不等于合规）；取数须下沉 `domain/repository` 的 `default` 方法或 `infrastructure/query`（E-4.2、E-3.12） |
| `RoleService` | 无 | **删除**（D4-4）：`RoleRepository` 单点透传、全仓零调用方 |
| `PermissionService` | 无 | **删除**（D4-4）：同上 |

> 这次复核的价值不只是给九类贴标签——它当场暴露了三类此前没人看见的问题：**死代码**（两个零调用方的透传类）、**冻结基线掩盖的真实违规**（`application/service` 下 4 个类依赖读侧 DSL，占该规则冻结基线的全部 14 行）、**分类边界缺少前置条件**（"纯规则下沉 domain"在异常语义不匹配时不成立）。D4-1 的异常前置条件、D4-3 的"非单点透传"限定与 D4-4 的删除条款，都是被这九个真实样本逼出来的。
>
> 4 个 DSL 残留的统一解法是同一件事：**把条件构造与取数推到 `domain/repository` 的 `default` 方法**（ADR-0030：域仓储是该 DSL 的 SDK 集成点，`updateByCriteria` 由基类自带），或推到 `infrastructure/query`；application 只调用语义化方法（如 `unbindAccount` / `countAccountsInTenant`）。这属代码侧变更，不在本 ADR 范围，已登记在 `bone-platform/bone-iam/README.md`。

## 理由

1. **规范要描述事实，而不是否认事实。** 这 9 个类不是事故，它们承载的是真实存在的复用（角色绑定、租户配额、密码策略、层级解析）。要么承认这一类并给边界，要么必须给出迁移去向——现状是两者都没做，于是它们只能靠"没人查"活着。
2. **B2/B3/B4 三条把风险面收窄到可控。** 这类构件最容易发生两种退化：被 adapter 直接注入（悄悄变成第二套用例入口，制造 E-3.2 的套娃）、把事务和不变量塞进来（变成"什么都往里放的服务"）。三条硬边界直接对着这两种退化。
3. **不动 `support/` 的定义。** `support/` → 不碰 domain 这条边界清晰且有用（它保护了技术编排类不越层），不因为新增第三类而放宽。
4. **与 E-3.7 的立场一致。** AS-01 承认"入口自带独立职责时可直接使用专用构件"；同理，协作逻辑自成一类时也应当被承认，而不是硬塞进 ApplicationService 把它撑成上帝对象（那正是 `adaptersMustNotDependOnGodObjects` 要防的方向）。

## 后果

### 正面

- IAM 的 9 个类从"按字面读即越界"变成"已登记的合法形态"，评审不必再各自解释；
- `*Service` 后缀的双层语义被显式写清，E-13.4 的"一个后缀只承载一种构件类别"不再自相矛盾；
- 给出落点判据后，新增类有地方可问，减少"随手建一个 XxxService"的熵增。

### 负面 / 风险

- **承认一类就增加一类被滥用的可能**：`application/service/` 有机会变成新的万能桶。缓解：B2～B4 三条边界 + README 登记 + D4 的落点判据顺序；若同一模块该类数量持续上升，应回到本 ADR 复核判据而非默许。
- **后缀歧义成本转移给读者**：`*Service` 现在必须看包才知道语义。这是有意的取舍——替代方案（强制改名为 `*CollaborationService` 之类）会引入第二套后缀，代价更大（见备选方案 C）。
- **无机器门禁**：全靠评审，存在长期失守风险。若将来出现可静态判别的信号（例如"被 adapter 注入的 application/service 类"），应补规则并回到 G-1.5 登记证明能力。

## 备选方案

| 方案 | 未采纳原因 |
|---|---|
| A. 全部迁到 `domain/service` | `TenantQuotaEnforcer` 注入仓储并跨聚合读，属应用层协作而非纯领域规则；搬过去会把 IO 与仓储依赖拖进 domain，直接撞依赖方向（CORE-02、HC-002） |
| B. 全部迁到 `application/support/` | `support/` 的定义是"只依赖 `application/port/out`、不碰 domain"；这些类要碰 domain 仓储，迁过去只能靠放宽 `support/` 边界，代价是摧毁一条有效的护栏 |
| C. 保持现状（不承认、不迁移），把 E-13 相关条目升级为 Hard gate 逼其迁移 | 门禁无法判别"是否被多个用例复用"，一升级就是误报制造机；且把 9 个已经在用的类判成违规，等于要求无收益的搬迁（违 P-1 的务实取向） |
| D. 承认但强制改名（如 `*CollaborationService`） | `*Service` 在两层按包区分是可读的；新增后缀会与 `*DomainService` / `*ApplicationService` 组成第二套命名体系，落地成本高于收益（E-13 本就是 Advisory 的一致性约定） |

## 合规与迁移

- **规范**：E-10.2 的对照表由两列扩为三列（新增"应用层协作服务"）、E-13.2「三种含义」改「四种含义」并补一行、E-13.4 的 `*Service` 裁决条目改写（v5.5.14 同批）。
- **代码**：**零改动**——IAM 现有 9 个类就地落入合法分类。不要求重命名、不要求搬包。
- **门禁**：不新增规则，不调整任何现有规则的强度（E-13 相关仍为 Advisory）。
- **后续动作（代码侧，另行排期）**：① `bone-iam/README.md` 登记这 9 个类的用途与命中的落点判据；② 逐个复核 B2～B4 是否成立——尤其 `PasswordPolicyValidator` 属"纯规则无 IO"，按 D4 第 1 条应下沉 `domain/service`，是否迁移由模块 Owner 在触达该代码时决定；③ 若新增同类构件，按 D4 顺序判定并登记。
