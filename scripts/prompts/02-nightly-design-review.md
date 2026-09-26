# 任务：Bone 工程夜间设计复核 → 优化 → 实现闭环（每晚 23:01）

> **类型**：定时任务 ｜ **触发**：每晚 23:01 ｜ **前置**：必须先跑过 `01-global-anchor.md`
> **两阶段模型（本版核心）**：
> - **阶段 A（设计）**：复核 → v2 优化稿 → 自审 → **停，等人工卡点**，不写代码
> - **阶段 B（实现）**：仅当人工卡点存在（`approved`）才执行 → 写代码 → 门禁 → 前后联调 → 提交
> **为什么这样切**：① 与 `doc/design/AI-夜间设计复核工作流方案.md` §3.3 的人工卡点一致（v1 提示词把这道卡点删掉了）；② 与需求口径一致——"先把各模块设计完善，再实现代码"；③ 无人值守写代码若与设计稿脱节，返工成本远高于先冻结设计。
> **单晚范围**：默认**每晚处理 2 个模块**（同模块的多份文档算 1 个模块）。合批策略见 §1.6。

---

## 0. 运行前提（任一不满足 → 写运行摘要后退出，**不改任何文件**）

| # | 前提 | 检查方式 | 不满足时 |
|---|------|----------|----------|
| 1 | **工作树洁净** | `git status --porcelain` 输出为空 | 退出 + 告警。**禁止**把他人未提交改动一起提交（当前仓库就有其他 AI 的在途改动） |
| 2 | **不在主干上改** | 当前分支不是 `main` / `master` / `release/*` | 切到 `codex/nightly-<module>-<yyyymmdd>`（不存在则创建） |
| 3 | **锚定文件新鲜** | `doc/design/_global-contracts.yaml` 存在且 `generated_at` ≤ 7 天 | 终止并提示"需先重跑 `01-global-anchor.md`"；**不得**用过期的锚定做判断 |
| 4 | **契约基线可读** | 锚定文件的 `modules` 非空、`hc_semantics` 存在 | 终止并提示重跑锚定 |

**绝对禁止触碰**（违反即视为失败，回滚并记 blocked）：

- `.comet/**`（Comet 状态台账）
- `doc/architecture/Bone-DDD-最终实践方案.md`（尤其 §12 与 §G-1.7 —— AGENTS.md 定义为 L4）
- `doc/architecture/gate-state.json`（门禁状态唯一真源，只能由脚本渲染）
- `bone-init.sql`（DDL 变更属 L3/L4：**只产出变更稿，不落库、不改文件**）
- 任何密钥、证书、`.env`、CI 工作流文件（`.github/workflows/**`）

**L0–L4 分级门禁**（AGENTS.md §三）：实现清单里**每一项都要标级别**。

| 级别 | 含义 | 本任务是否执行 |
|------|------|----------------|
| L0 | 格式化、注释 | 可执行 |
| L1 | 单测、DTO | 可执行（须过 `./scripts/check.sh`） |
| L2 | 业务逻辑、Controller/Handler 方法 | 可执行（须在报告中标注待双人 Review） |
| L3 | DDL、删除既有代码、依赖升级、CI 脚本 | **不执行**：写进报告的「待审批清单」，等架构师 |
| L4 | 生产库迁移、密钥证书、发布打 tag、修改 §12 | **禁止**：只登记，不提议执行 |

## 1. 选模块（唯一真源 = `_global-contracts.yaml`）

1. 读 `doc/design/_global-contracts.yaml`（模块清单、顺序、契约、HC 基线）+ `doc/design/_review-status.yaml`（进度）。
2. **顺序规则**：严格按锚定的 `modules` 数组顺序取（锚定已按依赖拓扑排序）。**夜间任务不维护自己的模块↔设计稿对照表**——v1 自带的那张表与仓库实际不符（`bone-gateway` 指向「1. 控制台与仪表盘…」、`bone-file` 指向「5. 扩展管理…」、`bone-blueprint` 指向 Studio Generator 设计稿，而锚定文件记录这三个模块 `design_doc: null`，`bone-blueprint` 是参考样板）。

