---
comet_change: synapclaim-ai-config-governance-api
role: technical-design
canonical_spec: openspec
---

## Context

动机见 `proposal.md`。配置真源为 `t_feature_toggle`；网关只转发 `/api/v1/claims/**`，`/api/ai/v1/**` 不通，通用 HITL 也没有「曝光」事实，因此回流落在理赔域自建。批 1 的任务归属用于 AI 建议归因。已确认上游是归档设计 `design.md` 与提案 §13.5。本批依赖批 1，且并行会话正在实现回流表与 DTO，须等其入库后基于成果续做。

## Goals / Non-Goals

**Goals:**
- 配置写入有权限、有审计、只收紧；关闭开关即可回落只读
- 采纳率有真实分母，裁决事实可追溯但不含敏感字段
- 与并行会话在途的 `0124` 回流资产合并，不重复建表

**Non-Goals:**
- 不引入 `AUTO_REJECT`、不自动改案件状态、不写资金
- 不改网关白名单、不新开 `/api/ai/v1/**`、不引入新规则引擎
- 不做独立复审（批 3）、抽检代理（批 4）、任务分配（批 1）

## Decisions

- **D1 沿用已归档 v2.3 设计，不重新做技术选型**：AI 三层配置语义、只收紧棘轮与 ReasonType 四值均已在 v2.3 §13 拍板，本批只落地不重开选型
- **D2 复用既有配置/状态基础设施，不新增外部依赖**：配置复用 `t_feature_toggle`，回流复用并行会话在途的 `t_claim_ai_feedback_event`，不新建同义表
- **D3 全部新增端点挂在网关已白名单的 `/api/v1/claims/**` 下**：网关前缀白名单未登记会让整个路径 404 且无后端日志；不新开 `/api/ai/v1/**`。
- **D4 写入期拒绝 + 读取期兜底保留**：非法值与哨兵不得靠「读侧忽略」掩盖；收紧必配正向钉子与变异验证。
- **D5 新增配置表/字段先找消费方**：grep 不到消费方即「配了不生效」，须 fail-closed 或标注预配置，UI 不得暗示已生效。
- **D6 迁移号按基线末尾空位分配**：当前 `dev` 基线 `6f0ea427f` 清单止于 `order=123`，`0124` 文件已在基线但登记由并行会话 staged 未入库；本批须等其入库后追加末尾空位，禁止插入中间或回改已发迁移（sha256 journal）。
- **D7 等并行会话入库后再进 Build**：并行会话正新建 `ClaimWorkbenchController` / `ClaimSearch*` / `AiFeedback*` 并改 `ClaimErrorCodes` / `ClaimErrors` / `ClaimCaseRepository`；用户已裁定等入库后开工，避免重复造轮子与同文件冲突。
- **D8 新增仓储方法同批交 SQL 模板**：模板首行必须是语句关键字、SELECT 列对齐实体、含 `/*bone:tenant*/` 锚点，否则启动期 `TemplateNotFoundException` 或静默读空。

## Risks / Trade-offs

