# Bone 测试策略

> **文档性质**：单测、集成、契约、架构与 E2E 的**分层边界与门禁**。  
> **更新**：2026-05-17  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md) §15、[Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md)

---

## 1. 测试金字塔

```
        E2E（少量，发布前）
      契约 / 集成（模块 verify）
    单测 + ArchUnit（每次 PR）
```

| 层级 | 占比目标 | 速度 |
|------|----------|------|
| 单测 | 70%+ 用例数 | 秒级 |
| 集成 | 关键路径 | 分钟级 |
| 契约 | API 变更 | 分钟级 |
| E2E | 核心旅程 | 十分钟级 |

---

## 2. 单测

| 项 | 规则 |
|----|------|
| 框架 | JUnit 5 + Mockito |
| 命名 | `*Test.java` / `*Tests.java` |
| Domain | 纯逻辑，无 Spring 上下文 |
| Application | Mock Repository；测用例编排 |
| 覆盖率 | 模块 **≥80%**（L2 ≥85%，见 `CLAUDE.md`） |

---

## 3. 架构测试

| 模块 | 工具 | 内容 |
|------|------|------|
| bone-iam、bone-integration 等 | ArchUnit | 分层依赖、`*Service` 包位置 |
| 新增模块 | **必须** 提供 `ArchitectureTest` | 见 DDD 文档 |

---

## 4. 契约测试（HTTP）

真源与流程见 **[Bone-API-规范 §15](./Bone-API-规范.md#15-契约测试http--openapi)**。

| 类型 | 门禁 |
|------|------|
| openapi-diff | PR breaking 检测 |
| Provider（MockMvc） | 成功 + 典型 4xx |
| Pact | 对外 SDK / 跨团队 |

---

## 5. 集成测试

| 项 | 规则 |
|----|------|
| DB | Testcontainers MySQL 或 H2（仅非 MySQL 特性） |
| Redis | 嵌入式或 Testcontainers |
| 租户 | 每用例设置 `TenantContext`（见 [多租户规范](./Bone-多租户规范.md)） |

---

## 6. 前端

| 项 | 工具 |
|----|------|
| 单测 | Vitest |
| Lint | ESLint（`npm run lint`） |
| E2E | Playwright（关键路径，逐步） |

---

## 7. CI 映射

以 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) 为准：

| Job | 内容 |
|-----|------|
| `backend-quality` | `mvn spotless:check` + `mvn clean verify -DskipITs=true` |
| `backend-security` | OWASP Dependency-Check（`failBuildOnCVSS=7`） |
| `frontend-quality` | `bone-frontend` 下 lint + build；**CI 主栈 pnpm**（与本地 npm workspaces 共存，详见 [bone-前端架构 §8](./bone-前端架构.md)） |
| `docker-build` | 镜像构建（按需） |

**蓝图**：根聚合不含 `bone-blueprint`；本地 `mvn -f bone-blueprint/pom.xml test`（见 [wiki/08](../wiki/08-blueprint与主工程对齐.md)）。`scripts/ci-local.sh` 可能包含额外检查，注释以脚本为准。

---

## 8. PR 检查清单

- [ ] 新逻辑有单测  
- [ ] API 变更有契约/OpenAPI  
- [ ] 新错误码有断言  
- [ ] ArchUnit 通过（若适用）  
- [ ] 覆盖率未降（模块门禁）  

---

## 9. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版；与 API §15 分工 |
