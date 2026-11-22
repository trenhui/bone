# 报案 1:0..N 前后端联调报告

- 日期：2026-11-22
- 变更：`claim-report-case-one-to-many`（分支 `comet/claim-report-case-one-to-many`）
- 目标：在真实服务、真库、真实浏览器上验证「报案详情 + 追加赔案」前后端全链路，而非仅靠单测。

---

## 1. 联调环境编排（为什么不能用现成的 8888/8087）

联调开始时，本机所有现成服务都属于**另一个会话的 worktree** `bone-wt-claim-full`（分支 `comet/claim-id-linkage`）：

| 端口 | 归属 | 说明 |
|---|---|---|
| 8888 网关 | 对方 worktree | 路由指向对方 8087 |
| 8087 claim | 对方 worktree | 跑对方实现（`/api/v1/claim-reports/...`，接口面与本分支不同） |
| 3000 shell / 3013 claim 前端 | 对方 worktree | 加载对方前端代码 |

直接复用会测到对方的实现。因此**另起一套我方独立栈**，全程不改对方任何进程：

| 组件 | 我方端口 | 构建/来源 | 关键配置 |
|---|---|---|---|
| claim 服务 | **8097** | 本 worktree `mvn -o package` 的 fat jar（含 `ClaimReportController`/`ClaimReport.class`） | `--server.port=8097`，连共享 `bone` 库 |
| 网关 | **18888** | 复用现成 gateway jar（其 `application.yml` 支持 `${BONE_CLAIM_URI}` 覆盖） | `BONE_CLAIM_URI=http://127.0.0.1:8097` |
| claim 前端 | **3010** | 本 worktree 源码 + 临时 vite 配置（`/tmp`，不入库） | `proxyTarget=http://127.0.0.1:18888`，端口取 CORS 白名单内的 3010 |

### 判别证据：18888 确实命中我方 8097，而非对方 8087

对同一不存在的报案号 `RP20260101ZZZZZZZZ9999`：

| 端口 | 响应 errorCode | message |
|---|---|---|
| **18888（我方网关）** | `SC_CLAIM_REPORT_NOT_FOUND` | `SC_CLAIM_REPORT_NOT_FOUND: RP...` |
| **8097（我方服务直连）** | `SC_CLAIM_REPORT_NOT_FOUND` | `SC_CLAIM_REPORT_NOT_FOUND: RP...` |
| 8888（对方网关） | `COMMON_NOT_FOUND` | `请求地址不存在:api/v1/claims/reports/RP...` |
| 8087（对方服务直连） | `COMMON_NOT_FOUND` | `请求地址不存在:...` |

我方网关与我方服务响应完全一致；对方服务**根本没有** `/claims/reports/{reportNo}` 端点。链路归属无疑。

---

## 2. 后端 API 联调（20/20 通过）

脚本把 `ClaimLifecycleE2ETest.reportDetailAndAppendCaseEndToEnd` 翻译成真实 HTTP 请求，经 18888 打我方 8097。

| 步骤 | 断言 | 结果 |
|---|---|---|
| 1 建案 | HTTP 201；`claimNo` 以 CL 开头；`reportNo` 以 RP 开头；两号不等 | ✅ `CL202611227GCWYERJSZ2MTA` / `RP202611225S5C9NF7H97DKP` |
| 2 报案详情 | `status=REPORTED`、`caseCount=1`、`allCasesFinished=false`、`policyNo/planId` 从报案继承、`cases[0].claimNo`=首案 | ✅ 全部命中 |
| 3 追加赔案 | HTTP 201、信封 `code=201`、新 CL 号、回报同一报案号 | ✅ `CL2026112286KEXTDFQAWKFX` |
| 4 幂等重放 | 同键复用首笔追加案，详情 `caseCount` 仍为 2 | ✅ 未重复赔付 |
| 5 400 | 请求体带 `policyNo` → `SC_CLAIM_REPORT_CONTEXT_MISMATCH` | ✅ |
| 6 404 | 不存在报案号 → `SC_CLAIM_REPORT_NOT_FOUND` | ✅ |

---

