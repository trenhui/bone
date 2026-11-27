---
comet_change: procuraai-owner-freeze-package-draft
role: owner-freeze-package-draft
status: Draft / 待 owner 批准
canonical_spec: openspec
---

# procuraAI Owner 冻结包草案（Draft / 待 owner 批准）

> 本文件是 owner / 法务 / 架构评审的待签草案，不是业务真源，也不是冻结结论。任何签名、法务结论、模型效果达标值、压测链接、契约 Frozen 状态均须由相应责任人在另行授权的 change 中填写。
>
> 裁决顺序沿用 `doc/ai-product/procuraAI/文档资产登记与真源裁决.md` 的八级链：D3-v2 与 LLD 机读工件优先于旧族；决策登记只做状态索引，不替代业务裁决。

## 0. 文档性质与六字段模板

本草案只提供四类待签材料：契约冻结评审清单、评测扩容计划、法务书面结论输入清单、开口项全量索引。每一项均使用以下六个字段，缺一即视为未完成：

1. **状态**：固定为 `Draft / 待 owner 批准`；无法确认时保持 Draft，不得升级为已冻结 / 已达标 / 已签署。
2. **owner**：真源未给出具体签署人时写 `未指派`；不得用部门名、角色名或人名代替签署人。
3. **验收判据**：写可执行的机器检查、可定位工件字段或明确签署动作。
4. **未闭合风险**：写清仍依赖谁、缺什么证据、为何当前不能冻结。
5. **真源锚点**：写仓库路径 + 稳定章节名 / JSON 字段名，避免仅依赖行号。
6. **待裁决动作**：写 owner / 法务需要填写或批准的具体内容。

### 状态诚实护栏（全局断言）

| 断言 | 当前真源值 | 本草案行为 |
|---|---|---|
| 评测集状态 | `eval/ai-decision-evalset.json`：`status=seededDraft`、`needsOwnerReview=true` | 只读引用，不改成 `frozen` |
| 扩容计划状态 | `eval/ai-decision-evalset.json`：`scalePlan.status=notStarted` | 只读引用，不改成 `inProgress` / `completed` |
| 样本规模 | `caseCount=27`、`targetCases=200` | 不把 27 写成 200，不伪造达标 |
| 契约冻结状态 | 设计态、未冻结 | 差异项保持 Draft，不写 Frozen |
| owner / 法务 / 模型效果 / 压测 | 待填 | 不写签名、书面结论、达标数值或压测链接 |
| CI 三入口判据集合 | 现有常量集合 | 不新增判据、不改常量、不接新 blocking step |

## 1. 契约冻结评审清单

真源对照面：`采购AI-接口契约与数据字典-v2.md` §1.1 / §2 / §3.1 / §3.2 / §3.3、`detailed-design/00-总纲与统一约定.md` §22、`detailed-design/contracts/openapi.yaml`、`detailed-design/contracts/asyncapi.yaml`。

### CF-01 能力码 ↔ 模块码 ↔ 端口 / 端点映射

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：`D3-v2 §1.1` 每个 AI 能力码都能在 `openapi.yaml` 与编排端口 / DTO 登记中找到同名或显式映射项；悬空项必须列出。
- **未闭合风险**：新增子能力码的冻结范围未裁决；映射表若变更会牵动 evalset 的 `capabilityCode` 双向追溯。
- **真源锚点**：`doc/ai-product/procuraAI/采购AI-接口契约与数据字典-v2.md#1-ai-能力码--采购模块码-显式映射`；`detailed-design/contracts/openapi.yaml` paths / components.schemas；`detailed-design/00-总纲与统一约定.md#22-契约冻结流程跨卷-raci-与待决总表`。
- **待裁决动作**：确认本轮冻结的能力码清单、映射 owner、版本戳；驳回或接受新增子能力码登记。

