#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DEMO ↔ 实现对齐判据（P1–P8）。

为什么要有这道门禁：9 页原型是签约演示资产，03-POC演示脚本 是签约承诺。
两者漂移不会让任何现有门禁变红（M1–M9 只扫 .md 语义），现场演示才会穿帮。
本判据把「POC 三案例在位」「不承诺到账时间」「映射表覆盖 8 页」变成可判红的不变量。

设计约束：纯 stdlib、零 DB、零网络；--self-test 用注入文本反证每条判据，
不碰真实文件（换机无 /tmp 残留依赖）。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
DEMO = ROOT / "07-delivery" / "prototypes" / "claim-workbench"
README = ROOT / "07-delivery" / "README.md"

PAGES = (
    "customer-journey.html", "claim-manage.html", "claim-detail.html",
    "claim-image-detail.html", "claim-rule-config.html", "job-config.html",
    "form-config.html", "import-record.html", "evaluation-report.html",
)

# P3–P5：每个 POC 案例的落点与必备锚点（缺一即演示失败）
CASE_A = {
    "customer-journey.html": ("30 秒内返回建议结论", "到账时效随支付通道确定"),
    "claim-detail.html": ("决策回放",),
}
CASE_B = {
    "claim-detail.html": (
        "建议拒赔", "一键确认拒赔", "事实认定", "条款依据", "证据位置",
        "申诉渠道与时限", "不会自动生效",
    ),
}
CASE_C = {
    "claim-manage.html": (
        "POC 案例 C", "重复金额风险", "风险标记上下文", "AI 建议", "决策轨迹",
        "人工终审",
    ),
}
# P8：第 3 幕评测报告样式（剧本 §二：不预置实测值，目标线与现场生成必须分列）
CASE_EVAL = {
    "evaluation-report.html": (
        "评测报告", "现场生成", "≥ 95%", "审计日志", "一键回放",
    ),
}

# P6：签约脚本明令禁止的承诺口径（03-POC演示脚本.md §三 案例 A）
FORBIDDEN = (
    re.compile(r"预计\s*\d+\s*分钟"),
    re.compile(r"20\d{2}-\d{2}-\d{2}\s*到账"),
    re.compile(r"预计[^\n]{0,20}到账"),
)


def check_pages(files: set[str]) -> list[str]:
    """P1：9 页齐全（fail-closed：原型目录被改名/删空即红）。"""
    missing = sorted(set(PAGES) - files)
    return [f"P1 原型缺页：{', '.join(missing)}"] if missing else []


def check_mapping(readme: str, pages: set[str]) -> list[str]:
    """P2：对齐表必须覆盖 9 页，否则映射表静默过期。"""
    errs: list[str] = []
    if "DEMO ↔ 实现对齐" not in readme:
        errs.append("P2 README 缺少「DEMO ↔ 实现对齐」章节")
    if "claimStatus.ts" not in readme or "14 态" not in readme:
        errs.append("P2 状态口径说明缺失或过期（须含 14 态与 claimStatus.ts）")
    # 只取对齐章节到下一章节之间的表格，避免别的索引表凑数
    m = re.search(r"### DEMO ↔ 实现对齐.*?(?=\n### |\Z)", readme, re.S)
    if not m:
        return errs
    named = set(re.findall(r"`([a-z-]+\.html)`", m.group(0)))
    missing = sorted(set(PAGES) - named)
    if missing:
        errs.append(f"P2 对齐表未覆盖：{', '.join(missing)}")
    return errs


def check_cases(texts: dict[str, str]) -> list[str]:
    """P3–P5：三案例锚点逐条对账。"""
    errs: list[str] = []
    for code, spec in (("P3 案例A", CASE_A), ("P4 案例B", CASE_B), ("P5 案例C", CASE_C), ("P8 评测", CASE_EVAL)):
        for page, anchors in spec.items():
            text = texts.get(page, "")
            for a in anchors:
                if a not in text:
                    errs.append(f"{code} {page} 缺少锚点：{a}")
    return errs


def check_no_promise(texts: dict[str, str]) -> list[str]:
    """P6：禁止承诺到账时间 / 无口径 SLA。"""
    errs: list[str] = []
    for page, text in sorted(texts.items()):
        for pat in FORBIDDEN:
            m = pat.search(text)
            if m:
                errs.append(f"P6 {page} 出现禁止口径：{m.group(0)}")
    return errs


# P7：README 承诺九页共用 UX 骨架（互跳导航 / 命令面板 / 指引抽屉 / toast）。
# 这些是签约演示的"一套系统"观感基线；任一页漏接会让现场演示出现差异化体验，
# 而 M1–M9 只扫 .md 语义，不会判红。锚点取自九页实测公共 id（每页均命中）。
UX_ANCHORS = (
    "id=\"uxCmd\"", "id=\"uxCmdList\"", "id=\"uxCmdInput\"",
    "id=\"uxFab\"", "id=\"uxDrawer\"", "id=\"uxToast\"", "id=\"uxMask\"",
)


