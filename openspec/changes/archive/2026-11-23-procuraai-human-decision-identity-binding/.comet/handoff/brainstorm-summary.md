# Brainstorm Summary — procuraai-human-decision-identity-binding

- Change: procuraai-human-decision-identity-binding
- Date: 2026-11-23
- Phase: design
- 状态：候选方案（用户已确认范围、三 change 拆分与当前 dev 工作区；以下技术判断随 Design Wait 一并确认）

## 一、已由用户确认的裁决（不再作为开放问题）

1. **范围**：procuraAI 未实现能力按三 change 落地，本 change 聚焦「人工终决身份绑定」；最大范围含草案，但本 change 不触碰 owner 冻结项。
2. **授权边界**：不改 DDL/CI 三入口；不接真实模型；不改评测集状态；不执行生产数据库操作；不伪造 owner 签字。
3. **工作区**：沿用当前 `dev` 目录，不建分支/Worktree；其他未跟踪 change 保持不动。
4. **Open 产物**：change 名称、proposal、2 条 Requirement/6 场景的 delta spec、5 项设计决策与 8 项任务已获用户确认（Open approval hash `09c90eb3494c7c12c9bfd3fcff2c17dae322ff6676c54336edba4116060855b0`）。

## 二、候选技术方案（源码实证）

### T1 — onboarding approve 复用 `ReviewIdentityPort`
源码实证：`QualificationController:86-92` 的 `approve()` 仍用 `request.tenantId()/request.operator()`，而同文件 `reviewOcr` 已用 `reviewIdentity.current()`（`QualificationController:81-82`）。控制器已注入 `ReviewIdentityPort`（`:27,:32,:35`），无需改构造器。改为解析登录态身份，请求体同名字段仅做一致性校验，冲突/匿名/缺租户一律拒绝且不落库。备选「继续信任请求体」会使具名终决可冒名，直接违反 spec，不采用。

### T2 — sourcing 新增本域 `AwardIdentityPort`
源码实证：`SourcingController` 无 award 端点，`SourcingApplicationService.awardByHuman(tenantId, projectNo, operator)`（`:59-66`）只有应用服务方法。在 sourcing 域定义只读身份端口，基础设施实现从 `SecurityContextHolder` + `JwtPrincipal` 解析（复用 `SecurityContextReviewIdentityPort` 的解析语义，但放在 sourcing 避免跨域编译依赖）。备选「直接复用 qualification 的端口」会泄漏准入域概念到寻源域，不采用。

### T3 — 授标以 `rfxNo` 为路径键，经 RFx 定位项目
源码实证：OpenAPI `detailed-design/contracts/openapi.yaml:103-112` 已登记 `POST /procurement/rfx/{rfxNo}/award`；`Rfx` 持有 `projectId`。仓储白名单（`SourcingProjectRepository`）当前只有 `save/saveRfx/findByProjectNo/findRfxByNo`，无按 id 定位项目方法。方案：新增只读 `findProjectByRfxNo(tenantId, rfxNo)` 到端口，Adapter 用 `findRfxByNo` 取 `projectId` 再按 id 定位项目（tenant 锚定）；不存在/不对应返回 400，状态不变。备选「用 projectNo 路径」与公开契约冲突触发 A8 漂移，不采用。

### T4 — 请求体不携带权威确认人
新端点路径参数仅 `rfxNo`，请求体不设 `confirmed_by`。OpenAPI 文案同步为「确认人来自登录态，冒名/匿名被拒」，状态码集合不变：权限不足 403（框架）、身份/参数不一致 400、状态冲突 409。备选「保留字段并与登录态比对」会误导调用方视其为权威输入，不采用。

### T5 — 不增加数据库结构
复用 `SourcingProjectRepository`/`QualificationRepository` 现有 save 与状态迁移，不新增表/列/迁移脚本；写操作以持久化读回作第二证据。

## 三、验证策略与失败路径

- 正向：具名登录态可对有效 RFx 授标（AWARDED + 记录登录态操作人）、可准入终决。
- 负向：请求体冒名、匿名、跨租户、空白操作人、非 RFx 对应项目、AI 直接授标均被拒且不落库。
- 风险：现有测试夹具可能用匿名/请求体 operator → 同步改为带登录态夹具，保留负向场景。
- 风险：`rfxNo→project` 映射缺失 → 仓储只读定位单测 + 不存在编号 400 场景覆盖。
- 风险：OpenAPI 口径漂移 → 同 change 内同步文案，不动判据集合。
- 门禁：qualification/sourcing 模块全量测试、前端 tsc/vitest/eslint(json)、procuraAI 对齐/evalset/AsyncAPI/persistence 门禁。

## 四、Spec Patch

无。规格在 Open 阶段已确认，候选方案不改变外部行为或验收，不需要修改 delta spec；若后续要改，先说明具体补丁并取得用户同意。