### CF-02 ContractReviewed 唯一 payload 定义

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：`D3-v2 §2` 的字段清单与 `ContractReviewedEvent` 记录字段逐一对应；`asyncapi.yaml` 通道 schema 与 D3-v2 字段类型 / 必填约束一致，无第二份 payload 定义。
- **未闭合风险**：持久化事件链已落地 Outbox / Inbox / DLQ，但事件 schema 的跨语言生成与版本兼容策略仍待评审。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#2-contractreviewed-唯一-payload-定义r3`；`detailed-design/contracts/asyncapi.yaml` channels / components.messages；`t_proc_contract_outbox` / `t_proc_contract_inbox` / `t_proc_contract_dlq` DDL。
- **待裁决动作**：确认 7 字段契约冻结版本；决定是否允许非破坏性扩展字段及其兼容窗口。

### CF-03 三态判定与 UNKNOWN fail-closed

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：D3-v2 §2 / §3 的 `UNKNOWN` 不得压成布尔或低风险；`openapi.yaml` 枚举与 evalset 负向用例断言一致。
- **未闭合风险**：不同模块对 UNKNOWN 的展示与人工兜底动作可能不一致；契约冻结前无法保证跨端语义统一。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#2-contractreviewed-唯一-payload-定义r3`（§2 表格「缺省 fail-closed 视为 UNKNOWN」）与 `#3-ai-合规契约r2取代已撤销的-adr-06`；`eval/ai-decision-evalset.json` 的 `MATCH-02` / `RISK-01` 期望值。
- **待裁决动作**：批准三态枚举、UNKNOWN 转人工路径与端到端错误码映射。

### CF-04 AI 参与披露三态

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：披露接口字段能表达「已参与 / 未参与 / 无法确认」；模型版本快照不得冒充参与证据；请求跨案时 fail-closed。
- **未闭合风险**：披露文案与留存期限属合规语义，工程只能提供字段，不能替代法务确认。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#3-ai-合规契约r2取代已撤销的-adr-06`；`eval/ai-decision-evalset.json` 的 `aiDisclosure` suite；`GET /api/v1/claims/{claimId}/decision/disclosure` 为工程侧参考实现，不直接移植。
- **待裁决动作**：确认披露字段、文案基线、留存期限与跨案拒绝口径。

### CF-05 ContractReviewed 事件链语义

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：`asyncapi.yaml` 对 Outbox 发布 / Inbox 幂等消费 / DLQ 失败隔离的描述与实际持久化实现一致；payload 字段数不变；租户以消费方上下文锚定。
- **未闭合风险**：文档语义已同步，但真实 MQ / 生产 DB 尚未接入；不能据文档声明生产级投递保证。
- **真源锚点**：`detailed-design/contracts/asyncapi.yaml` ContractReviewed 通道；`tools/check_procura_event_chain.py`；`bone-init.sql` 三张事件链表 DDL。
- **待裁决动作**：批准事件链语义与未来真实 MQ 接入的变更边界；本轮不得把文档同步写成生产验收。

### CF-06 SLO 数值冻结台账

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：每条 L38–L43 SLO 都有真实压测链接、样本条件、统计口径与冻结签名；缺失项保持「草案」。
- **未闭合风险**：无 owner 验证的压测证据；当前数值不得作为发布承诺。
- **真源锚点**：`detailed-design/00-总纲与统一约定.md#224-数值冻结台账与模板`；决策登记中 SLO 开口项的状态行。
- **待裁决动作**：指派 owner、提供压测链接并逐项签署冻结值。

### CF-07 AI 子能力端口 / DTO / 端点登记

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：八码子能力在 `D3-v2 §1.1`、编排端口方法签名、DTO 与 `openapi.yaml` 端点登记中一一可追溯；冻结前工程只保留 fail-closed / 披露 / rules_only 降级骨架。
- **未闭合风险**：真实模型接入未授权；冻结后若改签名会触发跨模块契约回归。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#1-ai-能力码--采购模块码-显式映射`；`ProcurementAiOrchestrationPort` 接口；`detailed-design/contracts/openapi.yaml`。
- **待裁决动作**：逐码批准或驳回签名 / DTO / 端点；指定契约版本戳与冻结 owner。

