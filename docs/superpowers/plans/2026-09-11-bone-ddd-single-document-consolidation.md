# Bone DDD 单文档整合实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `doc/architecture/ddd/` 的有效内容完整合并进 `doc/architecture/Bone-DDD-最终实践方案.md`，删除分册并修复所有仍生效的引用，使主文档成为唯一、自包含的 DDD 规范入口。

**Architecture:** 主文档继续按“速览 → 原则 P → 工程决策 E → 门禁 G → 附录”组织；分册中的独有内容按职责合并，而不是简单拼接。新增 ADR-0026 推翻 ADR-0024 的拆分决定；历史 ADR 保留原文，并由新 ADR 说明决策演进。

**Tech Stack:** Markdown、Python 3 文档校验脚本、ripgrep、Git。

## Global Constraints

- 已获得 L3 架构师审批，可以删除 `doc/architecture/ddd/` 下 12 个 Markdown 文件。
- 不删除任何规范内容、版本历史、迁移台账或仍有效的示例；重复内容只保留主文档中的一份。
- `08-module-acl-status.md` 作为带日期的历史快照并入附录，不冒充当前实时状态。
- `.specstory/`、`.codebuddy/memory/` 是历史记录，不改写；其他仍生效的仓库文档、源码 Javadoc 和 CI 脚本必须迁移引用。
- 保留旧章节语义的显式 HTML 锚点；删除文件路径本身无法重定向，所有生效引用必须按 Task 1 的锚点映射改到主文档。
- 本次不修改业务实现、架构规则行为或 freeze 基线。

---

### Task 1: 建立单文档决策与锚点映射

**Files:**
- Create: `doc/architecture/adr/0026-ddd-single-document-consolidation.md`
- Modify: `doc/architecture/adr/README.md`
- Modify: `doc/architecture/Bone-DDD-最终实践方案.md`

**Interfaces:**
- Consumes: ADR-0024 的分册决定、用户批准的单文档决定。
- Produces: v5.1.0 文档结构、旧分册章节到主文档锚点的唯一映射。

- [ ] **Step 1: 编写 ADR-0026**

记录以下决定：

1. ADR-0024 的“正文与分册并存”被 ADR-0026 取代。
2. 主文档是唯一规范与实施状态真源。
3. 原分册独有内容并入主文档附录。
4. 历史快照保留日期，不作为实时门禁证明。
5. 删除分册后同步更新源码、文档和脚本引用。

- [ ] **Step 2: 在主文档声明 v5.1.0 与单文档边界**

将顶部版本改为 `5.1.0`，决策链接增加 ADR-0026；删除“专题参考”分册索引，替换为主文档内部目录和锚点。

- [ ] **Step 3: 建立锚点映射**

至少保留：

```text
context-map-业务限界上下文
application-use-case-boundary
cqrs-port-location
reliable-event-publishing
g-1-1-hard-gate
migration-ledger
naming-style
payment-sample
architecture-fitness-dashboard
version-history
```

- [ ] **Step 4: 校验版本和旧决策表述**

Run:

```bash
rg -n "专题参考|ddd/README|v5\\.0\\.2" doc/architecture/Bone-DDD-最终实践方案.md
```

Expected: 不再存在分册入口；`v5.0.2` 只允许出现在版本历史或兼容说明。

---

### Task 2: 合并战略设计与上下文映射

**Files:**
- Modify: `doc/architecture/Bone-DDD-最终实践方案.md`
- Source to merge: `doc/architecture/ddd/context-map.md`

**Interfaces:**
- Consumes: Context Map 的战略分类、上下文关系、数据所有权和技术依赖内容。
- Produces: 主文档 P-2/E-1 中完整可阅读的上下文映射。

- [ ] **Step 1: 将战略分类和业务限界上下文表并入 P-2**

保留 Metadata 核心域、支撑域表、业务术语、模块路径和 `ic_*` 归属说明。

