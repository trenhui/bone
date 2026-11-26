# Comet Design Handoff

- Change: claim-secure-signing
- Phase: design
- Mode: compact
- Context hash: 675917e4f5c800a66e5d1659c453d7d057bfd83d7f9a798a51d5d2e3096787e6

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/claim-secure-signing/proposal.md

- Source: openspec/changes/claim-secure-signing/proposal.md
- Lines: 1-31
- SHA256: 46f087383c5762fef91dad5652aadad53f673823af974491d533adfa06464c2c

```md
## Why

Bone 理赔目前只有「报案即受理签收」（`POST /api/v1/claims` 建案末端 `claim.accept()`，状态 `DRAFT → ACCEPTED`），没有独立的签收能力：既无法接收保险公司/CPL/第三方 TPA 推送的案件做自动签收，也无法对线下快递/柜台收到的案件做批次签收，更没有签收记录、签收轨迹、影像完备性校验和防重复签收。对照普康/亿保 TPA 的成熟实践，签收是理赔链路的正式起点（批次四态状态机 + `source`/`signType`/`signChannel` 三维区分 + 幂等 CAS + 全链路留痕），本次把这块能力补齐到 Bone，达到业界可审计水平。

## What Changes

- **新增签收领域模型**：新增签收批次聚合 `t_claim_sign_batch`（批次号 `batchNo` 唯一、来源 `source`=线上/线下、签收方式 `signType`=自动/人工、渠道 `signChannel`、快递/发件人等线下要素、状态 `signStatus`、签收人/时间/意见），子集合签收明细 `t_claim_sign_batch_item` 与追加式轨迹 `t_claim_sign_track`（无 version 列）。
- **批次四态状态机**：`WAIT_FOR_PARTICIPANT → WAIT_FOR_IMAGE → WAIT_FOR_CONFIRMATION → FINISHED`，另 `DELETED`；状态迁移带前置校验，终态拒绝重复签收。
- **线上自动签收双触发**：① 内部报案提交后自动建签收批次并签收（`source=线上`、`signType=自动`）；② 新增外部推送回执端点 `POST /api/v1/claims/sign/acknowledge`，接收保险公司/CPL/TPA 推送幂等自动建案+签收（`source=线上`、`signType=自动`、`signChannel` 记录推送方）。
- **线下人工签收**：新建批次 → 导入人员名单 → 上传影像 → 人工二次确认签收（`source=线下`、`signType=人工`），确认后案件阶段 `SIGNING → PRE_EXAM`，进入初审。
- **安全与幂等**：批次号唯一索引 + 状态 CAS 条件更新（`WHERE sign_status = 前置态`，影响行数 0 视为已签收）+ 幂等键（稳定键 ≤64 字符）+ 处理人权限/挂起校验 + 签收意见长度下限 + 全程轨迹落库；签收写入 REQUIRED 事务传播，禁 `SELECT ... FOR UPDATE`。
- **前端正式菜单页面**：`bone-claim-app` 新增「案件签收」页面（React18 + antd5），含线上/线下 Tab、批次列表、新建批次、人员名单导入、影像上传、二次确认签收、轨迹查看；以 visible 路由接入，同步 IAM 菜单迁移 SQL 与单导航 E2E 探针（名称/顺序逐字一致）。
- **错误码与契约**：新增签收相关错误码并同步前端中英文语言包与错误码台账；仓储方法按 SDK 规范提供 `sql/.../<method>.sql` 模板。

## Capabilities

### New Capabilities

- `claim/secure-signing`: 签收批次聚合与四态状态机、线上自动签收双触发、线下人工批次签收、来源/方式/渠道三维区分、幂等 CAS 与防重复、签收轨迹留痕、前端签收工作台的验收契约。

### Modified Capabilities

- `claim/web-response-contract`: 新增签收相关 REST 端点（`POST /api/v1/claims/sign/acknowledge`、`POST /api/v1/claims/sign/batches`、`/sign/batches/{id}/participants`、`/image`、`/confirm` 等）及其 `ApiResponse`/`PageResult` 契约与幂等键约定。

## Impact

- **数据库**：新增 `t_claim_sign_batch`、`t_claim_sign_batch_item`、`t_claim_sign_track`；迁移脚本写入 `scripts/migration/`，同步 `bone-init.sql` 基线；不执行生产 DDL（仅提供迁移脚本与本地验证）。
- **后端**：`synap-claim-app` 新增 domain 聚合/值对象/仓储、application 用例（自动签收、批次创建、名单导入、影像登记、确认签收、轨迹）、adapter/web Controller 与 DTO、错误码三处登记（ClaimErrorCodes + DEFAULT_HTTP_STATUS + 前端两语言包）、SQL 模板；提交链路末端接入自动签收用例。
- **前端**：`bone-claim-app` 新增 `CaseSignPage`、`claimSign` service、类型与状态字典、路由/权限/菜单接入、单测与 E2E 探针同步。
- **隔离**：独立 worktree `.worktrees/claim-secure-signing`，分支 `comet/claim-secure-signing`，不触碰主工作树 39 个在途改动。
- **不影响**：既有赔案状态机与阶段码、发票唯一约束、复核/资金链路、初审/核赔工作台既有行为；线上自动签收对既有报案提交保持向后兼容（新增副作用，不改响应结构）。

```

