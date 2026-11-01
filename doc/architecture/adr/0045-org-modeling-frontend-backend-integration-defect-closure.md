# ADR-0045：组织建模前后端联调缺陷闭环 — IAM 账号展示/部分更新修复 + 审批摘要与授权口径待决项

| 项 | 内容 |
|----|------|
| **状态** | **部分已实现 / 部分待决**（D1–D3 已修并由单测 + 变异验证锁定；D4 审批摘要行级 ACL 与 D5 授权口径漂移属安全边界，**只出方案不擅动**，留 owner 裁决） |
| **日期** | 2026-11-01 |
| **决策者** | 架构（追述登记：ADR-0044 三决策落地后的前后端联调中实测暴露的缺陷闭环） |
| **关联** | [0044](./0044-approver-id-field-semantics-contract.md)（`approver_id` 语义契约，D1/D2/D3 三决策来源）、[0029](./0029-sdk-auto-tenant-filter.md)（租户自动过滤）、[0042](./0042-evidence-idempotency-unique-index.md)（幂等兜底，E2E 幂等基线同源思想） |
| **下游同步** | `bone-platform/bone-iam/src/main/java/com/bone/iam/application/AccountApplicationService.java`（`page()` 无条件装配 deptName、`update()` 消费 email）、`bone-platform/bone-iam/src/main/java/com/bone/iam/domain/model/account/Account.java`（`updateProfile` 逐字段 null 守卫、新增 `changeEmail`）、`AccountApplicationServiceDeptNameBackfillTest`（4 例）、`AccountApplicationServicePartialUpdateTest`（5 例）、`scripts/e2e/workflow-org-routing-e2e.py`（API 层 32 项）、`scripts/e2e/ui/ui-org-routing-probe.mjs`（UI 层 23 项） |

---

## 背景

ADR-0044 落地了 D1（`approver_id` 改标签不删除）、D2（组织覆盖率作为可度量 SLI）、D3（REFERENCE 接线暂缓 + 安全红线守卫）三项决策。决策本身是**纸面正确**的——但纸面正确不等于系统正确。本轮按「模拟人工操作构建企业真实数据 + 前后端联调」的要求，用**产品自身的 HTTP API**（而非 SQL 直插）造出 5 部门 / 8 账号 / 1 角色 / 6 权限的企业组织基线，再用真实浏览器走完「发起 → 审批 → 落库」闭环。

联调暴露出 **5 个前后端不一致缺陷**，其中 3 个是 IAM 侧的**数据完整性缺陷**（界面给出错误信息、资料被静默抹除），2 个是运行时侧的**安全边界问题**（不在本轮擅动范围）。此外实测确认了一条**种子数据债**（幽灵审批人账号）。

这 5 个缺陷都不是「读文档能发现」的：它们全部只在「真实数据 + 真实浏览器 + 真实点击」的组合下才暴露。

## 缺陷清单与处置

### D1（已修）：账号列表「所属部门」恒显「未归属」

**现象**：账号管理页整列「所属部门」显示「未归属」，即使账号确实挂在某个部门下。

**根因**：`AccountApplicationService.page()` 中 `deptName` 的装配**依附于部门树懒加载**——部门树只在「按部门筛选」时才加载，于是不过滤的默认列表里 `deptName` 全为 null。`AccountManagement.tsx` 把 `deptName == null` 渲染成「未归属」，于是**有部门的账号被显示成未归属**。这是**错误信息**而非数据缺失：用户会以为组织关系丢了。

> 原代码注释里已自陈过这个坑（「按需加载，避免无谓开销」），但当时只修了主管姓名（`managerName`），部门名沿用了同一套懒加载 ⇒ 同类缺陷复发。

**修复**（`AccountApplicationService.java:272-280, 384-390`）：
- `deptName` 改为**无条件批量装配**：`deptNameIndexByIds(result.getRecords().stream().map(Account::getDeptId).toList())`；
- 新增 `deptNameIndexByIds(List<Long>)`：一次 `findByIds` 查完本页涉及的部门，避免 N+1；
- 返回 `HashMap` 而非 `Map.of()`：不可变 Map 的 `get(null)` 抛 NPE，而**未归属部门的账号其 `deptId` 就是 null**——调用方一次 `get(null)` 就能让整个列表接口 500；
- 原 `deptNameIndex(List<Dept>)` 已无调用，删除（避免两条装配路径并存）。

**锁定测试**：`AccountApplicationServiceDeptNameBackfillTest`（4 例）——不过滤也装配 / 未归属账号 `deptName=null` 且不 NPE / **不再加载整棵部门树**（`verify(deptRepository, never()).listAll()`）/ 按部门筛选同样装配。第三条是**防回退**的关键：只断言「装配了」不够，防止有人「优化」回懒加载。

