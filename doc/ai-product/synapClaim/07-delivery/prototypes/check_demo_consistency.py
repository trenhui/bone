#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""智能理赔交付原型一致性机检（只读 / fail-closed / 仅标准库）。

用法：
    python3 check_demo_consistency.py                # 校验真实原型目录
    python3 check_demo_consistency.py --self-test    # 临时目录构造正反例自证判据有效

任何断言失败即以非零码退出；不会修改真实原型文件。
"""
import re
import sys
import tempfile
from html.parser import HTMLParser
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_DIR = HERE / "claim-workbench"

PAGES = [
    "claim-manage.html",
    "claim-detail.html",
    "claim-image-detail.html",
    "claim-rule-config.html",
    "job-config.html",
    "import-record.html",
    "customer-journey.html",
    "form-config.html",
    "evaluation-report.html",
    "settlement.html",
    "ai-governance.html",
    "mapping.html",
    "role-views.html",
]
KEYS = [p[:-5] for p in PAGES]  # 文件名去后缀作为 key
HONESTY_PAGES = ["evaluation-report.html", "ai-governance.html"]

BAD_AMOUNTS = ["12,860.50", "6,420.25"]
GOOD_AMOUNT = "1,280.00"
OLD_IMAGE_PATTERNS = [
    r"影像数量\s*—",
    r"上传次数\s*0",
    r"清点张数</span><span class=\"v\">—",
]
# 只拦截「已达成实测值」语义；目标线（如 ≥95%）允许存在
ACHIEVED_PATTERNS = [
    r"已达\s*\d+(?:\.\d+)?\s*%",
    r"达成\s*\d+(?:\.\d+)?\s*%",
    r"实测\s*\d+(?:\.\d+)?\s*%",
    r"当前\s*\d+(?:\.\d+)?\s*%",
]
ITEM_RE = re.compile(r"var\s+items\s*=\s*(\[.*?\]);", re.S)
KEY_RE = re.compile(r"'([a-z0-9-]+)\.html'")


class CellParser(HTMLParser):
    """抽取所有 td 文本，用于判断「现场生成」列是否被预置实测值。"""

    def __init__(self):
        super().__init__()
        self.cells = []
        self.cur = None

    def handle_starttag(self, tag, attrs):
        if tag == "td":
            self.cur = []

    def handle_data(self, data):
        if self.cur is not None:
            self.cur.append(data)

    def handle_endtag(self, tag):
        if tag == "td" and self.cur is not None:
            self.cells.append("".join(self.cur).strip())
            self.cur = None


def _read(path):
    try:
        return path.read_text(encoding="utf-8")
    except Exception as exc:  # fail-closed：读不到就算失败
        return None


def run_checks(base):
    """返回 [(检查名, 是否通过, 说明), ...]，不抛异常。"""
    base = Path(base)
    results = []

    # 1) 13 个 HTML 全部存在
    missing = [p for p in PAGES if not (base / p).is_file()]
    results.append(("文件齐备", not missing,
                    "缺失：" + ", ".join(missing) if missing else "13 个 HTML 均存在"))

    texts = {}
    for p in PAGES:
        t = _read(base / p)
        if t is not None:
            texts[p] = t

    # 2) 每页命令面板 items 数组包含全部 13 个页面标识
    bad_items = []
    for p in PAGES:
        t = texts.get(p)
        if t is None:
            bad_items.append(p + "(无法读取)")
            continue
        m = ITEM_RE.search(t)
        if not m:
            bad_items.append(p + "(无 items 数组)")
            continue
        found = set(KEY_RE.findall(m.group(1)))
        absent = [k for k in KEYS if k not in found]
        if absent:
            bad_items.append(p + "缺少 " + ", ".join(absent))
    results.append(("命令面板 13 页", not bad_items,
                    "；".join(bad_items) if bad_items else "13 页 items 均含全部 13 个 key"))

    # 3) 跨页金额一致性
    all_html = sorted(base.glob("*.html"))
    all_text = [(p.name, _read(p) or "") for p in all_html]
    bad_hits = []
    for name, t in all_text:
        for amt in BAD_AMOUNTS:
            if amt in t:
                bad_hits.append(name + " 含 " + amt)
    good_pages = [n for n, t in all_text if GOOD_AMOUNT in t]
    amount_ok = not bad_hits and len(good_pages) >= 2
    results.append(("金额一致性", amount_ok,
                    ("；".join(bad_hits) if bad_hits else
                     "错误金额零命中；%d 页含 %s" % (len(good_pages), GOOD_AMOUNT))))

    # 4) 影像张数一致性
    img_pages = [n for n, t in all_text if "12 张" in t or "共 12 张" in t]
    old_hits = []
    for name, t in all_text:
        for pat in OLD_IMAGE_PATTERNS:
            if re.search(pat, t):
                old_hits.append(name + " 命中旧表述 " + pat)
    img_ok = len(img_pages) >= 2 and not old_hits
    results.append(("影像一致性", img_ok,
                    ("；".join(old_hits) if old_hits else
                     "%d 页含「12 张/共 12 张」；旧表述零命中" % len(img_pages))))

    # 5) 评测诚实性
    honesty_bad = []
    for p in HONESTY_PAGES:
        t = texts.get(p)
        if t is None:
            honesty_bad.append(p + "(无法读取)")
            continue
        if "现场生成" not in t:
            honesty_bad.append(p + " 缺「现场生成」")
        parser = CellParser()
        try:
            parser.feed(t)
        except Exception as exc:
            honesty_bad.append(p + " 表格解析失败")
            continue
        scene_cells = [c for c in parser.cells if "待现场生成" in c]
        if len(scene_cells) < 3:
            honesty_bad.append(p + "「待现场生成」单元格不足 3 个")
        for c in scene_cells:
            if re.search(r"\d|%", c):
                honesty_bad.append(p + " 现场生成列被预置值：" + c[:30])
        for pat in ACHIEVED_PATTERNS:
            m = re.search(pat, t)
            if m:
                honesty_bad.append(p + " 含写死达成值：" + m.group(0))
    results.append(("评测诚实性", not honesty_bad,
                    "；".join(honesty_bad) if honesty_bad else
                    "两页均含「现场生成」，现场列留空，无写死达成百分比"))

    return results


def _good_fixture(root):
    """在临时目录造正例：满足全部判据的最小 HTML 集合。"""
    root = Path(root)
    root.mkdir(parents=True, exist_ok=True)
    items = "[" + ",".join(
        "['%s.html','·','%s','页']" % (k, k) for k in KEYS
    ) + "]"
    for p in PAGES:
        body = ("<!doctype html><html><body><script>var items=%s;</script>"
                "发票 ¥%s · 共 12 张影像" % (items, GOOD_AMOUNT))
        if p in HONESTY_PAGES:
            body += ("<table><tr><td>目标线 ≥95%%</td><td>待现场生成</td></tr>"
                     "<tr><td>目标线 ≤0.20</td><td>待现场生成</td></tr>"
                     "<tr><td>目标线 ≤5pp</td><td>待现场生成</td></tr></table>")
        (root / p).write_text(body + "</body></html>", encoding="utf-8")


def _self_test():
    print("=== 自测：临时目录正反例（不触碰真实文件） ===")
    with tempfile.TemporaryDirectory(prefix="demo_consistency_") as tmp:
        tmp = Path(tmp)
        good = tmp / "good"
        _good_fixture(good)
        r = run_checks(good)
        if not all(ok for _, ok, _ in r):
            for name, ok, msg in r:
                print("  ✗ 正例未通过 %s：%s" % (name, msg))
            print("自测失败：正例应全绿")
            return 1
        print("  ✓ 正例：全部判据通过（说明判据可满足）")

        def expect_fail(label, mutate, want):
            d = tmp / label
            _good_fixture(d)
            mutate(d)
            got = dict((n, ok) for n, ok, _ in run_checks(d))
            ok = not got.get(want, True)
            print("  %s 反例[%s] 触发「%s」失败" % ("✓" if ok else "✗", label, want))
            return ok

        checks = []
        checks.append(expect_fail("缺文件", lambda d: (d / PAGES[0]).unlink(), "文件齐备"))
        def drop_key(d):
            p = d / PAGES[0]
            p.write_text(p.read_text(encoding="utf-8").replace("'%s.html'" % KEYS[-1], "'gone.html'"), encoding="utf-8")
        checks.append(expect_fail("items缺项", drop_key, "命令面板 13 页"))
        def bad_amount(d):
            for p in d.glob("*.html"):
                p.write_text(p.read_text(encoding="utf-8").replace(GOOD_AMOUNT, "9,999.00").replace("共 12 张", "共 12,860.50 张"), encoding="utf-8")
        checks.append(expect_fail("错误金额", bad_amount, "金额一致性"))
        def old_image(d):
            for p in d.glob("*.html"):
                p.write_text(p.read_text(encoding="utf-8").replace("共 12 张", "影像数量— · 上传次数 0"), encoding="utf-8")
        checks.append(expect_fail("旧影像表述", old_image, "影像一致性"))
        def fake_result(d):
            p = d / "ai-governance.html"
            p.write_text(p.read_text(encoding="utf-8").replace("待现场生成", "已达 96%", 1), encoding="utf-8")
        checks.append(expect_fail("预置实测值", fake_result, "评测诚实性"))

        if not all(checks):
            print("自测失败：存在未触发的反例（判据可能恒真）")
            return 1
    print("自测通过：5 个反例均被拦截，判据非恒真")
    return 0


def main():
    args = sys.argv[1:]
    if "--self-test" in args:
        return _self_test()
    base = DEFAULT_DIR
    if "--dir" in args:
        i = args.index("--dir")
        if i + 1 >= len(args):
            print("错误：--dir 缺少参数")
            return 2
        base = Path(args[i + 1])
    print("=== 智能理赔交付原型一致性机检 ===")
    print("检查目录：" + str(base))
    results = run_checks(base)
    for name, ok, msg in results:
        print("  [%s] %s：%s" % ("PASS" if ok else "FAIL", name, msg))
    if all(ok for _, ok, _ in results):
        print("结论：全部 %d 项断言通过 ✓" % len(results))
        return 0
    print("结论：存在失败断言，机检不通过 ✗")
    return 1


if __name__ == "__main__":
    sys.exit(main())
