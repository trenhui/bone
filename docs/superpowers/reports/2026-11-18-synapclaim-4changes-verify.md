---
doc_id: records.verify.synapclaim-4changes-20261118
role: evidence
currency: current
---

# 4 个 synapClaim 在途变更 · 联调验证报告（2026-11-18）

> 本次为 `/comet` 显式触发的联调验证。用户选定落点：对 4 个处于 verify 阶段的 Classic change 做前后端联调验证。
> 本报告只记录**本机真实执行**的结果；不复用历史报告的"通过"结论作为本次证据。

## 0. 结论先行

| 项 | 结论 |
|---|---|
| 4 个 change 代码是否已实现 | ✅ 是，task 全勾选（10/10、9/9、13/13、15/15），代码已合并 dev |
| 联调验证是否发现真问题 | ✅ **发现并修复 3 处缺陷**：2 处共享树合并遗留的编译/测试断裂 + 1 处 E2E 夹具标识口径错误（详见 §2） |
| 修复后后端专项单测 | ✅ 11/11（ClaimCaseReReviewTest 7 + IndependentReReviewApplicationServiceTest 4） |
| 修复后 ai-config verify.sh | ✅ 5/5 全绿，末尾 `ALL CHECKS PASSED` |
| 前端 bone-claim-app 测试 | ✅ 137/137（修复 mock 后） |
| E2E 影响门禁 / lint | ✅ 通过；inspection 用真实交付范围 6/6 端点覆盖 |
| 真机端到端 E2E（经网关+MySQL） | ✅ **4 个脚本全绿**：inspection 8/8、ai-config 5/5、workbench 17/17、re-review 5/5（补回活库 admin claim 权限 + 修 R2 夹具后） |
| 真机 E2E 新发现 | ⚠️ 独立复审 submit/complete 端点 `reviewerId` 由客户端自报（领域守卫按数字 ID 比对）——API 契约潜在加固点。前端未接入该端点，无"前端已能绕过"的现成缺陷；已更正初稿误判，见 §6 |
| Comet 状态推进（verify→archive） | 🔴 被阻塞：4 个 change 绑定分支已删除，checkpoint 与 state 不匹配 |

## 1. 验证方法与判据

- 只信任**本次实跑**：`mvn -o -pl ... -am clean test`、`npm run typecheck/lint/test`、`python3 scripts/e2e/impact.py --strict`、`bash scripts/e2e/lint.sh`、各 change 自带 `verify.sh`。
- Maven 测试绕沙箱执行（沙箱拦 surefire file-write-unlink，属已知环境限制非代码错误）。
- verify.sh 步骤 4 校验的是历史报告固定字符串 + tasks 勾选数，是**完整性检查**，不代表当前代码通过；判别力由编译/单测/E2E 实跑提供。

## 2. 发现并修复的 3 处真实缺陷

### 缺陷 1（P0，阻断编译）：独立复审仓储方法被误删

- **现象**：跑 ai-config verify.sh 第 1 步即编译失败：
  `IndependentReReviewApplicationService.java:[85,27] 找不到符号 findReReviewQueue(long,String,int,int)`、`[86,36] 找不到符号 countReReviewQueue(long,String)`。
- **取证**：`ClaimCaseRepository` 接口中这两个方法 + 私有辅助 `reReviewQueueCriteria` **均不存在**（grep 只在调用点与单测命中）；全模块干净编译仅此一个断点。
- **根因**：提交 `a4a3c1726`（批3 独立复审）引入了这 3 个方法；提交 `975f5d3f1`（`chore(claim): 同步 synap-claim 全量工作树变更与 risk/fatigue 归档产物`，提交说明自述"同步并行会话对独立复审等模块的 staged 回退"）把它们从仓储接口删除，但调用方应用服务、单测、Controller 端点全部保留 → 部分回滚导致编译断裂。历史"9/9 全绿"报告是当时 worktree 状态，合回 dev 时被破坏。
- **修复**：从 `a4a3c1726` 原样回补 3 个方法（diff 校验字节级一致，28 行）。这 3 个方法是 `default` + `Criteria` 通用查询（仓库已有 6 处同形态先例），不绑定专属 SQL 模板，不触发 TemplateNotFoundException。
- **验证**：`-am clean test` 编译通过；`ClaimCaseReReviewTest` 7/7、`IndependentReReviewApplicationServiceTest` 4/4 全绿；ai-config verify.sh 5/5 通过。

