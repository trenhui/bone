# Proposal

## Why

`procurement/business-module-skeleton` 已规定“授标/准入必须具名人工终决”，但 HTTP 层存在自报身份：`POST /qualifications/onboardings/{no}/approve` 从请求体接收 `tenantId/operator`，`awardByHuman` 只有应用服务方法、没有真实 HTTP 入口。这样即使领域不变量存在，也无法证明人工终决由登录态操作人作出，审计链可被请求体伪造。需按生产就绪基线把两处终决绑定到登录态，并补齐与 OpenAPI 一致的授标端点。

## What Changes

- 修改 `QualificationController` 的 onboarding approve：租户与操作人统一从 `ReviewIdentityPort`/登录态解析；请求体自报值与登录态不一致即拒绝；匿名、缺租户、空白操作人一律 fail-closed。
- 在 `SourcingController` 新增与 OpenAPI 对齐的 `POST /api/v1/procurement/rfx/{rfxNo}/award`，权限使用既有 `procurement:rfx:approve`，不接受请求体 `confirmed_by`，由登录态提供具名操作人。
- 调整 `SourcingApplicationService.awardByHuman`：支持以可信的 `rfxNo` 定位项目并保留既有状态机/终态/空白操作人不变量；AI 结论仍不得直接置 AWARDED。
- 补正向与负向行为测试：具名登录态可终决；跨租户、冒名、匿名、空白操作人、非 RFx 对应项目均被拒绝且不落库。
- 前端仅在已有采购工作台需要时补最小联动，不改变人工终决“由人确认”的交互语义。

非目标：不改 DDL/CI 三入口；不接真实模型；不改评测集状态；不执行生产数据库操作；不扩展 Bid/Award/Quotation/CAPA 完整聚合。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `procurement/business-module-skeleton`: 把“具名人工终决”从领域方法约束提升为 HTTP 边界可验证要求；onboarding approve 与 RFx award 必须绑定登录态租户/操作人，禁止请求体自报。

## Impact

- 代码：`bone-procurement-qualification`、`bone-procurement-sourcing`、可能的 `bone-procurement-app` 安全适配器与对应测试。
- API：新增 `POST /api/v1/procurement/rfx/{rfxNo}/award`；onboarding approve 的 `tenantId/operator` 不再作为权威输入。
- 权限：复用已有 `procurement:rfx:approve`，不新增权限码或迁移脚本。
- 数据：不新增表、不改 DDL；写操作仍以持久化读回作为第二证据。
- 风险：若登录态缺少租户或操作人，相关人工终决端点必须返回明确拒绝，不得回退到请求体或默认租户。
