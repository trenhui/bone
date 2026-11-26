# claim/web-response-contract Specification

## Purpose
统一 Controller 出站响应，消除领域对象直出，使所有对外端点返回显式响应 DTO，并把同类响应的装配收敛到单一真源。

## Requirements

### Requirement: 统一响应 DTO

synap-claim 的 Controller SHALL 返回 `ApiResponse<T>` 或 `PageResult<T>`，其中 `T` 为 `*Resp` 或其它显式响应 DTO，不得是 domain 聚合、实体或执行轨迹领域对象。

#### Scenario: 端点机检无领域对象直出

- **WHEN** 运行响应契约机检扫描全部 Controller 返回签名
- **THEN** 不存在以 domain 包类型作为响应泛型参数的端点

#### Scenario: 决策轨迹端点

- **WHEN** 请求决策轨迹查询端点
- **THEN** 返回显式轨迹响应 DTO 列表，字段与原领域对象等价且不暴露内部实现类型

### Requirement: Assembler 单一真源

同类响应对象的装配逻辑 SHALL 集中在 `adapter/web/assembler/`，Controller 不得内联构造响应 DTO 或复制映射规则。

#### Scenario: 装配收敛机检

- **WHEN** 运行装配收敛检查
- **THEN** 同一响应 DTO 的构造点唯一，Controller 中无重复映射代码

#### Scenario: 映射回归

- **WHEN** 执行 Assembler 单元测试
- **THEN** 关键字段映射与空值处理全部通过，且回归探针能发现映射被绕过

### Requirement: 签收端点响应契约

synap-claim 的签收相关端点 SHALL 遵循既有 `ApiResponse<T>`/`PageResult<T>` 契约，响应泛型 MUST 为显式 `*Resp` DTO（如 `SignBatchResp`、`SignBatchItemResp`、`SignTrackResp`），不得直接返回签收聚合或轨迹领域对象；装配逻辑 MUST 收敛到 `adapter/web/assembler/`，Controller 不得内联构造响应。

#### Scenario: 签收批次列表分页响应
- **WHEN** 请求签收批次列表端点
- **THEN** 返回 `ApiResponse<PageResult<SignBatchResp>>`，`records` 字段为显式响应 DTO，分页字段与既有案件查询契约一致

#### Scenario: 签收轨迹端点响应
- **WHEN** 请求批次轨迹查询端点
- **THEN** 返回显式 `SignTrackResp` 列表，不包含内部领域类型

### Requirement: 签收写端点幂等键契约

所有签收写端点（自动签收回执、创建批次、导入名单、登记影像、确认签收）MUST 接受幂等键，优先使用请求头 `Idempotency-Key`，长度 MUST 不超过 64 字符；同一键重复请求 MUST 返回首次结果而非重复副作用。前端 MUST 对稳定语义的操作使用由业务标识派生的稳定键（超长时前缀+哈希截断），仅对无法预知的操作使用随机键兜底。

#### Scenario: 幂等键超长被拒
- **WHEN** 客户端提交长度超过 64 字符的 `Idempotency-Key`
- **THEN** 系统 MUST 拒绝并返回 `COMMON_VALIDATION_FAILED` 类错误码，不执行签收写操作

#### Scenario: 稳定键跨重试一致
- **WHEN** 前端对同一案件同一签收动作重试并沿用派生稳定键
- **THEN** 后端 MUST 识别为同一操作并返回首次结果

### Requirement: 签收错误码对外契约

签收相关的业务失败 MUST 返回专用错误码并映射到恰当的 HTTP 状态码，至少覆盖：重复签收冲突、前置状态不满足、影像未完备、签收意见不合法、权限不足、案件挂起、外部推送要素缺失；错误码 MUST 在后端错误码枚举、默认 HTTP 状态映射、前端中英文语言包与错误码台账四处保持一致。

#### Scenario: 冲突类错误映射
- **WHEN** 因并发确认导致 CAS 失败
- **THEN** 响应 MUST 携带签收冲突错误码并映射为冲突类 HTTP 状态码，前端 MUST 提示「刷新后重试」语义

#### Scenario: 前端可解析错误文案
- **WHEN** 前端收到任一签收错误码
- **THEN** 中英文语言包与后端枚举 MUST 存在同名条目，前端 MUST 显示可读文案而非原始码
