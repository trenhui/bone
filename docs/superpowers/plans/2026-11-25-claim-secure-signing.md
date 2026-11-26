---
change: claim-secure-signing
design-doc: openspec/changes/claim-secure-signing/design.md
base-ref: 62e589b3c74ca83e237d3a3a3207f0f56e1a2b97
plan-revision: 1
archived-with: 2026-11-26-claim-secure-signing
---

<!-- comet-task-authority: openspec/changes/claim-secure-signing/tasks.md -->

# Build Plan — 案件签收（线上自动签收 + 线下签收）

> 唯一口径：本 plan 是实施口径真源；proposal/specs/design/tasks 已由 Open/Design 批准并哈希锁定，本轮不修改。任务完成以 tasks.md 为准，本 plan 用 `comet-task-ref` 映射，不复制 checkbox。
> 配置：direct + tdd + standard（用户 2026-11-25 批准，direct_override=true）。TDD 要求：并发/幂等/状态机/校验等关键逻辑先写失败用例再实现；standard 审查：风险任务与最终集成各一次独立审查。

## 1. 基线事实

- worktree `/Users/renhui.trh/wps/bone/.worktrees/claim-secure-signing`，分支 `comet/claim-secure-signing`，基于 dev `62e589b3c74ca83e...`，`git status` 干净。
- 后端 `bone-apps/synap-claim/synap-claim-app`：DDD 四层，持久化仅 bone-metadata-sdk，禁 `SELECT ... FOR UPDATE`；新增仓储方法必配 `sql/.../<Repo>/<method>.sql` 模板（首行语句关键字、无注释）。
- 前端 `bone-frontend/apps/bone-claim-app`：React18+antd5+qiankun，basename `/sclaim/claim`，无 `@/` 别名（相对路径 import），ID 全用 string（19 位雪花 ID），antd2 字按钮注入空格（测试用 `/^签\s*收$/` 或 data-testid）。
- 迁移：`scripts/migration/` 清单唯一真源是 `migration-manifest.json` 的 `max(order)+1`；脚本 + 清单 + `bone-init.sql` 同批；已发迁移不改一字；生产 DDL 属 L4 禁止 AI 执行，本 change 只产出脚本。

## 2. 实施顺序与任务映射

<!-- comet-task-ref:f18a25ba-0cef-4ef6-acf3-21709798cde3 -->
### T1.1 领域模型（后端 domain）
- 新建 `domain/model/sign/`：`ClaimSignBatch`（聚合根，@Version）、`SignBatchItem`、`SignTrackLog`；值对象 `SignSource`/`SignType`/`SignChannel`/`SignStatus`（四态+DELETED），`transitionTo` 含前置校验。
- TDD：先写 `SignStatusTransitionTest`（合法四步、跳过前置、终态重复均断言），红灯后实现。
- 验证：`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test -Dtest=SignStatusTransitionTest` 通过；domain 零框架依赖机检通过。

<!-- comet-task-ref:a32d3510-a69b-49a2-a038-b0566ad3bc8f -->
### T1.2 仓储与 SQL 模板
- `ClaimSignBatchRepository`（含 `findByBatchNo`、`confirmWithCas(id, expectedStatus)` —— 方法名避 insert/update/delete/persist）。
- 每个方法配 `src/main/resources/sql/ClaimSignBatchRepository/<method>.sql`；CAS 模板首行 `UPDATE`，含 `WHERE sign_status = ?`。
- 验证：应用上下文启动无 `TemplateNotFoundException`；CAS 方法单测影响行数语义正确。

<!-- comet-task-ref:478862cb-de8b-4d2f-add5-f3d80887790c -->
### T1.3 迁移脚本
- 新脚本写 `scripts/migration/<max+1>_claim_secure_signing.sql`：三张表、batch_no 唯一索引、必要索引；追加 `information_schema` 断言 + `SIGNAL`（MESSAGE_TEXT ≤128 字符）；同步 `bone-init.sql` 与 manifest（order 取 max+1）。
- 验证：`scripts/check.sh` 中迁移相关步骤通过；本地库可执行且重复执行被 SIGNAL 拒绝；不碰生产库。

<!-- comet-task-ref:62dc8068-d227-41eb-832f-bc6dd0156a78 -->
### T2.1 内部报案自动签收用例
- `AutoSignApplicationService.signOnAcceptance(...)`，报案提交末端调用；事务 REQUIRED，source=ONLINE/signType=AUTO，写轨迹；失败不吞错（签收失败=报案失败）。
- TDD：先写成功/幂等重复/要素缺失三用例，红灯后实现。
- 验证：三用例通过；`ClaimCaseApplicationService` 提交链路集成测试确认新副作用。

<!-- comet-task-ref:bb89f111-9be3-4566-8790-4e73b1a3427f -->
### T2.2 外部推送自动签收
- 回执端点入参 DTO（businessKey、signChannel、案件要素），按 businessKey 幂等：已存在则返回首次批次，不重复建案。
- TDD：先写同 key 重复推送幂等用例，红灯后实现。
- 验证：幂等用例通过，轨迹只写一次。

