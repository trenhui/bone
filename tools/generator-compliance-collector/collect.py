#!/usr/bin/env python3
"""Studio Generator Docs-as-Code 合规收集器。"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import compliance_lib as cl  # noqa: E402

COLLECTOR_DIR = Path(__file__).resolve().parent
OUT_DIR = ROOT / "doc" / "_generated" / "generator"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "generator-v1.yaml"
MAIN = ROOT / "bone-engine" / "studio-generator" / "src" / "main" / "java"
TEST = ROOT / "bone-engine" / "studio-generator" / "src" / "test" / "java"
INIT_SQL = ROOT / "bone-init.sql"
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "8.Studio Generator 详细设计方案.md"
AS_IS_MARKER = ("<!-- GEN_COMPLIANCE_ASIS:START -->", "<!-- GEN_COMPLIANCE_ASIS:END -->")
BACKLOG_MARKER = ("<!-- GEN_COMPLIANCE_BACKLOG:START -->", "<!-- GEN_COMPLIANCE_BACKLOG:END -->")


def build_as_is_checks() -> list[dict]:
    sql = cl.read(INIT_SQL)
    return [
        {
            "id": "openapi-generator",
            "title": "generator-v1 OpenAPI",
            "status": "as_is",
            "evidence": {"openapi": cl.scan_openapi_paths(OPENAPI)},
        },
        {
            "id": "generation-controllers",
            "title": "生成/模板/数据源/任务 REST",
            "status": "as_is",
            "evidence": {
                "java": cl.grep_files(
                    MAIN,
                    r"CodeGenerationController|CodeTemplateController|"
                    r"DataSourceController|GenerationTaskController|GeneratorOperationController",
                    ROOT,
                ),
            },
        },
        {
            "id": "archunit",
            "title": "studio-generator ArchUnit",
            "status": "as_is",
            "evidence": {"java": cl.grep_files(TEST, r"ArchitectureTest", ROOT)},
        },
        {
            "id": "gen-ddl",
            "title": "gen_* 表在 bone-init §6",
            "status": "as_is",
            "evidence": {
                "gen_generation_task": "gen_generation_task" in sql,
                "any_gen": "CREATE TABLE `gen_" in sql,
            },
        },
        {
            "id": "command-handlers",
            "title": "DDD CommandHandler 写侧",
            "status": "as_is",
            "evidence": {"java": cl.grep_files(MAIN, r"CommandHandler", ROOT)[:12]},
        },
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sync-doc", action="store_true")
    args = parser.parse_args()
    checks = build_as_is_checks()
    backlog = cl.load_backlog(COLLECTOR_DIR / "backlog.yaml")
    if args.check:
        return 0 if cl.check_outputs(OUT_DIR, checks, backlog, "generator") else 1
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    cl.write_outputs(
        OUT_DIR,
        "generator",
        "Studio Generator",
        checks,
        backlog,
        generated_at,
        "../../../tools/generator-compliance-collector/backlog.yaml",
    )
    if args.sync_doc:
        ok = cl.sync_design_doc(
            DESIGN_DOC,
            AS_IS_MARKER,
            BACKLOG_MARKER,
            checks,
            backlog,
            "../../_generated/generator/compliance.json",
            "../../../tools/generator-compliance-collector/backlog.yaml",
        )
        if ok:
            print(f"[sync-doc] updated {DESIGN_DOC.relative_to(ROOT)}")
    print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
