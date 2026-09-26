# 任务：Bone 工程夜间设计复核 → 实现 → 联调 → 验收闭环（每晚 23:01）

> **类型**：定时任务 ｜ **触发**：每晚 23:01 ｜ **前置**：必须先跑过 `01-global-anchor.md`
> **六段闭环模型（本版核心）**：
> **A 设计**（复核 → v2 优化稿） → **A' 方案自审**（PASS 才继续） → **B 实现**（写代码） → **B' 代码复核**（diff 级复核） → **C 联调**（真实 HTTP 码 + 响应摘要） → **D 验收**（构造测试数据 + 模拟人工操作走功能） → 收尾提交
> **为什么改成闭环**：v2 把流水线切成「A 设计 / B 实现」并加了一道**强制人工卡点**与「周末不做 B」的日历闸门。无人值守场景下这道闸门等于永久停在 A：设计稿永远变不成代码，也就无从验证设计对不对。正确做法是——**用段内判据与两道自审承担风险控制，而不是用日历与人肉卡点承担**。人工保留**显式叫停权**（§5），但默认放行。
> **单晚范围**：默认**每晚处理 2 个模块**（同模块的多份文档算 1 个模块）。合批策略见 §1.6。走不完就标 `interrupted` 明晚续，**不得**为了赶进度砍段（尤其不得跳过 B' / C / D）。

---

## 0. 运行前提（任一不满足 → 写运行摘要后退出，**不改任何文件**）

| # | 前提 | 检查方式 | 不满足时 |
|---|------|----------|----------|
| 1 | **工作树洁净** | `git status --porcelain` 输出为空 | 退出 + 告警。**禁止**把他人未提交改动一起提交（当前仓库就有其他 AI 的在途改动） |
| 2 | **不在主干上改** | 当前分支不是 `main` / `master` / `release/*` | 切到 `codex/nightly-<module>-<yyyymmdd>`（不存在则创建） |
| 3 | **锚定文件新鲜** | `doc/design/_global-contracts.yaml` 存在且 `generated_at` ≤ 7 天 | 终止并提示"需先重跑 `01-global-anchor.md`"；**不得**用过期的锚定做判断 |
| 4 | **契约基线可读** | 锚定文件的 `modules` 非空、`hc_semantics` 存在 | 终止并提示重跑锚定 |
| 5 | **无显式叫停** | `doc/design/approvals/<module>.yaml` 里 `decision` 不是 `hold` / `block` | 停在对应段，把原因写进报告与状态文件（见 §5） |

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
| L2 | 业务逻辑、Controller / ApplicationService 方法 | 可执行（须在报告中标注待双人 Review） |
| L3 | DDL、删除既有代码、依赖升级、CI 脚本 | **不执行**：写进报告的「待审批清单」，等架构师 |
| L4 | 生产库迁移、密钥证书、发布打 tag、修改 §12 | **禁止**：只登记，不提议执行 |

## 1. 选模块（唯一真源 = `_global-contracts.yaml`）

1. 读 `doc/design/_global-contracts.yaml`（模块清单、顺序、契约、HC 基线）+ `doc/design/_review-status.yaml`（进度）。
2. **顺序规则**：严格按锚定的 `modules` 数组顺序取（锚定已按依赖拓扑排序）。**夜间任务不维护自己的模块↔设计稿对照表**——v1 自带的那张表与仓库实际不符（`bone-gateway` 指向「1. 控制台与仪表盘…」、`bone-file` 指向「5. 扩展管理…」、`bone-blueprint` 指向 Studio Generator 设计稿，而锚定文件记录这三个模块 `design_doc: null`，`bone-blueprint` 是参考样板）。

### 1.1 参考轮换清单（以锚定文件为准，此为人肉校验基线）

以下为首次跑锚定之前的预期轮换顺序，**锚定文件产出后以其 `modules` 数组为准**。如果锚定顺序与此不同（合理，因为锚定有代码级依赖拓扑分析），以锚定为准。

