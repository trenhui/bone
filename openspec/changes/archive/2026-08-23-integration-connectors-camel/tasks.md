# Tasks: 集成连接器全量与 Camel 编排

## 1. 连接器补齐（按频率）
- [x] REST 连接器（已有 RestClientImpl，JDK HttpClient）
- [x] Redis 连接器（RedisClientImpl，RedisTemplate：ping + GET/SET/DELETE/EXPIRE）
- [x] ES 连接器（EsClientImpl，JDK HttpClient 调 ES REST：SEARCH/INDEX，零依赖）
- [x] Mongo 连接器（MongoClientImpl，mongodb-driver-sync：INSERT/DELETE/FIND）
- [x] S3 连接器（S3ClientImpl，MinIO SDK 兼容 S3：GET/PUT/LIST）
- [x] SOAP 连接器（SoapClientImpl，JDK HttpClient POST SOAP 信封，零依赖）

## 2. Camel 编排
- [x] `CamelFlowRuntime` 基于 RouteBuilder 动态构建路由（已有，CamelFlowCompiler）
- [x] 节点映射：start→from、task/http→to、branch→choice/when、parallel→multicast（已有）
- [x] **解除分支内禁嵌套**：runLinearChain 支持嵌套 DECISION/PARALLEL（runNestedDecision/runNestedParallel 递归 + Simple 求值）
- [x] 路由生命周期管理（start/stop/replace，removeRoute/compile 已有）

## 3. 前端联调
- [x] `ConnectorManagement.connectorTypes` 对齐后端 20 枚举（含 REDIS/ELASTICSEARCH/MONGO_DB/S3）
- [x] `FlowDesign.nodeTypes` 对齐后端 NodeType（补 DECISION/PARALLEL/TRANSFORM/LOG/WAIT/LOOP）
- [~] 节点配置面板接连接器选择 → **结构性缺口已记录**（FlowDesign 无独立节点配置面板；nodeTypes/connectorTypes 已对齐，连接器绑定 UI 属后续联调增强，不阻塞后端能力）

## 4. 校验
- [x] 每连接器实现编译通过 + 真实 testConnection/sendRequest（非 501）
- [x] Camel 分支/并行嵌套执行逻辑完成
- [x] mvn spotless + ArchUnit 18/18 通过；前端 tsc 通过