- [ ] **Step 2: 将上下文关系图和上下游契约表并入 P-2.3**

明确 OHS、ACL、Conformist、Shared Kernel 与 Platform Kernel 的区别。

- [ ] **Step 3: 将数据所有权模板和演进触发条件并入 E-1**

保留表唯一写 Owner、跨上下文 Join 禁止、分析数据产品和 Context Map 复核触发条件。

- [ ] **Step 4: 检查内容完整性**

Run:

```bash
rg -n "Platform Kernel|Conformist|数据所有权|演进检查" doc/architecture/Bone-DDD-最终实践方案.md
```

Expected: 四类内容均在主文档出现且仅有一处规范定义。

---

### Task 3: 合并应用架构、可靠性和命名规则

**Files:**
- Modify: `doc/architecture/Bone-DDD-最终实践方案.md`
- Source to merge: `doc/architecture/ddd/application-and-consistency.md`
- Source to merge: `doc/architecture/ddd/naming-style.md`

**Interfaces:**
- Consumes: 用例入口表、事务代理约束、可靠性矩阵、Envelope 字段、并发矩阵、命名表。
- Produces: 完整的 E-3～E-7 和 E-13。

- [ ] **Step 1: 扩充 E-3 应用边界**

合并入口构件表、禁止套娃清单、Spring 代理/`TransactionTemplate` 限制和 `*UseCase` 的 Bone 命名政策。

- [ ] **Step 2: 扩充 E-4/E-5**

合并读写流程、`oneAggregatePerTransaction()` 能力边界、可靠性最低要求、Integration Event Envelope 字段和并发策略必测行为。

- [ ] **Step 3: 收紧可靠发布契约**

在 E-5.2 明确：

```text
Durable：业务写与发布记录同事务；失败向上传播；成功交接后才清事件。
Best-effort：允许丢失；必须显式命名/配置，并记录日志和指标。
```

禁止仅凭 `publishFrom()` 方法名推断可靠性。

- [ ] **Step 4: 收紧 Domain Gateway IO 边界**

在 E-4.3/E-5.1 明确远程 IO 不隐藏在聚合方法中；超时、重试、熔断和事务范围由应用用例或 Orchestrator 控制。

- [ ] **Step 5: 完整合并命名表**

将构件后缀和互斥语义表并入 E-13，确保 `Command/Qry/Query/Req/Resp/PO/Repository/QueryPort/Gateway/Adapter` 均有唯一含义。

- [ ] **Step 6: 检查关键规则**

Run:

```bash
rg -n "Durable|Best-effort|TransactionTemplate|Integration Event Envelope|后缀语义" doc/architecture/Bone-DDD-最终实践方案.md
```

Expected: 每个主题均有明确规范，不依赖分册解释。

---

### Task 4: 合并门禁、迁移台账与历史快照

**Files:**
- Modify: `doc/architecture/Bone-DDD-最终实践方案.md`
- Source to merge: `doc/architecture/ddd/enforcement.md`
- Source to merge: `doc/architecture/ddd/migration-ledger.md`
- Source to merge: `doc/architecture/ddd/08-module-acl-status.md`

**Interfaces:**
- Consumes: Active/Frozen/Planned 矩阵、规则能力边界、freeze 台账、迁移优先级。
- Produces: 主文档 G 部分的完整实施状态和迁移附录。

- [ ] **Step 1: 扩充 G-1**

合并 Hard gate 12 行矩阵、规则“能证明/不能证明”表和逐模块启用状态。

- [ ] **Step 2: 扩充 G-2/G-3**

合并 TEST-HYGIENE-01 限制、freeze-ledger 字段、例外治理和新规则准入步骤。

- [ ] **Step 3: 新增“附录 A：迁移台账”**

完整合并 v5.0/v4.x 迁移项、模块命名基线、P0/P1/P2 优先级和关闭定义。

- [ ] **Step 4: 新增“附录 B：历史实施快照”**

