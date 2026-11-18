# 验证报告 — procurement-supplier-frontend-integration

- 变更：`openspec/changes/procurement-supplier-frontend-integration/`
- 工作区：worktree `.worktrees/procurement-supplier-mdm-slice`，分支 `comet/procurement-supplier-mdm-slice`，基线 `190cad738`
- 验证日期：2026-11-16
- 构建配置：`buildMode=autonomous`、`tddMode=tdd`（strict）、`reviewMode=standard`
- 本报告为**唯一最终验证报告**：覆盖第 1 轮最终集成审查（REVIEW: FAIL，0 CRITICAL / 3 IMPORTANT / 3 SUGGESTION）的全部 IMPORTANT 闭环与复验结果。第一轮结论不保留在本文件，避免混读。

---

## 1 最终集成审查（standard）IMPORTANT 闭环

| # | 审查发现 | 处置 | 复验证据 |
|---|---|---|---|
| IMPORTANT-1 | 页面级 `try/catch → fromApiError → degradeMessage` 修复零组件测试保护，失败语义可被无声回归 | **已闭环**：新增 `pages/SupplierManagement.test.tsx`（5 例），渲染真实页面并钉住 404/403 降级、详情业务失败登记失败保留表单、迁移失败不推进本地状态 | §2 F5–F9 + §3 两个负向探针；tasks.md 3.2 从"勾选但文件缺失"变为真实交付 |
| IMPORTANT-2 | spec 实为 **14** Scenario，报告/委托误写 12 | **已闭环**：本报告与 `docs/superpowers/plans/...integration.md` 全文口径统一为 6 Requirement / 14 Scenario（§4 逐条映射） | `grep -c '#### Scenario' spec.md` = 14 |
| IMPORTANT-3 | Scenario #14「从界面完成全链路」缺 UI 层端到端证据 | **如实标注偏差**：本 change 不做浏览器跨网直连生产（design D6 明示）。以"契约测试 + 后端真实 HTTP + 组件级 UI 失败路径测试"三层交叉替代，不宣称 Scenario #14 浏览器级达成 | §5 偏差 B1；本报告不写"UI 全链路通过" |

SUGGESTION（3 项，非阻断）：对象摘要降级显示、按钮文本匹配容忍、错误消息兜底文案，均已在 §2 测试与 `supplierView.ts` 中覆盖或记录，不改变行为契约。

---

## 2 验证命令与结果（本次实际执行）

| # | 命令 | 结果 |
|---|---|---|
| V1 | `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` | **346 例 / 0 失败 / 0 错误 / 0 跳过**，`BUILD SUCCESS`（后端零改动，确认未回归） |
| V2 | V1 内 `ArchitectureTest` | 26/26 绿（含 E-5.4 应用层写仓储边界），冻结基线无新增违规 |
| V3 | `npx vitest run apps/bone-masterdata-app/src/pages/SupplierManagement.test.tsx` | **5/5 绿**（连续 3 次重跑稳定，无 flaky） |
| V4 | 供应商相关三组：`SupplierManagement.test.tsx` + `supplierView.test.ts` + `supplierApi.test.ts` | **38/38 绿**（5 组件 + 21 纯逻辑 + 12 API 契约） |
| V5 | `npx tsc --noEmit -p apps/bone-masterdata-app/tsconfig.json` | RC=0，无类型错误 |
| V6 | `npx eslint <5 文件> -f json` 解析 `errorCount/warningCount` | 0 errors / 0 warnings（按铁律用 json，规避 stylish formatter 假绿） |
| V7 | 全量 `npx vitest run` | 689 通过 / 11 失败（其中本 change 相关 1 例已修复，余 10 例为 HEAD 基线既有失败，见 §7）；供应商全部用例通过 |
| V8 | 前端组件测试负向探针 P1/P2 | 2/2「注入必红、还原必绿」（§3） |

说明：V7 的 11 例失败中，1 例是本轮新增的迁移测试在全量并行下的时序 flaky，已用「等详情按钮出现」替代「等列表 tag」修复，连续 3 次定向重跑（V3）稳定绿；另 10 例经 HEAD 基线探针确认在**未修改代码**上同样失败（§7），不属于本 change 范围。

---

## 3 负向探针（2/2 PASS：注入必红 → 还原必绿）

