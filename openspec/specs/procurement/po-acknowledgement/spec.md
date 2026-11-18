# procurement/po-acknowledgement Specification

## Purpose
定义供应商对已下达采购订单的行级确认行为契约（855 语义类比）。该能力以 PO 为上游，保证确认动作的幂等性、版本一致性与终态保护，并把 PO 推进到「可发货」前提，使后续 ASN 有可信依据。

## Requirements

### Requirement: 行级 PO 确认
系统必须支持供应商按行对采购订单给出确认结果，取值为 `FULL`（全部接受）、`PARTIAL`（部分接受/改期）、`REJECT`（拒收），并记录每行的确认明细。确认必须针对具体的 `po_no` + `po_version`。

#### Scenario: 对已下达的 PO 做全额确认
- **WHEN** 供应商对状态为 `SENT` 的采购订单以 `ack_type=FULL` 提交确认
- **THEN** 系统记录确认并返回幂等键 `po_no+po_version` 对应的确认结果，PO 进入「已确认、可发货」态

#### Scenario: 部分确认带行级结果
- **WHEN** 供应商以 `ack_type=PARTIAL` 提交，且附带每行接受数量或改期信息
- **THEN** 系统逐行保存确认结果，未接受的行不进入后续 ASN 可引用集合

### Requirement: 确认幂等与版本一致（INV-01）
系统必须以 `po_no+po_version` 作为确认的幂等键与乐观并发依据。同一版本的重复确认不得产生第二条记录；版本落后于当前 PO 的确认必须被拒绝。

#### Scenario: 重复确认同一版本只记一条
- **WHEN** 供应商对同一 `po_no+po_version` 重复提交确认两次
- **THEN** 系统只保留一条确认记录，第二次返回首次结果，不产生重复数据

#### Scenario: 陈旧版本确认被拒绝
- **WHEN** 提交的 `po_version` 小于该 PO 当前版本
- **THEN** 系统拒绝确认并返回 `invalid_transition`（含当前状态、尝试版本与允许版本），不改写确认记录

### Requirement: 已关闭 PO 不可确认（INV-02）
系统必须拒绝对手工关闭或终态 PO 的确认。终态不可回写。

#### Scenario: 关闭态 PO 确认被拒绝
- **WHEN** 供应商对状态为 `CANCELLED`（或其他终态）的采购订单提交确认
- **THEN** 系统拒绝并返回 `invalid_transition`，PO 状态不变，不产生确认记录

### Requirement: 跨供应商越权确认零数据（M5-1）
系统必须将确认单据的供应商与请求方身份强制绑定，不得接受客户端传入的 `vendor_id` 伪造归属。越权请求必须返回 `403` 且不泄露任何订单数据。

#### Scenario: 供应商 A 确认供应商 B 的 PO
- **WHEN** 供应商 A 用供应商 B 的 `po_no` 提交确认
- **THEN** 系统返回 `403` 且不读取、不返回该 PO 的任何字段

#### Scenario: 缺失身份的确认被拒绝
- **WHEN** 请求未携带可解析的供应商身份却尝试确认
- **THEN** 系统 fail-closed 返回 `403`，不执行确认逻辑
