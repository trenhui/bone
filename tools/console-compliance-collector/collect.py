#!/usr/bin/env python3
"""bone-system 控制台模块 Docs-as-Code 合规收集器（v2.0）。

派生 As-Is 证据（源码扫描 + 权限码 + 真实代码引用）与 [Target]/[Vision]
Backlog（backlog.yaml），写入 doc/_generated/console/。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COLLECTOR_DIR = Path(__file__).resolve().parent
OUT_DIR = ROOT / "doc" / "_generated" / "console"
BACKLOG_YAML = COLLECTOR_DIR / "backlog.yaml"

SYS_MAIN = ROOT / "bone-platform" / "bone-system" / "src" / "main" / "java"
SYS_TEST = ROOT / "bone-platform" / "bone-system" / "src" / "test" / "java"
INIT_SQL = ROOT / "bone-init.sql"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "console-v1.yaml"
PERM_TS = ROOT / "bone-frontend" / "packages" / "shared-types" / "src" / "bonePermissionCodes.ts"
DEFAULT_PERM = (
    ROOT
    / "bone-platform"
    / "bone-iam"
    / "src"
    / "main"
    / "java"
    / "com"
    / "bone"
    / "iam"
    / "domain"
    / "permission"
    / "DefaultPermissionCodes.java"
)


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def _grep_files(root: Path, pattern: str) -> list[str]:
    hits: list[str] = []
    if not root.exists():
        return hits
    rx = re.compile(pattern)
    for p in root.glob("**/*.java"):
        try:
            if rx.search(p.read_text(encoding="utf-8")):
                hits.append(str(p.relative_to(ROOT)))
        except OSError:
            pass
    return sorted(hits)


def load_backlog() -> list[dict]:
    text = _read(BACKLOG_YAML)
    items: list[dict] = []
    current: dict | None = None
    for line in text.splitlines():
        if line.strip().startswith("- id:"):
            if current:
                items.append(current)
            current = {"id": line.split(":", 1)[1].strip()}
        elif current is not None and ":" in line:
            key, _, val = line.partition(":")
            key, val = key.strip(), val.strip()
            if key in ("id", "tier", "title", "reference", "tracking"):
                current[key] = val
    if current:
        items.append(current)
    return items


def build_as_is_checks() -> list[dict]:
    sql = _read(INIT_SQL)
    perm_ts = _read(PERM_TS)
    default_perm = _read(DEFAULT_PERM)
    return [
        {
            "id": "controller-single-source",
            "title": "ConsoleController 单一真源（bone-system）",
            "status": "as_is",
            "evidence": {
                "controller": _grep_files(SYS_MAIN, r"class ConsoleController\b"),
                "iam_duplicate_removed": not _grep_files(
                    ROOT / "bone-platform" / "bone-iam" / "src" / "main" / "java",
                    r"class ConsoleController\b",
                ),
            },
        },
        {
            "id": "application-entry",
            "title": "控制台用例入口 ConsoleApplicationService（ADR-0028）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(SYS_MAIN, r"class ConsoleApplicationService\b"),
            },
        },
        {
            "id": "gateway-ports",
            "title": "出站端口（ServiceHealth/ResourceUsage/KeyMetrics）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SYS_MAIN, r"ServiceHealthGateway|ResourceUsageGateway|KeyMetricsGateway"
                ),
            },
        },
        {
            "id": "graceful-degradation",
            "title": "keyMetrics graceful degradation（单表异常容错为 0）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(SYS_MAIN, r"JdbcKeyMetricsGateway"),
                "test": _grep_files(SYS_TEST, r"singleTableFailureDoesNotPropagate"),
            },
        },
        {
            "id": "method-security",
            "title": "方法级 @PreAuthorize('sys:console:read') + SecurityConfig",
            "status": "as_is",
            "evidence": {
                "preauthorize_on_controller": "@PreAuthorize" in _read(
                    SYS_MAIN
                    / "com"
                    / "bone"
                    / "system"
                    / "adapter"
                    / "web"
                    / "controller"
                    / "ConsoleController.java"
                ),
                "enable_method_security": bool(
                    _grep_files(SYS_MAIN, r"EnableMethodSecurity")
                ),
                "jwt_filter": _grep_files(SYS_MAIN, r"class JwtAuthenticationFilter\b"),
                "security_test": _grep_files(SYS_TEST, r"ConsoleSecurityTest"),
            },
        },
        {
            "id": "frontend-route-guard",
            "title": "bone-shell 首页 SYS_CONSOLE_READ 路由守卫",
            "status": "as_is",
            "evidence": {
                "authorized_component": (
                    ROOT
                    / "bone-frontend"
                    / "apps"
                    / "bone-shell"
                    / "src"
                    / "auth"
                    / "Authorized.tsx"
                ).is_file(),
                "sys_console_read_in_app": "SYS_CONSOLE_READ" in _read(
                    ROOT / "bone-frontend" / "apps" / "bone-shell" / "src" / "App.tsx"
                ),
                "jwt_test": (
                    ROOT
                    / "bone-frontend"
                    / "apps"
                    / "bone-shell"
                    / "src"
                    / "auth"
                    / "jwt.test.ts"
                ).is_file(),
            },
        },
        {
            "id": "permission-code",
            "title": "sys:console:read 权限码四处对齐",
            "status": "as_is",
            "evidence": {
                "ddl_id_18": bool(re.search(r"sys:console:read", sql)),
                "frontend_const": "SYS_CONSOLE_READ" in perm_ts,
                "admin_fallback": "sys:console:read" in default_perm,
                "controller_doc": _grep_files(SYS_MAIN, r"sys:console:read"),
            },
        },
        {
            "id": "domain-read-vos",
            "title": "读侧值对象位于 domain.console（按聚合平铺形态，E-10）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SYS_MAIN,
                    r"class (ConsoleOverview|ServiceStatus|ResourceUsage|KeyMetrics|QuickAction)\b",
                ),
            },
        },
        {
            "id": "micrometer-counter",
            "title": "概览刷新 Micrometer 计数器（SLO 真源）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(SYS_MAIN, r"bone_console_overview_refresh_total"),
            },
        },
        {
            "id": "openapi-spec",
            "title": "OpenAPI 契约 console-v1.yaml（5 个 GET 端点）",
            "status": "as_is",
            "evidence": {
                "file_exists": OPENAPI.is_file(),
                "paths_overview": "/overview:" in _read(OPENAPI),
                "paths_quick_actions": "/quick-actions:" in _read(OPENAPI),
                "permission_in_yaml": "sys:console:read" in _read(OPENAPI),
                "preauthorize_as_is_in_yaml": "鉴权落地（As-Is）" in _read(OPENAPI),
                "bearer_security": "bearerAuth" in _read(OPENAPI),
            },
        },
        {
            "id": "cnsl-ddl-only",
            "title": "cnsl_* 表 DDL-only（运行时无引用）",
            "status": "as_is",
            "evidence": {
                "ddl_present": all(
                    t in sql for t in ("cnsl_dashboard_widget", "cnsl_notification", "cnsl_recent_access")
                ),
                "no_java_reference": not _grep_files(
                    SYS_MAIN, r"cnsl_dashboard_widget|cnsl_notification|cnsl_recent_access"
                ),
            },
        },
    ]


def render_as_is_md(checks: list[dict], generated_at: str) -> str:
    lines = [
        "# bone-system 控制台 As-Is 证据（CI 派生）",
        "",
        f"> **生成时间**：{generated_at}（UTC）",
        "> **真源**：`bone-platform/bone-system/.../adapter/web/controller/ConsoleController.java`",
        "",
        "| ID | 能力 | 摘要 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        parts: list[str] = []
        for k, v in ev.items():
            if isinstance(v, list):
                parts.append(f"{k}: {len(v)}")
            elif isinstance(v, bool):
                parts.append(f"{k}: {'✓' if v else '✗'}")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(parts) or '—'} |")
    return "\n".join(lines) + "\n"


def render_backlog_md(items: list[dict], generated_at: str) -> str:
    lines = [
        "# bone-system 控制台 [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）",
        f"> **维护源**：[`backlog.yaml`](../../../tools/console-compliance-collector/backlog.yaml)",
        "",
        "| Tier | ID | 项 | 引用 | 跟踪 |",
        "|------|-----|-----|------|------|",
    ]
    for it in items:
        lines.append(
            f"| **{it.get('tier', 'Target')}** | `{it.get('id', '')}` | "
            f"{it.get('title', '')} | {it.get('reference', '')} | {it.get('tracking', '')} |"
        )
    return "\n".join(lines) + "\n"


def write_outputs(checks: list[dict], backlog: list[dict], generated_at: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": generated_at,
        "module": "console",
        "as_is": checks,
        "backlog": backlog,
    }
    (OUT_DIR / "compliance.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (OUT_DIR / "as-is-evidence.md").write_text(
        render_as_is_md(checks, generated_at), encoding="utf-8"
    )
    (OUT_DIR / "backlog.md").write_text(
        render_backlog_md(backlog, generated_at), encoding="utf-8"
    )


def check_outputs(checks: list[dict], backlog: list[dict]) -> int:
    path = OUT_DIR / "compliance.json"
    if not path.exists():
        print("[check] missing compliance.json", file=sys.stderr)
        return 1
    existing = json.loads(path.read_text(encoding="utf-8"))
    if {"as_is": checks, "backlog": backlog} != {
        "as_is": existing.get("as_is"),
        "backlog": existing.get("backlog"),
    }:
        print("[check] stale — run collect.py", file=sys.stderr)
        return 1
    print("[check] console compliance.json is up to date")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    checks = build_as_is_checks()
    backlog = load_backlog()
    if args.check:
        return check_outputs(checks, backlog)
    write_outputs(checks, backlog, generated_at)
    print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
