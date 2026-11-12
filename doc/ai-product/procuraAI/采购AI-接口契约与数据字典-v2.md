# procuraAI 接口契约与数据字典 v2（D3-v2）

> **版本**：v0.3（2026-11-09）
> **状态**：**Proposal（AI 契约真源，待架构评审冻结）**
> **取代**：`采购AI-接口契约与数据字典.md`（D3-v1，原历史归档；其独有 OA/ERP 字段映射已并入本文 §1.2，原文件已移除，见 `文档资产登记与真源裁决.md §3`）
> **定位**：procuraAI 作为**采购域 AI 子能力**对采购域暴露的契约真源；不是独立产品的对外 API 文档。
> **上游**：`采购总体架构方案.md §5.1`（AI fail-closed）、`采购领域架构设计方案.md §6.2`（`ContractReviewed` 冻结 topic）、`detailed-design/00-总纲与统一约定.md §16 / §17 / 附录 A`。
> **门禁**：`tools/check_procura_alignment.py` **A10**（唯一 payload / 适配层映射 / 合规条款 / 版本戳）。

---

## 1. D3 → Procura 适配层映射表（取代「给 D3 补说明」）

诊断 P0-2/P0-3 指出 D3 与下游在端点前缀、错误体、码段、表名、字段类型上逐项不兼容，而 `总体 §12.1` 曾用「补 `contract_no`→`review_id` 关联说明 / 采购域新增 `31xxx` 段」在下游文档里反向修改上游。**本表把这类弥合统一收口为一张显式映射表，两侧都不改**：D3 保留历史原值，Procura 侧用适配层转换。

| 维度 | D3 历史值（只读，不再演进） | Procura 现行值 | 适配层动作 |
|------|------------------------------|----------------|------------|
| API 前缀 | `/api/v1/documents`、`/reviews`、`/alerts`、`/ask`、`/rules` | `/api/v1/procurement/<resource>`、`/api/v1/portal/<resource>`（`总纲 §17`、OpenAPI） | `AiReviewGateway` 做路径与资源名映射；**不对外暴露 D3 路径** |
| 成功响应体 | `{code,message,trace_id,data}` | `ApiResponse<T>` / `PageResult<T>`（HC-003） | 网关包装，保留 `trace_id` |
| 错误体 | 自定义 `{code,message}` | RFC 9457 `application/problem+json` + `ApiResponse<T>` 兼容（`总纲 §17.2`） | 错误码映射见 §4 |
| 错误码段 | `30xxx / 40xxx / 50xxx` | `31xxx`（`总纲 附录 A` 唯一目录） | 逐码映射，未映射者落 `31000` 通用域错误并告警 |
| `review_id` | `varchar(32) PK`（D3）；`BIGSERIAL PK`（旧技术方案，两者本就互斥） | `rv-<ULID>` 字符串（`03 卷 §6`） | 以字符串为跨域关联键；旧数值 ID 经 `legacy_ref` 映射表转换 |
| `contract_no` | 审查入参主键 / 集成主键 | **仅入参关联键**；风险结论挂 `review_id`（`03 卷 §6`） | 禁止把 AI 结论回写到 `contract_no` 字段（负向探针 M3-1） |
| 表前缀 | `review` / `review_snapshot` / `risk_item` / `rule` / `alert_task` | `t_proc_*`（`总纲 §18`） | AI 运行时内部表保持独立库；跨域只经事件与只读端口，**不共享表** |
| 必备列 | D3 自有审计列 | `总纲 §18.1` 八项必备列 | 采购域投影表按必备列建；AI 侧字段经事件 payload 显式声明 |
| 状态枚举 | `MERGING/PENDING_REVIEW/…/FINALIZED` | 采购域 `03 卷 §5` 状态机 | 事件只传 `risk_level` 与结论引用，不直接驱动采购域状态迁移 |
| 事件订阅 | `document.archived` / `contract.draft` / `contract.signed` / `payment.recorded` / `acceptance.recorded` | 采购域 `§7` 集成事件 + 8 字段信封 | 见 §5；**D3 无 `ContractReviewed`**，由本文件 §2 定义 |

> **纪律**：适配层是**单向的**（D3 历史值 → Procura 现行值）。任何变更只能改 Procura 侧或新增映射行；**禁止回头改 D3 文档来适配下游**（诊断 §4.2 裁决链第 5/6 条）。

### 1.1 AI 能力码 ↔ 采购模块码 显式映射（消除 C-C 双轨撞名）

> 诊断 C-C 指出：LLD 用单数字 **AI 能力码**（M1/M2/M4/M5/M7），本文件 §2 用双数字 **采购模块码**（M02/M04/M10），两套码表正交、文本近撞（如「M4 辅助评标」vs「M04 合同模块」），且无映射。下表把两者绑定，消除歧义。

| AI 能力码 | 能力 | 所属模块码（D3-v2 命名） | 模块中文 | LLD 落点 |
|-----------|------|--------------------------|----------|----------|
| M1 | 招标文件合规检测 | M02 | 寻源与准入 | 02 卷 §8 |
| M2 | 合同风险审查 | M04 | 合同风险绩效与AI | 03 卷 §8 |
| M4 | 辅助评标（**仅建议，禁 AI 授标/准入**） | M02 | 寻源与准入 | 02 卷 §8 |
| M5 | 合同问答 | M04 | 合同风险绩效与AI | 03 卷 §8 |
| M7 | 合同起草辅助 | M04 | 合同风险绩效与AI | 03 卷 §8 |
| M3 | 投标合规自查 / 供应商信用画像（P1） | M02 | 寻源与准入 | 02 卷 §8；旧族 PRD §5.4 |
| M6 | 投标响应助手：拆解派发 / 模板生成 / 评分预估 / 变更对比（P1） | M02 | 寻源与准入 | 02 卷 §8；旧族 PRD §5.6 |
| ORCHESTRATION | AI 编排治理域（三态 / 降级 / 披露 / 审计 / 对账） | M10 | procuraai-runtime | 06 卷 §8；03 卷 §8 |

