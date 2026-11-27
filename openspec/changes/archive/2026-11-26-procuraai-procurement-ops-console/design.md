---
comet_change: procuraai-procurement-ops-console
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-26-procuraai-procurement-ops-console
status: final
---

# Design：procuraAI 采购运营工作台补齐

## Context

见 `proposal.md`。采购前端已有交易、履约、供应商主数据、合同审查/HITL、AI 就绪度、建议、人工复核与治理态势路由（`App.tsx`），但寻源、准入、风险绩效没有可操作页面。现有模式是 `pages/*.tsx` + 纯映射 `*.view.ts` + `services/*Api.ts`，以 `ApiResponse.success` 而非 HTTP 2xx 判定结果，未知码显示原始码，失败显示“无法确认”。本设计沿用这些模式，只消费后端端点，不改 Java 代码。

## Goals / Non-Goals

**Goals:**

- 让寻源—报价比较—人工授标与准入—风险处置—绩效查看在浏览器里可操作、可追溯。
- 在前端维持 fail-closed：冒名/匿名/未知/取数失败均不默认显示成功或全部正常。
- 为后续人工终决耗时与申诉回流提供可观察入口，但不伪造指标。

**Non-Goals:**

- 不新增后端端点、DDL、权限种子或 CI 判据集合。
- 不把 `rules_only` 建议、AI 推荐排序或模型版本串包装成“AI 已达标”。
- 不改合同 HITL、人工复核、AI 就绪度与治理态势的既有规格。
- 不执行生产库操作或接生产 MQ。

## Decisions

1. **新增三条路由而非改造现有供应商页**：`/sourcing`、`/supplier-onboarding`、`/supplier-risk`。
   - 理由：寻源/准入/风险是不同决策路径，混进 `SupplierManagement` 会模糊权限与状态语义。
   - 备选：把所有内容塞进现有供应商页。放弃，会导致列重叠与权限点混乱。

2. **纯映射层与页面组件分离**：每个页面配套一个 `*.view.ts` 把后端响应映射为展示模型，未知码保留原始码，失败统一为“无法确认”；组件只渲染展示模型。
   - 理由：与 `governancePostureView` / `readinessView` 既有模式一致，便于单测覆盖边界。
   - 备选：在组件里直接判断状态码。放弃，会产生不可测的分散分支。

3. **API 客户端按后端真源判定结果**：`services/sourcingApi.ts` 与 `services/supplierRiskApi.ts` 读取顶层 `success`，403/404/网络失败分别映射为权限不足、能力未启用、无法确认；不因 HTTP 2xx 自动成功。
   - 理由：采购域已有“2xx ≠ 成功”的红线。
   - 备选：依赖 axios 默认 throw。放弃，无法覆盖 `success=false` 的 2xx 响应。

4. **前端不提供身份输入**：授标、OCR 复核、预警处置请求不发送前端自报的 `tenantId` / `operator` / `confirmedBy`；只发送业务标识与理由/结论，身份一律由后端登录态裁决。
   - 理由：身份冒名已在后端负向测试中封禁，前端再传值只会制造歧义。
   - 备选：从本地存储填身份。放弃，属于高风险伪造来源。

5. **导航复用而非状态复制**：新增共享导航组件链接现有 `/contract-hitl`、`/human-review`、`/ai-readiness`、`/governance`；不缓存这些页面的判词，不在工作台重复渲染全局结论。
   - 理由：避免“态势聚合”与既有治理页面口径分叉。

## Risks / Trade-offs

- **依赖后端已交付端点**：寻源/授标页面消费 `procuraai-durable-business-aggregates-rebaseline` 已落地的 `/api/v1/procurement/business/*`（GET award、POST bids/quotations、GET quotations）；A 方案 `procuraai-business-aggregates-completion` 保留为草稿，不作依赖。→ 取数失败/能力未启用一律走 fail-closed 降级，不默认显示成功。
- **内存仓储不跨重启**：页面展示的数据可能随后端重启清空。→ 页面只呈现“当前服务实例内的事实”，不写“持久/生产可用”文案。
- **PII 泄露**：证照响应可能意外携带证件号。→ 展示模型字段白名单，测试断言 DOM 可见文本与属性不含证件号/电话。
- **权限码不匹配**：复用既有权限码可能与实际部署不一致。→ 403 显示无权限提示，不隐藏整个应用也不自行放行。
- **全仓 vitest 假红基线**：不限定 app 的测试会扫到无关模块失败。→ 以采购 app 范围 vitest + tsc + eslint(JSON errorCount) 为权威判据。

## Test Strategy

- **API 客户端单测**：`sourcingApi.test.ts` / `supplierRiskApi.test.ts` mock `createApiClient`，断言路径与请求方法正确、body 不含 `tenantId`/`operator`/`confirmedBy`；覆盖 `success=false`（2xx）、403、404、网络失败四类降级映射。
- **纯映射层单测**：`sourcingView.test.ts` / `supplierRiskView.test.ts` 覆盖未知码保留原始码、不可比报价带原因、AI 建议只作建议字段、PII 白名单剔除、绩效缺失显示“无法确认”。
- **页面组件单测**：三页 + 导航组件用 Testing Library 挂载，断言冒名/匿名不显示成功、404 显示能力未启用、403 显示无权限、未知严重度转人工提示、取数失败不显示“无风险”。
- **权威门禁**：限定 app 的 `vitest run --root . apps/bone-procurement-app`、`tsc --noEmit`、`eslint -f json` 解析 `errorCount`；不把全仓测试或 HTTP 2xx 当成功，不让 eslint 默认 stylish formatter 的 `ansiRegex` 崩溃制造假绿。

## Migration Plan

无数据库迁移。按 API 客户端 → 展示映射 → 页面组件 → 路由与导航 → 测试的顺序实现；回滚时撤销本 change 的前端文件与路由注册即可。不触碰后端契约与数据。

## Open Questions

（无。依赖端点已由 `procuraai-durable-business-aggregates-rebaseline` 交付并在 `BusinessController` 落地；前端按真实响应形状实现，若与之不符，按实现遗漏在当前范围内修正，不扩需求。）
