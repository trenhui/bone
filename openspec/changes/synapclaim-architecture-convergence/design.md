---
comet_change: synapclaim-architecture-convergence
role: technical-design
canonical_spec: openspec
---

## Context

本设计基于 2026-11-08 实机核查的事实基线，不沿用任何「待补 / 未落地」的陈旧戳记。

- D1 父聚合已落地：`bone-apps/synap-claim/pom.xml` 为 `packaging=pom`，modules 为 `synap-claim-app` / `synap-claim-sdk` / `adjustment-engine`；后两者目前只有一个 `pom.xml`，无 `src`。仓库根 `pom.xml` 只到 `bone-apps`，`bone-apps/pom.xml` 指向 `synap-claim`。
- 决策图现状：`DecisionGraph.canonical()` 固定 5 节点 `EvidenceLoad → RuleCheck → AiAssess → Adjust → Arbitrate`，`MAX_NODES=5`，`node_type` 对外只有 `OCR/RULE/LLM/DECISION` 四类，唯一可跳过节点是 `AiAssess`。落地版 §7.3 同时写着一张 6 行节点表（含 `FraudScreen`）和「7 步链路」，并在表下注明「目标态设计，不是当前实现」；`FraudScreen` 依赖 `GraphFraudPort`、RAG 二期与 05 契约枚举扩展（须与契约 owner 协同），当前代码零实现。
- 理算现状：`domain/model/adjustment/engine/` 13 个类，`AdjustmentEngine` 是约 102 KB 单类；三类给付策略只存在于独立 oracle 参考实现与《理算引擎设计》§11/§14 的目标态描述。生产侧已落地三轨验证：`AdjustmentDualTrackConsistencyTest`（双轨对拍）、`AdjustmentSemanticGapInventoryTest`（缺口哨兵 + 反证测试）、`AdjustmentMutationDiscriminationTest`（19 类变异鉴别）。《理算引擎设计》§14.7 明确要求「oracle 不得并入生产模块」「策略改动靠开关回退，不热修」。
- 沙箱现状：全仓唯一 Java 沙箱是 `bone-engine/.../ExtensionSandbox.java`，它调用 `System.setSecurityManager` 与全局 `Policy.setPolicy`（Java 17 起标记废弃、影响整个 JVM）；`bone-parent/pom.xml` 已把 Aviator 移出选型，`BoneDddArchRules` 有 `noAviatorDependency` 门禁。
- Web 层现状：35 个 Controller 统一返回 `ApiResponse<T>`，但存在 `ApiResponse<List<DecisionTraceNode>>` 一类领域对象直出；`adapter/web/assembler/` 只有 2 个类，synap-claim 侧 `*Resp` 仅 3 个。
- 其它现状：`domain/model/` 下 20 个平铺子域；`AI技术方案.md` 1562 行且版本历史 / 实施日志内联；`scripts/migration/` 130 个文件中 `responsibility` / `liability_element` / 责任要素零命中；诊断报告 §7 把 M2 责任要素 DDL 登记为「L4 禁止且 R1–R8 未拍板」。
- 项目红线：`AGENTS.md` L3（DDL、删码、依赖、CI 脚本需架构师审批）、L4（生产库迁移、密钥证书、发布打 tag、修改 §12 内容完全禁止 AI 执行）、HC-003、ADR-0030 / ADR-0031、分层依赖与 `bone-metadata-sdk` 持久化唯一。
- 用户授权：新建 full change；工作区全量提交并 PUSH 到 `dev`；剩余项按全量执行到底授权，L4 项仍交人工窗口。

## Goals / Non-Goals

**Goals:**

1. 把剩余项一次性规划完，形成可勾选、可验证、可回退的任务清单。
2. 把可安全落地的项变成真实代码与可验证事实，不制造「声明已支持但无读取点」的死配置。
3. 文档与代码双向一致：文档不对以代码为准，代码不对以文档为准；目标态 / 设计态 / 现状三层不得混写。
4. 全量验证通过（单测、12 道提交门禁、文档机检、错误码机检）后全量提交并 PUSH。

**Non-Goals:**