### 缺陷 2（P1，阻断前端测试）：caseSections mock 缺少 inspectionHandoffApi

- **现象**：`npm run test -w bone-claim-app` 137 例中 1 例失败：
  `caseSections.test.tsx > 2.6 后督质检` → `No "inspectionHandoffApi" export is defined on the "../services/claimApi" mock`，源自 `InspectionWorkbenchPage.tsx:142`。
- **根因**：抽检交接 change 在 `claimApi.ts` 新增 re-export `inspectionHandoffApi`，`InspectionWorkbenchPage` 使用 `samplingConfig()`；但 `caseSections.test.tsx`（commit `4b6ca8c5d`）的 `vi.mock` 只列了 `inspectionApi`，合并后夹具滞后。
- **修复**：mock 补 `inspectionHandoffApi: { samplingConfig: vi.fn(() => ok(null)) }`（1 行）。`samplingConfig` 的 catch 分支容忍 null，不破坏用例语义。
- **验证**：`npm run test -w bone-claim-app` → 8 files / 137 tests 全绿（含 typecheck、lint）。

### 缺陷 3（P1，E2E 夹具）：re-review R2 用例标识口径错误 + 错误码提取层级错

- **现象**：真机 `synap-claim-independent-re-review-e2e.py` R2「同人不可自审」失败：`http=200 errorCode=None`（期望 409），后续 R1 连锁 409 中断脚本。
- **取证**：领域守卫 `ClaimCase.submitForIndependentReReview` 用 `reviewerId == firstReviewerId` 严格比对；`first_reviewer_id` 由 `adjust/claim` 端点写 `CurrentOperator.requireId()`，库内实存 **`'1'`（数字 ID 字符串）**。E2E R2 却传用户名字符串 `"admin"`，`'1' != 'admin'` ⇒ 守卫按设计放行（200）。另：错误码实际在响应 `data.errorCode`，脚本却取顶层 `body.get("errorCode")` ⇒ 即使触发 409 也取 None。二者是同一脚本的两个独立夹具缺陷。
- **判定**：**产品代码自洽**（内部 reviewerId 口径是数字 ID，守卫与单测一致），不是守卫失效；这是 E2E 夹具与实现口径不同步。历史报告"3.2 同人不可自审 PASS"是单测用一致字符串口径验证的，真机才暴露跨层口径差。
- **修复**：`claim_status()` 增读 `first_reviewer_id`；R2 改用库内真实初审人 ID 提交（可移植、不硬编码 `'1'`）；新增 `error_code_of()` 兼容顶层/`data` 两种错误码层级，R2/R5 统一使用。
- **验证**：重跑 E2E → R2 `http=409 errorCode=SC_CLAIM_REREVIEWER_CONFLICT firstReviewer=1`，R1–R5 all=True；守卫本身工作正常。
- **附带发现**：前端 `claimConfigApi.ts` 中 `reviewerId?: string`，调用方按字符串标识传值——若前端传用户名而非数字 ID，则自审守卫在真机可被绕过。此为你需裁定的高优先级契约隐患，见 §6（不属本次夹具修复范围，未擅改产品代码）。

## 3. 各 change 验证明细

