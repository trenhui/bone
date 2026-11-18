# Verify 独立复审报告：procurement-fulfillment-collaboration-slice

- 日期：2026-11-18
- 分支：`comet/procurement-fulfillment-collaboration-slice`（worktree，基线 `27e1948df`）
- 复审方式：源码实证 + 变异测试 + 门禁实跑 + 真实浏览器，不依赖二手报告

## 1. 范围与结论

本 change 落地采购 05 履约协同「核心协同闭环」：PO 确认(855) → ASN(856) → 自助开票(810) → IR 例外台账，
跨上下文取 fail-closed 桩，前端在 `bone-procurement-app` 增履约协同页。**复审结论：通过，可归档**。

## 2. 需求 → 实现 → 证据 对照（5 capability）

| Capability | 关键契约 | 实现落点 | 验证 |
|---|---|---|---|
| po-acknowledgement | po_no+po_version 幂等、终态拒绝、跨供应商 403 | `PoAckApplicationService` + `PoStatus.SENT→ACKNOWLEDGED` 唯一迁移 | `PoAckApplicationServiceTest`(5) + `PoStatusTest`(6) |
| asn | asn_no 唯一、未确认 PO 拒绝 31503、行引用校验、包装/SSCC | `AsnApplicationService` + `AsnStatus` 显式迁移 | `AsnApplicationServiceTest`(5) + `FulfillmentStatusMachineTest` |
| invoice-submission | 去重 31502、超余额 31504、TDS、997 不越级、采购域不放行 31505 | `InvoiceSubmissionApplicationService` + `InvoiceStatus` | `InvoiceSubmissionApplicationServiceTest`(7) + 负向探针 |
| ir-exception | 幂等、只 Finance 回写、采购域无放行、差异单列、分页 | `IrExceptionService` + `IrExceptionStatus` + `findPage` | `IrExceptionServiceTest`(6) |
| fulfillment-console | 四视图、按态按钮、31504 保留表单、403 降级、997 不显示放行 | `FulfillmentConsole.tsx` + `fulfillmentView.ts` + `fulfillmentApi.ts` | `FulfillmentConsole.test.tsx`(6) 等共 17 例 |

## 3. 关键防线复核（亲自实证）

1. **997 语法回执不越级（变异测试）**：将 `InvoiceStatus` 的 `finance_ack→MATCH_PENDING` 注入为
   `→MATCHED`，`FulfillmentStatusMachineTest.发票997语法回执不置MATCHED` 精确转红
   （expected MATCH_PENDING but was MATCHED）；还原后 `clean test` 46 例全绿。证明非假绿。
2. **前端 eslint 假绿防御**：用 `-f json` 解析 `errorCount`，实测 0（默认 stylish formatter 在本环境会崩溃且退出码 0）。
3. **迁移 SQL 幂等与号段 bug 修复**：初跑 0102 撞主键 `...303`（0100 已用 3xx），将号段改为 4xx；
   两次重跑 exit=0，权限码 1+1、角色绑定 2、菜单 2（租户各 1）、零重复、零残留临时表/存储过程。
4. **能力开关缺口补齐**：`FulfillmentController` 有 `@ConditionalOnProperty(bone.procurement.fulfillment.enabled)`，
   原 `application.yml` 缺该声明；补 `BONE_PROCUREMENT_FULFILLMENT_ENABLED:false` 并扩展
   `ProcurementCapabilityToggleTest`（2→3 例），保持默认 fail-closed。
5. **真实浏览器 E2E**：CDP 访问 `/#/fulfillment`，页面挂载、无白屏、显示"能力未启用"降级态、零控制台错误。
   边界：本 worktree 无履约业务表、采购后端未启动、能力开关默认关 ⇒ 仅验证降级态（对齐上一轮
   supplier-mdm 边界），真实业务提交链路列为环境前置项，非代码缺陷。

## 4. 门禁证据（全绿）

- 后端 `mvn -o -f bone-procurement/pom.xml clean test`：transaction 52 + fulfillment 46 + app 3 = **101 例**，BUILD SUCCESS。
- 前端 `tsc --noEmit` exit=0；`eslint -f json` errorCount=0；`vitest run` **77 例**（8 文件）全绿。
- 全仓静态门禁：G32 菜单↔路由(0)、G37 图标白名单(0)、G40 权限自洽(0)、G41 拓扑(0)。
- procuraAI 文档族：alignment 10/10(0)、evalset RESULT:PASS(0)、asyncapi 29 事件零漂移(0)。
- Comet Build 门禁：`comet check run ... build` exit=0，receipt 绑定，phase 自动推进 verify。

## 5. 偏差登记（如实）

- **路径偏差**：LLD openapi 端点为 `/api/v1/portal/*`（BFF），本轮内部实现 `/api/v1/procurement/*`；
  design.md 已登记，不谎称契约覆盖。
- **边界偏差**：浏览器 E2E 仅降级态（见 §3.5）；真实业务 E2E 需补齐「履约 DDL 落本地库 + 采购后端启动 +
  能力开关打开 + 测试 PO 数据」四项环境前置，本轮按 worktree 隔离与"不越权启停服务"原则未做。
- **跨上下文**：InventoryAclPort/FinanceAclPort 默认不可用，运行期不触发真实上游；Finance 裁决与 GR 投影属后续切片。

## 6. 结论

30/30 tasks 完成，所有可在本环境验证的门禁与测试全绿，负向探针有效，偏差已如实登记。**建议归档**。