### CF-08 AI 原生运行时契约 X1–X4

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：X1–X4 字段级 schema 逐字段确认或驳回；A·B·C 重排所需的 N1 / G10 能力码、专属负向用例、登记总表与估工均已齐备；任何新增门禁判据另行立项。
- **未闭合风险**：SOX / GDPR Art.22 / EU AI Act 逐项对齐材料未闭合；未冻结前不得放开任何自治档。
- **真源锚点**：`AI接入判定与AI原生内核补充设计.md §8「运行时契约层字段级规范（DesignOnly，2026-11-14 业界校准增补）」`（文件第 305 行）；`采购AI-接口契约与数据字典-v2.md#1-ai-能力码--采购模块码-显式映射`；决策登记的 AI 原生运行时契约状态行。
- **待裁决动作**：批准字段级 schema、A·B·C 分级与重排前置清单；明确仍为 `ADVISORY_ONLY`。

## 2. 评测扩容计划

真源：`eval/ai-decision-evalset.json` 的 `scalePlan` / `groundTruthPolicy` / `suites` / `cases`；D3-v2 §3.2 / §3.3；决策登记中评测与扩容相关开口项。当前 `status=seededDraft`、`scalePlan.status=notStarted`、`caseCount=27`、`targetCases=200`，本计划不修改这些字段。

### EV-01 27 → 200 样本扩容边界

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：owner 裁决后 `targetCases=200` 可拆分为 P0 / P1 / P2 配额，且每套至少 1 正向 + 1 负向；当前仍为 27 条种子草稿。
- **未闭合风险**：扩容样本来源、合成数据与真实脱敏数据的边界未批准；不得先改门禁下界。
- **真源锚点**：`eval/ai-decision-evalset.json` 顶层字段 `status` / `needsOwnerReview` / `scalePlan.status` / `caseCount` / `targetCases`；`groundTruthPolicy.piiPolicy`。
- **待裁决动作**：批准 200 例配额与转正日期；转正须另开 change。

### EV-02 分层覆盖与对抗 / 公平样本

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：P0 覆盖高风险 fail-closed 与六条红线；P1 覆盖能力码正向对照与事件链；P2 覆盖边界值 / 长尾；对抗红队样本入 L5，外部污染与 PII 样本在摄入层拒收。
- **未闭合风险**：公平性样本的属性维度与脱敏口径需法务确认；未确认前不得声称无偏见。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.adversarialSamplePolicy`、`algorithmicFairness` suite；D3-v2 §3 算法公平条款。
- **待裁决动作**：批准分层配额、红队样本构造规则与公平性属性维度。

### EV-03 双人标注与一致性

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：每条新增样本由两名标注人独立判定；Cohen's Kappa ≥ 0.8；低于阈值的批次退回重标。
- **未闭合风险**：标注人资质、培训与盲审机制未指派；Kappa 计算脚本未纳入本 change。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.provenance`；决策登记中扩容开口项的验收判据。
- **待裁决动作**：指派标注 owner、批准 Kappa 门槛与复标流程。

### EV-04 第三方仲裁

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：双人标注分歧由第三方仲裁人给出最终 `expected`；仲裁结果与 `groundTruthRef` 同批留存；不得由模型生成期望值。
- **未闭合风险**：仲裁人独立性与留痕格式未批准。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.provenance` / `changePolicy`。
- **待裁决动作**：批准仲裁人角色、分歧升级路径与证据留存字段。

### EV-05 训练 / 验证 / 冻结三分离

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：冻结集只用于回归判定，不参与训练与提示词调优；模型 / 提示词 / 规则集 / 分诊阈值任一变更必跑本集。
- **未闭合风险**：真实模型接入尚未授权；当前无法验证物理隔离的训练数据管道。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.frozenSetDiscipline` / `changePolicy`。
- **待裁决动作**：批准集合切分比例、冻结集访问控制和变更触发规则。

