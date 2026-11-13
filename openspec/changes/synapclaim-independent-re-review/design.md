---
comet_change: synapclaim-independent-re-review
role: technical-design
canonical_spec: openspec
---

## Context

动机见 `proposal.md`。`44` 是既有双人复核状态，本批新增 `43/46` 与之并列；任务归属来自批 1 的 `t_claim_task`。仓库铁律：新增错误码必须三处齐（错误码类 + HTTP 状态映射 + 两语言包），否则类加载即抛；权限码须在网关与字典同时登记。上游设计见归档 `design.md` 与提案 §13.5。

## Goals / Non-Goals

**Goals:**
- 独立复审有专属状态、权限、队列，且同人不可自审是可验证的硬约束
- 开关关闭后 43/46 零流量，不影响 44 与初审
- 新增错误码与权限码全链路可解析

**Non-Goals:**
- 不合并 44 双人复核、不放宽职责分离
- 不做抽检打回（批 4）、AI 配置（批 2）、任务分配（批 1）
- 不改网关白名单、不改既有审核状态机

## Decisions

- **D1 沿用已归档 v2.3 设计，不重新做技术选型**：43/46 与 44 并列、职责分离、权限码命名均已在 v2.3 拍板，本批只落地
- **D2 复用既有配置/状态基础设施，不新增外部依赖**：复审归属复用批 1 的 `t_claim_task`，不新建第二套任务表；状态走既有字典
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
2. 部署后先保持开关关闭，旧路径零行为变更；再灰度打开 `RE_REVIEW`，按本批验收判据取实测数值。
3. contract：确认无回归后再把读侧切到新能力；历史数据不做编造回填。
4. 回滚：关闭 `RE_REVIEW`，写侧停写、新表保留不读，无需 DROP TABLE，无数据损失。


## 正式技术设计（独立复审 · 已批准方案 A）

范围来自已批准 `proposal.md` / `specs/claim/independent-re-review/spec.md` / `tasks.md`；沿用归档 v2.3 §13 既有拍板，**无 Spec Patch**。

### 方案 A（推荐）：状态字典并列 + 服务端职责分离硬断言 + 归属过滤队列 + 开关 fail-closed
- `43`（待独立复审）/`46`（终态）与既有 `44` 双人复核**并列**登记；状态机只加新边、不改旧边；字典对账用例钉死三者共存与互不覆盖。
- 职责分离放在写侧领域服务断言（初审人 ≠ 独立复审人），命中即拒并返回可辨认错误码；不依赖 UI 隐藏，`RE_REVIEW` 关闭时也**不放宽**。
- 复审队列：`claim:re-review:write` 权限 + 批 1 `t_claim_task` 归属过滤 + 租户隔离 + `status=43`；SQL 模板含 `/*bone:tenant*/` 锚点，SELECT 列对齐实体。
- 权限码在字典与网关前缀白名单同时登记，端点复用 `/api/v1/claims/**`。
- 新增错误码四处齐：`ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` + 前端中英语言包 + 台账 §6，缺任一处门禁判红。
- `RE_REVIEW` 关闭 → 无 43/46 新流转，`44` 与初审行为不变。

### 方案 B（不采用）：复用 44 加「是否独立」标记位
直接违反 spec「MUST 与 44 并列、不得合并」，三状态无法并列查询；字典对账变异用例会判红，且职责分离会随标记位被绕过。

### 方案 C（不采用）：前端队列按人过滤 + 后端只做权限校验
跨租户/越权案件可被枚举，职责分离可被直连接口绕过；不满足「队列按持权人与任务归属过滤 + 租户隔离」。

### 关键取舍
不新建任务表（复用批 1），代价是队列强依赖批 1 归属字段已落地，两批必须按序开工。

### 风险与处理
① 状态字典与前端字典漂移 → 字典对账用例 + 变异验证「合并写法」判红；② 错误码四处不齐导致类加载即抛 → 同批交付 + `_check_error_code_i18n.py` E1–E5；③ 权限码未登记网关导致整体 404 无日志 → 白名单与字典同批；④ 并行会话占用 `ClaimErrorCodes`/`ClaimErrors` → 等入库；⑤ 新增仓储方法同批 SQL 模板；⑥ 前端 ID 一律 `string`。

### 测试策略
单元：三状态并列、职责分离（含开关关闭态）、错误码 HTTP 映射。集成：无权限写入被拒、跨租户不可见、队列只出 43、关闭开关零流量且 44 可用、启动期不抛。变异：合并写法 / 同人自审两处判据必须转红。运行期证据写入 `docs/superpowers/reports/`。

### 开工约束
用户裁定等并行会话在途改动入库 + 全量门禁转绿后才进 Build；本阶段只出设计文档，不写实现代码。

## 批准记录与停止条件

- 提案哈希：`cf6d68c8e0995be81278fb86e06aea8ac7610ad94dfd7e5c5b3b25ea6ccea3ab`（`comet state propose-design` 生成，handoff `design-context.json` 引用的 5 份源文件 sha256 逐字节一致）
- 用户通过结构化提问明确选择「批准方案 A（推荐）」，非 Agent 推断；`decide-design --choice approved` 已落库。
- Spec Patch：无。未新增能力、未改公开行为与验收；范围严格来自本 change 已批准的 proposal / delta spec / tasks 与归档 v2.3 §13.5。
- 方案 B / C 的否决理由见上文；正文与已批准提案一致。
- **Build 前停止条件**：用户裁定等并行会话在途改动入库且全量门禁转绿后才开工。本次 `scripts/check.sh` 已实测 `CHECK_RC=0`（末行「✅ 本地自检通过！」），但 `dev` HEAD 仍 `6f0ea427f`、主树 156 个在途文件、`ClaimWorkbenchController` / `ClaimSearch*` / `AiFeedback*` 未跟踪、`migration-manifest.json` 的 `0124` 登记仅 staged ⇒ 解除条件**未满足**。本阶段只出设计文档，不写实现代码、不勾选 tasks、不进 Build。