## openspec/changes/claim-secure-signing/design.md

- Source: openspec/changes/claim-secure-signing/design.md
- Lines: 1-78
- SHA256: d1e5cd5edcf85722260f1fdcb7f96fb7c64253a5dcda1c1afdf9c346c834c03e

```md
## Context

Bone 理赔现有链路：`POST /api/v1/claims` 建案末端调用 `claim.accept()`（`DRAFT → ACCEPTED`），无独立签收实体；前端 `bone-claim-app` 为 React18 + antd5 + qiankun 微应用（shell 前缀 `/sclaim/claim`）。参考实现（普康 `pk-tpa-saas`）为低代码 Vue3 系统：批次四态状态机 + `source/signType/signChannel` 三维区分 + `batchNo` 唯一，但其确认签收仅做状态前置校验、缺乐观锁，前端按钮无防抖。本次在独立 worktree 实现，需遵守 Bone 的 DDD 分层、只用 `bone-metadata-sdk`、禁 `SELECT ... FOR UPDATE`、@Version 乐观锁、新增仓储方法必交 SQL 模板、错误码四处同步等约束。动机详见 `proposal.md`，行为契约见 `specs/claim/secure-signing/spec.md`。

## Goals / Non-Goals

**Goals:**

- 建立以批次为聚合的正式签收模型，覆盖线上自动（内部报案 + 外部推送）与线下人工两条通道。
- 幂等与并发安全达到可审计水平（唯一键 + CAS + 幂等键 + 轨迹）。
- 前端提供正式菜单入口的签收工作台，与现有查询/工作台范式一致。

**Non-Goals:**

- 不重构既有赔案状态机、初审/核赔/资金链路；签收成功后仅把阶段推进到 `PRE_EXAM`。
- 不实现影像本体的存储（复用既有 `bone-file`/evidence 体系），签收批次只登记影像完备状态与关联。
- 不实现低代码 schema 渲染（PKRender 等价物），前端按 antd5 常规页面实现。
- 生产库 DDL 属部署动作，本次只产出迁移脚本，不在本机对共享库执行。

## Decisions

### D1：签收批次作为独立聚合根，明细与轨迹为子集合

- **决策**：新建聚合 `ClaimSignBatch`，内含 `batchNo/source/signType/signChannel/state/快递要素/签收结果`；明细 `SignBatchItem` 与轨迹 `SignTrackLog` 作为子集合（由批次仓储一并存取），不单独建 Repository（遵守「子集合不建 Repository」）。
- **理由**：参考实现把签收做成独立批次实体并带轨迹，Bone 现有 `ClaimCase` 不应被签收字段污染；子集合建模契合 DDD。
- **备选**：把签收字段挂在 `t_claim_case` 上（改动小但无法表达批次与多案签收）→ 否决。

### D2：状态机四态 + CAS 条件更新（不用 FOR UPDATE）

- **决策**：`WAIT_FOR_PARTICIPANT → WAIT_FOR_IMAGE → WAIT_FOR_CONFIRMATION → FINISHED`（+ `DELETED`）。确认签收用 `UPDATE ... WHERE sign_status = 'WAIT_FOR_CONFIRMATION' AND id = ?`，影响行数 0 即冲突；批次聚合带 `@Version` 乐观锁兜底。
- **理由**：参考实现缺锁，两个并发确认可双签；Bone 规范禁 `SELECT ... FOR UPDATE`，`@Version` 与条件更新是指定方案。
- **备选**：Redis 分布式锁 → 引入外部依赖且需处理锁续期，否决（CAS 已足够解决单批次确认竞争）。

### D3：线上自动签收双触发统一用例

- **决策**：抽出 `AutoSignApplicationService.signOnAcceptance(...)` 单一用例，内部报案提交链路末端与外部回执端点 `POST /api/v1/claims/sign/acknowledge` 均调用它；用例参数含 `businessKey`（外部幂等标识）、`signChannel`、`tenantId`，用稳定幂等键去重。
- **理由**：用户要求双触发；统一用例避免两套逻辑漂移，且保证两路径行为一致（同一状态、同一轨迹格式）。
- **备选**：两条链路各写一套 → 易漂移，否决。

### D4：幂等键归一化到 64 字符

- **决策**：前端稳定键 = 前缀 + FNV-1a 哈希（超长时截断），保证 `≤64`；后端在写端点校验长度并对同键请求返回首次结果。
- **理由**：项目实测稳定键超 `varchar(64)` 会在真库报 `COMMON_VALIDATION_FAILED`，必须提前钉死长度。

### D5：source/signType/signChannel 显式三维建模

- **决策**：来源 `ONLINE/OFFLINE`、方式 `AUTO/MANUAL`、渠道枚举（内部报案、API推送、快递、柜台）均为强类型值对象/枚举，`varchar` 存储并登记后端生成的字典，前端从 `dicts.generated.ts` 读取（不发明新码）。
- **理由**：参考实现用字典表柔性配置，Bone 既有字典生成机制可复用；显式字段让筛选与审计无需隐式推断。

### D6：前端 hidden→visible 正式菜单接入

- **决策**：`src/routes.tsx` 追加 visible 路由 `/sign`（名称「案件签收」），同步 IAM 菜单迁移 SQL（`/sclaim/claim/sign`，order_no 与声明顺序一致）与单导航 E2E 探针；页面具名导出 `CaseSignPage`，复用 `useClaimQuery`/`ClaimQueryFields`/`ClaimQueryBar`/`claimStatusMeta`/`AuthButton`/`useConfirmAction`。
- **理由**：用户选择正式菜单；探针会做菜单↔路由双向全等+顺序+逐字名称校验，漏一处判红。

### D7：错误码四处同步

- **决策**：新增签收错误码同时写入 `ClaimErrorCodes`、`DEFAULT_HTTP_STATUS`、前端中英文语言包、`Bone-错误码登记.md` §6；同一语义只保留单一配置源。
- **理由**：项目铁律——缺任一处即前端显示原始码或 HTTP 状态错配。

## Risks / Trade-offs

- **[风险] worktree 与主线程并行**：主线程有 39 个在途改动，若其也改签收相关文件，合并时冲突 → 缓解：所有改动仅在 worktree，提交前 `git status` 只提交本 change 路径，合并前用 `git merge-tree` 干跑。
- **[风险] 自动签收改变既有报案行为**：内部报案新增签收副作用，若签收失败可能回滚整个报案 → 缓解：自动签收在报案事务内且失败不吞错，明确「签收失败=报案失败」语义并加测试。
- **[风险] 外部推送幂等键来源不可信**：若推送方给的 businessKey 不稳定会重复签收 → 缓解：后端以业务唯一标识（匹配 `claimNo`/外部流水号）为幂等真源，推送方键仅作辅助。
- **[风险] 影像完备性判定口径**：签收批次「影像完成」需与既有 evidence 关联，口径不一致会误拒 → 缓解：明确以明细关联的 evidence 是否齐备（DocType 四类）为准，写清在 specs/design 并加契约测试。
- **[风险] 正式菜单 E2E 探针失败**：名称/顺序/SQL 任一处不一致 → 缓解：一次性同步三处并跑 `claim-single-nav-probe.mjs`。

## Migration Plan

1. 在 worktree 新分支上实现后端 domain/application/adapter、SQL 模板、迁移脚本（`scripts/migration/<序号>_claim_secure_signing.sql`）与 `bone-init.sql` 基线同步。
2. 实现前端页面/service/路由/权限/菜单 SQL/语言包，跑 typecheck、单测、构建。
3. 本地跑迁移脚本验证（仅本地库，不碰共享生产 DDL），联调双触发与线下四态流程。
4. 阶段 Verify 跑 `./scripts/check.sh` + 前端相关测试 + E2E 探针，记录真实结果。
5. 回滚：迁移脚本提供DOWN（drop 新表/列按 expand/contract 顺序），应用回滚到无签收端点版本；生产执行属部署动作由人工审批。

## Open Questions

无。四项关键决策（隔离方式、交付范围、线上触发语义、导航接入）已在 Open 前由用户拍板。

```