- [并行会话占用同一批核心文件与迁移清单] → 用户裁定等其入库；开工前以 `git status --short -- <精确路径>` 复核，绝不 `git add` 全量。
- [新增错误码三处不齐导致类加载即抛] → `ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端两语言包 + 台账 §6 同批交付。
- [配置改了没有消费方] → 按 D5 先 grep 消费方，缺则 fail-closed，不在 UI 暗示已生效。
- [已支付场景被打回造成资金缺口] → 打回前置必须先冲正，写入期拒绝而非事后补偿。
- [新增 worktree 位于仓库内被全仓扫描误扫] → Runtime 硬约束，用户选择保持现状；红点单独归因，不宣称全仓 `check.sh` exit 0。

## Migration Plan

1. expand：按 D6 追加迁移与清单登记、`bone-init.sql` 同批补齐，末尾 `information_schema` 强断言 + `SIGNAL`（`MESSAGE_TEXT` ≤128 字符）。
2. 部署后先保持开关关闭，旧路径零行为变更；再灰度打开 写 API 开关，按本批验收判据取实测数值。
3. contract：确认无回归后再把读侧切到新能力；历史数据不做编造回填。
4. 回滚：关闭 写 API 开关，写侧停写、新表保留不读，无需 DROP TABLE，无数据损失。


## 正式技术设计（AI 配置治理 API · 已批准方案 A）

范围来自已批准 `proposal.md` / `specs/claim/ai-config-governance/spec.md` / `tasks.md`；沿用归档 v2.3 §13 的既有拍板，不重开选型，**无 Spec Patch**。

### 方案 A（推荐）：服务端棘轮 + 开关双闸门 + 幂等事实复用
- 写端点挂 `/api/v1/claims/**`（不新开 `/api/ai/v1/**`、不改网关白名单）；`@PreAuthorize` + SDK `@TenantScope` + 模板内 `/*bone:tenant*/` 锚点；`@Version` 乐观锁防并发写覆盖，禁 `SELECT ... FOR UPDATE`。
- 只收紧棘轮放在领域层纯函数：逐字段比较「当前生效值 vs 请求值」，只接受相等或更严格；写入期拒绝放宽，不靠读侧忽略掩盖。审计 append-only 记录旧值/新值/拒绝原因。
- 写 API 开关（复用 `t_feature_toggle`）双闸门：服务端拒写 + 治理页读开关态后渲染只读，消除「页面可编辑但提交失败」。
- 曝光/裁决回流复用并行会话在途的 `0124_synap_claim_ai_feedback_event.sql` 与 `AiFeedback*`，**不重复建表**；租户内 `event_id` 唯一键，同键同载荷返回首次结果，同键异载荷按载荷摘要比对返回 409 且不覆盖首次事实；事件只带 `traceId`、AI 参与四态、`ReasonType` 四值，不含姓名/证件/金额。
- 端点不改案件状态、不写资金。

### 方案 B（不采用）：前端/网关做棘轮，后端只做 CRUD
直连接口即可绕过门槛放宽；服务端无审计与幂等事实，采纳率分母不可信。与 spec「系统 SHALL 逐字段比较当前生效值」冲突。

### 方案 C（不采用）：新建 `t_ai_config_version` 版本表 + 事件溯源求当前值
与 `t_feature_toggle` 形成双真源，触发项目铁律「配了不生效」；且与并行会话 `0124` 回流资产重复造轮子，扩大迁移面。

### 关键取舍
不新增业务表（降低 DDL/清单/实体求差成本），代价是配置历史只保留 append-only 审计记录，不做完整版本快照回放。

### 风险与处理
① 并行会话占用 `ClaimErrorCodes`/`ClaimErrors`/`AiFeedback*` → 等其入库，精确路径复核，绝不 `git add` 全量；② 迁移号撞号 → 追加末尾空位、脚本+清单+`bone-init.sql` 同批，已发迁移含注释不回改；③ 新错误码四处齐（错误码类 + HTTP 状态 + 前端双语 + 台账 §6）；④ 新增仓储方法同批 SQL 模板，首行语句关键字、SELECT 列对齐实体、含租户锚点；⑤ 前端雪花 ID 一律 `string`；⑥ 开关/配置须先找消费方，缺则 fail-closed；⑦ 变异验证「放宽阈值被拒」必须转红。

### 测试策略
单元：棘轮逐字段/边界/相等、开关双态、幂等载荷摘要比对。集成：无权限与跨租户被拒、409 不覆盖首次事实、启动期无 `TemplateNotFoundException`、读回新值。负向：放宽阈值、关闭写 API、盲标/非 `AI_ASSISTED` 裁决。变异：放宽判据转红。运行期证据写入 `docs/superpowers/reports/` 并带实测数值。

### 开工约束
用户裁定等并行会话在途改动入库 + 全量门禁转绿后才进 Build；本阶段只出设计文档，不写实现代码。

## 批准记录与停止条件

- 提案哈希：`2bb35a443b1350993afa81e3143cb8418bff414a6032500d18f1729d0b37feaa`（`comet state propose-design` 生成，handoff `design-context.json` 引用的 5 份源文件 sha256 逐字节一致）
- 用户通过结构化提问明确选择「批准方案 A（推荐）」，非 Agent 推断；`decide-design --choice approved` 已落库。
- Spec Patch：无。未新增能力、未改公开行为与验收；范围严格来自本 change 已批准的 proposal / delta spec / tasks 与归档 v2.3 §13.5。
- 方案 B / C 的否决理由见上文；正文与已批准提案一致。
- **Build 前停止条件**：用户裁定等并行会话在途改动入库且全量门禁转绿后才开工。本次 `scripts/check.sh` 已实测 `CHECK_RC=0`（末行「✅ 本地自检通过！」），但 `dev` HEAD 仍 `6f0ea427f`、主树 156 个在途文件、`ClaimWorkbenchController` / `ClaimSearch*` / `AiFeedback*` 未跟踪、`migration-manifest.json` 的 `0124` 登记仅 staged ⇒ 解除条件**未满足**。本阶段只出设计文档，不写实现代码、不勾选 tasks、不进 Build。
