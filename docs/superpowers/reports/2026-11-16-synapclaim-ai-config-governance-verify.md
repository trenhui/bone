# 运行期证据报告：synapclaim-ai-config-governance-api（批 2）

- 日期：2026-11-16
- 阶段：Build 收尾（任务 1.1–4.3）
- 绑定分支：`comet/synapclaim-ai-config-governance-api`｜工作流：classic｜产物语言：zh-CN
- 构建配置：沿用批 1（`executing-plans` / `tdd` / `thorough` / `subagent_dispatch=null`）
- 环境：本机 MySQL 8（`bone` 库）、JDK 21、Maven 3.8.6；网关 8888 / claim 8087 / iam 8081 在线；前端 `node_modules` 符号链接复用批 1 worktree

## 0. 范围与诚实说明

本批 10 条任务中 4 条新建（1.1–2.1）、3 条验收留证（2.2 / 3.1 / 3.2）、3 条验收门禁（4.1–4.3）。
2.2 / 3.1 / 3.2 是**已入库能力**，按 plan §6 只跑探针与留证，**不重写**。任何未在本次真实执行的检查，下文均显式标注证据来源，不冒充真机结果。

---

## 1. 实现清单与测试证据

| 任务 | 实现落点 | 测试证据 |
| --- | --- | --- |
| 1.1 受控写端点 | `ClaimWorkbenchController.configGovernance/updateTriageLimits`，`claim:claims:read` / `claim:review:write`，租户经 `TenantPort` 端口获取 | `AiConfigGovernanceControllerTest` 3/3（反射钉路径、权限、白名单前缀） |
| 1.2 只收紧棘轮 | `AiConfigGovernanceService.ratchet()` 逐字段相对当前生效值比较；金额更低更严、置信度更高更严 | `AiConfigGovernanceServiceTest` 8/8（含「当前 8000 请求 9000 虽低于代码基线仍拒绝」） |
| 1.3 写开关与回落 | `AI_CONFIG_WRITE` 复用 `t_feature_toggle`，未配置 fail-closed；`get` 端点回显 `writeEnabled` | 单测覆盖关闭/未配置两态；4.1-P2 真机验证 |
| 2.1 治理页可写 | `AiGovernancePage` 按 `writeEnabled` 渲染只读/可写，显式标注「仅收紧」；方向判据抽 `CONFIG_LIMIT_DIRECTION` / `isLoosened` 单一真源 | typecheck 0 / lint 0 / vitest 8 files 137 tests（新增 6 用例，含互补变异） |
| 4.x | 见 §2–§4 | 见下 |

