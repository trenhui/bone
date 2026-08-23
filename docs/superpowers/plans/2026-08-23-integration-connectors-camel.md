---
archived-with: 2026-08-23-integration-connectors-camel
status: final
---
# Plan: 集成连接器全量与 Camel 编排

> 对应 `openspec/changes/integration-connectors-camel/tasks.md`。
> 设计：`docs/superpowers/design/integration-connectors-camel.md`

## 1. 连接器补齐（ExternalSystemClient 实现）
- [x] `RedisClientImpl`（@Component("REDIS")，RedisTemplate：testConnection ping + GET/SET/DELETE/EXPIRE）
- [x] `EsClientImpl`（@Component("ELASTICSEARCH")，JDK HttpClient 调 ES REST：SEARCH/INDEX/DELETE，零依赖）
- [x] `MongoClientImpl`（@Component("MONGO_DB")，mongodb-driver-sync：ping + INSERT/DELETE/FIND）
- [x] `S3ClientImpl`（@Component("S3")，MinIO SDK：listBuckets + GET/PUT/LIST）
- [x] `SoapClientImpl`（@Component("SOAP")，JDK HttpClient POST SOAP 信封：零依赖）
- [x] pom 新增 mongodb-driver-sync 4.11.1 + minio 8.5.7 依赖（REST 已有，Redis/ES/SOAP 零新依赖）

## 2. Camel 编排（解除嵌套限制）
- [x] `CamelFlowCompiler.runLinearChain` 支持 DECISION/PARALLEL 嵌套（runNestedDecision/runNestedParallel 递归 + evaluateCondition Simple 求值）
- [x] 分支/并行嵌套执行逻辑完成

## 3. 前端对齐
- [x] `ConnectorManagement.connectorTypes` 从 5 种对齐后端 20 枚举（含 REDIS/ELASTICSEARCH/MONGO_DB/S3）
- [x] `FlowDesign.nodeTypes` 对齐后端 NodeType（补 DECISION/PARALLEL/TRANSFORM/LOG/WAIT/LOOP，修正 CONDITION→DECISION、DB→JDBC）
- [~] 节点配置面板接连接器选择：FlowDesign 无独立节点配置面板（结构性缺口，nodeTypes 已对齐；连接器绑定 UI 待联调完善）

## 4. 校验
- [x] mvn spotless:apply + ArchUnit 18/18 通过（含 5 个新连接器 + Camel 嵌套）
- [x] 前端 tsc 通过（connectorTypes/nodeTypes 无错误）
