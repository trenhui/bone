# BONE 全模块前后端联调报告（自动化执行）

> 执行时间：2026-11-13 22:27 GMT+8（自动化任务）
> 范围：静态门禁 + 后端集成联调（py-core / node-api）+ 测试数据生成（datagen）+ 环境状态盘点 + 缺陷定位
> 说明：本 run 在共享开发树（dev）上执行；存在并行会话，按本仓提交纪律**只提交自有交付物**，不触动并行会话的 146 个在途改动。

---

## 一、结论速览

| 项 | 结果 |
|---|---|
| 静态门禁（60 项 python 检查） | ✅ 全部 PASS（含 DB 相关 orphan-tables / init↔live sync） |
| 后端联调 py-core（app-to-data 全链） | ✅ 54/54 PASS |
| 后端联调 py-core（ui-schema-samples） | ✅ 31/31 PASS |
| 后端联调 py-core（two-scenario 全链） | ⚠️ 81/90 PASS，**9 失败，全部在采购审批工作流** |
| 后端联调 node-api（理赔全链） | ❌ 网关 503（synap-claim 下游熔断/不可用），理赔模块集成层不可测 |
| 测试数据生成 datagen | ✅ 5 个场景已构建（sales-claim / procurement / tenant-iam / master-data / claim-ai-feedback） |
| UI 探针（"点击每个功能"） | ✅ 已跑（装好 Chromium）：smoke-render PASS；全菜单回归 **333/335 PASS**（74 个菜单、35 页按钮闭环），仅 `/claim` 微应用因 503 失败（synap-claim 宕机） |

**是否达成"所有功能正常"：否。** 两个硬阻塞：
1. **synap-claim 503** —— 网关上报其下游服务熔断/不可用（运行态服务宕机，非代码缺陷）。
2. **采购审批 E2E 与产品工作流定义漂移** —— 见第二节根因。

> 注：此前"缺 Chromium 二进制"一项已在本次 run 内通过 `npx playwright install chromium` 解决，UI 探针已实际跑通（见第四节）。

---

## 二、采购审批挂起：根因精确定位（已定位，待修复）

### 现象
`py-core` 的 `two-scenario-full-chain` 中，采购订单提交后审批返回 **400「审批流程已挂起（审批人待分配）」**，导致后续 `startReceive / confirmReceive / pay` 因单据仍处 SUBMITTED 而连锁 409，共 9 个用例失败。报销、理赔（ outpatient ）审批正常。

### 根因
采购工作流**产品定义已升级到 v3（`DEPT_HEAD` 部门负责人指派）**，但 E2E 脚本仍按旧版 **v1/v2（`FIELD/approver_id` 申请人自选审批人）** 编写，二者语义不一致。

- 引擎在 `WorkflowEngineImpl#start`（L480）使用 `definitionRepository.findLatestPublished(definitionKey)`，对 `purchase_order` 恒定取**最新已发布版本 = v3**。
- 采购 v3 的 `approve` 节点指派策略 = `DEPT_HEAD`（`wf_process_def` 实测：`{'approve':[{'strategy':'DEPT_HEAD'}]}`）。
- `DEPT_HEAD` 经 `OrgHierarchyProvider.deptHeadOf(发起人)` 解析部门负责人；而 E2E 的采购提交人以 `admin(userId=1)` 发起，`act_as(APPROVER_HDR,…)` 用 `APPROVER_ID` 去审批，**假设 assignee = APPROVER_ID**（见 `two-scenario-full-chain-e2e.py` L514 / L648 / L703-707 的注释与取值）。
- 实际 assignee = admin 的部门负责人 ≠ `APPROVER_ID` ⇒ 要么解析不出（SUSPENDED/400），要么任务级 SoD 不匹配（403）。本次实测为 SUSPENDED→400。

### 实证
- 实时库 `wf_process_def` 中 `purchase_order` 三版均 PUBLISHED：`v1=FIELD`、`v2=FIELD+会签`、`v3=DEPT_HEAD`；`expense_reimbursement` 同期也升到 v3（`LINE_MANAGER`/`DEPT_HEAD`），报销场景因提交人 org 上下文完整而通过，反衬采购 E2E 的 org/审批人假设陈旧。
- 迁移 `0083_expense_org_routing_v3.sql` 明确将采购 `approve` 从 `FIELD/approver_id` 收敛为 `DEPT_HEAD`（"采购单级审批同样原为 FIELD/approver_id，一并收敛到部门负责人"）。

