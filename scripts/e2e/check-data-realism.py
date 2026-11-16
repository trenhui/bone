#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""造数真实性门禁 —— 拦住「看不懂的假数据」，把生产仿真口径变成机器判据。

为什么要有它
------------
`e2e_lib/fixtures` 已经把「像生产」的方法给出来了，但**给方法不等于会用**：
2026-11 实测仍有脚本写 `"张三"`、`receiverName: "李四"`、`amount: 8800`、
`price: 199.0`。这类数据跑得通，却无法演示、无法评审，还会让上下游客服对不上号。
本门禁只做一件事：把「新写出来的机器味数据」挡在提交前。

判据（违规即退出码 1）
--------------------
  R1 占位人名/垃圾串：`"张三"` / `"李四"` / `"test123"` / `"abc123"` / `"asdf"` / `"lorem"`
  R2 未播种随机造数：Python 直接调 `random.random/randint/choice`；JS `Math.random()`
     落在 name/amount/price 这类数据字段上（幂等键等非数据用途不在此列）
  R3 整百/整千金额字面量：`amount: 8800`、`price: 199.0`（业务金额应带分位，见 fixtures.amount）
  R4 UUID 当显示名：`name = str(uuid4())`、`title: uuid4()`
  R5 测试态占位显示值：`"测试地址"` / `"测试商品"` / `"某省某市某区 1 号"` / `"E2E买家"`

为什么有 baseline
----------------
存量脚本里的遗留脏数据不是本轮能一次清完的（改了可能动到断言）。按仓库既有棘轮
纪律（`check-archunit-freeze-baseline.py`、`MISSING_TEST_BASELINE`）：基线**只可收缩**——
新增违规必红；修掉一部分却不下调基线也红。基线文件：`data-realism-baseline.json`。

仅扫描代码行：注释与 docstring 里举反例（如本文件的 R1 说明）不算违规。

用法::

    python3 scripts/e2e/check-data-realism.py            # 默认扫描本目录
    python3 scripts/e2e/check-data-realism.py --json
    python3 scripts/e2e/check-data-realism.py --update-baseline   # owner 显式操作
"""
import argparse
import json
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
BASELINE = os.path.join(HERE, "data-realism-baseline.json")
SUFFIXES = (".py", ".mjs", ".cjs")
# selftest 用 2863.5 作为 fixtures 守恒基准值（测的是库本身，不是业务数据），不属于造数脏数据
EXEMPT_NAMES = {"selftest.py", "selftest.cjs"}

RULES = [
    ("R1", "占位人名/垃圾串",
     re.compile(r'"(?:张三|李四|王五|赵六|test123|abc123|asdf|lorem ipsum|foo)"')),
    ("R2", "未播种随机造数",
     re.compile(r'\brandom\.(?:random|randint|choice)\(|'
                r'(?:name|Name|amount|Amount|price|Price|title|Title)\w*\s*[=:][^;\n]*'
                r'Math\.random\(')),
    ("R3", "整百/整千金额字面量",
     re.compile(r'(?i)\b(?:amount|price|total|money|fee|金额)\w*\s*[=:]\s*(\d{3,})(?:\.0+)?(?![\d.])')),
    ("R4", "UUID 当显示名",
     re.compile(r'(?i)\b(?:name|title|label)\w*\s*[=:]\s*[^,\n]*uuid4\(')),
    ("R5", "测试态占位显示值",
     re.compile(r'"(?:测试地址|测试商品|联调测试商品|取消测试商品|E2E买家|E2E商品|某省某市某区[^"]*)"')),
]


def _strip_comments(text, suffix):
    """去掉注释与 docstring 后的代码行（保留行号对齐，违规报行号才准）。"""
    out = []
    in_triple = None
    for line in text.splitlines():
        stripped = line.strip()
        if suffix == ".py":
            if in_triple:
                if in_triple in line:
                    in_triple = None
                out.append("")
                continue
            # docstring 只会出现在行首；同一行内闭合的（"""反例：xxx"""）直接整行丢弃，
            # 否则 docstring 里举的反例会被当成真实违规（误报比漏报更糟）。
            if stripped.startswith(('"""', "'''")):
                quote = stripped[:3]
                if quote not in stripped[3:]:
                    in_triple = quote
                out.append("")
                continue
            out.append("" if stripped.startswith("#") else line)
        else:
            out.append("" if stripped.startswith("//") or stripped.startswith("*") else line)
    return out