> **M10 澄清（2026-11-10 复诊 P1-⑤）**：`M10 = procuraai-runtime` 是**采购模块码**，不是 AI 能力码。三态能力 / 降级 / 披露 / 审计 / 对账等编排治理用例在评测集中统一标 `capabilityCode=ORCHESTRATION`，**刻意不新增 M10 能力码**，避免与模块码撞名导致 C-C 双轨复发。
> **M4 口径裁决**：新族（`领域 §2`/`§9`、`02 卷 §8`）把 M4 定义为「辅助评标（仅建议，禁 AI 授标 / 准入）」；旧族 PRD §5.3 的「M4 履约到期预警」是**历史归档口径**，按 `文档资产登记与真源裁决.md` 的裁决链，**以新族 LLD + 本表为准**，旧族仅作受限引用，不得反向改写本表。
> **能力码 ↔ 评测用例双向追溯（真源 = 本表）**：本表 8 行是能力码的**唯一结构化真源**；`eval/ai-decision-evalset.json` 每条 case 必填 `capabilityCode`，`capabilityCodes` 登记 8 码且各有 ≥1 条用例。评测集校验器 `check_procura_evalset.py` 的 **A14 判据解析本表第一列**做「文档真源 ↔ capabilityCodes ↔ cases」三方比对：本表新增 / 删码而评测集未同步 → 转红。脚本内 `CAPABILITY_CODES` 常量只作**独立防误删下界**，不承担真源角色（避免「期望值来自被校验对象」的闭环自证）；CI blocking 守护。第三轮补充：**A14** 另校验本表主键唯一（重复行不得被 set 吞掉）；**A16** 校验每条 `groundTruthRef` 的 path#anchor 可解析到真实标题 slug / JSON 字段；**A17** 要求每个能力码至少 1 条正向对照 + 1 条负向用例（防「恒拒绝」退化实现拿高分）且 `testSymbol` 唯一。27 例 seed（18 负向 / 9 正向对照），仍为 `seededDraft`。

> **纪律**：AI 能力码（M1/M2/M4/M5/M7）与采购**模块**码（M02/M04/M10，见 `文档资产登记与真源裁决.md` 模块注册表）是两套正交码表，**不得混用**；本文及 LLD 提及 `Mxx` 时须显式标注是「AI 能力」还是「采购模块」，避免 C-C 复发。

### 1.2 与 OA/ERP 适配的字段映射（统一字典，从 D3-v1 归档件并入）

> 缓解多 ERP 口径不一：procuraAI 标准字段 ↔ 各源系统字段名映射。来源系统 Golden Record 见 `采购总体架构方案.md §238`。D3-v1 已移除，本表为其唯一保留内容。

| procuraAI 标准字段 | U8 | SAP | Oracle | 金蝶 |
|--------------------|----|-----|--------|------|
| contract_no | 合同编号 | ZKONNR | CONTRACT_NUM | 合同号 |
| amount | 合同金额 | ZAMOUNT | AMOUNT | 金额 |
| pay_date | 付款日期 | ZPAYDATE | PAY_DATE | 付款日期 |
| vendor_id | 供应商编码 | LIFNR | VENDOR_ID | 供应商编号 |

---

## 2. `ContractReviewed` 唯一 payload 定义（R3）

**这是全族唯一权威定义**。`02 卷 §7` 只作消费方投影声明（不重述 payload）；`03 卷 §7` 的速查视图必须与本表逐字段一致；`contracts/asyncapi.yaml` 由总纲附录 B 生成，其 `x-fields` 来自本定义经 `03 卷` 速查视图投影。

| 属性 | 值 |
|------|-----|
| `eventType` | `ContractReviewed` |
| `topic` | `domain.procurement.contract_reviewed.v1`（**领域 §6.2 冻结值，不得改名**） |
| 方向 | **INBOUND**，生产方 = `procuraai-runtime`（M10） |
| 消费方 | M02 寻源与准入（仅风险参考）、M04 合同（`ContractReviewLink`） |
| `schemaVersion` | `1.0` |
| `partition_key` | `tenant_id + review_id`（`总纲 §6` 双轨） |
| delivery | at-least-once；消费侧 `t_proc_processed_event(consumer_group, event_id)` 幂等 |
| 顺序 | 仅同 `partition_key` 成立；payload 带 `aggregate_version` 供乱序/旧版本丢弃 |
| 归属 | `INBOUND` + domain 段偏差，登记在 `总纲 §16.8`（L31 待裁决）；裁决前只以 `eventType + eventId + HMAC` 识别授权 |

**payload 字段（7 项，唯一定义）**

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `review_id` | string | ✅ | 幂等键；`rv-<ULID>`；跨域关联唯一标识 |
| `contract_no` | string | ✅ | 仅入参关联键；**不得作为结论回写目标** |
| `risk_level` | enum(`LOW`/`MEDIUM`/`HIGH`/`UNKNOWN`) | ✅ | `UNKNOWN` 不得压成「无风险」；消费方隔离告警 |
| `risk_items_hash` | string | ✅ | 风险明细摘要哈希；明细不进事件（PII / 体积） |
| `review_snapshot_ref` | string | ✅ | 审查快照引用（AI 运行时独立对象存储），采购域只存引用不存原文 |
| `ai_available` | enum(`AVAILABLE`/`UNAVAILABLE`/`UNKNOWN`) | ✅ | **唯一认可的 AI 参与证据来源**（LLM 节点自报）；三态须区分 参与/未参与/UNKNOWN（详见 §3「三态」条款），`UNKNOWN` 不得压成「未参与」；缺省 fail-closed 视为 `UNKNOWN` |
| `model_version_snapshot` | string | ⚠️ 可空 | 入口可填快照，**不得冒充参与证据**；版本串截断 128 字符 |