### 1.1 参考轮换清单（以锚定文件为准，此为人肉校验基线）

以下为首次跑锚定之前的预期轮换顺序，**锚定文件产出后以其 `modules` 数组为准**。如果锚定顺序与此不同（合理，因为锚定有代码级依赖拓扑分析），以锚定为准。

> 今晚（2026-09-26）开始，从 #1 + #2（bone-gateway + bone-metadata-server）一起执行；如果 gateway 因 `design_doc: null` 从零产出 v1 耗 token 过多，降级为当晚只做 #1，#2 顺延到明晚。

| 轮换序号 | 代码模块 | 预期设计文档 | 备注 |
|---------|---------|-------------|------|
| 1 | bone-gateway | `design_doc: null`（锚定预期） | 网关模块，设计稿可能需从零产出 v1 |
| 2 | bone-metadata-server | doc/design/modules/2. 元数据管理模块详细设计方案.md + 2a + 2b | 主数据服务 |
| 3 | bone-masterdata | doc/design/modules/3. 主数据管理模块详细设计方案.md + 3a | 主数据 |
| 4 | bone-integration | doc/design/modules/4. 集成管理模块详细设计方案.md | 集成能力 |
| 5 | bone-system | doc/design/modules/7. 系统管理模块详细设计方案.md + 7a. 数据字典 | 系统管理 |
| 6 | bone-iam | doc/design/modules/6. IAM账号权限管理模块详细设计方案.md + 6a | 账号权限 |
| 7 | bone-file | `design_doc: null`（锚定预期） | 文件服务，设计稿可能需从零产出 v1 |
| 8 | bone-notification | `design_doc: null`（锚定预期） | 通知服务，无独立设计文档，需从零产出 v1 |
| 9 | bone-blueprint | doc/design/modules/8.Studio Generator 详细设计方案.md（参考样板） | Studio 生成器，锚定可能标记 design_doc 非空但需核对实际引用 |
| 10 | bone-engine | doc/design/modules/9. SmartMeta 引擎模块技术说明.md | SmartMeta 引擎 |

**支撑/框架模块不参与本轮**：`bone-framework`、`bone-core`、`bone-sdk`、`bone-utils`、`bone-web`、`bone-security`、`bone-metadata-sdk`、`bone-openapi-sdk`、`bone-client-sdk`、`bone-metadata-engine`（有 active Comet change）、`bone-architecture-test`。它们的设计归属在各业务模块的集成章节或框架规范中。
3. **选择优先级**：
   1. `needs_revision` / `interrupted` → 同模块续做（A 阶段）
   2. `design_ready` 且人工卡点文件已存在 → 转 `approved` → 进**阶段 B**
   3. `pending` → 进**阶段 A**
   4. `skip` → 重新评估（周末/无设计稿/Comet 占用等条件可能已变化）
   5. 全部 `done` → 输出"本批全部完成"，建议重跑 `01-global-anchor.md` 刷新拓扑
4. **Comet 保护**：锚定中 `has_active_comet_change: true` 或 `comet_change_id` 命中本模块 → 标 `skip`，把 change_id / phase 写进 warnings，取下一个模块。
   > 当前实测：`bone-metadata-engine` 被 classic change `refactor/metadata-engine-boundary-ddd`（phase=design）占用 → skip。注意 `bone-metadata-server` 是**另一个模块**，不受影响。
