# ADR-0035：应用层只保留 ApplicationService（撤销 ADR-0033 的第三类构件）

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted，2026-09-22）**：规范三处已回退（E-10.2 废止说明 / E-13.2 回三种含义 / E-13.4 恢复"`*Service` 只出现在 `domain/service`"），门禁与基线已落地 |
| **日期** | 2026-09-22 |
| **决策者** | 架构师 |
| **取代** | [ADR-0033](./0033-application-collaboration-service.md)（应用层协作服务 = `application/service/*Service`） |
| **关联** | CORE-04 / CORE-08、E-3.2（Ceremonial Architecture）、E-3.7（入口构件决策）、E-3.8（ApplicationService 拆分标准）、E-10.1 / E-10.2（包结构与落点）、E-13.1 / E-13.2 / E-13.4（命名）、[ADR-0030](./0030-domain-repository-read-merge.md)、[ADR-0032](./0032-controlled-batch-convergence.md) |
| **下游同步** | `Bone-DDD-最终实践方案.md`（E-10.2 / E-13.2 / E-13.4）、**新增** `scripts/check-application-constructs.py` + `doc/architecture/application-constructs-baseline.json`（并入 `scripts/ci-check.sh` `[9/9]`）、各模块 `application/service/**` 的存量清理（代码侧，见 E-0.2） |

---

## 背景

### 1. ADR-0033 的第三类构件是怎么来的，又为什么站不住

ADR-0033 承认「应用层协作服务」为第三类构件（落点 `application/service/`，判据是"被多个用例复用的应用级协作逻辑"），并**删掉了 E-10.2 原有的禁令**"不建 `application/service/` 子包"。它当时的目标是给 `bone-iam` 已有的 9 个类一个合法归属。

这个出发点错在方向：规范写的是**目标态**，存量不合目标态应按 E-0.2「触达即收敛」迁移，而不是把现状洗成合法类别——正是 E-3.2 Ceremonial Architecture 与 E-3.11「新增抽象先质问：没有它，哪个独立问题解决不了」要防的事。

### 2. 两天内长出来的东西证明了它站不住

- `bone-iam` 自己把 `application/service/` 分化成 **`service/` + `binding/` + `policy/`** 三个角色包（原 ADR-0033 预期"数量保持少量"，实际是继续分包）；
- `bone-integration` 的 `application/service/` 里同时装着：**端口**（`FlowRuntime`）、**端口实现**（`LinearSyncFlowRuntime` / `CamelFlowRuntime`）、**执行引擎**（`FlowNodeExecutor`）、**用例编排**（`ConnectorService` / `FlowService` / `FlowExecutionService`）、**读侧统计**（`FlowMonitorService`）——一个包五种东西；
- `bone-extension-studio` / `studio-generator` / `metadata` 等模块另有 11 个 `application/service/*Service`；
- 同期还有 **57 个 `*ApplicationService` 被放进 `command/handler` / `query/handler`**：命名按新口径改了，位置却留在旧构件包里——同一类漂移的另一副面孔。

### 3. 认知成本的机理（这才是要解决的问题）

`*Service`（应用层）与 `*ApplicationService` 的唯一实质差别是"**是否被 adapter 直接调用**"。这是**调用关系**，不是职责：今天被 1 个用例调用、明天被 3 个用例调用，而职责不会随调用者数量漂移。用一个会变的维度划分构件类别，结果必然是"放哪都说得通"；更关键的是这类判断**无法被门禁机械校验**（CORE-08 已承认门禁证明不了这种语义），于是判断成本被永久摊到每一次改动上——这就是"职责定位模糊 + 认知门槛"的来源。

## 决策

### D1. application 层只有三类构件（白名单）

1. **用例入口**：`*ApplicationService`，**平铺在 `application/` 根目录**（E-10.2）；
2. **契约与端口**：`*Command` / `*Query` / `*Result` / `*Dto` / `*Projection` / `*Port` / `*Assembler` 等，落在 `command/` `query/` `port/` `event/` 等约定子包；
3. **技术编排类**：`support/`，只协调 `application/port/out`，**不碰 `domain`**（典型模块 ≤2 个）。

白名单之外的顶层子包与"第二类 service 类"一律不允许。

### D2. 废止第二类 service 层

恢复 E-10.2 的禁令：`application/service/`（以及 `binding/` / `policy/` 这类按调用关系命名的角色包）不再存在。E-13.2 回退为「`*Service` 后缀的三种含义」，E-13.4 恢复"`*Service` 只出现在 `domain/service`"。

### D3. 复用逻辑的四个落点（"跨用例复用"不是建层理由）

| 复用内容的性质 | 落点 | 判据 |
|---|---|---|
| 业务规则 / 不变量 | 聚合，或 `domain/service`（可依赖 domain 端口；优先纯函数，取数留给应用层）。**领域类不带 Spring stereotype**：`domainCoreShouldOnlyDependOnAllowedPackages` 禁止 domain 依赖 Spring，由应用层构造或在 `config` 类 `@Bean` 装配 | CORE-03、P-3.3 |
| 技术能力（缓存失效、密码编码、审计落库、幂等） | `application/port/out` + infrastructure 实现；AS 内一行调用 | E-4.3 |
| 读侧组合 / 统计 | `QueryPort`（`application/query/port` + `infrastructure/query`），或域仓储 `default` 读方法 | E-4.2、ADR-0030 |
| 跨用例共享的编排 | 同一个 AS 的方法，或按用例族拆 AS（E-3.8：按共享依赖与事务语义拆，不按调用者数量拆） | E-13.2（禁止 AS 互调） |