**变异验证**：撤掉无条件装配后测试转红（`expected: "前端研发组" but was: null`），证明该测试有判别力。

### D2（已修）：部分更新静默抹掉真实姓名与手机号

**现象**：管理员只改「角色」，保存后该用户的**真实姓名与手机号被清空**。

**根因**：语义不一致——`deptId` / `managerId` / `status` / `roleIds` 的约定是「null = 不变更、0 = 清空」，但 `Account.updateProfile(realName, phone, avatarUrl)` 是**无条件覆盖**。仓储层是全列 UPDATE，于是任何只提交部分字段的编辑请求都会把 `null` 写回库。

**修复**：
- `Account.updateProfile`（`Account.java:200-211`）改为**逐字段 null = 不变更**，与 `changeManager` / `changeDept` 的 null 守卫同构；「清空」语义若将来需要，由应用层显式传空串（姓名/手机号本就不可为空，故不提供清空入口）；
- `AccountApplicationService.update()`（`:132`）以 `account.updateProfile(cmd.getRealName(), cmd.getPhone(), null)` 调用。

**锁定测试**：`AccountApplicationServicePartialUpdateTest`（5 例）——只传 `roleIds` 不清空资料 / 提交的姓名手机号照常生效 / 邮箱可更新 / `managerId=0` 清空主管 / `null` 不变更且不触碰角色绑定。

### D3（已修）：邮箱被静默丢弃

**现象**：管理员改邮箱，界面提示成功，库里没变。

**根因**：`AccountWebConverter.toUpdateAccountCommand` **确实**把 email 传了下来（问题不在适配层），但 `AccountApplicationService.update()` 根本没有消费 `cmd.getEmail()` ⇒ 提交即蒸发。契约在链路上被无声截断。

**修复**：新增 `Account.changeEmail(Email)`（`:219-225`，null = 不变更），应用层 `if (cmd.getEmail() != null) { account.changeEmail(Email.of(cmd.getEmail())); }`（`:134-136`）。邮箱在库表上**无唯一约束**（唯一键是 `(tenant_id, username)`），故与创建路径保持一致，不引入额外唯一性规则，避免「改邮箱被 409 拦住」这种与创建行为不一致的割裂。

**锁定测试**：`AccountApplicationServicePartialUpdateTest#appliesSubmittedEmail`。

### D4（待决 · L3 安全边界）：审批人待办摘要恒为空

**现象**：审批中心「待我审批」列表的「单据 / 金额 / 单据状态」列全部显示 `-`，审批人**看不到自己要批的是什么**。

**根因链（逐跳取证）**：
1. `RuntimeApprovalApplicationService.summarize(instance)`（`RuntimeApprovalApplicationService.java:486-509`）调 `records.getById(entityCode, recordId)` 装配摘要；
2. `RuntimeRecordApplicationService.getById`（`:137-140`）把 `rowOwnerId()` / `currentRoleCodes()` 传下去；
3. `rowOwnerId()`（`:107-116`）对非平台管理员返回**当前用户的数字 ID**；
4. `JdbcRuntimeRecordService.applyRowOwnerFilter`（`:1245-1271`）：实体未声明行策略时，**只要表存在 `created_by` 列就注入 `AND created_by = :rowOwner`**；
5. 单据由发起人（emp1）创建，审批人（femgr）不是 owner ⇒ 查询 0 行 ⇒ 抛 `RUNTIME_RECORD_NOT_FOUND` ⇒ 被 `summarize` 的 catch 吞掉 ⇒ 返回 null ⇒ 界面 `-`。

**性质**：这不是「漏装配」，而是**两套授权语义被混用**——行级 ACL 回答的是「谁能浏览这张单」，而审批摘要需要的授权来源是「**该任务指派快照等于当前办理人**」（`wf_task.assignee_id`，见 ADR-0044 的真源分层）。审批人**有**权看摘要，但那个权利不来自记录归属。

**为什么本轮不擅动**：任何修法都要**放宽可见性**，属安全边界（L3），按既有纪律只出方案。

**推荐方案（A，架构首选）**：把审批摘要从**记录读路径**剥离为**审批授权路径**——`summarize` 不再走 `records.getById`（owner 作用域），改为走一个专用读口，其可见性判据是「当前用户是该实例上某条 `wf_task` 的 assignee」（与 `RuntimeApprovalController` 类注释自述的判据一致：「授权对象是**流程任务本身**……可见性判据是『任务指派快照 == 当前办理人』」），并**只**施加**字段级**角色裁剪（`RuntimeFieldVisibilityPolicy`），不施加行级 owner 过滤。

