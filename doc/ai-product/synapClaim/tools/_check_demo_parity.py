#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DEMO ↔ 实现对齐判据（P1–P10）。

为什么要有这道门禁：9 页原型是签约演示资产，03-POC演示脚本 是签约承诺。
两者漂移不会让任何现有门禁变红（M1–M9 只扫 .md 语义），现场演示才会穿帮。
本判据把「POC 三案例在位」「不承诺到账时间」「映射表覆盖 9 页 + 路由真源对账」
「退役 DEMO 不残留」变成可判红的不变量。

设计约束：纯 stdlib、零 DB、零网络；--self-test 用注入文本反证每条判据，
不碰真实文件（换机无 /tmp 残留依赖）。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = ROOT.parents[2]  # doc/ai-product/synapClaim → 仓库根
DELIVERY = ROOT / "07-delivery"
DEMO = DELIVERY / "prototypes" / "claim-workbench"
README = DELIVERY / "README.md"
# P10：README 承诺"9 页 ↔ 22 路由由本脚本机检"，路由真源在前端 routes.tsx。
ROUTES_TS = REPO / "bone-frontend" / "apps" / "bone-claim-app" / "src" / "routes.tsx"

PAGES = (
    "customer-journey.html", "claim-manage.html", "claim-detail.html",
    "claim-image-detail.html", "claim-rule-config.html", "job-config.html",
    "form-config.html", "import-record.html", "evaluation-report.html",
)

# P9：README 已声明删除的过时单文件大 Demo（独特能力已升级进 9 页原型）。
# 这两个文件若重新出现在 07-delivery/ 任意子目录，即"文档声明删除但文件仍在"漂移，
# 现场演示可能拿错旧稿。新增退役资产时在此登记文件名即可持续防回归。
RETIRED = (
    "synapClaim DEMO.html",
    "synapClaim DEMO（PC版v1）.html",
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


def check_route_mapping(readme: str, routes_ts: str) -> list[str]:
    """P10：README 承诺「9 页 ↔ 22 路由由本脚本机检」——补全此前只校验左列的缺口。

    旧 check_mapping 只提取第 1 列 HTML 文件名，从不读第 2 列「实现落点」路由，
    也不校验 routes.tsx 实际路由条数，于是 README 写着"22 路由"却无人对账：
    新增/删路由、或映射表把落点写成不存在的路径，机检依然全绿。
    """
    errs: list[str] = []
    if not routes_ts.strip():
        errs.append("P10 routes.tsx 读取失败/为空 → fail-closed")
        return errs
    # 只匹配 `{ path: '...' }` 形式的路由声明；刻意不匹配 interface 里的
    # `path: string;`（§9.2 曾把类型定义计入 grep 得 23 的误判）。
    actual = set(re.findall(r"\{ path: '([^']+)'", routes_ts))
    if not actual:
        errs.append("P10 routes.tsx 未解析到任何路由（格式变更？）→ fail-closed")
        return errs
    # README 声明的路由条数（如「22 路由」）
    declared = re.findall(r"(\d+)\s*路由", readme)
    if declared and int(declared[0]) != len(actual):
        errs.append(
            f"P10 路由条数漂移：README 声明 {declared[0]} 条，routes.tsx 实际 {len(actual)} 条"
        )
    # 映射表第 2 列「实现落点」引用的路由必须都存在于 routes.tsx
    m = re.search(r"### DEMO ↔ 实现对齐.*?(?=\n### |\Z)", readme, re.S)
    if m:
        mapped: set[str] = set()
        for line in m.group(0).splitlines():
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) >= 2 and re.fullmatch(r"`[a-z-]+\.html`", cells[0]):
                mapped.update(re.findall(r"`(/[a-z][a-z0-9-]*)`", cells[1]))
        stale = sorted(mapped - actual)
        if stale:
            errs.append(f"P10 映射表引用不存在的路由：{', '.join(stale)}")
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


def check_retired(readme: str, delivery_root: pathlib.Path) -> list[str]:
    """P9：README 声明已删的过时 DEMO 不得残留在 07-delivery/ 任意子目录。

    fail-closed：README 缺"已删除"声明、或交付目录不存在时判红——
    否则将来有人移除声明，本判据就会被静默旁路。
    """
    errs: list[str] = []
    # 只要退役清单非空，就要求 README 明确登记"已删除"，否则本判据可被移除声明旁路
    if RETIRED and "已删除" not in readme:
        errs.append("P9 README 缺少过时 DEMO「已删除」声明 → 判据可能被旁路")
    if not delivery_root.is_dir():
        errs.append(f"P9 交付目录不存在：{delivery_root} → fail-closed")
        return errs
    # rglob 覆盖 07-delivery/ 全目录（含 demo/ 等历史子目录），不再只扫 prototypes/
    present = {p.name: p for p in delivery_root.rglob("*.html")}
    for retired in RETIRED:
        if retired in present:
            errs.append(
                f"P9 过时 DEMO 未删除（README 声明已删但文件仍在）：{present[retired].relative_to(delivery_root)}"
            )
    return errs


def run() -> list[str]:
    errs: list[str] = []
    if not DEMO.is_dir():
        errs.append(f"P1 原型目录不存在：{DEMO}")
        files: set[str] = set()
    else:
        files = {p.name for p in DEMO.glob("*.html")}
        errs += check_pages(files)
    texts = {p: (DEMO / p).read_text(encoding="utf-8")
             for p in PAGES if (DEMO / p).is_file()}
    errs += check_cases(texts)
    errs += check_no_promise(texts)
    errs += check_ux_skeleton(texts, files)
    readme_text = README.read_text(encoding="utf-8") if README.is_file() else ""
    if not readme_text:
        errs.append("P2 README 不存在 → fail-closed")
    else:
        errs += check_mapping(readme_text, files)
        # P10：路由映射对账（README 承诺由本脚本机检）；routes.tsx 缺失时 fail-closed。
        routes_text = ROUTES_TS.read_text(encoding="utf-8") if ROUTES_TS.is_file() else ""
        errs += check_route_mapping(readme_text, routes_text)
    errs += check_retired(readme_text, DELIVERY)
    return errs


