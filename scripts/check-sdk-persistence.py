#!/usr/bin/env python3
"""HC-006：业务代码不得绕过 bone-metadata-sdk 直接使用 JDBC / MyBatis 会话。

SDK 与 metadata-engine 自己就是这条通道的实现，不在扫描范围内。其余 `src/main/java`
里出现下列 import，视为绕过：

- org.springframework.jdbc.core.JdbcTemplate
- org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
- java.sql.Connection / PreparedStatement
- org.apache.ibatis.session.SqlSession

存量命中登记在 `doc/architecture/sdk-persistence-bypass-baseline.json`，只可收缩。
新增文件命中即失败。这不是 ArchUnit `repositoryMustUseSdk`（该规则仍不在共享规则库）。

用法:
  python3 scripts/check-sdk-persistence.py            # 报告
  python3 scripts/check-sdk-persistence.py --check    # 新增零容忍
  python3 scripts/check-sdk-persistence.py --baseline # 用当前命中重写基线
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/sdk-persistence-bypass-baseline.json"
ALLOW_PREFIXES = (
    "bone-engine/bone-metadata-sdk/",
    "bone-engine/bone-metadata-engine/",
)
IMPORT_RE = re.compile(
    r"import\s+(?:"
    r"org\.springframework\.jdbc\.core\.JdbcTemplate|"
    r"org\.springframework\.jdbc\.core\.namedparam\.NamedParameterJdbcTemplate|"
    r"java\.sql\.Connection|"
    r"java\.sql\.PreparedStatement|"
    r"org\.apache\.ibatis\.session\.SqlSession"
    r")\s*;"
)


def hits() -> list[str]:
    found: list[str] = []
    for path in REPO.glob("**/src/main/java/**/*.java"):
        rel = path.relative_to(REPO).as_posix()
        if rel.startswith(ALLOW_PREFIXES):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError:
            continue
        if IMPORT_RE.search(text):
            found.append(rel)
    return sorted(found)


def load_baseline() -> list[str]:
    if not BASELINE.exists():
        return []
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return sorted(data.get("files", []))


def write_baseline(files: list[str]) -> None:
    payload = {
        "description": "HC-006 存量绕过 SDK 的 JDBC/MyBatis import。只可收缩，新增文件不得加入。",
        "files": files,
    }
    BASELINE.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()

    current = hits()
    if args.baseline:
        write_baseline(current)
        print(f"wrote {len(current)} paths to {BASELINE.relative_to(REPO)}")
        return 0

    registered = load_baseline()
    extra = sorted(set(current) - set(registered))
    gone = sorted(set(registered) - set(current))

    print(f"HC-006 bypass imports: current={len(current)} baseline={len(registered)}")
    if gone:
        print(f"  shrinkable ({len(gone)}), remove from baseline when convenient:")
        for path in gone:
            print(f"    - {path}")
    if extra:
        print("  new bypass (not in baseline):", file=sys.stderr)
        for path in extra:
            print(f"    + {path}", file=sys.stderr)
        if args.check:
            return 1
    if args.check:
        print("HC-006 check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