## openspec/changes/claim-secure-signing/tasks.md

- Source: openspec/changes/claim-secure-signing/tasks.md
- Lines: 1-30
- SHA256: d14d9cb3f4f29f5a9d504a5bb01ede76f5c5f31b48c1b9b189a42bbad57b8043

```md
## 1. 后端领域模型与持久化

- [ ] 1.1 新建签收聚合 `ClaimSignBatch`、值对象 `SignSource/SignType/SignChannel/SignStatus`、子集合 `SignBatchItem/SignTrackLog`（domain 零框架依赖），编译通过且领域单测覆盖四态迁移校验 <!-- comet-task:f18a25ba-0cef-4ef6-acf3-21709798cde3 -->
- [ ] 1.2 新建仓储接口 `ClaimSignBatchRepository`（含 CAS 条件更新方法，方法名禁 insert/update/delete/persist），提供全部 `sql/.../<method>.sql` 模板（首行为语句关键字、无注释），启动无 TemplateNotFoundException <!-- comet-task:a32d3510-a69b-49a2-a038-b0566ad3bc8f -->
- [ ] 1.3 编写迁移脚本 `scripts/migration/<序号>_claim_secure_signing.sql`（三张表+唯一索引+枚举约束）与 `bone-init.sql` 基线同步，本地迁移验证通过且不触碰生产库 <!-- comet-task:478862cb-de8b-4d2f-add5-f3d80887790c -->

## 2. 后端应用用例

- [ ] 2.1 实现 `AutoSignApplicationService.signOnAcceptance`（内部报案末端调用，source=ONLINE/signType=AUTO，写轨迹，事务 REQUIRED），单测覆盖成功、幂等重复、要素缺失三类场景 <!-- comet-task:62dc8068-d227-41eb-832f-bc6dd0156a78 -->
- [ ] 2.2 实现外部推送自动签收用例（业务唯一标识去重，signChannel 记录推送方），单测覆盖同 businessKey 重复推送幂等返回首次结果 <!-- comet-task:bb89f111-9be3-4566-8790-4e73b1a3427f -->
- [ ] 2.3 实现线下批次用例：创建批次、导入名单（生成明细、阶段 SIGNING）、登记影像、确认签收（CAS + 影像完备 + 意见校验 + 阶段推进 PRE_EXAM + 轨迹），单测覆盖四态流转与终态/跳步/意见过短拒绝 <!-- comet-task:c9876735-0e29-43f7-9625-ae05d5bd6657 -->
- [ ] 2.4 实现批次查询与轨迹查询用例（服务端真分页、按来源/状态/渠道筛选），单测覆盖筛选与分页边界 <!-- comet-task:7d5184c2-96c8-4c73-95bd-81ca873d6d08 -->

## 3. 后端适配器与契约

- [ ] 3.1 新增 `ClaimSignController` 端点（回执、批次 CRUD、名单导入、影像登记、确认、轨迹）与 `assembler/` 中显式 `*Resp` DTO 装配，机检无领域对象直出、装配收敛 <!-- comet-task:d761cafa-ae88-40d9-9959-6a63c6241bef -->
- [ ] 3.2 新增签收错误码并同步 `ClaimErrorCodes` + `DEFAULT_HTTP_STATUS` + 前端中英文语言包 + 错误码台账 §6 四处，冲突类错误映射 409 <!-- comet-task:67049349-5371-430b-8207-be0876c0d052 -->
- [ ] 3.3 写端点接受 `Idempotency-Key`（≤64 字符校验，稳定键同键返回首次结果），契约测试覆盖超长拒绝与重试幂等 <!-- comet-task:1a7ef5fb-e658-4846-ba71-5a3645355e8d -->

## 4. 前端页面与 API

- [ ] 4.1 新建 `src/services/claimSign.ts` 与类型（复用 `createApiClient('/api/v1/claims')`，写动作带稳定幂等键 ≤64），单测覆盖键归一化与响应解包 <!-- comet-task:f878b77c-28b9-4308-8811-30f5b58e7be4 -->
- [ ] 4.2 新建 `src/pages/CaseSignPage.tsx`（具名导出）：线上/线下 Tab、批次列表（复用 useClaimQuery/ClaimQueryFields/ClaimQueryBar/Table/normalizeTotal）、新建批次、名单导入、影像入口、二次确认、轨迹抽屉，组件测试覆盖四态渲染与确认防抖 <!-- comet-task:49d5561f-38c0-4121-99e1-466a87b213b0 -->
- [ ] 4.3 `routes.tsx` 追加 visible 路由 `/sign`（名称「案件签收」）+ `App.tsx` 权限守卫 + `constants/permissions.ts` 权限码，同步 IAM 菜单迁移 SQL（`/sclaim/claim/sign`，名称/顺序逐字一致），单导航 E2E 探针通过 <!-- comet-task:09f2c572-8f30-4dd7-b912-8df1b12261dc -->

## 5. 集成验证与收口

- [ ] 5.1 本地跑通双触发（内部报案自动签收 + 外部推送幂等签收）与线下四态全链路，记录真实请求/响应与轨迹 <!-- comet-task:da303d2e-1e3f-431f-b99d-d0801ab2d328 -->
- [ ] 5.2 跑前端 typecheck、单测、构建与 `./scripts/check.sh` 全量门禁（沙箱测试命令按项目纪律处理），结果填入 Verify 报告 <!-- comet-task:b29baa2b-06c5-422c-96b9-04b16b41b8bc -->
- [ ] 5.3 跑 `claim-single-nav-probe.mjs` 验证菜单↔路由双向全等与顺序，确认无幽灵菜单/不可达功能/名称漂移 <!-- comet-task:f78053b1-a0b0-45d5-928a-47089e697436 -->

```