错误码四处齐：`ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端双语包 + `doc/architecture/Bone-错误码登记.md` §6。

---

## 2. 4.1 三条负向判据（真机运行期证据）

**环境构造**：`t_feature_toggle` 初始 tenant 0 为 **0 行**（写开关未配置 ⇒ fail-closed）。
验证脚本临时插入一行 `AI_CONFIG_WRITE=ENABLED` 构造可写态，P2 后改为 `DISABLED`，结束时 `finally` **精确删除该行**；实测终态仍为 0 行，与初始一致（零残留、不碰任何既有数据）。

| 判据 | 请求 | 实测 | 结论 |
| --- | --- | --- | --- |
| P0 快照 | `GET /api/v1/claims/ai/config/governance` | HTTP 200；`writeEnabled=true`；limits=`autoAmountLimit=10000`、`aiManualThreshold=0.7`、`aiReviewThreshold=0.85`、`ocrManualThreshold=0.7`、`dualReviewAmount=10000` | PASS |
| P1 放宽被拒 | `PUT /ai/config/triage-limits` `autoAmountLimit: 10000 → 10001` | HTTP 400 + `SC_CLAIM_CONFIG_RATCHET_VIOLATION`；库内 `TRIAGE_LIMITS.config_json` 前后均为空（未改写） | PASS |
| P2 关写被拒 | 临时开关改 `DISABLED` 后 `PUT autoAmountLimit: 9999` | HTTP 403 + `SC_CLAIM_CONFIG_WRITE_DISABLED` | PASS |
| P3 盲标拒绝 | `POST /claims/{claimId}/ai/feedback` `scene=blind_label`、`involvement=AI_ASSISTED`、`disposition=ADOPT` | HTTP 400 + `SC_CLAIM_AI_FEEDBACK_INVALID`；detail 原文「scene 非法：blind_label 是盲标金标准禁区，禁止产生 AI 裁决回流」 | PASS |

> P1 的关键不是「低于代码基线就放行」：当前值已是代码基线 10000，请求 10001 相对**当前生效值**是放宽，被正确拒绝。若复用 `TriageLimits.apply()` 的基线校验，该请求会被静默收敛成 10000 并放行——这正是本批要堵的「先收紧再放宽」后门。

---

## 3. 4.2 全量门禁

| 项 | 实测 |
| --- | --- |
| 仓库原生 `check.sh`（pre-commit 内运行） | 59/59 步通过，`exit 0` + 末行 `✅ 本地自检通过！` |
| E2E 影响分析（`impact.py --strict`） | 变更触及 9 个端点，已覆盖 9 个；两个新端点由 `synap-claim-ai-config-governance-e2e.py` 覆盖 |
| E2E lint / selftest | lint 0；selftest 45/45 |
| 前端 typecheck / lint / vitest | 0 / 0 / 8 files 137 tests passed |
| 后端 TDD 测试 | `AiConfigGovernanceServiceTest` 8/8、`AiConfigGovernanceControllerTest` 3/3、`ArchitectureTest` 28/28 |

**本轮实测发现并修复的真实红点**（均非基线项，由本批引入）：

1. ArchUnit `tenant_context_via_provider` / `tenant_context_aslong_read_only_by_tenant_port_adapter`：Controller 直读 `TenantContext.getTenantIdAsLong()`。改为注入 `application.port.out.TenantPort`，由 `TenantPortAdapter` 唯一读取。
2. ArchUnit `save_must_pair_with_publish_or_exempt`：`AiConfigGovernanceService` 有 `Repository.save()` 无事件。按 E-5.4「技术中间态」声明 `@NoDomainEvent` 并在 JavaDoc 说明理由（只写配置快照，生效口径在读取期兜底收敛，审计走 remark + 结构化日志）。
3. 提交被 2 分钟超时 SIGTERM 留下 stale `index.lock`，确认无活进程后清理；门禁本身耗时属正常。

> 一次门禁日志出现 `iam_menu 活库 300 ≠ 种子 288` 的 seed 红点；直接在 worktree 跑 `python3 scripts/seed-bootstrap.py --check` 返回 0（种子与活库一致），判定为 `commit --only` 临时索引下的读态误判，非本批代码问题，未改种子。

---

## 4. 4.3 变异验证（判据判别力）

对「放宽阈值被拒」判据做双向变异，验证其不是恒真断言：

| 变异 | 操作 | 期望 | 实测 |
| --- | --- | --- | --- |
| M1 后端金额方向反转 | `rejectIfLoosenedAmount` 中 `compareTo(current) > 0` → `< 0` | 放宽用例转红 | rc=1，判红 ✅ |
| M1' 恢复 | 还原源码 | 转绿 | rc=0，判绿 ✅ |
| M2 前端方向真源反转 | `CONFIG_LIMIT_DIRECTION.autoAmountLimit` `lower` → `higher` | 方向钉子转红 | rc=1，判红 ✅ |
| M2' 恢复 | 还原源码 | 转绿 | rc=0，判绿 ✅ |

变异后以 `git diff` 校验两份源码均**零残留**。互补变异同时证明：金额与置信度方向未被退化成同一种比较（前端 6 用例中的「同一数值变化在两个方向判定相反」钉子）。

---

## 5. 2.2 / 3.1 / 3.2 验收留证（已实现能力，未重写）

| 任务 | 已入库实现 | 本次留证方式 |
| --- | --- | --- |
| 2.2 AI 建议曝光与裁决回流 | 前端 `workbench/telemetry.ts`、`persist.ts` 的 `aiFeedbackApi.record`、`AiSuggestionCard`；后端 `/{claimId}/ai/feedback` + `AiFeedbackRules`（四态、`ReasonType` 四值、`blind_label` 禁区、非 `AI_ASSISTED` 拒裁决） | §2-P3 真机验证盲标禁区生效（`SC_CLAIM_AI_FEEDBACK_INVALID`）；未改任何既有代码 |
| 3.1 租户内 `event_id` 幂等 | `ClaimFeedbackApplicationService.findByEventId` + `CLAIM_AI_FEEDBACK_CONFLICT(409)` + 0124 `uk_tenant_event` + 迁移终态断言 | 既有单测与 0124 迁移终态断言已在 HEAD；本次全量门禁覆盖清单 M1/M2/M3，未重复建表建码 |
| 3.2 `0124` / `AiFeedback*` 合并 | 已在 HEAD（design 中「并行会话在途」措辞过时，以 plan §6 为准） | 全量门禁清单校验全绿；重建 fat-jar 后服务 `Started ClaimApplication in 4.668`，启动期无 `TemplateNotFoundException` |

---

## 6. 结论

- 批 2 四条新建任务（1.1 / 1.2 / 1.3 / 2.1）已实现并通过 TDD、契约钉子、typecheck/lint 与真机联调。
- 三条负向判据（放宽被拒、关写被拒、盲标拒绝）真机全部命中，且库状态零残留；变异验证证明判据有判别力且方向非退化。
- 全量门禁 59/59 绿，本批引入的 3 处 ArchUnit 红点已按架构口径修复。
- 2.2 / 3.1 / 3.2 保持已入库实现，按 plan 要求只留证不重写。
- 残余说明：未执行生产库迁移；`i18n` 覆盖盲区（前端 `constants/errorCodes.ts` 与新错误码的登记关系）按 plan 4.2 明确**单列独立核实，不并入本批**。
