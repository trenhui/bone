# 08 — Blueprint 与主工程对齐

[← Wiki 首页](./README.md)

---

## 定位

| 工程 | 角色 |
|------|------|
| **根聚合 `bone-*` 模块** | 可运行平台（framework / engine / platform） |
| **`bone-blueprint/`** | **DDD + CQRS 参考实现**（订单等领域示例），**默认不**列入根 `pom.xml` `<modules>` |

蓝图用于：ArchUnit 规则样例、Handler/聚合命名、与 [Bone-DDD 最终实践方案](../architecture/Bone-DDD-最终实践方案.md) 对照。

---

## 本地构建

```bash
# 仅蓝图（独立 POM，不依赖根聚合 install）
mvn -f bone-blueprint/pom.xml clean test
```

全平台仍用：

```bash
mvn clean install -DskipTests=true
```

---

## CI

根仓 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) **未**单独跑 `bone-blueprint`；发布前建议本地或流水线追加 `mvn -f bone-blueprint/pom.xml test`。

---

## 对齐检查清单（发版或大重构前）

- [ ] `bone-blueprint` 包结构仍符合 DDD 四层（adapter / application / domain / infrastructure）  
- [ ] `ArchitectureTest` 通过  
- [ ] 命名与正式 PRD/平台模块 Handler 约定一致（`*CommandHandler` / `*QueryHandler`）  
- [ ] 持久化路径与 **Bone Metadata SDK** 规约一致（见 AGENTS.md §5.1.1）  
- [ ] 若平台层 API 变更，同步更新蓝图示例或 README 说明差异  

---

## 相关文档

- [02-仓库结构与模块](./02-仓库结构与模块.md)  
- [bone-blueprint/README.md](../../bone-blueprint/README.md) — 如何使用参考实现（极简 vs 全量演示）  
- [Bone-DDD 最终实践方案](../architecture/Bone-DDD-最终实践方案.md) — **唯一架构门禁**（附录 A：废止 Blueprint 版本号）  
- [CONTRIBUTING.md](../../CONTRIBUTING.md)