| 轮换序号 | 代码模块 | 预期设计文档 | 备注 |
|---------|---------|-------------|------|
| 1 | bone-iam | `6. IAM…` + `6a.` + `10. 应用与模块管理` | 2026-09-26 已完成 A / A'，B 段待续 |
| 2 | bone-metadata-server | `2.` + `2a` + `2b` | 主数据服务 |
| 3 | bone-masterdata | `3.` + `3a` | 主数据 |
| 4 | bone-integration | `4.` | 集成能力 |
| 5 | bone-system | `7.` + `7a.` + `1. 控制台` | 系统管理 |
| 6 | bone-extension-studio | `5.` + `5a.` | 扩展管理 |
| 7 | studio-generator | `8.Studio Generator` | Studio 生成器 |
| 8 | bone-file | `design_doc: null` | 文件服务，需从零产出 v1 |
| 9 | bone-notification | `design_doc: null` | 通知服务，需从零产出 v1 |
| 10 | bone-gateway | `design_doc: null` | 网关，需从零产出 v1 |
| 11 | bone-frontend/bone-shell | `1. 控制台…` + `bone-pages-spec.html` | 宿主应用单独一轮 |

**支撑/框架模块不参与本轮**：`bone-framework`、`bone-core`、`bone-sdk`、`bone-utils`、`bone-web`、`bone-security`、`bone-metadata-sdk`、`bone-openapi-sdk`、`bone-client-sdk`、`bone-metadata-engine`（有 active Comet change）、`bone-architecture-test`。它们的设计归属在各业务模块的集成章节或框架规范中。

3. **选择优先级**：
   1. `needs_revision` / `interrupted` → 同模块续做（从断掉的那一**段**继续：A / A' / B / B' / C / D）
   2. `design_ready` → 进 **B 实现**（默认放行，见 §5）
   3. `pending` → 进 **A 设计**
   4. `implementing` → 从 **B' 或 C** 续（看报告第六/七章是否已填）
   5. `skip` → 重新评估（Comet 占用等条件可能已变化）
   6. 全部 `done` → 输出"本批全部完成"，建议重跑 `01-global-anchor.md` 刷新拓扑
4. **Comet 保护**：锚定中 `has_active_comet_change: true` 或 `comet_change_id` 命中本模块 → 标 `skip`，把 change_id / phase 写进 warnings，取下一个模块。
   > `bone-metadata-engine` 被 classic change `refactor/metadata-engine-boundary-ddd`（phase=design）占用 → skip。注意 `bone-metadata-server` 是**另一个模块**，不受影响。
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
     - 第一个模块走完 D 段后 token / 时间预算已用超 60%
   - 禁止因"今晚搞不完"临时换轻模块或跳号
7. `design_doc == null` 的模块（如 `bone-gateway` / `bone-file` / `bone-notification`）：本晚只产出**从零设计稿 v1**（功能 + UI），状态停在 `design_ready`，**不进实现**（无既有稿可对照，实现风险过高，需人工先看稿）。

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
| B4 | 租户与审计 | 业务表是否有 `tenant_id`；实体是否声明 `tenantId`；`@TenantScope` / `*AllTenants` 是否登记；审计字段是否齐 | `Bone-多租户规范.md` |
| B5 | 并发安全 | 聚合是否有 `version`；更新是否带版本条件；有无 `REQUIRES_NEW` 误用；计数类校验是否存在 lost-update | 乐观锁最佳实践 |
| B6 | 集成事件与 Outbox | 事件类是否实现 `IntegrationEnvelope`；Outbox 是否与业务同本地事务；消费者是否幂等 | E-5.2、消息与事件规范 |
| B7 | 错误码与 i18n | 错误码是否用常量类（禁裸字符串）；错误码文案是否外提；裸 message 异常是否绕过码表 | `scripts/check-i18n-sync.py`、错误码登记 |
| B8 | 可观测性 | 是否有真实 `HealthIndicator`、指标埋点、Actuator 暴露面（dev/prod 是否一致）、关键路径日志（含 `tenantId` / `traceId`） | 可观测性与日志规范 |

