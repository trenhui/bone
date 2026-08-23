# Quickstart: 集成连接器全量与 Camel 编排

## 前置
- `bone-integration`（8085）；Redis/ES/Mongo/S3 测试实例（按需）

## 本地验证
```bash
mvn -pl bone-platform/bone-integration -am clean install -DskipTests
cd bone-frontend/apps/bone-integration-app && npm run dev
```

## 关键入口
- 连接器：`bone-platform/bone-integration/.../infrastructure/connector/`
- 编排：`bone-platform/bone-integration/.../infrastructure/flow/CamelFlowRuntime.java`
