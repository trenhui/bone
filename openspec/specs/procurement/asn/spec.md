# procurement/asn Specification

## Purpose
定义供应商发货通知（856 语义类比）的行为契约。该能力以已确认 PO 为上游，保证 ASN 编号唯一、发货行引用有效、包装层级与 SSCC 完整，并通过显式状态机拒绝非法迁移，使收货对齐有可信凭证。

## Requirements

### Requirement: ASN 创建与唯一性（INV-03）
系统必须以 `asn_no` 作为发货通知的业务唯一键（租户内唯一）。重复编号的重传必须被幂等吸收，不产生第二条 ASN。

#### Scenario: 创建 ASN 成功
- **WHEN** 供应商提交带唯一 `asn_no`、引用已确认 PO 的发货通知
- **THEN** 系统创建 ASN，初始状态 `CREATED`，返回 ASN 号与状态

#### Scenario: 重复 asn_no 幂等
- **WHEN** 同一租户下重复提交相同 `asn_no` 的发货通知
- **THEN** 系统只保留一条 ASN，重复请求返回首次结果（重传去重，不以事件 ID 代替业务键）

### Requirement: 发货行必须引用已确认 PO 行（INV-04 / M5-3）
系统必须校验 ASN 的每一行都引用该供应商已确认 PO 的有效行。未确认的 PO、不存在或超量的 PO 行不得作为发货依据。

#### Scenario: 未确认 PO 直接发 ASN 被拒绝
- **WHEN** 供应商对尚未完成 PO 确认（或确认未通过）的订单提交 ASN
- **THEN** 系统拒绝并返回错误码 `31503`，不创建 ASN

#### Scenario: 引用不存在的 PO 行被拒绝
- **WHEN** ASN 明细引用了 PO 上不存在的行号
- **THEN** 系统拒绝 ASN 并指出无效行号，不创建 ASN

#### Scenario: 发货数量超过确认数量被拒绝
- **WHEN** ASN 某行的发货数量超过该 PO 行已确认的接受数量
- **THEN** 系统拒绝 ASN，返回数量越界错误，不创建 ASN

### Requirement: 包装层级与 SSCC 校验
系统必须校验 ASN 携带的包装层级引用（SSCC）完整且自洽；每个 `AsnLine` 的 `pack_ref` 必须指向一个存在的 SSCC 包装。

#### Scenario: pack_ref 指向不存在的 SSCC
- **WHEN** ASN 明细的 `pack_ref` 未在 ASN 的 SSCC 列表中声明
- **THEN** 系统拒绝 ASN 并返回包装引用错误

#### Scenario: 包装层级完整时通过
- **WHEN** ASN 的每条明细 `pack_ref` 均指向已声明的 SSCC
- **THEN** 系统接受 ASN 并保持初始状态 `CREATED`

### Requirement: ASN 显式状态机与非法迁移拒绝
系统必须以显式迁移表管理 ASN 状态：`CREATED → TRANSMITTED → RECEIVING_MATCHED → RECONCILED`，`CANCELLED` 为终态。迁移未列出即拒绝（fail-closed）。

#### Scenario: 合法传输迁移
- **WHEN** 状态为 `CREATED` 的 ASN 执行 `transmit`（PO 已确认、行引用有效、包装完整）
- **THEN** 状态变为 `TRANSMITTED`

#### Scenario: 跳过传输直接对账被拒绝
- **WHEN** 状态为 `CREATED` 的 ASN 尝试 `reconciled`
- **THEN** 系统返回 `invalid_transition`（含当前状态、尝试事件与允许事件），状态不变

#### Scenario: 终态 ASN 不可再迁移
- **WHEN** 状态为 `CANCELLED` 或 `RECONCILED` 的 ASN 尝试任何推进事件
- **THEN** 系统拒绝，状态不变

### Requirement: ASN 与 GR 差异进例外台账（INV-05 / M5-4）
系统必须把 ASN 数量与后续收货（GR）的差异记入例外台账，不得静默改写 ASN 数量以对齐 GR。GR 只由 Inventory 事件投影生成（本轮端口预留、运行期不触发）。

#### Scenario: ASN 与 GR 数量不符
- **WHEN** 收货投影的实收数量与该 ASN 行数量不一致
- **THEN** 系统生成差异例外记录，ASN 数量保持原值不被改写

#### Scenario: Finance/Inventory 上游不可用时保持待态
- **WHEN** `GoodsReceived` 事件未到达（`InventoryAclPort` 默认 fail-closed）
- **THEN** ASN 状态停在 `TRANSMITTED`，不推断收货、不推进到 `RECEIVING_MATCHED`

### Requirement: 跨供应商越权 ASN 零数据（M5-1）
系统必须将 ASN 与请求方供应商强制绑定，越权创建或查询返回 `403` 且零数据。

#### Scenario: 供应商 A 查供应商 B 的 ASN
- **WHEN** 供应商 A 请求查看仅属于供应商 B 的 ASN
- **THEN** 系统返回 `403` 且不返回该 ASN 字段
