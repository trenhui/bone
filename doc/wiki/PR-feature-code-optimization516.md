# PR 合并前检查清单 · `feature/code-optimization516` → `master`

> **创建 MR**：https://gitee.com/meishan315/bone/pulls/new?source_branch=feature/code-optimization516&target_branch=master  
> **推送**（本机）：`git push -u origin feature/code-optimization516`

## 1. 变更摘要（36 commits · 推送前以 `git log master..HEAD` 为准）

| 类别 | 代表 commit | 说明 |
|------|-------------|------|
| 文档治理 | `7de6782d`、`6ccca2ff`、`722f07fd` | modules DRY、architecture As-Is 端口、OpenAPI 交叉引用 |
| IAM | `07fa6e95`、`89862c35`、`c61a829e` | Tenant CRUD、审计设置、RBAC JWT scopes、iam-v1.yaml |
| 主数据/集成 | `b56daa1e` | convert 幂等+发布校验、integration metrics、Gateway IT |
| 元数据/生成器/扩展 | `e826b7b5`、`ddd364e7`、`fd48127f` 等 | 引擎、Generator、Camel 路由 |
| 平台/构建 | `30cf9b51`、`2cfc9488`、`a7862ed8` | 模块顺序、bone-tool 移除、共享 ArchUnit |
| 前端 chore | `066f6dd0` | shared-services/utils ESLint、extension-api indent |

## 2. 本地验证（Agent 2026-05-21）

| 命令 | 结果 |
|------|------|
| `mvn install -DskipTests -pl bone-platform/bone-iam,... -am` | ✅ |
| `mvn test -pl bone-platform/bone-iam -am` | ✅ 18 tests |
| `mvn test -pl bone-platform/bone-masterdata -Dtest=MasterDataEntityControllerTest` | ✅ 7 tests（含 convert） |
| `mvn test -pl bone-platform/bone-gateway -Dtest=IamGatewayRouteIT,IntegrationGatewayRouteIT` | ✅ 10 tests |
| `mvn test -pl bone-platform/bone-masterdata -am`（全模块） | ⚠️ 10 errors（Mockito inline / `NoClassDefFound` 等**既有** Controller 测，非本次 convert 专项） |
| `mvn test -pl bone-platform/bone-integration -am` | ✅ 27 tests |
| `npm run lint`（bone-frontend） | ⚠️ `bone-generator-app` 等仍有 indent/quotes（合并前本机 `--fix` 或单独 chore） |

**合并前建议**（本机完整复现）：

```bash
mvn clean install -DskipTests
mvn test -pl bone-platform/bone-iam,bone-platform/bone-masterdata,bone-platform/bone-integration,bone-platform/bone-gateway -am
cd bone-frontend && npm ci && npm run lint && npm run test
```

## 3. 文档与契约抽检

- [ ] [wiki/03 端口表](./03-本地开发与构建.md) ↔ `BONE-总体架构` §22.3 As-Is 表
- [ ] [openapi/](../architecture/openapi/) `iam-v1` · `masterdata-v1` · `integration-v1` ↔ 各模块详设 §0 HTTP 真源
- [ ] [07-P0-TODO](./07-P0-TODO看板.md) MD-04/05、IAM-06/07/09 与实现一致
- [ ] `bone-init.sql` 若含 `iam_audit_settings` 等增量，与 IAM 实现同步

## 4. 风险与评审焦点

| 项 | 级别 | 说明 |
|----|------|------|
| 分支体量 | 高 | 33 commits 跨文档+多模块；可考虑拆「仅 docs」与「feat」两个 MR |
| 8085 端口冲突 | 中 | integration vs studio-generator 同机勿双启 |
| ADR `0002` 双文件 | 低 | 引用时用完整文件名 |
| masterdata 全量单测 | 中 | 合并前建议修 Mockito/类路径或 `-rf` 单独模块 |

## 5. 合并后

- [ ] 更新 [07-P0-TODO](./07-P0-TODO看板.md) 剩余 open 项（IAM-08 SSO/MFA 等）
- [ ] Gitee 打 tag / 发布说明（可选）
