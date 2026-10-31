# ADR-0044：审批人字段 `approver_id` 语义契约 — FIELD 专用指定审批人 + 组织策略改读主管/负责人

| 项 | 内容 |
|----|------|
| **状态** | **已接受 / 已实现**（0081/0082/0083/0084 + `RecordFieldApproverResolver` 已落地并双远程同步，2026-10-31 收口归档） |
| **日期** | 2026-10-31 |
| **决策者** | 架构（追述登记：组织建模联调中实测出「部门负责人自审批死路」，据此收敛语义并补 ADR 闭环「落地前需补 ADR」的承诺） |
| **关联** | [0043](./0043-enum-option-source-layered-truth.md)（枚举选项源分层真源，同构问题：真源在哪、谁引用谁）、[039-发布包](../Bone-版本与发布规范.md)（expand/contract 增量迁移）、[ADR-生产数据库增量迁移策略](./ADR-生产数据库增量迁移策略.md)（expand/contract 模式） |
| **下游同步** | `scripts/migration/0051_expense_production_templates.sql`（`approver_id` 类型 REFERENCE→iam_account）、`scripts/migration/0081_approver_field_semantics.sql`（display_name/comment 收敛为「指定审批人」）、`scripts/migration/0082_iam_org_hierarchy.sql` / `0083_expense_org_routing_v3.sql` / `0084_org_leader_reporting_line.sql`（组织层级 + 汇报线 + v3 路由）、`bone-workflow-engine/.../starter/adapter/RecordFieldApproverResolver.java`（策略 switch：FIELD 读本字段、组织策略经 OrgHierarchyProvider）、`bone-workflow-engine/.../runtime/WorkflowEngineImpl.java`（Resolution + 挂起原因）、`scripts/audit-org-coverage.py`（组织覆盖审计）、`OrgRoutingDefinitionV3Test.java`（负向探针） |

---

## 背景

报销 / 采购流程在 v1/v2 用**同一个** `approver_id` 标量列同时承载两种语义：

1. **申请人自选的指定审批人**（流程定义 `strategy=FIELD` 节点按它解析办理人）；
2. **组织层级的审批人**（经理 / 部门负责人 / 向上 N 级）。

这构成一个典型的「共享列反模式」：mgr 与 dept 共用一列，导致分级审批语义塌缩成「同一审批人」（实测租户 1001 报销中额单据的 `mgr` 与 `dept` 解析出同一人），职责分离（SoD）名存实亡。

复核时又发现第二个断裂：**审批是 (节点 × 轮次 × 办理人) 的 1:N 事实，真源在引擎任务台账 `wf_task`**，而单据上只有一个 `approver_id` 标量，且全仓 grep 0 处代码写它（改派/加签/转办只改 `wf_task` 不回写单据），于是它必然陈旧。界面「当前审批人」若读它，会显示历史输入值而非真实办理人。

第三个断裂（最高危）：v3 定义让部门负责人自己的 `manager_id` 恒为 NULL（假设「负责人没有上级」），在真实三层部门树上这是错的——科室负责人要向事业部负责人汇报。实测租户 1001 的 24 个真实发起人里有 10 个是部门负责人（42%），他们的每张单据在 `mgr`（manager_id 空）与 `dept`（解析到本人）双双挂起，报销流程根本跑不动。

问题被呈现为「`approver_id` 应该怎么定义」——需要选型的不是「是否保留该列」，而是**它承载什么语义、谁引用谁、真源在哪**。

## 决策

**「语义收敛 + 类型 REFERENCE + 组织策略改走专用列 + expand/contract 兼容」**，而非「一刀切删列」：

1. **`approver_id` 语义唯一收敛为「申请人发起时指定的审批人」（FIELD 策略的流程输入）**。仅在流程定义 `strategy=FIELD` / `INITIATOR_SELECT` 的节点生效；它是**历史输入值**，不是「当前审批人」。界面「当前/历史审批人」一律从引擎台账 `wf_task` 派生，不读本字段（0081 在 display_name/comment 写明边界）。
2. **类型定为 `REFERENCE` → `iam_account`**（0051）。值语义 = `iam_account.id`（与行策略 `:operator` 同源，保证审批人须为可登录账号）；SoD 禁止 `=` 申请人。IAM 账号运行时未暴露前**降级为 STRING 是临时形态**，不是目标态。
3. **组织策略（LINE_MANAGER / DEPT_HEAD / LEVEL_UP）不再读 `approver_id`，改经 `OrgHierarchyProvider` SPI 读 `iam_account.manager_id` / `iam_dept.leader_id`**（0082/0083/0084 + `RecordFieldApproverResolver` 的 switch 结构保证：org 策略走 `resolveByOrgHierarchy`，FIELD 策略走 `resolveFromRecord` 读本字段，两条路径物理隔离）。
4. **分级语义恢复**：`mgr` 节点 → 直属主管（LINE_MANAGER），`dept` 节点 → 部门负责人（DEPT_HEAD），两者是组织目录里**不同的人**（v1/v2 共用列时塌缩的正是这一点）。
5. **expand/contract 中间态，保留列与历史数据**：v1/v2 在途实例仍按 FIELD 策略读 `approver_id`（它是合法的「申请人指定审批人」），新增的组织解析路径不依赖它。不删列、不改物理结构，避免破坏在途单据。
6. **自审批上溯（换审批人而非跳过节点）**：组织策略解析到发起人本人时，向上取一级主管；上溯后仍为本人（manager_id 自环）或已到汇报链顶层 ⇒ 返回空，交给引擎 fail-closed 挂起。这**不是**「跳过审批节点」（那等于单据无人复核直接放行），只是把「我审我自己」变成「我的主管审我」，节点仍在、内控链条完整。
7. **失败关闭**：任一组织策略解析不出人（无主管 / 无部门 / 目录不可达），引擎挂起并附**可操作原因**（describeFailure 逐策略给出「查了谁的什么、怎么修」），绝不跳过节点放行。

