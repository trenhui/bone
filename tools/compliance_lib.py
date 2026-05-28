"""Docs-as-Code 收集器共享工具（供 tools/*-compliance-collector 引用）。"""
from __future__ import annotations

import json
import re
from pathlib import Path


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def grep_files(root: Path, pattern: str, root_base: Path) -> list[str]:
    hits: list[str] = []
    if not root.exists():
        return hits
    rx = re.compile(pattern)
    for p in root.glob("**/*.java"):
        try:
            if rx.search(p.read_text(encoding="utf-8")):
                hits.append(str(p.relative_to(root_base)))
        except OSError:
            pass
    return sorted(hits)


def load_backlog(backlog_yaml: Path) -> list[dict]:
    text = read(backlog_yaml)
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


def scan_openapi_paths(openapi: Path) -> dict:
    text = read(openapi)
    paths = re.findall(r"^  (/[^\n:]+):\s*$", text, re.MULTILINE)
    return {
        "openapi_file": str(openapi) if openapi.exists() else "",
        "path_count": len(paths),
        "paths_sample": paths[:8],
    }


def render_as_is_md(module: str, title: str, checks: list[dict], generated_at: str) -> str:
    lines = [
        f"# {title} As-Is 证据（CI 派生）",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        f"> **模块**：`{module}`",
        "",
        "| ID | 能力 | 摘要 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c.get("evidence", {})
        parts: list[str] = []
        if "openapi" in ev and ev["openapi"].get("path_count"):
            parts.append(f"OpenAPI {ev['openapi']['path_count']} paths")
        if "java" in ev and ev["java"]:
            parts.append(f"{len(ev['java'])} Java")
        for k, v in ev.items():
            if k in ("openapi", "java"):
                continue
            if isinstance(v, bool):
                parts.append(f"{k}: {'yes' if v else 'no'}")
            elif isinstance(v, list) and v:
                parts.append(f"{k}: {len(v)}")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(parts) or '—'} |")
    return "\n".join(lines) + "\n"


def render_backlog_md(
    module: str, items: list[dict], generated_at: str, backlog_yaml_rel: str
) -> str:
    lines = [
        f"# {module} [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        f"> **维护源**：[`backlog.yaml`]({backlog_yaml_rel})",
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


def write_outputs(
    out_dir: Path,
    module: str,
    title: str,
    checks: list[dict],
    backlog: list[dict],
    generated_at: str,
    backlog_yaml_rel: str,
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "generated_at": generated_at,
        "module": module,
        "as_is": checks,
        "backlog": backlog,
    }
    (out_dir / "compliance.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (out_dir / "as-is-evidence.md").write_text(
        render_as_is_md(module, title, checks, generated_at), encoding="utf-8"
    )
    (out_dir / "backlog.md").write_text(
        render_backlog_md(module, backlog, generated_at, backlog_yaml_rel), encoding="utf-8"
    )


def render_as_is_embed(checks: list[dict], json_rel: str) -> str:
    """详设嵌入块（无时间戳，避免 CI 漂移）。"""
    lines = [
        f"> **CI 派生** · 完整 JSON 见 [`{json_rel}`]({json_rel})",
        "",
        "| ID | 能力 | 证据 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c.get("evidence", {})
        bits: list[str] = []
        if "openapi" in ev and ev["openapi"].get("path_count"):
            bits.append(f"OpenAPI {ev['openapi']['path_count']} paths")
        if "java" in ev and ev["java"]:
            bits.append(f"{len(ev['java'])} 源码命中")
        for k, v in ev.items():
            if k in ("openapi", "java"):
                continue
            if isinstance(v, bool) and v:
                bits.append(k)
            elif isinstance(v, list) and v:
                bits.append(f"{k}:{len(v)}")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(bits) or '—'} |")
    return "\n".join(lines)


def render_backlog_embed(items: list[dict], backlog_rel: str) -> str:
    lines = [
        f"> **维护源**：[`{backlog_rel}`]({backlog_rel}) · 已落地项**勿写入** backlog。",
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


def replace_block(doc: str, start: str, end: str, body: str) -> str:
    import re as _re
    pattern = _re.escape(start) + r".*?" + _re.escape(end)
    return _re.sub(pattern, f"{start}\n{body}\n{end}", doc, count=1, flags=_re.DOTALL)


def sync_design_doc(
    design_doc: Path,
    as_is_marker: tuple[str, str],
    backlog_marker: tuple[str, str],
    checks: list[dict],
    backlog_items: list[dict],
    json_rel: str,
    backlog_yaml_rel: str,
) -> bool:
    if not design_doc.exists():
        return False
    src = read(design_doc)
    if as_is_marker[0] not in src or backlog_marker[0] not in src:
        return False
    out = replace_block(src, *as_is_marker, render_as_is_embed(checks, json_rel))
    out = replace_block(out, *backlog_marker, render_backlog_embed(backlog_items, backlog_yaml_rel))
    if out != src:
        design_doc.write_text(out, encoding="utf-8")
    return True


def check_outputs(out_dir: Path, checks: list[dict], backlog: list[dict], label: str) -> bool:
    path = out_dir / "compliance.json"
    if not path.exists():
        print(f"[check] missing {label} compliance.json", file=__import__("sys").stderr)
        return False
    existing = json.loads(path.read_text(encoding="utf-8"))
    if {"as_is": checks, "backlog": backlog} != {
        "as_is": existing.get("as_is"),
        "backlog": existing.get("backlog"),
    }:
        print(f"[check] {label} stale — run collect.py", file=__import__("sys").stderr)
        return False
    print(f"[check] {label} compliance.json is up to date", flush=True)
    return True
