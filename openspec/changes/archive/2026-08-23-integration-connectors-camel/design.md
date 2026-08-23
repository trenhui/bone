# Design: 集成连接器全量与 Camel 编排

## Context
`bone-integration` 后端 50%，Connector 注册表声明 20 种但仅 FTP/JDBC/MQ 真实、HTTP 节点仅执行、其余 501。编排仅线性顺序。PRD INT-11 要求分支/并行。这是 P0「连通外部」主价值半骨架。

## Decision 1：连接器补齐
- 复用 `ConnectorClientSupport` 接口，新增实现：
  - REST（`RestTemplate`/`WebClient`）
  - SOAP（JAX-WS / `WebServiceTemplate`）
  - Redis（`RedisTemplate`）
  - ES（`RestHighLevelClient`/`ElasticsearchClient`）
  - Mongo（`MongoClient`）
  - S3（`aws-java-sdk-s3` / MinIO client）
- 按使用频率排序，先 REST/Redis/ES/Mongo/S3/SOAP。

## Decision 2：Camel 编排
- `CamelFlowRuntime`：基于 Apache Camel `RouteBuilder` 动态构建路由。
- 节点类型映射：start→from、http/task→to、branch→choice/when、parallel→multicast、end→to（sink）。
- 执行引擎解析 `IntFlowNode` + `IntFlowConnection` 生成 Camel Route 并启动。

## Decision 3：前端联调
- integration-app FlowDesign 已用 x6 画图 + flowApi 持久化；新增连接器后需联调节点配置面板。

## Risks
- Camel 动态路由需管理生命周期（start/stop/replace）。
- 连接器配置（密钥/endpoint）需加密存储（复用现有加密机制）。
