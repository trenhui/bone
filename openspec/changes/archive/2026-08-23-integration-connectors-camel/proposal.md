# Proposal: 集成连接器全量与 Camel 编排

## 目标（Why）

`bone-integration` 连接器注册表声明 20 类，但仅 REST 真实、FTP/JDBC/MQ 抛 501、Redis/ES/Mongo/S3/SOAP 无实现类。Camel 分支/并行编排 `CamelFlowCompiler` 已实现（DECISION→choice、PARALLEL→multicast），缺口在分支内禁嵌套。前端 FlowDesign 节点库与后端 NodeType 不对齐、无连接器类型选择面板。这是 P0「连通外部系统」主价值。

## 范围（What）

1. **后端连接器补齐**：基于 `ExternalSystemClient` 接口 + `@Component(name)` 注册机制，新增 Redis / ES / Mongo / S3 / SOAP 5 个实现类（REST 已有），使 6 种高频连接器真实可调通。
2. **Camel 编排**：`CamelFlowCompiler` 分支/并行已实现；解除「分支内禁嵌套」限制，支持 DECISION/PARALLEL 嵌套。
3. **前端**：`integration-app` FlowDesign 节点库对齐后端 `NodeType`（补 DECISION/PARALLEL），节点配置面板接连接器选择（复用 `connectorApi.getConnectors`）；`ConnectorManagement` 的 connectorTypes 对齐后端 20 枚举。

## 非目标（Non-Goals）

- 不重构 `ExternalSystemClient` 抽象层（仅新增实现）。
- 不新增自定义连接器 SDK 协议；连接器密钥加密复用现有机制。

## 验收标准（Acceptance Criteria）

- [ ] ≥6 种连接器有真实实现（REST/Redis/ES/Mongo/S3/SOAP），`ConnectorRegistry` 可解析不再 501。
- [ ] Camel 分支/并行节点可正确执行（含嵌套）。
- [ ] 前端设计器节点库对齐 NodeType、节点配置面板可绑连接器。
