# 验证报告：procurement-transaction-pr-po-slice

> Verify 阶段报告。覆盖 04 采购交易 PR+PO 切片的后端模块、前端独立子应用、契约对齐、门禁与负向探针验证证据。
> 验证日期：2026-11-17。Change：`procurement-transaction-pr-po-slice`。

## 结论

**通过**。25/25 任务完成；后端 `clean test` 全绿（含 13 条 ArchUnit 分层规则）；采购前端子应用 48 例全绿、全 workspace typecheck / eslint(json) 0 错；三个负向探针注入必红 / 还原必绿；OpenSpec strict validate、G32 菜单路由一致性、G40 权限码、G41 运行时拓扑四门禁通过。接受偏差见 §6，均在范围声明内且已在 design/proposal 标注。

## 1. 后端实现与测试

- 模块：`bone-apps/bone-procurement`（pom 聚合器）→ `bone-procurement-transaction`（DDD 四层）+ `bone-procurement-app`（薄启动壳，`ProcurementApplication` 对齐 `ClaimApplication` 装配口径）。
- DDL：`bone-init.sql` 新增 `t_proc_requisition` / `t_proc_po` / `t_proc_po_line` / `t_proc_price_snapshot`，含必备审计列、`uk(tenant_id,po_no)`、`uk(tenant_id,po_no,po_version)` 等唯一键；金额 `DECIMAL(19,4)`、数量 `DECIMAL(19,6)`、币种 `CHAR(3)`。
- 领域不变量：`Money`（scale=4 / HALF_UP / 跨币种抛错 / 逐行舍入再汇总）、PR/PO 显式迁移表（未列出即拒绝，非法迁移 409 + `currentState/attempted/allowed[]`）、`PriceSnapshot.computeHash`（SHA-256，输入含 poNo/poVersion/lines/tax/currency/fxAsOf/roundingMode/taxCodeVersion）。
- 应用服务：`RequisitionApplicationService`（建单/提交/审批/转 PO/取消，转单失败保持 `APPROVED`）、`PurchaseOrderApplicationService`（Vendor 闸门 + 法人隔离 + 金额一致 + 下达冻结快照 + 终态保护）、`OrderNoGenerator`（PR/PO 单号，冲突重试上限 3）。
- 端口：`VendorQueryPort` + `VendorQueryPortAdapter`（反射调用 `bone-masterdata` 的 `SupplierApplicationService.currentState`，bean 缺失/异常/状态空均 fail-closed 返回 `Optional.empty()` ⇒ 上层 31003）；测试用内存实现。
- 端点：2 PR + 5 PO，共 7 个；`bone.procurement.transaction.enabled` 默认 false，关闭不注册；全部 `@PreAuthorize`（6 权限码）；全局异常映射 31003/31004/31402/31403/31405/409。
  - **本期新增** `GET /api/v1/procurement/purchase-orders`（列表分页，tenantId+status+page+size，`PageResult` 用 records/total/page/size），补齐前端 `load()` 既有调用——原后端缺失该端点会导致交易控制台开启能力后仍永远显示「能力未启用」。该行为本就是已批准 spec `transaction-console/spec.md`「列表加载与分页归一化」的验收项。

**验证命令与结果**：

| 检查 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `JAVA_HOME=jdk21 mvn -o -f bone-apps/bone-procurement/pom.xml spotless:apply clean test` | BUILD SUCCESS；transaction **51** 例 / app **2** 例，0 failures / 0 errors |
| 架构门禁 | 上含 `ArchitectureTest`（@AnalyzeClasses `com.bone.procurement.transaction`） | **13** 条 ArchUnit 规则全绿（依赖方向、adapter 入站、仓储白名单、异常纪律、租户收敛、save↔publish、bean 名唯一、端点 @PreAuthorize、跨上下文 domain 越界） |
| DDL↔实体对账 | `ProcurementEntityDdlCoverageTest` | 3 例绿：4 张表齐全、必备列/精度匹配、金额列 DECIMAL(19,4) |
| 能力开关 | `ProcurementCapabilityToggleTest` | 2 例绿：默认 false、两 Controller 条件注解生效 |

## 2. 前端实现与测试

