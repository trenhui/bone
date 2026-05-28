#!/usr/bin/env python3
"""bone-blueprint Docs-as-Code 合规收集器（样板模块，无详设 GENERATED 块）。"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COLLECTOR_DIR = Path(__file__).resolve().parent
OUT_DIR = ROOT / "doc" / "_generated" / "blueprint"
BACKLOG_YAML = COLLECTOR_DIR / "backlog.yaml"
MAIN_JAVA = ROOT / "bone-blueprint" / "src" / "main" / "java"
TEST_JAVA = ROOT / "bone-blueprint" / "src" / "test" / "java"
RESOURCES = ROOT / "bone-blueprint" / "src" / "main" / "resources"


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
    schema = _read(RESOURCES / "schema.sql")
    return [
        {
            "id": "archunit",
            "title": "ArchUnit 分层（BoneDddArchRules）",
            "status": "as_is",
            "evidence": {"java": _grep_files(TEST_JAVA, r"BoneDddArchRules")},
        },
        {
            "id": "outbox",
            "title": "Transactional Outbox（bp_outbox）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(MAIN_JAVA, r"OrderOutboxWriter|OrderOutboxRelay"),
                "schema_has_bp_outbox": "bp_outbox" in schema,
            },
        },
        {
            "id": "tenant",
            "title": "多租户 TenantAggregateRoot + 过滤器",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    MAIN_JAVA, r"TenantAggregateRoot|TenantContextFilter|TenantSupport"
                )
            },
        },
        {
            "id": "money-vo",
            "title": "Money 值对象",
            "status": "as_is",
            "evidence": {"java": _grep_files(MAIN_JAVA, r"class Money\b")},
        },
        {
            "id": "read-port",
            "title": "CQRS 读侧 OrderReadPort + SQL 投影",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(MAIN_JAVA, r"OrderReadPort|OrderDetailAssembler"),
                "sql": ["bone-blueprint/src/main/resources/sql/order/findOrderWithItems.sql"]
                if (RESOURCES / "sql/order/findOrderWithItems.sql").exists()
                else [],
            },
        },
        {
            "id": "domain-events",
            "title": "领域事件 AFTER_COMMIT 发布",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    MAIN_JAVA,
                    r"SpringDomainEventPublisher|TransactionalEventListener|AggregatePersistence",
                )
            },
        },
        {
            "id": "cqrs-handlers",
            "title": "CommandHandler / QueryHandler 分离",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    MAIN_JAVA, r"CommandHandler|QueryHandler"
                )[:12],
            },
        },
        {
            "id": "integration-event",
            "title": "集成事件与领域事件分离",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    MAIN_JAVA, r"OrderPaidIntegrationEvent|OrderIntegrationEventPublisher"
                )
            },
        },
    ]


def render_as_is_md(checks: list[dict], generated_at: str) -> str:
    lines = [
        "# bone-blueprint As-Is 证据（CI 派生）",
        "",
        f"> **生成时间**：{generated_at}（UTC）",
        "",
        "| ID | 能力 | 摘要 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        parts = []
        if ev.get("schema_has_bp_outbox"):
            parts.append("schema: bp_outbox")
        if ev.get("java"):
            parts.append(f"{len(ev['java'])} 个 Java 文件")
        if ev.get("sql"):
            parts.append("SQL 投影")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(parts) or '—'} |")
    return "\n".join(lines)


def render_backlog_md(items: list[dict], generated_at: str) -> str:
    lines = [
        "# bone-blueprint [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）",
        f"> **维护源**：[`backlog.yaml`](../../../tools/blueprint-compliance-collector/backlog.yaml)",
        "",
        "| Tier | ID | 项 | 引用 | 跟踪 |",
        "|------|-----|-----|------|------|",
    ]
    for it in items:
        lines.append(
            f"| **{it.get('tier', 'Target')}** | `{it.get('id', '')}` | "
            f"{it.get('title', '')} | {it.get('reference', '')} | {it.get('tracking', '')} |"
        )
    return "\n".join(lines)


def write_outputs(checks: list[dict], backlog: list[dict], generated_at: str) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": generated_at,
        "module": "blueprint",
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
    print("[check] compliance.json is up to date")
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
