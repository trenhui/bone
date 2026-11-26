# Tasks

## 1. 前置授权与模块骨架

- [x] 1.1 记录 DDL、迁移脚本与 CI 三入口修改的 L3 授权证据；未取得授权时停止且不进入后续实现，授权文件/用户决定可追溯 <!-- comet-task:d8a80bf7-2e3d-4086-9bfe-617fb963839a -->
- [x] 1.2 新建 `bone-procurement-business` Maven 模块及父子 pom 依赖；`mvn -o -f .../pom.xml test` 空模块编译通过 <!-- comet-task:9251e76d-bb64-4bea-b2dc-47e82ffa44eb -->

## 2. DDL、迁移与门禁

- [x] 2.1 在 `bone-init.sql` 新增 4 张业务表（租户/审计列、业务唯一键与索引）并同步数据库规范清单；DDL 对账与必备列检查通过 <!-- comet-task:9a515211-05e8-4d43-aa0a-02791815e02e -->
- [x] 2.2 新增增量迁移脚本并登记迁移清单；`check-migration-manifest.py` 通过且 order 连续 <!-- comet-task:918327dc-3504-43f3-af31-29f754c5dbdc -->
- [x] 2.3 按 A10.17 同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`、门禁常量与注入探针；注入必红、还原必绿均通过 <!-- comet-task:83c836b2-0ac6-4e96-a183-8ec92b4f71df -->

## 3. 领域聚合

- [x] 3.1 实现 Bid/Quotation/Award 聚合的显式迁移、幂等、不可比标记、具名人工授标与终态不可回写；`BidQuotationAwardTest` 覆盖全部负向场景，测试通过 <!-- comet-task:3ed46cb3-6806-4174-9e42-98a8e62c55e2 -->
- [x] 3.2 实现 CAPA 立项、责任人/期限校验、超期转人工与具名关闭；`CapaTest` 覆盖缺失字段、超期、关闭与自动状态隔离，测试通过 <!-- comet-task:cf98b798-589e-4ad8-9818-a6c69b87522b -->

## 4. SDK 持久化

- [x] 4.1 为四类聚合定义 SDK Entity 与仓储适配器；`BusinessPersistenceRestartTest` 验证授标与 CAPA 重启读回一致，测试通过 <!-- comet-task:4b3399e5-7eda-4d40-adcf-6bd6003b8c2f -->
- [x] 4.2 验证租户唯一键与跨租户隔离；`BusinessTenantIsolationTest` 验证同租户重复写入被拒、跨租户不可覆写/不可读，测试通过 <!-- comet-task:5c6831d6-e61f-40b0-a51f-f21d47be2083 -->

## 5. 应用与 HTTP

- [x] 5.1 实现只读查询与具名人工操作应用服务，复用登录态身份端口；`BusinessApplicationServiceTest` 验证匿名/冒名/AI 建议被拒，测试通过 <!-- comet-task:c55d734d-c544-4f0a-a9ea-e87e8d0f80bf -->
- [x] 5.2 新增统一 `ApiResponse` HTTP 端点与字段白名单 DTO；`BusinessEndpointContractTest` 验证权限、请求形状、PII 不回传与失败态，测试通过 <!-- comet-task:975817c6-56ca-4d3b-9d55-adb8dc919de6 -->

## 6. 集成验收

- [x] 6.1 运行采购后端模块测试与 procuraAI 主门禁，记录测试数、失败数、门禁与注入探针结果，全部通过 <!-- comet-task:ca0a1754-7ace-4568-9037-efcfbdfeda66 -->
- [x] 6.2 运行 `comet classic openspec -- validate <change> --strict`，确认 9 个新增需求与全部场景通过严格校验 <!-- comet-task:0757dc09-8823-4453-8691-64ee71b89c04 -->
- [x] 6.3 独立审查 DDL/迁移、身份边界、AI 建议隔离与门禁同步，修复全部 CRITICAL/IMPORTANT 后复测通过 <!-- comet-task:41be5c3a-9da9-4381-b217-fa778463890c -->
