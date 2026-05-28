#!/usr/bin/env python3
"""
IAM 模块 Docs-as-Code 合规收集器。

派生 As-Is 证据（OpenAPI + 源码扫描 + DDL 扫描）与 [Target]/[Vision] Backlog
（backlog.yaml），写入 doc/_generated/iam/，并可同步详设附录 A/C 中的 GENERATED
标记块。

用法:
  python3 tools/iam-compliance-collector/collect.py
  python3 tools/iam-compliance-collector/collect.py --sync-doc
  python3 tools/iam-compliance-collector/collect.py --check
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
OUT_DIR = ROOT / "doc" / "_generated" / "iam"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "iam-v1.yaml"
IAM_MAIN = ROOT / "bone-platform" / "bone-iam" / "src" / "main" / "java"
IAM_TEST = ROOT / "bone-platform" / "bone-iam" / "src" / "test" / "java"
INIT_SQL = ROOT / "bone-init.sql"
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "6. IAM账号权限管理模块详细设计方案.md"
BACKLOG_YAML = COLLECTOR_DIR / "backlog.yaml"

AS_IS_START = "<!-- IAM_COMPLIANCE_ASIS_BODY:START -->"
AS_IS_END = "<!-- IAM_COMPLIANCE_ASIS_BODY:END -->"
BACKLOG_START = "<!-- IAM_COMPLIANCE_BACKLOG_BODY:START -->"
BACKLOG_END = "<!-- IAM_COMPLIANCE_BACKLOG_BODY:END -->"

IAM_TABLES = [
    "iam_tenant",
    "iam_account",
    "iam_role",
    "iam_permission",
    "iam_account_role",
    "iam_role_permission",
    "iam_audit_log",
    "iam_audit_settings",
    "iam_policy",
    "iam_refresh_token",
]


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def _grep_files(root: Path, pattern: str, glob: str = "**/*.java") -> list[str]:
    hits: list[str] = []
    if not root.exists():
        return hits
    rx = re.compile(pattern)
    for p in root.glob(glob):
        try:
            text = p.read_text(encoding="utf-8")
        except OSError:
            continue
        if rx.search(text):
            hits.append(str(p.relative_to(ROOT)))
    return sorted(hits)


def count_preauthorize() -> dict:
    """统计 Controller 中 @PreAuthorize 数量及覆盖的权限码。"""
    if not IAM_MAIN.exists():
        return {"files": [], "scopes": []}
    rx_method = re.compile(r"@PreAuthorize\(\"hasAuthority\('([^']+)'\)\"\)")
    files: set[str] = set()
    scopes: set[str] = set()
    for p in IAM_MAIN.glob("**/adapter/web/controller/*.java"):
        text = p.read_text(encoding="utf-8")
        matches = rx_method.findall(text)
        if matches:
            files.add(str(p.relative_to(ROOT)))
            scopes.update(matches)
    return {
        "files": sorted(files),
        "scopes": sorted(scopes),
    }


def scan_iam_tables() -> dict:
    text = _read(INIT_SQL)
    present = sorted(t for t in IAM_TABLES if f"CREATE TABLE {t} " in text or f"CREATE TABLE {t}\n" in text)
    missing = sorted(set(IAM_TABLES) - set(present))
    return {"expected": IAM_TABLES, "present": present, "missing": missing}


def scan_openapi_paths() -> dict:
    text = _read(OPENAPI)
    paths: list[str] = []
    in_paths = False
    for line in text.splitlines():
        if line.startswith("paths:"):
            in_paths = True
            continue
        if in_paths:
            m = re.match(r"^  (/[^\s:]+):\s*$", line)
            if m:
                paths.append(m.group(1))
            elif line and not line.startswith(" "):
                break
    has_bearer = "bearerAuth" in text
    return {
        "openapi_file": str(OPENAPI.relative_to(ROOT)),
        "path_count": len(paths),
        "paths": paths,
        "security_bearer_jwt": has_bearer,
    }


def build_as_is_checks() -> list[dict]:
    pre_auth = count_preauthorize()
    ddl = scan_iam_tables()
    openapi = scan_openapi_paths()
    return [
        {
            "id": "iam-ddl-tables",
            "title": "IAM 10 张表全部入 bone-init.sql（含 iam_audit_settings）",
            "status": "as_is",
            "evidence": ddl,
        },
        {
            "id": "iam-openapi-single-source",
            "title": "iam-v1.yaml 作为 HTTP 契约单源 + bearer JWT 安全声明",
            "status": "as_is",
            "evidence": openapi,
        },
        {
            "id": "iam-method-security",
            "title": "@EnableMethodSecurity + @PreAuthorize 方法级鉴权",
            "status": "as_is",
            "evidence": {
                "security_config": _grep_files(
                    IAM_MAIN, r"@EnableMethodSecurity"
                ),
                "preauthorize": pre_auth,
            },
        },
        {
            "id": "iam-tenant-context-from-jwt",
            "title": "JwtAuthenticationFilter 写入 TenantContext + finally 清理",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"TenantContext\.setTenantId|TenantContext\.clear",
                ),
            },
        },
        {
            "id": "iam-tenant-query-filter",
            "title": "Query Handler 强制按 TenantContext 过滤（非平台租户）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"resolveTenantFilter|TenantContext\.getTenantId"),
            },
        },
        {
            "id": "iam-jwt-scopes-claim",
            "title": "JWT claim `scopes`（权限码）+ refresh token + 黑名单",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r'claim\("scopes"|JwtTokenService|RefreshTokenService|TokenBlacklistService',
                ),
            },
        },
        {
            "id": "iam-audit-settings",
            "title": "审计设置 GET/PUT 经 Handler + AuditSettingsStore",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"GetAuditSettingsQueryHandler|UpdateAuditSettingsCommandHandler|AuditSettingsStore",
                ),
                "ddl_has_table": "iam_audit_settings" in ddl["present"],
            },
        },
        {
            "id": "iam-password-policy",
            "title": "弱口令策略 + 登录 requirePasswordChange",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"PasswordPolicyValidator|requirePasswordChange"),
            },
        },
        {
            "id": "iam-refresh-reuse",
            "title": "Refresh Token 复用检测（replaced_by）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN, r"REFRESH_TOKEN_REUSE|replaced_by|revokeAllActiveForAccount"
                ),
            },
        },
        {
            "id": "iam-role-tenant-guard",
            "title": "角色权限分配租户一致性校验",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN, r"assertCallerMayManageRole|TENANT_ACCESS_DENIED"
                ),
            },
        },
        {
            "id": "iam-audit-cleanup-job",
            "title": "审计日志按保留天数清理 Job",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"AuditLogCleanupJob"),
            },
        },
        {
            "id": "iam-tenant-controller",
            "title": "TenantController CRUD + 启停 + DELETE + /quota",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"class TenantController\b|DeleteTenantCommandHandler|UpdateTenantQuotaCommandHandler",
                ),
            },
        },
        {
            "id": "iam-tenant-quota",
            "title": "租户配额列 + TenantQuotaEnforcer",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"TenantQuotaEnforcer|max_accounts|max_roles"),
                "ddl": "max_accounts" in _read(INIT_SQL),
            },
        },
        {
            "id": "iam-init-demo-password-gate",
            "title": "init 演示弱口令 CI ACK 门禁",
            "status": "as_is",
            "evidence": {
                "script": ["scripts/ci/check-iam-init-demo-password.sh"],
                "marker": "BONE_IAM_DEMO_PASSWORD_ACK" in _read(INIT_SQL),
            },
        },
        {
            "id": "iam-login-lockout",
            "title": "登录失败锁定（threshold + lockMinutes 可配，含 LOCKED 状态校验）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"recordLoginFailure|IamPasswordProperties|ACCOUNT_LOCKED|isLocked",
                ),
            },
        },
        {
            "id": "iam-role-hierarchy",
            "title": "角色继承闭包求值（parent_role_id，最大深度 5，环检测）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"RoleHierarchyResolver|resolveClosure"),
            },
        },
        {
            "id": "iam-sessions-api",
            "title": "会话管理 API（在线会话 + 强制下线，iam:sessions:* 权限码）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"SessionController|RefreshTokenSessionStore|RevokeSessionCommandHandler|iam:sessions",
                ),
            },
        },
        {
            "id": "iam-me-self-service",
            "title": "个人信息自助 API（/me + /me/change-password，仅认证不需权限码）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"MeController|ChangeMyPasswordCommandHandler|UpdateMyProfileCommandHandler|CurrentAccountResolver",
                ),
            },
        },
        {
            "id": "iam-password-expiry",
            "title": "密码到期策略（max-age-days → requirePasswordChange）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"maxAgeDays|passwordUpdatedAt|isPasswordExpired"),
            },
        },
        {
            "id": "iam-audit-csv-export",
            "title": "审计日志 CSV 导出（UTF-8 BOM + Content-Disposition）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_MAIN, r"text/csv|CSV_HEADER|iam-audit-logs\\.csv"),
            },
        },
        {
            "id": "iam-mfa-sso-501-contract",
            "title": "MFA / SSO 未启用 501 契约（社区版）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    IAM_MAIN,
                    r"IAM_SSO_NOT_CONFIGURED|MFA_NOT_AVAILABLE|MfaController|ssoCallback",
                ),
            },
        },
        {
            "id": "iam-archunit",
            "title": "ArchUnit 分层 + 仓储白名单",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(IAM_TEST, r"BoneDddArchRules|ArchitectureTest"),
            },
        },
        {
            "id": "iam-gateway-route-it",
            "title": "Gateway → bone-iam 路由 IT",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    ROOT / "bone-platform" / "bone-gateway" / "src" / "test" / "java",
                    r"IamGatewayRouteIT",
                ),
            },
        },
        {
            "id": "iam-permission-codes-catalog",
            "title": "跨模块权限码注册表（iam:*, metadata:*, extension:*）",
            "status": "as_is",
            "evidence": {
                "java_default": _grep_files(IAM_MAIN, r"DefaultPermissionCodes"),
                "frontend": [
                    "bone-frontend/packages/shared-types/src/bonePermissionCodes.ts"
                ]
                if (
                    ROOT
                    / "bone-frontend"
                    / "packages"
                    / "shared-types"
                    / "src"
                    / "bonePermissionCodes.ts"
                ).exists()
                else [],
            },
        },
    ]


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
            key = key.strip()
            val = val.strip()
            if key in ("id", "tier", "title", "reference", "tracking"):
                current[key] = val
    if current:
        items.append(current)
    return items


def render_as_is_md(checks: list[dict], generated_at: str) -> str:
    lines = [
        "# IAM 模块 As-Is 证据（CI 派生）",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        "> **勿手改**：由 `tools/iam-compliance-collector/collect.py` 生成。",
        "",
        "| ID | 能力 | 证据摘要 |",
        "|----|------|----------|",
    ]
    for c in checks:
        ev = c["evidence"]
        parts: list[str] = []
        if isinstance(ev, dict):
            if "present" in ev:
                parts.append(
                    f"DDL：{len(ev['present'])}/{len(ev.get('expected', []))} 表"
                )
                if ev.get("missing"):
                    parts.append(f"缺：{', '.join(ev['missing'])}")
            if ev.get("path_count"):
                parts.append(f"OpenAPI：{ev['path_count']} paths")
                if ev.get("security_bearer_jwt"):
                    parts.append("Bearer JWT")
            if "preauthorize" in ev:
                pa = ev["preauthorize"]
                parts.append(
                    f"@PreAuthorize：{len(pa.get('files', []))} 文件 / {len(pa.get('scopes', []))} 权限码"
                )
            if ev.get("security_config"):
                parts.append("`@EnableMethodSecurity`")
            if ev.get("ddl_has_table"):
                parts.append("DDL 有表")
            if ev.get("java"):
                preview = ", ".join(f"`{p}`" for p in ev["java"][:3])
                parts.append(f"源码：{preview}")
                if len(ev["java"]) > 3:
                    parts.append(f"… +{len(ev['java']) - 3}")
            if ev.get("java_default"):
                parts.append("`DefaultPermissionCodes`")
            if ev.get("frontend"):
                parts.append("`bonePermissionCodes.ts`")
        summary = "；".join(parts) if parts else "—"
        lines.append(f"| `{c['id']}` | {c['title']} | {summary} |")
    lines.extend(["", "## 明细", ""])
    for c in checks:
        lines.append(f"### `{c['id']}` — {c['title']}")
        lines.append("")
        lines.append("```json")
        lines.append(json.dumps(c["evidence"], ensure_ascii=False, indent=2))
        lines.append("```")
        lines.append("")
    return "\n".join(lines)


def render_backlog_md(items: list[dict], generated_at: str) -> str:
    lines = [
        "# IAM 模块 [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        f"> **维护源**：[`backlog.yaml`](../../../tools/iam-compliance-collector/backlog.yaml)（仅写未落地项）。",
        "",
        "| Tier | ID | 项 | 引用 | 跟踪 |",
        "|------|-----|-----|------|------|",
    ]
    for it in items:
        tier = it.get("tier", "Target")
        lines.append(
            f"| **{tier}** | `{it.get('id', '')}` | {it.get('title', '')} | "
            f"{it.get('reference', '')} | {it.get('tracking', '')} |"
        )
    return "\n".join(lines)


def render_as_is_embed(checks: list[dict], _: str) -> str:
    lines = [
        "> **CI 派生**（时间戳见 "
        "[`compliance.json`](../../_generated/iam/compliance.json) `generated_at`）",
        "",
        "| ID | 能力 | 证据 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        bits: list[str] = []
        if isinstance(ev, dict):
            if ev.get("present"):
                bits.append(f"DDL {len(ev['present'])} 表")
            if ev.get("path_count"):
                bits.append(f"OpenAPI {ev['path_count']} paths")
            if ev.get("preauthorize"):
                pa = ev["preauthorize"]
                bits.append(
                    f"@PreAuthorize × {len(pa.get('scopes', []))}"
                )
            if ev.get("security_config"):
                bits.append("MethodSecurity")
            if ev.get("ddl_has_table"):
                bits.append("DDL OK")
            if ev.get("java"):
                bits.append(f"源码 × {len(ev['java'])}")
            if ev.get("java_default"):
                bits.append("Default codes")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(bits) or '—'} |")
    lines.append("")
    lines.append(
        "收集命令：`python3 tools/iam-compliance-collector/collect.py --sync-doc`"
    )
    return "\n".join(lines)


def render_backlog_embed(items: list[dict], _: str) -> str:
    lines = [
        "> **维护源**：[`tools/iam-compliance-collector/backlog.yaml`](../../../tools/iam-compliance-collector/backlog.yaml)  ",
        "> **已落地项不得写入 backlog**；验收见 [附录 C](#附录-c-as-is-证据ci-派生) 与 §1.4 / §1.5。",
        "",
        "| Tier | ID | 项 | 引用 | 跟踪 |",
        "|------|-----|-----|------|------|",
    ]
    for it in items:
        tier = it.get("tier", "Target")
        lines.append(
            f"| **{tier}** | `{it.get('id', '')}` | {it.get('title', '')} | "
            f"{it.get('reference', '')} | {it.get('tracking', '')} |"
        )
    return "\n".join(lines)


def replace_block(doc: str, start: str, end: str, body: str) -> str:
    pattern = re.escape(start) + r".*?" + re.escape(end)
    replacement = f"{start}\n{body}\n{end}"
    if start in doc and end in doc:
        return re.sub(pattern, replacement, doc, count=1, flags=re.DOTALL)
    return doc


def write_outputs(
    checks: list[dict], backlog: list[dict], generated_at: str, sync_doc: bool
) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": generated_at,
        "module": "iam",
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

    if sync_doc and DESIGN_DOC.exists():
        doc = _read(DESIGN_DOC)
        doc = replace_block(
            doc, AS_IS_START, AS_IS_END, render_as_is_embed(checks, generated_at)
        )
        doc = replace_block(
            doc,
            BACKLOG_START,
            BACKLOG_END,
            render_backlog_embed(backlog, generated_at),
        )
        DESIGN_DOC.write_text(doc, encoding="utf-8")
        print(f"[sync-doc] updated {DESIGN_DOC.relative_to(ROOT)}")


def check_outputs(checks: list[dict], backlog: list[dict]) -> int:
    json_path = OUT_DIR / "compliance.json"
    if not json_path.exists():
        print("[check] missing compliance.json — run collect.py first", file=sys.stderr)
        return 1
    existing = json.loads(json_path.read_text(encoding="utf-8"))
    fresh = {"as_is": checks, "backlog": backlog}
    stale = {"as_is": existing.get("as_is"), "backlog": existing.get("backlog")}
    if fresh != stale:
        print(
            "[check] doc/_generated/iam/compliance.json is stale — "
            "run: python3 tools/iam-compliance-collector/collect.py",
            file=sys.stderr,
        )
        return 1
    print("[check] compliance.json is up to date")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="IAM module compliance collector")
    parser.add_argument(
        "--sync-doc",
        action="store_true",
        help="Sync GENERATED blocks in 6. IAM账号权限管理模块详细设计方案.md",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Exit 1 if compliance.json would change",
    )
    args = parser.parse_args()
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    checks = build_as_is_checks()
    backlog = load_backlog()

    if args.check:
        return check_outputs(checks, backlog)

    write_outputs(checks, backlog, generated_at, sync_doc=args.sync_doc)
    print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
