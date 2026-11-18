# procurement/ir-exception Specification

## Purpose
定义发票例外（Invoice Reconciliation Exception）台账的行为契约。该能力承载发票匹配差异的登记、补料与申诉，严格保持「采购域不裁决、不放行」边界：状态只由 Finance 事件回写，采购方仅能补料/申诉，重复例外码幂等，差异单列台账不并入净额等式。

## ADDED Requirements

### Requirement: 例外登记与幂等（INV-11）
系统必须为发票匹配差异、TDS 不符、ASN/GR 差异生成例外记录，并以 `(tenant_id, exception_no)` 与 `(tenant_id, invoice_no, exception_code)` 保证唯一。重复同一例外的触发必须幂等，不产生重复台账行。

#### Scenario: 匹配差异生成例外
- **WHEN** 收到发票匹配结果为 `EXCEPTION`（或 TDS 校验超容差）
- **THEN** 系统生成一条 IR 例外记录，初始状态 `OPEN`

#### Scenario: 重复 exception_code 幂等
- **WHEN** 同一 `invoice_no` + `exception_code` 被重复触发
- **THEN** 系统只保留一条例外记录，重复触发返回首次结果

### Requirement: 状态只由 Finance 事件回写（INV-10 / M5-6）
系统必须以显式状态机管理 IR 例外：`OPEN → SUPPLEMENTED → UNDER_REVIEW → RESOLVED / REJECTED`。`RESOLVED` 只能由 Finance 的匹配结果事件置位，采购域不得自行放行或关闭。

#### Scenario: 补料推进到已补充
- **WHEN** 供应商对 `OPEN` 的例外提交补料
- **THEN** 状态推进到 `SUPPLEMENTED`，记录补料引用

#### Scenario: 采购域无法自行置 RESOLVED
- **WHEN** 采购角色尝试把任何状态的例外直接置为 `RESOLVED`
- **THEN** 系统拒绝并返回错误码 `31505`，状态不变

#### Scenario: Finance 事件回写解决
- **WHEN** `FinanceAclPort` 收到该例外对应的放行/解决事件
- **THEN** 系统按事件把状态置为 `RESOLVED`，记录 Finance 事件引用

### Requirement: 例外补料/申诉无放行权限
系统必须允许供应商对例外补料与申诉，但补料接口不得提供任何匹配放行、金额调整或状态跳过能力。

#### Scenario: 补料成功
- **WHEN** 供应商对本供应商的例外提交补料材料
- **THEN** 系统记录补料并保持例外处于待审状态，不改动匹配结果

#### Scenario: 跨供应商补料被拒绝
- **WHEN** 供应商 A 尝试对供应商 B 的例外补料
- **THEN** 系统返回 `403` 且零数据

### Requirement: 差异单列台账，不并入净额等式（INV-12 / M5-10）
系统必须把预付、红冲、部分付款、RTV、汇兑差异等单列例外台账，禁止并入三通道对账的净额等式。对账归因分 SUPPLY / INVOICE / PAYMENT 三通道独立成账。

#### Scenario: 差异进入独立台账
- **WHEN** 出现部分付款或红冲差异
- **THEN** 系统以独立例外记录登记差异类型与金额，不计入净已付或净应付等式

#### Scenario: 把差异并入净额等式被拒绝
- **WHEN** 有人试图把差异金额并入净额对账结果
- **THEN** 系统按台账约束拒绝该合并，差异仍单列

### Requirement: 例外列表可分页且越权零数据
系统必须提供例外的只读列表（cursor 分页 + 服务端 max_page_size），且按供应商身份过滤；大表禁止精确 `COUNT(*)`。

#### Scenario: 分页查询例外
- **WHEN** 采购方按 cursor 查询例外列表
- **THEN** 系统返回当页记录与 `has_more`，不返回全量精确计数

#### Scenario: 越权查询例外零数据
- **WHEN** 供应商查询不属于自己的例外
- **THEN** 系统返回 `403` 且不返回例外字段
