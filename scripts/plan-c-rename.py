#!/usr/bin/env python3
"""Plan C: rename audit/time fields in selected paths (idempotent)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TARGET_DIRS = [
    ROOT / "bone-tool" / "bone-codegen",
    ROOT / "bone-blueprint",
    ROOT / "bone-engine" / "bone-extension-engine",
    ROOT / "bone-frontend" / "apps",
    ROOT / "doc" / "deployment",
    ROOT / "doc" / "design",
]

EXTENSIONS = {".java", ".sql", ".vm", ".md", ".puml", ".ts", ".tsx", ".yaml", ".yml", ".xml", ".go"}

# Java/TS identifiers — longest first
CAMEL_REPLACEMENTS = [
    ("createTimeStr", "createdAtStr"),
    ("updateTimeStr", "updatedAtStr"),
    ("createTimeBegin", "createdAtBegin"),
    ("createTimeEnd", "createdAtEnd"),
    ("getCreateTime", "getCreatedAt"),
    ("setCreateTime", "setCreatedAt"),
    ("getUpdateTime", "getUpdatedAt"),
    ("setUpdateTime", "setUpdatedAt"),
    ("getCreateBy", "getCreatedBy"),
    ("setCreateBy", "setCreatedBy"),
    ("getUpdateBy", "getUpdatedBy"),
    ("setUpdateBy", "setUpdatedBy"),
    ("createTime", "createdAt"),
    ("updateTime", "updatedAt"),
    ("createBy", "createdBy"),
    ("updateBy", "updatedBy"),
    ("lockedUntil", "lockedAt"),
    ("pwdUpdatedAt", "passwordUpdatedAt"),
]

SNAKE_REPLACEMENTS = [
    ("create_time", "created_at"),
    ("update_time", "updated_at"),
    ("create_by", "created_by"),
    ("update_by", "updated_by"),
    ("start_time", "started_at"),
    ("end_time", "ended_at"),
    ("resolved_time", "resolved_at"),
    ("locked_until", "locked_at"),
    ("pwd_updated_at", "password_updated_at"),
]

SKIP_SUBSTRINGS = (
    "updateByCriteria",
    "update_by_criteria",
)


def should_skip_line(line: str) -> bool:
    return any(s in line for s in SKIP_SUBSTRINGS)


def transform(content: str, path: Path) -> str:
    lines = content.splitlines(keepends=True)
    out: list[str] = []
    for line in lines:
        if should_skip_line(line):
            out.append(line)
            continue
        new_line = line
        for old, new in SNAKE_REPLACEMENTS:
            new_line = new_line.replace(old, new)
        ext = path.suffix.lower()
        if ext in {".java", ".ts", ".tsx", ".go", ".vm"}:
            for old, new in CAMEL_REPLACEMENTS:
                new_line = re.sub(rf"\b{re.escape(old)}\b", new, new_line)
        out.append(new_line)
    return "".join(out)


def main() -> int:
    changed = 0
    for base in TARGET_DIRS:
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix.lower() not in EXTENSIONS:
                continue
            if "node_modules" in path.parts or "target" in path.parts:
                continue
            text = path.read_text(encoding="utf-8", errors="replace")
            new_text = transform(text, path)
            if new_text != text:
                path.write_text(new_text, encoding="utf-8")
                changed += 1
                print(path.relative_to(ROOT))
    print(f"Updated {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
