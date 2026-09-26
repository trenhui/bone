# 任务：Bone 工程全局设计拓扑锚定（一次性 / 可按需刷新）

> **类型**：手动执行，可重复执行（幂等）｜**产出**：`doc/design/_global-contracts.yaml` + `doc/design/_review-status.yaml`
> **用途**：成为 `02-nightly-design-review.md` 的**唯一模块清单与契约基线真源**——夜间任务不再自带模块↔设计稿对照表
> **刷新触发**：`generated_at` 超过 7 天 ／ `doc/design/**` 有新增或改名 ／ 锚定文件的模块清单与目录不一致 ／ 主分支发生结构性重构

---

## 0. 不可违反（违反立即停止，输出原因后退出）

1. **唯一写面**：只写 `doc/design/_global-contracts.yaml` 与 `doc/design/_review-status.yaml`。代码、`doc/architecture/**`、`.comet/**`、`bone-init.sql` 一律只读。
2. **禁止复写门禁状态**：HC 与门禁的**定义与实测状态**唯一真源是 `doc/architecture/Bone-DDD-最终实践方案.md` §G-1.7（锚点 `#hc-hard-constraints`）与 `doc/architecture/gate-state.json`（由脚本渲染，不得手改）。锚定文件只写**编号 + 本模块判据 + 代码证据**，**不得**出现 `Active / Manual / Planned / Frozen` 这类状态词。
3. **代码为准**：设计稿与代码冲突时以代码实测为准，并把冲突原文与位置写入 `warnings`。
4. **不确定就标 `unknown`**：无法验证的字段写 `unknown` 并记 warning，禁止猜测、禁止从设计稿抄。
5. **禁止修改 §12 与 §G-1.7 内容**（AGENTS.md L4：完全禁止 AI 执行）。

---

## 1. 加载基线（严格按序）

1. `AGENTS.md` §一 —— 6 条不可违反的工程约束（分层依赖 / 持久化唯一 / 入站边界 / 统一响应 / 租户与审计 / 格式）。
   > **注意**：AGENTS.md 是薄引用入口，**不含** HC 表；不要试图在它里面找 HC 定义。
2. `doc/architecture/Bone-DDD-最终实践方案.md`：
   - §G-1.7 的 HC-001~HC-008 **定义**（锚点 `#hc-hard-constraints`）→ 抽取为本产出的 `hc_semantics` 段（**只写判据，不写状态**）
   - 分层与 DDD 基线（E-2 / E-4.1 / G-1.1）
3. `doc/agents/03-架构分层规范.md` + `doc/agents/05-数据库与安全.md`（后端判据）；`doc/architecture/Bone-API-规范.md`（入站契约）
4. **Comet / OpenSpec 现状（容忍缺失，缺失本身记 warning）**：
   - `.comet/config.yaml`、`.comet/state.json`（**不是** `current-change.json` —— 该文件在当前仓库中不存在）、`.comet/batches/*.json`
   - `openspec/changes/**`（含 `archive/`）
   - 三方不一致（例：batch item 仍 `pending` 但 openspec 已归档）必须记入 `warnings`
5. `git status --porcelain` 与当前分支 —— **只记录不修改**；工作树不干净时写入 `warnings`（夜间任务据此决定是否开工）

## 2. 枚举设计文档与模块

1. `doc/design/modules/**/*.md`（含 `README.md`、`2. 元数据管理模块-生产就绪优化计划(P0·P1).md` 这类计划稿）
2. `doc/design/*.md`（如 `BONE-X-Studio-详细设计方案.md`、`国际化设计方案.md`、`AI-夜间设计复核工作流方案.md`）→ 按 `doc_kind` 标注为 `cross-cutting | process`，**不混入模块轮换**
3. **模块以代码目录为锚**：`bone-platform/*`、`bone-engine/*`、`bone-framework/*`、`bone-frontend/apps/*`；设计稿只是模块的属性：
   - 一个模块可对应**多份**设计稿（例：`bone-metadata-server` ↔ `2.` + `2a.` + `2b.` + `元数据能力-实现映射与竞品对照.md`）
   - **无设计稿的模块必须显式登记 `design_doc: null`**，不得用别的模块的设计稿顶替（当前已知：`bone-gateway`、`bone-file`、`bone-notification`）
   - 参考样板（`bone-blueprint` 等）登记 `role: "reference-sample"`，**不进轮换**
   - 每份设计稿登记 `doc_kind: module-design | plan | cross-cutting | process` 与 `lines`（`wc -l` 实测）

