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
