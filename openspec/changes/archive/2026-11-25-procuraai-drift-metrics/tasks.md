# Tasks

## 1. 领域层：漂移信号值对象与裁决策略

- [x] 1.1 新增 `DriftVerdict` 三态枚举（UNDETERMINED / NO_ACTIONABLE_SIGNAL / REVIEW_REQUIRED），含 `isActionable()` 与"不声明模型健康"语义 <!-- comet-task:26780ad8-5398-4bac-a1a9-bc0bc42601fa -->
- [x] 1.2 新增 `DriftThresholds` 配置 record（缺省未配置、阈值 (0,1]、最小样本量正数校验） <!-- comet-task:a12efd58-ea0c-403e-948a-56da00dd6b6d -->
- [x] 1.3 新增 `DriftSignal` 结果 record（能力码/判词/样本数/推翻数/override 率/阈值/最小样本/原因码/计算时刻，`claimsModelHealthy()` 恒 false） <!-- comet-task:48f4a725-0dd6-44a9-af8a-ff4f4948c66c -->
- [x] 1.4 新增 `DriftSignalPolicy` 纯函数裁决：阈值合法性→样本充足→计数一致→触阈判定，原因码常量齐全 <!-- comet-task:054d7690-6697-40d3-963b-a3ad01d1723e -->
- [x] 1.5 编写 `DriftSignalTest` 领域单测（阈值未配置/样本不足/触阈/未触阈/非法输入共 5 组，RED→GREEN） <!-- comet-task:8b0caf41-82bb-4c88-83ac-e91742786dbb -->

## 2. 应用层：只读方法与投影

- [x] 2.1 新增 `DriftMetricsView` 只读投影 record <!-- comet-task:3a1c754e-077c-4ca2-955f-aee2e7bb2561 -->
- [x] 2.2 `AiGovernanceApplicationService` 新增 `driftSignal(code, thresholds)`：从 tracePort 计样本数、ledger 取推翻数，未登记能力 fail-closed <!-- comet-task:5435d83d-d1d8-45e1-9913-ae4f2ae9fcaa -->
- [x] 2.3 应用服务测试：真实计数一致性、阈值缺省/样本不足 fail-closed、触阈需复核无副作用、未登记能力拒绝 <!-- comet-task:14508752-ccb6-47bb-b93f-cbff4bc3d70f -->

## 3. 适配层：只读 HTTP 端点与契约

- [x] 3.1 `AiGovernanceController` 新增 `GET /capabilities/{code}/drift`，权限 `procurement:ai-governance:read`，返回 `ApiResponse<DriftMetricsView>` <!-- comet-task:27527739-2e55-421a-8df3-efd1c9a64a65 -->
- [x] 3.2 端点使用配置属性驱动阈值（默认未配置 → UNDETERMINED），不新增权限码 <!-- comet-task:bec22584-c189-4d4c-a492-1d52e7b8accd -->
- [x] 3.3 更新 `AiGovernanceControllerContractTest`：路径/方法/权限/ApiResponse 包装锁定 <!-- comet-task:f33bebc2-824d-4ee9-bc74-f49348517648 -->

## 4. 验证与 POC 报告

- [x] 4.1 `mvn spotless:apply` 后跑 ai-governance 模块测试全绿（含 ArchitectureTest） <!-- comet-task:79153d37-3707-451e-bf24-c4ec3f834ab2 -->
- [x] 4.2 跑契约模块与采购聚合测试，确认无回归 <!-- comet-task:f4360b4d-20b4-4097-8624-afe72878651e -->
- [x] 4.3 跑 procuraAI 文档门禁（alignment / evalset / frontmatter / persistence / event-chain），确认不退化 <!-- comet-task:7d47e61b-58c5-4f68-9e3c-b8d257b9aaca -->
- [x] 4.4 内部模拟 POC：构造真实终决序列验证三态判词与 fail-closed 负向探针，产物落 POC 验证报告 <!-- comet-task:1b38d9b5-01e2-4df6-aed8-ada82be5a81e -->
- [x] 4.5 报告诚实标注边界：CI 绿不等于模型效果达标、阈值待 owner、未改 DDL/CI 判据集合 <!-- comet-task:f17d0cb0-0317-4cf9-9b8a-941ab26e794f -->

## Workflow follow-up

- 归档到 `openspec/changes/archive/`，同步主 spec `procurement/ai-drift-metrics`
- 提交并推送 `origin/dev`（沿用用户既有授权）
