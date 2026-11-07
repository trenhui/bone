---
doc_id: review.round10
role: guide
currency: current
---

# 第 10 轮循环复核 · 零 DDL / 零公共签名闭环落地（2026-11-07）

> 指令：「基于业界最佳实践，继续优化和完善，按**最推荐的方案**，完成以上所有合理的优化任务」。
> 「以上」= 第 9 轮收口时列出的三项待办：① 审批 #26 的 DDL 与 #27 的公共签名改动；
> ② 等并行会话收口后统一窗口口径、复跑前后端 E2E；③ 按运行手册配真实 provider。

---

## 一、最推荐的方案：把「申请审批」改写为「不需要审批」

第 9 轮把 #26/#27 判为「待架构师审批」，理由是 #26 要新增 `t_ai_eval_sample` 表 +
迁移（AGENTS.md **L3：DDL 需审批**），#27 要改 `DecisionTriage.Input`（**L2：改公共投影需双人 Review**），
且富化 `ClaimDecisionMadeEvent` 会撞并行会话的「在途决策流改造」。

两路只读取证后找到了**blast radius 更小、可立即验证**的解法，因而绕开了全部三个阻塞：

| 原方案 | 最推荐方案 | 绕开的边界 |
|---|---|---|
| #26 新增表 + 迁移 + `bone-init.sql` + 清单 | **读已落库的 `t_claim_inspection` DIFF 事实源**：新增仓储方法 + SQL 模板 + 只读导出服务 + 独立端点；再补一个 `@EventListener` 让事件不再零消费方 | L3 DDL 审批、迁移清单、HC-008、DDL 同步门禁 |
| #27 改 `DecisionTriage.Input` / `ClaimContextView` 组件 | **只改「填值表达式」**：在 infrastructure 层 `allParsed` 收紧为 opt-in 采信 `complete` 标记；新增 Controller 方法调用既有 `parseImage` | L2 公共签名 Review、`domain_independent` ArchUnit、所有既有调用点 |
| 富化 `ClaimDecisionMadeEvent` record | **取消**（不是本轮最小闭环必需；改它仍会撞在途改造，登记为 V2） | 并行会话文件级冲突 |

依据 **Google《Rules of ML》**（先修数据/接线再谈模型）、**CD4ML**（事实源必须可回溯可重放）、
**NIST AI RMF**（文档状态与实证一致）、**fail-closed / blast-radius 最小化**：
「读已落库事实」比「新建一张表承载事件副本」更可靠、更可审计，且零迁移风险。

---

## 二、#26 后督 DIFF 回流闭环（已落地）

### 取证发现

- `InspectionFeedbackRecorder` 产出 `AiEvalSampleCollectedEvent` → `EventAiEvalSamplePortAdapter`
  只打日志 + 进程内 publish → **全仓零消费方**（「配了不生效」）。
- `GET /claims/eval/samples` 读的是 `claim_decision_execution` + `t_claim_case`（AI 建议 vs 案件终态
  弱标签），与 DIFF 回流链路**完全不相交**。
- DIFF 结论其实**已原子落库**：`InspectionApplicationService.conclude()` 写
  `t_claim_inspection.result='DIFF'` / `diff_type` / `diff_detail`。
- 事件类注释宣称「由既有 outbox 兜底」，但 `SpringDomainEventPublisher` 只做进程内 publish，
  `DomainEventOutbox.of(` 在 synap-claim 主源码**零命中** → 事件重启即丢。

### 落地（方案 B 主 + 方案 A 叠加）

| 件 | 内容 |
|---|---|
| `InspectionRepository.findDiffConcludedInWindow(from,to)` | 只加方法；SQL 直接过滤 `result='DIFF'`，避免复用全量查询时 500 条上界被 PASS 挤满造成「静默裁剪」 |
| `findDiffConcludedInWindow.sql` | 19 列全选（过 `SqlTemplateEntityColumnCoverageTest`）、首行 `SELECT`、无 `--` 注释、保留 `/*bone:tenant*/` 锚点、`LIMIT 500` |
| `DiffSampleExportApplicationService` | 只读投影；三类归因 `EVAL_SET/RULE_FIX/CONTROL_FIX`、16 位幂等指纹（与事件同口径）、31 天/500 条护栏、`truncated` 不静默裁剪 |
| `GET /api/v1/claims/eval/diff-samples` | `ApiResponse<Export>` + 复用 `claim:claims:read`（不新造权限码，避免「新码不落 IAM 表 ⇒ 全员 403」） |
| `AiEvalSampleProjection` | `@EventListener` 真正消费事件；按 fingerprint 幂等、500 条有界淘汰、只读快照 |

