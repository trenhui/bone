## 1. 规格与现状确认

- [x] 1.1 核对 `InspectionSamplingPolicy.shouldSample` 现有 `ratePercent>=100` 分支与 `InspectionSamplingJob` 终态来源，记录到 change 说明；验收：逐行确认两处代码路径并在任务备注引用文件+行号 <!-- comet-task:71857625-e20c-4b73-a40a-02e02e3c2038 -->
- [x] 1.2 确认 `t_feature_toggle.INSPECTION_SAMPLING` 为唯一配置源且无第二处口径；验收：grep 全仓确认只有该键被抽检逻辑消费 <!-- comet-task:07a546a0-d523-494e-adf4-5032759a5626 -->

## 2. 抽样策略与终态覆盖实现

- [x] 2.1 为 `InspectionSamplingPolicy` 补 100% 全量命中的正向单测；验收：比例=100 时 N 笔案件生成 N 条抽检记录，测试通过 <!-- comet-task:e7b70782-2061-4f9b-947d-3e8372516cf0 -->
- [x] 2.2 扩展自动抽样终态白名单，纳入拒赔终态（默认仍未配置时不生效）；验收：100% 配置下拒赔终态案件自动生成抽检记录，测试通过 <!-- comet-task:94a4e3f7-175a-4769-81e7-ba7242056d17 -->
- [x] 2.3 保持默认回落 5% 与现网行为一致；验收：未配置租户的抽检行为与 change 前完全一致，回归测试通过 <!-- comet-task:7b2f1345-f7f1-48c9-9dd6-5ede328ce66b -->
- [x] 2.4 配置读取失败 fail-safe 回落 5% 并记诊断日志；验收：注入配置读取异常，确认回落且日志可观测、主理赔流程不中断，测试通过 <!-- comet-task:6e5bf894-df0a-4298-a37b-28672e19dfc2 -->
- [x] 2.5 租户隔离：租户 A=100% 不影响租户 B 默认 5%；验收：跨租户场景测试通过 <!-- comet-task:e7611ee2-be45-4cb4-ad69-033b8d5d1cc7 -->

## 3. 资金与语义边界守护

- [x] 3.1 确认抽检结果不进入 `Adjustment` 资金路径、不改 `AUTO_APPROVE`/视觉桩 `available`/目录准入 `MANDATORY`；验收：grep + 测试证明抽检仅写质检/复核记录，无资金闸字段写入 <!-- comet-task:c805471f-16ad-4d38-ac39-feb34ac4cf03 -->
- [x] 3.2 确认赔付金额计算语义未变；验收：现有资金相关测试全绿，无金额计算代码改动 <!-- comet-task:23848349-b853-4e5b-92e4-ea7fedba074a -->

## 4. 门禁与可证伪验证

- [x] 4.1 为每条新判据补变异双向钉子（比例篡改、终态范围回退、fail-safe 移除）；验收：改一处必红、还原必绿，变异记录归档 <!-- comet-task:df274979-ebde-47a7-9f8d-48e312e1b490 -->
- [x] 4.2 按仓库纪律把新判据同步到相关门禁入口；验收：check 入口可见新判据，注入必红还原必绿 <!-- comet-task:5a922fbe-0800-47a9-b327-2ad8b5685404 -->
- [x] 4.3 跑 synap-claim 模块相关测试全绿；验收：`mvn -pl <synap-claim模块> -am test` 通过，测试数从收据取真实值 <!-- comet-task:99fbd482-08c6-4a5c-8cd1-7e5ce1ef2616 -->