合并 2026-05/08 ACL 与 ArchitectureTest 状态；标题和首段明确“历史快照，不代表当前 CI”。

- [ ] **Step 5: 校验实施状态不被夸大**

Run:

```bash
rg -n "Active|Frozen|Planned|历史快照|不能证明|关闭定义" doc/architecture/Bone-DDD-最终实践方案.md
```

Expected: 所有术语和免责声明可直接从主文档定位。

---

### Task 5: 合并入门、示例和版本历史

**Files:**
- Modify: `doc/architecture/Bone-DDD-最终实践方案.md`
- Source to merge: `doc/architecture/ddd/quick-start.md`
- Source to merge: `doc/architecture/ddd/public-overview.md`
- Source to merge: `doc/architecture/ddd/07-supplements.md`
- Source to merge: `doc/architecture/ddd/samples/payment.md`
- Source to merge: `doc/architecture/ddd/CHANGELOG.md`

**Interfaces:**
- Consumes: 新人路径、外部概览、代码样板、支付样板和完整版本历史。
- Produces: 主文档附录 C～F。

- [ ] **Step 1: 新增“附录 C：团队快速入门”**

保留四句话、10 分钟阅读路径、首个读写用例清单、提交前检查和分歧处理顺序；内部链接改为主文档锚点。

- [ ] **Step 2: 新增“附录 D：代码与支付样板”**

保留 Controller→Handler→Aggregate、值对象、ACL、查询、支付状态机、回调幂等、验签、并发兜底和超时任务。

- [ ] **Step 3: 新增“附录 E：架构适应度仪表盘”**

保留指标、统计口径和季度复核要求。

- [ ] **Step 4: 新增“附录 F：版本历史”**

完整保留 v4.5～v5.0.2，新增 v5.1.0 单文档整合记录；修正不存在的 ADR-0026 引用为本次新建 ADR。

- [ ] **Step 5: 保留外部推广摘要**

将 `public-overview.md` 的“解决什么问题”和“可复用经验”放入主文档开头或附录 C，重复原则不再复制。

---

### Task 6: 迁移生效引用和校验脚本

**Files:**
- Modify: `AGENTS.md`
- Modify: `doc/architecture/README.md`
- Modify: `doc/glossary.md`
- Modify: `bone-framework/bone-architecture-test/README.md`
- Modify: `doc/architecture/Bone-API-规范.md`
- Modify: `doc/architecture/adr/0013-extension-studio-repository-read-side.md`
- Modify: `doc/architecture/adr/0020-anti-anemia-weak-form-strong-behavior.md`
- Modify: `doc/architecture/adr/0023-core-domain-smart-metadata.md`
- Modify: `doc/architecture/adr/0024-ddd-v5-rule-semantics-and-document-split.md`
- Modify: `doc/architecture/adr/0025-ddd-v5-0-2-implementation-alignment.md`
- Modify: `doc/design/modules/README.md`
- Modify: `doc/design/modules/1. 控制台与仪表盘模块详细设计方案.md`
- Modify: `doc/design/modules/2. 元数据管理模块详细设计方案.md`
- Modify: `doc/design/modules/3. 主数据管理模块详细设计方案.md`
- Modify: `doc/design/modules/4. 集成管理模块详细设计方案.md`
- Modify: `doc/design/modules/6. IAM账号权限管理模块详细设计方案.md`
- Modify: `doc/design/modules/8.Studio Generator 详细设计方案.md`
- Modify: `doc/design/BONE-X-Studio-详细设计方案.md`
- Modify: `scripts/ci/check-ddd-doc-code-sync.py`
- Modify: `scripts/check-ddd-doc-drift.py`
- Modify: `bone-blueprint/src/main/java/com/bone/blueprint/application/command/handler/HandlePaymentCallbackCommandHandler.java`

**Interfaces:**
- Consumes: Task 1 的主文档锚点。
- Produces: 不再引用待删除分册的生效文档、源码和脚本。

