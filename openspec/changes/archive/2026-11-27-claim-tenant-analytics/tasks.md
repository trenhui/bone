## 1. 现状与口径确认

- [x] 1.1 核对 `ClaimAnalyticsApplicationService` 现有指标（终态分布/FALLBACK/影子自动化率/P50-P90）与 scopeNote 生成逻辑；验收：逐项确认并在任务备注引用文件+行号 <!-- comet-task:01f54f2e-8331-4130-8028-9f6cb9274e88 -->
- [x] 1.2 确认案件实体含 `tenantId` 且现有查询未分组；验收：代码确认+grep <!-- comet-task:dbe4f879-eb20-4102-be73-489ade5c38bb -->

## 2. 租户分组实现

- [x] 2.1 在聚合结果层按 tenantId 分组；验收：分组场景测试通过，各组指标与全量口径一致 <!-- comet-task:97ba6261-bccc-40d1-97dc-5f950b80d274 -->
- [x] 2.2 查询层强制按请求方权限过滤 tenantId；验收：租户 A 请求不返回 B 数据，越权测试通过 <!-- comet-task:bec6fa02-e0a6-453f-a94a-79062433df89 -->
- [x] 2.3 无租户维度时回退全量口径；验收：与现有 `/analytics/summary` 结果一致，回归测试通过 <!-- comet-task:ac9876b9-e9e4-41df-9993-3f9a7fbef891 -->
- [x] 2.4 影子自动化率随分组结果携带 scopeNote；验收：响应必含 scopeNote 且表述为影子口径，测试通过 <!-- comet-task:cc58e374-3c69-44bb-9b87-bfefe3cf518d -->

## 3. 前端分组视图

- [x] 3.1 `/analytics` 增加租户维度切换与对比；验收：前端页面可切换租户查看分组报表，手动验证通过 <!-- comet-task:cc2d3ff8-0ca4-4473-9d60-86f5bae876dc -->

## 4. 边界与门禁

- [x] 4.1 确认报表只读、不改指标口径/资金/配置；验收：grep + 测试证明无写操作 <!-- comet-task:67df3ee1-9e58-48fb-8828-c6c97b2380c1 -->
- [x] 4.2 补变异双向钉子（分组口径、隔离过滤、scopeNote）；验收：改一处必红还原必绿 <!-- comet-task:6242c046-44f0-4b79-b806-e22f66dc11b3 -->
- [x] 4.3 跑 synap-claim 相关测试全绿；验收：mvn 测试通过，测试数从收据取真实值 <!-- comet-task:42f28dc9-91fb-4c59-bfb7-8095ced2af89 -->
