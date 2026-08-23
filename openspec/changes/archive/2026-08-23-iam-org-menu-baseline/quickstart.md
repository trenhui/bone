# Quickstart: IAM 组织树与菜单基座

## 前置
- JDK 17+、Maven、MySQL、Redis 已启动
- `feature/code-optimization614` 分支（当前工作分支）

## 本地验证
```bash
# 后端编译
mvn -pl bone-platform/bone-iam -am clean install -DskipTests
# 启动 iam (8081)
# 前端
cd bone-frontend/apps/bone-iam-app && npm run dev
```

## 关键入口
- 后端聚合：`bone-platform/bone-iam/.../domain/model/aggregate/Dept.java`、`Menu.java`
- 前端页面：`bone-frontend/apps/bone-iam-app/src/pages/OrganizationManagement.tsx`