**前端**（`bone-frontend/apps/*` 有对应微应用时）

| # | 维度 | 验证方法 | 对标 |
|---|------|----------|------|
| F1 | 微前端契约 | token 注入、locale 订阅、lifecycle 是否单一真源、独立运行 fallback、路由守卫 / 权限码 | qiankun 实践、`bone-前端架构.md`、`国际化设计方案.md` |
| F2 | i18n 与 locale 管道 | `python3 scripts/check-i18n-sync.py`；dayjs locale / utc 插件是否同步；locale 是否被硬编码覆盖 | `国际化设计方案.md` |
| F3 | 组件与状态 | 列表/表单/弹窗组件用法；空态 / 加载态 / 错误态 / 无权限态是否覆盖 | 组件库既有约定 |
| F4 | 前后端契约一致 | API 路径、DTO 字段、**ID 精度（Long→string，禁 `Number()`）**、错误码是否与锚定 `exposed_apis` / `shared_dtos` 一致 | 锚定文件 |
| F5 | 功能与 UI 完整性 | 对照功能场景稿（`2a/3a/5a/6a/7a`）与 `doc/design/bone-pages-spec.html` 逐页核对；后端已就绪但前端无页面的能力缺口 | 本仓库设计稿 |

**跨模块**

- 本模块暴露的 API / 事件 / 共享 DTO 是否与调用方、订阅方一致？（grep 调用方 import 与调用点）
- 锚定文件的 `cross_module_contracts` 中涉及本模块的条目逐条核对；偏离必须进「阻断级」

### 2.3 证据纪律

每条 finding 必须带：**可复现命令 + 输出摘要 + `文件:行号`**。做不到就标 `unknown` 并进 warnings，禁止写"我认为/通常来说"。

> **取证加速**：并行起两个 Explore 子代理（后端 B1–B8 / 前端 F1–F5），prompt 里写死「只读取证 / 每条给命令 + 输出 + `文件:行` / 无命中写 0 命中 + 命令」。
> **但子代理结论必须自己复核**：锚定的 `hc_evidence` / `design_vs_code_gaps` 与横切文档（如 `国际化设计方案.md`）都可能陈旧或误报。实测踩过：① 锚定判「未登记 JDBC 绕过」实为 Javadoc 提及的误报；② 横切文档称「后端无 Jackson 时区配置」，而该模块已配 `spring.jackson.time-zone: UTC`。
> **shell 坑**：zsh 下 `grep --include=*.ts` 会被通配符吃掉落得「没搜到」，按扩展名过滤要用 Grep 工具的 `glob` 参数。

## 3. 阶段 A 产物

### 3.1 `doc/design/modules/review-report-<module>.md`

```markdown
# <模块名> 设计方案复核报告
> 执行时间 / 轮换序号 / 对应设计稿 / 锚定版本（generated_at）/ 代码快照（分支 + HEAD 短 hash）
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

## 六、代码复核结果（B' 段填写）
| # | 复核项 | 方法 | 结果 | 处置 |

## 七、联调验证结果（C 段填写）
| # | 场景/API | 验证方法 | 实际 HTTP 码 + 响应摘要 | 备注 |

## 八、验收测试结果（D 段填写）
| # | 场景 | 构造数据 | 模拟操作步骤 | 断言 | 实测 | 数据已清 |

## 九、AI 自审结论（见 §4 / §6.2）
```

### 3.2 v2 优化稿落盘

- 原设计稿存在 → 在其末尾追加 `## v2 优化稿`（**不得覆盖原稿**），内容与报告第四章一致
- `design_doc == null` → 新建 `doc/design/modules/<module>-设计方案.md` 作为 v1（功能 + UI 完整）

