#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""E2E 共享层离线自证（selftest）—— **不需要起任何服务** 就能证明「测试基础设施本身是好的」。

**为什么需要**：`lint.sh` 只查语法（`py_compile` / `node --check`），语法通过
不等于行为正确。历史上「配了不生效」是头号正确性债务：`fixtures` 的分位守恒、
`chain_runner` 的路径模板替换、JUnit 出口的转义，任何一处写错都不会被语法检查发现，
只有在真跑全链路时才暴露——而真跑要起 5 个后端 + 前端，成本极高、且容易把
「基础设施坏了」误判成「业务坏了」。

本脚本用 **fake http + 子进程** 验证共享层契约，2 秒内给出结论：
  1. `config`  —— 变量齐备、`run_suffix` 格式合法（cleanup 靠它定点回收）
  2. `report`  —— 断言计数与退出码正确（子进程验，避免自测污染自身计数）
  3. `fixtures`—— 同 seed 同序列、金额非圆整、`split` 分位守恒、`break_` 恒差 1.01
  4. `chain_runner` —— 路径模板/extract/status·eq·page 判据/stop_if_empty/未知 kind 报错
  5. JUnit 出口 —— Python 与 Node 两侧都能写出**可解析**的 XML，且特殊字符正确转义
  6. 纪律棘轮 —— 内联浏览器登录的探针数不得增加（只可收缩）

用法：
    python3 scripts/e2e/selftest.py
    python3 scripts/e2e/selftest.cjs   # Node 侧（run.sh selftest 会两个都跑）