5. **状态文件自愈**：若 `_review-status.yaml` 的模块清单 / 设计稿路径 / 顺序与锚定不一致 → **以锚定为准重建**（沿用已有 `status`、`last_reviewed_at`、`report_path`），把差异原文写入 warnings。
6. **单晚范围硬规则**：
   - 默认每晚 **2 个模块**，从轮换清单取连续相邻的两个（如 #1 + #2、#3 + #4），**不得跨序跳选**
   - 同一模块的多份设计稿（如 `2.` + `2a.` + `2b.`）算一个模块，**不得拆到两晚**
   - **合批质量门槛**（同时满足才做 2 个，否则降级为 1 个）：
     - 两个模块在锚定 `cross_module_contracts` 中**无相互耦合**（不是互相依赖 / 互发事件 / 共享 DTO）
     - 两个模块的 `design_doc` 总行数预估 < 600 行
     - 两个模块都不是 `design_doc == null`（从零产出 v1 比 review 现有稿更耗 token）
   - **降级条件**（任一触发则当晚只做 1 个模块）：
     - 上述合批质量门槛未满足
     - 第一个模块自审判 `REVISE` 或 `BLOCK`
     - token 预算在第一个模块完成后已用超 60%
   - 禁止因"今晚搞不完"临时换轻模块或跳号
7. `design_doc == null` 的模块（如 `bone-gateway` / `bone-file` / `bone-notification`）：本晚只产出**从零设计稿 v1**（功能 + UI），状态停在 `design_ready`，**不进实现**。

## 2. 阶段 A：设计复核

### 2.1 加载（不可跳过）

- `AGENTS.md` §一（6 条工程约束）
- 锚定文件的 `hc_semantics` + 本模块 `hc_evidence` + `cross_module_contracts`
- HC 的**定义与状态真源**：`doc/architecture/Bone-DDD-最终实践方案.md` §G-1.7；状态另有 `gate-state.json`（只引用，不复写）
- 分层/数据库：`doc/agents/03-架构分层规范.md`、`doc/agents/05-数据库与安全.md`、`doc/architecture/Bone-API-规范.md`
- 本模块全部设计稿 + 代码（`code_roots`）

> **HC 编号纪律**：解释一律引用锚定文件的 `hc_semantics`。**禁止**沿用旧示例里的漂移语义（HC-004 ≠ 入站边界、HC-005 ≠ 租户审计、HC-007 ≠ 微前端契约、HC-008 ≠ 错误码 i18n）。错误码 i18n、微前端契约、入站边界本身都要 review，但它们是**独立维度**，不得借用 HC 编号。

### 2.2 审查维度（每条都要有可复现证据）

**后端**

| # | 维度 | 验证方法 | 对标 |
|---|------|----------|------|
| B1 | 分层依赖 + 入站边界 | `/domain/` 是否 import 外层；`application` 是否 import `infrastructure`；Controller 是否直注 domain service / repository / infrastructure；是否 Handler 与 ApplicationService 套娃 | Evans DDD、ADR-0028、AGENTS.md §一.1/.3 |
| B2 | 持久化唯一（HC-001 / HC-006） | 模块内 ORM import / pom 依赖；`JdbcTemplate` / `SqlSession` 绕过（对照 `doc/architecture/sdk-persistence-bypass-baseline.json`） | HC-001 / HC-006 |
| B3 | 统一响应（HC-003） | Controller 是否返回 `ResponseEntity` 或裸对象；分页是否 `PageResult<T>` | HC-003、Bone-API-规范 |
| B4 | 租户与审计（HC-008 的租户部分） | 业务表是否有 `tenant_id`；实体是否声明 `tenantId`；`@TenantScope` / `*AllTenants` 是否登记；审计字段是否齐 | HC-008、`Bone-多租户规范.md` |
| B5 | 并发安全 | 聚合是否有 `version`；更新是否带版本条件（`WHERE id=? AND version=?`）；有无 `REQUIRES_NEW` 误用 | 乐观锁最佳实践 |
| B6 | 集成事件与 Outbox | 事件类是否实现 `IntegrationEnvelope`；Outbox 是否与业务同本地事务；消费者是否幂等 | E-5.2、消息与事件规范 |
| B7 | 错误码与 i18n 对称 | 错误码是否用常量类（禁裸字符串）；`zh-CN` / `en-US` 是否对称；前缀是否规范 | `scripts/check-i18n-sync.py`、错误码登记 |
| B8 | 可观测性 | 是否有 `HealthIndicator`、指标命名、Actuator 暴露面、关键路径日志（含 `tenantId` / `traceId`） | 可观测性与日志规范 |

