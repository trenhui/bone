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


def load_exemptions() -> tuple[dict[str, str], dict[str, str]]:
    """加载「技术豁免」与「调试工具豁免」。

    ★ **为什么要有独立于 baseline 的豁免（2026-10-07）**：
    baseline 的语义是「**欠债**——将来要收敛回 SDK 的写法」，规则是「只可收缩」。
    但存量 14 条里有一类是**永远不会收敛**的：`information_schema` DDL 对齐、
    定时任务无租户上下文的跨表清理、配置类的传递依赖……
    把它们放进 baseline 会让 baseline 变成「永不缩小的黑名单」，
    于是「只可收缩」这条规则失去意义（谁也无法让数字变小）。

    分类依据见 `doc/architecture/HC-006-存量绕过裁决.md`，裁决权归模块 Owner。
    豁免同样**只可收缩**：条目被改造成走 SDK 后，应从豁免中移除。

    返回：(技术豁免 → 理由, 调试工具豁免 → 理由)
    """
    if not BASELINE.exists():
        return {}, {}
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    tech = data.get("technicalExemptions", [])
    debug = data.get("debugToolExemptions", [])
    reasons = data.get("exemptionReasons", {})
    tech_map = {p: reasons.get(p, "（未登记理由）") for p in tech}
    debug_map = {p: reasons.get(p, "（未登记理由）") for p in debug}
    return tech_map, debug_map


def write_baseline(files: list[str]) -> None:
    # 保留既有的两层豁免，避免 --baseline 重写时把 technicalExemptions /
    # debugToolExemptions / exemptionReasons 一并抹掉（否则分类裁决丢失，所有项退回"待收敛"）。
    data: dict = {}
    if BASELINE.exists():
        try:
            data = json.loads(BASELINE.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            data = {}
    data["description"] = (
        "HC-006 存量绕过 SDK 的 JDBC/MyBatis import。基线分两层：files=真正待收敛的技术债"
        "（须排期迁 SDK / 服务客户端）；technicalExemptions / debugToolExemptions=结构性或工具性"
        "豁免（SDK 无法表达或 by-design，仍只可收缩）。新增文件命中即失败。"
        "裁决依据见 doc/architecture/HC-006-存量绕过裁决.md。"
    )
    data["files"] = files
    BASELINE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


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
    tech_exempt, debug_exempt = load_exemptions()
    all_exempt = {**tech_exempt, **debug_exempt}

    # 豁免清单里若有不复存在的文件 => 应收敛（与 baseline 同样的「只可收缩」纪律）
    stale_exempt = sorted(set(all_exempt) - set(current))

    extra = sorted(set(current) - set(registered) - set(all_exempt))
    gone = sorted(set(registered) - set(current))

    print(f"HC-006 bypass imports: current={len(current)}")
    print(
        f"  baseline(待收敛)={len(registered)}  "
        f"技术豁免={len(tech_exempt)}  调试工具豁免={len(debug_exempt)}"
    )
    if gone:
        print(f"  shrinkable ({len(gone)}), remove from baseline when convenient:")
        for path in gone:
            print(f"    - {path}")
    if stale_exempt:
        print(
            f"  ⚠️ 豁免清单中已无对应代码（应收敛）: {len(stale_exempt)} 条",
            file=sys.stderr,
        )
        for path in stale_exempt:
            print(f"    - {path}", file=sys.stderr)
    unreasoned = [p for p, why in all_exempt.items() if why == "（未登记理由）"]
    if unreasoned:
        print(
            f"  ⚠️ 豁免条目未登记理由: {len(unreasoned)} 条（见 exemptionReasons）",
            file=sys.stderr,
        )
        for path in unreasoned:
            print(f"    ! {path}", file=sys.stderr)
    if extra:
        print("  new bypass (not in baseline nor exempt):", file=sys.stderr)
        for path in extra:
            print(f"    + {path}", file=sys.stderr)
        if args.check:
            return 1
    if args.check:
        if stale_exempt or unreasoned:
            return 1
        print("HC-006 check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