### 修复建议（二选一，均精确到文件）
- **方案 A（推荐·对齐产品意图 v3）**：更新 `two-scenario-full-chain-e2e.py` 的采购场景，使其适配 `DEPT_HEAD`——让采购提交人归属于某个部门且该部门负责人 = `APPROVER_ID`（或在 E2E 中建立 IAM 部门/负责人上下文后，用部门负责人会话审批）。需 E2E 补充 org 建模步骤。
- **方案 B（回退采购语义）**：若产品判定采购应保持"申请人自选审批人"，写新迁移 `0084` 将 `purchase_order` 的 `approve` 节点改回 `FIELD/approver_id`（或停用 v3、启用 v2），与现有 E2E 一致。

> 本 run **不在共享实栈上单方面改生产定义 / 重部署 / 改 E2E 后强行重跑**，以避免干扰并行会话与引入未验证改动。根因与修复已精确到迁移号、库表、引擎行号与 E2E 行号，交并行会话或下次联调落地。

---

## 三、synap-claim 503：运行态服务宕机（非代码缺陷）

`node-api` 套件全部失败，网关返回：
```
{"code":503,"message":"下游服务暂不可用（熔断）","errorCode":"GW_UPSTREAM_UNAVAILABLE"}
```
即网关对 synap-claim 上游的熔断器处于打开态 / 上游不可达。本机 `:3012`、`:3013` 健康检查返回 200（其他后端存活），但经网关的 synap-claim 路由 503，说明 synap-claim 业务后端实例未就绪。

- 性质：**运行态服务 outage**，不是源码缺陷，无法通过改代码修复。
- 处置：需重启 / 恢复 synap-claim 后端（共享实栈，由并行会话负责），本 run 不单方面重启。
- 影响：智能理赔模块全部前端功能 + 理赔全链集成测试在此环境不可测。

---

## 四、UI 探针结果（"点击每个功能"）

仓库具备 30+ Playwright 探针（`scripts/e2e/ui/*.mjs`），覆盖：菜单回归、元数据 CRUD、审批发送/挂起原因、理赔功能/单导航/Toast、采购提交反馈、九模块深度探针等。

本次 run 已安装 Chromium 并实际执行代表性探针：

- **`smoke-render.mjs` ✅ PASS**：前端壳层渲染正常，检出 **44 个按钮**、完整菜单（含智能理赔 / 采购订单 / 审批中心等）、模型设计页正常，无 Vite overlay 报错。
- **`ui-all-menu-regression.mjs` ✅ 333/335 PASS**（耗时约 4.5 分钟）：自动发现 **74 个可导航菜单**，UI 登录成功，主操作按钮点击闭环 **共触发 35 个页面**；仅 2 项失败，且同为 `/claim#/mappings`（智能理赔微应用）因网关 **503** 报 `[qiankun] 微应用加载异常 … status code 503`，与第三节 synap-claim 宕机完全对应。

结论：**除智能理赔微应用（受 synap-claim 503 牵连）外，其余全部模块的菜单、页面渲染与按钮交互均通过浏览器实测可用**。这是"点击每个功能"层面的最高可达覆盖。

---

## 五、测试数据生成（模拟人工操作造数）

通过项目自带 `datagen.py` 构建真实业务流程仿真数据（落盘 `/tmp/bone-data/`）：
- `sales-claim`、`procurement`、`tenant-iam`、`master-data`、`claim-ai-feedback` 五个场景，均为 JSON 结构化样本，可直接驱动联调与前端渲染。

---

## 六、提交 / PUSH 说明

- 工作树共 **146 个改动**，全部属于并行会话（共享树）。按本仓铁律（`git commit --only -- <自有路径>`，**绝不 `git add -A`**），**不提交这些文件**——否则会改写他人工作并污染共享索引。
- 本 run **未对生产源码做任何改动**：采购问题是"E2E 与定义漂移"而非源码缺陷；synap-claim 是运行态宕机；UI 探针已实际跑通（第四节）。因此**没有"我方"源码改动可提交**。
- 本报告作为本次联调交付物落盘并呈交；未强行 PUSH 他人文件。如需把本报告随提交带走，可单独 `git add --only -- scripts/e2e/integration-report-2026-11-13.md` 后提交（不影响并行会话）。

---

## 七、下一步（落实"所有功能正常"所需）

1. **恢复 synap-claim 后端**并确认网关熔断复位 → 重跑 `node-api`，使理赔模块可测（当前智能理赔前端亦受 503 牵连）。
2. **按方案 A 或 B 修复采购 E2E / 定义漂移** → 重跑 `py-core` 的 `two-scenario`，目标 90/90。
3. UI 探针 Chromium 二进制已就绪，后续可在本机直接跑 `run.sh node` 与 `ui/*.mjs` 做全量 UI 回归（本 run 已跑通 smoke-render + 全菜单回归）。
4. 上述两项闭合后，再做一次全量门禁 + 全量集成 + 全量 UI 的"全绿"快照，再统一提交。
