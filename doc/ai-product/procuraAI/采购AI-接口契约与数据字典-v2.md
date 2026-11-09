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
| `match_status` | enum(`MATCHED`/`EXCEPTION`/`REVIEW`) | ✅ | 三单匹配结果状态 |
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

### VendorActivated（核心 OUTBOUND，生产方 = 01 MDM；topic `domain.procurement.vendor_activated.v1`）

| 字段 | 类型 | 必填 | 语义 / 约束 |
|------|------|------|-------------|
| `vendor_id` | string | ✅ | Vendor 主键；消费幂等关联键 |
| `supplier_id` | string | ✅ | 关联 Supplier（Supplier/Vendor 分离） |
| `legal_entity_id` | string | ✅ | 合作法人 |
| `company_code` | string | ✅ | 公司代码（ERP 映射） |
| `activated_at` | date-time | ✅ | 激活时间 |
| `purchasing_org` | string | ⚠️ 可空 | 采购组织 |

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
| 留存 | 审查快照、人工确认人/时间、override 记录按 `03 卷 §13.6` 保留期归档 |

**负向用例（注入必红 / 还原必绿）**

| # | 注入 | 期望 |
|---|------|------|
| AI-1 | `risk_level=LOW` + `confirmed_by=null` 请求置终态 | 拒绝 + 审计告警；状态不变 |
| AI-2 | `ai_available=UNKNOWN` 被映射为 `LOW` 后触发已撤销的自动归档路径（**禁止**） | 契约测试失败，隔离告警 |
| AI-3 | payload 携带 `output` 原文含身份证号 | 入口拒绝 + PII 告警 |
| AI-4 | `model_version_snapshot` 有值但 `ai_available=false`，被当作 AI 参与证据 | 判定为「AI 未确认」，fail-closed |

> AI-1 同步登记为 `03 卷 §11` 的 M3-13；A10 守护「本文件 §3 与 03 卷 M3-13 同时存在」。

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