def self_test() -> int:
    """负向探针：每条判据喂入缺失样本必须变红，防止判据被改成恒绿。"""
    errs: list[str] = []
    probes = 0  # 动态计数：新增判据即新增探针，杜绝硬编码数字与真实探针数脱节的假绿

    good = {p: "OK" for p in PAGES}
    full_b = " ".join(CASE_B["claim-detail.html"])
    good["claim-detail.html"] = full_b + " 决策回放"
    good["customer-journey.html"] = " ".join(CASE_A["customer-journey.html"])
    good["claim-manage.html"] = " ".join(CASE_C["claim-manage.html"])
    good["evaluation-report.html"] = " ".join(CASE_EVAL["evaluation-report.html"])

    def must_red(label: str, actual: list[str]) -> None:
        nonlocal probes
        probes += 1
        if not actual:
            errs.append(f"负向探针失效（恒绿）：{label}")

    def must_green(label: str, actual: list[str]) -> None:
        nonlocal probes
        probes += 1
        if actual:
            errs.append(f"正向探针失效（过严）：{label}")

    must_red("P1 缺页", check_pages(set(PAGES) - {"form-config.html"}))
    must_red("P2 对齐表缺失", check_mapping("无对齐章节", set(PAGES)))
    bad_map = "### DEMO ↔ 实现对齐\n\n| 页 |\n| `claim-detail.html` |\n\n### 下一节\n14 态 claimStatus.ts"
    must_red("P2 映射表漏页", check_mapping(bad_map, set(PAGES)))
    # P10 负向/正向探针：注入最小映射表 + routes 文本，验证三类判红与 fail-closed
    p10_readme = (
        "原型与实现（22 路由）不是一一对应。\n"
        "### DEMO ↔ 实现对齐\n\n| DEMO 页 | 实现落点 | 对齐状态 |\n|---|---|---|\n"
        "| `claim-manage.html` | `/workbench` | 对齐 |\n"
        "| `claim-detail.html` | `/case` + `/review` | 对齐 |\n"
        "### 下一节\n"
    )
    p10_routes = "\n".join(
        [f"  {{ path: '/r{i}', name: 'n', element: <P /> }}," for i in range(19)]
        + ["  { path: '/workbench', name: 'n', element: <P /> },",
           "  { path: '/case', name: 'n', element: <P /> },",
           "  { path: '/review', name: 'n', element: <P /> },"]
    )  # 22 条：19 占位 + workbench/case/review（覆盖映射表第 2 列全部 token）
    must_green("P10 正向映射", check_route_mapping(p10_readme, p10_routes))
    # §9.2 教训钉子：interface 里的 `path: string;` 不得被误计为路由 → 仍应绿
    must_green("P10 类型定义不计入", check_route_mapping(
        p10_readme, p10_routes + "\nexport interface X { path: string; name: string; }"))
    must_red("P10 映射引用不存在路由", check_route_mapping(
        p10_readme.replace("`/workbench`", "`/no-such-route`"), p10_routes))
    must_red("P10 routes 缺失 fail-closed", check_route_mapping(p10_readme, ""))
    # 删掉一条路由（21 条），声明仍 22 → 必须判红
    must_red("P10 路由条数少于声明", check_route_mapping(
        p10_readme, "\n".join(p10_routes.splitlines()[:-1])))
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
    probes += 1
    if check_ux_skeleton(skeleton, set(PAGES) - {"form-config.html"}):
        errs.append("P7 缺页时应交由 P1 计，不应重复判红")
    # 正向：完整样本必须全绿
    must_green("正向 P3/P4/P6", check_cases(good) + check_no_promise(good))
    must_green("正向 P7 UX 骨架", check_ux_skeleton(skeleton, set(PAGES)))

    # P9 负向/正向探针：在系统临时目录构造假 07-delivery/ 树，测完自动清理，不碰真实文件
    with tempfile.TemporaryDirectory(prefix="synap-parity-p9-") as td:
        root = pathlib.Path(td)
        (root / "prototypes" / "claim-workbench").mkdir(parents=True)
        # 正向：README 有"已删除"声明且无退役文件 → 绿
        must_green("P9 干净交付目录", check_retired("过时 Demo 已删除", root))
        # 负向1：退役文件重现（深层子目录，验证 rglob 覆盖全目录）→ 必须红
        victim = root / "demo" / "legacy" / RETIRED[0]
        victim.parent.mkdir(parents=True)
        victim.write_text("x", encoding="utf-8")
        must_red("P9 退役 DEMO 重现", check_retired("过时 Demo 已删除", root))
        # 负向2：README 删掉"已删除"声明 → 必须红（fail-closed，防旁路）
        must_red("P9 删声明旁路", check_retired("", root))
        victim.unlink()

    for e in errs:
        print("【SELF-TEST FAIL】" + e)
    print(f"自检：{probes - len(errs)}/{probes} 探针通过")
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
    print("【OK】DEMO ↔ 实现对齐：9 页齐全 / 三案例与评测锚点在位 / 无到账承诺 / 映射表与路由真源一致 / 退役 DEMO 不残留")
    return 0


if __name__ == "__main__":
    sys.exit(main())
