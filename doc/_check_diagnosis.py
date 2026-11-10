#!/usr/bin/env python3
"""诊断文档生命周期机检（ratchet：规则只增不减）。

扫描 doc/ 下的"诊断 / 复核 / review"类文档，校验：
  1. 头部 frontmatter 含强制字段；
  2. 含发现项闭环表；
  3. currency:current 且 closed_at 空且 mtime>90 天 → 陈旧；
  4. README/索引中指向已删诊断文档的悬空链接；
  5. 仓库根目录游离过程性报告（联调/核验/验证/复核）告警。

用法：
  python3 doc/_check_diagnosis.py            # 检查默认 doc 根
  python3 doc/_check_diagnosis.py --root X   # 指定根
  python3 doc/_check_diagnosis.py --self-test
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import time
from dataclasses import dataclass, field
from typing import Optional

# 核心身份字段（缺失即判红）；其余为推荐字段（缺失仅警告，ratchet 渐进收紧）
REQUIRED_FIELDS = ["doc_id", "role", "currency"]
RECOMMENDED_FIELDS = ["superseded_by", "closed_at", "related_changes"]

# 已具备自身治理规约的区域，不纳入本机检（避免误判既有规约文件）
EXCLUDE_DIRS = {"09-records", "10-archive", "archive", "modules", "node_modules", ".git"}
# 非诊断类但文件名命中的文档（治理规范本身、工作流方案），排除
EXCLUDE_NAME_RE = re.compile(r"工作流方案|生命周期规范")

# 文件名命中即视为诊断文档
DIAGNOSIS_NAME_RE = re.compile(
    r"(诊断|复核|review|诊断与优化|诊断报告)", re.IGNORECASE
)
DIAGNOSIS_ROLE_RE = re.compile(r"^\s*role\s*:\s*(review|evidence)\s*$", re.MULTILINE)

# 仓库根目录游离报告识别（比 doc/ 内更宽：覆盖 联调/核验/验证 等命名）
ROOT_REPORT_RE = re.compile(r"(联调|核验|验证|复核|诊断|report)", re.IGNORECASE)

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)
CLOSURE_TABLE_RE = re.compile(
    r"^\s*\|.*\b(状态|闭环)\b.*\|", re.IGNORECASE | re.MULTILINE
)

STALE_DAYS = 90


@dataclass
class Finding:
    path: str
    level: str  # error | warning
    msg: str


@dataclass
class Context:
    root: str
    findings: list = field(default_factory=list)

    def add(self, path: str, level: str, msg: str) -> None:
        self.findings.append(Finding(path, level, msg))


def parse_frontmatter(text: str) -> Optional[dict]:
    m = FRONTMATTER_RE.match(text)
    if not m:
        return None
    data: dict = {}
    for line in m.group(1).splitlines():
        if ":" not in line:
            continue
        k, _, v = line.partition(":")
        data[k.strip()] = v.strip()
    return data


def is_diagnosis_doc(path: str, text: str) -> bool:
    name = os.path.basename(path)
    if EXCLUDE_NAME_RE.search(name):
        return False
    if DIAGNOSIS_NAME_RE.search(name):
        return True
    if DIAGNOSIS_ROLE_RE.search(text or ""):
        return True
    return False


def iter_md_files(root: str):
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        for fn in files:
            if fn.endswith(".md"):
                yield os.path.join(dirpath, fn)


def check_doc(ctx: Context, path: str) -> None:
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()

    fm = parse_frontmatter(text)
    if fm is None:
        ctx.add(path, "error", "缺少 frontmatter（诊断文档必须带生命周期字段）")
        return

    missing_req = [k for k in REQUIRED_FIELDS if k not in fm]
    if missing_req:
        ctx.add(path, "error", f"frontmatter 缺核心字段：{', '.join(missing_req)}")
    missing_rec = [k for k in RECOMMENDED_FIELDS if k not in fm]
    if missing_rec:
        ctx.add(path, "warning", f"frontmatter 缺推荐字段：{', '.join(missing_rec)}")

    role = (fm.get("role") or "").lower()
    currency = (fm.get("currency") or "").lower()
    closed_at = (fm.get("closed_at") or "").strip()

    if role == "review" and currency == "current" and not CLOSURE_TABLE_RE.search(text):
        ctx.add(
            path,
            "warning",
            "role:review 且 currency:current 但未发现项闭环表（建议含 状态/闭环 列的表格）",
        )

    if currency == "current" and not closed_at:
        mtime = os.path.getmtime(path)
        age_days = (time.time() - mtime) / 86400.0
        if age_days > STALE_DAYS:
            ctx.add(
                path,
                "warning",
                f"currency:current 但 closed_at 为空且已陈旧 {age_days:.0f} 天（> {STALE_DAYS}），须闭环或删除",
            )


def check_dangling_links(ctx: Context, root: str) -> None:
    """README/索引中指向已删/不存在的诊断文档的链接 → 悬空。"""
    link_re = re.compile(r"\]\(([^)]+)\)")
    for md in iter_md_files(root):
        with open(md, "r", encoding="utf-8") as f:
            text = f.read()
        base = os.path.dirname(md)
        for m in link_re.finditer(text):
            target = m.group(1).split("#")[0].strip()
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            resolved = os.path.normpath(os.path.join(base, target))
            if not os.path.exists(resolved):
                name = os.path.basename(resolved)
                if DIAGNOSIS_NAME_RE.search(name):
                    ctx.add(
                        md,
                        "error",
                        f"悬空链接指向已删/不存在的诊断文档：{target}",
                    )


def check_repo_root(ctx: Context, repo_root: str) -> None:
    """仓库根目录下的游离过程性报告（联调/核验/验证/复核）不应入库根目录。"""
    if repo_root == ctx.root:
        return
    for fn in sorted(os.listdir(repo_root)):
        full = os.path.join(repo_root, fn)
        if not fn.endswith(".md") or not os.path.isfile(full):
            continue
        try:
            with open(full, "r", encoding="utf-8") as f:
                text = f.read()
        except (OSError, UnicodeDecodeError):
            continue
        if ROOT_REPORT_RE.search(fn) or DIAGNOSIS_ROLE_RE.search(text):
            ctx.add(
                full,
                "warning",
                "仓库根目录存在游离过程性报告，回写真源后应删除或移入 doc/archive/（不应堆积在根目录）",
            )


def run_checks(root: str) -> Context:
    ctx = Context(root=root)
    for path in iter_md_files(root):
        with open(path, "r", encoding="utf-8") as f:
            text = f.read()
        if is_diagnosis_doc(path, text):
            check_doc(ctx, path)
    check_dangling_links(ctx, root)
    repo_root = os.path.dirname(os.path.abspath(root))
    if os.path.isdir(repo_root):
        check_repo_root(ctx, repo_root)
    return ctx


def self_test() -> int:
    """内置规则自检：用合成数据验证判定逻辑。"""
    tmp = "_check_diagnosis_selftest.tmp"
    cases = [
        # (内容, 期望 level 集合)
        (
            "---\ndoc_id: x\nrole: review\ncurrency: current\nsuperseded_by: ''\nclosed_at: ''\nrelated_changes: []\n---\n| id | 状态 |\n|---|---|\n",
            set(),  # 完整，应通过
        ),
        (
            "# 无 frontmatter\n| id | 状态 |\n",
            {"error"},  # 缺 frontmatter
        ),
        (
            "---\ndoc_id: x\nrole: review\ncurrency: current\n---\n| id | 状态 |\n",
            {"warning"},  # 缺推荐字段（核心字段齐全）
        ),
        (
            "---\ndoc_id: x\nrole: review\ncurrency: current\nsuperseded_by: ''\nclosed_at: ''\nrelated_changes: []\n---\n正文无表格\n",
            {"warning"},  # 活跃 review 缺闭环表（ratchet 警告）
        ),
        (
            "---\ndoc_id: x\n---\n| id | 状态 |\n",
            {"error"},  # 缺核心字段 role/currency
        ),
    ]
    failures = 0
    for i, (content, expected_levels) in enumerate(cases):
        ctx = Context(root="<selftest>")
        # 直接调用 check_doc 需要路径；用合成路径
        path = f"{tmp}_{i}.md"
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        try:
            check_doc(ctx, path)
            got = {f.level for f in ctx.findings}
            if not expected_levels.issubset(got):
                print(f"[self-test] case {i} 失败：期望包含 {expected_levels}，得到 {got}")
                failures += 1
        finally:
            if os.path.exists(path):
                os.remove(path)
    if failures == 0:
        print("[self-test] OK：规则判定符合预期")
        return 0
    print(f"[self-test] 失败 {failures} 例")
    return 1


def main() -> int:
    ap = argparse.ArgumentParser(description="诊断文档生命周期机检")
    ap.add_argument("--root", default=os.path.dirname(os.path.abspath(__file__)),
                    help="doc 根目录（默认本脚本所在目录）")
    ap.add_argument("--self-test", action="store_true", help="运行内置规则自检")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    root = os.path.abspath(args.root)
    if not os.path.isdir(root):
        print(f"根目录不存在：{root}")
        return 2

    ctx = run_checks(root)
    errors = [f for f in ctx.findings if f.level == "error"]
    warnings = [f for f in ctx.findings if f.level == "warning"]

    for f in ctx.findings:
        marker = "ERROR" if f.level == "error" else "WARN"
        rel = os.path.relpath(f.path, root)
        print(f"[{marker}] {rel}: {f.msg}")

    print(f"\n诊断文档机检完成：{len(errors)} 错误，{len(warnings)} 警告")
    if errors:
        print("存在错误，ratchet 不通过。")
        return 1
    print("通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
