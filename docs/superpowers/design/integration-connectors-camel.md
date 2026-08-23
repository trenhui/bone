---
archived-with: 2026-08-23-integration-connectors-camel
status: final
---
# Design Doc: 集成连接器全量与 Camel 编排

> 注：本 doc 修正了 change.md/原 design.md 的过时假设。探查确认 `CamelFlowCompiler` 已实现 DECISION(choice)/PARALLEL(multicast) 分支/并行编排；真缺口是连接器实现类缺失 + 分支内禁嵌套 + 前端不对齐。

## 1. 背景与目标

`bone-integration`：`ExternalSystemClient` 接口 + `@Component(name)` + Spring `Map<String,ExternalSystemClient>` 注册。已有 REST 真实实现（`RestClientImpl`），FTP/JDBC/MQ 抛 501，Redis/ES/Mongo/S3/SOAP 无实现类。Camel 4.8 已启用，`CamelFlowCompiler` 已支持分支/并行但**分支内禁嵌套**。前端 FlowDesign 节点库硬编码 6 种且与后端 `NodeType` 不对齐。

目标：补 5 个连接器实现类（REST 已有 → 6 种真实调通）；解除分支内禁嵌套；前端节点库对齐 + 节点配置面板接连接器。

## 2. 关键决策

### Decision 1：连接器实现（基于 ExternalSystemClient，新增 5 个 @Component）
- `RedisClientImpl`（`@Component("REDIS")`）：用 `RedisTemplate`（pom 已有 spring-boot-starter-data-redis），`testConnection` 用 `redisTemplate.getConnectionFactory().getConnection().ping()`；`sendRequest` 按 config 的 `operation`(GET/SET/DELETE) + `key` + `value` 执行。
- `EsClientImpl`（`@Component("ELASTICSEARCH")`）：用 `org.elasticsearch.client.RestClient`（新增 `elasticsearch-rest-client`），`testConnection` 发 `GET /` 集群信息；`sendRequest` 按 `index`/`operation`(SEARCH/GET/INDEX)。
- `MongoClientImpl`（`@Component("MONGO_DB")`）：用 `com.mongodb.client.MongoClient`（新增 `mongodb-driver-sync`），`testConnection` 执行 `ping`；`sendRequest` 按 `database`/`collection`/`operation`。
- `S3ClientImpl`（`@Component("S3")`）：用 `software.amazon.awssdk:s3`（MinIO 兼容，endpoint/region/accessKey/secretKey），`testConnection` 调 `listBuckets`；`sendRequest` 按 `bucket`/`key`/`operation`(GET/PUT)。
- `SoapClientImpl`（`@Component("SOAP")`）：用 JDK 内置 `javax.xml.soap.SOAPConnectionFactory`，`sendRequest` 发送 SOAPMessage 到 `endpoint`；`testConnection` 尝试 HTTP 连接。

### Decision 2：Camel 编排——解除嵌套限制
- `CamelFlowCompiler.runLinearChain` 目前抛「分支内暂不支持嵌套」。改为支持嵌套：在分支子链构造时**递归调用 appendFromNode**（而非 runLinearChain 线性），使 DECISION/PARALLEL 可在分支内嵌套。
- 保持 `choice().when/otherwise`、`multicast` 语义。

### Decision 3：前端对齐
- `ConnectorManagement.connectorTypes` 硬编码 5 种 → 对齐后端 20 枚举（引入共享枚举或硬编码完整列表）。
- `FlowDesign` 节点库 `nodeTypes` 从 6 种硬编码对齐后端 `NodeType`（补 DECISION/PARALLEL，修正 CONDITION→DECISION、DB→JDBC）。
- 节点配置面板：新建/编辑节点时可选绑定连接器（复用 `connectorApi.getConnectors`）。

## 3. 目录结构（新增）

```
bone-integration/src/main/java/com/bone/integration/infrastructure/external/
  RedisClientImpl.java
  EsClientImpl.java
  MongoClientImpl.java
  S3ClientImpl.java
  SoapClientImpl.java
```
（CamelFlowCompiler 改 runLinearChain 支持嵌套）

## 4. 风险

- ES/Mongo/S3 需新增 pom 依赖（elasticsearch-rest-client / mongodb-driver-sync / aws-sdk2-s3）；无真实服务时 testConnection 返回失败/异常（非 501）。
- 嵌套编排复杂度提升，需保证 choice/multicast 嵌套正确。
- 前端对齐 NodeType 需同步 drag 类型与后端。

## 5. 验收对照

- REST/Redis/ES/Mongo/S3/SOAP 6 类有真实 `ExternalSystemClient` 实现，`ConnectorService.resolveClient` 不再抛不支持。
- DECISION/PARALLEL 分支内可嵌套执行。
- 前端 nodeTypes 对齐 NodeType，节点面板可绑连接器。