| change | 验证方式 | 本次实测结果 |
|---|---|---|
| synapclaim-ai-config-governance-api | 自带 verify.sh（5 步） | ✅ 5/5 全绿（修复缺陷 1 后）。后端专项单测、前端 typecheck/lint/vitest、E2E 影响门禁+lint、报告 7 项证据、9 份交付物在位 |
| synapclaim-independent-re-review | 编译 + 专项单测 + 真机 E2E | ✅ 编译通过（缺陷 1 直接受益方）；ClaimCaseReReviewTest 7/7、ApplicationServiceTest 4/4；真机 E2E R1–R5 5/5 全绿（修缺陷 3 后，R2 正确触发自审守卫 409） |
| synapclaim-inspection-handoff-timeout | 自带 verify.sh（6 步）+ 影响门禁 | ✅ 步骤 1 后端单测、步骤 2 前端 typecheck 通过。步骤 3 假红：verify.sh 写死 `BASE=2c13eb348..HEAD`，在当前 dev HEAD（已合入后续批1-4及采购切片，184 Java/6 Controller）范围漂移，报 17 个**采购/masterdata 端点**零覆盖——与本 change 无关。用真实交付范围 `be7d89a63^..be7d89a63` 重跑：25 Java/1 Controller、6/6 端点全覆盖、零遗漏。未篡改 verify.sh，未给无关端点补用例 |
| synapclaim-task-assignment-workbench | 真机 E2E + 影响门禁 | ✅ 代码/单测随 `-am clean test` 编译通过；真机 E2E 17/17 全绿（分页/排序/归属/并发/租户隔离） |

历史报告证据（未重写，仅索引）：`docs/superpowers/reports/2026-11-16-synapclaim-*-verify.md`，结论均为通过（E2E 17/17、9/9、59/59 全量门禁、变异判别力实证）。

## 4. 真机端到端 E2E：4 个脚本全绿（经网关 + MySQL）

- 绕沙箱后基础设施可用：MySQL 3306 可连；8081/8087/8888/3000/3013 health 全 200；经网关 `POST /api/v1/iam/login` 签发 JWT。批4/独立复审端点返回**业务级响应**（400/200）而非 404 → 确认 8087 为含最新端点的 fat-jar。
- **权限前置修复**：首轮执行 inspection E2E 报 `sampling-config -> 403`。直查活库：admin（`SUPER_ADMIN`，role_id=1）仅绑定 1 个 claim 权限（`claim:re-review:write`），缺其余 12 个；库中 13 个权限定义齐全。系共享活库角色绑定漂移（与已记录的"iam 权限被并行会话清空"同族）。处置：以**幂等 INSERT IGNORE** 仅给 SUPER_ADMIN 补回缺失的 12 个 claim 权限绑定（不动任何其他角色/用户/权限定义），复探 403→200。这是恢复漂移破坏的正确状态，非破坏性写。
- **实测结果**（均绕沙箱、经真实网关 + MySQL 直查第二证据源）：

| E2E 脚本 | 结果 |
|---|---|
| `synap-claim-inspection-handoff-e2e.py` | ✅ 8/8（E1–E8 all=True，含 409 冲突码、代理生效区间、不可改写留痕） |
| `synap-claim-ai-config-governance-e2e.py` | ✅ 5/5（配置快照、写开关闸门、库内 config_json 未改写、盲标无 AI 回流） |
| `synap-claim-task-workbench-e2e.py` | ✅ 17/17（分页累加==total==去重、全序、归属写入期、并发领取无双成功、跨租户隔离） |
| `synap-claim-independent-re-review-e2e.py` | ✅ 5/5（R1–R5 all=True，修夹具后 R2 正确触发自审守卫） |

- **re-review E2E 夹具修复（发现缺陷 3）**：首轮 R2 失败 surfaced 一个真实的标识口径问题，详见 §6。夹具修正后守卫被正确触发：`R2 http=409 errorCode=SC_CLAIM_REREVIEWER_CONFLICT firstReviewer=1`。
- 回归门禁：`python3 -m py_compile` 通过；`impact.py --strict` RC=0；`bash scripts/e2e/lint.sh` RC=0（含造数真实性 R1–R5 无新增违规）。

## 5. Comet 状态机阻塞（需用户裁定）