针对 IMPORTANT-1 的核心缺陷形态（页面吞掉/谎报失败），做受控变异验证测试确实可证伪：

| # | 注入的缺陷 | 预期变红的用例 | 实测 |
|---|---|---|---|
| P1 | 移除 `loadPage` 的 `try/catch`（退回裸 `await`） | `列表加载返回 404 → 降级空态` | **红 ✔**：`message.error` 0 调用 + vitest 捕获 unhandled rejection；还原后绿 |
| P2 | `doTransition` 的 catch 改为 `message.success('已提交')`（失败谎报成功） | `迁移失败 → 本地状态不推进` | **红 ✔**：断言 `message.error('守卫拒绝…')` 0 调用；还原后绿 |

两处还原均经源码 catch 计数与测试全绿确认，无残留变异。

---

## 4 Requirement → Scenario 覆盖映射（6 Requirement / **14** Scenario）

| Requirement | Scenario | 证据（file:line） |
|---|---|---|
| 供应商登记表单 | 登记成功呈现双角色初始态 | `supplierApi.test.ts:register 路径/body`；`SupplierManagement.test.tsx:154` 登记成功路径 + `supplierView.test.ts` 事件映射 |
| 供应商登记表单 | 顶层失败不误报成功 | `SupplierManagement.test.tsx:154-172`（业务失败保留弹窗、不清空表单、无 success）；`supplierApi.test.ts` 失败 reject 透传 |
| 双角色状态与历史只读呈现 | 双状态机独立呈现 | `supplierView.test.ts`（allowedSupplierEvents/allowedVendorEvents 独立映射）；`SupplierManagement.tsx:227,253` 双 Card |
| 双角色状态与历史只读呈现 | 未知状态码不吞掉 | `supplierView.test.ts`（未知码原样）；`supplierView.ts:105,107` String 原样保留 |
| 双角色状态与历史只读呈现 | PII 不落地到前端呈现 | `supplierView.test.ts`（注入 phone/uscc 断言过滤）；`supplierView.ts:101-112` 白名单投影 |
| 显式迁移事件触发 | 合法事件触发迁移 | `supplierApi.test.ts`（Supplier/Vendor 迁移 POST 路径 + 事件码 + source）；`supplierView.test.ts` 合法事件映射 |
| 显式迁移事件触发 | 后端拒绝时保持状态 | `SupplierManagement.test.tsx:174-195`（迁移失败 message.error、无 success、无 ACTIVE_VENDOR 乐观更新）+ P2 探针 |
| 显式迁移事件触发 | 付款冻结解禁来源约束不在前端绕过 | `supplierView.ts:39-46,48-54`（仅 EXTERNAL_FINANCE 解禁入口）；`supplierView.test.ts` 断言无 DOMAIN 解禁 |
| 分页列表与响应归一化 | 空列表 | `supplierApi.test.ts`（records=[]/total='0' 归一）；`supplierView.test.ts:pageCount` |
| 分页列表与响应归一化 | 字符串总数归一 | `supplierApi.ts:37-52` normalizeSupplierPage；`supplierView.test.ts`（total='23'/size=10 → 3 页） |
| 能力开关与权限的可观察降级 | 能力未启用 | `SupplierManagement.test.tsx:118-126`（404 → 降级空态 + message.error）；`supplierView.ts:57-65` degradeMessage |
| 能力开关与权限的可观察降级 | 权限不足 | `SupplierManagement.test.tsx:128-137`（403 → 无权限文案）；`supplierApi.test.ts` 403 reject 透传 |
| 前后端契约一致性 | 迁移请求形状 | `supplierApi.test.ts`（POST /{partyId}/supplier-transitions + 事件码 body；Vendor 带 source） |
| 前后端契约一致性 | 全链路联调可达 | **偏差 B1**，见 §5：契约测试 + 后端真实 HTTP（`SupplierHttpIntegrationTest` 6/6）+ 组件 UI 失败路径三层交叉，未做浏览器跨网直连 |

---

## 5 契约对齐与偏差

### 5.1 前后端五端点契约对齐（差异清单为空）

