---
change: procuraai-human-decision-identity-binding
design-doc: openspec/changes/procuraai-human-decision-identity-binding/design.md
base-ref: 8c04f5100d2b98666947aa20030c816507e71eb4
archived-with: 2026-11-23-procuraai-human-decision-identity-binding
---

<!-- comet-task-authority: openspec/changes/procuraai-human-decision-identity-binding/tasks.md -->

# Build Plan — procuraAI 人工终决身份绑定（实施口径唯一真源，2026-11-23）

> 本计划引用已批准的 `design.md` 与 `tasks.md`（Open/Design 已锁定为已接受证据，本轮不修改）。任务完成状态一律以 tasks.md 为准，本计划仅以 `comet-task-ref` 映射任务 ID，不复制 checkbox。

## 1. 执行配置

`build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard` / `subagent_dispatch=null` / `direct_override=false`（用户 2026-11-23 明确选择，`propose-build --confirmed` 已批准）。TDD 保留真实 RED/GREEN；standard 对风险任务与最终集成做唯一独立审查。

## 2. 基线事实（2026-11-23 源码实证）

- 分支 `dev`，HEAD `8c04f5100d2b98666947aa20030c816507e71eb4`；工作区仅有三个未跟踪 change 目录，不触碰 `synapclaim-*` 两个。
- `QualificationController:86-92` onboarding approve 仍从请求体读 `tenantId/operator`；同文件 reviewOcr `:81-82` 已用 `ReviewIdentityPort`，控制器已注入该端口（`:27,:32,:35`）。
- `SourcingController` 无 award 端点；`SourcingApplicationService.awardByHuman(tenantId, projectNo, operator)`（`:59-66`）仅应用服务方法。`Rfx` 持有 `projectId`；`SourcingProjectRepository` 端口只有 `save/saveRfx/findByProjectNo/findRfxByNo`，无按 rfxNo 定位项目方法。
- OpenAPI `detailed-design/contracts/openapi.yaml:103-112` 已登记 `POST /procurement/rfx/{rfxNo}/award`，文案仍为“缺 confirmed_by → 20xxx”。
- 安全身份解析基线：qualification 的 `SecurityContextReviewIdentityPort` 从 `JwtPrincipal` + 可信 `TenantContext` 解析，匿名/缺租户返回 null。

## 3. 实施纪律

- TDD：先写会红的负向/正向测试，再实现转绿；每个 fail-closed 分支（冒名/匿名/跨租户/空白操作人/AI 直授）都要有独立断言，不伪造 RED。
- 分层：Controller（adapter/web）只解析登录态并做一致性校验，不信任请求体权威身份；应用服务保留领域不变量与终态保护；身份解析放 domain 端口 + infrastructure 实现；仓储查询经 domain/gateway 端口，application 不直调 SDK。
- 租户锚定：`rfxNo→Rfx.projectId→SourcingProject` 全程 tenant 参与定位，不存在或不对应返回 400 且状态不变。
- 权限：复用 `procurement:rfx:approve` / `procurement:onboarding:approve`，不新增权限码、DDL、迁移脚本、CI 判据，不接真实模型，不改 evalset 状态。
- 写操作以持久化读回作第二证据；负向断言必须验证状态不变、无审计写入。
- 改 Java 后 `mvn spotless:apply`；关键产物完成即精确 `git add`（共享树并行会话保护）。

## 4. 任务映射

### 组 1 · 登录态身份端口与准入终决

- 1.1 在 sourcing 域新增只读 `AwardIdentityPort`（record: tenantId/operator）并在 infrastructure 实现认证上下文解析；匿名/example 缺租户返回 null。先写匿名/已认证两组 Web 层或端口单测见红，再实现转绿。 <!-- comet-task-ref:864f4bcf-8752-4406-bf4b-1a03bf026b39 -->
- 1.2 将 `POST /onboardings/{no}/approve` 改为从 `ReviewIdentityPort` 获取租户与操作人，请求体同名字段只做一致性校验；跨租户、冒名、匿名三类 Web 层测试验证拒绝且不落库。 <!-- comet-task-ref:d9c5d39e-c6a5-49ac-aece-12aaa635cdb1 -->

### 组 2 · RFx 人工授标 HTTP 入口

- 2.1 在 `SourcingProjectRepository` 端口新增只读 `findProjectByRfxNo(tenantId, rfxNo)`（Adapter：findRfxByNo→projectId→tenant 锚定定位项目），并让 `awardByHuman` 支持可信租户/操作人参数；存在、不存在、空白操作人三类服务单测验证状态与不变量。 <!-- comet-task-ref:fd36e285-917e-4c5a-9a6a-fd80dab94784 -->
- 2.2 新增 `POST /api/v1/procurement/rfx/{rfxNo}/award`，复用 `procurement:rfx:approve`，确认人只取登录态；具名成功、无权限/匿名、请求体冒名、AI 直接授标四类 Web 层测试验证 200/403/400 与持久化状态。 <!-- comet-task-ref:049c3659-4076-452f-b24b-131a3b5057b5 -->
- 2.3 同步 `openapi.yaml` 授标端点文案为“确认人来自登录态”，删除权威 `confirmed_by` 语义；用现有契约/对齐门禁验证零漂移。 <!-- comet-task-ref:c767ca7e-f854-438c-8434-5aadc10424b6 -->

### 组 3 · 集成验收与独立审查

- 3.1 运行 qualification 与 sourcing 模块全量测试，验证无失败、无跳过回归，确认写操作有持久化读回或拒绝后状态不变断言。 <!-- comet-task-ref:49853118-45ea-45c2-b8ac-c5b53f0a1e10 -->
- 3.2 运行采购前端 TypeScript、vitest、ESLint JSON 门禁，验证本 change 未破坏前端构建与行为测试。 <!-- comet-task-ref:b9bf88be-83fc-45cc-885f-69ccbbde99c1 -->
- 3.3 运行 procuraAI 对齐、evalset、AsyncAPI、persistence 门禁，验证 OpenAPI 文案同步未触发 A8/A10 契约漂移。 <!-- comet-task-ref:b5b76d5b-7e13-4112-895a-41f010ea1a74 -->
- standard 独立审查：对两个身份绑定 + 授标端点的最终 diff 做唯一最终集成审查，解决 CRITICAL/IMPORTANT 后再进入 Verify。

## 5. 回滚

无 DDL、无数据迁移、无新增权限码；回滚即撤销控制器身份校验、授标端点、仓储查询与 OpenAPI 文案同步，既有领域终态不变量与 fail-closed 行为不变。不回滚到请求体自报身份语义。
