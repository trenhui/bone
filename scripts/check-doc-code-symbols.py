#!/usr/bin/env python3
"""检查 doc/ 中引用的 Java 类名是否能在仓库里找到同名 .java 文件。

只报告疑似漂移，不自动修改文档。已登记的 baseline 条目不重复报。

用法：
    python3 scripts/check-doc-code-symbols.py                  # 报告未登记的疑似漂移
    python3 scripts/check-doc-code-symbols.py --all            # 连 Vision / Target 段落一起报
    python3 scripts/check-doc-code-symbols.py --show-baseline  # 打印 baseline 内容

退出码：0 = 无新增疑似漂移；1 = 存在疑似漂移（仅提示，不阻断）。

baseline 见 doc/architecture/doc-code-symbols-baseline.json，只可收缩：
文档修订使某个类名不再出现在文中后，应从 baseline 删除对应条目。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOC_DIR = ROOT / "doc"
BASELINE = ROOT / "doc" / "architecture" / "doc-code-symbols-baseline.json"
SKIP_DIRS = ("archive", "_generated", ".git")

# 历史快照：内容刻意保留决策时点状态，不参与漂移检查
SKIP_FILES = {"Bone-DDD-最终实践方案 1.0.md"}

SUFFIXES = (
    "Controller",
    "Repository",
    "Handler",
    "ApplicationService",
    "Gateway",
    "Adapter",
    "Job",
    "Validator",
    "Mapper",
    "Assembler",
    "Converter",
    "Properties",
    "Configuration",
)
SYMBOL = re.compile(r"\b([A-Z][A-Za-z0-9]{3,}(?:" + "|".join(SUFFIXES) + r"))\b")

# 泛化模式名：文档用它讲规则，不是指某个具体类
GENERIC = {
    "ApplicationService",
    "QueryPort",
    "CommandHandler",
    "QueryHandler",
    "QueryAdapter",
    "QueryService",
    "DomainService",
    "ReadPort",
    "GatewayAdapter",
    "CommandRepository",
    "QueryRepository",
}

# 第三方 / JDK / Spring 常见类名
THIRD_PARTY = {
    "RestController",
    "ResponseEntity",
    "DataMapper",
    "ObjectMapper",
    "RestTemplate",
    "WebClient",
    "TransactionTemplate",
    "PropertyValidator",
    "ConfigurationProperties",
    "TypeHandler",
}


def collect_java_classes() -> set[str]:
    classes: set[str] = set()
    for path in ROOT.rglob("*.java"):
        parts = path.parts
        if any(part in SKIP_DIRS or part in ("target", "node_modules") for part in parts):
            continue
        classes.add(path.stem)
    return classes


def collect_docs() -> list[Path]:
    docs = []
    for path in DOC_DIR.rglob("*.md"):
        rel_parts = path.relative_to(DOC_DIR).parts
        if any(part in SKIP_DIRS for part in rel_parts):
            continue
        if path.name in SKIP_FILES:
            continue
        docs.append(path)
    return sorted(docs)


def load_baseline() -> tuple[set[str], dict[str, list[str]]]:
    """返回（整篇豁免的相对路径集合，symbol -> 允许出现的文档相对路径）。"""
    if not BASELINE.exists():
        return set(), {}
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    doc_level = {item["doc"] for item in data.get("docLevel", [])}
    symbols: dict[str, list[str]] = {}
    for item in data.get("symbols", []):
        symbols[item["symbol"]] = [d.replace("doc/", "") for d in item.get("docs", [])]
    return doc_level, symbols


def main() -> int:
    parser = argparse.ArgumentParser(description="检查文档中引用的 Java 类名是否与仓库一致")
    parser.add_argument("--all", action="store_true", help="一并报告 Vision / Target 段落中的引用")
    parser.add_argument("--show-baseline", action="store_true", help="打印 baseline 内容")
    args = parser.parse_args()

    if args.show_baseline:
        if not BASELINE.exists():
            print("baseline 不存在:", BASELINE)
            return 0
        data = json.loads(BASELINE.read_text(encoding="utf-8"))
        print(f"baseline v{data.get('version')}，整篇豁免 {len(data.get('docLevel', []))} 篇，"
              f"符号级 {len(data.get('symbols', []))} 条")
        for item in data.get("docLevel", []):
            print(f"  [doc]  {item['doc']}  ({item['reason']}) {item.get('note', '')}")
        return 0

    classes = collect_java_classes()
    doc_level, baseline_symbols = load_baseline()
    hits: dict[str, list[tuple[str, int]]] = {}

    for doc in collect_docs():
        rel = doc.relative_to(ROOT).as_posix()
        rel_in_doc = doc.relative_to(DOC_DIR).as_posix()
        if rel_in_doc in doc_level:
            continue
        for lineno, line in enumerate(doc.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
            if not args.all and ("[Vision]" in line or "[Target]" in line):
                continue
            for match in SYMBOL.finditer(line):
                name = match.group(1)
                if name in GENERIC or name in THIRD_PARTY or name in classes:
                    continue
                if name.endswith("s") and name[:-1] in classes:
                    continue
                allowed_docs = baseline_symbols.get(name)
                if allowed_docs and rel_in_doc in allowed_docs:
                    continue
                hits.setdefault(name, []).append((rel, lineno))

    if not hits:
        print(f"OK: 未发现未登记的疑似类名漂移（仓库 Java 类 {len(classes)} 个）")
        return 0

    print(f"未登记疑似类名漂移 {len(hits)} 个（仓库 Java 类 {len(classes)} 个）\n")
    for name, places in sorted(hits.items(), key=lambda item: -len(item[1])):
        rel, lineno = places[0]
        print(f"  {name}  ({len(places)} 处)  {rel}:{lineno}")

    print("\n处理顺序：先确认代码里真实类名，再改文档；确认属示意名 / 历史名则登记进 baseline。")
    print("baseline 只可收缩：文档修订后类名不再出现，应删除对应条目。")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
