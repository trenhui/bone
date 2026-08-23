# Quickstart: Shell 动态化与测试补齐

> 依赖 iam-org-menu-baseline 完成后验证。

## 本地验证
```bash
cd bone-frontend/apps/bone-shell && npm run dev
# 登录后侧边栏应来自 IAM 动态菜单
```

## 关键入口
- 菜单加载：`bone-frontend/apps/bone-shell/src/menu/`（动态拉取）
- 路由守卫：`bone-frontend/apps/bone-shell/src/router/`
- 测试：`bone-frontend/apps/bone-shell/src/**/*.test.ts`