**禁止项**：不得回传 `output` 原文（PII 禁入）、不得回传供应商联系人/身份证/银行账号、不得携带合同正文全文。对应负向探针 `03 卷 §11` M3-2 / M3-3 / M3-9。

**消费方投影**（`02 卷 §7` 允许写法）：本卷只消费 `review_id / risk_level` 两个字段的子集，**不重述 payload 定义**；完整定义见本文件 §2。生成器据此只取 `03 卷` 的字段速查，避免诊断 P0-3 的「按卷序取简版」。

---

## 2.1 核心 / INBOUND 事件 payload schema 集（v0.3 补齐，续诊 O1 闭合 payload 空壳）

> **本集 = 核心事件字段级 schema 权威源**（派生物 `contracts/asyncapi.yaml` 的 `payload.properties` 由本集 + §2 生成，禁止手改）。`ContractReviewed` 字段 schema 见 §2（R3 唯一真源，不在此重复）。本集覆盖其余 **INBOUND 消费方期望 schema** 与 2 个最高频 **核心 OUTBOUND** 契约事件。字段类型为 `string / boolean / integer / decimal / date / date-time / Money / enum(...)`；`Money` 引用 `asyncapi.yaml#/components/schemas/Money`（金额与币种同行，§14）。状态：**L35 决议前冻结草案（Proposal）**，待 schema registry 选型后升为冻结。门禁 A8 校验核心事件 payload 非空壳。
>
> **payload 字段名唯一真源裁决（第十二轮，2026-11-11）**：本集字段名是核心事件 payload 的 **唯一字段名真源**。`总体 §7.1` 与各卷 §7「payload 字段速查」列必须与本集**逐字一致**，不得各自简写或保留互斥别名；`x-fields` 由各卷 §7 生成、`properties/required` 由本集生成，二者字段集合由门禁 **A10.19** 集合比对守护。按 `领域 §2.2.1` Supplier↔Vendor 分离：PO / 收货 / 发票主体统一用 `supplier_id`，付款与收款主体用 `vendor_id`；`vendor_id` 不再出现在 `PurchaseOrderCreated` / `InvoiceSubmitted` 的 payload 中。本集未覆盖的事件（如 `SupplierChanged` / `AsnSubmitted` / `RiskAlertRaised`）仍以 `总体 §7.1` 为字段真源，各卷 §7 同步登记。金额一律 `Money`（金额与币种同行，禁裸数字，§14 D-01–D-03）。

### GoodsReceived（INBOUND，生产方 = Inventory ACL；topic `domain.procurement.gr_recorded.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `gr_no` | string | ✅ | 收货单号；消费幂等关联键 |
| `po_no` | string | ✅ | 关联采购单 |
| `po_line_no` | integer | ✅ | 采购单行号 |
| `received_qty` | decimal | ✅ | 实收数量（正数）；单位见 `uom` |
| `uom` | string | ✅ | 计量单位（ISO 80000 / 企业主数据） |
| `received_at` | date-time | ✅ | 收货时间（ISO-8601 UTC） |
| `site_id` | string | ✅ | 收货地点（Inventory 域 Site） |
| `tenant_id` | string | ✅ | 租户隔离键 |
| `supplier_id` | string | ⚠️ 可空 | 供应商（直运场景可缺省） |
| `asn_no` | string | ⚠️ 可空 | 预约送货单号（无 ASN 场景可缺省；字段名以此为准） |

### InvoiceSubmitted（INBOUND，门户提交经 Finance ACL；topic `domain.procurement.invoice_submitted.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `invoice_no` | string | ✅ | 发票号；消费幂等关联键 |
| `po_no` | string | ⚠️ 可空 | 关联采购单（服务类发票可能无 PO） |
| `supplier_id` | string | ✅ | 开票供应商 |
| `invoice_amount` | Money | ✅ | 发票金额（币种同行） |
| `tax_amount` | Money | ⚠️ 可空 | 税额 |
| `invoice_date` | date | ✅ | 开票日期 |
| `due_date` | date | ⚠️ 可空 | 到期日 |
| `currency` | string | ✅ | ISO 4217 币种（与 `invoice_amount.currency` 一致） |

### InvoiceMatched（INBOUND 只读，生产方 = Finance ACL；topic `domain.procurement.invoice_matched.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `match_id` | string | ✅ | 匹配单号；消费幂等关联键 |
| `invoice_no` | string | ✅ | 关联发票 |
| `po_no` | string | ✅ | 关联采购单 |
| `match_status` | enum(`MATCHED`/`EXCEPTION`/`REVIEW`) | ✅ | 三单匹配结果状态；**缺失/非法值 fail-closed，不得推断为 `MATCHED`**，消费方须转人工复核（与同节 fail-closed 纪律一致；精确目标枚举值映射待 owner 裁决） |
| `matched_amount` | Money | ✅ | 匹配金额 |
| `exception_code` | string | ⚠️ 可空 | 例外码（EXCEPTION 时必填） |
| `matched_at` | date-time | ✅ | 匹配时间 |

### PaymentRecorded（INBOUND，生产方 = Finance ACL；topic `domain.procurement.payment_recorded.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `payment_id` | string | ✅ | 付款流水号；消费幂等关联键 |
| `invoice_no` | string | ✅ | 关联发票 |
| `paid_amount` | Money | ✅ | 实付金额 |
| `payment_date` | date-time | ✅ | 付款时间 |
| `payment_method` | enum(`BANK_TRANSFER`/`CHECK`/`OTHER`) | ⚠️ 可空 | 付款方式 |
| `payer_legal_entity` | string | ✅ | 付款法人（Finance 域 LegalEntity） |

