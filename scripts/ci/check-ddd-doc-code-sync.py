#!/usr/bin/env python3
"""DDD 规范文档 ↔ 代码 符号一致性 lint（防漂移，P1-3）。

检查三类最廉价的漂移——这正是 v5.0.2 复核发现 P0-1/P0-2 的病根：

1. 文档规范正文中引用bone-core / SDK 不存在的 API 符号
   （如历史案例 publish(aggregate.releaseDomainEvents())）；
2. 文档残留 v4.x 历史编号被当作现行条文引用
   （如 E-5.3.1 出现在非兼容区段落作为当前规范）。
3. E-13 命名约定小节的 `A` → `B` 映射样例指向不存在的类
   （2026-09-18 实测：E-13.3 的 `PaymentGateway` → `SimulatedPaymentGatewayImpl` 中右侧类已被删除，
   文档却仍把它当"当前规则样例"——命名小节是全文最容易被重构打穿的地方）。

设计取向：
- 只对单一规范正文 doc/architecture/Bone-DDD-最终实践方案.md 生效；
- API 符号真源从 bone-core 与 bone-sdk 源码自动提取 public 成员，
  文档里出现「不存在符号 + 调用形态」才告警，注释/术语命中不告警；
- 默认 warning 模式：有 --strict 才退出非零，便于先挂 CI 观察。
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# 规范正文范围（标题/引用区排 CHANGELOG 等叙事文件）
DOC_FILES = [
    REPO / "doc/architecture/Bone-DDD-最终实践方案.md",
]

# 符号真源根目录：bone-core + 客户端 SDK + metadata-sdk（Repository / 分页等持久化 API 的真源，
# 文档大量示例调用 repository.xxx(...)，不纳入此真源则无法校验）
CODE_BASES = (
    "bone-framework/bone-core",
    "bone-sdk",
    "bone-engine/bone-metadata-sdk",
)

# —— v4.x 历史编号：作为「当前条文锚点」出现在正文即漂移 ——
# 注：主文档「兼容入口与迁移说明」章节内的 Legacy 标题属白名单；
# 07-supplements 等分册正文中以 R# 论证历史的段落允许出现在括号注释里。
V4_RULE_REF = re.compile(r"(?<![A-Za-z0-9-])R[1-9](?![A-Za-z0-9-])")
V4_SECTION_REF = re.compile(r"(?<![A-Za-z0-9.-])(?:E-\d+(?:\.\d+)*|G-\d+(?:\.\d+)*|P-\d+(?:\.\d+)*)(?![A-Za-z0-9-])")
COMPAT_ZONE_HEADING = re.compile(r"^#{1,3}\s*(兼容入口|Legacy)")

# 3) 命名约定样例：`A` → `B`。右侧是具体类名时必须真实存在；左右两侧都是抽象构件名的
# 通用模式（如 `Command` → `CommandHandler`）登记在豁免表里。
NAMING_SAMPLE = re.compile(r"`([A-Za-z][A-Za-z0-9]*)`\s*→\s*`([A-Z][A-Za-z0-9]*)`")
NAMING_SAMPLE_EXEMPT = {
    ("Command", "CommandHandler"): "抽象构件名之间的通用映射，不是具体类",
}
# 允许把历史改名记录在案（与 v4 编号检查同一取向：显式标为历史/已删除则放过），
# 否则"已收敛（保留追溯）"这类记录本身会被判红。
NAMING_SAMPLE_HISTORICAL = re.compile(r"(历史|曾用|旧版|已删除|已收敛|收敛前|改名前)")
JAVA_PRUNED_DIRS = {"target", "node_modules", ".git", "build", "dist"}

# 调用形态提取：publisher.xxx( / aggregate.releaseDomainEvents() / repo.findById(...)
CALL = re.compile(
    r"\b(?:(?:domainEventPublisher|eventPublisher|publisher)\.(?P<ev>[a-zA-Z_]\w*)\()"
    r"|\b(?P<agg>[a-zA-Z_]\w*)\.releaseDomainEvents\(\)"
    r"|\b(?:(?:orderRepository|repository|repo|[a-zA-Z_]\w*[Rr]epository))\.(?P<repo>[a-zA-Z_]\w*)\("
    r"|\b(?P<find>findById)\(",
)


def collect_code_symbols() -> set[str]:
    syms: set[str] = set()
    for base in CODE_BASES:
        root = REPO / base
        if not root.exists():
            continue
        for java in root.rglob("*.java"):
            text = java.read_text(encoding="utf-8", errors="ignore")
            # public/default 方法名
            syms.update(re.findall(r"\b(?:public|default)\s+(?:[\w<>\[\],.?]+\s+)+(\w+)\s*\(", text))
            # public final 字段/record 组件
            syms.update(re.findall(r"\bpublic\s+(?:static\s+)?final\s+[\w<>\[\],.?]+\s+(\w+)", text))
            # 类型名
            syms.update(re.findall(r"\b(?:public|final)?\s*(?:abstract\s+)?(?:class|interface|record|enum)\s+(\w+)", text))
    return syms


def collect_java_simple_names() -> set[str]:
    """全仓 .java 的类文件名集合（真源取文件系统，含未提交新增文件）。

    刻意不用 `git ls-files`：改名"先动文件、后 add"是常见工作序，索引口径会把旧名留在集合里、
    把新名挡在外面，反而放过最该拦的漂移。
    """
    names: set[str] = set()
    for dirpath, dirnames, filenames in os.walk(REPO):
        dirnames[:] = [d for d in dirnames if d not in JAVA_PRUNED_DIRS]
        for fn in filenames:
            if fn.endswith(".java"):
                names.add(fn[: -len(".java")])
    return names


def collect_repository_methods() -> set[str]:
    """收集「仓储方法宇宙」：SDK ``Repository`` 基接口 + 全仓 ``*Repository.java`` 声明的方法名。

    文档里的 ``repository.xxx(...)`` 必须命中此集合才算合法。不能退化为「全仓符号并集」——
    否则 ``FragmentCache.getById`` 之类同名方法会把 ``customerRepository.getById`` 误放行。
    领域写仓储常声明 SDK 之外的复合键方法（如 ``findByIdInTenant``），一并纳入。
    """
    methods: set[str] = set()
    for java in REPO.rglob("*Repository.java"):
        text = _strip_java_comments(java.read_text(encoding="utf-8", errors="ignore"))
        for m in re.finditer(r"(?<![\w.$])(\w+)\s*\(", text):
            name = m.group(1)
            if name not in JAVA_KEYWORDS:
                methods.add(name)
    return methods


# Java 控制/构造关键字：出现即非方法声明名，避免污染仓储方法集合。
JAVA_KEYWORDS = {
    "if", "for", "while", "switch", "catch", "return", "new", "super", "this",
    "else", "do", "try", "synchronized", "assert", "throw", "case", "instanceof",
}


def _strip_java_comments(text: str) -> str:
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def compat_zones(lines: list[str]) -> list[tuple[int, int]]:
    """主文档「兼容入口与迁移说明」章节行号区间（1-based）。"""
    zones: list[tuple[int, int]] = []
    start = None
    for i, ln in enumerate(lines, 1):
        if ln.startswith("# ") and "兼容入口" in ln:
            start = i
        elif start is not None and ln.startswith("# "):
            zones.append((start, i - 1))
            start = None
    if start is not None:
        zones.append((start, len(lines)))
    return zones


def in_zone(no: int, zones: list[tuple[int, int]]) -> bool:
    return any(a <= no <= b for a, b in zones)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="发现问题时退出非零（默认仅告警）")
    ap.add_argument("--v4-refs", action="store_true", help="同时检查 v4 编号残留")
    args = ap.parse_args()

    code_syms = collect_code_symbols()
    repo_methods = collect_repository_methods()
    java_names = collect_java_simple_names()
    problems: list[str] = []

    known_aggregate_api = {"addDomainEvent", "getDomainEvents", "clearDomainEvents", "releaseDomainEvents"}
    known_publisher_api = {"publish", "publishAll", "publishFrom"}

    for doc in DOC_FILES:
        if not doc.exists():
            continue
        lines = doc.read_text(encoding="utf-8", errors="ignore").splitlines()
        zones = compat_zones(lines) if doc.name.startswith("Bone-DDD") else []

        for no, raw in enumerate(lines, 1):
            if raw.lstrip().startswith((">", "|", "#")) is False and not raw.strip():
                continue
            text = raw
            # 1) API 调用形态核对
            for m in CALL.finditer(text):
                ev, agg, repo_m = m.group("ev"), m.group("agg"), m.group("repo")
                if ev and ev not in known_publisher_api and ev not in code_syms:
                    problems.append(f"{doc.name}:{no} 未知 publisher API: .{ev}(")
                if ".releaseDomainEvents()" in text:
                    agg_name = agg or "?"
                    problems.append(
                        f"{doc.name}:{no} 聚合 API releaseDomainEvents() 不存在于 AggregateRoot"
                        f"（真源：addDomainEvent/getDomainEvents/clearDomainEvents + publishFrom）[ctx: {agg_name}]"
                    )
                # 仓储方法必须落在「仓储方法宇宙」（SDK Repository 基接口 + 全仓 *Repository）。
                if repo_m and repo_m not in repo_methods:
                    problems.append(f"{doc.name}:{no} 未知仓储方法: .{repo_m}(")
            # 2) v4 编号残留（可选，默认开在 --v4-refs 时）
            if args.v4_refs and not in_zone(no, zones):
                if V4_RULE_REF.search(text) and "R1" in text and "铁律" in text:
                    problems.append(f"{doc.name}:{no} 残留 v4 铁律编号表述：{text.strip()[:80]}")
                for mm in V4_SECTION_REF.finditer(text):
                    ref = mm.group(0)
                    # 兼容历史注释（含「v4」「历史」「曾」字样的行放过）
                    ctx = text[max(0, mm.start() - 30) : mm.end() + 10]
                    if re.search(r"(v4|历史|曾用|旧版)", ctx):
                        continue
                    problems.append(f"{doc.name}:{no} 疑似 v4 条文编号残留：{ref}")
            # 3) 命名约定样例 `A` → `B`：右侧类名必须真实存在
            for mm in NAMING_SAMPLE.finditer(text):
                left, right = mm.group(1), mm.group(2)
                if (left, right) in NAMING_SAMPLE_EXEMPT:
                    continue
                if len(left) == 1 or len(right) == 1 or "Xxx" in left + right:
                    continue  # A → B / XxxPlaceholder 之类的字面占位，不是真实类名
                if NAMING_SAMPLE_HISTORICAL.search(text):
                    continue
                if right not in java_names:
                    problems.append(
                        f"{doc.name}:{no} 命名样例 `{left}` → `{right}` 的右侧类在仓库中不存在"
                        f"（改名/删除后未同步文档？）"
                    )

    if problems:
        level = "ERROR" if args.strict else "WARN"
        for p in problems:
            print(f"[{level}] {p}")
        print(f"\n共 {len(problems)} 处疑似漂移。" + ("" if args.strict else "（warning 模式，不阻断；修复后可切 --strict）"))
        return 1 if args.strict else 0
    print("DDD doc↔code sync lint: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
