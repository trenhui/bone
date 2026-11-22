---
comet_change: claim-report-case-one-to-many
role: technical-design
canonical_spec: openspec
---

# 设计：报案与赔案拆分（1:0..N）

## Context

现行模型把「报案（FNOL）」与「赔案（claim）」压成一个实体：`t_claim_case.claim_no` 一列既是报案号又是赔案号，基数恒为 1:1。契约 §9.3.1 已把它定性为**范围取舍**而非领域事实，并明确「若业务需要一次报案多赔案，须补报案实体与子案编号规则」。本设计兑现该升级。

约束条件（决定方案形状）：

- 系统未上线，无存量业务数据（迁移只需处理测试库数据），不受「已发号不可回改」约束；
- `claim_no` 已被资金幂等键（`{claimNo}:{stage}:{attempt}`）、OSS 路径、对账通道、E2E 探针引用，**不能改格式或语义**；
- 发票唯一键 `uk_tenant_invoice` 是防重复赔付的硬约束，不得放松；
- 平台规范强制 `/api/v1/{domain}/...`，不得为本次引入新前缀。

## Goals / Non-Goals

**Goals**

1. 报案成为独立聚合，`report : case = 1:0..N`；
2. 两套编号（`claim_no` / `report_no`）各自全局唯一、共用同一编解码内核；
3. 既有 `POST /api/v1/claims` 契约不破坏（仅新增字段）；
4. 新增「追加赔案」与「报案详情」两个用例；
5. 存量数据一键回填，迁移幂等且终态可断言。

**Non-Goals（本次不做，登记为已知缺口）**

- 报案与赔案的**跨保单**合并（一次事故多保单）——事故与保单仍一对一；
- 报案注销/撤销的审批流转（`CLOSED` 只有状态位，无审批单）；
- 复制赔案（`is_copy` / `parent_claim_id`）从设计态落地——本次先用「同一报案下多赔案」表达，复制语义待定；
- 报案级别的材料池与理算汇总（材料仍挂赔案）；
- `batch_no` 语义收缩——保留为 OSS 路径前缀，不再暗示「同批次即同报案」。

## 决策

### D1 报案是聚合根，赔案通过 `report_id` 单向引用

```
ClaimReport (t_claim_report) 1 ──── 0..N ClaimCase (t_claim_case.report_id)
```

- 报案**不持有**赔案集合（不做 `@Cascade` 子集合）：赔案有自己的状态机与生命周期，把它做成子实体等于让报案保存时级联改赔案状态，与「赔案状态解释权归赔案聚合」冲突。
- 方向固定为「赔案 → 报案」：查「某报案下的赔案」走 `ClaimCaseRepository.findByReportId`（Criteria），不引入双向依赖。
- **被否决的方案**：`t_claim_case` 内嵌报案字段（即现状加字段）。它无法表达「报案未立案」，且每加一个报案属性都要在赔案表复制一份，回到「同一语义两处真源」。

### D2 编号共用内核，前缀区分语义

新增 `BizNoCodec`（domain 层，包内可见工具类）承载 Feistel 双射 + Crockford Base32 + Luhn32；`ClaimNoGenerator`（`CL`）与 `ReportNoGenerator`（`RP`）成为薄封装。

- 校验位**只覆盖日期 + 载荷**，不含前缀 ⇒ 同一雪花 id、同一天在两个前缀下的校验位相同。这不是巧合而是刻意：它让存量回填（`CL→RP` 前缀替换）产生的号仍然合法，无需在迁移里实现 Feistel。
- 逆向解析必须带前缀：`extractSnowflakeId(prefix, no)`，前缀不匹配直接拒绝——否则「用赔案号当报案号查」会静默命中，把两个命名空间混成一个。
- 唯一性链条：雪花 id（全局唯一）→ Feistel（双射）→ Base32（双射）⇒ 号全局唯一；DB `uk_claim_no` / `uk_report_no` 为最终兜底。
- 两套号段靠前缀天然互斥，不需要跨表唯一约束（也无法用单表索引表达）。

### D3 幂等键保留在赔案上，报案与首案同事务创建

- 现存幂等语义是「一次提交只落一个赔案」，落在 `t_claim_case.idempotency_key`（`uk_tenant_idem`）。本次**不搬移**该语义：搬去报案会要求同步改 `ClaimCase.draft()` 的 14 处调用方与既有单测，收益仅是形式对称。
- 提交用例在**同一事务**内：查幂等 → 建报案 → 建首案 → 插库。幂等命中直接返回首案（含 `reportNo`），不新建报案 ⇒ 不存在孤儿报案。
- 并发双写：后者触发 `uk_tenant_idem` 冲突 → 整事务回滚 → 报案插入一并回滚。**验证点**：单测需断言「冲突后报案表行数不变」，否则「回滚彻底」只是口号。
- 追加赔案复用同一机制（新赔案自带 `Idempotency-Key`），不引入第二套幂等表。

### D4 报案状态只表达「可否追加」