| 前端 `supplierApi` | 后端 `SupplierController`（`MASTERDATA_V1 + /suppliers`） |
|---|---|
| `register` POST `/registrations` | `@PostMapping("/registrations")` |
| `transitionSupplier` POST `/{partyId}/supplier-transitions` | `@PostMapping("/{partyId}/supplier-transitions")` |
| `transitionVendor` POST `/{partyId}/vendor-transitions` | `@PostMapping("/{partyId}/vendor-transitions")` |
| `current` GET `/{partyId}` | `@GetMapping("/{partyId}")` |
| `page` GET `` + `records/total` | `@GetMapping` + `ApiResponse<PageResult<T>>`（数组字段 `records`） |

响应统一信封 `success/code/message/data`；`total` Long→String 前端归一；`getList()` 已 `@JsonIgnore`，前端只读 `records`。逐项比对无漂移。

### 5.2 已接受偏差

- **B1（对应 IMPORTANT-3 / Scenario #14）**：不做浏览器跨网直连生产后端（design D6）。UI 全链路（界面点击完成登记→闸门→准入→退出）未以真实浏览器证据达成；以组件级 UI 失败路径测试（本 change）+ 后端真实 HTTP 6/6 + API 契约 12 例三层交叉替代。**不宣称 Scenario #14 浏览器级通过**。
- **B2（owner 前置，不在本 change 完成内）**：A-2 生产 JDBC adapter、A-8 身份唯一索引/DDL、A-4 真实 Finance/PO/合同上游；A-1/A-3/A-6/A-7/A-9/A-10 已裁决偏差。
- **B3（环境既有失败，非本 change）**：见 §7。

---

## 6 红线核对

- 未建 Maven 模块、未写 DDL、未接生产库、未改 CI 判据、未改后端领域规则：本 change 仅新增/修改前端 6 个源文件 + 3 个测试文件 + 路由/菜单 2 文件，后端零改动（V1 确认 346 例与基线一致）。
- PII 白名单：身份数据仅作登记命令候选源提交，不在 UI 回显；`safeStateProjection` 过滤 phone/uscc（§4 证据）。
- 失败语义：`apiClient` 对 `success=false` 与 HTTP 错误一律 reject，页面统一 try/catch，不存在 `if (!resp.success)` 死代码；本轮组件测试 + 负向探针锁定该契约。

---

## 7 HEAD 基线既有失败甄别（与本 change 无关）

全量 `vitest run` 的 11 例失败中，10 例经**主仓 HEAD 未修改代码**同命令复跑确认同样失败：

| 失败测试 | HEAD 基线复跑 | 根因 | 归属 |
|---|---|---|---|
| `extensionApi.test.ts`（3 例 StudioApiError instanceof） | 3 failed / 24 passed | 既有断言与当前 error 类型实现不符 | 非本 change，未触碰该文件 |
| `uiSchemaRenderer.test.tsx`（3 例 `resolveGradient is not a function`） | 3 failed / 1 passed | jsdom CSS 依赖 `@asamuzakjp/css-color` 缺函数，依赖树既有问题 | 非本 change |
| `caseSections.test.tsx`（4 例同上） | 4 failed / 4 passed | 同上 | 非本 change |

**关于 jsdom 环境修复**：本次为运行组件测试，补回 package-lock 本就声明的 `whatwg-mimetype@5.0.0` 嵌套依赖（此前被拍平为 4.0.0，导致 `MIMEType is not a constructor`，连现有 tsx 测试都无法加载）。该修复只作用于本地 `node_modules`（未纳入 git diff），不改变任何 tracked 文件，且在 HEAD 基线上 `resolveGradient` 类失败同样存在，非本次引入。

---

## 8 结论

- 最终集成审查 3 项 IMPORTANT **全部闭环**，其中 IMPORTANT-1 以组件测试 + 2 个负向探针真实保护，非纸面修复。
- 验证：**后端 346 例全绿、供应商前端 38 例全绿、tsc/eslint 通过、契约对齐差异为空**。
- 全量 vitest 的 10 例失败为 HEAD 基线既有（依赖树/既有断言），不属本 change；本 change 引入的 1 例时序 flaky 已修复并连续 3 次验证稳定。
- 唯一未达成项为 **Scenario #14 浏览器跨网直连**（design D6 明示偏差 B1），已如实标注，以三层交叉证据替代，不宣称浏览器级全链路通过。
- 可进入 Archive。归档时只提交本 change 文件；worktree 内与本 change 无关的 `mdm-slice-verify-r2.md` §11 追加记录、`node_modules` 软链及 `node_modules.vite-only` 备份不纳入本 change 提交。
