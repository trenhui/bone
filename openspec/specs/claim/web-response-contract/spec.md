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