## 3. 交叉验证（必须用代码，禁止抄设计稿）

逐模块提取并验证：

- **聚合根**：`@Table("<table>")` 实体 + 基类（`AggregateRoot` / `TenantAggregateRoot` / `TenantAbstractEntity` / `Entity`）
- **Repository**：`interface .*Repository` 的方法签名
- **对外 API**：`@RequestMapping` / `@GetMapping` 等，路径以 grep 结果为准；同时标注「代码有、设计稿无」与「设计稿有、代码无」
- **事件**：`IntegrationEnvelope` / `IntegrationEvent` 实现类
- **错误码**：常量类名与前缀（裸字符串要显式标出）
- **表与租户**：本模块表清单（来自 `bone-init.sql`）+ 实体租户声明；可直接复用 `scripts/check-tenant-entity-declaration.py` 的输出
- **HC 逐条只写代码证据**（取值仅 `implemented | violated | partial | unknown | n/a`；`n/a` 用于该模块不存在该层/该入口的情形，如纯前端模块无 Controller）：

| 编号 | 判据（写进 `hc_semantics`，全文只解释一次） | 取证方式 |
|------|------|------|
| HC-001 | 禁止引入 MyBatis-Plus / JPA / Hibernate / MyBatis | 模块 `src/main` 的 ORM import + pom 依赖 |
| HC-002 | `domain` 不依赖 adapter / application / infrastructure | `/domain/` 下 import 外层包 |
| HC-003 | Controller 返回 `ApiResponse<T>` / `PageResult<T>` | Controller 是否返回 `ResponseEntity` / 裸领域对象 |
| HC-004 | 禁止硬编码密钥 / 密码 / Token | `scripts/scan-secrets.sh` 或 gitleaks 结果 |
| HC-005 | 核心模块测试覆盖率门槛 | 模块 pom 的 `jacoco.minimum.coverage` 实测值 |
| HC-006 | 数据库访问必须走 `bone-metadata-sdk` Repository | `JdbcTemplate / NamedParameterJdbcTemplate / SqlSession`，对照 `doc/architecture/sdk-persistence-bypass-baseline.json` |
| HC-007 | PR 的 OpenAPI spec 不得引入 breaking change | 本模块 spec 是否存在（`doc/architecture/openapi/**`） |
| HC-008 | 新增表必须含 `tenant_id` + `created_at` + `updated_at` + `deleted` | 本模块表在 `doc/architecture/ddl-required-columns-baseline.json` 的分类 |

> **反例警戒**：`AGENTS.md` §一 与 `doc/design/AI-夜间设计复核工作流方案.md` §4.3 的示例存在 HC 编号语义漂移（把 HC-004 当入站边界、HC-005 当租户审计、HC-007 当微前端契约、HC-008 当错误码 i18n）。**一律以 §G-1.7 真源为准**，并把该漂移记入 `warnings` 提醒人工修正示例。

## 4. 产出 1：`doc/design/_global-contracts.yaml`