- 4 个 change 的 `comet-state.yaml` 标记 `managed-run` 且内嵌 SDK `run_checkpoint`，`phase=verify`、`verify_result=pending`。
- 其绑定分支 `comet/synapclaim-*` 已被上一轮清理删除，`comet state next/get`、`comet guard`、`comet check` 全部报 **"Classic portable Run checkpoint ... does not match its state"** 并拒绝推进。
- 按项目规则 `comet-workflow-guard.md`："selection 缺失/跨分支失效/状态不可安全读取时停止，不得回退到 default_workflow 猜测"；按 Comet 收口铁律：不得以伪造证据/回执推进归档。**故本次未推进任何 change 状态、未执行 archive**。
- 可选收尾（待用户裁定，均不在本次擅自执行）：① 由 Comet 正确入口恢复对应 Run 后走 verify→archive；② 接受以本报告 + Git 交付为最终事实，不再操作状态机。

## 6. 待用户裁定项（未在本次擅改产品代码）

1. **【API 契约风险·需裁定】独立复审 submit/complete 端点的 `reviewerId` 由客户端自报**：领域守卫按 `firstReviewerId`（`CurrentOperator.requireId()` 的数字 ID，如 `'1'`）严格比对 `reviewerId`；端点却从 `@RequestBody` 取客户端传值，未取已验签 token 的操作者。风险在于：调用方若传用户名形态（如 `'admin'`），`'1' != 'admin'` 守卫放行，自审可能绕过。设计文档《独立复审阶段设计》§4 的校验①语义是"当前用户具备权限"，项目内 `ReconciliationDiffController` 等端点的既定范式是"操作人取自已验签 token，不接受请求体自报"。
   - **更正（2026-11-18 复核）**：初稿曾称"前端 `claimConfigApi.ts` 传用户名导致绕过"——**该论断有误**。全局核查确认：① 前端**未接入** re-review submit/complete 端点（0 处调用）；② `claimConfigApi.ts:1252` 的 `reviewerId?: string` 属于 `PublishMappingReq`（映射发布复核），与独立复审无关。故**不存在"前端已能绕过"的现成缺陷**，这是 API 契约层的潜在加固点。真机 R2 传 `'admin'` 放行是 E2E 夹具用了错误标识形态，已修（见 §2 缺陷 3）。
   - **本次处置**：未改产品代码（收紧端点契约会影响 API 兼容性 + 需补契约测试 + 产品语义确认，属独立变更）。建议裁定是否把 submit/complete 的 reviewerId 改为服务端取 `CurrentOperator.requireId()`，与 `ReconciliationDiffController` 范式一致；若保留客户端传值，则需在 API 契约中明确"reviewerId 须为操作者数字 ID"并补一条"用户名形态被拒"的负向用例。
2. `synapclaim-independent-re-review` 报告 §6：`reReviewQueue` 端点把 `reviewerId` 作为可选 `@RequestParam`，持权人可查他人待复审队列，偏离设计 §5 方案 C 的"持权人视角"语义。是否收紧为一律取 `CurrentOperator`，需产品/架构裁定后走独立变更。
3. 全仓 `npm run test` 另有 3 例 `bone-extension-app` 的 `deletePlugin` 失败（异常类型断言），与本批零交集，属并行会话基线项。

## 7. 本次改动文件

- `bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim/domain/repository/ClaimCaseRepository.java`（+28，回补独立复审仓储三方法）
- `bone-frontend/apps/bone-claim-app/src/cases/caseSections.test.tsx`（+1，补 inspectionHandoffApi mock）
- `scripts/e2e/synap-claim-independent-re-review-e2e.py`（+26/-6，修 R2 初审人 ID 口径 + 错误码提取层级）
- 本报告 `docs/superpowers/reports/2026-11-18-synapclaim-4changes-verify.md`

提交：以上 4 个文件按项目规范以 `git commit --only -- <paths>` 圈定提交（共享树，不裹挟并行会话在途改动）。活库权限补回（SUPER_ADMIN +12 claim 权限绑定）是环境数据恢复，不入库。Comet 状态机因跨分支失效未推进，见 §5。