- `status` 取值 `REPORTED` / `CLOSED`（TINYINT 还是 VARCHAR？——沿用 `t_claim_case.status` 的口径：**存枚举 name 的 VARCHAR**，避免「枚举带 code 落 varchar 列读不出」那类历史坑）。
- 不落「赔付中 / 已结案」：那是赔案的进度，落进报案就形成两份互相矛盾的真源。报案详情接口用赔案清单**派生**「是否全部结案」。
- `CLOSED` 只拦「追加新赔案」，不拦既有赔案的状态机推进——报案关闭是「不再受理新赔付」，不是「把在办案件卡死」。

### D5 追加赔案继承事故上下文，禁止覆盖

`POST /api/v1/claims/reports/{reportNo}/cases`：

- 保单号、计划、被保人、出险时间**一律从报案继承**，请求体不接受这四个字段（DTO 里没有，不是「传了忽略」——没有字段就没有覆盖的可能）。
- 请求若带了 `policyNo` 等字段会被反序列化忽略吗？Jackson 默认忽略未知字段 ⇒ 需要**显式拒绝**：DTO 用 `@JsonIgnoreProperties(ignoreUnknown = false)` 或在 `SubmitClaimReq` 复用路径上校验。**采用**：追加请求 DTO 独立定义（`AppendCaseReq`），并对「请求里出现保单维度键」做一次前置拒绝（400），理由是静默忽略会让调用方误以为覆盖成功。
- 报案 `CLOSED` → 422 `SC_CLAIM_REPORT_CLOSED`；报案不存在 → 404 `SC_CLAIM_REPORT_NOT_FOUND`；租户不符按不存在处理（防枚举）。

### D6 存量回填：一条赔案一条报案，前缀替换得号

迁移 `0139_synap_claim_report_case_split.sql`：

1. `CREATE TABLE IF NOT EXISTS t_claim_report`；
2. `ALTER TABLE t_claim_case ADD COLUMN report_id BIGINT NULL`（幂等：先查 `information_schema.columns`）；
3. 回填 `t_claim_report`：对每条**尚无对应报案**的赔案插入一行，`id = claim.id`（直接复用雪花 id，省一次映射）、`report_no = CONCAT('RP', SUBSTRING(claim_no, 3))`；
   - 复用 `claim.id` 作为 `report.id` 的理由：一赔案一报案的 1:1 回填下两者一一对应，复用 id 让 `report_id = id` 自映射，回填 SQL 无需额外映射表；未来新报案用新雪花 id，不冲突。
   - 幂等：`INSERT ... SELECT ... WHERE NOT EXISTS (SELECT 1 FROM t_claim_report r WHERE r.id = c.id)`。
4. `UPDATE t_claim_case SET report_id = id WHERE report_id IS NULL`；
5. `ALTER TABLE t_claim_case MODIFY report_id BIGINT NOT NULL`；
6. 加索引 `idx_claim_case_report (tenant_id, report_id)`（先查 `statistics` 再建）；
7. **终态断言**（任一不满足 `SIGNAL SQLSTATE '45000'` 中止）：
   - `report_id IS NULL` 计数 = 0；
   - 悬空引用（`report_id` 不在 `t_claim_report`）计数 = 0；
   - 跨租户挂靠（`c.tenant_id <> r.tenant_id`）计数 = 0；
   - `report_no` 重复计数 = 0；
   - `report_no` 非 `RP` 前缀计数 = 0。

`MESSAGE_TEXT` 每条 ≤128 字符（超长报 ERROR 1648）。

### D7 三处 DDL 同批同步

| 位置 | 角色 |
|---|---|
| `scripts/migration/0139_*.sql` | 生效通道（已发迁移，登记 `migration-manifest.json` order 顺延） |
| `sql/bone-init.sql` | 全量新装基线 |
| `bone-apps/synap-claim/synap-claim-app/src/main/resources/db/migration/V*.sql` | 模块内参考态（与 0058 同构） |

漏一处即「配了不生效」：新装库缺列会在启动期才炸，或在 `INSERT` 时因列缺失静默丢字段。

### D8 分层落点

| 层 | 变更 |
|---|---|
| domain/model/claim | 新增 `ClaimReport`（工厂 + `close()` + 意图揭示 `claimable()`）、`ReportStatus`、`ReportNoGenerator`、`BizNoCodec`；`ClaimCase.draft()` 增 `reportId` 参数并入 `@Column(name="report_id")` |
| domain/repository | 新增 `ClaimReportRepository`（`findByReportNo` / `findById` 走 Criteria，无需 SQL 模板）；`ClaimCaseRepository` 增 `findByReportId` |
| application | `ClaimCaseApplicationService#submit` 改造（建报案 + 首案同事务）；新增 `ClaimReportApplicationService`（`appendCase` / `detail`） |
| adapter/web | `ClaimReportController`（`/api/v1/claims/reports/**`）、`AppendCaseReq` / `ClaimReportResp` / `ClaimCaseBriefResp`、`ClaimCaseResp` 增 `reportNo` |
| common | `ClaimErrorCodes` + `ClaimErrors.DEFAULT_HTTP_STATUS` 新增 3 码 |
| 前端 | `services/claimApi` 增 `reportDetail` / `appendCase`；`ClaimProgressPage` 展示报案号与同案清单；`ClaimSubmitPage` 支持 `?reportNo=` 追加模式 |

