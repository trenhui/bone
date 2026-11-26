# Tasks

## 1. API 客户端

- [ ] 1.1 新建 `services/sourcingApi.ts` 提供 RFx/投标/报价/授标记录的列表、详情与具名授标调用，按顶层 `success` 判定结果；`sourcingApi.test.ts` 断言请求方法/路径正确且 body 不含前端自报身份字段，测试通过
- [ ] 1.2 新建 `services/supplierRiskApi.ts` 提供证照复核、准入终决、预警处置与绩效读取，并把 403/404/网络失败/`success=false` 映射为明确降级原因；`supplierRiskApi.test.ts` 覆盖四类失败，测试通过

## 2. 寻源工作台页面

- [ ] 2.1 实现 `sourcingView.ts` 纯映射：未知状态保留原始码、不可比报价带原因、AI 建议只作建议字段；`sourcingView.test.ts` 覆盖未知码与不可比场景，测试通过
- [ ] 2.2 实现 `SourcingConsole.tsx` 的 RFx/投标列表、报价比较与授标操作区；`SourcingConsole.test.tsx` 验证冒名/匿名不显示成功、404 显示能力未启用，测试通过

## 3. 准入与风险页面

- [ ] 3.1 实现 `supplierRiskView.ts` 纯映射：未知严重度保留原始码、绩效缺失显示“无法确认”、PII 字段白名单；`supplierRiskView.test.ts` 覆盖未知码、缺失值与 PII 剔除，测试通过
- [ ] 3.2 实现 `SupplierOnboarding.tsx` 的证照 OCR 复核与准入终决入口；`SupplierOnboarding.test.tsx` 验证前端不发送身份字段、过期证照不呈现复核通过，测试通过
- [ ] 3.3 实现 `SupplierRisk.tsx` 的预警处置与绩效只读看板；`SupplierRisk.test.tsx` 验证未知严重度转人工提示、取数失败显示“无法确认”且不显示“无风险”，测试通过

## 4. 导航与集成验收

- [ ] 4.1 实现共享导航组件串接合同 HITL、人工复核、AI 就绪度与治理态势入口；`ProcurementWorkbenchNav.test.tsx` 验证 403/404 显示降级提示，测试通过
- [ ] 4.2 在 `App.tsx` 注册 `/sourcing`、`/supplier-onboarding`、`/supplier-risk` 并保留既有路由；路由渲染测试通过
- [ ] 4.3 运行采购前端 vitest（限定 app）、`tsc --noEmit` 与 eslint JSON errorCount 解析，三项全绿并记录结果
- [ ] 4.4 独立审查身份边界、失败态与 PII 白名单，修复全部 CRITICAL/IMPORTANT 后复测通过
