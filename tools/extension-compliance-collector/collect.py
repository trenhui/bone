#!/usr/bin/env python3
"""
扩展模块 Docs-as-Code 合规收集器。

派生 As-Is 证据（OpenAPI + 源码扫描）与 [Target]/[Vision] Backlog（backlog.yaml），
写入 doc/_generated/extension/，并可同步详设中的 GENERATED 标记块。

用法:
  python3 tools/extension-compliance-collector/collect.py
  python3 tools/extension-compliance-collector/collect.py --sync-doc
  python3 tools/extension-compliance-collector/collect.py --check
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
OUT_DIR = ROOT / "doc" / "_generated" / "extension"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "extension-v1.yaml"
STUDIO_JAVA = (
    ROOT
    / "bone-engine"
    / "bone-extension-engine"
    / "bone-extension-studio"
    / "src"
    / "main"
    / "java"
)
STUDIO_TEST = (
    ROOT
    / "bone-engine"
    / "bone-extension-engine"
    / "bone-extension-studio"
    / "src"
    / "test"
    / "java"
)
SDK_JAVA = (
    ROOT
    / "bone-engine"
    / "bone-extension-engine"
    / "bone-extension-sdk"
    / "src"
    / "main"
    / "java"
)
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "5. 扩展管理模块详细设计方案.md"
BACKLOG_YAML = COLLECTOR_DIR / "backlog.yaml"

AS_IS_START = "<!-- EXT_COMPLIANCE_ASIS:START -->"
AS_IS_END = "<!-- EXT_COMPLIANCE_ASIS:END -->"
BACKLOG_START = "<!-- EXT_COMPLIANCE_BACKLOG:START -->"
BACKLOG_END = "<!-- EXT_COMPLIANCE_BACKLOG:END -->"


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


def scan_openapi_delete_204() -> dict:
    text = _read(OPENAPI)
    delete_paths: list[str] = []
    # extension-v1.yaml: path keys are relative (e.g. /points/{id}), not full /api/v1/...
    for m in re.finditer(r"^  (/[^\n:]+):\s*\n", text, re.MULTILINE):
        path = m.group(1)
        start = m.end()
        next_path = re.search(r"^  /", text[start:], re.MULTILINE)
        block = text[start : start + next_path.start()] if next_path else text[start:]
        if "delete:" in block and "'204'" in block:
            delete_paths.append(path)
    return {
        "openapi_file": str(OPENAPI.relative_to(ROOT)),
        "delete_operations_with_204": len(delete_paths),
        "paths": delete_paths,
    }


def build_as_is_checks() -> list[dict]:
    openapi_delete = scan_openapi_delete_204()
    delete_handlers = _grep_files(STUDIO_JAVA, r"noContent\s*\(")
    idem_conflict = _grep_files(STUDIO_JAVA, r"IdempotencyConflictException|COMMON_IDEMPOTENCY_CONFLICT")
    archunit = _grep_files(STUDIO_TEST, r"BoneDddArchRules|ArchitectureTest")
    execution_guard = _grep_files(SDK_JAVA, r"class ExtensionExecutionGuard")
    lro = _grep_files(STUDIO_JAVA, r"class StudioLroService")
    idempotency = _grep_files(STUDIO_JAVA, r"class StudioIdempotencyService")

    return [
        {
            "id": "delete-204",
            "title": "DELETE 返回 204 无 body",
            "status": "as_is",
            "evidence": {
                "openapi": openapi_delete,
                "java_noContent": delete_handlers,
            },
        },
        {
            "id": "idempotency-409",
            "title": "幂等键冲突 409 COMMON_IDEMPOTENCY_CONFLICT",
            "status": "as_is",
            "evidence": {"java": idem_conflict},
        },
        {
            "id": "archunit-studio",
            "title": "Studio ArchUnit 分层守护",
            "status": "as_is",
            "evidence": {"java": archunit},
        },
        {
            "id": "execution-guard",
            "title": "SDK 执行舱壁与超时",
            "status": "as_is",
            "evidence": {"java": execution_guard},
        },
        {
            "id": "lro-deploy",
            "title": "LRO 部署 operationId",
            "status": "as_is",
            "evidence": {"java": lro},
        },
        {
            "id": "idempotency-key",
            "title": "Idempotency-Key（进程内）",
            "status": "as_is",
            "evidence": {"java": idempotency},
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
        "# 扩展模块 As-Is 证据（CI 派生）",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        "> **勿手改**：由 `tools/extension-compliance-collector/collect.py` 生成。",
        "",
        "| ID | 能力 | 证据摘要 |",
        "|----|------|----------|",
    ]
    for c in checks:
        ev = c["evidence"]
        parts: list[str] = []
        if "openapi" in ev:
            o = ev["openapi"]
            parts.append(f"OpenAPI DELETE 204：{o.get('delete_operations_with_204', 0)} 处")
        if "java_noContent" in ev and ev["java_noContent"]:
            parts.append(f"`noContent()`：{len(ev['java_noContent'])} 文件")
        if "java" in ev and ev["java"]:
            parts.append(f"源码：{', '.join(f'`{p}`' for p in ev['java'][:3])}")
            if len(ev["java"]) > 3:
                parts.append("…")
        summary = "；".join(parts) if parts else "—"
        lines.append(f"| `{c['id']}` | {c['title']} | {summary} |")
    lines.extend(
        [
            "",
            "## 明细",
            "",
        ]
    )
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
        "# 扩展模块 [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        f"> **维护源**：[`backlog.yaml`](../../../tools/extension-compliance-collector/backlog.yaml)（仅写未落地项）。",
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


def render_as_is_embed(checks: list[dict], generated_at: str) -> str:
    """详设附录 C 内嵌块（简短）。"""
    lines = [
        f"> **CI 派生**（{generated_at} UTC）· 完整 JSON 见 "
        f"[`doc/_generated/extension/compliance.json`](../../_generated/extension/compliance.json)",
        "",
        "| ID | 能力 | 证据 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        bits = []
        if "openapi" in ev:
            bits.append("OpenAPI DELETE→204")
        if "java_noContent" in ev and ev["java_noContent"]:
            bits.append("Handler `noContent()`")
        if "java" in ev and ev["java"]:
            bits.append("源码命中")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(bits) or '—'} |")
    lines.append("")
    lines.append(
        "收集命令：`python3 tools/extension-compliance-collector/collect.py --sync-doc`"
    )
    return "\n".join(lines)


def render_backlog_embed(items: list[dict], generated_at: str) -> str:
    lines = [
        f"> **维护源**：[`tools/extension-compliance-collector/backlog.yaml`](../../../tools/extension-compliance-collector/backlog.yaml)  ",
        f"> **生成时间**：{generated_at} UTC · 已落地项**不得**写入 backlog，见 [附录 C](#附录-c-as-is-证据ci-派生) 与 §2 符合度矩阵。",
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
        "module": "extension",
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
            "[check] doc/_generated/extension/compliance.json is stale — "
            "run: python3 tools/extension-compliance-collector/collect.py",
            file=sys.stderr,
        )
        return 1
    print("[check] compliance.json is up to date")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Extension module compliance collector")
    parser.add_argument(
        "--sync-doc",
        action="store_true",
        help="Sync GENERATED blocks in 5. 扩展管理模块详细设计方案.md",
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
