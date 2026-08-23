# Quickstart: 主数据标准与血缘

## 前置
- `bone-masterdata`（8084）

## 本地验证
```bash
mvn -pl bone-platform/bone-masterdata -am clean install -DskipTests
cd bone-frontend/apps/bone-masterdata-app && npm run dev
```

## 关键入口
- 标准：`bone-platform/bone-masterdata/.../domain/model/aggregate/DataStandard.java`
- 血缘：领域事件 handler 处发布 `DataLineageEvent`
