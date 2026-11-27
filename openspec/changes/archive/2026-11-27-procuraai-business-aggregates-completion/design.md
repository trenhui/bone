# Design：procuraAI 业务聚合补齐

## Context

见 `proposal.md`。代码基线已有 `bone-procurement-sourcing`（寻源项目、RFx、具名人工授标入口）与 `bone-procurement-qualification`（证照、准入、登录态复核身份），但投标、报价、授标记录与 CAPA 仍无领域载体。现有模块采用 `adapter → application → domain ← infrastructure` 分层、领域仓储端口与内存适配器、统一 `ApiResponse`，且人工终决身份由 `AwardIdentityPort` / `ReviewIdentityPort` 从登录态取得。本设计只在这些既有边界内补齐聚合，不改变 DDL、CI 判据集合或 owner 冻结状态。

## Goals / Non-Goals

**Goals:**

- 让投标、报价、授标记录、CAPA 的不变量成为可执行的领域规则，而非文档约定。
- 为后续前端运营工作台提供稳定、租户隔离的只读/具名人工端点。
- 保持 fail-closed：匿名、AI 建议、请求体自报身份不得越过人工终决；未知/不可比状态不得被压成可用态。

**Non-Goals:**

- 不新增 Maven 模块、DDL、迁移脚本或生产库操作。
- 不接生产 MQ，不扩 CI 三入口判据集合，不新增权限种子。
- 不做真实模型接入、评测转正、契约冻结或效果达标声明。
- 不实现前端页面（由后续 `procuraai-procurement-ops-console` 覆盖）。

## Decisions

1. **聚合归属按现有模块边界划分**：Bid / Quotation / Award 放入 `bone-procurement-sourcing`，CAPA 放入 `bone-procurement-qualification`。
   - 理由：Bid/Quotation/Award 是 RFx 生命周期的直接延续；CAPA 是供应商质量与准入整改的延续。
   - 备选：新建统一 `bone-procurement-business` 模块。放弃，因为会引入新 Maven 模块、依赖与门禁面，却没有新增行为价值。

2. **持久化沿用领域端口 + 内存适配器**：为四类聚合各定义领域仓储端口，基础设施层提供进程内适配器；不新增 `@Table` 实体或 `t_proc_*` 表。
   - 理由：本轮要验证领域不变量与 HTTP 身份边界；DDL 属 L3，且 owner 契约尚未冻结。
   - 备选：直接落 SDK Entity + DDL。放弃，因为越 L3 红线且会把未冻结字段固化到数据库。
   - 明确限制：进程重启后聚合不持久；这属于本轮已知边界，不写入能误导调用方的“生产可用”声明。

3. **人工终决身份复用既有登录态端口**：授标与 CAPA 关闭都只接受 `AwardIdentityPort` / `ReviewIdentityPort` 返回的租户与操作人；请求体字段仅做一致性校验。
   - 理由：与既有 `SourcingController` / `QualificationController` 的人工终决口径一致，避免冒名与跨租户。
   - 备选：由请求体传入 `operator`。放弃，已被现有负向测试证明为高风险。

4. **报价比价是纯领域投影**：比价视图只输出可比较字段与不可比原因码；AI 评分、推荐排序作为独立建议字段，不进入状态迁移。
   - 理由：保证“AI 仅建议、人工终决”的可验证边界，也避免把模型输出伪装成事实。
   - 备选：把 AI 排序直接映射为授标候选。放弃，越过 owner 红线。

5. **HTTP 端点扩展现有 Controller，复用既有权限码**：只读列表/详情与具名操作端点挂在现有 `/api/v1/procurement` 路径下，复用 `procurement:sourcing-project:write`、`procurement:rfx:approve` 与准入权限码；响应统一 `ApiResponse<T>`，DTO 字段白名单化。
   - 理由：避免新增权限种子与门禁判据；字段白名单防止 PII、理由原文或他方租户事实外泄。

## Risks / Trade-offs

- **进程内持久化不跨重启** → 在 design 与交付说明中显式标注；后续耐久性改造必须先过 owner DDL/契约冻结。
- **内存仓储并发冲突** → 聚合层面用状态迁移表与幂等键约束；同一租户业务键重复写入拒绝，不依赖数据库行锁。
- **两个模块各自持有身份端口** → 保持端口语义一致：缺租户/空白操作人一律拒绝；不合并端口以免扩大本轮改动面。
- **比价视图暴露敏感商务信息** → 只返回比价必需字段与不可比原因码，不返回合同正文、模型输出或证件号。
- **CAPA 关闭证据可能是自由文本** → 以引用标识建模，不落 PII；文本说明在后续 owner 契约冻结时再定字段级 schema。

## Migration Plan

无数据库迁移。代码按模块分批复盘：先领域聚合与测试，再应用服务与仓储适配器，最后 HTTP 端点与契约测试。回滚方式为撤销本 change 的源码改动；不涉及数据回滚或生产库变更。后续若需要持久化，应另起 change，先完成 owner 契约冻结与 DDL 审批。

## Open Questions

（无。本设计范围内的决策已定；owner 冻结、评测转正、生产 MQ 与真实模型接入属于明确非目标，不作为可推迟问题混入本 change。）
