# TPA 管理端（React）

React + TypeScript + Vite 实现的 TPA 管理端（**在役**）。

**迁移目标**：成为 TPA **唯一**管理端；新功能只在本仓库开发。Vue3 实现见 [`../tpa-sass-vue3/`](../tpa-sass-vue3/)（维护期）。路线见 [`../TPA-MIGRATION.md`](../TPA-MIGRATION.md)。

## 本地运行

```bash
npm install
npm run dev
```

当前对接 Java 后端 [`../tpa-saas/`](../tpa-saas/)；Go 后端就绪后通过环境变量切换 API 基址（见 `vite` / `.env*`）。