<!-- comet-task-ref:c9876735-0e29-43f7-9625-ae05d5bd6657 -->
### T2.3 线下批次四步用例
- 创建批次→导入名单（明细 stage=SIGNING）→登记影像→确认签收（CAS + 影像完备 + 意见≥10字 + 阶段推进 PRE_EXAM + 轨迹）。
- TDD：先写四态流转、终态拒绝、跳步拒绝、影像未完备拒绝、意见过短拒绝五组用例，红灯后实现。
- 验证：五组用例全通过；权限/挂起校验单测通过。

<!-- comet-task-ref:7d5184c2-96c8-4c73-95bd-81ca873d6d08 -->
### T2.4 查询用例
- 批次列表（服务端真分页，按 source/status/channel 筛选）与轨迹查询。
- 验证：分页边界与筛选组合单测通过。

<!-- comet-task-ref:d761cafa-ae88-40d9-9959-6a63c6241bef -->
### T3.1 Controller 与 Assembler
- `ClaimSignController` 挂 `/api/v1/claims/sign/**`：`POST acknowledge`、`POST batches`、`POST batches/{id}/participants`、`POST batches/{id}/image`、`POST batches/{id}/confirm`、`GET batches`、`GET batches/{id}/tracks`。
- 响应 `ApiResponse<PageResult<SignBatchResp>>` 等显式 DTO，装配在 `adapter/web/assembler/`。
- 注意：POSTMapping 登记 `{"", "/"}` 两路径（避免尾斜杠 404）。
- 验证：响应契约机检无领域对象直出；装配收敛检查通过。

<!-- comet-task-ref:67049349-5371-430b-8207-be0876c0d052 -->
### T3.2 错误码四处同步
- 新增签收错误码（冲突409、前置状态不满足、影像未完备、意见非法、权限、挂起、推送要素缺失），写 `ClaimErrorCodes` + `DEFAULT_HTTP_STATUS` + 前端中英文语言包 + `Bone-错误码登记.md` §6。
- 验证：四处 grep 命中一致；前端可解析文案测试通过。

<!-- comet-task-ref:1a7ef5fb-e658-4846-ba71-5a3645355e8d -->
### T3.3 幂等键契约
- 写端点读 `Idempotency-Key` 头，≤64 校验，超长拒绝；同键返回首次结果。
- 验证：契约测试覆盖超长拒绝与重试幂等。

<!-- comet-task-ref:f878b77c-28b9-4308-8811-30f5b58e7be4 -->
### T4.1 前端 service
- `src/services/claimSign.ts`：createApiClient('/api/v1/claims')，类型追加到 types.ts，barrel 导出；稳定幂等键 `boundedStableKey` ≤64。
- TDD：先写键归一化与响应解包测试，红灯后实现。
- 验证：单测通过；typecheck 通过。

<!-- comet-task-ref:49d5561f-38c0-4121-99e1-466a87b213b0 -->
### T4.2 前端页面
- `src/pages/CaseSignPage.tsx`（具名导出）：线上/线下 Tab、批次列表（复用 useClaimQuery + ClaimQueryFields + ClaimQueryBar + Table + normalizeTotal）、新建批次 Modal、名单导入、影像入口、二次确认（useConfirmAction + confirmLoading + 按钮禁用）、轨迹 Drawer。
- 权限 `AuthButton` + PERMISSIONS；消息固定 key；ID 保持 string。
- 验证：组件测试覆盖四态渲染、确认防抖、筛选 URL 契约；typecheck 通过。

<!-- comet-task-ref:09f2c572-8f30-4dd7-b912-8df1b12261dc -->
### T4.3 路由/菜单/权限/E2E
- `routes.tsx` visible `/sign`（名称「案件签收」）+ App.tsx 守卫 + permissions.ts；IAM 菜单迁移 SQL（`/sclaim/claim/sign`，名称/顺序逐字一致）。
- 验证：`claim-single-nav-probe.mjs` 通过（无幽灵菜单/不可达/名称漂移/顺序漂移）。

<!-- comet-task-ref:da303d2e-1e3f-431f-b99d-d0801ab2d328 -->
### T5.1 本地联调
- 本地跑通双触发与线下四态，记录真实请求/响应与轨迹。
- 验证：联调记录写入 Verify 报告。

<!-- comet-task-ref:b29baa2b-06c5-422c-96b9-04b16b41b8bc -->
### T5.2 全量门禁
- 跑 typecheck、单测、构建、`./scripts/check.sh`（沙箱测试按项目纪律越权执行）。
- 验证：全部通过，结果写入 Verify 报告。

<!-- comet-task-ref:f78053b1-a0b0-45d5-928a-47089e697436 -->
### T5.3 菜单探针
- 跑单导航探针验证。
- 验证：通过。

## 3. 审查策略（standard）

- 风险任务：T1.2（CAS 仓储）、T2.1/T2.2（幂等）、T2.3（状态机）、T3.2（错误码四处）、T4.3（菜单三处同步）——每个完成后做一次独立审查（检查 diff + 对照 spec + 反向用例）。
- 最终集成审查：所有任务完成后，对整体 diff 做一次跨层一致性审查（契约↔实现↔测试↔迁移），记录发现与修复。

## 4. 完成判据

- 16 个任务勾选、standard 审查无遗留 CRITICAL/IMPORTANT、门禁全绿、Verify 报告含真实命令与结果。
