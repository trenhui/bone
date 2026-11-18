# procurement/invoice-submission Specification

## Purpose
定义供应商自助开票（810 语义类比）的行为契约。该能力以 PO 与确认/收货投影为上游，保证发票号去重、金额不超 PO 未开票余额、TDS 金额自洽，并严格区分「语法回执」与「业务接受」——采购域只提交发票、只回写匹配结果，不裁决三单匹配与放行。

## Requirements

### Requirement: 发票去重（INV-07 / M5-2）
系统必须以 `(tenant_id, invoice_no, vendor_id)` 作为发票唯一键。同一供应商重复提交相同发票号必须被去重，不产生第二条提交记录。

#### Scenario: 重复发票号被去重
- **WHEN** 同一租户、同一供应商重复提交相同 `invoice_no`
- **THEN** 系统返回错误码 `31502` 且只保留一条发票提交记录

#### Scenario: 不同供应商相同发票号允许
- **WHEN** 两个不同供应商提交相同 `invoice_no`
- **THEN** 系统视为两条独立发票并分别保存（去重键含 `vendor_id`）

### Requirement: 发票金额不超未开票余额（INV-08 / M5-5）
系统必须校验发票金额不超过所引用 PO 的未开票余额（本域投影）。超出时必须拒绝提交。

#### Scenario: 发票金额超未开票余额
- **WHEN** 提交的发票金额大于引用 PO 的未开票余额
- **THEN** 系统拒绝并返回错误码 `31504`，发票状态不变、不产生提交记录

#### Scenario: 金额在余额内可提交
- **WHEN** 提交的发票金额等于或小于引用 PO 的未开票余额
- **THEN** 系统接受并创建发票提交，初始状态 `DRAFT`

#### Scenario: 金额与币种按 Money 语义保存
- **WHEN** 发票金额形如 `4250.00`、币种 `CNY`
- **THEN** 系统以四位小数保存金额并随行保存币种，不出现浮点漂移

### Requirement: TDS 金额自洽校验（M5-12）
系统必须校验发票 TDS 等于各行金额按容差配置汇总的结果（Σ IT1 ± SAC，逐行舍入后汇总）。超出按供应商/品类配置的容差时必须拒收并生成例外单。容差必须可配置，禁止硬编码阈值。

#### Scenario: TDS 与行合计一致
- **WHEN** 发票 `TDS` 与逐行金额汇总的差额在配置容差内
- **THEN** 系统接受发票提交

#### Scenario: TDS 差额超容差被拒收
- **WHEN** 发票 `TDS` 与行合计差额超过该品类配置的容差
- **THEN** 系统拒收发票并生成例外单，不得将其置为 `MATCH_PENDING` 后放行

### Requirement: 语法回执不等于业务接受（M5-7 / 31506）
系统必须把 Finance 的语法回执（997/999 类比）仅用于标记「已收到」，绝不据此将发票置为 `MATCHED`。业务接受只由 Finance 的匹配结果事件决定。

#### Scenario: 997 语法回执到达
- **WHEN** 收到仅表示语法确认的回执事件
- **THEN** 发票状态仅从 `SUBMITTED` 推进到 `MATCH_PENDING`，不得变为 `MATCHED`

#### Scenario: 把语法回执当作业务接受被拒绝
- **WHEN** 有人试图以语法回执直接把发票置为 `MATCHED`
- **THEN** 系统拒绝并返回错误码 `31506`，发票状态保持不越级

### Requirement: 采购域只提交、只回写，不裁决匹配（PA-04 / M5-6）
系统必须保证采购域不提供发票匹配放行接口。`MATCHED` / `BLOCKED` 状态只由 Finance 的 `InvoiceMatched` 事件回写（本轮端口预留、运行期不触发）；采购域不得自行把 `BLOCKED` 改为 `MATCHED`。

#### Scenario: 采购域越权放行被拒绝
- **WHEN** 有人通过采购接口尝试把 `BLOCKED` 发票改为 `MATCHED`
- **THEN** 系统返回错误码 `31505`，发票状态保持不变

#### Scenario: Finance 匹配结果回写
- **WHEN** `FinanceAclPort` 收到 `InvoiceMatched` 事件（match_status=MATCHED/EXCEPTION/REVIEW）
- **THEN** 系统按事件结果回写发票状态，EXCEPTION/REVIEW 只生成或更新 IR 例外，不放行

#### Scenario: Finance 不可用时保持待态（M5-11）
- **WHEN** `InvoiceMatched` 事件未到达（Finance 不可用）
- **THEN** 发票保持 `MATCH_PENDING`，系统不推断匹配通过

### Requirement: 跨供应商越权开票零数据（M5-1）
系统必须将发票提交与请求方供应商强制绑定，不得接受客户端伪造 `vendor_id`。开票后状态与详情只读投影必须脱敏，不返回 PII 原文。

#### Scenario: 供应商 A 用 B 的 PO 开票
- **WHEN** 供应商 A 引用供应商 B 的 PO 提交发票
- **THEN** 系统返回 `403` 且零数据，不创建发票

#### Scenario: 发票响应不含 PII 原文
- **WHEN** 查询发票提交详情
- **THEN** 系统不返回银行账号、税号、联系人等 PII 原文（仅返回引用或脱敏值）