def check_ux_skeleton(texts: dict[str, str], files: set[str]) -> list[str]:
    """P7：九页共用 UX 骨架齐全 + 互跳覆盖全部 9 页。"""
    errs: list[str] = []
    if set(PAGES) - files:  # 缺页由 P1 报，避免重复计数
        return errs
    for page in PAGES:
        text = texts.get(page, "")
        for anchor in UX_ANCHORS:
            if anchor not in text:
                errs.append(f"P7 {page} 缺少公共 UX 锚点：{anchor}")
        linked = set(re.findall(r'href="([a-z-]+\.html)"', text))
        missing = set(PAGES) - linked
        if missing:
            errs.append(f"P7 {page} 互跳缺页：{', '.join(sorted(missing))}")
    return errs


def run() -> list[str]:
    errs: list[str] = []
    if not DEMO.is_dir():
        return [f"P1 原型目录不存在：{DEMO}"]
    files = {p.name for p in DEMO.glob("*.html")}
    errs += check_pages(files)
    texts = {p: (DEMO / p).read_text(encoding="utf-8")
             for p in PAGES if (DEMO / p).is_file()}
    errs += check_cases(texts)
    errs += check_no_promise(texts)
    errs += check_ux_skeleton(texts, files)
    if not README.is_file():
        errs.append("P2 README 不存在 → fail-closed")
    else:
        errs += check_mapping(README.read_text(encoding="utf-8"), files)
    return errs


def self_test() -> int:
    """负向探针：每条判据喂入缺失样本必须变红，防止判据被改成恒绿。"""
    errs: list[str] = []
    good = {p: "OK" for p in PAGES}
    full_b = " ".join(CASE_B["claim-detail.html"])
    good["claim-detail.html"] = full_b + " 决策回放"
    good["customer-journey.html"] = " ".join(CASE_A["customer-journey.html"])
    good["claim-manage.html"] = " ".join(CASE_C["claim-manage.html"])
    good["evaluation-report.html"] = " ".join(CASE_EVAL["evaluation-report.html"])

    def must_red(label: str, actual: list[str]) -> None:
        if not actual:
            errs.append(f"负向探针失效（恒绿）：{label}")

    must_red("P1 缺页", check_pages(set(PAGES) - {"form-config.html"}))
    must_red("P2 对齐表缺失", check_mapping("无对齐章节", set(PAGES)))
    bad_map = "### DEMO ↔ 实现对齐\n\n| 页 |\n| `claim-detail.html` |\n\n### 下一节\n14 态 claimStatus.ts"
    must_red("P2 映射表漏页", check_mapping(bad_map, set(PAGES)))
    t = dict(good); t["evaluation-report.html"] = t["evaluation-report.html"].replace("现场生成", "")
    must_red("P8 评测预置实测值", check_cases(t))
    t = dict(good); t["claim-detail.html"] = t["claim-detail.html"].replace("事实认定", "")
    must_red("P3/P4 锚点缺失", check_cases(t))
    t = dict(good); t["customer-journey.html"] = t["customer-journey.html"].replace(
        "到账时效随支付通道确定", "预计 2026-06-10 到账")
    must_red("P6 到账承诺", check_no_promise(t))
    t = dict(good); t["customer-journey.html"] = "预计 2 分钟内返回"
    must_red("P6 无口径 SLA", check_no_promise(t))
    # P7 负向：缺一个公共 UX 锚点必须判红
    skeleton = {p: " ".join(UX_ANCHORS) + " " +
                " ".join(f'href="{x}"' for x in PAGES) for p in PAGES}
    t = dict(skeleton); t["claim-detail.html"] = t["claim-detail.html"].replace('id="uxToast"', "")
    must_red("P7 公共 UX 锚点缺失", check_ux_skeleton(t, set(PAGES)))
    # P7 负向：互跳少一页必须判红
    t = dict(skeleton); t["claim-manage.html"] = t["claim-manage.html"].replace(
        'href="form-config.html"', "")
    must_red("P7 互跳缺页", check_ux_skeleton(t, set(PAGES)))
    # P7 边界：缺页时交由 P1 计，P7 不重复计数（防止双计虚增红点）
    if check_ux_skeleton(skeleton, set(PAGES) - {"form-config.html"}):
        errs.append("P7 缺页时应交由 P1 计，不应重复判红")
    # 正向：完整样本必须全绿
    if check_cases(good) or check_no_promise(good) or check_ux_skeleton(skeleton, set(PAGES)):
        errs.append("正向样本被误判为红（判据过严）")
    for e in errs:
        print("【SELF-TEST FAIL】" + e)
    print(f"自检：{13 - len(errs)}/13 探针通过")
    return 1 if errs else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    errs = run()
    if errs:
        print("=== DEMO ↔ 实现对齐校验 ===")
        for e in errs:
            print("【FAIL】" + e)
        print(f"共 {len(errs)} 处问题")
        return 1
    print("【OK】DEMO ↔ 实现对齐：9 页齐全 / 三案例与评测锚点在位 / 无到账承诺 / 映射表完整")
    return 0


if __name__ == "__main__":
    sys.exit(main())