- [ ] **Step 1: 批量替换规范引用**

所有现行规范引用按主题改成 Task 1 声明的主文档锚点，例如：

```text
doc/architecture/Bone-DDD-最终实践方案.md#cqrs-port-location
doc/architecture/Bone-DDD-最终实践方案.md#g-1-1-hard-gate
doc/architecture/Bone-DDD-最终实践方案.md#payment-sample
```

ADR-0024 保留历史决定文字，但增加“已被 ADR-0026 取代”状态和链接。

- [ ] **Step 2: 更新文档校验脚本**

将 `doc/architecture/ddd/*.md` 扫描改为仅扫描：

```text
doc/architecture/Bone-DDD-最终实践方案.md
```

- [ ] **Step 3: 更新支付样板 Javadoc**

将 `ddd/samples/payment.md §3` 改到主文档 `#payment-sample-signature`。

- [ ] **Step 4: 扫描剩余生效引用**

Run:

```bash
rg -n "doc/architecture/ddd/|\\./ddd/|\\.\\./ddd/" \
  --glob '*.md' --glob '*.java' --glob '*.py' \
  --glob '!.specstory/**' --glob '!.codebuddy/memory/**' \
  --glob '!docs/superpowers/**' .
```

Expected: 无生效引用；仅允许本计划中的历史路径说明。

---

### Task 7: 删除分册并完成验证

**Files:**
- Delete: `doc/architecture/ddd/README.md`
- Delete: `doc/architecture/ddd/context-map.md`
- Delete: `doc/architecture/ddd/application-and-consistency.md`
- Delete: `doc/architecture/ddd/enforcement.md`
- Delete: `doc/architecture/ddd/migration-ledger.md`
- Delete: `doc/architecture/ddd/naming-style.md`
- Delete: `doc/architecture/ddd/quick-start.md`
- Delete: `doc/architecture/ddd/public-overview.md`
- Delete: `doc/architecture/ddd/CHANGELOG.md`
- Delete: `doc/architecture/ddd/07-supplements.md`
- Delete: `doc/architecture/ddd/08-module-acl-status.md`
- Delete: `doc/architecture/ddd/samples/payment.md`

**Interfaces:**
- Consumes: 已合并正文和已迁移引用。
- Produces: 单一完整 DDD 主文档。

- [ ] **Step 1: 内容指纹核对后删除文件**

删除前逐个确认 Task 2～5 对应的独有标题或关键词已出现在主文档。

- [ ] **Step 2: 运行 DDD 文档校验**

Run:

```bash
python3 scripts/ci/check-ddd-doc-code-sync.py
python3 scripts/check-ddd-doc-drift.py
```

Expected: 两个脚本均退出 0。

- [ ] **Step 3: 运行链接和空白检查**

Run:

```bash
git diff --check
rg -n "doc/architecture/ddd/|\\./ddd/|\\.\\./ddd/" \
  --glob '*.md' --glob '*.java' --glob '*.py' \
  --glob '!.specstory/**' --glob '!.codebuddy/memory/**' \
  --glob '!docs/superpowers/**' .
```

Expected: `git diff --check` 退出 0；第二条只命中本计划中的历史说明。

- [ ] **Step 4: 核对删除范围**

Run:

```bash
git status --short -- doc/architecture/ddd doc/architecture/Bone-DDD-最终实践方案.md doc/architecture/adr scripts AGENTS.md
```

Expected: 12 个分册删除、主文档/ADR/引用/脚本修改，无业务实现文件变化（支付 Javadoc 除外）。

- [ ] **Step 5: 最终内容审查**

确认主文档可以单独回答：

1. Bone 的上下文和核心域是什么；
2. 写侧、读侧、事务和事件如何实现；
3. 包结构与端口如何放置；
4. 哪些规则 Active/Frozen/Planned；
5. 存量如何迁移；
6. 新成员如何开始；
7. 支付/回调样板如何落地；
8. 规范如何演进。
