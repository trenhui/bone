# Spec Delta — procurement/supplier-master-data

## Purpose

定义采购域供应商主数据（Supplier）与供应商法人（Vendor）的最小行为契约，覆盖租户隔离与审计、Golden Record 去重与合并裁决留痕、数据质量维度、Vendor 开通闸门，作为交易与 AI 审查的前置主数据。按 DAMA 数据质量维度（唯一性/完整性/有效性/一致性）约束最小集，避免 AI 审查建立在一主多档、来源不可追的主数据之上。

## ADDED Requirements

### Requirement: 供应商主数据租户隔离与审计

系统必须以租户隔离方式维护 Supplier 与 Vendor 聚合；每个聚合必须具备版本（乐观锁）、软删与审计列，且租户由可信上下文注入，业务层不得直读或覆盖。

#### Scenario: 创建供应商自动注入租户

- **WHEN** 合法租户内提交一条 Supplier
- **THEN** 记录持久化，`tenant_id` 由可信上下文注入，`version` 为初值，`deleted=false`，审计列已填

#### Scenario: 跨租户不可见

- **WHEN** 租户 A 查询供应商列表
- **THEN** 结果不包含租户 B 的 Supplier/Vendor

#### Scenario: 未声明租户隔离时拒绝

- **WHEN** 实体未继承租户基类或查询未带租户作用域
- **THEN** 门禁拒绝（fail-closed），不得返回跨租户数据

#### Scenario: 业务层不得覆盖审计列

- **WHEN** 业务请求试图显式设置 `tenant_id`、创建人/创建时间或 `version`
- **THEN** 系统忽略或拒绝该覆写，审计值由框架注入，且该尝试留痕

### Requirement: Golden Record 去重与合并裁决

系统必须以统一社会信用代码等业务唯一键识别重复供应商，重复时拒绝或合并并留痕，不得静默产生两张主档；合并须记录裁决依据与来源，且合并后引用一致。

#### Scenario: 重复信用代码

- **WHEN** 同一租户提交已存在的统一社会信用代码
- **THEN** 依据唯一键拒绝或进入合并流程，并记录留痕

#### Scenario: 合并裁决可回溯

- **WHEN** 两条候选主档被合并为一条 Golden Record
- **THEN** 记录被合并 ID、裁决依据、执行人与时间，原记录软删且可追溯

#### Scenario: 合并后引用一致

- **WHEN** 合并完成
- **THEN** 既有对旧 ID 的引用解析到 Golden Record，不产生悬空引用或双份引用

### Requirement: 数据质量最小维度

主数据写入须满足唯一性、完整性、有效性、一致性四项最小校验；不满足时拒绝写入并给出可判定原因，不得写入后再静默修补。

#### Scenario: 唯一键或必填项缺失

- **WHEN** 提交记录缺少统一信用代码等必填/唯一字段，或格式无效
- **THEN** 写入被拒绝并返回明确原因码，不产生半写入状态

#### Scenario: 数据质量门禁

- **WHEN** 数据质量门禁扫描主数据表
- **THEN** 报告重复、缺失、无效值计数；存在未处置项时 AI 审查链路不得引用该批主数据

### Requirement: Vendor 开通闸门

Vendor 激活必须经合规/准入工作流闸门，未通过不得激活，不得被交易或 AI 审查引用。

#### Scenario: 闸门未通过不得激活

- **WHEN** `gate_result` 非通过
- **THEN** `vendor_status` 保持待激活，交易侧引用被拒绝

#### Scenario: 闸门通过留痕

- **WHEN** 闸门通过并激活
- **THEN** 记录 `workflow_instance_id` 与 `gate_result`，可回溯

#### Scenario: 未激活不得被 AI 审查引用

- **WHEN** AI 合同审查请求关联一个未通过闸门的 Vendor
- **THEN** 引用被拒绝（fail-closed），不得静默降级为"无主数据"
