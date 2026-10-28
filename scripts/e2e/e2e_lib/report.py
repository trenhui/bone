# -*- coding: utf-8 -*-
"""断言与报告（每条 E2E 的「有牙」出口）。

统一口径，避免各脚本自造轮子导致判据漂移：

  * `check(名称, 条件, 失败详情)` —— 条件为真记 PASS，为假记 FAIL 并**打印失败详情**；
    详情必须写「拿到了什么」（状态码 / errorCode / message 片段），否则红了也没法定位。
  * `section(标题)` / `note(备注)` —— 分节与旁注，让长链路日志可读。
  * `finish()` —— 打印汇总并按结果 `sys.exit(1/0)`，CI 直接读退出码。

机器可读出口（CI 用，人读输出不变）：设 `BONE_E2E_JUNIT_DIR=/dir/` 后，
本进程的断言在退出时写成 `<dir>/<套件名>.xml`（标准 JUnit，GitHub Actions / Jenkins /
GitLab / Allure 均可直接渲染）。**未设变量时零副作用**。

为什么是「一个套件一个文件」而不是单个固定路径：一次 CI 会依次跑 11 个 Python 脚本 +
20 个探针，若都写同一个路径，后写者会**覆盖**前者的结果（实测踩到：Node 自检的 2 条
断言把 Python 侧 34 条覆盖成 2 条）。按套件分文件既无竞争，也让每个脚本的结果可独立定位。

失败详情是硬要求：`✗ 断言名 ← 422 SUB-COUNT-01` 远好过 `✗ 断言名`。
"""
import atexit
import os
import re
import sys

PASS = []
FAIL = []

# JUnit 累积：同进程多次 summary()（如 reset() 后连跑多套）→ 多个 <testsuite>，一个文件
_SUITES = []


def _xml_escape(text) -> str:
    """转义 XML 文本/属性。断言详情里常含 `<url>`、`"`、`&`，不转义会写出坏 XML。"""
    return (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def _suite_name() -> str:
    """套件名：默认取脚本文件名（out.xml 里一眼看出是哪个 E2E），可被环境变量覆盖。"""
    forced = os.environ.get("BONE_E2E_JUNIT_SUITE")
    if forced:
        return forced
    base = os.path.basename(sys.argv[0] or "e2e")
    return base[:-3] if base.endswith(".py") else base


def _junit_target() -> str:
    """算出本进程的 JUnit 落点 `<dir>/<套件名>.xml`；同目录已有同名文件则加序号。

    返回空串表示未启用。目录不存在会自动创建。
    """
    d = os.environ.get("BONE_E2E_JUNIT_DIR")
    if not d:
        return ""
    # 文件名只保留安全字符：套件名可能来自脚本名或外部变量
    base = re.sub(r"[^A-Za-z0-9._-]", "-", _suite_name()) or "e2e"
    path = os.path.join(d, base + ".xml")
    n = 2
    while os.path.exists(path) and n <= 50:
        path = os.path.join(d, f"{base}-{n}.xml")
        n += 1
    os.makedirs(d, exist_ok=True)
    return path


def _write_junit(path: str) -> None:
    """把累积的 suites 写成 JUnit XML（多套合并为 <testsuites>）。"""
    total = sum(len(s[1]) + len(s[2]) for s in _SUITES)
    fails = sum(len(s[2]) for s in _SUITES)
    lines = ['<?xml version="1.0" encoding="UTF-8"?>']
    lines.append(
        f'<testsuites name="bone-e2e" tests="{total}" failures="{fails}" errors="0">'
    )
    for name, passed, failed in _SUITES:
        n = len(passed) + len(failed)
        lines.append(
            f'  <testsuite name="{_xml_escape(name)}" tests="{n}" '
            f'failures="{len(failed)}" errors="0" skipped="0">'
        )
        for case in passed:
            lines.append(f'    <testcase name="{_xml_escape(case)}" time="0"/>')
        for entry in failed:
            # FAIL 条目格式为 "名称 :: 详情"
            case, _, detail = entry.partition(" :: ")
            lines.append(
                f'    <testcase name="{_xml_escape(case)}" time="0">'
                f'<failure message="{_xml_escape(detail)}">{_xml_escape(detail)}</failure>'
                f"</testcase>"
            )
        lines.append("  </testsuite>")
    lines.append("</testsuites>")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def _flush_junit() -> None:
    """进程退出时落盘（atexit）。写失败只告警，绝不掩盖脚本自身结果。"""
    if not _SUITES:
        return
    try:
        target = _junit_target()
        if target:
            _write_junit(target)
    except OSError as exc:  # 磁盘满 / 权限不足：告警即可，退出码由断言决定
        print(f"[junit] 写出失败: {exc}", file=sys.stderr)


atexit.register(_flush_junit)


def check(name, cond, detail=""):
    """记录一条断言。`detail` 只在失败时展示，请写实测值而非预期值。"""
    if cond:
        PASS.append(name)
        print(f"  ✓ {name}")
    else:
        FAIL.append(f"{name} :: {detail}")
        print(f"  ✗ {name}  ← {detail}")
    return bool(cond)


def section(title):
    print(f"\n{'=' * 78}\n{title}\n{'=' * 78}")


def note(text):
    print(f"    · {text}")


def reset():
    """清空计数（同进程连跑多套用例时用）。"""
    PASS.clear()
    FAIL.clear()


def summary(label="汇总"):
    """打印汇总块，返回退出码（0=全绿）。**不**退出进程，便于调用方决定时机。

    设了 `BONE_E2E_JUNIT_DIR` 时，顺带把本轮断言并入待落盘的 suites；
    真正的文件写入在进程退出时由 atexit 完成（不影响人读输出）。
    """
    section(label)
    print(f"通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
    for f in FAIL:
        print(f"  ✗ {f}")
    if os.environ.get("BONE_E2E_JUNIT_DIR"):
        name = _suite_name()
        if any(s[0] == name for s in _SUITES):
            name = f"{name}#{len(_SUITES) + 1}"
        _SUITES.append((name, list(PASS), list(FAIL)))
    return 1 if FAIL else 0


def finish(label="汇总"):
    """打印汇总并 `sys.exit`；E2E 脚本的标准收尾。"""
    sys.exit(summary(label))
