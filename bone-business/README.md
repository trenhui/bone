# bone-business

业务域目录。根 Maven 聚合 [`pom.xml`](./pom.xml) 当前**未挂载子 module**；行业包以独立工程形式存放，按需单独构建。

## TPA（在役 + 迁移规划）

| 路径 | 角色 | 迁移方向 |
|------|------|----------|
| [`tpa-saas/`](./tpa-saas/) | 后端（Java，在役） | 逐步迁至 [`tpa-go/`](./tpa-go/) |
| [`tpa-go/`](./tpa-go/) | 后端（Go，阶段 1 骨架） | **目标主栈** |
| [`tpa-sass-react/`](./tpa-sass-react/) | 管理端（React） | **目标唯一前端** |
| [`tpa-sass-vue3/`](./tpa-sass-vue3/) | 管理端（Vue3） | 维护至 React parity 后下线 |

详细阶段与原则：[**TPA-MIGRATION.md**](./TPA-MIGRATION.md)。

```bash
# TPA 后端 Java
cd tpa-saas && mvn clean install -DskipTests

# TPA 后端 Go（骨架）
cd tpa-go && make tidy && make run

# TPA 前端
cd tpa-sass-react && npm install && npm run dev   # 目标栈，新功能优先
cd tpa-sass-vue3 && npm install && npm run dev    # 在役，维护/parity
```

平台内核与 Bone 主工程见根 [README.md](../README.md)、[doc/wiki/02-仓库结构与模块.md](../doc/wiki/02-仓库结构与模块.md)。
