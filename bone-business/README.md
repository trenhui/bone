# bone-business

业务域目录。根 Maven 聚合 [`pom.xml`](./pom.xml) 当前**未挂载子 module**；行业包以独立工程形式存放，按需单独构建。

| 路径 | 说明 |
|------|------|
| [`tpa-saas/`](./tpa-saas/) | TPA SaaS 后端（`com.pkh.cloud`，独立 Maven 树） |
| [`tpa-sass-vue3/`](./tpa-sass-vue3/) | TPA 管理端前端（Vue3 + Vite） |
| [`tpa-sass-react/`](./tpa-sass-react/) | TPA React 前端（按需启用） |

```bash
# TPA 后端（在 tpa-saas 目录）
cd tpa-saas && mvn clean install -DskipTests

# TPA 前端（在对应子目录）
cd tpa-sass-vue3 && npm install && npm run dev
```

平台内核与 Bone 主工程见根 [README.md](../README.md)、[doc/wiki/02-仓库结构与模块.md](../doc/wiki/02-仓库结构与模块.md)。
