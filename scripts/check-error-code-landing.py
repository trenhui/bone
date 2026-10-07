#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：业务异常必须携带稳定业务码（errorCode 不得为 null）。

背景
----
`BizException` 有**两个「码」**（见其 javadoc）：

- ``code``（int）—— HTTP 状态，供 ``GlobalExceptionHandler`` 设置响应状态；
- ``errorCode``（String）—— 稳定业务码（如 ``BP_ORDER_NOT_FOUND``），
  是前端 i18n 映射键与监控聚合口径，**跨系统契约**。

`ProblemDetailContractTest` 已锁定「有 errorCode 时它正确」，
但**没有钉住「必须有 errorCode」**——于是全部默认路径都让``errorCode == null``。

实测（2026-10-07）本仓``BizException`` 的 9 个静态 ``of()`` 重载
**无一携带 errorCode**：它们统统委派到 ``(int, String, Throwable)`` 或
``(String)`` 这两个把 ``errorCode`` 硬置null 的构造器。包括注释里标为
「使用枚举类（推荐）」的 ``of(ErrorCode)``——``ErrorCode`` 只有 ``code``+``msg``，
委派后 ``errorCode`` 同样是 null。

后果链（与 §0 同源，但更隐蔽）：无errorCode ⇒ ``ProblemDetail.errorCode`` 为空 ⇒
前端拿不到 ``i18n.t('errors.' + errorCode)`` 的键（退化为展示中文/英文 message）⇒
监控只能按文案聚合，文案一改口径即断。**而 ``BizException.of("实体不存在: " + id)``
这类写法看着完全规范，实际是全仓最大的 footgun 来源**（仅
``MetaEntityApplicationService`` 一个文件就有 17 处）。

