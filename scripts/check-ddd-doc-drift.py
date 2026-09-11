#!/usr/bin/env python3
"""Bone DDD 文档防漂移 lint（P1-3 最小实现）。

两项检查：
1. v4 编号残留：新文档正文不允许出现 v4.x 的 R1–R9 / 铁律编号（Legacy 兼容章节白名单除外）；
2. 平台 API 引用真实性：文档代码中调用的 bone-core / SDK 公共方法必须真实存在。

用法：python3 scripts/check-ddd-doc-drift.py   （退出码非 0 即失败，供 CI 消费）
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent

DOC_FILES = [
    REPO / "doc/architecture/Bone-DDD-最终实践方案.md",
]

# Legacy 兼容章节内允许出现旧编号；锚点行也放行（历史链接迁移期）。
LEGACY_ANCHOR = re.compile(r"<a id=\"[^\"]*\"></a>")

V4_NUMBERING = re.compile(
    r"`R[1-9]`|(?<![A-Za-z0-9-])R[1-9](?![0-9A-Za-z-])铁律|铁律（R[1-9]）|v4\.\d"
)

# 从文档里抽取的反引号方法调用 → 必须在真实 API 白名单里。
API_WHITELIST = {
    "publishFrom",        # DomainEventPublisher default
    "publish",            # DomainEventPublisher
    "publishAll",         # DomainEventPublisher
    "addDomainEvent",     # AggregateRoot
    "getDomainEvents",    # AggregateRoot
    "clearDomainEvents",  # AggregateRoot
    "findById",           # BaseRepository
    "findByIdInTenant",   # TenantRepository
    "save",
    "markDeleted",
}

API_CALL = re.compile(r"(?:aggregate|repository|orderRepository|paymentRepository|repo|eventPublisher|domainEventPublisher)\.(\w+)\(")

def check_v4_numbering(text: str, path: Path):
    errors = []
    in_legacy = False
    for i, line in enumerate(text.splitlines(), 1):
        if LEGACY_ANCHOR.search(line) or line.startswith("## Legacy-"):
            in_legacy = True
        elif line.startswith("### ") or line.startswith("## "):
            in_legacy = line.startswith("## Legacy-")
        if in_legacy:
            continue
        if "e-53-应用层结构" in line or "e-54-模块适用性" in line or "b3-archunit-模板" in line:
            continue
        for m in V4_NUMBERING.finditer(line):
            errors.append(f"{path.relative_to(REPO)}:{i}: v4 编号残留「{m.group(0)}」")
    return errors

def strip_code_blocks(text: str):
    """返回 (去围栏正文, 代码块合集)"""
    parts = text.split("```")
    prose = "".join(p for i, p in enumerate(parts) if i % 2 == 0)
    code = "\n".join(p for i, p in enumerate(parts) if i % 2 == 1)
    return prose, code

def check_api_calls(text: str, path: Path):
    errors = []
    _, code = strip_code_blocks(text)
    for i, line in enumerate(code.splitlines(), 1):
        for m in API_CALL.finditer(line):
            if m.group(1) not in API_WHITELIST:
                errors.append(
                    f"{path.relative_to(REPO)}: 文档代码调用未知平台 API「.{m.group(1)}(」"
                    f"（不在白名单；新增 API 请同步白名单与 bone-core/SDK 真实签名）"
                )
    return errors

def check_markdown_links(text: str, path: Path):
    errors = []
    for i, line in enumerate(text.splitlines(), 1):
        for target in re.findall(r"\]\((\.{1,2}/[^)#]+)", line):
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"{path.relative_to(REPO)}:{i}: 相对链接目标不存在：{target}")
    return errors

def main():
    errors = []
    for f in DOC_FILES:
        if not f.exists():
            continue
        text = f.read_text(encoding="utf-8")
        errors += check_v4_numbering(text, f)
        errors += check_api_calls(text, f)
        errors += check_markdown_links(text, f)
    if errors:
        print("DDD 文档防漂移检查失败：")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    print(f"OK: {len(DOC_FILES)} 个 DDD 文档通过防漂移检查（v4 编号 / 平台 API / 相对链接）")

if __name__ == "__main__":
    main()
