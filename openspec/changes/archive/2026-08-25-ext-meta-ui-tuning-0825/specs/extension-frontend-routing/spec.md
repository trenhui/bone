# extension-frontend-routing Specification

## ADDED Requirements

### Requirement: 运行日志页面独立可访问

扩展微应用的"运行日志"路由须指向独立的运行日志页面，不得与沙箱页（SandboxManagement）重叠。

#### Scenario: /logs 渲染独立运行日志页
- **WHEN** 用户访问 `/logs` 路由（或对应菜单项"运行日志"）
- **THEN** 页面渲染独立的运行日志视图（从沙箱页抽取的日志逻辑），而非沙箱配置页

#### Scenario: 沙箱页与日志页职责不重叠
- **WHEN** 用户分别访问 `/sandbox` 与 `/logs`
- **THEN** 两者渲染不同内容，日志查询逻辑在两处不重复实现（抽取为共享组件/函数）

### Requirement: 部署管理页消费真实后端数据

部署管理页（DeploymentStateDiagram）须基于后端接口渲染真实部署状态，不得为无数据加载的静态占位。

#### Scenario: 部署状态图渲染真实数据
- **WHEN** 用户在部署管理页选择或指定某插件
- **THEN** 页面调用后端部署状态 / 依赖图接口（`getDeploymentState` / `getDependencyGraph`）并渲染真实状态机 / 依赖关系

#### Scenario: 无后端数据时明确降级
- **WHEN** 后端返回空或无部署记录
- **THEN** 页面给出明确的"暂无部署数据"提示，而非静默展示空占位图

### Requirement: 前端路由与后端接口前缀一致

前端所有扩展引擎接口调用路径须与后端 Controller 前缀 `/api/v1/extension` 保持一致，不出现前缀错位导致 404。

#### Scenario: 接口前缀一致性
- **WHEN** 前端发起扩展引擎任意接口调用
- **THEN** 请求 URL 以 `/api/v1/extension` 为前缀，与后端 `ExtensionManagementController` 映射一致