**前端**（`bone-frontend/apps/*` 有对应微应用时）

| # | 维度 | 验证方法 | 对标 |
|---|------|----------|------|
| F1 | 微前端契约 | token 注入方式、`BONE_LOCALE_CHANGE` 监听、路由守卫、独立运行 fallback | qiankun 实践、`bone-前端架构.md` |
| F2 | i18n 对称 | `python3 scripts/check-i18n-sync.py`；dayjs locale / utc 插件同步 | `国际化设计方案.md` |
| F3 | 组件与状态 | 列表/表单/弹窗组件用法；空态 / 加载态 / 错误态 / 无权限态是否覆盖 | 组件库既有约定（以仓库实际用法为准，不假定具体 UI 库） |
| F4 | 前后端契约一致 | 页面调用的 API 路径、DTO 字段、错误码是否与锚定 `exposed_apis` / `shared_dtos` 一致 | 锚定文件 |
| F5 | 功能与 UI 完整性 | 对照功能场景稿（`2a/3a/5a/6a/7a`）与 `doc/design/bone-pages-spec.html` 逐页核对 | 本仓库设计稿 |

**跨模块**

- 本模块暴露的 API / 事件 / 共享 DTO 是否与调用方、订阅方一致？（grep 调用方 import 与调用点）
- 锚定文件的 `cross_module_contracts` 中涉及本模块的条目逐条核对；偏离必须进「阻断级」

### 2.3 证据纪律

每条 finding 必须带：**可复现命令 + 输出摘要 + `文件:行号`**。做不到就标 `unknown` 并进 warnings，禁止写"我认为/通常来说"。

## 3. 阶段 A 产物

### 3.1 `doc/design/modules/review-report-<module>.md`

```markdown
# <模块名> 设计方案复核报告
> 执行时间 / 轮换序号 / 对应设计稿 / 锚定版本（generated_at）/ 代码快照（HEAD 短 hash）
> 对标基线：锚定 hc_semantics + <业界对标标准>

## 一、阻断级问题（必须先改设计才能写代码）
| # | 问题 | 违反项（HC 编号 / ADR / 业界实践） | 修复建议 | 证据（命令 + 结果 + 文件:行） |

## 二、建议级优化
| # | 当前设计 | 业界对标 | 优化方案 | 证据 |

## 三、参考级对标（亮点，可酌情采纳）

## 四、优化后的设计改进稿（v2 完整内容）

## 五、实现计划（逐项标 L 级）
### 后端文件清单
- [ ] L2 | <相对路径> | <改动描述>
### 前端文件清单
- [ ] L2 | <相对路径> | <改动描述>
### 待审批清单（L3/L4，本任务不执行）
- [ ] L3 | <DDL / 删码 / 依赖 / CI 脚本> | <申请理由与影响面>
### 联调前置条件
## 六、联调验证结果（阶段 B 才填写）
| # | 场景/API | 验证方法 | 实际结果 | 备注 |
## 七、AI 自审结论（见 §4）
```

### 3.2 v2 优化稿落盘

- 原设计稿存在 → 在其末尾追加 `## v2 优化稿`（**不得覆盖原稿**），内容与报告第四章一致
- `design_doc == null` → 新建 `doc/design/modules/<module>-设计方案.md` 作为 v1（功能 + UI 完整）

### 3.3 更新状态

`_review-status.yaml` → 本模块 `status: design_ready`（写 `last_reviewed_at` 与 `report_path`）；未跑完 → `interrupted`。

