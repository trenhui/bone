---
change: procuraai-durable-business-aggregates-rebaseline
design-doc: openspec/changes/procuraai-durable-business-aggregates-rebaseline/design.md
base-ref: d1742235a8f81f93dff343ce685a00363f0a7fe0
archived-with: 2026-11-26-procuraai-durable-business-aggregates-rebaseline
---

<!-- comet-task-authority: openspec/changes/procuraai-durable-business-aggregates-rebaseline/tasks.md -->

# 实施计划：procuraAI 持久业务聚合补齐（B 方案，新基线重放）

## 范围与约束

在 origin/dev 新基线上按已批准设计新增 `bone-procurement-business` 模块，以 SDK 持久化落地 Bid / Quotation / Award / CAPA 四类聚合。采用 autonomous + tdd + standard：每个领域/持久化/端点任务先写失败测试，再实现到转绿；最终由独立 reviewer 审查 DDL/迁移、身份边界、AI 建议隔离与门禁同步。L3 授权已确认，但仅允许修改仓库内 DDL/迁移/规范/CI 文件，不执行生产库操作，不接生产 MQ，不改评测与 owner 冻结状态。

## 1. 前置授权与模块骨架

### 1.1 L3 授权证据留痕
<!-- comet-task-ref:d8a80bf7-2e3d-4086-9bfe-617fb963839a -->
把用户在 Build 配置决策中的 L3 授权记录到验证报告，明确覆盖 `bone-init.sql`、增量迁移、数据库规范、CI 三入口与门禁常量/探针，且明确不覆盖生产库。未记录前不进入 DDL/CI 写入。

### 1.2 新模块编译
<!-- comet-task-ref:9251e76d-bb64-4bea-b2dc-47e82ffa44eb -->
创建 Maven 模块及父子 pom 依赖，保持分层依赖方向；运行空模块 `mvn -o -f .../pom.xml test` 通过。

## 2. DDL、迁移与门禁

### 2.1 四张表与规范同步
<!-- comet-task-ref:9a515211-05e8-4d43-aa0a-02791815e02e -->
先在 DDL 对账测试中声明 4 张新表与必备列，确认 RED；再在 `bone-init.sql` 增加表、租户/审计列、业务唯一键和索引，并同步数据库规范清单至转绿。

### 2.2 增量迁移脚本
<!-- comet-task-ref:918327dc-3504-43f3-af31-29f754c5dbdc -->
按当前最大 order 递增编写迁移脚本，登记清单；以 `check-migration-manifest.py` 断言连续、无重复、无缺失。

### 2.3 三入口门禁与注入探针
<!-- comet-task-ref:83c836b2-0ac6-4e96-a183-8ec92b4f71df -->
先写注入变异用例确认缺失任一入口会转红，再同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`、门禁常量和探针；还原后正例通过。这是 L3 的重点检查项。

## 3. 领域聚合

### 3.1 Bid / Quotation / Award
<!-- comet-task-ref:3ed46cb3-6806-4174-9e42-98a8e62c55e2 -->
先用 `BidQuotationAwardTest` 固化到期拒收、重复投标、来源幂等、不可比报价、AI 建议不授标、匿名/AI 授标拒绝和终态不可回写；再实现纯领域状态迁移到转绿。

### 3.2 CAPA
<!-- comet-task-ref:cf98b798-589e-4ad8-9818-a6c69b87522b -->
先用 `CapaTest` 固化缺失责任人/期限拒绝、超期转人工保留原期限、关闭需具名操作人与证据、CAPA 不改变准入/绩效/授信；再实现领域规则到转绿。

## 4. SDK 持久化

### 4.1 实体、仓储与重启读回
<!-- comet-task-ref:4b3399e5-7eda-4d40-adcf-6bd6003b8c2f -->
定义 SDK Entity 与仓储适配器，应用服务只通过领域端口访问；`BusinessPersistenceRestartTest` 以重建仓储上下文模拟重启，断言授标与 CAPA 一致读回。

### 4.2 租户唯一键与隔离
<!-- comet-task-ref:5c6831d6-e61f-40b0-a51f-f21d47be2083 -->
`BusinessTenantIsolationTest` 覆盖同租户业务键重复被拒、跨租户同键并存但不可互读/覆写、缺失租户拒绝写入与查询；与 DDL 唯一键形成双层证据。

## 5. 应用与 HTTP

### 5.1 应用服务与身份边界
<!-- comet-task-ref:c55d734d-c544-4f0a-a9ea-e87e8d0f80bf -->
实现只读查询与具名人工操作应用服务，身份只来自登录态端口；`BusinessApplicationServiceTest` 拒绝匿名、冒名、跨租户与 AI 建议直接终决。

### 5.2 端点契约与字段白名单
<!-- comet-task-ref:975817c6-56ca-4d3b-9d55-adb8dc919de6 -->
新增 `ApiResponse<T>` 端点与字段白名单 DTO；`BusinessEndpointContractTest` 检查权限、请求形状、身份字段不来自请求体、PII 不回传和失败态。

## 6. 集成验收与审查

### 6.1 全量测试与门禁
<!-- comet-task-ref:ca0a1754-7ace-4568-9037-efcfbdfeda66 -->
运行采购后端模块测试、procuraAI 主门禁与注入探针，记录测试数、失败数、门禁结果；任何失败先按 debug-gate 定位，不以旧结果覆盖。

### 6.2 OpenSpec 严格校验
<!-- comet-task-ref:0757dc09-8823-4453-8691-64ee71b89c04 -->
运行 `comet classic openspec -- validate procuraai-durable-business-aggregates-rebaseline --strict`，确认 9 个新增需求与全部场景仍与实现一致。

### 6.3 独立审查
<!-- comet-task-ref:41be5c3a-9da9-4381-b217-fa778463890c -->
由独立 reviewer 审查最终 diff，重点 DDL/迁移可逆性、CI 三入口一致性、身份边界、AI 建议隔离和 PII；CRITICAL/IMPORTANT 必须修复并复测。

## 失败处理与回退

- DDL/迁移或门禁注入失败：停止推进，保留失败日志与变异证据，修复后重跑；不得绕过三入口一致性。
- 重启读回或租户隔离失败：回到持久化层修复，不用内存缓存掩盖问题。
- 身份边界失败：视为 P0，立即停止相关端点实现，先修登录态裁决。
- 生产库始终不在范围；本地迁移失败只用脚本的逆向/重建方式处理，不触碰生产环境。