## openspec/changes/claim-secure-signing/.openspec.yaml

- Source: openspec/changes/claim-secure-signing/.openspec.yaml
- Lines: 1-2
- SHA256: 3c3c72cd1a100807c59ef8347538654b4d2d22269f59561c985b01cb44c39491

```md
schema: spec-driven
created: 2026-11-25

```

## openspec/changes/claim-secure-signing/specs/claim/secure-signing/spec.md

- Source: openspec/changes/claim-secure-signing/specs/claim/secure-signing/spec.md
- Lines: 1-109
- SHA256: 433de87ed6c59bb2c88096659d9996e4dbe3b533a6634ad167256ab706c269aa

[TRUNCATED]

```md
## Purpose

定义 Bone 理赔的正式签收能力：以签收批次为聚合，覆盖线上自动签收（内部报案与外部推送双触发）与线下人工签收两条通道，规定状态机、幂等、权限与留痕等对外可验证行为。

## ADDED Requirements

### Requirement: 签收批次聚合与批次号唯一

系统 SHALL 以签收批次为单位管理签收，每个批次 MUST 具备全局唯一批次号 `batchNo`、租户 `tenantId`、来源 `source`（`ONLINE`/`OFFLINE`）、签收方式 `signType`（`AUTO`/`MANUAL`）、签收渠道 `signChannel`、状态 `signStatus`、签收人/时间/意见；数据库 MUST 对 `batchNo` 建唯一索引，重复批次号 MUST 被拒绝。批次下 MUST 可挂多条签收明细（赔案），明细与批次为多对一。

#### Scenario: 创建批次生成唯一批次号
- **WHEN** 系统创建一个线上或线下签收批次
- **THEN** 自动生成符合编码规则的 `batchNo`（如 `SG` 前缀），且重复使用该号创建第二批 MUST 失败并返回明确错误码

#### Scenario: 线下批次携带物流要素
- **WHEN** 以 `source=OFFLINE` 创建批次并填写快递公司/单号/发件人/到件时间
- **THEN** 这些要素 MUST 随批次持久化并可在批次详情查询到；`source=ONLINE` 的批次 MUST 不要求这些要素

### Requirement: 签收批次四态状态机

批次状态 MUST 为 `WAIT_FOR_PARTICIPANT` / `WAIT_FOR_IMAGE` / `WAIT_FOR_CONFIRMATION` / `FINISHED` / `DELETED` 之一，迁移 MUST 遵循 `WAIT_FOR_PARTICIPANT → WAIT_FOR_IMAGE → WAIT_FOR_CONFIRMATION → FINISHED`，且每个迁移 MUST 校验前置状态；非法跃迁 MUST 被拒绝且不产生副作用。`FINISHED` 为终态，MUST 拒绝再次确认签收。

#### Scenario: 合法四步流转
- **WHEN** 批次依次完成创建、名单导入、影像上传、人工确认
- **THEN** 状态依次进入四个合法态，每次迁移成功后可查询到新状态

#### Scenario: 终态幂等拒绝
- **WHEN** 对已 `FINISHED` 的批次再次调用确认签收
- **THEN** 系统 MUST 拒绝并返回「已完成签收，无需重复签收」类错误，状态与轨迹不变

#### Scenario: 跳过前置步骤被拒绝
- **WHEN** 批次尚在 `WAIT_FOR_PARTICIPANT` 时直接请求确认签收
- **THEN** 系统 MUST 拒绝并说明前置条件未满足

### Requirement: 线上自动签收双触发

系统 SHALL 支持两条线上自动签收触发路径：① 内部报案提交成功后，在同一事务边界内自动创建 `source=ONLINE`、`signType=AUTO` 的签收批次并签收该案件；② 通过外部回执端点接收保险公司/CPL/TPA 推送的案件信息，幂等地自动建案并签收（`source=ONLINE`、`signType=AUTO`，`signChannel` 记录推送方标识）。两条路径 MUST 产生可查询的签收轨迹，且 MUST 不改变既有报案提交 API 的响应结构。

#### Scenario: 内部报案自动签收
- **WHEN** 用户提交一份内部报案且校验通过
- **THEN** 系统在报案落库后自动创建线上自动签收批次、签收该案件，`ACCEPTED` 状态与签收轨迹均可查询

#### Scenario: 外部推送幂等签收
- **WHEN** 外部系统以同一业务标识重复推送同一案件两次
- **THEN** 系统 MUST 只创建一条签收记录与一条批次，第二次请求返回第一次的结果（幂等成功），批次号不变

#### Scenario: 外部推送缺失必填要素
- **WHEN** 外部推送缺少案件标识或来源方标识
- **THEN** 系统 MUST 拒绝并返回参数校验错误码，不创建任何批次或案件

### Requirement: 线下人工批次签收

系统 SHALL 支持线下人工批次签收：创建批次 → 导入人员名单（生成明细，案件阶段 `SIGNING`）→ 登记影像上传 → 人工二次确认签收；确认时 MUST 校验所有明细影像完备，成功后批次置 `FINISHED`、明细案件阶段 `SIGNING → PRE_EXAM`（进入初审），并记录签收人、签收时间与签收意见。签收意见 MUST 非空且长度不低于系统配置下限（默认 10 字）。

#### Scenario: 完整线下签收流程
- **WHEN** 操作人员创建线下批次、导入人员名单、完成影像上传并提交非空且满足长度的签收意见确认
- **THEN** 批次状态变为 `FINISHED`，全部明细案件进入 `PRE_EXAM`，轨迹记录签收人/时间/意见

#### Scenario: 影像未完备拒绝确认
- **WHEN** 存在任一明细尚未完成影像上传时请求确认签收
- **THEN** 系统 MUST 拒绝并列出未完成影像的明细，批次状态不变

#### Scenario: 签收意见过短拒绝
- **WHEN** 确认签收时签收意见为空或少于配置下限
- **THEN** 系统 MUST 拒绝并返回校验错误，不产生状态迁移

### Requirement: 防重复签收与并发安全

签收确认 MUST 使用 CAS 条件更新（按前置状态过滤，例如 `WHERE sign_status = 'WAIT_FOR_CONFIRMATION'`），影响行数为 0 MUST 视为已被他人签收并返回冲突类错误；MUST NOT 使用 `SELECT ... FOR UPDATE`。所有签收写操作 MUST 在 REQUIRED 事务传播下执行，携带幂等键（稳定键 MUST 归一化到 64 字符以内）；同一幂等键的重复请求 MUST 返回首次结果。

#### Scenario: 并发确认只有一个成功
- **WHEN** 两个请求同时对同一 `WAIT_FOR_CONFIRMATION` 批次确认签收
- **THEN** 恰有一个成功置 `FINISHED`，另一个 MUST 收到冲突错误且无副作用

#### Scenario: 幂等键重试
- **WHEN** 客户端使用同一幂等键重试一次已成功的确认签收请求
- **THEN** 系统 MUST 返回首次成功结果，不重复迁移状态、不重复写轨迹

### Requirement: 权限与签收轨迹留痕


```