- 不伪造决策节点：不为凑「7 步」添加没有真实处理器的 `FraudScreen` 轨迹。
- 不落 DDL：M2 责任要素表本轮只产出设计与 R1–R8 待拍板清单，不写 `scripts/migration/`。
- 不引入 Aviator，不启用 JVM 全局 `SecurityManager` / `Policy.setPolicy` 作为业务沙箱。
- 不把 `synap-claim-oracle` 并入生产 reactor，不删既有双轨 / 缺口哨兵 / 变异断言。
- 不物理移动 500+ Java 文件做 D4 包路径重构，不改 05 契约枚举，不动其它 3 个未归档 change 的任务。
- 不翻转 `synapclaim-ai-governance-hardening` 的 7 个 BLOCKED 外部前置。

## Decisions

### D-1 决策图：以代码为准，流程层补上，7 步登记为待评审目标态

选择：保留 `canonical()` 的 5 节点内核作为唯一真源，补齐 Stage → FlowFireService → Trigger → Condition 流程层与轨迹响应 DTO；把落地版 §7.3 的「6 行表 / 7 步链路」改写为「现状 5 节点 + Phase B 扩展阶段及前置清单（GraphFraudPort、RAG 二期、05 契约枚举 owner）」。

理由：文档自己声明 7 步是目标态，且节点表只有 6 行、顺序与代码不一致、新增节点依赖尚未落地的能力。按「文档不对以代码为准」与可证伪原则，伪造节点会产出「轨迹有节点但没有真实计算」的假绿，比保持 5 节点更糟。业界实践是编排拓扑由代码单一真源定义、文档只做投影。

备选（否决）：直接把 `MAX_NODES` 改 7 并插入两个占位节点 —— 无处理器、无失败语义、无契约枚举，属典型的假实现。

### D-2 脚本沙箱：声明式条件优先，受限求值器，fail-closed

选择：M5b 的 Condition 默认只允许声明式白名单操作符（比较、集合包含、字段取值）；确需脚本时使用进程内受限求值器，强制超时、步数与可访问标识符白名单，禁止文件 / 网络 / 反射 / 类加载。求值异常或沙箱不可用一律 fail-closed，不写业务状态。

理由：`ExtensionSandbox` 依赖已废弃的 `SecurityManager`，且 `Policy.setPolicy` 是 JVM 全局副作用，放进业务 app 会影响所有租户与定时任务；Aviator 已被项目门禁明确排除。业界实践（规则引擎 / 低代码条件）是「能力白名单 + 资源上限 + 无副作用」，而非事后靠全局安全管理器兜底。

备选（否决）：复用 `ExtensionSandbox`；引入 Aviator / GroovyShell 后靠黑名单过滤。

### D-3 理算引擎：先在 app 内策略化并对拍，再迁模块

选择：先在 `domain/model/adjustment/engine/` 抽出 `LiabilityAdjustmentStrategy` 接口与三策略实现 + 枚举分派，用既有三轨测试锁住金额等价；旧 `AdjustmentEngine` 保留并标 `@Deprecated`，通过开关切回。等价稳定后再把纯函数内核迁到 `adjustment-engine`，`synap-claim-sdk` 只放契约 record / 接口。

理由：《理算引擎设计》§14.7 已规定「策略改动靠开关回退、oracle 不并入生产」，双轨 / 缺口 / 变异三轨是现成的防假绿资产，先把重构放在它们覆盖范围内风险最低。直接搬 102 KB 单类会让所有对拍用例同时换成新坐标，失去「第二轨独立」的意义。

备选（否决）：一次性物理搬模块并同时拆策略；把 oracle 拉进 reactor 做比对。

### D-4 M1 契约与模块边界

选择：`synap-claim-sdk` 只含对外契约（纯 Java record / interface，零框架依赖）；`adjustment-engine` 不依赖 Spring 与 `bone-metadata-sdk`；app 通过契约调用引擎。新增依赖边界机检，逐模块 `mvn -o -pl ... -am test` 验证可独立编译。

理由：六边形架构的端口在内侧、适配器在外侧；契约模块一旦沾上框架依赖，下游任何模块都被迫继承同一技术栈。

### D-5 M2 责任要素 DDL：只设计、不落地

选择：在 design 附录与诊断报告中登记建议表结构、列口径、R1–R8 待拍板项与迁移前置，不写 `scripts/migration/`。

理由：诊断报告 §7 明确其为 L4 禁止项且业务口径未拍板；AGENTS.md 规定 DDL 需架构师审批、生产库迁移完全禁止 AI 执行。用户 Q3 的授权选项本身也写明「L4 项 AI 仍不得执行，实际仍需人工窗口」。

