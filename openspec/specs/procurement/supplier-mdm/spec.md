# procurement/supplier-mdm Specification

## Purpose
采购域供应商主数据能力提供 Supplier（合作资格）与 Vendor（交易与付款资格）角色分离的建模、统一身份解析、字段级可溯源的合并、两套互不覆盖的生命周期状态机，以及租户隔离与 fail-closed 的治理契约；它是采购域 9 个模块的领域地基，业务域只持有 partyId 与自有视图列，不另建第二份黄金记录。

## Requirements

### Requirement: 供应商与供应商主体的角色分离

系统 SHALL 将 Supplier（合作资格：寻源、准入、绩效、风险、退出）与 Vendor（交易与付款资格：财务侧 Company Code data，含银行、税务、付款条件与付款冻结）建模为同一 LegalEntity 下的两个独立角色，二者不得互相替代或合并为单一实体，且各自持有互不覆盖的生命周期状态机。

#### Scenario: 同一法人下的双角色
- **WHEN** 一个 LegalEntity 同时具备合作资格与交易付款资格被登记
- **THEN** 系统建立 1 个 LegalEntity、1 个 Supplier 角色与 1 个 Vendor 角色，三者通过统一 partyId 关联，且 Supplier 与 Vendor 各自持有独立的生命周期状态

#### Scenario: 角色不可互换
- **WHEN** 以 Vendor 角色查询合作资格状态
- **THEN** 系统不返回 Supplier 角色数据，角色边界不被静默穿透

#### Scenario: 双状态机互不覆盖
- **WHEN** Supplier 因风险事件进入 `SUSPENDED`，而其 Vendor 角色仍处于 `ACTIVE_VENDOR`
- **THEN** 系统保留两个独立状态，不以任一状态机的结果推导或覆盖另一状态机

### Requirement: 身份唯一性与禁止自动合并

系统 SHALL 以统一社会信用代码/税号作为身份主键字段；这些字段值冲突时 MUST 转人工复核，禁止自动合并，且不得静默丢弃跨租户候选记录。

#### Scenario: 身份主键冲突转人工
- **WHEN** 两条候选供应商记录的统一社会信用代码不同但其余字段高度相似
- **THEN** 系统拒绝自动合并，返回 `MANUAL_REVIEW` 裁决且不产生任何落库或分发副作用

#### Scenario: 跨租户候选记录
- **WHEN** 合并命令中出现租户号与命令租户不一致的候选源记录
- **THEN** 系统 fail-closed 拒绝整笔合并并给出明确原因，不静默过滤该记录

### Requirement: 字段级血缘与整笔拒绝

系统 SHALL 要求黄金记录中每个有值字段都携带完整字段级 lineage（来源记录、来源系统、胜出规则、观测时间）；缺任一条 lineage 时 MUST 整笔拒绝合并，不允许「部分字段可溯源」的记录进入分发链路。

#### Scenario: 缺失 lineage
- **WHEN** 某胜出字段缺少来源系统或胜出规则
- **THEN** 整笔合并被拒绝，不产出可分发记录

#### Scenario: 具名裁决完整性
- **WHEN** 字段胜出策略为 `STEWARD_OVERRIDE` 或 `MANUAL_REVIEW` 但无具名数据管家裁决记录
- **THEN** 整笔转人工，不部分应用任何字段胜出结果

### Requirement: Supplier 合作资格生命周期状态机

系统 SHALL 为 Supplier 角色维护合作资格生命周期状态机（`PROSPECT` → `ONBOARDING` → `ACTIVE` ⇄ `SUSPENDED` / `CORRECTIVE` → `EXITED`）；状态迁移 MUST 由显式事件驱动、经迁移表裁决，迁移表未列出即拒绝（fail-closed）。

#### Scenario: 合法准入路径
- **WHEN** 处于 `ONBOARDING` 的 Supplier 收到 `onboarding_approved` 事件，且变更请求全段已批准、至少存在 1 个 `ACTIVE_VENDOR`
- **THEN** 状态迁移为 `ACTIVE`，并记录迁移时间、操作人与依据