### PurchaseOrderCreated（核心 OUTBOUND，生产方 = 04 交易；topic `domain.procurement.po_created.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `po_no` | string | ✅ | 采购单号；消费幂等关联键 |
| `tenant_id` | string | ✅ | 租户隔离键 |
| `supplier_id` | string | ✅ | 供应商 |
| `total_amount` | Money | ✅ | 订单总额（币种同行） |
| `currency` | string | ✅ | ISO 4217 币种 |
| `po_version` | integer | ✅ | 订单版本戳（下达/变更累加） |
| `created_at` | date-time | ✅ | 创建时间 |
| `requisition_no` | string | ⚠️ 可空 | 来源请购单 |
| `legal_entity_id` | string | ✅ | 合作法人（多法人隔离，领域 §2.2.1） |
| `lines` | array | ✅ | PO 行数组（行内 `net_price` 为 Money，禁裸数字） |
| `contract_no` | string | ⚠️ 可空 | 关联合同号（仅入参关联键，非结论回写目标） |
| `price_snapshot_ref` | string | ⚠️ 可空 | 价格快照引用 |

### VendorActivated（核心 OUTBOUND，生产方 = 01 MDM；topic `domain.procurement.vendor_activated.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `vendor_id` | string | ✅ | Vendor 主键；消费幂等关联键 |
| `supplier_id` | string | ✅ | 关联 Supplier（Supplier/Vendor 分离） |
| `legal_entity_id` | string | ✅ | 合作法人 |
| `company_code` | string | ✅ | 公司代码（ERP 映射） |
| `activated_at` | date-time | ✅ | 激活时间 |
| `purchasing_org` | string | ⚠️ 可空 | 采购组织 |
| `payment_terms` | string | ⚠️ 可空 | 付款条件（Vendor 财务主体侧，非 Supplier 主档字段） |
| `bank_account_ref` | string | ⚠️ 可空 | 银行账号引用（只存引用，禁账号明文入事件） |

### PaymentRequested（核心 OUTBOUND，生产方 = 04 交易；topic `domain.procurement.payment_requested.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `po_no` | string | ✅ | 采购单号 |
| `invoice_no` | string | ✅ | 关联发票 |
| `vendor_id` | string | ✅ | 收款 Vendor（财务主体；Supplier↔Vendor 分离） |
| `legal_entity_id` | string | ✅ | 付款法人 |
| `amount` | Money | ✅ | **请求付款金额**；金额与币种同行，禁裸数字（§14 D-01–D-03）；采购域只发请求，付款裁决归 Finance |

---

## 3. AI 合规契约（R2，取代已撤销的 ADR-06）

| 条款 | 契约 |
|------|------|
| 决策权 | **AI 仅辅助，人工最终决策**。AI 输出是建议与证据引用，不是结论终态 |
| 自动结案 | **禁止**。任何风险等级（含 `LOW`）在 `confirmed_by` 为空时不得置 `FINALIZED` / `ARCHIVED` / `CLOSED`；旧 `ADR-06`「低置信度/低风险自动归档」**已撤销**（`文档资产登记与真源裁决.md §5`） |
| 三态 | `ai_available` 必须区分「参与 / 未参与 / **UNKNOWN**」；`UNKNOWN` 不得展示为「AI 未参与」，前端显示「无法确认」并保留原始码 |
| 降级 | AI 不可用降级为纯规则引擎并标 `rules_only`；**不得跳过人工法审**直接置 `LEGAL_REVIEWED`（M3-6） |
| 版本溯源 | `model_version_snapshot` 与规则库版本进证据链；升版须用固定评测集复跑（M3-12） |
| PII | 证据不回传 `output` 原文；日志/事件/快照按 `总纲 §20` 脱敏 |
| 算法公平与反歧视 | AI 辅助供应商筛选 / 评标 / 推荐**不得对特定供应商群体（所有制 / 地域 / 规模等）产生系统性偏见**；上线前与定期做偏见检测与公平性审计，命中歧视性偏差立即 fail-closed 并回滚模型版本、留伦理审查记录（中物联《人工智能+采购供应链应用指南》§四(五) / §六(一)） |
| 留存 | 审查快照、人工确认人/时间、override 记录按 `03 卷 §13.6` 保留期归档 |
| 射程分层 | `法源射程分层与自治边界-ADR.md` 是法源射程与自治档位专项真源：招标射程内（M1/M3/M4）按 195 号文三（十一）法定辅助性定位 `ADVISORY_ONLY` 不可稀释；非招标域在 F1–F4 + 预授权护栏下可有界自治执行**动作**，授标 / 结案 / 放款 / 风险条目终态仍 100% 人工终决。195 号文（`§3.3` B-1–B-5 `conditionalBiddingScope`）**不得用作全域治理依据**；本节六条红线两档共同适用，不因本条放宽 |
| 不可信输入隔离 | 合同 / 投标文件 / 发票 / 证照是**攻击者可控的不受信任输入**：① 系统提示与用户输入隔离，外部文本不得进入指令通道；② 工具最小权限白名单，AI 不得触发写 / 放款 / 授标工具；③ 输出经确定性规则复核后方可展示；④ 检出隐藏指令 / 越狱即 fail-closed 转人工并留审计。**推理期防御**区别于 `§3.2 E-3` 的「摄入层拒收污染样本」；能力码与关键负向用例见 `AI接入判定与AI原生内核补充设计.md §4.4`、评测集 `PROMPT-INJ-01` / `PROMPT-INJ-02` |

**负向用例（注入必红 / 还原必绿）**

| # | 注入 | 期望 |
|---|------|------|
| AI-1 | `risk_level=LOW` + `confirmed_by=null` 请求置终态 | 拒绝 + 审计告警；状态不变 |
| AI-2 | `ai_available=UNKNOWN` 被映射为 `LOW` 后触发已撤销的自动归档路径（**禁止**） | 契约测试失败，隔离告警 |
| AI-3 | payload 携带 `output` 原文含身份证号 | 入口拒绝 + PII 告警 |
| AI-4 | `model_version_snapshot` 有值但 `ai_available=false`，被当作 AI 参与证据 | 判定为「AI 未确认」，fail-closed |
| AI-5 | 评测集注入「按供应商所有制 / 地域 / 规模系统性降分」的模型输出 | 偏见检测命中 → fail-closed 拒绝上线 + 伦理审查告警，模型版本不升 |