**备选方案**：
- **B（声明式，最轻）**：给实体声明 `meta_entity.row_policies`，规则 `created_by = :operator OR approver_id = :operator`。**但只覆盖 FIELD / INITIATOR_SELECT 策略**——按 ADR-0044，组织路由的审批人在 `wf_task.assignee_id` 而**不在** `approver_id`，故对本场景**无效**。仅可作为 FIELD 场景的补充。
- **C（扩 DSL，风险最高）**：给 `RowPolicySpec` 的 DSL 增加 EXISTS 子查询谓词（`wf_task.assignee_id = :operator`）。改动面大，且 `RowPolicySpec` 的注释明确写着「权限类判定的默认方向必须是 fail-closed，宁可拒绝也不能放大」，扩展 DSL 需独立安全评审。
- **D（冗余快照）**：提交时把摘要字段快照进 `wf_task`/实例，待办列表不再回表读业务单据。飞书/钉钉审批即此形态，列表渲染最快；但引入快照陈旧问题与额外存储。

**建议**：采用 **A**，B 作为 FIELD 场景的补充声明。

### D5（待决 · L3 安全边界）：审批端点授权口径与 `RuntimeAccessGuard` 漂移

**现象**：同一份待办，`/runtime/approval/records/{entity}/{id}` 放行，`/runtime/approval/tasks/pending` 却 403。

**根因**：`RuntimeAccessGuard.canRead`（`:55-60`）的判据是「`platform.admin` OR `runtime:*:read` OR `runtime:{entity}:read`」——**实体级细码**即可；而 `RuntimeApprovalController` 的读端点注解层仍写死旧粗码：

```java
// RuntimeApprovalController.java:41 / 74 / 93 / 181 / 194（5 处读端点）
@PreAuthorize("hasAnyAuthority('metadata:runtime:read', 'metadata:read')")
```

⇒ 持有 `runtime:{entity}:read`（RC-004 推荐的**生产形态**：把 wildcard 换成具体实体码）的账号，会被注解层挡在待办列表之外。

**为什么本轮不擅动**：修法是**放宽注解**（接受 `runtime:*:read` / `runtime:{entity}:read`），属安全边界（L3）。

**推荐方案**：把 5 处读端点注解统一改为 `@PreAuthorize("@runtimeAccessGuard.canReadAnyRuntime()")`（新增一个「只判通配读 + 平台管理员」的布尔方法），并保留旧码为**兼容分支**（`hasAnyAuthority('metadata:runtime:read','metadata:read')` OR 新判据），使旧码用户在过渡期仍可用、新实体级码用户可用；写端点同理另立 `canWriteAnyRuntime()`。**同一判据只此一份**的原则（`RuntimeAccessGuard` 类注释已自陈该原则）在此被端点注解破了，必须收口。

## 验证证据（2026-11-01，会话内复跑）

### 双证据源范式

所有断言都取**两个独立证据源**：HTTP 响应 / MySQL 直查（API 层）、浏览器 DOM 事实 / 落库复核（UI 层）。单源断言不做「跑通即正确」的推断。

### API 层：`scripts/e2e/workflow-org-routing-e2e.py` — **32/32 通过**（三次连跑幂等）

企业真实数据**全部经 IAM HTTP API 自建**（非 SQL 直插）：5 部门 → 8 账号 → 回填负责人/主管 → 1 角色 + 6 权限（`[57, 9101, 9102, 9121, 9122, 9123]`，**一次性全量授予**，因 `POST /iam/roles/{id}/permissions` 是全量替换语义）。

六个场景：
| 场景 | 断言要点 | 结果 |
|---|---|---|
| S1 LINE_MANAGER | 小额 → `mgr` 节点，assignee = 直属主管 femgr，**≠ 表单 `approver_id`(CEO)**，且表单干扰值仍落库 | ✅ |
| S2 DEPT_HEAD | 中额(3000) → `dept` 节点，assignee = 部门负责人 bemgr | ✅ |
| S3 审批闭环 | femgr 待办可见 → 审批动作 200 → 实例终态 → 单据 APPROVED | ✅ |
| S4 fail-closed | 无主管账号发起 ⇒ 实例 SUSPENDED + 挂起原因落 `wf_status_outbox`（`intent='SUSPENDED'`）+ **未建 `wf_task`**（不误派单） | ✅ |
| S5 大额会签 | 金额 6000 → `finance_cosign`，assignee 是**不存在的种子账号** | ✅（已登记为种子债） |
| S6 安全红线 D3 | `iam_account ∉ meta_entity`（若注册为实体将被 `SELECT *` 外泄凭据列） | ✅ |