退出码：0 全绿；1 有断言失败（CI 直接读）。
"""
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from e2e_lib import chain_runner, config, fixtures  # noqa: E402
from e2e_lib.report import check, section, summary  # noqa: E402

# 内联浏览器登录的**棘轮基线**：只可收缩、不可增长。
# 现状：5 个探针仍在各自流程内联登录（迁移需逐个人工核对流程，盲改=假绿风险）：
#   ui/probe-metadata-loop.mjs、ui/ui-all-menu-regression.mjs、
#   ui/ui-full-module-crud-probe.mjs、ui/ui-runtime-evidences-regression.mjs、
#   lowcode-closed-loop.cjs
# 每迁移一个改用 e2e_lib/ui.cjs 的 uiLogin，就把这个数字下调 —— 与
# doc-code-symbols-baseline.json「只可收缩」同一条纪律。
INLINE_LOGIN_BASELINE = 5

_PWD_SELECTOR = re.compile(r'input\[type="password"\]|placeholder\*="密码"')
_USES_SHARED_UI = re.compile(r"e2e_lib/ui\.cjs")


def _node_bin():
    """定位 node：优先 PATH，其次本机 workbuddy 二进制（与 run.sh 同口径）。"""
    from shutil import which

    found = which("node")
    if found:
        return found
    hits = sorted(glob.glob("/Users/renhui.trh/.workbuddy/binaries/node/versions/*/bin/node"))
    return hits[0] if hits else None


def _parse_xml(path):
    """解析 XML，返回 (root | None, 失败原因)。

    坏 XML 必须变成一条可读的 ✗ 断言，而不是抛栈中断整个自检——
    否则读者看到 traceback 还得自己推断「是转义坏了还是文件没生成」。
    """
    try:
        return ET.parse(path).getroot(), ""
    except ET.ParseError as exc:
        return None, f"XML 不可解析：{exc}"


def _run(cmd, env_extra=None, cwd=None):
    env = dict(os.environ)
    # 子进程默认不写 JUnit，避免污染本次自检指定的输出目录（除非显式要求）
    env.pop("BONE_E2E_JUNIT_DIR", None)
    env.update(env_extra or {})
    return subprocess.run(
        cmd, capture_output=True, text=True, env=env, cwd=cwd or HERE, timeout=120
    )


# ==================== 1. config ====================
def test_config():
    section("1. config 契约")
    check("BASE 形如 http(s)://...", bool(re.match(r"^https?://", config.BASE)), config.BASE)
    check("LOGIN_BASE 形如 http(s)://...", bool(re.match(r"^https?://", config.LOGIN_BASE)), config.LOGIN_BASE)
    check("UI_BASE 形如 http(s)://...", bool(re.match(r"^https?://", config.UI_BASE)), config.UI_BASE)
    check("USER/PWD 非空", bool(config.USER) and bool(config.PWD), f"USER={config.USER!r}")
    check("TENANT 非空", bool(config.TENANT), repr(config.TENANT))
    check("TIMEOUT 为正整数", isinstance(config.TIMEOUT, int) and config.TIMEOUT > 0, config.TIMEOUT)
    suf = config.run_suffix()
    # cleanup-residue.py 按 ^\d{6,}$ 校验后缀，格式错会导致残留回收静默失效
    check("run_suffix 为 9 位数字（cleanup 按此回收残留）", bool(re.fullmatch(r"\d{9}", suf)), suf)


# ==================== 2. report ====================
def test_report():
    section("2. report 契约（子进程验计数与退出码，避免自测污染自身计数）")
    code = (
        "import sys\n"
        "from e2e_lib.report import check, summary\n"
        "check('甲', True)\n"
        "check('乙', False, 'boom <x> & \"q\"')\n"
        "sys.exit(summary())\n"
    )
    with tempfile.TemporaryDirectory() as td:
        r = _run(
            [sys.executable, "-c", code],
            env_extra={"BONE_E2E_JUNIT_DIR": td, "BONE_E2E_JUNIT_SUITE": "report-selftest"},
        )
        xml = os.path.join(td, "report-selftest.xml")
        check("有失败时退出码为 1（CI 读退出码）", r.returncode == 1, f"rc={r.returncode} {r.stderr[:120]}")
        check("控制台保留人读 ✓/✗ 输出", "✓ 甲" in r.stdout and "✗ 乙" in r.stdout, r.stdout[-160:])
        check("JUnit 文件已生成（每套件一个文件）", os.path.exists(xml), sorted(os.listdir(td)))
        if os.path.exists(xml):
            root, why = _parse_xml(xml)
            check("Python 侧 XML 可解析（转义正确）", root is not None, why)
            if root is not None:
                ts = root[0]
                check("testsuites 计数 tests=2/failures=1",
                      root.get("tests") == "2" and root.get("failures") == "1",
                      f"{root.attrib}")
                check("套件名为指定值", ts.get("name") == "report-selftest", ts.get("name"))
                fails = ts.findall("testcase/failure")
                check("失败用例带 failure 且特殊字符可还原",
                      len(fails) == 1 and fails[0].get("message") == 'boom <x> & "q"',
                      fails[0].get("message") if fails else "无 failure 节点")


# ==================== 3. fixtures ====================
def test_fixtures():
    section("3. fixtures.py 确定性与业务守恒")
    a, b = fixtures.Dataset("selftest-seed"), fixtures.Dataset("selftest-seed")
    seq_a = [a.person(), a.doc_no("BX"), a.amount(), a.customer["code"], a.split(1000, 3)]
    seq_b = [b.person(), b.doc_no("BX"), b.amount(), b.customer["code"], b.split(1000, 3)]
    check("同 seed 同调用序列 ⇒ 逐项同值（跨实例确定性）", seq_a == seq_b, f"{seq_a} vs {seq_b}")

    d = fixtures.Dataset("other-seed")
    check("doc_no 形如 BX-YYYYMMDD-NNNN",
          bool(re.fullmatch(r"BX-\d{8}-\d{4}", d.doc_no("BX"))), d.doc_no("BX"))

    cents = [fixtures.Dataset(f"amt-{i}").amount() for i in range(30)]
    rounded = [v for v in cents if abs(v - round(v)) < 0.005]
    check("amount 非圆整（30 个样本无整元）", not rounded, f"圆整样本={rounded[:5]}")

    total = 2863.5
    parts = fixtures.Dataset("split-seed").split(total, 3)
    check("split 分位守恒（Σ == total）", round(sum(parts), 2) == total, f"{parts} Σ={round(sum(parts), 2)}")
    brk = fixtures.Dataset("split-seed").split(total, 3, break_=True)
    check("break_ 恒差 1.01（负向断言可预期）",
          round(sum(brk) - total, 2) == 1.01, f"差={round(sum(brk) - total, 2)}")


# ==================== 4. chain_runner ====================
def test_chain_runner():
    section("4. chain_runner 执行器（fake http，不发真实请求）")
    calls = []

    def fake_http(method, path, body, token):
        calls.append((method, path, token))
        if method == "POST":
            return 200, {"code": 0, "data": {"id": "X1"}}
        if path.endswith("/X1"):
            return 200, {"code": 0, "data": {"id": "X1", "status": "OPEN"}}
        return 200, {"code": 0, "data": {"total": 2, "page": 1, "size": 2,
                                         "list": [{"id": "a", "n": 1}, {"id": "b", "n": 2}]}}

    emitted = []

    def emit(name, expect, actual, detail=""):
        emitted.append((name, expect, actual))
        return chain_runner.judge(expect, actual)

    chain_runner.RESULTS.clear()
    steps = [
        "分节标题（不执行）",
        {"method": "POST", "path": "/api/v1/thing", "body": {"k": "v"},
         "extract": {"rid": lambda c, st, j: chain_runner.rid_of(j)},
         "checks": [{"name": "创建 200", "kind": "status", "expect": 200}],
         "stop_if_empty": ["rid"]},
        {"method": "GET", "path": "/api/v1/thing/{rid}",
         "checks": [{"name": "详情状态 OPEN", "kind": "eq", "actual": "data.status", "expect": "OPEN"}]},
        {"method": "GET", "path": "/api/v1/thing",
         "checks": [{"name": "分页形状", "kind": "page", "expect_rows": 2, "required": ("id", "n")}]},
    ]
    ctx = chain_runner.ChainCtx(fake_http, main="tok", cid="T-SELFTEST")
    chain_runner.run_steps(steps, ctx, emit=emit)

    check("路径模板 {rid} 已替换为提取值", calls[1][1] == "/api/v1/thing/X1", calls[1][1])
    check("extract 写入 ctx.rid", getattr(ctx, "rid", None) == "X1", getattr(ctx, "rid", None))
    check("status 判据命中 200", ("创建 200", 200, 200) in emitted, emitted)
    check("eq 判据按点号路径取值", ("详情状态 OPEN", "OPEN", "OPEN") in emitted, emitted)
    check("page 判据全部通过（分页信封/取满/pageSize 生效/首行必填）",
          all(r[2] for r in chain_runner.RESULTS), [r for r in chain_runner.RESULTS if not r[2]])

    # stop_if_empty：取不到 id 必须中断，后续步骤不能执行
    chain_runner.RESULTS.clear()
    after = []

    def fake_http_empty(method, path, body, token):
        return 200, {"code": 0, "data": {}}

    steps2 = [
        {"method": "POST", "path": "/api/v1/thing",
         "extract": {"rid": lambda c, st, j: chain_runner.rid_of(j)},
         "stop_if_empty": ["rid"]},
        lambda c: after.append("不该执行"),
    ]
    ctx2 = chain_runner.ChainCtx(fake_http_empty, main="tok", cid="T2")
    chain_runner.run_steps(steps2, ctx2, emit=emit)
    check("stop_if_empty 命中即中断（后续步骤未执行）",
          ctx2.stopped and not after, f"stopped={ctx2.stopped} after={after}")

    # 未知 kind 必须显式报错，而不是静默跳过（静默跳过 = 断言消失的假绿）
    try:
        chain_runner.run_steps(
            [{"method": "GET", "path": "/x", "checks": [{"name": "坏判据", "kind": "nope"}]}],
            chain_runner.ChainCtx(fake_http, main="tok", cid="T3"), emit=emit,
        )
        raised = False
    except ValueError:
        raised = True
    check("未知判据 kind 抛 ValueError（不静默跳过）", raised)


# ==================== 5. JUnit（Node 侧） ====================
def test_junit_node():
    section("5. JUnit 出口 · Node 侧（零侵入：探针不改一行）")
    node = _node_bin()
    check("node 可用（20 个探针的硬依赖）", bool(node), "PATH 与 workbuddy 目录均未找到 node")
    if not node:
        return
    with tempfile.TemporaryDirectory() as td:
        probe = os.path.join(td, "junit_probe.cjs")
        xml = os.path.join(td, "node-selftest.xml")
        with open(probe, "w", encoding="utf-8") as fh:
            fh.write(
                f"const {{ makeChecker }} = require({json.dumps(os.path.join(HERE, 'e2e_lib', 'client.cjs'))});\n"
                "const { check, report } = makeChecker();\n"
                "check('甲', true);\n"
                "check('乙', false, 'HTTP 500 <body> & \"q\"');\n"
                "report('t');\n"
                "process.exit(1);\n"
            )
        r = _run([node, probe],
                 env_extra={"BONE_E2E_JUNIT_DIR": td, "BONE_E2E_JUNIT_SUITE": "node-selftest"})
        check("探针退出码保持原样（JUnit 不篡改结果）", r.returncode == 1,
              f"rc={r.returncode} {r.stderr[:160]}")
        check("JUnit 文件已生成（每套件一个文件）", os.path.exists(xml), sorted(os.listdir(td)))
        if os.path.exists(xml):
            root, why = _parse_xml(xml)
            check("Node 侧 XML 可解析（转义正确）", root is not None, why)
            if root is None:
                return
            ts = root[0]
            check("Node 侧 tests=2/failures=1",
                  root.get("tests") == "2" and root.get("failures") == "1", root.attrib)
            fails = ts.findall("testcase/failure")
            check("Node 侧失败详情特殊字符可还原",
                  len(fails) == 1 and fails[0].get("message") == 'HTTP 500 <body> & "q"',
                  fails[0].get("message") if fails else "无 failure 节点")


# ==================== 5b. JUnit 跨进程隔离 ====================
def test_junit_isolation():
    """回归：一次 CI 跑几十个脚本共用一个结果路径时，后写者会把前者覆盖掉。

    实测踩到过——Node 自检的 2 条断言把 Python 侧 34 条覆盖成 2 条，CI 只看到最后一个脚本。
    改为「目录 + 每套件一个文件」后，本断言锁死这个行为。
    """
    section("5b. JUnit 跨进程隔离（回归：曾把 34 条覆盖成 2 条）")
    py_code = (
        "import sys\n"
        "from e2e_lib.report import check, summary\n"
        "check('唯一断言', True)\n"
        "sys.exit(summary())\n"
    )
    node = _node_bin()
    with tempfile.TemporaryDirectory() as td:
        _run([sys.executable, "-c", py_code],
             env_extra={"BONE_E2E_JUNIT_DIR": td, "BONE_E2E_JUNIT_SUITE": "suite-a"})
        _run([sys.executable, "-c", py_code],
             env_extra={"BONE_E2E_JUNIT_DIR": td, "BONE_E2E_JUNIT_SUITE": "suite-b"})
        if node:
            probe = os.path.join(td, "p.cjs")
            with open(probe, "w", encoding="utf-8") as fh:
                fh.write(
                    f"const {{ makeChecker }} = require({json.dumps(os.path.join(HERE, 'e2e_lib', 'client.cjs'))});\n"
                    "const { check, report } = makeChecker();\n"
                    "check('唯一断言', true);\n"
                    "report('t');\n"
                )
            _run([node, probe],
                 env_extra={"BONE_E2E_JUNIT_DIR": td, "BONE_E2E_JUNIT_SUITE": "suite-c"})
        files = sorted(f for f in os.listdir(td) if f.endswith(".xml"))
        check("三个脚本产出三个独立文件（互不覆盖）", len(files) == 3, files)
        bad = []
        for f in files:
            root, why = _parse_xml(os.path.join(td, f))
            if root is None or root.get("tests") != "1":
                bad.append(f"{f}: {why or root.attrib}")
        check("每个文件各自 tests=1（内容未被别的脚本改写）", not bad, bad)


# ==================== 6. 纪律棘轮 ====================
def test_discipline_ratchet():
    section("6. 纪律棘轮：内联浏览器登录只可收缩")
    inline = []
    for pattern in ("ui/*.mjs", "*.mjs", "*.cjs"):
        for path in sorted(glob.glob(os.path.join(HERE, pattern))):
            with open(path, encoding="utf-8", errors="replace") as fh:
                src = fh.read()
            if _PWD_SELECTOR.search(src) and not _USES_SHARED_UI.search(src):
                inline.append(os.path.relpath(path, HERE))
    check(f"内联登录探针数 ≤ 基线 {INLINE_LOGIN_BASELINE}（只可收缩）",
          len(inline) <= INLINE_LOGIN_BASELINE,
          # 超基线时给出完整清单：无法可靠判断「哪个是新增的」，列全量让操作者自己比对
          f"当前 {len(inline)} 个（基线 {INLINE_LOGIN_BASELINE}）：{inline}")
    if len(inline) < INLINE_LOGIN_BASELINE:
        print(f"    · 已降到 {len(inline)} 个 —— 请把 selftest.py 的 INLINE_LOGIN_BASELINE 下调，"
              f"让棘轮只进不退")

    # 自定义 uiLogin/login 函数是最严重形态：重复实现共享层，一处改口径就分裂
    offenders = []
    for path in sorted(glob.glob(os.path.join(HERE, "ui/*.mjs"))):
        with open(path, encoding="utf-8", errors="replace") as fh:
            src = fh.read()
        if re.search(r"(async function|const)\s+(uiLogin|login)\s*(=\s*async)?\s*\(", src):
            offenders.append(os.path.relpath(path, HERE))
    check("探针内不得自定义 uiLogin/login 函数（应 import ui.cjs）",
          not offenders, f"违规：{offenders}")


def main():
    test_config()
    test_report()
    test_fixtures()
    test_chain_runner()
    test_junit_node()
    test_junit_isolation()
    test_discipline_ratchet()
    sys.exit(summary("selftest 汇总"))


if __name__ == "__main__":
    main()