## 4. 自审 Gate（同一晚、独立重跑）

**三问**（每问都要独立执行，不复用阶段 A 的中间结果）：

1. **证据可复现？** 逐条重跑 Phase A 的证据命令，不能复现的 finding 降级或删除，并记入自审表。
2. **分级正确？** 有没有该阻断却被写成建议（禁止"为了好看降级"）？有没有误判阻断？
3. **v2 稿安全？** 是否覆盖全部阻断级问题？是否引入新的硬约束违反？API / 事件 / DTO 是否与锚定 `cross_module_contracts` 对齐？实现清单是否遗漏文件或联调前置？

| 判定 | 条件 | 处理 |
|------|------|------|
| ✅ PASS | 阻断级全有方案；v2 无新违反；契约对齐；证据可复现 | 状态 `design_ready` → 进 §5（人工卡点） |
| ⚠️ REVISE | 漏标阻断 / 方案自身违规 / 证据不可复现 | 状态 `needs_revision` → **退出**，下一晚同模块重做 A |
| ❌ BLOCK | 硬约束与现状不可调和（如需改架构决策） | 状态 `blocked` + 写清原因 → **退出**，等人工 |

> 自审结论里**不得**出现 `Active / Manual / Planned` 等门禁状态词（HC 状态只引用 `gate-state.json`）；也不得声称"CI 已拦截"——本地脚本拦截就是本地脚本。

## 5. 人工卡点（强制；未过卡点**绝不**写代码）

- 卡点文件（**任一存在即视为已批准**）：
  - `doc/design/approvals/<module>.yaml`（推荐，与现有的扁平 `modules/*.md` 布局一致）
  - `doc/design/modules/<module>/_review-approved.yaml`（`AI-夜间设计复核工作流方案.md` §3.3 的旧路径；该目录当前不存在，需要时人工创建）
- 不存在 → 输出「设计报告待人工确认」+ 报告路径 + 需确认的三个关键点 → 状态保持 `design_ready` → **退出**
- 周末：A 阶段照常执行（人工可在周末批量批准）；**B 阶段默认不在周六日执行**（无人回滚风险），顺延到工作日
- 批准文件内容建议：`approved_by` / `approved_at` / `report` / `scope`（本次允许实现的清单范围）/ `notes`

## 6. 阶段 B：实现（前置 = 状态 `approved`）

**后端**
1. 严格按报告第五章清单逐文件实现，只做清单内的事
2. `mvn spotless:apply`
3. `./scripts/check.sh`（全量本地门禁）；若本模块涉及 i18n / 契约 / 租户，再跑 `python3 scripts/check-i18n-sync.py`、`bash scripts/contract-lock-check.sh`、`python3 scripts/check-tenant-deletion-coverage.py`
4. 出现**新增**的硬约束违反 → 立即回滚该文件，改为写进报告待审批清单

**前端**
1. `cd bone-frontend && npm run lint && npm run typecheck && npm run build`
2. i18n 键对称 + 微应用注册 + token/locale 注入验证

**禁止**：改 DDL / 依赖 / CI 脚本 / `.comet/**`；删除既有代码（L3）；把未经批准的范围一起改（scope creep）。

**契约同步**：新增 API / 事件 / DTO → **追加**到 `_global-contracts.yaml`（只增不删），并在报告中标注契约变更。

## 7. 联调（可验证性优先，禁止口头"通过"）

1. 启动顺序：`bone-gateway` → 依赖模块 → 本模块（依赖不可用时见第 4 条）
2. 后端：按锚定 `exposed_apis` 逐条调用（curl 或集成测试），记录**实际 HTTP 码 + 响应摘要**
3. 前端：注入 token、切 locale、走路由守卫；确认错误码在 UI 上正确国际化
4. **服务不可用时的降级**：只做契约级验证（单测 + OpenAPI 校验），联调列如实写「未联调（原因）」，**不得**写"通过"
5. 端到端最低通过判据：≥1 条完整场景（登录 → 模块页 → 调用本模块 API → 出错时显示正确 i18n 文案）

