#!/usr/bin/env python3
"""HC-008 必备列校验：表必须有 tenant_id / created_at / updated_at / deleted。

为什么不是一个"全表扫一遍就红"的脚本（2026-09-19 实测口径）：
`bone-init.sql` 68 张表里 42 张四列齐全，26 张缺列，而**缺列不等于缺陷**——缺 `tenant_id` 的
13 张里既有真正的全局目录（`iam_permission`），也有应随聚合落盘的子表（`t_order_item`、
`iam_role_permission`、`mdm_qcheck_detail`），还有租户表本身（`iam_tenant`）。机械判定会把
"设计如此"和"漏加列"混成一张红名单，最终只会换来 `--no-verify`。

所以分两段，与 G-2 Freeze 的取向一致（存量登记待分类、新增零容忍）：

- `--check`：**新增表**（不在基线里）缺任一必备列即失败；基线内表**只可收缩**——缺列集合变大
  同样失败；基线里已消失的表提示清理。这条是真正可安全执行的"新增零容忍"。
- 默认（报告）：打印存量缺口清单，供模块 Owner 逐表分类，分类结论回填基线 `note` 后再从基线移除。

登记位置：`doc/architecture/ddl-required-columns-baseline.json`（唯一真源，勿在别处复制清单）。

用法:
  python3 scripts/check-ddl-required-columns.py              # 报告（不阻断）
  python3 scripts/check-ddl-required-columns.py --check      # 新增表门禁 + 基线只可收缩
  python3 scripts/check-ddl-required-columns.py --baseline    # 把当前缺口刷进基线（首次登记用）
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DDL = REPO / "bone-init.sql"
BASELINE = REPO / "doc/architecture/ddl-required-columns-baseline.json"

REQUIRED = ("tenant_id", "created_at", "updated_at", "deleted")

CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?(\w+)`?\s*\((.*?)\n\)\s*ENGINE",
    re.S | re.I,
)


def parse_tables(sql: str) -> dict[str, list[str]]:
    """返回 {表名: 缺失的必备列}。列判定要求出现在列定义位置（行首），避免命中索引定义。"""
    out: dict[str, list[str]] = {}
    for name, body in CREATE_TABLE.findall(sql):
        missing = [
            col
            for col in REQUIRED
            if not re.search(r"(?m)^\s*`?" + col + r"`?\s", body, re.I)
        ]
        if missing:
            out[name] = missing
    return out


def load_baseline() -> dict:
    if not BASELINE.exists():
        return {"tables": {}}
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def write_baseline(missing: dict[str, list[str]]) -> None:
    old = load_baseline().get("tables", {})
    tables = {
        name: {
            "missing": cols,
            "classification": old.get(name, {}).get("classification", "needs-owner-decision"),
            "note": old.get(name, {}).get("note", "待分类：租户表 / 聚合子表 / 全局目录 / 漏列"),
        }
        for name, cols in sorted(missing.items())
    }
    doc = {
        "_comment": (
            "HC-008 存量缺口登记（唯一真源）。新增表必须四列齐全，不在此表内即失败；"
            "此表内表的缺列集合只可收缩。classification：by-design（缺列是设计取舍）/"
            "gap-candidate（疑似漏列，改动属 L3 DDL 变更）/ needs-owner-decision（证据不足待裁决）。"
            "by-design 不等于已合规，只表示缺列有可写明的理由。"
        ),
        "_required_columns": list(REQUIRED),
        "tables": tables,
    }
    BASELINE.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="新增表缺列 / 基线缺口扩大则失败")
    ap.add_argument("--baseline", action="store_true", help="把当前缺口刷进基线")
    args = ap.parse_args()

    if not DDL.exists():
        print("未找到 DDL 文件：{}".format(DDL), file=sys.stderr)
        return 1

    missing = parse_tables(DDL.read_text(encoding="utf-8", errors="ignore"))

    if args.baseline:
        write_baseline(missing)
        print("OK: 已写入基线 {} 张表 → {}".format(len(missing), BASELINE.relative_to(REPO)))
        return 0

    if not args.check:
        print("HC-008 必备列报告（bone-init.sql；必备列 {}）".format("/".join(REQUIRED)))
        if not missing:
            print("OK: 全部表四列齐全")
            return 0
        baseline = load_baseline().get("tables", {})
        pending = [
            n
            for n, cols in missing.items()
            if baseline.get(n, {}).get("classification", "needs-owner-decision")
            != "by-design"
        ]
        print(
            "  缺列 {} 张：已分类为 by-design {} 张，仍待处理 {} 张".format(
                len(missing), len(missing) - len(pending), len(pending)
            )
        )
        for name, cols in sorted(missing.items()):
            cls = baseline.get(name, {}).get("classification", "needs-owner-decision")
            print("    {:<28} 缺 {:<34} [{}]".format(name, "/".join(cols), cls))
        print(
            "  说明：缺 tenant_id 既可能是全局目录 / 聚合子表，也可能是漏列——"
            "分类结论写入 {}".format(BASELINE.relative_to(REPO))
        )
        return 0

    baseline = load_baseline().get("tables", {})
    errors: list[str] = []
    for name, cols in sorted(missing.items()):
        if name not in baseline:
            errors.append(
                "新增表 `{}` 缺必备列 {}（HC-008：新增表必须含 {}）".format(
                    name, "/".join(cols), "/".join(REQUIRED)
                )
            )
            continue
        was = baseline[name].get("missing", [])
        worse = [c for c in cols if c not in was]
        if worse:
            errors.append(
                "基线内表 `{}` 缺列扩大：{}（基线只可收缩）".format(name, "/".join(worse))
            )
    for name in sorted(set(baseline) - set(missing)):
        print("提示：`{}` 已不在缺口列表中，可从基线移除".format(name))

    if errors:
        print("HC-008 必备列检查失败：")
        for e in errors:
            print("  - {}".format(e))
        return 1
    pending = [
        n
        for n, meta in baseline.items()
        if meta.get("classification", "needs-owner-decision") != "by-design"
    ]
    print(
        "OK: HC-008 必备列检查通过（新增表 0 违规；基线内 {} 张存量表缺口未扩大，"
        "其中 {} 张仍待处理：gap-candidate / needs-owner-decision）".format(
            len(baseline), len(pending)
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