> **S1 的判别力**：表单里**故意**把 CEO 填进 `approver_id`，并断言该值仍落库（业务表 `ext_str_01`）。若「组织策略改读主管」未生效，待办会派给 CEO ⇒ 转红。只断言「派给了某人」不足以区分新旧策略。

### UI 层：`scripts/e2e/ui/ui-org-routing-probe.mjs` — **23/23 通过**

P0 API 前置自证（不成立即 exit 1，**不把服务端问题归因到 UI**）→ P1 账号管理页（admin）→ P2 组织机构页（admin）→ P3 审批中心（**审批人 femgr 真实点击「通过」**）→ 落库复核。

P1 用 **DOM 事实 + 量化可见性**（`getBoundingClientRect().height > 0`）判定，避免 IAM 冷启动首屏慢被误报成页面坏；P3 精确锁定点击对象（待办按 `created_at DESC` ⇒ 第一行即新造单，并用 API 层 `pendingTasks[0].assigneeId` 校验）。

**探针自身的缺陷（已修，非产品缺陷）**：弹窗关闭断言原写作
```js
document.querySelector('.ant-modal-wrap .ant-modal-content:visible')
```
`:visible` 是 **Playwright 专有伪类**，放进浏览器原生 `querySelector` 会抛 `SyntaxError` ⇒ `waitForFunction` 直接 reject ⇒ 被 `.catch` 吞成 `false` ⇒ **恒红假阴性**（当时 20/21）。已改为 computed style + 盒模型判定，并补两条增强断言：「审批弹窗在提交前处于可见态（**判别对照**——否则「已关闭」是恒真断言，无判别力）」+「页面出现「已通过」成功提示」。前端侧 `runDecision` 确有 `setDecisionTarget(null)`（`ApprovalCenterPage.tsx:237`）⇒ 弹窗本就应关闭。

### 回归

- `bone-iam` 模块全量单测 **308/308 通过**；
- API 层 E2E **32/32**（三次连跑，部门恒 5、账号恒 8 ⇒ 幂等）；
- UI 探针 **23/23**；
- 在线生效验证（重启 IAM 后）：`deptName=联调-前端研发组`、只传 `roleIds` 不再清空资料、邮箱可改。

## 遗留债（不在本轮范围，但已登记）

1. **幽灵审批人（种子债）**：`finance_cosign` 节点的 assignee 是账号 `9104` / `9105`，**这两个账号在 `iam_account` 中不存在**（E2E 已断言其确实不存在）。历史 99 条组织路由 task 的 assignee 亦全部是这类不存在的种子账号（9103/9104/9105）。⇒ 金额 > 5000 的单据在真实环境下**必然挂起**。修法：在 tenant 0 建真实的财务会签账号并回填节点定义，属**种子数据**变更，需 owner 拍板（会改流程定义快照）。
2. **跨租户组织目录不可见**：`HttpOrgHierarchyQuery` 用**单一 admin 服务账号**登录并缓存 300s，`org-hierarchy` 请求**不带租户头**；JWT 的 `tenantId` 由登录账号所属租户决定 ⇒ tenant 1001 的组织数据对 tenant 0 的服务账号**不可见**（404），运行态 `X-Acting-Tenant-Id` 未生效。这正是本轮改在 tenant 0 自建组织基线的原因。属 **L3 租户隔离**，未擅动。
3. **IAM 契约陷阱（供后续联调参照）**：`PUT /iam/depts/{id}` 是**全量替换**语义，且 `order_no` NOT NULL 无默认值 ⇒ 少传字段即报「数据违反完整性约束」；部门**只有** `/iam/depts/tree`，**无分页列表端点**（调 `/iam/depts?page=…` 恒空）；`POST /iam/roles/{id}/permissions` 是全量替换；`PUT /iam/accounts/{id}` 的 `deptId/managerId/status/roleIds` 为「null=不变更、0=清空」。

## 范围纪律

本轮只做「联调 → 定位 → 修复可安全自证的三处 IAM 缺陷 + 建立两条可复跑的 E2E 资产」。**未触碰**：多租户隔离（L3）、审批端点授权注解（L3 放宽）、行级 ACL 语义（L3 放宽）、种子数据变更（改流程定义快照）、流程引擎与前端表单引擎。D4 / D5 / 遗留债 1–3 均以**方案 + 证据**形式登记在本文档，等 owner 裁决。
