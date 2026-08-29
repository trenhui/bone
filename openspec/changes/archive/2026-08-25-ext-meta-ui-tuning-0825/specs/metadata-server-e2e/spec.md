# metadata-server-e2e Specification

## ADDED Requirements

### Requirement: 元数据主链路 Controller 集成测试

元数据服务端须对实体、字段、校验规则、视图、审批流的主链路提供 Controller 级集成测试（`@SpringBootTest` + MockMvc），验证 HTTP 层的行为契约，而非仅依赖分层门禁或单测。

#### Scenario: 实体 CRUD 通过 HTTP 接口验证
- **WHEN** 测试向 `/api/v1/metadata/entities` 发起创建、查询、更新、删除请求
- **THEN** 各操作返回符合 `ApiResponse` / `PageResult` 契约的响应，状态码与业务结果一致

#### Scenario: 字段与校验规则可通过接口写入并读回
- **WHEN** 测试为某实体创建字段并附加校验规则，再查询该实体详情
- **THEN** 返回结果包含已写入的字段与校验规则定义

#### Scenario: 视图与审批流接口可达且返回结构化结果
- **WHEN** 测试调用 `/api/v1/metadata/views` 与 `/api/v1/metadata/approval-flows` 相关接口
- **THEN** 接口返回非空或定义明确的结构化结果，不抛未处理异常

### Requirement: 真实 MySQL 端到端校验

元数据服务端须提供真实 MySQL（非内存库）的端到端测试，验证建模数据经持久化后可被正确读回，且跨模块引用校验在异常路径上行为正确。

#### Scenario: moduleId 引用不存在的 IAM 模块时被拒绝
- **WHEN** 创建元数据实体并指定一个在 `bone_module` 表中不存在的 `moduleId`
- **THEN** 服务端返回引用校验失败的错误响应（`IamModuleValidator.requireExists` 触发），且不包含任何脏数据写入

#### Scenario: 建模数据在真实库中持久化并可回读
- **WHEN** 经接口创建实体及其字段，并直接查询底层 MySQL 表
- **THEN** 记录真实落库，再次经接口查询可得到一致结果

### Requirement: 测试可独立运行于本地基础设施

端到端测试须可基于项目已配置的本地 MySQL（`MYSQL_DATABASE=bone`，凭据来自 `BONE_DB_*` / `MYSQL_*` 环境变量）运行，不依赖尚未搭建的额外服务。

#### Scenario: 本地环境一键运行测试
- **WHEN** 在已配置 MySQL 环境与 `BONE_DB_PASSWORD` 等变量的前提下执行测试
- **THEN** 集成测试与 MySQL E2E 真实通过，不要求 Testcontainers 等额外编排