**为什么不新开 ApplicationService 给 submit**：submit 是「报案 + 首案」的原子用例，拆成两个服务会引入跨服务事务边界，且现有 Controller 已依赖 `ClaimCaseApplicationService`。新增的 `ClaimReportApplicationService` 只承载**报案视角**的用例（追加、详情），两者不互相调用。

### D9 前端追加赔案复用报案页，不另建表单

`ClaimSubmitPage` 增加 `reportNo` 查询参数模式：进入时拉报案详情、把保单/计划/被保人/出险时间渲染为只读、隐藏保单输入项、提交走 `appendCase`。成功后跳转新赔案详情。

**被否决**：在详情页做 Modal 表单。报案表单含动态明细行 + 费用类别目录 + 校验逻辑（321 行），复制一份等于把「至少一条明细」「金额合计」等规则写两遍，后端加字段时必然漏改一处。

### D10 兼容与语义收敛

- `ClaimWithItemsProjection` 增 `reportId` / `reportNo`（SQL 模板 `findClaimWithItems.sql` 补列）；这是投影，不是实体，补列不影响写侧。
- 前端详情页原标签「报案号」展示的是 `claimNo`，本次改为「赔案号」，另加一行「报案号」——**这是文案纠错**，不是新增字段；不改会让客服按赔案号当报案号检索。
- 文档 §9.3.1 整段重写；§9.5 增表；术语表区分报案/赔案；`batch_no` 注释去掉「报案号前缀」表述。

## 数据流

**首次报案（POST /api/v1/claims）**

```
Controller → 幂等快照查重 → ApplicationService.submit (@Transactional)
  ├─ claimCaseRepository.findByIdempotencyKey → 命中则返回首案(含 reportNo)
  ├─ resolvePolicy / 被保人 / 保单期间校验
  ├─ ClaimReport.open(reportId, ...)  → 插入 t_claim_report
  ├─ ClaimCase.draft(reportId = report.id, ...) → accept()
  └─ 插入 t_claim_case（report_id 落地）+ 发领域事件
```

**追加赔案（POST /api/v1/claims/reports/{reportNo}/cases）**

```
Controller → Idempotency-Key（必传）→ ClaimReportApplicationService.appendCase (@Transactional)
  ├─ findByReportNo（租户内）→ 不存在 404 / CLOSED 422
  ├─ 拒绝请求体携带保单维度（400）
  ├─ 继承 policy/plan/insured/outInsureTime，其余取请求
  ├─ ClaimCase.draft(reportId = report.id, ...) → accept()
  └─ 插入（发票唯一键、幂等键均由 DB 兜底）
```

## 风险与回滚

| 风险 | 处置 |
|---|---|
| 应用层建了报案但赔案插入失败 | 同事务，失败即整体回滚；单测断言回滚后报案数为 0 |
| 报案与赔案跨租户挂靠（DB 无复合外键） | 应用层一律用 `TenantPort.currentTenantId()` 建两侧；迁移终态断言跨租户挂靠为 0；详情查询按租户过滤 |
| 存量回填后 `report_no` 与未来新号冲突 | 回填复用赔案雪花 id 作报案 id，新报案用新 id；同一 id 不会被发两次 |
| 前端旧版本不认识 `reportNo` | 仅新增字段，旧代码忽略未知字段；`claimNo` 语义不变（仍是赔案号） |
| 回滚 | 反向迁移：删 `idx_claim_case_report` → 删 `report_id` → drop `t_claim_report`。数据损失仅报案行（可由赔案重建），无外部系统依赖 |

## 测试策略（判别力优先）

1. **编号内核**：`BizNoCodec` round-trip、`CL→RP` 前缀替换后校验位仍合法、跨前缀逆向必须抛错、10 万连续 id 零碰撞（沿用既有 9 项测试的加强）。
2. **1:N 行为**：一个报案下建 2 案 → 两案 `report_id` 相同、`claim_no` 不同；报案详情返回 2 条。
3. **幂等**：同键重复提交返回同案同报案号且 `t_claim_report` 行数不变（断言行数，不只断言返回值）。
4. **拒绝路径**：`CLOSED` 报案追加 → 422；不存在的报案 → 404；追加请求带 `policyNo` → 400。
5. **发票约束不回退**：跨报案复用同一发票 → 拒（`uk_tenant_invoice`）。
6. **变异验证**：把 `report_id` 写成 `id`（自映射）或把报案号写成赔案号，用例必须判红。
7. **迁移**：本地 MySQL 跑一次正向 + 重跑幂等；脏数据探针（人为造悬空 `report_id`）必须中止。
8. **前端**：`ClaimSubmitPage` 追加模式的表单校验单测；`ClaimProgressPage` 渲染报案号与同案清单（无反例静默通过）。

## 不做清单（明确登记，防被当成已实现）

- 报案级材料池、报案级理算汇总、报案撤销审批流、一次事故多保单、复制赔案（`is_copy`/`parent_claim_id`）。
- `batch_no` 的语义收缩仅做注释纠正，不迁移历史值、不加约束。