> AI-1 同步登记为 `03 卷 §11` 的 M3-13；A10 守护「本文件 §3 与 03 卷 M3-13 同时存在」。
> AI-5 与「算法公平与反歧视」条款由 **A10.3.2 守护**（条款与负向用例不得删除，删除即门禁转红）。

---

### 3.1 合规依据适用性与工程化清单（2026-11-10 复诊 P1-⑦）

> **适用性裁决**：本文及新族文档早期把「监管 §xx」统一挂到金监总局《关于银行业保险业人工智能安全开发应用的指导意见》，该文件是**银行业保险业专用**，不能作为通用采购系统的合规基线。采购域通用基线改为：中物联《人工智能+采购供应链应用指南》（物联采字〔2025〕217 号）、发改法规〔2026〕195 号、《人工智能生成合成内容标识办法》（2025-09-01 施行）、《生成式人工智能服务管理暂行办法》、NIST AI RMF / ISO 42001；**金融行业客户另叠加**金监总局指导意见，不混作通用条款。早期文档沿用的「监管 §4/§15/§17/§21/§22/§23/§24」来自 synap-claim 理赔域，属**银行业保险业专用条款号误植**，不得再作为采购域依据；本表依据列的「《指南》四(二)」等是公开文件原文章节号，`C-1`–`C-8` 是本文内部清单编号，两类编号**不得混用**。

> **第七轮射程校准（2026-11-11）**：上列通用基线中的《生成式人工智能服务管理暂行办法》须**移出**——其第 2 条第三款逐字规定「行业组织、企业、教育和科研机构、公共文化机构、有关专业机构等研发、应用生成式人工智能技术，未向境内公众提供生成式人工智能服务的，不适用本办法的规定」；企业内部采购系统不向境内公众提供服务，属**法源明文排除**情形（见 §3.3 B-13/B-14），C-2 因此改列内部发布检查单基线，不再借外部条款背书。发改法规〔2026〕195 号虽在抬头列有「各中央企业」，但总体目标限定「围绕招标投标交易全过程和管理重点环节」「保障公共资源公平高效配置、规范招标投标市场秩序」，三（十一）主体明确为招标人 / 招标代理机构 / 投标人 / 评标专家，三（十二）要求对象是「各地」⇒ **射程是招标投标活动**，仅在该射程内作指导性依据，**不覆盖企业非招标采购的合同审查 / 供应商画像 / 智慧问答 / 发票与履约绩效**（§3.3 标 `conditionalBiddingScope`）。行业组织指南与指导性意见均不等于强制法定义务；本族适用结论最终以法务为准。

| # | 合规义务 | 依据 | 可验收动作（owner 执行） | 状态 |
|---|----------|------|--------------------------|------|
| C-1 | 生成合成内容**显式 + 隐式元数据双标识** | 《标识办法》第 4/5 条（2025-09-01 施行，见 §3.3 B-6/B-7）；**第 2 条主体限定**「网络信息服务提供者」，企业内部采购系统是否强制适用待法务确认，本族按设计态**自愿采纳** | M7 起草稿 / M2 审查报告 / M5 问答输出 / **M6 投标响应草稿**均带显式标识 + 文件元数据标记（生成合成内容不因能力码豁免）；缺隐式标识禁止出域；评测集 `LABEL-01` 注入必红、`POS-M6-01` 正向对照 | 契约已定义，代码未实现 |
| C-2 | 算法备案 / 安全评估 | **无外部强制依据**：《生成式人工智能服务管理暂行办法》第 2 条第三款明确排除未向境内公众提供服务的企业研发应用（见 §3.3 B-13），其第 17 条的模型备案 / 安全审核义务对本族**不成立**（B-14）；195 号文三（十二）同类要求射程限于招标投标活动（B-3，`conditionalBiddingScope`）。C-2 因此是**内部发布检查单基线**，不是外部法定义务；若客户为央国企且场景落入依法必须招标项目，须由法务另行确认 | 上线前完成备案材料清单 + 安全评估报告，纳入发布检查单 | owner 待办 |
| C-3 | 数据分类分级 + PII 禁入 | 《指南》§四(二)、§六(一)（见 §3.3 B-8/B-10）；**原挂 §六(二)「加强风险韧性管理」与 PII / 数据分级无关，2026-11-11 依公开原文纠正** | 采购数据分级目录（公开/内部/敏感/机密）、LLM 入参脱敏网关、评测集 `piiFree` 全 true + `AI-3` 负向 | 脱敏契约已有，分级目录待 owner |
| C-4 | AI 专项应急预案 + **全域 kill switch** | 《指南》§六(三)（见 §3.3 B-12）；195 号文三（十二）「有效防范和应对模型黑箱、幻觉和算法歧视等风险」；**统一停用开关属内部工程细化，非条款原文义务** | 覆盖 M1–M7 与编排层的统一停用开关、降级演练、RTO/RPO 与责任人；现仅 G16 自治车道有 kill switch，**须扩到全域** | 未闭合 |
| C-5 | 第三方算法审计 / 偏见定期复检 | 《指南》§六(一)「定期开展第三方审计，保障人工智能应用安全合规」（见 §3.3 B-10/B-11） | 年度第三方审计 + 季度公平性复跑（`FAIR-01/02`），报告归档 | owner 待办 |
| C-6 | 模型 / 提示词漂移闭环 | 195 号文三（十）「持续迭代优化」（见 §3.3 B-5）；《指南》§六(一)；Gartner 2026 Decision Governance（业界框架，非法规） | 升版固定评测集复跑（M3-12）+ override 率监控 + 自动回滚 | 03 卷 §13 有设计，未实现 |
| C-7 | AI 参与告知 + 人工复核权 + 显著标识 | **告知 / 人工复核权**：195 号文三（十一）辅助性定位的**原则延伸**（原文无此条款义务，见 §3.3 B-2）；**显著标识**：《标识办法》第 4/5 条（B-6/B-7，主体适用性同上） | 披露三态 `UNDETERMINED` 不得压成「AI 未参与」；前端展示标识与复核入口；`DISCLOSE-01/02` | 契约已有，前端未实现 |
| C-8 | AI 应用清单 / 高风险场景审批 | 《指南》§四(五)「建立人工智能应用伦理审查机制，对高风险场景的人工智能模型进行事前评估和事后监督，引导行业健康、负责任地发展人工智能。」（公开原文，见 §3.3 B-15）；同节偏见句见 B-9；金融客户叠加金监总局 §16（不混作通用） | 逐能力登记 AI 应用清单与风险等级，高风险场景审批留痕 | owner 待办 |

