#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""synapClaim 文档门禁**统一入口**（唯一真源）。

用法：
    python3 tools/check_all.py              # 跑全套（CI / 本地一致）
    python3 tools/check_all.py --self-test  # 只测本脚本的判据，不跑全套、不写文件
    python3 tools/check_all.py --list       # 列出登记项与排除项

为什么需要它（2026-11-08 实测，三条都指向同一族缺陷）：

1. 治理规约 §六 第 7 条原本是 18 条 `python3 ... && python3 ...` 串成的人工命令。
   实测「规范写的」与「最近实际跑的」已漂移：校准内核 `--check` / `--self-test` 两道
   被漏跑，就绪度自检被多跑。命令写在文档里 ≠ 每次都跑全。
2. 9 道门禁在 CI 中**零调用**：`docs-compliance.yml` 管的是另一套 8 模块
   compliance collector，`ci.yml` / `check.sh` 均未引用本目录脚本（治理规约 §六
   明确「本目录自带脚本一贯不进 check.sh」是 pre-commit 的制度决策，不是 CI 决策）。
3. P22 教训：静态覆盖归零不会报错，只会静默变绿。故本脚本**自动发现**
   `tools/_check_*.py` 并与登记表做集合对账（G1–G4）。

设计约束：纯 stdlib、零 DB、零 JDK、零网络，可在干净检出上无条件跑。
`_audit_ddl_vs_db.py` 的实跑依赖活库，故只接 `--self-test`（解析逻辑）。

G5/G6（2026-11-08 二次加固，都是**本脚本首版自己造的洞**）：

  · G1–G4 只守「脚本名集合」——删掉登记表里一行（如 `_check_ddl.py --self-test`）
    集合不变、判据仍全绿，与 P22「覆盖率静默归零」同族。故引入**第二张表**
    `EXPECTED_CALLS` 与 `GATES`/`EXTRA` 交叉校验：两张表互为独立真源，删行 /
    改参数 / 新增脚本只进一张表都判红。
  · `synap-claim-docs.yml` 此前在所有机检里零引用 ⇒ 被删、被加 paths 白名单、
    分支漂移、step 被改成空壳都没人报红。`ci_wiring_errors()` 把 workflow 的
    分支 / 两条命令 / 无 paths / 最小权限绑成可判定判据。