- 新建独立 qiankun 子应用 `bone-frontend/apps/bone-procurement-app`：端口 3014、activeRule `/procurement`、lifecycle 单对象范式（对齐 bone-iam-app / bone-claim-app）；`src/qiankun-env.d.ts` 声明 helper 子路径类型。
- 交易控制台 `PurchaseConsole.tsx`：404 → 「能力未启用」空态；403 → 只读禁用建单；迁移失败不本地推进状态、只显示后端错误；建单失败保留弹窗与字段值（测试实证）。
- 分层：`types/purchase.ts` 白名单投影、`services/purchaseApi.ts`（7 端点 + `normalizeTotal` 分页归一）、`pages/purchaseView.ts` 纯逻辑（node 可测，只读投影不内置守卫裁决）、组件测试。
- 供应商治理页 7 文件从 `bone-masterdata-app` 迁入（含 3 个测试），`useMessage` 经新 App 的 `MessageContext` 同构解耦；masterdata-app 已删除对应路由、死代码菜单组与文件。
- shell 4 处注册 + 硬编码清单 6 处 + 运行时拓扑图；2 个 migration SQL（6 权限码绑超管 + 「采购」目录/两叶子，`WHERE NOT EXISTS` 幂等，图标 `ShoppingOutlined` 在白名单）。

**验证命令与结果**：

| 检查 | 命令 | 结果 |
|---|---|---|
| 采购 app 单测 | `vitest run --root . apps/bone-procurement-app` | **5 文件 / 48 例全绿**（交易 4 + purchaseView 6 + 供应商页 38） |
| 采购 app 类型 | `npm run typecheck --workspace=bone-procurement-app` | RC=0 |
| 采购 app lint | `eslint apps/bone-procurement-app -f json` 解析 errorCount | 18 文件 **errors=0 / warnings=0** |
| 全 workspace 类型 | `npm run typecheck`（--workspaces） | 全部 app + packages 通过，RC=0 |
| 改动 app lint | shell / masterdata / procurement 三 app eslint -f json | 均 errors=0 / warnings=0 |
| OpenSpec | `openspec validate procurement-transaction-pr-po-slice --strict` | 通过（仅中文 spec 既有 RFC2119 英文关键词 warning） |
| G32 菜单路由 | `python3 scripts/check-menu-route-consistency.py` | 51 个微应用菜单项，0 死链 |
| G40 权限码 | `python3 scripts/check-seed-menu-permission.py` | 222 带权限菜单 / 103 权限点 / 0 孤儿 |
| G41 运行时拓扑 | `python3 scripts/check-runtime-topology.py` | 图内端口与代码真源一致（含新增 :3014 前端 / :8090 后端） |

**环境说明（非产品问题）**：worktree 的 `node_modules` 因一次离线 `npm install` 中断残留空包壳（`.bin` 缺失、`call-bind` 空目录、嵌套 `@vitest/mocker` 遮蔽顶层包）。已按主仓同版 lockfile 权威同步修复。全 workspace vitest 共 **700 通过 / 10 失败**，10 例分布在 `bone-claim-app/caseSections.test.tsx`、`bone-extension-app/extensionApi.test.ts`、`bone-metadata-app/uiSchemaRenderer.test.tsx` 三个本次未改动文件；在主仓未改动工作区复现同样失败 ⇒ 确认为存量基线失败，与本 change 无关。采购子应用 48 例全绿，供应商页迁移后 masterdata-app 相关用例全绿，证明无回归。

## 3. 负向探针（注入必红 / 还原必绿）

对 `PurchaseOrderApplicationService` 逐个注入守卫旁路，跑对应单测确认转红，trap 还原后全绿：

| 探针 | 注入手法 | 结果 |
|---|---|---|
| M4-1 供应商未开通 | `if (!vendor.isActiveVendor())` → `if (false)` | RED（NPE：未抛 31003）→ 还原 GREEN |
| M4-5 金额不一致 | 金额 `compareTo(...) != 0` → `&& false` | RED → 还原 GREEN |
| M4-6 终态回写 | `transition()` 内 CLOSED/CANCELLED 守卫 → `if (false)` | RED（`invalid_transition` 提前抛出）→ 还原 GREEN |

M4-2（法人不匹配 31004）、M4-10（缺快照 31405）由单元测试常态覆盖。证据日志：`/tmp/probe2-M4-1.log`、`/tmp/probe2-M4-5.log`、`/tmp/probe-m46.log`、`/tmp/probe2-restored.log`。

## 4. 前后端契约对齐（7 端点）

