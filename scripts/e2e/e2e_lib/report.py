# -*- coding: utf-8 -*-
"""断言与报告（每条 E2E 的「有牙」出口）。

统一口径，避免各脚本自造轮子导致判据漂移：

  * `check(名称, 条件, 失败详情)` —— 条件为真记 PASS，为假记 FAIL 并**打印失败详情**；
    详情必须写「拿到了什么」（状态码 / errorCode / message 片段），否则红了也没法定位。
  * `section(标题)` / `note(备注)` —— 分节与旁注，让长链路日志可读。
  * `finish()` —— 打印汇总并按结果 `sys.exit(1/0)`，CI 直接读退出码。

失败详情是硬要求：`✗ 断言名 ← 422 SUB-COUNT-01` 远好过 `✗ 断言名`。
"""
import sys

PASS = []
FAIL = []


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
    """打印汇总块，返回退出码（0=全绿）。**不**退出进程，便于调用方决定时机。"""
    section(label)
    print(f"通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
    for f in FAIL:
        print(f"  ✗ {f}")
    return 1 if FAIL else 0


def finish(label="汇总"):
    """打印汇总并 `sys.exit`；E2E 脚本的标准收尾。"""
    sys.exit(summary(label))
