# Comet Design Handoff

- Change: procuraai-durable-business-aggregates-rebaseline
- Phase: design
- Mode: compact
- Context hash: 0538f52cf436a2efae3eb3cbbbdf5d2bad2a8000b5b46dc535883e8f1470754e

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/proposal.md

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/proposal.md
- Lines: 1-33
- SHA256: beb0c9e8d86558f152f5386fc3c3ecb604a036c3c3205502d65b08d3ad711071

```md
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

```

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/design.md

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/design.md
- Lines: 1-70
- SHA256: e725c60d295248e4038bd2c9f8c98f41d443c23b33e30c2313e824ed964d9cd8

```md
---
comet_change: procuraai-durable-business-aggregates-rebaseline
role: technical-design
canonical_spec: openspec
---

# Design：procuraAI 持久业务聚合补齐（B 方案）

## Context

见 `proposal.md`。A 方案（`procuraai-business-aggregates-completion`）只做进程内聚合，不能跨重启保留授标与 CAPA 事实。用户在 Design 阶段正式选择 B：以独立模块 + SDK 持久化 + DDL/迁移落地耐久性。设计沿用 DDD 分层、`bone-metadata-sdk` 持久化、统一 `ApiResponse`、登录态身份端口与 fail-closed 红线；不执行生产库操作。

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

```

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/tasks.md

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/tasks.md
- Lines: 1-33
- SHA256: 419956b681018ecdf62feb4711fcb77754fcfae59db26fe7eda0aede76593444

```md
# Tasks

## 1. 前置授权与模块骨架

- [ ] 1.1 记录 DDL、迁移脚本与 CI 三入口修改的 L3 授权证据；未取得授权时停止且不进入后续实现，授权文件/用户决定可追溯 <!-- comet-task:d8a80bf7-2e3d-4086-9bfe-617fb963839a -->
- [ ] 1.2 新建 `bone-procurement-business` Maven 模块及父子 pom 依赖；`mvn -o -f .../pom.xml test` 空模块编译通过 <!-- comet-task:9251e76d-bb64-4bea-b2dc-47e82ffa44eb -->

## 2. DDL、迁移与门禁

- [ ] 2.1 在 `bone-init.sql` 新增 4 张业务表（租户/审计列、业务唯一键与索引）并同步数据库规范清单；DDL 对账与必备列检查通过 <!-- comet-task:9a515211-05e8-4d43-aa0a-02791815e02e -->
- [ ] 2.2 新增增量迁移脚本并登记迁移清单；`check-migration-manifest.py` 通过且 order 连续 <!-- comet-task:918327dc-3504-43f3-af31-29f754c5dbdc -->
- [ ] 2.3 按 A10.17 同步 `ci.yml`、`scripts/check.sh`、`check-procura-ci.sh`、门禁常量与注入探针；注入必红、还原必绿均通过 <!-- comet-task:83c836b2-0ac6-4e96-a183-8ec92b4f71df -->

## 3. 领域聚合

- [ ] 3.1 实现 Bid/Quotation/Award 聚合的显式迁移、幂等、不可比标记、具名人工授标与终态不可回写；`BidQuotationAwardTest` 覆盖全部负向场景，测试通过 <!-- comet-task:3ed46cb3-6806-4174-9e42-98a8e62c55e2 -->
- [ ] 3.2 实现 CAPA 立项、责任人/期限校验、超期转人工与具名关闭；`CapaTest` 覆盖缺失字段、超期、关闭与自动状态隔离，测试通过 <!-- comet-task:cf98b798-589e-4ad8-9818-a6c69b87522b -->

## 4. SDK 持久化

- [ ] 4.1 为四类聚合定义 SDK Entity 与仓储适配器；`BusinessPersistenceRestartTest` 验证授标与 CAPA 重启读回一致，测试通过 <!-- comet-task:4b3399e5-7eda-4d40-adcf-6bd6003b8c2f -->
- [ ] 4.2 验证租户唯一键与跨租户隔离；`BusinessTenantIsolationTest` 验证同租户重复写入被拒、跨租户不可覆写/不可读，测试通过 <!-- comet-task:5c6831d6-e61f-40b0-a51f-f21d47be2083 -->

## 5. 应用与 HTTP

- [ ] 5.1 实现只读查询与具名人工操作应用服务，复用登录态身份端口；`BusinessApplicationServiceTest` 验证匿名/冒名/AI 建议被拒，测试通过 <!-- comet-task:c55d734d-c544-4f0a-a9ea-e87e8d0f80bf -->
- [ ] 5.2 新增统一 `ApiResponse` HTTP 端点与字段白名单 DTO；`BusinessEndpointContractTest` 验证权限、请求形状、PII 不回传与失败态，测试通过 <!-- comet-task:975817c6-56ca-4d3b-9d55-adb8dc919de6 -->

## 6. 集成验收

- [ ] 6.1 运行采购后端模块测试与 procuraAI 主门禁，记录测试数、失败数、门禁与注入探针结果，全部通过 <!-- comet-task:ca0a1754-7ace-4568-9037-efcfbdfeda66 -->
- [ ] 6.2 运行 `comet classic openspec -- validate <change> --strict`，确认 9 个新增需求与全部场景通过严格校验 <!-- comet-task:0757dc09-8823-4453-8691-64ee71b89c04 -->
- [ ] 6.3 独立审查 DDL/迁移、身份边界、AI 建议隔离与门禁同步，修复全部 CRITICAL/IMPORTANT 后复测通过 <!-- comet-task:41be5c3a-9da9-4381-b217-fa778463890c -->

```

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/.openspec.yaml

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/.openspec.yaml
- Lines: 1-2
- SHA256: 4492318684c90914e715dee55afe9b23f60f7465350ada31d1d9437964f126f8