### D-6 D4 包路径：逻辑映射表 + 门禁，不做物理重构

选择：把 20 子域 → 7 上下文的映射写成机器可读单一真源（JSON），新增门禁校验包归属与 `quota` 归 Funds；物理包移动登记为需独立窗口的后续变更。

理由：物理移动 500+ 文件会同时冲击 ArchUnit 规则、domain-model-layout-baseline、机检脚本与并发会话，收益是目录观感，风险是全仓编译与门禁基线。业界实践优先用「可机检的逻辑边界」而非高风险大爆炸重构。

备选（否决）：本轮直接 `git mv` 全部包。

### D-7 响应契约：按端点补 `*Resp` + Assembler，先治直出

选择：先修 `DecisionController` 的领域对象直出并补轨迹响应 DTO，再按机检清单补其余端点的 `*Resp` 与 Assembler；新增响应契约机检防止回退。

理由：HC-003 是硬约束；先修真实违规点再铺开，避免「全面重写 35 个 Controller」式的高风险改动。

### D-8 AI 技术方案瘦身

选择：把 `AI技术方案.md` 内联的版本历史与实施日志外置到 `00-governance/` 下的独立记录文件，正文只留规格、选型、契约与决策；`docs.manifest.json` / `AI-INDEX.md` 登记新文件，`check_all.py` 与 `_check_*.py` 保持全绿。

理由：changelog 与规格混在一处是口径漂移的主要来源；外置后规格可被机检引用，历史可独立归档。

### D-9 文档治理：单一真源 + 机检闭环

选择：所有数字口径、端点状态、节点拓扑、包归属都从代码或单一真源表投影；改完必跑 `check_all.py`、`_check_contract_drift.py`、`_check_ddl.py`、`_check_numbers.py`、错误码 / i18n 机检，并回写诊断报告 §7/§8、整合历史 P38、文档地图。

理由：本仓并发提交频繁，手工同步必然漂移，只有机检能锁住。

### D-10 验证与交付

选择：先 `mvn -o -pl bone-apps/synap-claim/synap-claim-app spotless:apply test`（当前基线 1290 tests / 0 failures），再 `./scripts/check.sh` 12 道门禁，再文档机检与错误码机检；全绿后按用户 Q2 授权全量 `git add -A` 提交并 PUSH 到 `dev`，提交后用 `git branch --contains <sha>` 核对落点。

理由：用户明确要求全量提交并 PUSH；并发提交者已暂存的文件按授权一并纳入，但提交信息中逐类列出，便于事后追溯。

## Risks / Trade-offs

- [7 步链路被降级为待评审目标态，与用户「全量执行」的直觉不符] → 在 Open 产物确认点显式列出该偏差与依据，由用户决定是否要求强制伪造节点；不擅自替用户决定。
- [M2 DDL 无法在本轮落地] → 产出可直接进入架构评审的表设计与 R1–R8 清单，并在最终报告明确「人工窗口」而非声称完成。
- [策略化重构改变赔付金额] → 复用既有三轨测试，任何差异即失败；旧实现保留 + 开关回退，不做热修。
- [沙箱能力不足导致脚本条件被静默放行] → 声明式条件优先；沙箱不可用一律 fail-closed，并加负向探针证明越界脚本确实被阻断。
- [D4 逻辑映射表被当成「已重构」] → 措辞明确写「逻辑边界已机检、物理重构未做」，并在诊断报告按时态三层登记。
- [全量提交混入并发会话未完成增量] → 提交前 `git status` 复核、`git diff --cached --stat` 列出，提交信息分类说明；失败时按提交 SHA 回退，不使用 `--amend` 掩盖。
- [全仓门禁基线因新增模块 / 包映射变动被打破] → 每次改动后跑 `./scripts/check.sh`，新增 baseline 条目必须写明理由与到期，不永久豁免。
- [`AI技术方案.md` 外置历史后引用失效] → `check_all.py` 的交叉引用检查必须先于提交通过。

## Migration Plan

