#!/usr/bin/env python3
"""
元数据模块 Docs-as-Code 合规收集器。

派生 As-Is 证据（OpenAPI + 源码扫描 + DDL）与 [Target]/[Vision] Backlog（backlog.yaml），
写入 doc/_generated/metadata/，并可同步详设中的 GENERATED 标记块。

用法:
  python3 tools/metadata-compliance-collector/collect.py
  python3 tools/metadata-compliance-collector/collect.py --sync-doc
  python3 tools/metadata-compliance-collector/collect.py --check
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
OUT_DIR = ROOT / "doc" / "_generated" / "metadata"
OPENAPI_RUNTIME = ROOT / "doc" / "architecture" / "openapi" / "metadata-runtime-v1.yaml"
SERVER_JAVA = ROOT / "bone-engine" / "bone-metadata-server" / "src" / "main" / "java"
ENGINE_JAVA = (
    ROOT
    / "bone-engine"
    / "bone-metadata-engine"
    / "bone-metadata-engine-runtime"
    / "src"
    / "main"
    / "java"
)
GENERATOR_JAVA = ROOT / "bone-engine" / "studio-generator" / "src" / "main" / "java"
INIT_SQL = ROOT / "bone-init.sql"
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "2. 元数据管理模块详细设计方案.md"
BACKLOG_YAML = COLLECTOR_DIR / "backlog.yaml"

AS_IS_START = "<!-- META_COMPLIANCE_ASIS:START -->"
AS_IS_END = "<!-- META_COMPLIANCE_ASIS:END -->"
BACKLOG_START = "<!-- META_COMPLIANCE_BACKLOG:START -->"
BACKLOG_END = "<!-- META_COMPLIANCE_BACKLOG:END -->"

META_TABLES = [
    "meta_entity",
    "meta_field",
    "meta_entity_relation",
    "meta_code_template",
    "meta_data_quality_rule",
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


def scan_meta_tables() -> dict:
    text = _read(INIT_SQL)
    present = sorted(
        t
        for t in META_TABLES
        if f"CREATE TABLE {t} " in text or f"CREATE TABLE {t}\n" in text
    )
    missing = sorted(set(META_TABLES) - set(present))
    delivery_mode = "delivery_mode" in text and "meta_entity" in text
    return {
        "expected": META_TABLES,
        "present": present,
        "missing": missing,
        "delivery_mode_column": delivery_mode,
    }


def scan_runtime_openapi() -> dict:
    text = _read(OPENAPI_RUNTIME)
    paths = re.findall(r"^  (/entities/[^\n:]+):\s*$", text, re.MULTILINE)
    methods: dict[str, list[str]] = {}
    for path in paths:
        start = text.find(f"  {path}:")
        block = text[start : start + 800] if start >= 0 else ""
        ops = []
        for m in ("get", "post", "put", "delete"):
            if f"\n    {m}:" in block:
                ops.append(m.upper())
        methods[path] = ops
    return {
        "openapi_file": str(OPENAPI_RUNTIME.relative_to(ROOT)),
        "paths": paths,
        "methods": methods,
    }


def count_preauthorize_metadata() -> dict:
    eav_files: list[str] = []
    catalog_files: list[str] = []
    runtime_files: list[str] = []
    scopes: set[str] = set()
    rx = re.compile(r"@PreAuthorize\([^)]*['\"]([^'\"]+)['\"]")
    if SERVER_JAVA.exists():
        for p in SERVER_JAVA.glob("**/*.java"):
            text = p.read_text(encoding="utf-8")
            if "@PreAuthorize" not in text:
                continue
            rel_unix = str(p.relative_to(ROOT)).replace("\\", "/")
            for scope in rx.findall(text):
                scopes.add(scope)
            if "adapter/web/controller/MetadataController" in rel_unix:
                eav_files.append(rel_unix)
            elif "catalog/adapter/web/controller" in rel_unix:
                catalog_files.append(rel_unix)
            elif "runtime/adapter/web" in rel_unix:
                runtime_files.append(rel_unix)
    return {
        "eav_controller_files": sorted(eav_files),
        "catalog_controller_files": sorted(catalog_files),
        "runtime_controller_files": sorted(runtime_files),
        "scopes_found": sorted(scopes),
    }


def count_created_status() -> dict:
    """Scan controllers that return ResponseEntity.created(...) for 201 + Location."""
    catalog_files: list[str] = []
    runtime_files: list[str] = []
    eav_files: list[str] = []
    if SERVER_JAVA.exists():
        for p in SERVER_JAVA.glob("**/*.java"):
            text = p.read_text(encoding="utf-8")
            if "ResponseEntity.created" not in text and "HttpStatus.CREATED" not in text:
                continue
            rel_unix = str(p.relative_to(ROOT)).replace("\\", "/")
            if "catalog/adapter/web/controller" in rel_unix:
                catalog_files.append(rel_unix)
            elif "runtime/adapter/web" in rel_unix:
                runtime_files.append(rel_unix)
            elif "adapter/web/controller/MetadataController" in rel_unix:
                eav_files.append(rel_unix)
    return {
        "catalog_controller_files": sorted(catalog_files),
        "runtime_controller_files": sorted(runtime_files),
        "eav_controller_files": sorted(eav_files),
    }


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
    ddl = scan_meta_tables()
    openapi = scan_runtime_openapi()
    preauth = count_preauthorize_metadata()
    created = count_created_status()
    adr_0015 = ROOT / "doc/architecture/adr/0015-metadata-runtime-jdbc-via-engine.md"
    adr_0016 = ROOT / "doc/architecture/adr/0016-metadata-catalog-abstract-entity-tenant.md"

    return [
        {
            "id": "meta-ddl-five-tables",
            "title": "meta_* 五表在 bone-init.sql",
            "status": "as_is",
            "evidence": ddl,
        },
        {
            "id": "catalog-rest-controllers",
            "title": "catalog REST（Meta*CatalogController）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"class MetaEntityCatalogController|class MetaFieldCatalogController|class MetaRelationCatalogController",
                ),
            },
        },
        {
            "id": "eav-fields-api",
            "title": "EAV fields:search|allocate（MetadataController）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"fields:search|fields:allocate|class MetadataController",
                ),
                "preauthorize": preauth,
            },
        },
        {
            "id": "runtime-crud-api",
            "title": "模式 B RuntimeRecordController + OpenAPI",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(SERVER_JAVA, r"class RuntimeRecordController"),
                "openapi": openapi,
            },
        },
        {
            "id": "runtime-jdbc-service",
            "title": "JdbcRuntimeRecordService + CatalogRuntimeEntityProvider",
            "status": "as_is",
            "evidence": {
                "engine": _grep_files(ENGINE_JAVA, r"class JdbcRuntimeRecordService"),
                "server": _grep_files(
                    SERVER_JAVA, r"class CatalogRuntimeEntityProvider"
                ),
                "adr": (
                    [str(adr_0015.relative_to(ROOT))] if adr_0015.exists() else []
                ),
            },
        },
        {
            "id": "generator-catalog-snapshot",
            "title": "generator CATALOG_SNAPSHOT 直读 meta_*",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    GENERATOR_JAVA,
                    r"class CatalogMetadataGatewayAdapter|MetadataEntitySnapshotController|DELIVERY_RUNTIME",
                ),
            },
        },
        {
            "id": "catalog-abstract-entity",
            "title": "catalog 聚合根 extends AbstractEntity（ADR-0016）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"class MetaEntity extends AbstractEntity|class MetaField extends AbstractEntity",
                ),
                "adr": (
                    [str(adr_0016.relative_to(ROOT))] if adr_0016.exists() else []
                ),
            },
        },
        {
            "id": "catalog-runtime-preauthorize",
            "title": "catalog + runtime Controller @PreAuthorize（metadata:read|write）",
            "status": "as_is",
            "evidence": {
                "preauthorize": preauth,
            },
        },
        {
            "id": "catalog-runtime-201-location",
            "title": "catalog + runtime POST 201 Created + Location（AIP-133）",
            "status": "as_is",
            "evidence": {
                "created": created,
            },
        },
        {
            "id": "eav-allocate-201",
            "title": "fields:allocate 返回 201（EAV 局部符合 AIP-133）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"allocateAndPersistFields|HttpStatus\.CREATED",
                ),
            },
        },
        {
            "id": "catalog-if-match-entity",
            "title": "meta_entity PUT/publish If-Match + 412（AIP-154）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"CatalogVersionSupport|CatalogOptimisticLockException|HttpHeaders\.IF_MATCH",
                ),
            },
        },
        {
            "id": "catalog-idempotency-key",
            "title": "publish + fields:allocate Idempotency-Key（24h 进程内）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"CatalogIdempotencyService|Idempotency-Key",
                ),
            },
        },
        {
            "id": "metadata-problem-detail",
            "title": "GlobalExceptionHandler → ApiResponse<ProblemDetail>",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"CatalogApiResponses|ApiResponse\.fail|ProblemDetail",
                ),
            },
        },
        {
            "id": "runtime-query-params",
            "title": "runtime 列表 fields/sort/q 查询参数",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    ENGINE_JAVA,
                    r"RuntimePageQuery|RuntimeQuerySupport",
                ),
                "server": _grep_files(
                    SERVER_JAVA,
                    r"RuntimePageQuery\.parse|@RequestParam.*fields",
                ),
            },
        },
        {
            "id": "runtime-if-match",
            "title": "runtime PUT If-Match（物理表 version 列）",
            "status": "as_is",
            "evidence": {
                "engine": _grep_files(ENGINE_JAVA, r"expectedVersion|META_PRECONDITION_FAILED"),
                "server": _grep_files(SERVER_JAVA, r"HttpHeaders\.IF_MATCH.*RuntimeRecordController|parseIfMatchVersion"),
            },
        },
        {
            "id": "catalog-runtime-entity-cache",
            "title": "已发布 RUNTIME 实体 Caffeine 缓存（默认 60s）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(SERVER_JAVA, r"CachedCatalogRuntimeEntityProvider"),
            },
        },
        {
            "id": "metadata-publish-scope",
            "title": "metadata:publish 权限码 + publish hasAnyAuthority",
            "status": "as_is",
            "evidence": {
                "ddl": {"permission": "metadata:publish" in _read(INIT_SQL)},
                "java": _grep_files(
                    SERVER_JAVA,
                    r"metadata:publish|hasAnyAuthority\('metadata:publish'",
                ),
            },
        },
        {
            "id": "catalog-redis-cache",
            "title": "RUNTIME 实体 Redis 缓存（配置 backend=redis + StringRedisTemplate）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"RedisCachedRuntimeEntityCatalog|runtime-entity-cache",
                ),
            },
        },
        {
            "id": "catalog-idempotency-redis",
            "title": "Idempotency-Key Redis 存储（配置 idempotency.backend=redis）",
            "status": "as_is",
            "evidence": {
                "java": _grep_files(
                    SERVER_JAVA,
                    r"RedisCatalogIdempotencyStore|idempotency:\s*\n\s*backend",
                ),
            },
        },
        {
            "id": "catalog-field-relation-version",
            "title": "meta_field / meta_entity_relation version + If-Match PUT",
            "status": "as_is",
            "evidence": {
                "ddl": {
                    "meta_field_version": "meta_field" in _read(INIT_SQL)
                    and "version" in _read(INIT_SQL).split("CREATE TABLE meta_field")[1].split(
                        "CREATE TABLE"
                    )[0],
                },
                "java": _grep_files(
                    SERVER_JAVA,
                    r"MetaField.*version|MetaEntityRelation.*version|MetaFieldCatalogController",
                ),
            },
        },
    ]


def render_as_is_md(checks: list[dict], generated_at: str) -> str:
    lines = [
        "# 元数据模块 As-Is 证据",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        "> **收集器**：`tools/metadata-compliance-collector/collect.py`",
        "",
        "| ID | 能力 | 摘要 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        parts: list[str] = []
        if isinstance(ev, dict):
            if ev.get("present"):
                parts.append(f"DDL {len(ev['present'])}/{len(ev.get('expected', []))} 表")
            if ev.get("paths"):
                parts.append(f"OpenAPI {len(ev['paths'])} paths")
            if ev.get("preauthorize"):
                pa = ev["preauthorize"]
                parts.append(
                    f"EAV {len(pa.get('eav_controller_files', []))} · "
                    f"catalog {len(pa.get('catalog_controller_files', []))} · "
                    f"runtime {len(pa.get('runtime_controller_files', []))}"
                )
            if ev.get("created"):
                cr = ev["created"]
                parts.append(
                    f"created: EAV {len(cr.get('eav_controller_files', []))} · "
                    f"catalog {len(cr.get('catalog_controller_files', []))} · "
                    f"runtime {len(cr.get('runtime_controller_files', []))}"
                )
            if ev.get("java"):
                parts.append(f"源码 × {len(ev['java'])}")
            if ev.get("engine"):
                parts.append(f"engine × {len(ev['engine'])}")
            if ev.get("adr"):
                parts.append("ADR")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(parts) or '—'} |")
    return "\n".join(lines)


def render_backlog_md(items: list[dict], generated_at: str) -> str:
    lines = [
        "# 元数据模块 [Target] / [Vision] Backlog",
        "",
        f"> **生成时间**：{generated_at}（UTC）  ",
        f"> **维护源**：[`backlog.yaml`](../../../tools/metadata-compliance-collector/backlog.yaml)",
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
        "[`compliance.json`](../../_generated/metadata/compliance.json) `generated_at`）",
        "",
        "| ID | 能力 | 证据 |",
        "|----|------|------|",
    ]
    for c in checks:
        ev = c["evidence"]
        bits: list[str] = []
        if isinstance(ev, dict):
            if ev.get("present"):
                bits.append(f"DDL {len(ev['present'])}")
            if ev.get("paths"):
                bits.append("runtime OpenAPI")
            if ev.get("java"):
                bits.append(f"java × {len(ev['java'])}")
            if ev.get("preauthorize"):
                pa = ev["preauthorize"]
                bits.append(
                    f"@PreAuthorize (EAV {len(pa.get('eav_controller_files', []))} / "
                    f"catalog {len(pa.get('catalog_controller_files', []))} / "
                    f"runtime {len(pa.get('runtime_controller_files', []))})"
                )
            if ev.get("created"):
                cr = ev["created"]
                bits.append(
                    f"201+Location (catalog {len(cr.get('catalog_controller_files', []))} / "
                    f"runtime {len(cr.get('runtime_controller_files', []))})"
                )
            if ev.get("adr"):
                bits.append("ADR")
        lines.append(f"| `{c['id']}` | {c['title']} | {' · '.join(bits) or '—'} |")
    lines.append("")
    lines.append(
        "收集命令：`python3 tools/metadata-compliance-collector/collect.py --sync-doc`"
    )
    return "\n".join(lines)


def render_backlog_embed(items: list[dict], _: str) -> str:
    lines = [
        "> **维护源**：[`tools/metadata-compliance-collector/backlog.yaml`](../../../tools/metadata-compliance-collector/backlog.yaml)",
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


def main() -> int:
    parser = argparse.ArgumentParser(description="Metadata Docs-as-Code collector")
    parser.add_argument("--sync-doc", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    checks = build_as_is_checks()
    backlog = load_backlog()

    payload = {
        "module": "metadata",
        "generated_at": generated_at,
        "as_is": checks,
        "backlog": backlog,
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    compliance_path = OUT_DIR / "compliance.json"
    if args.check and compliance_path.exists():
        existing = json.loads(compliance_path.read_text(encoding="utf-8"))
        if existing.get("as_is") != checks or existing.get("backlog") != backlog:
            print("[fail] compliance.json stale; run collect.py without --check", file=sys.stderr)
            return 1
    else:
        compliance_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        (OUT_DIR / "as-is-evidence.md").write_text(
            render_as_is_md(checks, generated_at), encoding="utf-8"
        )
        (OUT_DIR / "backlog.md").write_text(
            render_backlog_md(backlog, generated_at), encoding="utf-8"
        )

    if args.sync_doc:
        doc = _read(DESIGN_DOC)
        doc = replace_block(
            doc, AS_IS_START, AS_IS_END, render_as_is_embed(checks, generated_at)
        )
        doc = replace_block(
            doc, BACKLOG_START, BACKLOG_END, render_backlog_embed(backlog, generated_at)
        )
        DESIGN_DOC.write_text(doc, encoding="utf-8")
        print(f"[ok] synced {DESIGN_DOC.relative_to(ROOT)}")

    if not args.check:
        print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    else:
        print("[ok] compliance.json up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