### 3.3 更新状态

`_review-status.yaml` → 本模块 `status: design_ready`（写 `last_reviewed_at` 与 `report_path`）；未跑完 → `interrupted`。

## 4. 阶段 A'：方案自审 Gate（同一晚、独立重跑）

**三问**（每问都要独立执行，不复用阶段 A 的中间结果）：

1. **证据可复现？** 逐条重跑 A 段的证据命令，不能复现的 finding 降级或删除，并记入自审表。
2. **分级正确？** 有没有该阻断却被写成建议（禁止"为了好看降级"）？有没有误判阻断？
3. **v2 稿安全？** 是否覆盖全部阻断级问题？是否引入新的硬约束违反？API / 事件 / DTO 是否与锚定 `cross_module_contracts` 对齐？实现清单是否遗漏文件或联调前置？

| 判定 | 条件 | 处理 |
|------|------|------|
| ✅ PASS | 阻断级全有方案；v2 无新违反；契约对齐；证据可复现 | 进 §5 → **B 实现** |
| ⚠️ REVISE | 漏标阻断 / 方案自身违规 / 证据不可复现 | 状态 `needs_revision` → **当晚原地重做 A**，不再往下走 |
| ❌ BLOCK | 硬约束与现状不可调和（如需改架构决策） | 状态 `blocked` + 写清原因 → **退出**，等人工 |

> 自审结论里**不得**出现 `Active / Manual / Planned` 等门禁状态词（HC 状态只引用 `gate-state.json`）；也不得声称"CI 已拦截"——本地脚本拦截就是本地脚本。

## 5. 人工卡点：默认放行，可显式叫停

- **默认放行**：无人值守时按已批准口径自动走完 A → A' → B → B' → C → D → 收尾提交（只到特性分支）。**没有"等批准"这一步**。
- **显式叫停**：`doc/design/approvals/<module>.yaml` 里写 `decision: hold`（暂停，保留产物）或 `decision: block`（附原因）时才停；把 `decision` / `reason` 抄进报告与状态文件 `warnings`。文件不存在 = `auto`。
- 建议文件内容：`decision`（`auto` / `hold` / `block`）/ `approved_by` / `approved_at` / `report` / `scope`（本次允许实现的清单范围）/ `notes`。
- **周末照常跑完整流水线**（v2 的「B 阶段默认不在周六日执行」已废止）。
- 提交只到 `codex/nightly-<module>-<yyyymmdd>`；**禁** force-push、**禁**直推 `main` / `release/*`，等人工 PR Review。

## 6. 阶段 B：实现 + 代码复核

### 6.1 实现

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

### 6.2 阶段 B'：代码复核（实现完必须做，不能省）

重读本轮 diff，逐项核对并填报告第六章：

| # | 复核项 | 方法 | 不过怎么处置 |
|---|--------|------|--------------|
| 1 | **禁碰文件未被改** | `git status --porcelain` + `git diff --name-only` 对照 §0 禁碰清单 | 立刻还原该文件 |
| 2 | **无 scope creep** | diff 文件是否都在第五章清单内；清单外的一律回滚，或补进清单并说明理由 | 回滚 / 补登记 |
| 3 | **硬约束未新增违反** | 重跑 A 段的 B1–B8 / F1–F5 取证命令，与 A 段结论逐条比对 | 回滚对应文件 |
| 4 | **L3/L4 未被执行** | 检查是否有 DDL、删码、依赖升级、CI 改动混进来 | 还原 + 改进待审批清单 |
| 5 | **契约一致** | 前端 API 路径 / DTO 字段 / 错误码 vs 后端 Controller；与锚定 `exposed_apis` 对齐 | 改到一致 |
| 6 | **门禁绿** | `./scripts/check.sh`、前端 lint / typecheck / build 全部通过 | 修到绿 |
| 7 | **他人 WIP 未被卷入** | `git status` 里只应有本模块改动 | 无关改动保留不动，不 add |