## 8. 收尾（每晚固定输出，缺一不可）

1. 更新 `_review-status.yaml`：状态迁移 + `last_reviewed_at` / `implemented_at` / `report_path`
2. **追加** `doc/design/_nightly-log.md` 一行：`日期 | 模块 | 阶段(A/B) | 状态迁移 | 产物路径 | 下一步 | 阻塞`
   > v1 只有"最新状态"，没有时间线；出问题时无法回溯哪晚做了什么。
3. 对话输出运行摘要：选中模块与原因 / 阶段 / 状态迁移 / 产物路径 / 阻断级问题数 / 待审批（L3/L4）条数 / 下一步
4. **提交（仅阶段 B）**：分支 `codex/nightly-<module>-<yyyymmdd>`；commit `[nightly][<module>] <摘要>`；**禁** force-push、**禁**直推 `main` / `release/*`，等人工 PR Review

## 9. 铁律（违反立即停止并输出原因）

1. **绝不无证据下结论**：每条 finding 必须有可复现命令 + 输出 + `文件:行`
2. **绝不自定义 HC 编号语义**：一律引用锚定文件 `hc_semantics`（真源 §G-1.7）
3. **绝不把 `Manual` / `Planned` 写成 `Active`**，绝不声称本地脚本已被 CI 阻断
4. **绝不跳过人工卡点写代码**；卡点文件不存在就只能停
5. **绝不执行 L3 / L4 动作**（DDL、删码、依赖、CI、密钥、生产迁移、§12）
6. **绝不在脏工作树上开工**（他人未提交改动必须被保护）
7. **绝不临时改顺序或跳模块**；合批只按 §1.6 的例外条件
8. **绝不写超出设计稿的实现**：v2 未覆盖的边界 → 回设计阶段补稿
9. **绝不压缩阻断级问题**：有几条写几条；写不完标 `interrupted` 明晚续
10. **绝不触碰** `.comet/**`、`gate-state.json`、`Bone-DDD-最终实践方案.md` §12/§G-1.7、`bone-init.sql`、`.github/workflows/**`

---

## 附：相对 v1 的关键修正（本次优化留痕）

| # | v1 的问题 | v2 的处置 |
|---|-----------|-----------|
| 1 | 删掉了工作流设计稿 §3.3 的**人工卡点**，同一晚自动写代码 | 恢复卡点并做成 A/B 两阶段（§5 / §6）；与需求口径"先完善设计再实现"一致 |
| 2 | HC 编号语义错误（HC-004 当入站边界、HC-005 当租户审计、HC-007 当微前端、HC-008 当 i18n） | 改为只引用锚定 `hc_semantics`；三个维度保留但不再冒充 HC（§2.1 / B1–B8） |
| 3 | 自带模块↔设计稿对照表，与仓库实际错配（gateway/file/blueprint），且漏了「10. 应用与模块管理」 | 轮换改为**以锚定 `modules` 为唯一真源**，并支持状态文件自愈（§1.2–§1.5） |
| 4 | 无 L0–L4 分级，未防 AI 自动做 DDL / 删码 / 依赖升级 | 新增 §0 分级门禁 + 待审批清单（报告第五章） |
| 5 | 未防脏工作树，可能提交他人 WIP | 新增 §0 前提 1 + 铁律 6 |
| 6 | "token 预算 80% 立即停"，而模型无法可靠自测 token | 改为阶段产物 + 时间上限的可观测断点（见 01 §6 与 §8 收尾） |
| 7 | `.comet/current-change.json` 不存在，命令必然失败 | 锚定任务改读 `.comet/state.json` 并容忍缺失（01 §1.4） |
| 8 | 无时间线日志，出问题无法回溯 | 新增 `doc/design/_nightly-log.md` 追加式日志（§8.2） |
