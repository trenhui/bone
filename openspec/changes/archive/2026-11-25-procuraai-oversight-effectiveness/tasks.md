# Tasks

## 1. 领域层：监督有效性三态判词

- [x] 1.1 新增 `OversightVerdict` 三态枚举（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED）与 `isActionable()`；用枚举单元测试验证三态与可操作性判定 <!-- comet-task:2e4c60fb-5a3c-4cf4-8b43-6f030fc1dc4e -->
- [x] 1.2 新增 `OversightThresholds` record（reviewDurationFloorMs/minSampleSize/fastReviewRatioThreshold），缺省未配置、非法阈值 fail-closed 校验；用单元测试验证负数/非正数被拒与 configured() 判定 <!-- comet-task:96d90b1d-7b2c-4bf0-b20d-e2aaf0f7d798 -->
- [x] 1.3 新增 `OversightSignal` 结果 record（能力码/判词/样本数/三类风险计数/原因码/computedAt），`claimsModelHealthy()` 恒 false；用单元测试验证护栏 <!-- comet-task:2f2166da-abfe-4da1-a673-4e68567faf91 -->
- [x] 1.4 新增 `OversightSignalPolicy` 纯函数裁决：阈值未配置/样本不足→UNDETERMINED，无理由推翻/未回流评测/耗时过短触阈→REVIEW_REQUIRED，否则 NO_ACTIONABLE_SIGNAL；用 TDD 覆盖全部六类场景与边界（等于阈值、100%、零样本、非法计数），RED→GREEN <!-- comet-task:89adf35e-c053-4dde-9e00-80ac81c64e02 -->

## 2. 应用层：真实计数与只读服务

- [x] 2.1 扩展 `HumanDecisionLedger` 接口新增 `findByCapability(CapabilityCode)` 只读查询，SDK adapter 复用 query，退役内存实现同步签名；用编译通过验证接口一致 <!-- comet-task:97c9c4aa-5f9c-4864-aa52-8b2836310c32 -->
- [x] 2.2 在 `AiGovernanceApplicationService` 新增 `oversightSignal(code, thresholds)`：校验能力已登记，按能力码取终决记录统计样本数与三类风险计数，委托纯函数裁决；用应用测试验证真实计数一致、未登记能力拒绝、无副作用 <!-- comet-task:2a3136dc-0ef3-4a5d-8f4a-11b5a98198cc -->
- [x] 2.3 新增 `OversightMetricsView` 只读投影（含 modelHealthClaim=NONE、requiresOwnerReview、原因码与计数）；用单元测试验证 from(signal) 映射不回传 PII 与原文 <!-- comet-task:5ce4acba-2028-4964-bf70-1800142be5dd -->

## 3. 适配层：配置与只读端点

- [x] 3.1 在 `AiGovernanceProperties` 新增 oversight 阈值字段（reviewDurationFloorMs 默认 null、minSampleSize 默认 20、fastReviewRatio 默认 null）及 getter/setter；用配置测试验证缺省 fail-closed <!-- comet-task:415e4ad1-d12f-4f63-8084-6f672971bf9f -->
- [x] 3.2 在 `AiGovernanceController` 新增 `GET /capabilities/{code}/oversight`，注入 properties 构造服务端阈值，方法只接受路径参数 code，复用 read 权限码；用契约测试锁定端点数量（十端点）、权限码与方法签名不含请求参数 <!-- comet-task:7164c6a2-5293-4d5e-b9a4-0fb3584984a2 -->
- [x] 3.3 更新 `AiGovernanceToggleTest`：验证 oversight 阈值缺省未配置；用测试通过验证 <!-- comet-task:e106a57b-17e7-454a-bdc5-e879ecb1db65 -->

## 4. 验证与内部模拟 POC

- [x] 4.1 运行 ai-governance 模块全部单测（含新增领域/应用/投影/契约/配置测试），零失败；用 maven surefire 报告验证 <!-- comet-task:e56a7657-9653-4187-ba65-2ba0f37e4b10 -->
- [x] 4.2 运行采购聚合模块构建与测试，确认无跨模块回归；用 maven 退出码 0 验证 <!-- comet-task:ef93af99-b05f-4b56-bee9-29e197446955 -->
- [x] 4.3 执行内部模拟 POC：以人工构造的真实终决记录（有效复核/橡皮图章/无理由推翻/未回流评测/样本不足）驱动端点投影，验证三态判词与 fail-closed 与规格一致；用 POC 报告记录每步输入输出与断言 <!-- comet-task:8b8e19e4-dc78-4ef3-8160-c4d59e279ebc -->
- [x] 4.4 执行独立审查（standard）：检查 DDD 分层、fail-closed、PII 禁入、无副作用、阈值不可注入；CRITICAL/IMPORTANT 必须修复后复验，报告审查结论 <!-- comet-task:317bbefc-480b-4463-8b93-adf98f42ba11 -->
- [x] 4.5 生成 Verify 报告（docs/superpowers/reports/），汇总交付物 file:line、POC 场景、负向探针、门禁结果、独立审查与诚实边界；用报告文件存在且结论 PASS 验证 <!-- comet-task:84554a99-3c74-465b-924f-c8390d88fb7b -->