> **门禁**：A10.14 守护本节适用性声明与 C-1–C-8 八条义务的关键可验收动作不得删除；判据常量由门禁脚本**硬编码为外部锚点、不在本节复述**（避免「期望值也来自被校验文档」的闭环自证，见 R5 纪律），注入删除任一即转红。
> **边界**：本表是**设计态合规清单**，列的是「要做什么 / 怎么验收」，**不代表已完成**；不得据此宣称合规已达标。

### 3.2 评测集行为枚举与真源落点（2026-11-10 第五轮）

> **新增动机**：`eval/ai-decision-evalset.json` 的期望值里存在 `ADVISORY_ONLY` / `NO_BLOCKER_FOUND` / `COMPARISON_SUMMARY` / `PII_OR_ADVERSARIAL_REJECTED` 等评测集侧枚举，此前在全族文档**无字面真源**；`ANOMALY-02` 的 `groundTruthRef` 甚至指向评测集自身字段，构成「期望值来自被校验对象」的闭环自证。本节把行为枚举收敛为**契约真源**：评测集只引用、不自定义；代码落地后本节须与源码枚举类双向对齐。

| # | 枚举组 | 允许值 | 契约语义（fail-closed） | 状态 |
|---|--------|--------|------------------------|------|
| E-1 | 分诊档位 `tier` | `ADVISORY_ONLY` / `MANUAL` | `ADVISORY_ONLY` 只给建议与证据，`humanFinalDecision` 恒为 `true`；`MANUAL` 表示材料 / 信号不足或规则冲突，转人工处置。`AUTO` 档在本族**不存在**，任何自动结案 / 自动授标 / 自动放款档位一律拒绝 | 契约已定义，代码未实现 |
| E-2 | 审查结果 `result` | `REVIEW_COMPLETED_ADVISORY` / `NEED_MORE_EVIDENCE` / `MANUAL_REQUIRED` / `NO_BLOCKER_FOUND` / `RISK_PROFILE_READY` / `COMPARISON_SUMMARY` / `ANSWER_WITH_CITATION` / `DRAFT_GENERATED` / `DRAFT_WITH_CLAUSE_SOURCE` | 全部是「建议 / 摘要 / 草稿」态，不是结论终态；`NO_BLOCKER_FOUND` 仅表示体检未发现阻断项，**不等于合规通过或自动授标**；`COMPARISON_SUMMARY` 只给对比摘要，不给排序定标结论 | 契约已定义，代码未实现 |
| E-3 | 摄入拒收 `reason` | `PII_OR_ADVERSARIAL_REJECTED` | 外部注入 / 未受控 / 含 PII 的污染样本在**数据摄入与标注层拒收**，不进训练 / 验证 / 冻结任一集合；内部受控构造的红队样本按 `PRD` 第 9.3 节与 `技术方案` 第 7.1 节 L5 **入评测集对抗层**并季度重标。两者是「来源管控」与「评测设计」的分工，不矛盾 | 契约已定义，代码未实现 |
| E-4 | 缺省与三态 | `UNKNOWN` / `UNDETERMINED` / `rules_only` | 能力码缺省视为 `UNKNOWN`；AI 参与态 `UNDETERMINED` 不得压成「AI 未参与」；降级标 `rules_only` 且不得跳过人工法审。三者都不得静默转成放行 | 契约已定义，代码未实现 |
| E-5 | 生成内容标识 `labels` | `EXPLICIT` / `IMPLICIT` | 生成合成内容须同时带显式标识与隐式元数据标识，缺一禁止出域 | 契约已定义，代码未实现 |

> **门禁与边界**：评测集 `groundTruthRef` 须指向本节或本节之外的**文档真源**，不得再指向 `eval/ai-decision-evalset.json` 自身字段（A16 守护）；本节枚举增删须同步评测集 `expected` 与 CI blocking 评测门禁。`status=seededDraft`、`scalePlan.status=notStarted` 不变，本节**不代表模型效果已测评**。

### 3.3 外部法规原文核对表（2026-11-11 第六轮建立 / 第七轮适用射程校准）

