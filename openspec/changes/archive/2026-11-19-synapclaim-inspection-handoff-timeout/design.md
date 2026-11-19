---
comet_change: synapclaim-inspection-handoff-timeout
role: technical-design
canonical_spec: openspec
---

## Context

动机见 `proposal.md`。账户是投影、流水是事实：已支付打回必须先冲正，不能用事后补偿掩盖资金缺口；释放冻结与回冲是不同事实，口径不变。任务归属来自批 1，复审状态来自批 3。MySQL 无 partial unique index，代理区间唯一性用生成列 `active_key` + `UNIQUE(tenant_id, active_key)`。上游设计见归档 `design.md` 与提案 §13.5。

## Goals / Non-Goals

**Goals:**
- 抽检处置可追溯、已支付前置冲正不可绕过
- 请假/转交/代理与超时回池成为可验证的事实，而非文档描述
- 设计文档引用的列必须存在于 DDL（消除 `org_id` 那类漂移）

**Non-Goals:**
- 不改变资金三值口径（`available = initial − claimed − frozen`）、不改额度写事务语义
- 不做任务分配（批 1）、AI 配置（批 2）、独立复审（批 3）
- 不改网关白名单、不改已封存的 v2.3 设计文档

## Decisions

- **D1 沿用已归档 v2.3 设计，不重新做技术选型**：打回冲正前置、代理区间、超时回池阈值口径均已在 v2.3 §13 与三份新设计文档拍板
- **D2 复用既有配置/状态基础设施，不新增外部依赖**：抽检比例复用 `t_feature_toggle`；任务归属复用批 1 的 `t_claim_task`，不新建第二套任务表
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
2. 部署后先保持开关关闭，旧路径零行为变更；再灰度打开 `INSPECTION_ACTION`，按本批验收判据取实测数值。
3. contract：确认无回归后再把读侧切到新能力；历史数据不做编造回填。
4. 回滚：关闭 `INSPECTION_ACTION`，写侧停写、新表保留不读，无需 DROP TABLE，无数据损失。


## 正式技术设计（抽检打回与交接超时 · 已批准方案 A）

范围来自已批准 `proposal.md` / `specs/claim/inspection-handoff-timeout/spec.md` / `tasks.md`；沿用归档 v2.3 §13 与三份新设计文档既有拍板，**无 Spec Patch**。

### 方案 A（推荐）：三张 append-only 事实表 + 冲正前置闸门 + 生成列代理唯一键 + 幂等扫描
- 新增 `t_claim_inspection_action`（处置留痕）、`t_claim_task_transfer`（转交留痕）、`t_claim_user_agent`（代理区间）；不提供历史记录更新/删除入口。
- 代理区间唯一性：MySQL 无 partial unique index ⇒ 生成列 `active_key = IF(区间生效, key, NULL)` + `UNIQUE(tenant_id, active_key)`；区间外不生效，重叠生效代理第二条被拒。生成列不进实体。
- 打回：写入期先查资金状态，已支付且无冲正事实 **MUST 拒绝**（不事后补偿）；冲正后生成重办任务并留痕关联该任务。资金流水保持 append-only，「账户是投影、流水是事实」与三值口径 `available = initial − claimed − frozen` 不变；额度写用 `REQUIRED`，禁 `REQUIRES_NEW`。
- 超时回池扫描：复用既有 `ScheduleLockService`，不引入新调度框架；回池前置 = 任务仍处「超时未领」状态且归属未释放（与释放 hold 的前置同构），配合 `@Version` 乐观锁，重复扫描不产生第二条回池事实；监控计数按小时可观测。归属闸门与池级闸门不得串联。
- 抽检比例从全局 `@Value` 迁入 `t_feature_toggle` 并上屏；`INSPECTION_ACTION` 关闭 → 处置只登记、不生成重办任务、不改案件状态。
- DDL 文档一致性门禁：解析设计文档引用的列与 `information_schema`/DDL 求差；扫描全文用 `finditer` + 计数对账（不用只取首个匹配的 `search()`），判据上线前先对全仓跑正向审计，避免 `package-info.java` 类假红。

### 方案 B（不采用）：打回后异步补偿冲正
存在资金缺口窗口，违反 spec「MUST 先完成冲正，未完成 MUST 拒绝打回」；把静默多赔换成事后补偿，风险放大。

### 方案 C（不采用）：代理用应用层先查后写 + 分布式锁，免生成列
并发双写窗口下两条重叠代理都能通过，无法满足「同一时段至多一条生效代理」；项目亦禁 `SELECT ... FOR UPDATE`。

### 关键取舍
多三张表与一次增量迁移，换取处置/转交/代理可追溯事实与并发正确性；不引入新调度框架，代价是扫描幂等须由前置断言 + 乐观锁自证。

### 风险与处理
① 迁移号撞号 → 追加末尾空位，脚本+清单+`bone-init.sql` 同批，已发迁移含注释不回改；② 实体 `@Column` 与活库 `information_schema` 逐列求差，末尾强断言 + `SIGNAL`（`MESSAGE_TEXT` ≤128 字符）；③ 新增仓储方法同批 SQL 模板，首行语句关键字、SELECT 列对齐、含租户锚点；④ 新错误码四处齐；⑤ 门禁误报比漏报更致命 → 正向审计 + 互补变异；⑥ 前端 ID 一律 `string`；⑦ 不改资金三值口径与网关白名单。

### 测试策略
单元：区间外不生效、重叠代理被拒、扫描幂等、冲正闸门。集成：`information_schema` 终态断言、启动期无 `TemplateNotFoundException`、历史转交更新被拒、资金流水含冲正事实、页面值=配置读回值。变异：「已支付未冲正被拒」「重复扫描幂等」两处判据必须转红。运行期证据写入 `docs/superpowers/reports/`。

### 开工约束
用户裁定等并行会话在途改动入库 + 全量门禁转绿后才进 Build；本阶段只出设计文档，不写实现代码。

## 批准记录与停止条件

- 提案哈希：`67c84ab6564473079a8bec0fb16cf26f9caff14dc9ee72ad1f9bdc7b4a191630`（`comet state propose-design` 生成，handoff `design-context.json` 引用的 5 份源文件 sha256 逐字节一致）
- 用户通过结构化提问明确选择「批准方案 A（推荐）」，非 Agent 推断；`decide-design --choice approved` 已落库。
- Spec Patch：无。未新增能力、未改公开行为与验收；范围严格来自本 change 已批准的 proposal / delta spec / tasks 与归档 v2.3 §13.5。
- 方案 B / C 的否决理由见上文；正文与已批准提案一致。
- **Build 前停止条件**：用户裁定等并行会话在途改动入库且全量门禁转绿后才开工。本次 `scripts/check.sh` 已实测 `CHECK_RC=0`（末行「✅ 本地自检通过！」），但 `dev` HEAD 仍 `6f0ea427f`、主树 156 个在途文件、`ClaimWorkbenchController` / `ClaimSearch*` / `AiFeedback*` 未跟踪、`migration-manifest.json` 的 `0124` 登记仅 staged ⇒ 解除条件**未满足**。本阶段只出设计文档，不写实现代码、不勾选 tasks、不进 Build。