```yaml
generated_at: "<ISO 时间戳>"
generator: "01-global-anchor v2"
git: { branch: "<当前分支>", head: "<短 hash>", dirty: true|false }
hard_constraints_ref: "doc/architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints"
gate_state_ref: "doc/architecture/gate-state.json"   # 状态真源，只引用不复写

# 只解释一次，后续全部引用本段（夜间任务不得自行解释 HC 编号）
hc_semantics:
  HC-001: "禁止引入 MyBatis-Plus / JPA / Hibernate / MyBatis"
  HC-002: "domain 层不依赖 adapter / application / infrastructure"
  HC-003: "Controller 必须返回 ApiResponse<T> / PageResult<T>"
  HC-004: "禁止硬编码密钥 / 密码 / Token"
  HC-005: "核心模块测试覆盖率门槛（父 POM 默认 + 模块覆盖）"
  HC-006: "数据库访问必须通过 bone-metadata-sdk Repository"
  HC-007: "PR 的 OpenAPI spec 不得引入 breaking change"
  HC-008: "新增表必须含 tenant_id + created_at + updated_at + deleted"

modules:
  - name: "<bone-xxx>"
    role: "product | reference-sample"
    code_roots: ["bone-platform/bone-xxx"]
    design_doc: "<主设计稿路径；无则 null>"
    design_docs: ["<多份设计稿全列>"]
    doc_kind: "module-design"
    lines: 0
    port: 0
    dependencies: []
    aggregates: []
    repositories: []
    exposed_apis: []          # 实测路径；设计稿未记载的用 "(code-only)" 标注
    published_events: []
    subscribed_events: []
    shared_dtos: []
    error_codes_used: []
    tables: ["<本模块涉及的 bone-init.sql 表名>"]
    # 字段名沿用既有文件（hc_coverage）；口径 = 本模块**代码判据**，与 gate-state.json 的门禁状态无关
    hc_coverage: { HC-001: "implemented|violated|partial|unknown|n/a" }
    hc_evidence: { HC-003: "<可复现的 grep 命令 + 结果摘要>" }
    design_vs_code_gaps: []
    has_active_comet_change: false
    comet_change_id: null

cross_module_contracts:
  - between: ["<模块A>", "<模块B>"]
    api_contract: "<调用关系与路径>"
    shared_dtos: []
    constraint_risk: "<硬约束风险；无则 null>"

comet_active_changes:
  - workflow: "classic|native"
    change_id: "<id>"
    module: "<归属模块>"
    phase: "<design|build|...>"
    skip_design_review: true

warnings: []   # 每条写清「事实 + 位置 + 建议动作」
```

## 5. 产出 2：`doc/design/_review-status.yaml`（派生 + 幂等）

**规则**：

1. 模块清单与顺序**以本次产出的 `modules` 数组为准**（顺序 = 依赖拓扑：被依赖方在前，前端 shell 最后）；夜间任务不再维护自己的对照表。
2. 文件已存在时：按 `name` 对齐，**保留** `status` / `last_reviewed_at` / `implemented_at` / `report_path`；新模块补 `pending`；消失的模块移入 `removed_modules` 并写原因。
3. **清单不一致时以锚定为准重建**，并把差异原文写进 `warnings`。
   > 当前仓库已存在这种漂移：`_review-status.yaml` 把 `bone-gateway` 映射到「1. 控制台与仪表盘…」，而锚定文件记录 `bone-gateway` / `bone-file` / `bone-notification` 在 `doc/design/modules/` 下**无设计稿**（`design_doc: null`）；`doc/design/modules/10. 应用与模块管理详细设计方案.md` 也未被任何轮换表覆盖。
4. **状态取值（9 个，不得自创）**：`pending | reviewing | design_ready | approved | implementing | done | interrupted | skip | blocked`
   - `design_ready` = review + 自审通过，**等人工卡点**
   - `approved` = 人工卡点文件已存在，可进入实现
   - `skip` = 本晚按规则主动跳过（周末、无设计稿、Comet 占用等），**下一晚必须重新评估**
5. 禁止"默认成功"推断：只有产物文件真实存在且自审 PASS 才能写 `design_ready`。

## 6. 预算与断点（用可观测检查点，不要自称 token 百分比）

- 模型无法可靠自测 token 占用 → 一律以**阶段产物是否落盘**为断点：
  - 模块级增量落盘：每完成一个模块的交叉验证就写一次 YAML（保持合法 YAML），避免长任务全丢
  - 到时间上限（建议 45 分钟）仍有余量模块 → 落盘已完成部分，`warnings` 记 `incomplete: <已完成>/<总数>`，下次重跑续写
- 重跑**幂等**：以 `generated_at` 覆盖，保留 `warnings` 中未解决项，不重复已解决的告警。
- 收尾固定输出：模块总数、有设计稿数、无设计稿模块、HC `violated` / `unknown` 计数、Comet 占用模块、warnings 条数。