### EV-06 通过线待冻结字段

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：高后果召回率、override 率、注入样本检出率、PII 泄露零容忍等字段均写「待 owner 冻结」；不得填数值或宣称达标。
- **未闭合风险**：缺 owner 冻结值，评测集无法从 `seededDraft` 转正；不得用工程默认阈值替代。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.adversarialSamplePolicy`；D3-v2 §3.2 评测集行为枚举；PRD §9.3 注入样本检出率的原始要求。
- **待裁决动作**：逐项填写通过线、样本口径与生效版本。

## 3. 法务书面结论输入清单

本清单是输入材料，不代替法务书面结论。`法源射程分层与自治边界-ADR.md` 的档 B 在结论出具前保持 `DesignOnly`，不可执行。

### LG-01 六条不可稀释红线

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：法务结论逐条覆盖 AI 不碰钱、授标归人、结案归人、三态 fail-closed、可解释与留痕、公平护栏；不得合并或稀释。
- **未闭合风险**：当前六条红线仅为工程 / 设计约束，尚无书面法律结论。
- **真源锚点**：`法源射程分层与自治边界-ADR.md` 六条红线；`文档资产登记与真源裁决.md` §5；D3-v2 §3。
- **待裁决动作**：出具逐条书面结论并指定生效范围与版本。

### LG-02 主体适用性

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：结论明确适用主体（招标人 / 代理机构 / 投标人 / 评标专家 / 采购企业内部用户）及不适用主体；外部义务的射程以 D3-v2 §3.3 核对表为准。
- **未闭合风险**：《暂行办法》已按第 2 条第三款排除企业内部应用；195 号文限定招标投标活动，误用会造成过度合规或漏合规。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#33-外部法规原文核对表`；`eval/ai-decision-evalset.json` 各 `basisVerification.applicability`。
- **待裁决动作**：批准主体适用性矩阵，纠正任何原则性引用被当作强制义务的表述。

### LG-03 场景射程

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：逐场景写明合同审查、三单匹配、供应商风险、异常检测、披露、对账、公平性、内容标识的适用 / 不适用条件。
- **未闭合风险**：同一外部条款在不同场景可能是 principleExtension 或 conditionalBiddingScope；未裁决前不得一刀切。
- **真源锚点**：`eval/ai-decision-evalset.json` `regulatoryBasis.basisVerification.applicability`；D3-v2 §3.3。
- **待裁决动作**：批准场景射程表与例外场景处理口径。

### LG-04 C-2 按客户属性叠加材料包

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：对央国企客户或依法必须招标项目，明确是否叠加算法备案与安全评估材料包；触发条件、材料清单与责任方均可定位。
- **未闭合风险**：客户属性识别与项目属性判定缺法务确认；未确认前不得声称全套材料已具备。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#31-合规依据适用性与工程化清单` C-2 条件；决策登记的法务结论开口项补注。
- **待裁决动作**：批准 C-2 触发条件、叠加材料包清单与豁免边界。

### LG-05 外部法规原文与引用口径

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：逐字摘录、URL、核验日期、引用口径与主体适用性均来自 D3-v2 §3.3；不得臆造条款号。
- **未闭合风险**：网页原文可能更新；当前核对表是某日期快照。
- **真源锚点**：`采购AI-接口契约与数据字典-v2.md#33-外部法规原文核对表`；各 `basisVerification` 字段。
- **待裁决动作**：批准核对表版本与定期复核周期。

### LG-06 PII 禁入

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：评测集与日志均 `piiFree=true`；真实姓名 / 证件号 / 手机号 / 银行卡 / 供应商机密不得进入样本或证据回传。
- **未闭合风险**：证据回传若含 `output` 原文会泄漏 PII；需在法务结论中明确禁止。
- **真源锚点**：`eval/ai-decision-evalset.json` `groundTruthPolicy.piiPolicy`；D3-v2 §3 PII 条款与 §3.1 C-3。
- **待裁决动作**：批准 PII 分类与脱敏策略，确认证据字段白名单。

### LG-07 可解释与留痕

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：AI 建议、人工终决、规则命中与版本来源均可追溯；无法确认的参与状态保持 UNKNOWN。
- **未闭合风险**：留存期限、取证格式与跨域审计边界未签署。
- **真源锚点**：D3-v2 §3 可解释 / 留痕条款；披露三态契约；`ReviewTracePort` 持久化追溯链。
- **待裁决动作**：批准留痕字段、留存期限与审计导出流程。