Full source: openspec/changes/claim-secure-signing/specs/claim/secure-signing/spec.md

## openspec/changes/claim-secure-signing/specs/claim/web-response-contract/spec.md

- Source: openspec/changes/claim-secure-signing/specs/claim/web-response-contract/spec.md
- Lines: 1-37
- SHA256: 25e8308d879f8e361d13040e420ca818b1365ec62ac64807228410ec5fe07e28

```md
## ADDED Requirements

### Requirement: 签收端点响应契约

synap-claim 的签收相关端点 SHALL 遵循既有 `ApiResponse<T>`/`PageResult<T>` 契约，响应泛型 MUST 为显式 `*Resp` DTO（如 `SignBatchResp`、`SignBatchItemResp`、`SignTrackResp`），不得直接返回签收聚合或轨迹领域对象；装配逻辑 MUST 收敛到 `adapter/web/assembler/`，Controller 不得内联构造响应。

#### Scenario: 签收批次列表分页响应
- **WHEN** 请求签收批次列表端点
- **THEN** 返回 `ApiResponse<PageResult<SignBatchResp>>`，`records` 字段为显式响应 DTO，分页字段与既有案件查询契约一致

#### Scenario: 签收轨迹端点响应
- **WHEN** 请求批次轨迹查询端点
- **THEN** 返回显式 `SignTrackResp` 列表，不包含内部领域类型

### Requirement: 签收写端点幂等键契约

所有签收写端点（自动签收回执、创建批次、导入名单、登记影像、确认签收）MUST 接受幂等键，优先使用请求头 `Idempotency-Key`，长度 MUST 不超过 64 字符；同一键重复请求 MUST 返回首次结果而非重复副作用。前端 MUST 对稳定语义的操作使用由业务标识派生的稳定键（超长时前缀+哈希截断），仅对无法预知的操作使用随机键兜底。

#### Scenario: 幂等键超长被拒
- **WHEN** 客户端提交长度超过 64 字符的 `Idempotency-Key`
- **THEN** 系统 MUST 拒绝并返回 `COMMON_VALIDATION_FAILED` 类错误码，不执行签收写操作

#### Scenario: 稳定键跨重试一致
- **WHEN** 前端对同一案件同一签收动作重试并沿用派生稳定键
- **THEN** 后端 MUST 识别为同一操作并返回首次结果

### Requirement: 签收错误码对外契约

签收相关的业务失败 MUST 返回专用错误码并映射到恰当的 HTTP 状态码，至少覆盖：重复签收冲突、前置状态不满足、影像未完备、签收意见不合法、权限不足、案件挂起、外部推送要素缺失；错误码 MUST 在后端错误码枚举、默认 HTTP 状态映射、前端中英文语言包与错误码台账四处保持一致。

#### Scenario: 冲突类错误映射
- **WHEN** 因并发确认导致 CAS 失败
- **THEN** 响应 MUST 携带签收冲突错误码并映射为冲突类 HTTP 状态码，前端 MUST 提示「刷新后重试」语义

#### Scenario: 前端可解析错误文案
- **WHEN** 前端收到任一签收错误码
- **THEN** 中英文语言包与后端枚举 MUST 存在同名条目，前端 MUST 显示可读文案而非原始码

```