```md
schema: spec-driven
created: 2026-11-26

```

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/specs/procurement/business-module-skeleton/spec.md

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/specs/procurement/business-module-skeleton/spec.md
- Lines: 1-91
- SHA256: f12fa0e51d770f4a7ff1deba1397a7ac87725850e8a909597ea794c137d094ce

[TRUNCATED]

```md
# Spec Delta

## ADDED Requirements

### Requirement: 投标生命周期与到期拒收

系统 SHALL 用显式状态（DRAFT / SUBMITTED / WITHDRAWN / REJECTED）守护投标；到期后 MUST NOT 接收新投标或从撤回态恢复提交。同一租户、RFx 与供应商组合 MUST 唯一；同一来源引用重复投递 MUST 幂等，不得生成第二条投标事实。

#### Scenario: 到期后提交被拒
- **WHEN** 当前时间不早于 RFx 截止时间时对有效 DRAFT 投标执行提交
- **THEN** 提交被拒，投标保持原状态，不产生投标事实

#### Scenario: 同一供应商重复投标被拒
- **WHEN** 同一租户同一 RFx 下已存在该供应商的 SUBMITTED 投标时再次提交
- **THEN** 第二次提交被拒，既有投标状态与内容不变

#### Scenario: 来源引用重复投递幂等
- **WHEN** 以相同租户、RFx、供应商与来源引用重复登记投标
- **THEN** 系统复用既有投标标识，不新增记录，也不重复触发提交副作用

#### Scenario: 撤回态不得恢复提交
- **WHEN** 对 WITHDRAWN 投标再次执行提交
- **THEN** 操作被拒，投标保持 WITHDRAWN

### Requirement: 报价比价只读与 AI 建议边界

系统 SHALL 以报价明细、币种与有效期形成只读比价视图；币种不一致或有效期已过的报价 MUST 标记为不可比而不被静默纳入排序。AI 评分与推荐排序 MUST 只作建议字段，不得产生授标、入围或自动淘汰结论。

#### Scenario: 过期报价不可比
- **WHEN** 报价有效期已过时被纳入比价视图
- **THEN** 该报价被标记为不可比，排序不将其作为有效候选

#### Scenario: 币种不一致不可比
- **WHEN** 报价币种与 RFx 预算币种不一致时被纳入比价视图
- **THEN** 该报价被标记为不可比，不参与金额排序

#### Scenario: AI 推荐不自动授标
- **WHEN** 报价附带 AI 推荐排序或高分建议
- **THEN** 视图仅展示建议字段，项目状态不得进入 AWARDED，供应商不得被自动淘汰

### Requirement: 授标记录具名人工终决

系统 SHALL 只允许登录态具名人工确认授标或拒绝；匿名、空白操作人、AI 建议或请求体自报身份 MUST 被拒绝。授标后项目与相关投标终态 MUST NOT 回写，记录 MUST 包含确认人、时间与理由。

#### Scenario: 匿名或空白操作人不得授标
- **WHEN** 未认证主体或空白确认人请求人工授标
- **THEN** 请求被拒，项目状态保持不变，不产生授标记录

#### Scenario: AI 建议不得直接授标
- **WHEN** 系统仅依据报价 AI 推荐且无具名人工确认
- **THEN** 项目不得进入 AWARDED，并返回明确拒绝原因

#### Scenario: 具名人工授标成功并留痕
- **WHEN** 具有授标权限的登录态操作人以非空理由确认授标
- **THEN** 项目进入 AWARDED，授标记录包含登录态确认人、时间与理由

#### Scenario: 授标终态不可回写
- **WHEN** 对已 AWARDED 项目再次执行授标或状态回退请求
- **THEN** 请求被拒，项目状态与既有授标记录不变

### Requirement: CAPA 立项、整改与超期转人工

系统 SHALL 为供应商问题建立 CAPA 记录，包含问题描述、责任人、期限、整改动作与关闭证据；责任人或期限缺失 MUST 拒绝立项。超期未关闭 MUST 转人工跟进并保留原期限，不得自动放宽期限。CAPA MUST NOT 自动改变供应商准入状态、绩效评分或授信。

#### Scenario: 缺失责任人或期限不得立项
- **WHEN** 创建 CAPA 时责任人或期限为空白
- **THEN** 立项被拒，不产生 CAPA 事实

#### Scenario: 超期未关闭转人工
- **WHEN** CAPA 超过期限且仍处于 OPEN
- **THEN** 标记需人工跟进并保留原期限，不自动关闭或延长

#### Scenario: 关闭必须具备名操作人与证据
- **WHEN** 以空白操作人或无关闭证据请求关闭 CAPA
- **THEN** 关闭被拒，CAPA 状态保持 OPEN

#### Scenario: CAPA 不自动改变供应商状态
- **WHEN** CAPA 处于 OPEN、超期或关闭任一状态
- **THEN** 系统不得自动改变供应商准入状态、绩效评分或授信


```