### LG-08 公平护栏

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：不得按所有制、地域、规模产生系统性偏见；公平性负向用例与 G15 护栏同时可定位。
- **未闭合风险**：偏见检测的统计口径与容忍阈值待 owner 冻结。
- **真源锚点**：`eval/ai-decision-evalset.json` `algorithmicFairness` suite；D3-v2 §3 算法公平与反歧视条款。
- **待裁决动作**：批准公平性指标、抽样窗口与复核触发条件。

### LG-09 生成内容标识

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：M7 起草稿 / M2 审查报告带显式标识 + 隐式元数据；缺一不得出域。
- **未闭合风险**：隐式元数据格式与跨系统保留策略未签署。
- **真源锚点**：`eval/ai-decision-evalset.json` `contentLabeling` suite；D3-v2 §3 生成内容标识条款。
- **待裁决动作**：批准标识文案、元数据字段与出域拦截规则。

### LG-10 法务结论收口

- **状态**：Draft / 待 owner 批准
- **owner**：未指派
- **验收判据**：法务书面结论逐项回应 LG-01…LG-09，明确版本、日期、签署人；结论出具前档 B 保持 DesignOnly。
- **未闭合风险**：书面结论未出具；工程红线不能替代法律效力判断。
- **真源锚点**：`法源射程分层与自治边界-ADR.md` 档 B；`文档资产登记与真源裁决.md` §5；决策登记的法务结论开口项。
- **待裁决动作**：出具书面结论并指定生效日期；不得由工程侧代签。

## 4. 开口项全量索引（18 项）

索引只做编号级登记；详细论证回指第 1–3 节与 `decisions/DECISION-REGISTER.md`。已落地项只引用既有工程证据，不回写历史台账或伪造签署。

### D-01 关键 SLO 数值冻结

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：真实压测链接 + 冻结台账签名齐全；缺一保持草案。
- **未闭合风险**：无 owner 验证的压测证据，不得对外承诺数值。
- **真源锚点**：决策登记 SLO 开口项；`detailed-design/00-总纲与统一约定.md#224-数值冻结台账与模板`。
- **待裁决动作**：指派 owner、提供压测链接并冻结 L38–L43。回指：CF-06。

### D-02 可见性矩阵会签

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：`总体 §9.1` 四角色会签栏实名签署；门禁只守结构不守签名。
- **未闭合风险**：结构存在不等于实名会签完成。
- **真源锚点**：决策登记会签开口项；`总体 §9.1`。
- **待裁决动作**：完成四角色实名会签。回指：CF-01。

### D-03 跨域接入 PR 与署名

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：平台 RFC §5.1 实名署名 + 至少 1 个可点开的真实跨域接入 PR。
- **未闭合风险**：跨域交付依赖外部团队，当前无真实 PR 证据。
- **真源锚点**：决策登记跨域开口项；平台 RFC §5.1。
- **待裁决动作**：提供 PR 链接并署名。回指：CF-05。

### D-04 ATAM 质量属性优先级

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：排序会纪要 + 签字；当前仅有 H/M 工程预排序。
- **未闭合风险**：未开评审会，优先级未获正式裁决。
- **真源锚点**：决策登记 ATAM 开口项；ATAM 设计文档。
- **待裁决动作**：召开评审会并签署优先级。回指：CF-06。

### D-05 跨域 ACL 交付

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：INBOUND 事件从「接口预留」转为「运行期可触发」的源码证据。
- **未闭合风险**：运行期触发涉及跨域凭证与权限，未授权不得接生产。
- **真源锚点**：决策登记跨域 ACL 开口项；对账闭环 ACL 设计。
- **待裁决动作**：批准 ACL 交付边界并给出源码证据。回指：CF-05。

### D-06 存量付款硬隔离