## 3. 前端真实浏览器联调（Playwright / Chrome headless）

我方前端 3010 独立模式（`<BrowserRouter basename=/sclaim/claim>`，故 URL 为 `/sclaim/claim/...`），token 注入 localStorage。

- **`GET /claims/reports/{reportNo}` 真实发出 2 次，均 200**（追加页 + 提交后刷新）。
- **追加页渲染完整**：`正在为报案 RP... 追加赔案`、保单号 `E2E-POLICY-0001`、`险种 OUTPATIENT`、`已建赔案 2 笔`、事故上下文继承说明文案。证明前端 `reportDetail()` 契约与后端一致。
- **0 console error / 0 pageerror**（修复完成后）。
- 前端 `tsc --noEmit` 零错误。

### 联调中发现并修复的真实缺陷：稳定幂等键超长撞穿 DB 列宽

**现象**：浏览器点「追加赔案」，POST 返回 `400 COMMON_VALIDATION_FAILED`「数据违反完整性约束」；但同一请求体用短 UUID 键 curl 直连却 201 成功。

**定位过程**（非猜测，逐层收敛）：
1. 首次 403 → 浏览器 `Origin: http://localhost:3010` 不在网关 CORS 白名单（白名单 3000–3013，3010 在内但初次误用 3015）。改用 3010 后 403→400。
2. 400 请求体 `{"invoiceCode":...,"invoiceNo":...,"items":[{"category":"SERVICE","itemName":...,"amount":321.5}]}` 结构合法；同体换短键成功 ⇒ 问题在**幂等键本身**。
3. 查库：`t_claim_case.idempotency_key` / `t_claim_report.idempotency_key` 均 `varchar(64) NOT NULL`；契约 §10.5 OpenAPI 也写 `maxLength: 64`。
4. 量前端生成的键：`claim:append:RP202611225S5C9NF7H97DKP|044052447346|C1795352447346|321.50` = **72 字符 > 64** ⇒ insert 违反 NOT NULL 列宽约束。

**为什么单测全绿**：`idempotencyKeys.test.ts` 把超长格式硬编码成断言期望（`"claim:append:RP...|04400001|12345678|10.00"`），只测确定性、长度方案例从不覆盖，等于把 bug 钉成预期。

**修复**（本 worktree）：
- `claim.ts` 新增 `boundedStableKey(prefix, body)`：≤64 原样返回（保留既有短键格式与可读性），超长保留前缀 + FNV-1a 32 位哈希（定长 8 十六进制）。`stableAppendCaseKey`/`stableSubmitKey`/`stableClaimActionKey` 统一收口。
- 测试先红后绿：新增「所有稳定键 ≤64 且超长仍确定性/有区分度」断言，修复前 421 字符红、修复后 4/4 绿。
- 契约 §10.3 补长度硬约束与实现口径。

**修复前后对照（同库同服务同请求体）**：

| | 幂等键 | 长度 | 结果 |
|---|---|---|---|
| 修复前 | `claim:append:RP...\|044052447346\|C1795352447346\|321.50` | 72 | 400 数据违反完整性约束 |
| 修复后 | `claim:append:8fc47fd3` | 20 | **201 成功** |

浏览器重跑：POST 201，页面显示「已追加赔案（CL202611222H3390B85VVDJD）」，0 console 错误；DB 对账该报案累计 7 笔赔案（联调真实写入）。

---

## 4. 结论与残留

**结论**：报案 1:0..N 前后端链路在真实浏览器 + 真库 + 独立网关下完整打通；后端 20/20、前端 0 错误；并借联调发现并修复了一个单测无法覆盖的契约缺陷（幂等键超长）。

**残留（不属本功能缺陷）**：
- 联调在共享 `bone` 库写入了若干测试赔案（CL 前缀），均为合法数据，未清理（库本就是测试库）。
- 独立栈（8097/18888/3010）为本次联调临时进程，联调结束后按需要停止；不写入仓库配置。
- 进度页 `/progress` 需 `claimId` 参数查询，仅传 reportNo 显示空查询态，是符合设计的空态，非缺陷。
