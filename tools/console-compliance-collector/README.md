# tools/console-compliance-collector

`bone-system` 控制台模块的 Docs-as-Code 合规收集器（v2.0）。

## 作用

派生 **As-Is 证据**（控制台代码 / 权限码 / DDL）与 **Backlog**（`backlog.yaml`）写入 `doc/_generated/console/`：

- `compliance.json` — 结构化基线（CI `--check` 用作 diff 锚点）
- `as-is-evidence.md` — 当前能力清单
- `backlog.md` — [Target] / [Vision] 看板

## 用法

```bash
# 生成最新基线（默认）
python3 tools/console-compliance-collector/collect.py

# 校验基线最新（CI 用）
python3 tools/console-compliance-collector/collect.py --check
```

## 收集规则

`collect.py::build_as_is_checks` 派生 11 项 As-Is 检查：

| ID | 校验内容 |
|----|---------|
| `controller-single-source` | 仅 `bone-system` 有 `ConsoleController`；`bone-iam` 副本必须已删除 |
| `cqrs-handler` | `ConsoleOverviewQueryHandler` / `QuickActionsQueryHandler` 存在 |
| `gateway-ports` | 三类出站端口接口存在 |
| `graceful-degradation` | `JdbcKeyMetricsGatewayAdapter` 与 `singleTableFailureDoesNotPropagate` 测试存在 |
| `method-security` | `ConsoleController` `@PreAuthorize` + `EnableMethodSecurity` + `JwtAuthenticationFilter` + `ConsoleSecurityTest` |
| `frontend-route-guard` | `bone-shell` `Authorized` + `SYS_CONSOLE_READ` 路由守卫 + `jwt.test.ts` |
| `permission-code` | `sys:console:read` 在 DDL / 前端常量 / `DefaultPermissionCodes` / Controller 文档四处对齐 |
| `domain-read-vos` | 5 个读侧值对象在 `domain.model.console` |
| `micrometer-counter` | `bone_console_overview_refresh_total` 计数器存在 |
| `openapi-spec` | `doc/architecture/openapi/console-v1.yaml` 存在且含 5 个 GET 路径 |
| `cnsl-ddl-only` | `cnsl_*` 三表在 DDL 但运行时无 Java 引用 |

OpenAPI 校验：`bash scripts/ci/validate-console-openapi.sh`

## 维护

- 新增 [Target] / [Vision] 项 → 改 `backlog.yaml`，重新跑 `collect.py`
- 落地为 As-Is → 在 `backlog.yaml` 删除该行，同步详设 §1.4 矩阵
- CI 在 `.github/workflows/docs-compliance.yml` 中调用 `--check`