### 备选方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 一刀删除 `approver_id` 列 | v1/v2 在途实例按 FIELD 策略读它作为「申请人指定审批人」，删列即破坏在途单据与历史数据；违反 expand/contract（ADR-生产数据库增量迁移策略） |
| 继续让 mgr/dept 共用 `approver_id` | 共享列反模式：分级语义塌缩成同一人，SoD 失效；飞书/钉钉审批、SAP agent determination、Semarchy 均无此实践 |
| 把 `approver_id` 当组织真源（在单据上写主管/负责人 id） | 审批办理人是 (节点×轮次×办理人) 的 1:N 事实，真源在引擎台账 `wf_task`；把组织关系塞进单据标量列会立刻陈旧且无法表达会签/加签 |
| 组织策略解析到本人时「跳过该节点」 | 等于少一道审批、单据无人复核直接放行，是审批系统最严重事故形态；业界（飞书/钉钉、SAP escalate）均定义「上溯一级」而非「跳过」 |
| `approver_id` 保持 STRING 不升级 REFERENCE | 失去引用完整性（审批人须为可登录账号）、SoD 无法约束；REFERENCE→iam_account 复用现有运行时关系引擎，零额外 DDL |

## 后果

**正向**：分级审批语义恢复（mgr≠dept）；自审批死路解除（租户 1001 部门负责人提交不再双双挂起，仅汇报链顶层根负责人按设计 fail-closed）；挂起原因从「解析不出审批人」升级为可操作一句话；真源分层清晰（输入=单据 FIELD 字段，办理事实=wf_task，组织关系=iam_account/iam_dept）。

**代价**：`approver_id` 与 `manager_id`/`leader_id` 并存（expand/contract 必然的过渡态），需靠 ADR 与测试锁定语义边界，防止回归成「又往 approver_id 塞组织关系」；IAM 未暴露时 REFERENCE 降级 STRING 是临时形态，需文档与迁移注释标明。

**范围纪律**：只做语义收敛、类型定 REFERENCE、组织路径改专用列、汇报线补齐、自审批上溯、挂起原因可操作化六件事；不删列、不改物理结构、不动 v1/v2 读取路径。

## 验证证据（2026-10-31 收口，会话内复跑）

- **负向探针（最关键）**：`OrgRoutingDefinitionV3Test` 从迁移 0083 抽取 v3 定义 JSON 原样反序列化，单据塞入诱饵 `approver_id=decoy-9`；断言 `mgr`/`dept` 节点待办派给组织目录的 `u-mgr`/`u-head` 而非诱饵，且 `u-mgr≠u-head`。只断言「派给了某人」不足以区分新旧策略——诱饵让「切换未生效」必然转红。 5 例断言（小额→主管 / 中额→负责人且≠主管 / 大额会签矩阵 / 无主管 fail-closed / 采购→负责人）全绿。
- **类型契约**：0051 将 `approver_id` 录入 `meta_field.type='REFERENCE'` + `reference.targetEntityCode='iam_account'`；0081 收敛 display_name=「指定审批人」、comment 写明「当前/历史审批人不在单据上，以 wf_task 为准」。全仓 grep 无 STRING 与 REFERENCE 冲突定义。
- **诊断可操作**：`RecordFieldApproverResolverDiagnosticsTest` 9 例覆盖 LINE_MANAGER/DEPT_HEAD/LEVEL_UP/ROLE/USER/FIELD 各自的 describeFailure 文案；`RecordFieldApproverResolverOrgHierarchyTest` 覆盖自审批上溯（解析到本人→上溯主管；顶层→挂起）。
- **数据侧**：迁移 0084 补齐「部门负责人向谁汇报」（负责人主管=父部门负责人、普通成员主管=本部门负责人、根负责人 NULL），带 `manager_id IS NULL` 守卫幂等可重跑，末尾 5 组校验 SELECT（覆盖率 / 残留无主管 / 自环 / 跨租户串线 / 汇报链成环）全绿。
- **组织覆盖审计**：`scripts/audit-org-coverage.py`（`--strict` 退出码 0）确认租户 1001 真实发起人 24 人中仅 2 人（根部门负责人，汇报链顶层）会因无主管挂单，属设计内 fail-closed，非回归；分母取 `wf_instance.started_by`（真实流量）而非账号总数，避免 E2E 残留账号制造虚假覆盖率。

**机器可验证门禁**：任何新增组织策略定义若重新把 `approver_id` 当数据源，`OrgRoutingDefinitionV3Test` 的 decoy 探针会使待办派给 `decoy-9` 而断言转红——该契约由测试而非文档保证。