- **状态**：Draft / 待 owner 批准（当前登记：`Landed`，2026-11-20）
- **owner**：未指派（用户授权本轮落地，未伪造 owner 签署）
- **验收判据**：`PaymentIsolationProperties` 默认 false、`PayHandlerConditionalRegistration` 单点注册、`PurchaseComponentScanConfiguration` 正则排除、启动期日志、双态装配测试均存在；模块 25 例测试全绿。
- **未闭合风险**：工程已落地不等于 owner 签署；历史台账不得回写。
- **真源锚点**：决策登记该条状态行与 2026-11-20 变更说明；`bone-app-purchase` 相关 Java 文件。
- **待裁决动作**：仅在需要法律效力时由 owner 另行签署；本草案不回写历史。回指：LG-01。

### D-07 AI 原生能力立项

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：逐项立项 + 通过评测集 + owner 排期；Proposal 本身不实现能力。
- **未闭合风险**：未立项即接入会造成能力边界与评测缺口。
- **真源锚点**：决策登记立项开口项；蓝图 AI 原生章节。
- **待裁决动作**：批准立项清单与排期。回指：CF-07、EV-02。

### D-08 法源射程法务结论

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：法务书面结论覆盖主体适用性、场景射程与 C-2 叠加材料包条件；结论前档 B 保持 DesignOnly。
- **未闭合风险**：无书面结论；六条红线尚属设计约束。
- **真源锚点**：决策登记法务开口项；`法源射程分层与自治边界-ADR.md` 档 B；D3-v2 §3.1 C-2。
- **待裁决动作**：出具书面结论。回指：LG-01…LG-10。

### D-09 契约冻结与 DDL 落库

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：冻结评审 + 迁移脚本；当前禁止写生产 DDL 真源。
- **未闭合风险**：未冻结即落库会形成事实契约，回退成本高。
- **真源锚点**：决策登记契约冻结开口项；D3-v2 §2；`bone-init.sql` 现有 `t_proc_*` 定义。
- **待裁决动作**：批准冻结范围与迁移窗口。回指：CF-01…CF-03。

### D-10 模型效果测评与漂移闭环

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：评测集从 `seededDraft` 转正 + 漂移闭环证据；当前不得写已达标。
- **未闭合风险**：转正依赖扩容、通过线冻结与真实模型接入三者齐备。
- **真源锚点**：决策登记评测开口项；`eval/ai-decision-evalset.json` `status` / `scalePlan.status` / `changePolicy`。
- **待裁决动作**：批准漂移阈值、复测周期与阻断规则。回指：EV-01…EV-06。

### D-11 frontmatter 守护接入 CI

- **状态**：Draft / 待 owner 批准（当前登记：`Landed`，2026-11-20）
- **owner**：未指派（用户授权本轮 L3 落地，未伪造 owner 签署）
- **验收判据**：`frontmatterGate` / `frontmatterProbe` 已在三入口一致；对齐 10/10、自检 149/149、3 条注入反证转红。
- **未闭合风险**：工程已落地不等于 owner 签署；供应链 target 态未改。
- **真源锚点**：决策登记该条状态行；`tools/check_procura_alignment.py` 常量区与三入口脚本。
- **待裁决动作**：无需本轮动作；若需法律效力另行签署。回指：CF-06。

### D-12 旧族物理迁移与三表收敛

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：物理迁 `archive/`、三表物理收敛与删除候选处置须逐文档裁决；A6 探针 `m_legacy_file_dropped` 已落地且自检 151/151。
- **未闭合风险**：物理迁移会击穿 A10 旧族全文反向守护，属黄区 / 红区，不得擅自执行。
- **真源锚点**：决策登记该条状态行；`tools/check_procura_alignment.py` `LEGACY_DOCS` / A6；资产登记 §11。
- **待裁决动作**：明确不得迁移的文档集合或批准替代守护方案。回指：CF-01。

### D-13 CI 软件供应链加固

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：CIS-1 最小权限已落地且 `m_ci_drop_permissions` 转红；CIS-2/3/4/5 与 Scorecard 待 owner 输入。
- **未闭合风险**：20 个 `uses:` 缺 owner 验证的官方全长 SHA，禁止使用 fork 摘要充数。
- **真源锚点**：决策登记该条状态行；`.github/workflows/ci.yml` 顶层 `permissions`；`tools/check_procura_alignment.py` 注入探针。
- **待裁决动作**：提供官方 SHA 与 Scorecard 立项决策。回指：CF-06。

