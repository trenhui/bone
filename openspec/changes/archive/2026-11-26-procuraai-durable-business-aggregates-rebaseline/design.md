---
comet_change: procuraai-durable-business-aggregates-rebaseline
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-26-procuraai-durable-business-aggregates-rebaseline
status: final
---

# Design：procuraAI 持久业务聚合补齐（B 方案）

## Context

见 `proposal.md`。A 方案（`procuraai-business-aggregates-completion`）只做进程内聚合，不能跨重启保留授标与 CAPA 事实。用户在 Design 阶段正式选择 B：以独立模块 + SDK 持久化 + DDL/迁移落地耐久性。本 Run 在 `origin/dev` 新基线上重放同一方案，需求、不变量与验收不变。设计沿用 DDD 分层、`bone-metadata-sdk` 持久化、统一 `ApiResponse`、登录态身份端口与 fail-closed 红线；不执行生产库操作。

## Goals / Non-Goals

**Goals:**

- 四类聚合的终态、唯一约束与人工终决事实在进程重启后仍可一致读回。
- DDL、迁移清单、数据库规范与门禁判据机器可验证，且注入必红。
- AI 建议与人工事实继续分离，不借持久化把建议态伪装成事实或效果达标。

**Non-Goals:**

- 不执行生产数据库迁移或改生产数据。
- 不接生产 MQ，不做 owner 契约冻结、评测转正、真实模型接入或效果声明。
- 不改合同 HITL、人工复核、治理态势等既有页面/端点规格。
- 不在本轮扩大到完整 S2P（合同/订单/发票/付款）。

## Approaches

1. **A：复用现有模块 + 内存仓储**：最小改动、无 L3，但重启丢失事实，已明确不满足审计与重放诉求。
2. **B：独立模块 + SDK Entity/DDL**（本次选择）：耐久、边界清晰、可独立门禁；代价是 L3 范围、迁移与 CI 同步成本，以及 owner 冻结前字段仍可能调整。
3. **C：扩展现有实体**：文件最少，但无法表达四类聚合各自的状态不变量，不采用。

## Decisions

1. **新增独立 Maven 模块 `bone-procurement-business`**：四类聚合集中在单一边界，避免把寻源/准入模块变成大杂烩。
   - 备选：塞入 sourcing + qualification。放弃：持久化表与门禁判据会跨两个模块分散，审计链不清。

2. **每类聚合一张表**：建议 `t_proc_bid`、`t_proc_quotation`、`t_proc_award`、`t_proc_capa`，均含租户、审计四列与业务唯一键。
   - 理由：与现有 `t_proc_*` 表风格一致，便于 DDL 对账与迁移清单检查。
   - 备选：一张通用事件表 + JSON。放弃：无法用数据库唯一键守住领域不变量，且 JSON 字段会绕过静态门禁。

3. **持久化只保存事实与建议引用，不保存模型 output 原文**：AI 评分/排序作为独立建议字段或引用，人工终决独立字段并保留操作人、时间、理由。
   - 理由：PII 禁入与审计可解释；持久化不得把建议态固化成事实。

4. **门禁按 A10.17 四入口一致**：新增 DDL/持久化判据必须同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`、门禁常量与注入探针；缺少任一入口即视为未完成。
   - 这是 L3 前置，未获明确架构授权前不实施该部分。

5. **权限先复用既有采购权限码**：若领域新端点确需新权限码，另列 L3 清单，不在本 change 静默新增种子。

## Risks / Trade-offs

- **L3 越界风险**：DDL/迁移/CI 修改需架构授权。→ Build 前取得明确授权；未授权停在 Design。
- **owner 契约未冻结导致字段漂移**：新增表字段可能后续调整。→ 只落领域不变量必需列，AI 相关字段只存引用与建议态，不存模型原文。
- **迁移失败影响既有表**：新增脚本必须 init-only 兼容、顺序连续。→ 迁移清单检查与 DDL 对账纳入注入必红探针；不执行生产库。
- **跨模块身份一致性**：新模块需复用登录态身份端口。→ 缺租户/空白操作人一律 fail-closed，端点契约测试覆盖冒名与跨租户。
- **全仓测试假红基线**：以采购模块与前端 app 范围为权威，不把无关模块失败归因本 change。

## Migration Plan

1. 取得 DDL、迁移脚本与 CI 三入口修改的 L3 授权。
2. 新增 Maven 模块与 pom 依赖，补齐领域、应用、基础设施三层。
3. 新增 4 张表 DDL、增量迁移脚本、数据库规范清单同步与 DDL 对账测试。
4. 实现 HTTP 端点与身份绑定测试，运行模块测试与 procuraAI 门禁。
5. 注入必红/还原必绿验证后进入 Verify；任何生产库操作仍不在范围内。
6. 回滚：撤销本 change 的代码、DDL 与门禁改动；已执行过的本地库变更按迁移脚本的 down 方案处理（若提供），不碰生产库。

## Open Questions

（无。L3 授权是 Build 前置门禁，不是可推迟的设计问题；owner 契约冻结属于明确非目标。）
