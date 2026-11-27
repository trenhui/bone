---
comet_change: claim-secure-signing
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-26-claim-secure-signing
status: final
---

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
