#!/usr/bin/env python3
"""procuraAI 文档族 frontmatter 语义守护（本地补充判据，2026-11-12）。

背景：全族 md 加 YAML frontmatter 后，真实注入反证发现把某份文档的
``status: Active`` 改成 ``status: Deprecated``，主门禁仍 rc=0 —— frontmatter
当时只是人/AI 可读标注，没有机器语义守护。本脚本闭合该边界。

**重要边界（勿误读为 CI 已接入）**：本命令**不在** A10.17 守护的三入口判据集合
（ci.yml / scripts/check.sh / check-procura-ci.sh）内。A10.17 以 FULL_GATE_KEYS
严格比对判据集合，擅自把本命令加进任一入口会立刻转红；接入 CI 需同步改三入口
+ 门禁常量 + 自检探针，属 L3 架构评审。因此本判据是「本地 / AI 手动调用」的
补充守护，由 AGENTS.md 列为必跑项。

用法：
    python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py
    python3 doc/ai-product/procuraAI/scripts/check_procura_frontmatter.py --self-test

设计纪律：仅标准库、Python 3.9 兼容（不用 X | Y 联合注解）；空集合 fail-closed；
每条判据配注入必红反证 + 正例对照，防「一律 FAIL」假安全。
"""

from __future__ import annotations

import argparse
import datetime
import pathlib
import re
import sys

BASE = pathlib.Path(__file__).resolve().parents[1]

REQUIRED_KEYS = ("status", "layer", "truth-for", "updated-on")
STATUSES = ("Active", "Restricted", "Archived", "Deprecated")
LAYERS = (
    "entry", "changelog", "governance", "architecture", "contract",
    "decision", "delivery", "lld", "archive",
)
MIN_DOCS = 30  # 空集合 / 截断目录不得恒绿

# 与 tools/check_procura_alignment.py:220 同源判据（-v2 是真源，不属旧族）
LEGACY = tuple(sorted(p.name for p in BASE.glob("采购AI-*.md")
                      if "-v2" not in p.name))
MUST_ACTIVE = (
    "README.md", "AGENTS.md", "CHANGELOG.md",
    "文档资产登记与真源裁决.md", "开发基线与开发就绪清单.md",
    "采购AI-接口契约与数据字典-v2.md",
)
BANNER_MARK = "资产状态："
BANNER_FORBID = "不得作为现行设计或编码依据"
BANNER_LABEL = {"Archived": "历史归档", "Restricted": "受限引用"}

FM_RE = re.compile(r"\A---\n(.*?)\n---\n", re.S)
DATE_RE = re.compile(r"\A\d{4}-\d{2}-\d{2}\Z")


def parse_frontmatter(text):
    """返回 (字段字典, 错误信息)；无 frontmatter / 格式错一律失败。"""
    m = FM_RE.match(text)
    if not m:
        return {}, "缺 YAML frontmatter（或分隔线不是 ---）"
    fields = {}
    for line in m.group(1).splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if ":" not in line:
            return {}, "frontmatter 行不是 key: value：%r" % line[:60]
        k, v = line.split(":", 1)
        k, v = k.strip(), v.strip()
        if not k or not v:
            return {}, "frontmatter 键或值为空：%r" % line[:60]
        if k in fields:
            return {}, "frontmatter 键重复：%s" % k
        fields[k] = v
    return fields, ""


def load():
    """相对路径 -> 全文；排除隐藏目录（.workbuddy 等）。"""
    out = {}
    for p in sorted(BASE.rglob("*.md")):
        parts = p.relative_to(BASE).parts
        if any(part.startswith(".") for part in parts):
            continue
        out[p.relative_to(BASE).as_posix()] = p.read_text(encoding="utf-8")
    return out