def scan_file(path, rel):
    suffix = os.path.splitext(path)[1]
    try:
        with open(path, encoding="utf-8", errors="ignore") as fh:
            lines = _strip_comments(fh.read(), suffix)
    except OSError:
        return []
    hits = []
    for lineno, line in enumerate(lines, 1):
        if not line.strip():
            continue
        for rule_id, rule_name, pattern in RULES:
            m = pattern.search(line)
            if m:
                hits.append({
                    "file": rel,
                    "line": lineno,
                    "rule": rule_id,
                    "rule_name": rule_name,
                    "excerpt": line.strip()[:120],
                    "match": m.group(0)[:60],
                })
    return hits


def iter_files(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d != "__pycache__" and not d.startswith("."))
        for name in sorted(filenames):
            if name.endswith(SUFFIXES) and name not in EXEMPT_NAMES:
                full = os.path.join(dirpath, name)
                yield full, os.path.relpath(full, root)


def load_baseline(path):
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as fh:
        doc = json.load(fh)
    return dict(doc.get("entries", {}))


def evaluate(hits, baseline):
    """返回 (新增违规, 应收缩项)。baseline 键为 `rel|RULE` → 允许条数。"""
    counts = Counter("%s|%s" % (h["file"], h["rule"]) for h in hits)
    new = []
    for hit in hits:
        key = "%s|%s" % (hit["file"], hit["rule"])
        allowed = baseline.get(key, 0)
        if counts[key] > allowed:
            # 同一键超基线时，只报前 (count-allowed) 条，避免刷屏
            new.append(hit)
            counts[key] -= 1
            if counts[key] <= 0:
                counts[key] = allowed  # 后续同键不再重复计
    shrink = [k for k, v in baseline.items() if Counter(
        "%s|%s" % (h["file"], h["rule"]) for h in hits).get(k, 0) < v]
    return new, shrink


def main(argv=None):
    parser = argparse.ArgumentParser(description="造数真实性门禁（R1–R4）")
    parser.add_argument("--root", default=HERE)
    parser.add_argument("--baseline", default=BASELINE)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--update-baseline", action="store_true",
                        help="按当前实测重写基线（owner 操作；会放宽约束，勿随手执行）")
    args = parser.parse_args(argv)

    hits = []
    for full, rel in iter_files(args.root):
        hits.extend(scan_file(full, rel))

    if args.update_baseline:
        entries = dict(Counter("%s|%s" % (h["file"], h["rule"]) for h in hits))
        doc = {
            "_note": "造数真实性门禁基线（只可收缩）。修掉违规后必须同步删减，否则判红。",
            "entries": entries,
        }
        with open(args.baseline, "w", encoding="utf-8") as fh:
            json.dump(doc, fh, ensure_ascii=False, indent=2, sort_keys=True)
            fh.write("\n")
        print("已写基线: %s（%d 个键）" % (args.baseline, len(entries)))
        return 0

    baseline = load_baseline(args.baseline)
    new, shrink = evaluate(hits, baseline)

    if args.json:
        print(json.dumps({"new": new, "shrink": shrink,
                          "baseline_keys": len(baseline)}, ensure_ascii=False, indent=2))
    else:
        if new:
            print("❌ 新增/超基线的机器味数据 %d 条：" % len(new))
            for h in new:
                print("  %s:%s [%s %s] %s" % (h["file"], h["line"], h["rule"],
                                              h["rule_name"], h["excerpt"]))
            print("\n改法：业务值统一走 `e2e_lib/fixtures`（人读名称/带分位金额/可读单号），")
            print("      或直接 `python3 scripts/e2e/datagen.py list` 选场景造数。")
        if shrink:
            print("❌ 基线该收缩（违规已修复，但基线未下调）：")
            for key in sorted(shrink):
                print("  %s" % key)
            print("  执行 `python3 scripts/e2e/check-data-realism.py --update-baseline` 并复核 diff。")
        if not new and not shrink:
            print("✅ 造数真实性：无新增违规（基线 %d 键仅可收缩）" % len(baseline))

    return 1 if (new or shrink) else 0


if __name__ == "__main__":
    raise SystemExit(main())