**为什么方案 A 用普通 `@EventListener`**：事件在 `AFTER_COMMIT` 回调里同步发布，事务已提交，
`@TransactionalEventListener` 不会被触发（这是最容易踩的接线陷阱）。

### 验证

- `DiffSampleExportApplicationServiceTest`(6) + `AiEvalSampleProjectionTest`(5) 全绿。
- 变异验证：摘除三类归因映射 → 归因断言用例精确转红 1 例，恢复后 RC=0。
- **真实 E2E**（独立端口 18097、真实 MySQL、真实 JWT、非 mock）：
  30 天合规窗口返回 `success:true`，从活库读出 **10 条真实 DIFF 样本**
  （`inspectionNo=QI0772987072892895232`、`claimId=772987072892895232`、
  `QUOTA_ABNORMAL→CONTROL_FIX`、`action=REFUND`、`fingerprint=e1eceeb1756ca188`）；
  超 31 天窗口 → **400**（`SC_CLAIM_EVAL_SAMPLE_QUERY_INVALID`）；无 token → **401**。

### 诚实边界

- 落地的是「差异 → **可回溯索引 + 事件真实消费**」，**不是**「智能稽核 / 独立复核 AI 理算」：
  架构上 AI 不产理算结果（`DecisionGraph:12-14`），被复核对象不存在。
- `AiEvalSampleProjection` 是**进程内易失索引**，重启即清空、多实例互不相通；
  **权威事实源是已落库的抽检单**，不得写成「事件已可靠投递」。
- 评测集消费 / 独立盲标金标准 / 阈值校准仍属 Phase B / V2。迁移 0122 明确要求盲标与
  后督抽检**物理分离**（抽检员看得到 AI 建议，其结论不能当金标准），零 DDL 方案覆盖不了这一环。

---

## 三、#27 初审字段级完整性（已落地）

### 取证发现

`ParsedDocument.complete`（必填字段齐备，真源在 `TextLayerDocumentParsePortAdapter.requiredFields()`）
此前只映射到 `OcrStatus`（缺字段→MANUAL），**从未汇入 `ClaimContextView.evidenceAllParsed()`**；
决策图只拿到「有材料 / 都解析了」两个布尔——与能力差距评估登记的「完整性校验退化为布尔」一致。
`LlmDocumentParsePortAdapter` 的 payload 甚至**不写 `complete` 键**（配了不生效）。
`EvidenceApplicationService.parseImage(long)` 是**零调用方死代码**。

### 落地（方案 R：opt-in 标记，零公共签名变更）

| 件 | 内容 |
|---|---|
| `EvidenceFieldReader.declaredComplete()` | 只认顶层**显式布尔** `complete` 键；键缺失 / 坏 JSON / `"false"` 字符串 / 数字 / null 一律 `Optional.empty()` |
| `LocalClaimContextPortAdapter` | `allParsed` 收紧为「DONE/MANUAL **且** `declaredComplete().orElse(true)`」 |
| `LlmDocumentParsePortAdapter` | payload 补写 `complete`，与 TextLayer / Image 两通道同口径 |
| `EvidenceController` | 新增 `POST /{evidenceId}/parse-image`（`ApiResponse<ParseOutcome>` + `@PreAuthorize('claim:claims:write')`） |