> **新增动机**：§3.1 依据列此前是「文件名 + 概括性章节」，既未核条款原文、也未区分「原文直引」与「内部断言借法规背书」；`eval/ai-decision-evalset.json` 的 `clauseRef` 又多为 `D3-v2 §3 决策权` 这类内部引用。本节按公开一手来源逐条核对，作为 **13 条 `externalRegulation` 主依据的唯一原文真源**：评测集只引用、不自定义，A13 解析本表并守护逐字一致。
>
> **口径约定（第七轮射程校准）**：摘录为**公开原文的连续片段**，允许截句但不得改字；核验日期 `2026-11-11`，来源 URL 均为政府 / 行业组织公开页面。「适用性」列是**法源射程判定**，不是合规达标结论，五值受控枚举：
> - `inScope` = 行业指南 / 条款覆盖采购供应链全流程且主体无争议（仅中物联《指南》B-8–B-12 / B-15）；**不表示法规具有强制力**，《指南》是行业组织指导性文件。
> - `conditionalBiddingScope` = 195 号文 B-1–B-5：原文抬头虽含「各中央企业」，但总体目标限定「围绕招标投标交易全过程和管理重点环节」，三（十一）主体明确为招标人 / 招标代理机构 / 投标人 / 评标专家，三（十二）要求对象是「各地」⇒ **射程是招标投标活动**，不覆盖企业非招标采购的合同审查、供应商画像、智慧问答、发票与履约绩效。挂这些行的评测用例须在 `applicabilityNote` 写明「落在招标场景内」或「仅作辅助性定位原则借鉴」。
> - `voluntaryAdoption` = 《标识办法》B-6/B-7：第 2 条把义务主体限定为「网络信息服务提供者」，企业内部采购系统是否强制适用**待法务确认**，本族按设计态自愿采纳双标识。
> - `excludedByScope` = 《生成式人工智能服务管理暂行办法》B-13/B-14：**第 2 条第三款明文排除**「未向境内公众提供生成式人工智能服务」的企业研发应用，故第 17 条的备案 / 安全评估义务对本族**不成立**；这两行只作排除性证据，**不得作为任何评测用例的主依据**（A13 守护）。
> - `pendingLegalReview` = 保留给后续新增条目。
>
> **不臆造纪律**：C-2 的「模型备案与安全评估」在排除《暂行办法》第 17 条后**没有外部强制依据**，改列为内部发布检查单基线，不借法规背书；§3.1 适用性裁决段据此把《暂行办法》从采购域通用基线中移出。§3.1 C-8 引用的《指南》四（五）伦理审查句已按公开原文单列 **B-15** 并纳入 A13 逐字守护（三个独立公开来源复核一致）。金监总局指导意见是**银行业保险业专用**，按 §3.1 适用性裁决不进通用基线。`sourceAliases` 中保留的《暂行办法》别名仅供历史裁决可追溯，**不得据以挂载 `externalRegulation` 主依据**。

| # | 法源与条款定位 | 逐字原文摘录（连续片段） | 公开来源 URL | 核验日期 | 适用性 | 映射 |
|---|----------------|--------------------------|--------------|----------|--------|------|
| B-1 | 195 号文 二（一）3「招标文件检测」 | 「鼓励实行招标文件“先体检、再发布”。」 | `https://www.ndrc.gov.cn/xwdt/tzgg/202602/t20260210_1403681.html` | 2026-11-11 | conditionalBiddingScope | BIDDOC-01 / POS-M1-01（M1 招标文件体检） |
| B-2 | 195 号文 三（十一）「健全应用机制」 | 「坚持技术的辅助性定位，模型生成的结论不替代招标人、招标代理机构、投标人、评标专家等的自主判断，不改变使用主体的法定责任。」 | `https://www.ndrc.gov.cn/xwdt/tzgg/202602/t20260210_1403681.html` | 2026-11-11 | conditionalBiddingScope | CONTRACT-01 / DISCLOSE-01/02 / POS-M1-01；C-7 原则延伸 |
| B-3 | 195 号文 三（十二）「提升安全水平」 | 「严格落实人工智能模型安全管理要求，强化模型算法、数据资源、基础设施、应用系统等安全能力建设，严格开展算法、模型备案和安全审核。」 | `https://www.ndrc.gov.cn/xwdt/tzgg/202602/t20260210_1403681.html` | 2026-11-11 | conditionalBiddingScope | C-2 / C-4；FAIR-01 辅助依据 |
| B-4 | 195 号文 三（九）「夯实数据基础」 | 「各地要加强招标投标数据治理，强化数据清洗和标注，加快构建涵盖招标投标政策法规和全流程各环节的高质量数据集和知识库，依托政务数据共享机制，推进高质量数据集的共建共享和生成数据的归集治理，更好支撑模型训练和应用。」 | `https://www.ndrc.gov.cn/xwdt/tzgg/202602/t20260210_1403681.html` | 2026-11-11 | conditionalBiddingScope | 地基层 F1；ANOMALY-02 辅助依据 |
| B-5 | 195 号文 三（十）「持续迭代优化」 | 「各地要建立人工智能模型常态化升级机制，及时更新数据集和知识库，运用招标投标专业数据进行针对性训练，不断优化模型算法，提升模型精准度。」 | `https://www.ndrc.gov.cn/xwdt/tzgg/202602/t20260210_1403681.html` | 2026-11-11 | conditionalBiddingScope | C-6 漂移闭环 |
| B-6 | 《标识办法》第 4 条第二款（显式标识） | 「服务提供者提供生成合成内容下载、复制、导出等功能时，应当确保文件中含有满足要求的显式标识。」 | `https://www.gov.cn/zhengce/zhengceku/202503/content_7014286.htm` | 2026-11-11 | voluntaryAdoption | LABEL-01 / POS-M6-01 / POS-M7-01；C-1 |
| B-7 | 《标识办法》第 5 条第一款（隐式标识） | 「服务提供者应当按照《互联网信息服务深度合成管理规定》第十六条的规定，在生成合成内容的文件元数据中添加隐式标识，隐式标识包含生成合成内容属性信息、服务提供者名称或者编码、内容编号等制作要素信息。」 | `https://www.gov.cn/zhengce/zhengceku/202503/content_7014286.htm` | 2026-11-11 | voluntaryAdoption | C-1（与 B-6 共同构成双标识） |
| B-8 | 中物联《指南》四（二）「强化数据要素支撑」 | 「严格落实数据安全、信息保护等相关法律法规要求，强化数据脱敏、匿名化处理，规范数据采集、存储、使用和共享行为，防范数据污染，提高数据质量。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | QA-01 / POS-M5-01 / ANOMALY-02；C-3 |
| B-9 | 中物联《指南》四（五）「完善伦理治理机制」 | 「加强算法偏见检测与缓解，确保人工智能决策的公平性，防止对特定供应商或群体造成歧视。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | FAIR-01；C-8 同节原则延伸 |
| B-10 | 中物联《指南》六（一）「构建应用安全防护体系」首段 | 「建立算法安全审查机制，对采购供应链领域人工智能模型进行偏见检测、稳定性测试与安全性评估，防范算法歧视与决策偏差。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | FAIR-02；C-3 / C-5 |
| B-11 | 中物联《指南》六（一）「构建应用安全防护体系」末段 | 「定期开展第三方审计，保障人工智能应用安全合规。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | C-5 第三方审计 |
| B-12 | 中物联《指南》六（三）「构建应急响应策略」 | 「针对算法偏见、数据泄露、数据污染、机器幻觉等模型特有风险，制定人工智能模型专项检测与应急处置预案，确保风险事件能快速响应、有效隔离和妥善恢复，确保模型运行的可靠性、连续性与安全性。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | C-4 应急预案 |
| B-13 | 《生成式人工智能服务管理暂行办法》第 2 条第三款（适用排除） | 「行业组织、企业、教育和科研机构、公共文化机构、有关专业机构等研发、应用生成式人工智能技术，未向境内公众提供生成式人工智能服务的，不适用本办法的规定。」 | `https://www.gov.cn/zhengce/202311/content_6917778.htm` | 2026-11-11 | excludedByScope | C-2 排除性证据（不得作主依据） |
| B-14 | 《生成式人工智能服务管理暂行办法》第 17 条（备案 / 安全评估） | 「提供具有舆论属性或者社会动员能力的生成式人工智能服务的，应当按照国家有关规定开展安全评估，并按照《互联网信息服务算法推荐管理规定》履行算法备案和变更、注销备案手续。」 | `https://www.gov.cn/zhengce/202311/content_6917778.htm` | 2026-11-11 | excludedByScope | C-2 排除性证据（不得作主依据；因 B-13 射程排除） |
| B-15 | 中物联《指南》四（五）「完善伦理治理机制」 | 「建立人工智能应用伦理审查机制，对高风险场景的人工智能模型进行事前评估和事后监督，引导行业健康、负责任地发展人工智能。」 | `http://chinawuliu.com.cn/upload/resources/file/2025/12/01/188718.pdf` | 2026-11-11 | inScope | C-8 应用清单 / 高风险场景审批 |

