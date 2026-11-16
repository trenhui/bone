# Tasks

## 1. 前端类型与 API 客户端

- [x] 1.1 新增 `types/supplier.ts`：定义 SupplierStateDTO / SupplierRegistrationResultDTO / SupplierTransitionResultDTO / SupplierPageQuery，全部只含状态、版本、裁决、摘要字段，不含身份主键与 PII；验证：TypeScript 编译通过，类型中无 uscc/taxId/phone/legalName 字段。 <!-- comet-task:debb3249-e972-4396-961f-acd981ab08bf -->
- [x] 1.2 新增 `services/supplierApi.ts`：封装 `supplierApi.register / transitionSupplier / transitionVendor / current / page` 五个方法，路径 `/api/v1/masterdata/suppliers`；列表用 `normalizePage` 同款 records/total 归一（`normalizeTotal` 处理 Long→String）；验证：契约单测断言 5 个路径与 body 形状、分页消费 records 而非 list。 <!-- comet-task:fd8528d8-dfc5-49ae-bd7d-dba813ec46a3 -->

## 2. 供应商治理页面

- [x] 2.1 新增 `pages/SupplierManagement.tsx`：登记表单 + 双角色分区（Supplier/Vendor 独立状态、版本、迁移摘要时间线）+ 允许事件按钮；顶层 `success=false` 按 `message` 呈现失败，不因 2xx 误判成功；验证：渲染测试覆盖双状态独立、未知码原样、失败不推进。 <!-- comet-task:5519150c-74a3-421c-8351-e962e7641775 -->
- [x] 2.2 接入分页列表：空态、`total` 字符串归一计算页数、点击行加载详情；验证：渲染测试覆盖 `records=[] / total="0"` 与 `total="23"` 两类，无类型转换错误。 <!-- comet-task:94e47a90-dc25-4876-b639-81119e604145 -->
- [x] 2.3 能力开关与权限降级：404/未注册 → "供应商能力未启用"空态且不渲染迁移按钮；403 → 无权限提示且不发起写调用；验证：渲染测试分别注入 404/403 响应断言提示文案与无后续写请求。 <!-- comet-task:31b25696-e609-4bf0-a2d0-45a121fea84d -->
- [x] 2.4 PII 与未知码边界：页面只渲染白名单字段，未知状态码显示原始码，摘要格式异常显示原始值；验证：注入含 phone/uscc 的意外响应，断言 DOM 文本与属性不含这些值。 <!-- comet-task:28abaae4-a763-4e17-9821-30fc0fce6152 -->
- [x] 2.5 接入路由与菜单：`App.tsx` 增加 `/suppliers` 路由，`Layout.tsx` 在"采购治理"分组增加入口；验证：`tsc --noEmit` 通过，菜单链接指向 `/suppliers`。 <!-- comet-task:05a0adbb-c004-4df7-b4ee-c8d62cabe1ed -->

## 3. 契约测试与前端门禁

- [x] 3.1 新增 `services/supplierApi.test.ts`（vitest）：断言登记/两类迁移/详情/分页的请求方法、路径、body（事件码 + 来源声明）与响应归一化；验证：`npx vitest run supplierApi.test.ts` 全绿。 <!-- comet-task:c428e669-91f1-467a-a4f2-ce40dada42dc -->
- [x] 3.2 新增 `pages/SupplierManagement.test.tsx`（Testing Library）：覆盖 §2.1–2.4 的呈现与降级；验证：vitest 全绿。 <!-- comet-task:ad528e5d-5d12-4639-b1a4-164a35971602 -->
- [x] 3.3 前端门禁：在 worktree 跑 `tsc --noEmit` + `eslint -f json` 解析 errorCount + vitest；验证：三项通过且 eslint errorCount=0（stylish formatter 崩溃为已知假绿，必须用 json）。 <!-- comet-task:4f48bbec-99ea-40f7-b4d5-a2a30f7a3306 -->

## 4. 真实前后端联调与验证

- [x] 4.1 后端真实 HTTP 复验：跑 `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test -Dtest=SupplierHttpIntegrationTest`；验证：全绿，记录登记→闸门→准入→退出全链路的 HTTP 断言通过。 <!-- comet-task:f253a7ec-0778-44e7-840b-2b71d27648f6 -->
- [x] 4.2 契约对齐复核：逐一比对前端 supplierApi 请求与 `SupplierController` 五个端点的路径/方法/字段；验证：差异清单为空，写入 Verify 报告。 <!-- comet-task:95a97cc7-ac74-4663-92a3-5e877eb7f837 -->
## Workflow follow-up

- 生成 Verify 报告 `docs/superpowers/reports/2026-11-16-procurement-supplier-frontend-integration-verify.md`，记录门禁数字、联动证据与未做项（属 Verify 阶段产物，不在 Build 勾选）。
- Comet Verify（guard verify → 最终集成审查 → 报告）与 Archive（归档 + 交付）按 Comet 流程在 Build 完成后执行。
- 未做项（A-2/A-8 生产 adapter 与 DDL、A-1/A-3/A-6/A-7/A-9/A-10 已裁决偏差）继续列为 owner 前置，不在本 change 宣称完成内。