判定：全部通过 → 进 §7 联调；任一不通过 → 回滚后重进 B。

## 7. 阶段 C：联调（可验证性优先，禁止口头"通过"）

1. 启动顺序：`bone-gateway` → 依赖模块 → 本模块（依赖不可用时见第 4 条）
2. 后端：按锚定 `exposed_apis` 逐条调用（curl 或集成测试），记录**实际 HTTP 码 + 响应摘要**
3. 前端：注入 token、切 locale、走路由守卫；确认错误码在 UI 上正确展示
4. **服务不可用时的降级**：只做契约级验证（单测 + OpenAPI 校验），联调列如实写「未联调（原因）」，**不得**写"通过"
5. 端到端最低通过判据：≥1 条完整场景（登录 → 模块页 → 调用本模块 API → 出错时显示正确文案）

**Bone 联调实操要点（实测）**

- **后台服务必须用工具的 `run_in_background` 启动**：`nohup + &` 会被会话回收（SIGKILL 静默死）。
- **IDE 有服务监管**：kill 端口进程后会按标准命令自动拉起（读 `target/classes`，编译 / 改 yml 即生效）；自己起的实例常撞「Port already in use」，验证以监管实例为准。
- **业务 API 必须走网关 8888**：直连模块端口 `/api/v1/**` 一律 401 空体（易误判为连接失败）。
- 启动模板：`SPRING_PROFILES_ACTIVE=dev BONE_DB_PASSWORD=mysql123 BONE_IAM_JWT_SECRET_KEY=dev-only-secret-key-minimum-32-bytes-long mvn -o -pl <模块> spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=<端口> -Dspring.cloud.nacos.discovery.enabled=false"`（Maven 须绕沙箱执行）。

## 8. 阶段 D：验收测试（构造数据 + 模拟人工操作）

> 联调只证明「接口通」，验收要证明「用户能办成事」。这一段必须真的造数据、真的点一遍。

1. **构造测试数据**：按报告第五章的联调前置条件，造最小可用数据集（如：1 个租户 + 1 个账号 + 1 个角色 + 若干权限 + 关联记录）。命名带可识别前缀（如 `nightly_<module>_*`），便于清理。
2. **模拟人工操作**：用 Playwright 走真实用户路径（登录 → 进模块页 → 新建 → 列表校验 → 编辑 → 删除 → 异常态校验）；无前端时用 curl + 脚本走完整业务链路。
   - `executablePath='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'`
   - `NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules node /tmp/x.cjs`
   - shell 是 history 路由（`/system`），子应用内 hash（`/system#/config`）
   - antd 两字按钮会插空格（「登 录」）→ 用 `button[type=submit]` 定位
   - 内容断言读 `body.innerText`
3. **断言与判据**：
   - 功能断言：每条用例列出期望值与实际值（列表条数、字段值、状态码）
   - 卫生断言：`console` 0 error、`pageerror` 0、5xx 0
   - 异常用例：至少 1 条（无权限 / 参数非法 / 边界值），验证错误码与文案正确
4. **清理测试数据（强制）**：脚本带清理步骤，跑完自证 `SELECT count(*)` 为 0 或对象已删；未清理的数据写进报告并标阻塞。
5. 结果填报告第八章；失败则**不进收尾提交**，改标 `interrupted` 并在状态文件写清失败用例。

## 9. 收尾（每晚固定输出，缺一不可）