> **判据与边界**：A13 要求每条 `externalRegulation` 的 `basisVerification.clauseId` 命中本表且**不得命中 `excludedByScope` 行**、`quote` 与本表摘录**逐字相等**、`sourceUrl` / `verifiedOn` / `applicability` 与本表一致；`principleExtension` 与非 `inScope` 用例须附 `applicabilityNote`。A13 **只守主依据的原文精确性、射程判定与引用方向**，不守辅助依据、不守 25 条 `clauseRef` 与内部断言的逐条语义对应，也不替代法务对主体适用性 / 场景射程的结论。本表是**设计态合规核对**，不代表备案 / 审计 / 标识已实现。

---

## 4. 错误码映射（D3 → 附录 A）

只做**映射声明**，不新增任何错误码（A8 守护 43 条目录不变）。

| D3 段 | 含义 | Procura 映射 |
|-------|------|--------------|
| `30xxx` | 参数 / 校验 | `31101`–`31199` 段（按 `总纲 附录 A` 逐项映射）或 `400/422` 通用 HTTP 语义 |
| `40xxx` | 认证 / 授权 / 状态 | `312xx` / `313xx` 段；`401/403/409` 通用语义优先 |
| `50xxx` | AI 运行时 / 降级 | `313xx`（AI 治理）与 `5xx` 可重试语义；降级须带 `rules_only` 标记 |
| 未映射 | — | 落 `31000` 通用域错误 + 告警工单，**禁止静默吞掉** |

> 完整 43 条编号以 `总纲 附录 A` 与 `openapi.yaml x-error-catalog` 为唯一真源（A8 双向校验）；本表不复制编号清单，避免第三真源。

---

## 5. 事件订阅（外部域 INBOUND，沿用 D3 历史值）

| D3 事件 | 生产方 | 采购域动作 | 幂等 |
|---------|--------|------------|------|
| `document.archived` | OA | 触发招标文件体检（M1） | `doc_id` + 版本 |
| `contract.draft` | OA（用印/定稿） | **签署前**触发审查 + 三一致校验 | `contract_no + doc_id` |
| `contract.signed` | OA | 归档 + 建台账 + 履约预警启动；重复审查幂等跳过 | `contract_no + sign_date` |
| `payment.recorded` | ERP 财务 | 履约节点消警 | `contract_no + pay_date` |
| `acceptance.recorded` | ERP/验收 | 验收节点消警 | `contract_no + item + date` |

- 传输：Webhook（HMAC 验签，四类负向注入见 `06 卷 §11`）或 15min 增量轮询降级。
- 这些是**外部域生产**的事件，采购域只订阅，不生产、不登记进总纲附录 B 的采购域注册表；路由与授权只认 `eventType + eventId + HMAC`。
- 与跨域契约的完整边界见 `跨域契约与外部域依赖.md`。

---

## 6. 版本戳与待决

- 本文件版本 `v0.3`，与 LLD 族、契约工件 `0.3.0`、SVG 版本戳一致（A10 守护）。
- **待决**：DA-1 旧 D3 数值型 `review_id` 的存量数据迁移窗口；DA-2 `30xxx/40xxx/50xxx` 逐码映射表的完整补全（需 D3 全量码表）；DA-3 AI 评测集规模与通过线（沿用 L41）。
- **不定义**：AI 运行时内部表 DDL（属 M10 独立库，不在采购域 `bone-init.sql` 范围）；独立产品对外 API（产品定位已裁决为子能力）。