def check(files):
    """纯函数判据，便于 --self-test 注入而不读真实磁盘。"""
    msgs = []
    if len(files) < MIN_DOCS:
        msgs.append("FAIL-C4 文档数 %d < 下界 %d（空集合 / 目录截断 fail-closed）"
                    % (len(files), MIN_DOCS))
    if len(LEGACY) < 8:
        msgs.append("FAIL-C4 旧族清单只有 %d 份（glob 判据失效 fail-closed）" % len(LEGACY))

    for rel, text in sorted(files.items()):
        name = rel.rsplit("/", 1)[-1]
        fields, err = parse_frontmatter(text)
        if err:
            msgs.append("FAIL-C1 %s：%s" % (rel, err))
            continue
        for k in REQUIRED_KEYS:
            if k not in fields:
                msgs.append("FAIL-C1 %s 缺必需键 %s" % (rel, k))
        status, layer = fields.get("status"), fields.get("layer")
        if status not in STATUSES:
            msgs.append("FAIL-C2 %s status 非法：%r（允许 %s）"
                        % (rel, status, "/".join(STATUSES)))
        if layer not in LAYERS:
            msgs.append("FAIL-C2 %s layer 非法：%r" % (rel, layer))
        date = fields.get("updated-on", "")
        if not DATE_RE.match(date):
            msgs.append("FAIL-C2 %s updated-on 非 YYYY-MM-DD：%r" % (rel, date))
        else:
            try:
                datetime.date.fromisoformat(date)
            except ValueError:
                msgs.append("FAIL-C2 %s updated-on 不是合法日期：%r" % (rel, date))

        if name in LEGACY:
            if status not in ("Archived", "Restricted"):
                msgs.append("FAIL-C3 %s 旧 POC 族 status 必须为 Archived/Restricted，实际 %r"
                            % (rel, status))
            body = text.split("---\n", 2)[-1]
            banner_lines = [ln for ln in body.splitlines() if BANNER_MARK in ln]
            if not banner_lines or BANNER_FORBID not in "\n".join(banner_lines):
                msgs.append("FAIL-C3 %s 旧 POC 族缺状态 banner（不得作为编码依据）" % rel)
            elif status in BANNER_LABEL and BANNER_LABEL[status] not in banner_lines[0]:
                msgs.append("FAIL-C3 %s banner 标签与 status %s 不一致" % (rel, status))
            if layer != "archive":
                msgs.append("FAIL-C3 %s 旧 POC 族 layer 必须为 archive，实际 %r" % (rel, layer))
        elif status in ("Archived", "Deprecated"):
            msgs.append("FAIL-C3 %s 非旧族文档不得标为 %s（真源被误判作废）"
                        % (rel, status))

        if rel in MUST_ACTIVE and status != "Active":
            msgs.append("FAIL-C3 %s 入口/契约/基线文档 status 必须为 Active，实际 %r"
                        % (rel, status))

    return (not msgs), msgs


def self_test():
    """正例对照防「一律 FAIL」假安全 + 六组注入必红反证。"""
    real = load()
    ok, msgs = check(real)
    cases = [("正例：当前磁盘全绿", ok, msgs)]

    def expect_red(label, mutate, key):
        files = dict(real)
        mutate(files)
        green, errs = check(files)
        hit = any(key in e for e in errs)
        cases.append((label, (not green) and hit, errs))

    expect_red("真源 status 改成 Deprecated",
               lambda f: f.__setitem__("架构师答辩包.md",
                                       f["架构师答辩包.md"].replace("status: Active",
                                                                  "status: Deprecated", 1)),
               "非旧族文档不得标为")
    expect_red("旧族 status 改成 Active",
               lambda f: f.__setitem__(LEGACY[0],
                                       re.sub(r"status: \w+", "status: Active",
                                              f[LEGACY[0]], count=1)),
               "旧 POC 族 status 必须")
    expect_red("旧族删除 banner",
               lambda f: f.__setitem__(LEGACY[0],
                                       re.sub(r"> \*\*资产状态：.*?\n\n", "",
                                              f[LEGACY[0]], count=1, flags=re.S)),
               "缺状态 banner")
    expect_red("删除某文档 frontmatter",
               lambda f: f.__setitem__("README.md",
                                       FM_RE.sub("", f["README.md"], count=1)),
               "缺 YAML frontmatter")
    expect_red("未知 layer",
               lambda f: f.__setitem__("README.md",
                                       f["README.md"].replace("layer: entry",
                                                              "layer: mystery", 1)),
               "layer 非法")
    expect_red("清空文档集合（空集合恒绿防护）",
               lambda f: f.clear(),
               "空集合")

    for label, passed, errs in cases:
        print("[%s] %s" % ("PASS" if passed else "FAIL", label))
        if not passed:
            print("       " + " / ".join(errs[:3]))
    good = sum(1 for _, p, _ in cases if p)
    print("[SELF-TEST] frontmatter 守护：%d/%d 符合预期" % (good, len(cases)))
    return 0 if good == len(cases) else 1


def main():
    ap = argparse.ArgumentParser(description="procuraAI 文档族 frontmatter 语义守护")
    ap.add_argument("--self-test", action="store_true", help="注入式负向反证 + 正例对照")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    files = load()
    ok, msgs = check(files)
    if ok:
        print("[doc-frontmatter] 全族 frontmatter 语义守护通过（%d 份文档）" % len(files))
        return 0
    for m in msgs:
        print("[doc-frontmatter] " + m)
    print("[doc-frontmatter] 守护未通过：%d 条" % len(msgs))
    return 1


if __name__ == "__main__":
    sys.exit(main())
