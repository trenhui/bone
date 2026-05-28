#!/usr/bin/env python3
"""集成模块 Docs-as-Code 合规收集器。"""
from __future__ import annotations

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import compliance_lib as cl  # noqa: E402

COLLECTOR_DIR = Path(__file__).resolve().parent
OUT_DIR = ROOT / "doc" / "_generated" / "integration"
OPENAPI = ROOT / "doc" / "architecture" / "openapi" / "integration-v1.yaml"
MAIN = ROOT / "bone-platform" / "bone-integration" / "src" / "main" / "java"
TEST = ROOT / "bone-platform" / "bone-integration" / "src" / "test" / "java"
INIT_SQL = ROOT / "bone-init.sql"
DESIGN_DOC = ROOT / "doc" / "design" / "modules" / "4. 集成管理模块详细设计方案.md"
AS_IS_MARKER = ("<!-- INT_COMPLIANCE_ASIS:START -->", "<!-- INT_COMPLIANCE_ASIS:END -->")
BACKLOG_MARKER = ("<!-- INT_COMPLIANCE_BACKLOG:START -->", "<!-- INT_COMPLIANCE_BACKLOG:END -->")


def build_as_is_checks() -> list[dict]:
    sql = cl.read(INIT_SQL)
    return [
        {
            "id": "openapi-integration",
            "title": "integration-v1 OpenAPI",
            "status": "as_is",
            "evidence": {"openapi": cl.scan_openapi_paths(OPENAPI)},
        },
        {
            "id": "rest-controllers",
            "title": "Connector / Flow / Monitor 控制器",
            "status": "as_is",
            "evidence": {
                "java": cl.grep_files(
                    MAIN, r"ConnectorController|FlowController|MonitorController", ROOT
                ),
            },
        },
        {
            "id": "archunit",
            "title": "bone-integration ArchUnit",
            "status": "as_is",
            "evidence": {"java": cl.grep_files(TEST, r"ArchitectureTest", ROOT)},
        },
        {
            "id": "int-ddl",
            "title": "int_* 表在 bone-init",
            "status": "as_is",
            "evidence": {
                "int_flow": "int_flow" in sql,
                "int_connector": "int_connector" in sql,
                "int_execution_log": "int_execution_log" in sql,
            },
        },
        {
            "id": "command-handlers",
            "title": "CQRS CommandHandler 写侧",
            "status": "as_is",
            "evidence": {"java": cl.grep_files(MAIN, r"CommandHandler", ROOT)[:15]},
        },
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--sync-doc", action="store_true")
    args = parser.parse_args()
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    checks = build_as_is_checks()
    backlog = cl.load_backlog(COLLECTOR_DIR / "backlog.yaml")
    if args.check:
        return 0 if cl.check_outputs(OUT_DIR, checks, backlog, "integration") else 1
    cl.write_outputs(
        OUT_DIR,
        "integration",
        "集成",
        checks,
        backlog,
        generated_at,
        "../../../tools/integration-compliance-collector/backlog.yaml",
    )
    if args.sync_doc:
        ok = cl.sync_design_doc(
            DESIGN_DOC,
            AS_IS_MARKER,
            BACKLOG_MARKER,
            checks,
            backlog,
            "../../_generated/integration/compliance.json",
            "../../../tools/integration-compliance-collector/backlog.yaml",
        )
        if ok:
            print(f"[sync-doc] updated {DESIGN_DOC.relative_to(ROOT)}")
    print(f"[ok] wrote {OUT_DIR.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
