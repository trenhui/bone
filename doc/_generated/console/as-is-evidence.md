# bone-system 控制台 As-Is 证据（CI 派生）

> **生成时间**：2026-09-20T08:09:00Z（UTC）
> **真源**：`bone-platform/bone-system/.../adapter/web/controller/ConsoleController.java`

| ID | 能力 | 摘要 |
|----|------|------|
| `controller-single-source` | ConsoleController 单一真源（bone-system） | controller: 1 · iam_duplicate_removed: ✓ |
| `application-entry` | 控制台用例入口 ConsoleApplicationService（ADR-0028） | java: 1 |
| `gateway-ports` | 出站端口（ServiceHealth/ResourceUsage/KeyMetrics） | java: 8 |
| `graceful-degradation` | keyMetrics graceful degradation（单表异常容错为 0） | java: 1 · test: 1 |
| `method-security` | 方法级 @PreAuthorize('sys:console:read') + SecurityConfig | preauthorize_on_controller: ✓ · enable_method_security: ✓ · jwt_filter: 1 · security_test: 1 |
| `frontend-route-guard` | bone-shell 首页 SYS_CONSOLE_READ 路由守卫 | authorized_component: ✓ · sys_console_read_in_app: ✓ · jwt_test: ✓ |
| `permission-code` | sys:console:read 权限码四处对齐 | ddl_id_18: ✓ · frontend_const: ✓ · admin_fallback: ✓ · controller_doc: 2 |
| `domain-read-vos` | 读侧值对象位于 domain.console（按聚合平铺形态，E-10） | java: 5 |
| `micrometer-counter` | 概览刷新 Micrometer 计数器（SLO 真源） | java: 1 |
| `openapi-spec` | OpenAPI 契约 console-v1.yaml（5 个 GET 端点） | file_exists: ✓ · paths_overview: ✓ · paths_quick_actions: ✓ · permission_in_yaml: ✓ · preauthorize_as_is_in_yaml: ✓ · bearer_security: ✓ |
| `cnsl-ddl-only` | cnsl_* 表 DDL-only（运行时无引用） | ddl_present: ✓ · no_java_reference: ✓ |