### D-14 AI 原生运行时契约分级

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：X1–X4 字段逐项确认；A·B·C 重排前置（N1 / G10 登记、专属负向、蓝图总表、估工、F1–F4）齐备；新增判据另行立项。
- **未闭合风险**：合规对齐材料不足，放开自治档风险不可接受。
- **真源锚点**：决策登记该条状态行与最小输入段；`AI接入判定与AI原生内核补充设计.md §8`。
- **待裁决动作**：批准字段级 schema、分级与仍为 `ADVISORY_ONLY` 的边界。回指：CF-08、LG-08。

### D-15 评测扩容与转正

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：双人标注 + Kappa ≥ 0.8 + 第三方仲裁 + 通过线冻结后，方可由授权 change 修改 `status` / `scalePlan.status`；当前必须保持 `seededDraft` / `notStarted` / 27。
- **未闭合风险**：尚无达标证据；不得先改门禁下界或把样本数写成 200。
- **真源锚点**：决策登记该条状态行；`eval/ai-decision-evalset.json` 顶层状态字段与 `scalePlan`。
- **待裁决动作**：批准扩容计划、通过线与转正时点。回指：EV-01…EV-06。

### D-16 AI 子能力契约冻结

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：八码端口方法签名 / DTO / 端点登记与 `D3-v2 §1.1` 一起冻结；冻结前只保留降级骨架。
- **未闭合风险**：签名变更会波及 evalset 与前端契约；真实模型接入未授权。
- **真源锚点**：决策登记该条状态行；`ProcurementAiOrchestrationPort`；`detailed-design/contracts/openapi.yaml`。
- **待裁决动作**：逐码批准签名与版本戳。回指：CF-07。

### D-17 真模型接入与效果声明

- **状态**：Draft / 待 owner 批准（当前登记：`Proposed`）
- **owner**：未指派
- **验收判据**：依赖扩容转正、子能力冻结与漂移闭环三者完成后另行立项；接入后仍 `ADVISORY_ONLY`、人工终决。
- **未闭合风险**：前置未闭合；不得先声明效果达标或自动决策。
- **真源锚点**：决策登记该条状态行；D3-v2 §3.1 人工最终决策；evalset `changePolicy`。
- **待裁决动作**：批准接入前置清单与模型选型。回指：EV-06、LG-01。

### D-18 自动结案 / 自动放款 / 授标红线

- **状态**：Draft / 待 owner 批准（当前登记：`Accepted + 外部依赖`）
- **owner**：未指派
- **验收判据**：资产登记 §5、撤销链与现有工程守卫确认「一律禁止」；具名人工操作人证据存在；最终以法务书面结论收口。
- **未闭合风险**：工程已落地但无法务书面结论；不得据此打开自动决策。
- **真源锚点**：决策登记该条状态行；`文档资产登记与真源裁决.md §5`；`awardByHuman` / `approveByHuman` 源码。
- **待裁决动作**：由法务书面确认红线，并拒绝任何自动放款 / 自动结案 / AI 授标提案。回指：LG-01。

## 5. 全局验收判据（本地复核口径）

- **AC-01 四类材料齐全**：契约冻结评审清单、评测扩容计划、法务书面结论输入清单、开口项全量索引四节标题均存在。
- **AC-02 六字段完整**：每个待签项均含状态、owner、验收判据、未闭合风险、真源锚点、待裁决动作。
- **AC-03 编号完整**：开口项全量索引覆盖 18 个编号，每个编号恰好出现一次。
- **AC-04 状态诚实**：evalset 关键字段与契约冻结状态未被改写；无伪造 owner / 法务 / 模型达标 / 压测结论。
- **AC-05 门禁不漂移**：CI 三入口判据常量未被本 change 修改，本地检查不接入 CI。
- **AC-06 注入反证**：删除任一开口项或篡改 `caseCount` 时本地检查必须转红，还原后转绿。
