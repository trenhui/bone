## 1. 现状与规格确认

- [x] 1.1 核对 `claim/ai-knowledge-rag` 现有 spec 四条 REQUIREMENT 与适配器实现一致；验收：逐条确认并引用文件+行号 <!-- comet-task:fa14433d-7c48-4b0e-8766-5f5b13fbd3a2 -->
- [x] 1.2 确认门控 `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED` 默认 false 且为唯一开关；验收：grep 确认 <!-- comet-task:873db42c-3ebf-4ca1-acd2-1ac3d0a2863d -->

## 2. 门控双态链路验证（内部模拟 provider）

- [x] 2.1 门控关闭态：验证知识层行为与现网完全一致、无检索调用；验收：关闭态测试通过 <!-- comet-task:e92f29b3-7a22-4535-95ca-a3fef30b4efe -->
- [x] 2.2 门控打开态：用内部模拟 embedding/rerank 端点跑通检索→引用→ACL 链路；验收：返回带可追溯引用，测试通过 <!-- comet-task:b8adb0e9-425d-4dcc-9770-7cebcad3eafd -->
- [x] 2.3 引用强制 fail-closed：无引用检索结果被拒、不进入知识依据；验收：注入无引用场景测试通过 <!-- comet-task:559f6a98-30fc-47f5-aeaa-0daa18c8af7f -->
- [x] 2.4 ACL deny 生效：默认拒绝策略不被绕过；验收：ACL 场景测试通过 <!-- comet-task:a95e682b-c458-4942-95fb-a5233d67aaf3 -->
- [x] 2.5 语料缺失/服务不可用返回 available=false 带成因码；验收：缺语料场景测试通过 <!-- comet-task:50427780-f40c-4736-aba8-f6896c413c2c -->

## 3. 工程证据与门禁

- [x] 3.1 所有内部模拟证据标注 internal-simulated、不接真实凭证；验收：grep 确认无真实凭证、证据含标注 <!-- comet-task:157fe0f7-3027-4bf2-83b1-09cb35829da7 -->
- [x] 3.2 确认不放宽 12 项 AI 原生就绪判据；验收：复核器判据本体未改 <!-- comet-task:19bc27e4-20f2-442b-9f10-5ee51614b6ef -->
- [x] 3.3 补变异双向钉子（门控双态/引用强制/ACL）；验收：改一处必红还原必绿 <!-- comet-task:040b4e34-e50c-4d91-970b-b1f7b61093da -->
- [x] 3.4 跑相关测试全绿；验收：mvn 测试通过，测试数从收据取真实值 <!-- comet-task:8594a407-2df0-428d-bb2f-4c61b0d9508c -->
