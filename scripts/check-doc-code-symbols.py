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

    # ---- 基线 stale 检测（2026-10-05 补）--------------------------------
    # 为什么需要：符号级豁免的语义是「文档里还在写这个类名，暂不报错」，一旦代码里该类
    # 已被重命名/删除，这条豁免就**永远不会再生效**——而它仍静静躺在基线里，让基线可以
    # 无限膨胀且无人察觉（这正是「基线只可收缩」缺机器判据的形态）。
    # 探针实测：往symbols 里塞一个代码中不存在的类名，本脚本原本 EXIT=0 且一句提示都没有。
    # 故此处对齐 check-soft-delete-declaration.py 的形态：识别 stale 并阻断，让人来收缩。
    # 注意方向：符号级豁免的语义是「文档里仍在写这个类名，而代码里已不存在（如 ADR-0028 把
    # Handler 收敛成 ApplicationService）⇒ 登记豁免以免误报」。因此
    #   代码里**没有**该类 ⇒ 豁免正在生效（正常）；
    #   代码里**已有**该类   ⇒ 豁免多余（stale，该删）。
    # 反向判据会把全部正常豁免误判为 stale（实测踩过：首版写成 `not in classes`，
    # 真实基线直接跑出 EXIT=1 并把 AccountAuthoritiesQueryHandler 等正常条目全列为 stale）。
    stale_symbols = sorted(s for s in baseline_symbols if s in classes)
    # 基线里 docLevel 的值是**相对 doc/** 的（如 design/modules/x.md），
    # 必须用 DOC_DIR 拼接；早先误用 ROOT 导致所有 docLevel 都被判成 stale（假红）。
    stale_docs = sorted(d for d in doc_level if not (DOC_DIR / d).exists())
    if stale_symbols or stale_docs:
        print(
            "⚠️ 基线中已不再生效的条目（代码里已无对应类 / 文档已删除），可从基线移除：",
            file=sys.stderr,
        )
        for s in stale_symbols:
            print(f"  [symbol] {s}", file=sys.stderr)
        for d in stale_docs:
            print(f"  [doc]    {d}", file=sys.stderr)
        print(
            "基线只可收缩：这些条目对门禁已无约束力，留着会让基线变成「什么都能豁免」的口子。"
            "请删除后重跑。",
            file=sys.stderr,
        )
        return 1
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