| 端点 | 前端 `purchaseApi` | 后端 Controller | 对齐 |
|---|---|---|---|
| POST `/requisitions` | `createRequisition` | `RequisitionController.create` | ✓ |
| POST `/requisitions/{no}/convert` | `convertRequisition` | `RequisitionController.convert` | ✓ |
| POST `/purchase-orders` | `createPurchaseOrder` | `PurchaseOrderController.create` | ✓ |
| GET `/purchase-orders`（含 tenantId） | `listPurchaseOrders` | `PurchaseOrderController.list` | ✓（本期补齐） |
| POST `/purchase-orders/{no}/submit` | `submitPurchaseOrder` | `PurchaseOrderController.submit` | ✓ |
| POST `/purchase-orders/{no}/send` | `sendPurchaseOrder` | `PurchaseOrderController.send` | ✓ |
| GET `/purchase-orders/{no}` | `getPurchaseOrder` | `PurchaseOrderController.detail` | ✓ |

请求字段、tenantId 传参（query param）、响应信封（`ApiResponse` + `PageResult` 的 records/total/page/size ↔ 前端 `normalizeTotal`/`normalizePurchasePage`）、错误码白名单逐项比对，差异仅 §1 所述列表端点补齐一项，已消除。

## 5. 独立审查与状态机证据

- Build 阶段审查模式 `standard`：edit-review 对 design.md 端点计数同步判定为 `non-behavioral`（行为已由批准 spec 覆盖），保留原批准与检查。
- 设计文档端点计数 6→7 触发 SDK「设计批准后变更」→ 用 `revise-design`（retain 实现/配置/决策）重开 Design，`propose-design` → `decide-design approved` → `complete-design` → 重新 `submit-plan`，原 Run 与全部实现/证据保留。
- 25/25 任务经 `comet state task-complete` 勾选；`complete-build` 通过；`comet guard build --apply` 真实执行 `clean test` 通过，阶段推进到 verify。

## 6. 接受偏差与本期边界（如实记录）

1. **供应商后端模块落点**：仍在 `bone-masterdata`（包 `com.bone.masterdata`），未按 §4.2 迁为 `bone-procurement-supplier-mdm`；本期不搬迁，作为后续独立 change 偿还。`VendorQueryPortAdapter` 以反射消费其 `currentState`，不直连表。
2. **价格快照范围**：仅冻结行净价（含税额/币种/汇率取值/税码版本），未实现 PriceCondition 条件引擎（属用户本期选定「仅冻结行净价快照」非目标）。
3. **预算占用**：`budgetRef` 仅落库占位，未接预算系统占用/释放。
4. **法人实体校验降级**：供应商侧 `SupplierStateDTO` 刻意不暴露法人实体 Long id，`VendorSnapshot.legalEntityId` 生产适配器返回 null ⇒ 真实链路跳过法人比对（仍由 vendorState 闸门放行）；M4-2 由测试内内存快照验证机制，生产降级如实标注。
5. **PR→PO 行映射**：本期 `convert` 传空 lines 建 PO，真实 PR 行到 PO 行的字段映射为本切片边界（PR/PO 建单各自独立带 lines），后续 change 补全。
6. **前端菜单可达性真实浏览器探针**：G32 静态一致性 + 组件测试已覆盖；端到端「登录后点 `/procurement#/transactions` 不白屏」需起 shell+app 的真实浏览器探针，本期以静态门禁与编译/路由注册证据替代，列为剩余人工 E2E 项。

## 7. 交付范围（供 Archive 精确挑选）

**纳入本次 change**：`bone-apps/pom.xml`、`bone-apps/bone-procurement/**`（新增）、`bone-init.sql`、`bone-frontend/apps/bone-procurement-app/**`（新增）、`bone-frontend/apps/bone-masterdata-app/**`（供应商页迁出）、`bone-frontend/apps/bone-shell/src/App.tsx`、`bone-frontend/package.json`、`bone-frontend/restart-all-apps.sh`、`scripts/dev-frontend-matrix.sh`、`scripts/dev/up-all-frontend-e2e.sh`、`scripts/check-menu-route-consistency.py`、`scripts/migration/0099_*`、`scripts/migration/0100_*`、`doc/design/assets/readme-runtime-topology.svg`、`openspec/changes/procurement-transaction-pr-po-slice/**`、`docs/superpowers/plans/procurement-transaction-pr-po-slice.md`、本报告。

**排除（工作区无关改动）**：`docs/superpowers/reports/2026-11-16-procurement-supplier-mdm-slice-verify-r2.md`（上一轮历史报告的既存改动，不属于本 change）。