1. **阶段 A（无行为变更）**：文档口径收敛、逻辑映射表、响应契约机检、模块契约骨架。回退：`git revert` 单个提交即可。
2. **阶段 B（可开关的行为变更）**：流程层 + 声明式 Condition、沙箱、策略化引擎。所有新路径默认关闭或由配置显式开启，旧实现保留 `@Deprecated`。
3. **阶段 C（模块搬迁）**：对拍稳定后把纯函数内核迁 `adjustment-engine`，SDK 落契约；依赖边界机检通过才算完成。
4. **阶段 D（人工窗口）**：M2 DDL 与 D4 物理重构、05 契约枚举扩展、`FraudScreen` 真实接入，按架构评审排期单独 change。
5. **回退统一原则**：金额与决策链路的改动按开关切回旧实现，不热修；文档与门禁改动按提交整体 revert。

## Open Questions

以下问题都不影响本轮要构建的内容，留给人工窗口；本设计不对它们作隐含假设。

- M2 责任要素的 R1–R8 业务口径由谁拍板、何时进入架构评审。
- 05 接口契约 `node_type` 枚举是否扩展 `FRAUD`，owner 与版本节奏。
- `GraphFraudPort` / RAG 二期的排期与容量预算，决定 `FraudScreen` 是否成立。
- D4 物理包重构的独立窗口与全仓门禁基线迁移方案。
- M4 双轨 oracle 随生产算法演进的持续同步预算（防双轨假绿）。

## 附录 A：人工窗口执行包（Q3 强制全量项的交付输入）

> 用户 Q3 选择「要求强制全做」。该选项自带约束：L4 项 AI 不得执行，需要架构师审批凭据并另开人工窗口；本次会话未提供凭据。因此以下三项是**可直接进入评审的执行输入**，不是本轮已落地成果，不写成「已完成」。

### A.1 M2 责任要素表（DDL 属 L3/L4，不写 `scripts/migration/`）

| 项 | 建议口径 | 待拍板 |
|---|---|---|
| 表归属 | 责任与理算上下文，按 `claim_id` 关联 `adj_liability` | R1 独立表还是主表 JSON 列 |
| 主键 | `id BIGINT`，与平台 SDK 主键约定一致 | R2 主键生成策略 |
| 租户与审计 | `tenant_id` + 创建/更新审计列，沿用 `TenantAbstractEntity` | R3 是否需要 `deleted` |
| 责任要素 | 建议逐项固定列而非 JSON，避免「声明了不读」的死配置 | R4–R8 要素枚举、可空性、金额精度、版本生效区间、历史复算口径 |
| 迁移 | 走 `scripts/migration/` 新编号 + `EntityTableDdlCoverageTest` 覆盖，禁 Flyway `db/migration/V*` | 架构师审批 |

前置：R1–R8 全部拍板；新增错误码需登记常量、HTTP 映射与双语；双轨 oracle 补对应场景。回退：不执行即无影响；执行后按新增迁移脚本单独 revert。

### A.2 扩展链路真实接入（不伪造节点）

| 前置 | 现状 | 人工窗口动作 |
|---|---|---|
| `GraphFraudPort` 与图计算能力 | 代码零实现 | 先落端口与降级路径，再谈节点 |
| 05 接口契约 `node_type` 枚举 | 仅 4 类 | 与契约 owner 协同扩枚举并回写 delivery/05 |
| RAG / 循证目录（二期） | 未排期 | 容量预算与排期评审 |
| 目标态节点表本身 | 6 行、顺序与 `canonical()` 不一致、混写扩展链路步数 | 先由交付件 owner 定稿唯一拓扑，再改 `MAX_NODES` 与 `canonical()` |

顺序：契约定稿 → 端口实现 → 处理器与失败语义 → 轨迹契约与回归 → 文档投影。禁止先加轨迹占位节点。

### A.3 D4 物理包迁移（需独立窗口）

1. 冻结并发提交，按逻辑映射表逐包 `git mv`，同步 `package` 与 `import`。
2. 更新 `doc/architecture/domain-model-layout-baseline.json`、ArchUnit 规则、全部机检脚本的路径口径与 `SCAN_ROOTS`。
3. `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am clean test` 与 `./scripts/check.sh` 全绿后再合入。
4. 回退：整包 revert，不做跨提交的部分回退。

### A.4 本轮已确认与未确认

- 已确认：proposal 范围、3 份 delta spec、D-1~D-10 决策、28 项任务、direct/direct/off 配置、全量提交并 PUSH 到 `dev`。
- 未确认（留给人工窗口）：架构师审批凭据、R1–R8、05 契约枚举扩展、图能力与 RAG 排期、物理包迁移窗口。
