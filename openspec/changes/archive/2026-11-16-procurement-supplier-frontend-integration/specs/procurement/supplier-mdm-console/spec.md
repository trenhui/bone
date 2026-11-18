# Spec Delta

## Purpose

采购供应商主数据的 Web 治理控制台提供登记、双角色状态与历史只读呈现、显式迁移事件触发，以及统一响应、能力开关与权限的可见降级，让数据管家能在浏览器里安全操作供应商主数据而不越领域边界。

## ADDED Requirements

### Requirement: 供应商登记表单

系统 SHALL 提供供应商登记入口，提交后端登记命令所需的身份键与主体标识，且不在界面持久化或回显统一社会信用代码、税号等身份主键原值以外的新增副本；提交成功与失败 MUST 依据响应顶层 `success` 字段判定，不得仅凭 HTTP 2xx。

#### Scenario: 登记成功呈现双角色初始态

- **WHEN** 数据管家填写合法登记信息并提交
- **THEN** 页面依据 `success=true` 呈现登记成功，并显示 Supplier 初始态 `PROSPECT` 与 Vendor 初始态 `PENDING_VENDOR`

#### Scenario: 顶层失败不误报成功

- **WHEN** HTTP 2xx 但响应 `success=false` 且 `message` 含身份冲突原因
- **THEN** 页面显示后端返回的失败原因，不呈现成功态，且不发起后续状态迁移调用

### Requirement: 双角色状态与历史只读呈现

系统 SHALL 在同一详情视图分区呈现 Supplier 合作资格与 Vendor 交易付款资格两套互不覆盖的状态、版本与迁移摘要；摘要 MUST 以"原状态->新状态 (事件码)"形状呈现，缺失或格式异常时显示原始值而非推断为正常；身份主键与 PII（电话、法人名）MUST NOT 出现在页面可见文本或 DOM 属性中。

#### Scenario: 双状态机独立呈现

- **WHEN** 供应商 Supplier 处于 `SUSPENDED` 而 Vendor 处于 `ACTIVE_VENDOR`
- **THEN** 页面同时显示两个独立状态，不以其中一个推导或覆盖另一个

#### Scenario: 未知状态码不吞掉

- **WHEN** 后端返回页面未预置的状态码
- **THEN** 页面显示后端返回的原始码，不改写为"正常"或已知态

#### Scenario: PII 不落地到前端呈现

- **WHEN** 详情响应意外包含电话或统一社会信用代码字段
- **THEN** 页面可见文本与 DOM 属性均不出现这些值，仅呈现状态、版本与摘要

### Requirement: 显式迁移事件触发

系统 SHALL 仅呈现后端迁移表允许的合法事件供点击，点击事件携带路径 partyId 与事件码调用对应 Supplier 或 Vendor 迁移端点；MUST NOT 在前端重新实现守卫裁决或绕过后端前置条件，失败时按后端 `message` 呈现原因。

#### Scenario: 合法事件触发迁移

- **WHEN** 数据管家在 Supplier 处于 `ONBOARDING` 时点击 `onboarding_approved`
- **THEN** 前端调用 Supplier 迁移端点，成功后刷新双角色状态

#### Scenario: 后端拒绝时保持状态

- **WHEN** 迁移被后端守卫拒绝（`success=false`）
- **THEN** 页面显示后端失败原因，不本地推进状态，重新拉取的状态与迁移前一致

#### Scenario: 付款冻结解禁来源约束不在前端绕过

- **WHEN** Vendor 处于 `PAYMENT_BLOCKED`
- **THEN** 前端不提供域内来源的自行解禁入口，解禁只能由后端 `EXTERNAL_FINANCE` + 人工留痕裁决通过

### Requirement: 分页列表与响应归一化

系统 SHALL 以 `PageResult` 的权威字段 `records` 与 `total` 呈现分页列表，将后端 Long 序列化的字符串 `total` 安全归一为数值；不得依赖兼容 getter 的 `list` 字段，也不得对雪花 ID 做 `Number()` 转换。

#### Scenario: 空列表

- **WHEN** 后端返回 `records=[]`、`total="0"`
- **THEN** 页面显示空列表态，总数呈现为 0，不抛类型转换错误

#### Scenario: 字符串总数归一

- **WHEN** 后端返回 `total="23"`、页大小 10
- **THEN** 页面正确计算 3 页总数，不把字符串与数字直接相乘

### Requirement: 能力开关与权限的可观察降级

系统 SHALL 在后端供应商能力未启用（端点未注册）或权限不足时给出可理解的降级提示，不得把 fail-closed 呈现为空白页或前端故障；页面 MUST NOT 视图 bypass 权限提示自行放行写操作。

#### Scenario: 能力未启用

- **WHEN** 后端 `bone.procurement.supplier-mdm.enabled=false`，前端请求返回未注册/404
- **THEN** 页面显示"供应商能力未启用"空态，不显示可操作的迁移按钮

#### Scenario: 权限不足

- **WHEN** 读或写请求返回 403
- **THEN** 页面显示无权限提示，且不发起后续写调用

### Requirement: 前后端契约一致性

系统 SHALL 用自动化测试验证前端 API 客户端发出的请求形状与后端端点契约一致——写操作用 POST 且路径为 `/api/v1/masterdata/suppliers/...`、body 带事件码与来源声明、列表用 `records/total` 消费；任一形状漂移 MUST 被测试捕获。

#### Scenario: 迁移请求形状

- **WHEN** 前端触发 Supplier 迁移
- **THEN** 测试断言请求方法为 POST、路径含 `/{partyId}/supplier-transitions`、body 含合法事件码

#### Scenario: 全链路联调可达

- **WHEN** 后端以供应商能力开关打开并装配测试端口启动，前端连该后端
- **THEN** 可从界面完成登记 → Vendor 闸门 → Supplier 准入 → 退出，且每一步状态与后端 HTTP 响应一致