**边界（诚实标注）**：CI 无法守护「自己被删除」——workflow 一旦被删，本判据也不会
被 CI 触发；只能在本地 / 其它 workflow 运行时发现，剩余风险靠 PR 审查。这与
`scripts/check-gate-liveness.py` 对「根入口是否被触发」的不可机械验证标注同口径。
"""
from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

TOOLS = pathlib.Path(__file__).resolve().parent
ROOT = TOOLS.parent

Gate = tuple[str, tuple[str, ...], str]

# 门禁登记：新增/删除脚本必须同步改这里，G2/G3 会逼你改。
# 同一脚本多行是允许的（主判据 + --self-test 是两次独立执行）。
GATES: tuple[Gate, ...] = (
    ("_check_manifest.py", (), "M1–M9 文档清单与磁盘双向一致"),
    ("_check_xref.py", (), "§ 交叉引用可解析"),
    ("_check_contract_drift.py", ("--self-test",), "契约漂移判据自证"),
    ("_check_contract_drift.py", (), "C1–C8 契约漂移"),
    ("_check_ddl.py", ("--self-test",), "DDL 判据自证（含 D7 集合对账）"),
    ("_check_ddl.py", (), "D1–D7 DDL 文档内部自洽"),
    ("_check_numbers.py", ("--self-test",), "数字口径 / oracle 接线判据自证"),
    ("_check_numbers.py", (), "FORBIDDEN / REQUIRED / ADR 可追溯 / oracle CI 接线"),
    ("_check_ruleset_alignment.py", ("--self-test",), "规则命名空间判据自证"),
    ("_check_ruleset_alignment.py", (), "A1–A8 规则 ID / 覆盖 / 族级条数 / 落点 / 表达式变量对账"),
    ("_check_error_code_i18n.py", ("--self-test",), "错误码双语判据自证"),
    ("_check_error_code_i18n.py", (), "E1–E5 错误码 ↔ HTTP ↔ 双语"),
    ("_check_ai_native_calibration.py", ("--check",), "校准内核 C1–C5 强判据"),
    ("_check_ai_native_calibration.py", ("--self-test",), "校准内核负向探针"),
    ("_check_doc_structure.py", ("--self-test",), "方案文档结构判据自证"),
    ("_check_doc_structure.py", (), "方案文档必选四节全量判红"),
)

# 非 _check_*.py，但属于全套且必须执行的步骤。
EXTRA: tuple[Gate, ...] = (
    ("_audit_ddl_vs_db.py", ("--self-test",), "列级对账解析自检（不连库）"),
    ("_ai_native_readiness.py", ("--self-test",), "上线就绪度判据自证"),
    ("_gen_ai_index.py", ("--check",), "AI-INDEX 无漂移"),
)

# 明确不接「全套实跑」的脚本，理由必须写清（评审可查，--list 会输出）。
EXCLUDED: dict[str, str] = {
    "_audit_ddl_vs_db.py": "实跑依赖活库 information_schema，CI 无 DB；只接 --self-test（EXTRA 已登记）",
    "_ai_native_readiness.py": "主输出是就绪度报告（BLOCKED 是业务事实不是构建失败）；只接 --self-test",
    "_manifest.py": "共享库，由各机检 import，没有独立入口",
}

SELF = pathlib.Path(__file__).name
TIMEOUT = 120

# synapClaim → ai-product → doc → bone
REPO = ROOT.parents[2]
CI_WF = REPO / ".github" / "workflows" / "synap-claim-docs.yml"
CI_MAIN = REPO / ".github" / "workflows" / "ci.yml"

# 第二张表：每个脚本**必须**被调用的 argv 组合。刻意与 GATES/EXTRA 分开定义，
# 否则「拿登记表验登记表」就是自证。两表键集合双向对账 + 调用组合双向对账。
EXPECTED_CALLS: dict[str, tuple[tuple[str, ...], ...]] = {
    "_check_manifest.py": ((),),
    "_check_xref.py": ((),),
    "_check_contract_drift.py": (("--self-test",), ()),
    "_check_ddl.py": (("--self-test",), ()),
    "_check_numbers.py": (("--self-test",), ()),
    "_check_ruleset_alignment.py": (("--self-test",), ()),
    "_check_error_code_i18n.py": (("--self-test",), ()),
    "_check_ai_native_calibration.py": (("--check",), ("--self-test",)),
    "_check_doc_structure.py": (("--self-test",), ()),
    "_audit_ddl_vs_db.py": (("--self-test",),),
    "_ai_native_readiness.py": (("--self-test",),),
    "_gen_ai_index.py": (("--check",),),
}


def load_state() -> tuple[set[str], set[str], set[str], set[str]]:
    """返回 (发现的门禁脚本, 登记的门禁脚本, tools 下全部 .py, 已登记归属的 .py)。"""
    discovered = {p.name for p in TOOLS.glob("_check_*.py")}
    registered = {g[0] for g in GATES}
    all_py = {p.name for p in TOOLS.glob("*.py")}
    known = registered | {g[0] for g in EXTRA} | set(EXCLUDED) | {SELF}
    return discovered, registered, all_py, known


def gate_errors(
    discovered: set[str], registered: set[str], all_py: set[str], known: set[str]
) -> list[str]:
    """G1–G4：登记完整性判据。纯函数，注入构造集合即可反证。"""
    errs: list[str] = []
    if not discovered:
        errs.append("G1 tools/_check_*.py 解析为空 → fail-closed（门禁目录被移动或改名）")
    if discovered - registered:
        errs.append("G2 新增门禁脚本未登记进 check_all.py：%s"
                    % sorted(discovered - registered))
    if registered - discovered:
        errs.append("G3 登记的门禁脚本已不存在：%s" % sorted(registered - discovered))
    if all_py - known:
        errs.append("G4 tools/ 下出现未登记归属的 .py（须进 GATES/EXTRA/EXCLUDED）：%s"
                    % sorted(all_py - known))
    return errs


def call_errors(expected, steps) -> list[str]:
    """G5：执行清单（GATES+EXTRA）与第二张期望表双向对账。纯函数，便于反证。"""
    if not steps:
        return ["G5 执行清单为空 → fail-closed"]
    errs: list[str] = []
    reg = {g[0] for g in steps}
    if reg - set(expected):
        errs.append("G5 新增脚本只进了 GATES/EXTRA、未进 EXPECTED_CALLS：%s"
                    % sorted(reg - set(expected)))
    if set(expected) - reg:
        errs.append("G5 EXPECTED_CALLS 里登记了执行清单没有的脚本：%s"
                    % sorted(set(expected) - reg))
    actual = {(g[0], tuple(g[1])) for g in steps}
    for name, calls in expected.items():
        for argv in calls:
            if (name, argv) not in actual:
                errs.append("G5 %s 的 %s 调用缺失（登记行被删或参数被改）"
                            % (name, " ".join(argv) or "主判据"))
    exp_pairs = {(n, tuple(a)) for n, cs in expected.items() for a in cs}
    if actual - exp_pairs:
        errs.append("G5 执行清单里出现 EXPECTED_CALLS 之外的调用：%s"
                    % sorted(actual - exp_pairs))
    return errs


def read_ci_wiring() -> tuple[str, str, bool, bool]:
    """读 workflow 与 ci.yml；返回 (wf, ci, wf_exists, ci_exists)。"""
    we, ce = CI_WF.is_file(), CI_MAIN.is_file()
    wf = CI_WF.read_text(encoding="utf-8") if we else ""
    ci = CI_MAIN.read_text(encoding="utf-8") if ce else ""
    return wf, ci, we, ce


def _branches(text: str) -> set[str]:
    out: set[str] = set()
    for m in re.finditer(r"^\s*branches:\s*\[([^\]]+)\]", text, re.M):
        out |= {x.strip().strip("'\"") for x in m.group(1).split(",") if x.strip()}
    return out


def ci_wiring_errors(wf_text: str, ci_text: str,
                     wf_exists: bool = True, ci_exists: bool = True) -> list[str]:
    """G6：CI 接线判据。纯函数，注入构造文本即可反证，不碰任何文件。"""
    if not wf_exists:
        return ["G6 CI workflow 文件不存在 → fail-closed（门禁接线被删）"]
    if not wf_text.strip():
        return ["G6 CI workflow 内容为空 → fail-closed"]
    if not ci_exists:
        return ["G6 无法读取 ci.yml 分支真源 → fail-closed"]

    errs: list[str] = []
    # 只匹配 YAML key 行，避开注释里「不设 on.paths」这类叙述。
    if re.search(r"^\s+paths:\s*$", wf_text, re.M):
        errs.append("G6 出现 paths 白名单：门禁输入含码侧资产，漏列即静默不触发")
    ci_b, wf_b = _branches(ci_text), _branches(wf_text)
    if not ci_b:
        errs.append("G6 ci.yml 分支解析为空 → fail-closed")
    elif ci_b - wf_b:
        errs.append("G6 workflow 分支少于 ci.yml：%s" % sorted(ci_b - wf_b))

    runs = [l.strip() for l in wf_text.splitlines()
            if re.match(r"^\s*(-\s*)?run:", l)]
    chk = [l for l in runs if "check_all.py" in l]
    if len(chk) < 2:
        errs.append("G6 门禁命令不足两条（须 --self-test + 全套）")
    else:
        if not any("--self-test" in l for l in chk):
            errs.append("G6 缺 --self-test 步")
        if not any("check_all.py" in l and "--self-test" not in l for l in chk):
            errs.append("G6 缺全套步")
    if not re.search(r"permissions:[\s\S]{0,120}?contents:\s*read", wf_text):
        errs.append("G6 缺最小权限 permissions.contents: read")
    return errs


def wiring_errors() -> list[str]:
    """全套接线判据汇总：G1–G6。"""
    return (gate_errors(*load_state())
            + call_errors(EXPECTED_CALLS, list(GATES + EXTRA))
            + ci_wiring_errors(*read_ci_wiring()))


def run_one(cmd: list[str]) -> tuple[bool, str]:
    """执行单条命令，返回 (是否 exit 0, 合并输出)。超时/起不来一律判失败。"""
    try:
        r = subprocess.run(
            cmd, cwd=str(ROOT), capture_output=True, text=True,
            encoding="utf-8", errors="replace", timeout=TIMEOUT,
        )
        return r.returncode == 0, (r.stdout or "") + (r.stderr or "")
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, "执行失败：%r" % (e,)


def self_test() -> int:
    """只测判据，不跑全套、不碰任何文件（共享树并行会话安全）。"""
    passed = 0
    failed = 0

    def expect(label: str, cond: bool) -> None:
        nonlocal passed, failed
        if cond:
            passed += 1
            print("  [OK] " + label)
        else:
            failed += 1
            print("  [FAIL] " + label)

    disc = {"_check_a.py", "_check_b.py"}
    reg = {"_check_a.py", "_check_b.py"}
    all_py = disc | {SELF, "_manifest.py", "_gen_ai_index.py"}
    known = reg | {"_gen_ai_index.py", "_manifest.py", SELF}

    print("=== check_all.py --self-test ===")
    expect("合规正例（发现 == 登记、全部 .py 有归属）",
           gate_errors(disc, reg, all_py, known) == [])
    expect("变异① 新增门禁脚本未登记 → G2 判红",
           any("G2" in e for e in gate_errors(disc | {"_check_new.py"}, reg, all_py, known)))
    expect("变异② 登记的脚本被删 → G3 判红",
           any("G3" in e for e in gate_errors(disc - {"_check_b.py"}, reg, all_py, known)))
    expect("变异③ 门禁目录解析为空 → G1 fail-closed",
           any("G1" in e for e in gate_errors(set(), reg, all_py, known)))
    expect("变异④ 新增无归属 .py → G4 判红",
           any("G4" in e for e in gate_errors(disc, reg, all_py | {"_orphan.py"}, known)))
    expect("变异⑤ 归属表漏一项（即使脚本在磁盘上）→ G4 判红",
           any("G4" in e for e in gate_errors(disc, reg, all_py, known - {"_manifest.py"})))

    # ---- G5：删行 / 改参数 / 只进一张表，都必须判红 ----
    steps_ok = list(GATES + EXTRA)
    expect("G5 合规正例（两表零差集）", call_errors(EXPECTED_CALLS, steps_ok) == [])
    steps_del = [g for g in steps_ok
                 if not (g[0] == "_check_ddl.py" and g[1] == ("--self-test",))]
    expect("G5 变异① 删掉一条登记行（脚本名集合不变）→ 判红",
           any("G5" in e for e in call_errors(EXPECTED_CALLS, steps_del)))
    steps_arg = [tuple(("--bogus",) if i == 1 else v for i, v in enumerate(g))
                 if g[0] == "_check_ddl.py" and g[1] == ("--self-test",) else g
                 for g in steps_ok]
    expect("G5 变异② 参数被改坏 → 判红",
           any("G5" in e for e in call_errors(EXPECTED_CALLS, steps_arg)))
    expect("G5 变异③ 新增脚本只进执行清单 → 判红",
           any("G5" in e for e in call_errors(
               EXPECTED_CALLS, steps_ok + [("_check_new.py", (), "")])))
    expect("G5 变异④ 执行清单为空 → fail-closed",
           any("G5" in e for e in call_errors(EXPECTED_CALLS, [])))

    # ---- G6：workflow 接线反证（纯文本注入，不写文件）----
    wf, ci, we, ce = read_ci_wiring()
    expect("G6 合规正例（真实 workflow + ci.yml）",
           ci_wiring_errors(wf, ci, we, ce) == [])
    expect("G6 变异① workflow 被删 → fail-closed",
           any("G6" in e for e in ci_wiring_errors("", ci, False, True)))
    expect("G6 变异② 删掉 --self-test 步 → 判红",
           any("G6" in e for e in ci_wiring_errors(
               wf.replace("check_all.py --self-test", "check_all.py"), ci)))
    expect("G6 变异③ 加 paths 白名单 → 判红",
           any("G6" in e for e in ci_wiring_errors(
               wf.replace("  push:\n", "  push:\n    paths:\n      - 'doc/**'\n"), ci)))
    expect("G6 变异④ 分支去掉 dev → 判红",
           any("G6" in e for e in ci_wiring_errors(
               wf.replace("[dev, main, master]", "[main, master]"), ci)))
    expect("G6 变异⑤ 最小权限被改 → 判红",
           any("G6" in e for e in ci_wiring_errors(
               wf.replace("contents: read", "contents: write"), ci)))

    # 真实磁盘集成正例：脚本集合 / 两表 / CI 接线必须与当前仓库完全对齐。
    real = wiring_errors()
    expect("集成正例 真实 tools/ + 两表 + CI 接线零差集", real == [])
    if real:
        for e in real:
            print("        " + e)

    # 证明执行链路不是恒绿：故意让子进程 exit 7，runner 必须捕获。
    ok, _ = run_one([sys.executable, "-c", "import sys; sys.exit(7)"])
    expect("变异⑥ 子进程非零退出必须被捕获（防 runner 恒绿）", not ok)
    ok2, _ = run_one([sys.executable, "-c", "print('ok')"])
    expect("合规正例 子进程 exit 0 判通过", ok2)

    print("  %d 通过 / %d 失败" % (passed, failed))
    return 1 if failed else 0


def show_list() -> int:
    print("=== check_all.py 登记项（%d 门禁 + %d 附加 = %d 步）==="
          % (len(GATES), len(EXTRA), len(GATES) + len(EXTRA)))
    for i, (name, args, label) in enumerate(GATES + EXTRA, 1):
        print("  %2d. %-38s %-14s %s" % (i, name, " ".join(args) or "(无参数)", label))
    print("\n=== G5 期望调用组合（与执行清单交叉校验）===")
    print("  %d 个脚本 / %d 个必需调用" % (len(EXPECTED_CALLS),
          sum(len(v) for v in EXPECTED_CALLS.values())))
    print("\n=== G6 CI 接线 ===")
    print("  %s（判据：分支 / 两条命令 / 无 paths / 最小权限）" % CI_WF.name)
    print("\n=== 明确不接实跑（理由必须保留）===")
    for name, why in sorted(EXCLUDED.items()):
        print("  %-32s %s" % (name, why))
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="synapClaim 文档门禁统一入口")
    ap.add_argument("--self-test", action="store_true", help="只测本脚本判据，不跑全套")
    ap.add_argument("--list", action="store_true", help="列出登记项与排除项")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    if args.list:
        return show_list()

    print("=== synapClaim 文档门禁全套（统一入口）===")
    errs = wiring_errors()
    if errs:
        for e in errs:
            print("【FAIL·接线】" + e)
        print("共 %d 项接线失败——门禁目录/登记/CI 接线变了但没跟上，禁止放行" % len(errs))
        return 1

    failed: list[str] = []
    steps = GATES + EXTRA
    for i, (name, argv, label) in enumerate(steps, 1):
        cmd = [sys.executable, str(TOOLS / name), *argv]
        ok, out = run_one(cmd)
        tag = "PASS" if ok else "FAIL"
        print("  [%2d/%d %-4s] %-38s %-14s %s"
              % (i, len(steps), tag, name, " ".join(argv) or "-", label))
        if not ok:
            failed.append(name + (" " + " ".join(argv) if argv else ""))
            tail = [l for l in out.strip().splitlines() if l.strip()][-12:]
            for l in tail:
                print("           | " + l[:160])

    print()
    if failed:
        print("【FAIL】%d/%d 步失败：%s" % (len(failed), len(steps), ", ".join(failed)))
        return 1
    print("【OK】%d 步全绿（G1–G6 接线 / 判据自证 / 主判据 / 索引校验）" % len(steps))
    return 0


if __name__ == "__main__":
    sys.exit(main())