### D4. 门禁与基线（防新增，只可收缩）

新增 `scripts/check-application-constructs.py`，三条纯结构判定：

- **R1 禁用角色包**：`application` 顶层子包必须命中白名单；
- **R2 唯一 service 形态**：`application` 下不得有以 `Service` 结尾但不是 `ApplicationService` 的类；
- **R3 位置**：`*ApplicationService` 必须平铺在 `application/` 根目录。

存量违规进 `doc/architecture/application-constructs-baseline.json`（首轮 96 条：R1 25 个文件——`service` 22 / `binding` 1 / `policy` 2，跨 studio、generator、iam、integration 四个模块；R2 14 个类；R3 57 个错位文件），只可收缩；脚本并入 `scripts/ci-check.sh` `[9/9]`。

### D5. 存量迁移

交代码侧按 E-0.2 触达即收敛执行，顺序建议 **R3 → R2 → R1**（先机械移动、再定性、最后删目录），明细与每类动作见基线文件的 `_migration`。本 ADR 不要求任何一次性批量重构（ADR-0032：批量须走受控通道）。

## 理由

1. **判据必须稳定且可机械校验**：职责不随调用者数量变化；白名单把"放哪"从语义判断变成确定性答案（规则→领域、用例→AS、技术→端口/基础设施），并可被门禁守住。
2. **它已经在诱导错误落点**（有现成案例，非假设）：`PasswordPolicyValidator`（无 IO 纯规则留在应用层，ADR-0033 的 D4-1 自己就写了该下沉 `domain/service`）、`RoleHierarchyResolver`（读侧 DSL 落在 application，E-4.2 违规）、`AccountRoleBindingService`（绑定写入本属聚合不变量，被搬出聚合）。
3. **门禁能补上认知成本**：ADR-0033 的判据是语义的，无法机械执行；本 ADR 的三条判定全是包名与类名，落地后新增违规立即失败。
4. **平台原本就是这么定的**：E-10.2 的原文禁令与 blueprint/IAM/system 各自复制过的 `..domain.repository..` 豁免同属一个取向——**应用层收窄，例外显式**。

## 后果

### 正面

- "逻辑放 service 还是 appservice"这个每次改动都要做的判断被消除；
- `FlowRuntime` 这类端口/实现混在应用层的形态会被 R1/R2 暴露出来，按 E-10.1 归位（端口→`application/port/out`，实现→`infrastructure`）；
- 57 个错位 `*ApplicationService` 有了可执行清单（移完一条，`--check` 会提示从基线移除）。

### 负面 / 风险

- **白名单可能过严**：出现第 4 类合法构件时必须先改规范 + ADR，而不是就地新增包——这是刻意摩擦；
- **基线存量面大**（14 + 57），短期内 `--check` 的价值主要是"防新增"，收口依赖代码侧按模块推进；
- **`support/` 的边界仍靠评审**：门禁只能验包名，验不了"是否偷偷碰了 domain"（CORE-08 的证明边界照旧适用）。

## 备选方案

| 方案 | 未采纳原因 |
|---|---|
| A. 保留 ADR-0033 的三类划分，只把判据写细 | 判据的核心维度（是否被 adapter 直接调用）会随重构漂移，写多细都不稳定；两天内的三个角色包已经证明它会继续分包 |
| B. 只删目录、不禁 `*Service` 命名 | 会以别的位置复发——现在根目录就躺着 `NotificationService`（`bone-notification`）与 `RoleHierarchyResolver`（IAM） |
| C. 只改规范、不加门禁 | 语义判据无法机械执行，退化只是时间问题（ADR-0033 就是先例：写完后两天内新增 11 个 `*Service`） |
| D. 一次性把全仓 `application/service` 清空 | 违 E-0.2 与 E-3.11（禁止批量重写）；且涉及跨模块调用面，必须走 ADR-0032 的受控通道 |

## 合规与迁移

- **规范**：E-10.2「`application/service/` 子包已废止」+ 三类构件白名单；E-13.2 回退三种含义；E-13.4 恢复 `*Service` 只在 `domain/service`；版本行 5.5.16。
- **门禁**：`scripts/check-application-constructs.py`（R1/R2/R3）+ `doc/architecture/application-constructs-baseline.json`（只可收缩）+ `ci-check.sh` `[9/9]`。
- **代码**：不在本 ADR 范围。存量按基线 `_migration` 的顺序由模块触达时收敛；`application/support/` 与 `*Orchestrator`（E-3.1 允许的入口构件）不受影响。
- **验证方式**：脚本 `--baseline` 生成基线、`--check` 防新增，负向探针（临时新增一个 `application/service/FooService`）验证会报红。