#### Scenario: 非法迁移被拒
- **WHEN** 处于 `PROSPECT` 的 Supplier 直接收到 `onboarding_approved` 事件
- **THEN** 系统拒绝迁移并保持原状态，返回非法迁移原因及当前允许的事件集合

#### Scenario: 退出前置校验
- **WHEN** 处于 `ACTIVE` 的 Supplier 收到 `exit_approved`，但存在未结 PO、未结合同、未清应付或仍有未关闭的 Vendor 角色
- **THEN** 系统拒绝迁移至 `EXITED` 并保持原状态，不产生任何副作用

#### Scenario: 终态不可回写
- **WHEN** 处于 `EXITED` 的 Supplier 收到任一迁移事件
- **THEN** 系统一律拒绝，终态不可回到任何其他状态

### Requirement: Vendor 交易与付款资格状态机

系统 SHALL 为 Vendor 角色维护交易与付款资格状态机（`PENDING_VENDOR` → `ACTIVE_VENDOR` ⇄ `PAYMENT_BLOCKED` → `INACTIVE_VENDOR`）；开通必须经闸门全项通过，付款冻结不得由本域自行放行。

#### Scenario: 开通闸门全项通过
- **WHEN** 处于 `PENDING_VENDOR` 的 Vendor 收到 `activation_passed`，且准入、银行核验、税务、付款条件与重复发票检查全部通过
- **THEN** 状态迁移为 `ACTIVE_VENDOR`，并记录闸门各项结论与时间

#### Scenario: 未经闸门不得开通
- **WHEN** 处于 `PENDING_VENDOR` 的 Vendor 被直接置位为 `ACTIVE_VENDOR` 而未经闸门
- **THEN** 系统拒绝该迁移并保持原状态

#### Scenario: 本域不得自行解除付款冻结
- **WHEN** 处于 `PAYMENT_BLOCKED` 的 Vendor 收到来源声明为域内的 `payment_block_released` 事件
- **THEN** 系统拒绝该迁移，付款冻结只能由外部财务事件加人工复核留痕解除

#### Scenario: 停用不得留下进行中采购
- **WHEN** Vendor 收到 `deactivate` 且存在进行中的 PO 且存量 PO 不允许走完
- **THEN** 系统拒绝迁移至 `INACTIVE_VENDOR` 并保持原状态

### Requirement: 租户隔离与 fail-closed

系统 SHALL 从登录态取租户上下文，不接受调用方自报租户；命令自带租户与登录态不一致时 MUST fail-closed。能力未启用或生产持久化端口未就绪时，端点不得注册，避免暴露「能调用但落不了库」的假能力。

#### Scenario: 租户不一致
- **WHEN** 请求命令携带的 tenantId 与登录态 tenantId 不一致
- **THEN** 系统拒绝请求，不执行任何领域逻辑

#### Scenario: 能力未启用
- **WHEN** 配置开关 `bone.procurement.supplier-mdm.enabled` 未开启
- **THEN** 相关 HTTP 端点不注册，应用上下文中不装配对应应用服务

### Requirement: 统一响应与权限契约

系统 SHALL 对供应商主数据的 HTTP 端点返回 `ApiResponse<T>` / `PageResult<T>` 统一响应，不裸返领域对象；写操作与治理操作 MUST 受权限点保护，读操作受只读权限点保护。

#### Scenario: 写操作鉴权
- **WHEN** 无 `masterdata:governance:write` 权限的主体调用供应商合并/迁移端点
- **THEN** 请求被拒绝，返回无权限响应

#### Scenario: 统一响应包装
- **WHEN** 主体成功查询供应商当前状态
- **THEN** 响应为 `ApiResponse` 包装的 DTO（仅含裁决、版本与摘要，不回传字段原值或 PII）