Full source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/specs/procurement/business-module-skeleton/spec.md

## openspec/changes/procuraai-durable-business-aggregates-rebaseline/specs/procurement/production-persistence/spec.md

- Source: openspec/changes/procuraai-durable-business-aggregates-rebaseline/specs/procurement/production-persistence/spec.md
- Lines: 1-63
- SHA256: d8a036075a1e90f88f9f0ca3be5af46d76a2dcb41aadb48e8f502fe997946fdf

```md
# Spec Delta

## ADDED Requirements

### Requirement: 业务聚合持久化与重启读回

系统 SHALL 以 SDK 持久化事实保存投标、报价、授标与 CAPA 的生命周期状态；进程重启后同一业务键 MUST 读回一致状态与终决记录，内存缓存不得作为唯一事实源。终态回写 MUST 在持久化层被拒绝。

#### Scenario: 重启后读回授标记录
- **WHEN** 具名人工授标成功后进程重启并查询同一 RFx
- **THEN** 返回 AWARDED 与既有确认人、时间、理由，不回退到中间态

#### Scenario: 重启后 CAPA 状态一致
- **WHEN** CAPA 关闭或超期转人工后进程重启并查询同一 CAPA
- **THEN** 返回与重启前一致的状态与关闭证据/跟进标记

#### Scenario: 终态持久化回写被拒
- **WHEN** 对已持久化的 AWARDED 或 CLOSED 记录执行回写请求
- **THEN** 持久化状态与审计记录不变，请求被拒

### Requirement: 租户唯一约束与跨租户隔离

系统 SHALL 对四类聚合使用租户锚定的业务唯一键；同一租户内业务键重复写入 MUST 被拒，跨租户同业务键 MUST 允许并存但不得互相覆写或读取。缺失租户上下文 MUST fail-closed 拒绝写入与查询。

#### Scenario: 同租户业务键重复被拒
- **WHEN** 同一租户以相同业务键重复创建投标或 CAPA
- **THEN** 第二次写入被拒，既有记录不变

#### Scenario: 跨租户同业务键并存但隔离
- **WHEN** 租户 A 与租户 B 使用相同业务键分别创建记录
- **THEN** 两条记录并存；任一租户查询或覆写对方记录均被拒

#### Scenario: 缺失租户上下文拒绝写入
- **WHEN** 写入请求无法解析登录态租户
- **THEN** 请求被拒且不产生持久化副作用

### Requirement: DDL 与迁移清单同步

系统 SHALL 在 `bone-init.sql` 定义新增业务表并包含全部必备列与审计四列；新增表的业务唯一键与索引 MUST 与领域不变量一致。增量迁移脚本 MUST 登记到迁移清单且顺序连续；数据库规范清单 MUST 与 SQL 表清单零差异。

#### Scenario: DDL 必备列与唯一键齐全
- **WHEN** 对新增表运行 DDL 对账与必备列检查
- **THEN** 租户列、审计列、业务唯一键与索引均通过，零违规

#### Scenario: 迁移清单连续
- **WHEN** 运行迁移清单检查
- **THEN** 新增脚本已登记且 order 连续，无重复或缺失

#### Scenario: 规范清单同步
- **WHEN** 运行 DDL/文档同步检查
- **THEN** SQL 表清单与数据库规范 §2.17 零差异

### Requirement: 持久化判据机器可验证

系统 SHALL 为四类聚合的租户唯一约束、重启读回、终态不可回写与 DDL/迁移同步提供机器可验证门禁判据；新增判据 MUST 同步 A10.17 三入口与门禁常量，并通过注入必红与正例不误杀探针。

#### Scenario: 注入必红
- **WHEN** 删除新增表的唯一键、移除任一 CI 入口判据或篡改迁移清单
- **THEN** 门禁转红并命中预期判据键

#### Scenario: 正例不误杀
- **WHEN** 未注入变异且实现满足全部持久化判据
- **THEN** 主判据与全部正例保持通过

```
