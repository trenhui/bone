#!/usr/bin/env python3
"""Bone DDD 文档防漂移 lint（P1-3 最小实现）。

四项检查：
1. v4 编号残留：新文档正文不允许出现 v4.x 的 R 加数字编号 / 铁律编号（Legacy 兼容章节白名单除外）；
2. 平台 API 引用真实性：文档代码里调用的平台方法必须落在白名单内。白名单由
   `bone-core` 与 `bone-metadata-sdk` 的真实公开方法自动收集（源码缺失时退回静态名单）；
3. 相对链接可达性：`](./x.md)` 目标文件必须存在；
4. 内部锚点可达性：`](#x)` 必须能解析到某个标题自动锚或显式 `<a id="x">`，且大小写与目标一致。

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
    r"`R[1-9]`"
    r"|(?<![A-Za-z0-9_-])R[1-9](?![0-9A-Za-z_-])铁律"
    r"|铁律（R[1-9]）"
    r"|(?<![A-Za-z0-9_-])R[1-9](?![0-9A-Za-z_])"
    r"|v4\.\d"
)

# 静态兜底：即使源码暂不可读，也要能挡住明显不存在的平台方法名。
API_WHITELIST_FALLBACK = {
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

# 白名单真源：这些文件里的公开方法即文档允许出现的平台 API。
# 除平台层外，还纳入被本文档正式引为范例的样板模块写侧仓储：文档用它们演示乐观锁
# （`update(entity)`）与写用例收尾，是真实存在的 API，不是编造。
# 该收集是动态的——样板仓储改名后此处随之更新，不会留成陈旧条目。
API_SOURCES = [
    "bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/Repository.java",
    "bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/BaseRepository.java",
    "bone-framework/bone-core/src/main/java/com/bone/core/domain/AggregateRoot.java",
    "bone-framework/bone-core/src/main/java/com/bone/core/domain/event/DomainEventPublisher.java",
    "bone-blueprint/src/main/java/com/bone/blueprint/domain/repository/OrderRepository.java",
    "bone-blueprint/src/main/java/com/bone/blueprint/domain/repository/PaymentRepository.java",
]

_SIGNATURE = re.compile(r"^[\w\s<>,.\[\]?]+?\b(\w+)\s*\(")
_KEYWORDS = {"if", "for", "while", "switch", "catch", "return", "new", "throw", "synchronized"}


def collect_api_symbols():
    """从真实源码收集公开方法名；返回 (符号集合, 缺失文件列表)。"""
    symbols = set(API_WHITELIST_FALLBACK)
    missing = []
    for rel in API_SOURCES:
        src = REPO / rel
        if not src.exists():
            missing.append(rel)
            continue
        for line in src.read_text(encoding="utf-8").splitlines():
            m = _SIGNATURE.match(line)
            if m and m.group(1) not in _KEYWORDS:
                symbols.add(m.group(1))
    return symbols, missing


# 文档里被调用的接收者名（覆盖面有限：新增示范对象名时同步这里，否则该调用不被检查）。
API_CALL = re.compile(
    r"(?:aggregate|repository|orderRepository|paymentRepository|repo"
    r"|eventPublisher|domainEventPublisher)\.(\w+)\("
)

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

def check_api_calls(text: str, path: Path, whitelist):
    errors = []
    _, code = strip_code_blocks(text)
    for i, line in enumerate(code.splitlines(), 1):
        for m in API_CALL.finditer(line):
            if m.group(1) not in whitelist:
                errors.append(
                    f"{path.relative_to(REPO)}: 文档代码调用未知平台 API「.{m.group(1)}(」"
                    f"（不在白名单；新增 API 请确认 bone-core / SDK 真实签名，"
                    f"必要时同步 API_SOURCES）"
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

def _gh_anchor(heading: str) -> str:
    """按 GitHub 规则生成标题锚点：小写、空格转连字符、剥离其余标点。"""
    a = heading.strip().lower().replace(" ", "-")
    return re.sub(r"[^\w\u4e00-\u9fff\-]", "", a)


def check_anchor_links(text: str, path: Path):
    """校验 `](#anchor)` 能解析到标题自动锚或显式 `<a id="anchor">`，且大小写一致。

    结构规范化（层级调整、章节重排）最容易造成内部断链，此检查守住该回归。
    GitHub 生成的 HTML id 一律小写，`#E-37-X` 这类写法虽然能通过宽松比较，
    在实际渲染的页面上却是断链，所以大小写不一致同样报错。
    """
    errors = []
    targets = {}
    in_fence = False
    for line in text.splitlines():
        s = line.strip()
        if s.startswith("```") or s.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        m = re.match(r"^#{1,6}\s+(.*)$", line)
        if m:
            targets[_gh_anchor(m.group(1))] = _gh_anchor(m.group(1))
        for a in re.findall(r'<a\s+id="([^"]+)"', line):
            targets[a.lower()] = a
    in_fence = False
    for i, line in enumerate(text.splitlines(), 1):
        s = line.strip()
        if s.startswith("```") or s.startswith("~~~"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        # 行内代码片段里的 `](#x)` 是在讲锚点语法本身，不是链接；先剔除再匹配。
        prose = re.sub(r"`[^`]*`", "", line)
        for a in re.findall(r"\]\(#([^)]+)\)", prose):
            target = targets.get(a.lower())
            if target is None:
                errors.append(
                    f"{path.relative_to(REPO)}:{i}: 内部锚点失效 `#({a})`"
                    f"（无对应标题或显式 <a id>；结构重排后请同步修正）"
                )
            elif a != target:
                errors.append(
                    f"{path.relative_to(REPO)}:{i}: 内部锚点大小写不一致 `#({a})`"
                    f"（目标为 `#{target}`；GitHub 的 id 一律小写）"
                )
    return errors


def main():
    errors = []
    whitelist, missing = collect_api_symbols()
    if missing:
        print(
            "警告：以下 API 白名单真源缺失，已退回静态名单：" + "、".join(missing),
            file=sys.stderr,
        )
    for f in DOC_FILES:
        if not f.exists():
            continue
        text = f.read_text(encoding="utf-8")
        errors += check_v4_numbering(text, f)
        errors += check_api_calls(text, f, whitelist)
        errors += check_markdown_links(text, f)
        errors += check_anchor_links(text, f)
    if errors:
        print("DDD 文档防漂移检查失败：")
        for e in errors:
            print(f"  - {e}")
        sys.exit(1)
    print(
        f"OK: {len(DOC_FILES)} 个 DDD 文档通过防漂移检查"
        f"（v4 编号 / 平台 API / 相对链接 / 内部锚点；白名单 {len(whitelist)} 个符号）"
    )

if __name__ == "__main__":
    main()