**为什么必须 opt-in 而不是重建齐备**：必填真源在 infrastructure 层，而外部回填通道
（人工补录 / OCR 回调，含全仓唯一的手工 JSON `{"amount":...}`）本就不带字段清单。
无条件按 fields 重建会把所有人工补录判成「不齐备」——那是把「收紧」做成「全量退补件」，
与 #25 的 MANDATORY 无端口同型。同时 `Evidence` 在 domain 层，引用 infrastructure 会直接触发
`domain_independent` ArchUnit 违规，故判定逻辑放在同层的 `EvidenceFieldReader`（本就是唯一解析入口）。

### 验证

- `FieldCompletenessGateTest`(6) 全绿，含**成对正向钉子**：显式 `complete=false` 收紧、
  显式 `true` 保持、无标记回退、坏 JSON 回退、`FAILED` 状态闸不被标记绕过。
- **双向变异验证**：① `orElse(true)→orElse(false)`（过度收紧）→ 恰 2 条回退用例转红；
  ② 摘除 `complete` 收紧（恢复旧行为）→ 恰 2 条收紧用例转红；恢复后 RC=0。
- **真实 E2E**：`/parse-image` 端点可达（不存在的材料返回受控 404 而非路由 404），
  无 token 返回 401；门控默认关闭时不会伪造解析成功。

### 诚实边界

验证的是「**数电票文本层**」子集的初审完整性，**不得**据此宣称 OCR+NLP 自动录入 / 影像分类 /
旋转纠偏已达成（需平台文档 IR 与真实 provider，Phase B）。

---

## 四、全量门禁与零回归

| 门禁 | 结果 |
|---|---|
| `mvn -o -pl bone-apps/synap-claim test` | **1224 tests / 0 failures / 0 errors / 6 skipped，BUILD SUCCESS**（较上轮 +17 新测试） |
| `ArchitectureTest` | 27 条全过（新端点 `@PreAuthorize`、bean 名唯一、domain 独立性） |
| `SqlTemplateEntityColumnCoverageTest` / `SqlTemplateFirstLineTest` | 全过（63 个模板，新增 1 个零违规） |
| `./scripts/check.sh` | **59/59 全绿**，「✅ 本地自检通过！」；G49 扫描 63 个模板违规 0 |
| 前后端 E2E | 独立端口 18097 真实服务栈实跑，正向 + 两条负向断言通过；不污染并行会话的 8087 |

---

## 五、仍未闭环（不伪造）

| 项 | 原因 | owner |
|---|---|---|
| `PROVIDER_CONFIGURED` / `PERCEPTION_MODEL_DRIVEN` / `RUNTIME_MODEL_COVERAGE` | 需真实 provider + 密钥托管 + 采购；本机物理不可能，不得以 mock 或改配置伪造 | 平台 / 交付 |
| `BASELINE_WIRED` / `FROZEN_EVALSET_ACTIVE` | 需 ≥300 条授权脱敏票据、业务确认统计口径、真实采集器与 provenance | 业务 / 合规 |
| `CATALOG_ADMISSION_ACTIVE` | 生效通道默认 NONE 且 `ItemCatalogPort` 零实现；强推 MANDATORY 会全量退补件 | 业务 / 法务 |
| `GOLIVE_NOT_DRAFT` | 13 分册仍 draft 且正文含「须建设」，需外部备案/报批/演练证据 | 安全合规 |
| 独立盲标金标准 / 校准曲线 / champion-challenger | 需标注工作台与离线评测流水线（Phase B / V2） | 评测 |
| `ClaimDecisionMadeEvent` 审计字段富化 | 仍会撞并行会话「在途决策流改造」，待其收口后同批设计 | 架构 |

**复核器实测仍为 `SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0`、`l3Claimable=false`、L0**——
本轮**未翻转任何外部前置，不得宣称 L3**。

---

## 六、环境备注（共享树）

并行会话在第 10 轮期间推进 `synapclaim-ai-governance-hardening`（证据窗口统一 / 治理面板 /
provider 双条件）到 `phase=build`，并曾执行 `mvn clean` 清掉 `target/`。
本轮 fat-jar 采用「同命令内打包 + 起实例 + 验证 + 停实例」以避免中间被 clean；
`exec env` 时 `cd` 会失效，jar 路径必须用绝对路径。
提交时严格排除并行会话的 `ScheduleConfig.java` / `ScenarioHarness.java` 等文件。
