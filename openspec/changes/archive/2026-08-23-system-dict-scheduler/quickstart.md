# Quickstart: 系统字典与定时任务

## 前置
- 同 IAM；`bone-system` 模块（8083）

## 本地验证
```bash
mvn -pl bone-platform/bone-system -am clean install -DskipTests
cd bone-frontend/apps/bone-system-app && npm run dev
```

## 关键入口
- 调度器：`bone-platform/bone-system/.../infrastructure/scheduler/DynamicScheduler.java`
- 前端页：`bone-frontend/apps/bone-system-app/src/pages/DictManagement.tsx`
