# Tasks：procuraAI 治理态势聚合看板

## 1. 领域层：汇总判词与聚合策略（TDD）

- [x] 1.1 新增 `PostureVerdict` 枚举（UNDETERMINED / ALL_CLEAR / ATTENTION_REQUIRED，含 isActionable 语义），先写 `PostureVerdictTest` 覆盖三态；验证：`mvn -o -f bone-procurement-ai-governance/pom.xml -Dtest=PostureVerdictTest test` 通过。 <!-- comet-task:06ce3fbc-895b-4db1-a063-b49b2d6508d4 -->
- [x] 1.2 新增 `GovernancePosturePolicy` 纯函数聚合策略（分项不可判定优先、其次 actionable、否则全清；不接受客户端阈值），先写 `GovernancePosturePolicyTest` 覆盖不可判定优先/需关注/全清/空清单边界；验证：策略测试通过且无架构依赖违规。 <!-- comet-task:fb3342c9-874d-4baa-ba3f-3b19da9c4a37 -->

## 2. 应用层：聚合视图与服务方法（TDD）

- [x] 2.1 新增 `GovernancePostureView` 与能力明细记录（code/displayName/state/裁决/漂移/监督判词与计数，不含 reason 原文/PII/model output），先写视图映射测试；验证：视图不暴露敏感字段。 <!-- comet-task:66f34f85-506a-423a-a3b3-be1eeb4d0e06 -->
- [x] 2.2 `AiGovernanceApplicationService.posture()` 复用 listCapabilities/decide/driftSignal/oversightSignal 聚合，阈值只取 `AiGovernanceProperties`；先写 `GovernancePostureApplicationTest`（含缺省阈值 → UNDETERMINED、actionable → ATTENTION_REQUIRED、全清）；验证：模块测试通过。 <!-- comet-task:156f6d62-2442-4cd3-90ee-7ed1f87d85bd -->

## 3. 适配层：只读聚合端点（TDD）

- [x] 3.1 `AiGovernanceController` 新增 `GET /posture`，`procurement:ai-governance:read` 权限，`@ConditionalOnAiGovernanceEnabled` 开关控制，无客户端阈值参数；先写 `AiGovernanceControllerContractTest` 断言权限/开关/不回传敏感字段；验证：契约测试通过。 <!-- comet-task:64284baa-2d48-40ab-a423-defe5fc45db9 -->

## 4. 前端：治理态势看板（TDD）

- [x] 4.1 新增 `governanceApi.ts` 与 `governancePostureView.ts`（未知码显示原始码、取数失败显示「无法确认」），先写视图映射单测；验证：`vitest run governancePostureView` 通过。 <!-- comet-task:0e6bee59-1e3a-4c8c-af9b-82611c2d8a74 -->
- [x] 4.2 `GovernanceStatus.tsx` 改造为聚合看板（汇总判词卡 + 能力明细表 + 全域总闸 + 就绪度 + 披露入口），先写 `GovernanceStatus.test.tsx`（未知码/失败态/披露入口/无写操作）；验证：页面测试通过。 <!-- comet-task:73fe7765-c1c1-458f-a52d-5bd9f692d695 -->
- [x] 4.3 前端全量静态检查：vitest 全绿 + tsc 无错 + eslint `-f json` 的 errorCount=0；验证：三项命令均通过。 <!-- comet-task:ffdc6de7-a145-4bbb-ac72-a29065bdc587 -->

## 5. 集成验证与 POC 证据

- [x] 5.1 ai-governance 模块 `clean test` 全绿（含本轮新增用例与既有回归）；验证：模块测试计数与全绿。 <!-- comet-task:26709502-826c-40dc-8d09-11585f9599bc -->
- [x] 5.2 采购聚合测试（ProcuraCapabilityCoverageTest 等相关模块）与 procuraAI 五套门禁（evalset/alignment/frontmatter/persistence/event-chain）复验通过；验证：门禁命令全 PASS。 <!-- comet-task:bcc741bb-ca63-4f12-a9be-13f25af2cead -->
- [x] 5.3 内部 POC 走查：以真实能力清单数据渲染态势看板，验证「不可判定优先 / 需关注 / 全清 / 取数失败 / 未知码」五个场景均符合 spec；验证：POC 报告记录五场景实际结果。 <!-- comet-task:3e4f6d65-41f0-4e4f-b97c-6d276e63e45c -->

## Workflow follow-up

- 完成 Verify 后归档 change、提交并推送到 origin/dev（按 Comet Classic 归档与交付流程执行，用户已选 push 方式时在归档阶段确认）。