1. 更新 `_review-status.yaml`：状态迁移 + `last_reviewed_at` / `implemented_at` / `report_path`
2. **追加** `doc/design/_nightly-log.md` 一行：`日期 | 模块 | 阶段(A/A'/B/B'/C/D) | 状态迁移 | 产物路径 | 下一步 | 阻塞`
3. 对话输出运行摘要：选中模块与原因 / **走完到第几段** / 状态迁移 / 产物路径 / 阻断级问题数 / 待审批（L3/L4）条数 / 联调与验收结论 / 下一步
4. **提交**：分支 `codex/nightly-<module>-<yyyymmdd>`；commit `[nightly][<module>] <摘要>`；**禁** force-push、**禁**直推 `main` / `release/*`，等人工 PR Review
5. 本晚未走完 → 状态 `interrupted` + 在 `_nightly-log.md` 写明「断在哪一段」，明晚从该段续

## 10. 铁律（违反立即停止并输出原因）

1. **绝不无证据下结论**：每条 finding 必须有可复现命令 + 输出 + `文件:行`
2. **绝不自定义 HC 编号语义**：一律引用锚定文件 `hc_semantics`（真源 §G-1.7）
3. **绝不把 `Manual` / `Planned` 写成 `Active`**，绝不声称本地脚本已被 CI 阻断
4. **绝不跳过 A' 方案自审与 B' 代码复核**——这两道是本闭环的风险承担者
5. **绝不执行 L3 / L4 动作**（DDL、删码、依赖、CI、密钥、生产迁移、§12）
6. **绝不在脏工作树上开工**（他人未提交改动必须被保护）
7. **绝不临时改轮换顺序或跳模块**；合批只按 §1.6 的条件
8. **绝不写超出设计稿的实现**：v2 未覆盖的边界 → 回设计阶段补稿
9. **绝不压缩阻断级问题**：有几条写几条；写不完标 `interrupted` 明晚续
10. **绝不因"今晚时间不够"砍掉 C 联调或 D 验收**——宁可标 interrupted 明晚补，也不写"看起来通过"
11. **绝不留测试数据**：D 段造的数据必须清干净并自证
12. **绝不触碰** `.comet/**`、`gate-state.json`、`Bone-DDD-最终实践方案.md` §12/§G-1.7、`bone-init.sql`、`.github/workflows/**`

---

## 附：相对 v2 的关键修正（本次优化留痕）

| # | v2 的问题 | v3 的处置 |
|---|-----------|-----------|
| 1 | **强制人工卡点**：无人值守时永远停在 A，设计稿变不成代码 | 改为**默认放行 + 显式叫停**（§5）；风险控制交给 A' 方案自审与 B' 代码复核 |
| 2 | **「周末 B 阶段默认不执行」**——用日历承担风险，周六日整晚空转 | 废止；六段流水线与星期几无关（§5） |
| 3 | 只有「设计自审」，没有**代码复核**；实现质量无判据 | 新增 **§6.2 阶段 B'** 代码复核 7 项 + 报告第六章 |
| 4 | 联调到「接口通」就收尾，**没有功能验收** | 新增 **§8 阶段 D**：构造测试数据 + 模拟人工操作 + 卫生断言 + 强制清理，报告第八章 |
| 5 | 报告模板只有「联调 / 自审」两章，装不下 B'/D 结果 | 报告扩为九章，按执行顺序排（六 代码复核 / 七 联调 / 八 验收 / 九 自审） |
| 6 | `interrupted` 只说明晚重做 A，续跑语义不清 | 加 `implementing` 状态；`interrupted` 须写明**断在哪一段**（§1.3 / §9.5） |
| 7 | 未沉淀取证加速与「锚定结论也要复核」的坑 | §2.3 补子代理取证 + 两处实测误报 + zsh `--include` 坑 |
| 8 | 未沉淀 Bone 联调实操（后台进程被回收 / 必须走网关 / IDE 服务监管） | §7 补四条实测要点 |

> v2 相对 v1 的修正（恢复人工卡点口径、HC 编号纪律、轮换以锚定为唯一真源、L0–L4 分级、脏工作树保护、时间线日志）在 v3 中全部保留，见 §0 / §1 / §2.1 / §9.2；其中「强制人工卡点」一项按本表第 1 条改为默认放行。