规则
----
在 **application/ 与 adapter/web/**（对外承诺语义，必须稳定）内：
- ``BizException.of(...)``  —— **一律违规**（9 个重载全丢码，无一例外）；
- ``new BizException(...)`` —— 仅当携带业务码时合规：
  ``(int, String, String errorCode)`` 与 ``(int, String, String, Throwable)``；
  其余重载（1~2 参、``(int,String,Throwable)``、``(String)``…）均违规。

逃生舱（刻意不算违规，但**必须留报告**）
------------------------------------
``domain/`` 与 ``infrastructure/`` **不阻断、只报存量**：
- domain 层抛裸异常是**正确分层**（域不依赖 ``BizException``），由 app 层catch 后翻译；
- infrastructure 的第三方 SDK 包装、以及「内部信号协议」式异常
  （如 ``RefreshTokenIssuerGatewayAdapter`` 抛 ``IllegalArgumentException``
  被 ``AuthApplicationService`` catch 后翻译）**改了反而绕过既有翻译**。
⇒ 这两类只统计不阻断，避免门禁逼人造假码。

存量baseline 只可收缩：新增违规不得加入；条目被修复后应从 baseline 移除。

用法::

    python3 scripts/check-error-code-landing.py# 阻断（新增违规）
    python3 scripts/check-error-code-landing.py --report-only     # 只出报告，恒 exit 0
    python3 scripts/check-error-code-landing.py --update-baseline # 按实测重写 baseline
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE_PATH = REPO / "doc/architecture/error-code-landing-baseline.json"

SCAN_ROOTS = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]

# 分层：只有这两层对外承诺业务语义，必须带码（阻断）；其余层只报存量
STRICT_MARKERS = ("/application/", "/adapter/web/")
ADVISORY_MARKERS = ("/domain/", "/infrastructure/")

# 门禁自身要豁免的文件：① 工厂/码类本体（它们**就是**修复手段）② 异常处理器③ 类型定义
EXEMPT_NAME_RE = re.compile(
    r"(ErrorCodes|Errors)\.java$|BizException\.java$|.*ExceptionHandler\.java$"
)

BIZEX_NEW_RE = re.compile(r"\bnew\s+BizException\s*\(")
BIZEX_OF_RE = re.compile(r"\bBizException\s*\.\s*of\s*\(")


def strip_code_keep_lines(text: str) -> str:
    """剥离注释与字符串字面量，**且输出行数与输入一一对应**（否则报错行号错位）。

    ⚠️禁用 ``re.sub(r"//.*$|/\\*.*?\\*/", ..., re.S)``：``re.S`` 会让
    ``/\\*.*?\\*/`` 从第一个 ``/*`` 吃到最后一个 ``*/``，实测把整文件吃成空串⇒ 门禁假绿。
    （实测缺陷模式①②，务必保留「逐行状态机 + 行数守恒」这两条。）
    """
    out = []
    in_block = False
    in_str = False
    in_chr = False
    quote = ""
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if in_block:
            if c == "*" and nxt == "/":
                in_block = False
                i += 2
                out.append("  ")
                continue
            # 保留换行以维持行数守恒
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if in_str or in_chr:
            q = quote
            if c == "\\" and nxt:
                out.append("  ")
                i += 2
                continue
            if c == q:
                in_str = in_chr = False
                quote = ""
                out.append('" ' if q == '"' else "' ")
                i += 1
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        # 正常态
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if c == "/" and nxt == "*":
            in_block = True
            i += 2
            out.append("  ")
            continue
        if c == '"':
            in_str = True
            quote = '"'
            out.append('" ')
            i += 1
            continue
        if c == "'":
            in_chr = True
            quote = "'"
            out.append("' ")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def split_top_level_args(s: str) -> list[str]:
    """按**顶层**逗号切参数实参（跳过括号内/嵌套<>内/字符串内的逗号）。"""
    args: list[str] = []
    depth = 0
    cur = []
    for c in s:
        if c in "(<[":
            depth += 1
            cur.append(c)
        elif c in ")>]":
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
    tail = "".join(cur).strip()
    if tail:
        args.append(tail)
    return args


CODE_LIKE_RE = re.compile(
    r'^(?:"|[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*$)'
)
def is_code_like(arg: str) -> bool:
    """第 3 实参是否「业务码」而非 Throwable/cause。

    合法的业务码形态：字符串字面量、``XxxErrorCodes.CONST``、形如 ``errorCode`` 的变量。
    排除显式 ``(Throwable) null`` 强转与 ``cause``/``e``/``ex`` 这类 Throwable 变量名。
    """
    a = arg.strip()
    if not a:
        return False
    if "Throwable" in a:  # (Throwable) null 强转
        return False
    if not CODE_LIKE_RE.match(a):
        return False
    if a in ("null", "cause", "e", "ex", "t", "throwable"):
        return False
    # 形如 xxxErrorCode / ErrorCodes.CONST 视为业务码
    return ("ErrorCode" in a) or ("errorCode" in a) or a.startswith('"')


def classify_new(seg: str) -> tuple[bool, str]:
    """判定 ``new BizException(...)`` 是否合规。返回 (是否违规, 形态描述)。"""
    args = split_top_level_args(seg)
    argc = len(args)
    if argc >= 3 and is_code_like(args[2]):
        return False, f"new BizException({argc}参, 带errorCode)"
    if argc >= 3:
        return True, f"new BizException({argc}参, 第3参非业务码 ⇒ errorCode=null)"
    if argc == 2:
        return True, "new BizException(2参) ⇒ errorCode=null"
    return True, "new BizException(1参) ⇒ errorCode=null"


def extract_after(text: str, start: int) -> str | None:
    """从 '(' 处取出配平的实参文本（跳过字符串字面量里的括号）。"""
    depth = 0
    for i in range(start, len(text)):
        c = text[i]
        if c == '"':
            i += 1
            while i < len(text) and text[i] != '"':
                if text[i] == "\\":
                    i += 1
                i += 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1 : i]
    return None


def scan():
    import os

    strict: list[dict] = []
    advisory: list[dict] = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for dirpath, _dirs, files in os.walk(base):
            dp = Path(dirpath)
            if "target" in dp.parts or "test" in dp.parts:
                continue
            for fn in files:
                if not fn.endswith(".java"):
                    continue
                if EXEMPT_NAME_RE.search(fn):
                    continue
                path = dp / fn
                rel = str(path.relative_to(REPO))
                is_strict = any(m in "/" + rel for m in STRICT_MARKERS)
                bucket = strict if is_strict else None
                src = path.read_text(encoding="utf-8", errors="ignore")
                code = strip_code_keep_lines(src)
                # 记录每行偏移，便于把「剥注释后」的行号映射回原文
                for m in BIZEX_NEW_RE.finditer(code):
                    seg = extract_after(code, m.end() - 1)
                    if seg is None:
                        continue
                    bad, form = classify_new(seg)
                    if not bad:
                        continue
                    row = {
                        "file": rel,
                        "line": code[: m.start()].count("\n") + 1,
                        "form": form,
                    }
                    (bucket if bucket is not None else advisory).append(row)
                for m in BIZEX_OF_RE.finditer(code):
                    row = {
                        "file": rel,
                        "line": code[: m.start()].count("\n") + 1,
                        "form": "BizException.of(...) ⇒ errorCode=null（9个重载全丢码）",
                    }
                    (bucket if bucket is not None else advisory).append(row)
    strict.sort(key=lambda r: (r["file"], r["line"]))
    advisory.sort(key=lambda r: (r["file"], r["line"]))
    return strict, advisory


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--update-baseline", action="store_true")
    args = ap.parse_args()

    strict, advisory = scan()
    total = len(strict) + len(advisory)

    if args.update_baseline:
        BASELINE_PATH.write_text(
            json.dumps(
                {
                    "note": "存量基线，只可收缩。修复后应移除对应条目。",
                    "rule": "application/ 与 adapter/web/ 内业务异常必须携带 errorCode",
                    "strictViolations": strict,
                    "advisoryViolations": advisory,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        print(f"baseline 已更新：严格 {len(strict)} 条/ 仅报告 {len(advisory)} 条")
        return 0

    baseline_entries: set[str] = set()
    if BASELINE_PATH.exists():
        data = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
        baseline_entries = {
            f"{e['file']}::{e['line']}" for e in data.get("strictViolations", [])
        }

    new_v = [r for r in strict if f"{r['file']}::{r['line']}" not in baseline_entries]
    baseline_only = [r for r in strict if f"{r['file']}::{r['line']}" in baseline_entries]
    live = {f"{r['file']}::{r['line']}" for r in strict}
    stale = sorted(baseline_entries - live)

    print("=" * 72)
    print("业务异常落点门禁（errorCode 不得为 null）")
    print(f"  违规总数: {total}")
    print(
        f"  严格层 application/ + adapter/web/: {len(strict)}"
        f"（基线 {len(baseline_only)} / 新增 {len(new_v)}）"
    )
    print(f"  仅报告 domain/ + infrastructure/: {len(advisory)}（逃生舱，不阻断）")
    print("=" * 72)

    if stale:
        print(f"\n⚠️baseline 中已无对应代码（应移除，属基线收缩）: {len(stale)} 条")
        for s in stale[:10]:
            print(f"    {s}")

    if new_v:
        print(f"\n❌ 发现 {len(new_v)} 个【新增】无业务码违规（不得加入 baseline）:")
        for r in new_v:
            print(f"    {r['file']}:{r['line']}")
            print(f"      {r['form']}")
        print("\n修复方式：改用本模块 {Module}Errors.of({Module}ErrorCodes.XXX, 上下文[, cause])；")
        print("         确属技术失败无业务语义（仅 infrastructure）→ 登记为仅报告，不阻断。")
        if not args.report_only:
            return 1

    if not new_v:
        print("\n✅ 严格层新增违规为 0（存量基线只可收缩）")

    return 0


if __name__ == "__main__":
    sys.exit(main())