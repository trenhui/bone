# Proposal：procuraAI 持久业务聚合补齐（Bid / Quotation / Award / CAPA）

## Why

上一轮 A 方案只做进程内聚合，能验证不变量却无法在重启后保留授标与 CAPA 事实；S2P 实务与审计要求寻源—投标—报价—授标、供应商问题—CAPA 闭环可追溯且可重放。用户已明确选择 B：以独立模块 + SDK 持久化 + DDL/迁移落地耐久性。这是正式扩范围，`procuraai-business-aggregates-completion`（A 方案）保留为设计草稿，不再推进。原 `procuraai-durable-business-aggregates` 因并行推送导致交付基线与 `origin/dev` 分叉，本 change 在 `origin/dev` 新基线上复用同一范围与方案重走完整流程，不扩大需求、不降低验收。

## What Changes

- 新增 Maven 模块 `bone-procurement-business`（或同等边界的独立模块），按 DDD 分层承载 Bid / Quotation / Award / CAPA 四类聚合、应用服务、SDK 仓储适配器与 HTTP 端点。
- 新增 4 张 `t_proc_*` 表（建议 `t_proc_bid`、`t_proc_quotation`、`t_proc_award`、`t_proc_capa`），含必备租户/审计列、业务唯一键与索引；同步 `数据库开发规范 §2.17` 的采购表计数（当前 24 → 28，实施前以脚本实测为准）。
- 新增增量迁移脚本并登记迁移清单；不改 `bone-init.sql` 之外的生产数据，不执行生产库操作。
- 按 A10.17 / D-11 四入口一致流程新增持久化门禁判据与注入式负向探针；这属于 L3，必须在 Build 前取得明确架构授权。
- 保留 fail-closed 红线：具名人工授标/关闭、租户隔离、幂等、AI 只作建议、终态不可回写、PII 禁入。
- 补齐领域、应用、HTTP 契约、DDL 对账与重启读回测试。

## Capabilities

### New Capabilities

- （无。行为归属既有能力，不创建近似重复的 capability。）

### Modified Capabilities

- `procurement/business-module-skeleton`：增加投标、报价、授标记录与 CAPA 四类聚合的行为要求与验收场景。
- `procurement/production-persistence`：增加四类聚合的租户唯一键、重启读回、终态不可回写与 DDL/迁移可验证要求。

## Impact

- **后端**：新增 Maven 模块及父子 pom 依赖；新增 SDK Entity/Repository 适配器；HTTP 端点沿用统一 `ApiResponse` 与既有权限码思路，权限种子如需新增另行列入 L3。
- **数据**：新增 4 表 DDL + 增量迁移；`bone-init.sql`、数据库规范、迁移清单、DDL 对账测试同步变更。
- **门禁**：新增持久化/DDL 判据必须同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`、门禁常量与注入探针，否则 A10.17 转红。
- **非目标**：不执行生产数据库操作；不接生产 MQ；不做 owner 契约冻结、评测转正、真实模型接入或效果达标声明；不把 `rules_only` 建议包装成 AI 已达标。
- **L3 前置**：DDL、迁移脚本与 CI 三入口修改需要在 Build 前获得明确架构授权；未授权时本 change 停在 Design，不进入实施。
