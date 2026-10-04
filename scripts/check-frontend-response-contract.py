#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁：前端 API 客户端「响应拦截器已解包」契约守卫。

背景（2026-10-03 实测）
----------------------
`packages/shared-services/src/apiClient.ts` 的响应拦截器是::

    instance.interceptors.response.use((response) => response.data, ...)

即业务代码拿到的**已经是 `ApiResponse` 本体**（`{success, code, message, data}`），
不再是 `AxiosResponse`。但 axios 的 `get<T>()` 声明返回 `Promise<AxiosResponse<T>>`，
于是「用单泛型 `get<ApiResponse<X>>()` 标注、却按解包后的形状消费」是 type lie；
一旦照着 `AxiosResponse` 的形状写代码，就产生**静默运行时 bug**。

实测两个真实缺陷（`apps/bone-extension-app`，2026-10-03 已修）：

1. `deleteExtPoint` / `deletePlugin` 写 ``if (res.status === 204)`` +
   ``if (res.data?.success === false) throw``：
   `res.status` 恒 `undefined`（status 在拦截器那层就被丢掉），
   `res.data?.success` 也恒 `undefined`（body 本身就是 ApiResponse，再取 `.data` 是双重解包）
   → **删除失败被静默吞成成功**。
2. `downloadPluginVersion` 写 `const blob = res.data`
   → `URL.createObjectURL(undefined)` 直接抛 `TypeError`，**JAR 下载功能不可用**。

这两处 typecheck 会报 TS2345/TS18046，但**语义正确与否无法从类型系统判断**，
故需要本门禁按「是否用了解包前的字段形状」来拦。

规则（刻意极窄，避免误报）
--------------------------
只报**同时满足**两个条件的行：

* **R1 HTTP 状态误判**：``<var>.status`` 与**数字字面量**比较（HTTP 语义），
  且该行所在文件**未经裸 axios**（无解包拦截器），且变量不是 `error.response`
  （后者是 `AxiosError`，未被解包）。
  —— 判别关键在「与数字比」而非「出现 .status」：仓库里 52 处 `.status` 是与
  `'PENDING'`/`'PUBLISHED'` 等**领域枚举**比较（治理工单、模板状态），完全合法；
  只有与 204/2xx 比较才是把业务字段错当 HTTP 状态。

* **R2 双重解包**：在 ``.data`` **之上再取信封字段**，即
  ``res.data.success`` / ``res.data.message` / `res.data.code` 这类
  ``.data.<信封字段>`` 形态；或 `(await api.get(...)).data` 链式取值。
  —— 判别关键在「`.data` 后是否跟信封字段」：`response.data` 取 payload 是
  **完全正确**的（ApiResponse 契约下 payload 就在 `.data`），仓库里上百处都是
  正确用法，宽泛匹配会误报一片；而 `.data.success` 才是真的多解了一层。

豁免
----
* 测试文件（`.test.*` / `.spec.*` / `__tests__`）：需直接构造原始响应。
* 裸 axios（`axios.get(...)` / `axios.create(...)`）：无解包拦截器，`.status`/`.data` 均合法。
* `error.response.status`：错误分支拿的是 `AxiosError.response`，**未**被解包（合法）。
* 领域枚举比较（`.status === 'PENDING'` 等）：业务字段，非 HTTP 状态。

用法::

    python3 scripts/check-frontend-response-contract.py            # 阻断
    python3 scripts/check-frontend-response-contract.py --report-only  # 恒 exit 0
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
FRONTEND = REPO_ROOT / "bone-frontend"
APPS_DIR = FRONTEND / "apps"

# R1：`<var>.status` 与数字比较（HTTP 语义）。字符串字面量 = 领域枚举，放行。
STATUS_NUMERIC_RE = re.compile(
    r"\b(?P<var>[A-Za-z_$][\w$]*)\s*\.\s*status\b\s*(?:===|!==|==|!=)\s*(?P<num>\d{3})"
)

# 变量名白名单：这些是业务对象（治理工单 r、领域记录 raw 等），不是 HTTP 响应
DOMAIN_VARS = {
    "r", "row", "rows", "item", "record", "raw", "d", "obj", "o",
    "task", "issue", "tpl", "template", "entity", "field", "job", "run",
    "rule", "result", "data", "payload", "body", "model", "detail", "info",
}

# R2：`.data` 之上再取**信封字段** = 真的多解了一层。
# 这是双重解包的精确特征：`.data.success` / `.data.message` / `.data.code`。
# 注意 `response.data`（取 payload）本身是正确用法，绝不能报。
ENVELOPE_FIELDS = "success|message|code|error|data|timestamp|error\\w*"
# 允许 `.data` 与下一个字段之间夹 TypeScript 类型断言/非空/括号收窄：
#   res.data?.success
#   (res.data as {data: Blob}).data
#   (res.data as ApiResponse<X>).message
_TYPE_NOISE = r"(?:\s*(?:\?\.|as\s+(?:unknown\s+as\s+)?\{[^}]*\}|\{[^}]*\}|readonly\s+\w+)|\s*\))*\s*\??\s*\.\s*"
DOUBLE_UNWRAP_RE = re.compile(
    r"\b[A-Za-z_$][\w$]*\s*\.\s*data" + _TYPE_NOISE + r"(?:" + ENVELOPE_FIELDS + r")\b"
)
# 链式：`(await api.get(...)).data.success` 之类
UNWRAP_DIRECT_RE = re.compile(
    r"\(\s*await\s+[A-Za-z_$][\w$.]*\.(?:get|post|put|delete|patch)\s*(?:<[^;]{0,300}?>)?\s*\([^;]{0,400}?\)\s*\)"
    r"\s*\.\s*data" + _TYPE_NOISE + r"(?:" + ENVELOPE_FIELDS + r")\b"
)

# 变量 ← `<错误对象>.response`：AxiosError 分支，其 .status 未被解包，合法
ERROR_RESPONSE_ASSIGN_RE = re.compile(
    r"\b(?:const|let|var)\s+(?P<var>[A-Za-z_$][\w$]*)\s*(?::[^=]+)?=\s*"
    r"(?:error|err|e|\w*[Ee]rror)\s*\.\s*response\b"
)

# ApiResponse 契约提示：用于 R2 判定「这个文件确实在用解包契约」
UNWRAP_HINT_RE = re.compile(r"ApiResponse|assertSuccess\s*\(|isOk\s*\(|createApiClient")

RAW_AXIOS_RE = re.compile(
    r"(?<![\w.])axios\s*\.\s*(?:get|post|put|delete|patch)\s*(?:<[^;]{0,200}?>)?\s*\(|"
    r"(?<![\w.])axios\s*\.\s*create\s*\("
)
CREATE_API_CLIENT_RE = re.compile(r"createApiClient\s*\(")

TEST_PATH_RE = re.compile(r"(\.test\.|\.spec\.|__tests__|/tests?/)")


def is_test_file(path: Path) -> bool:
    return bool(TEST_PATH_RE.search(path.as_posix()))


def iter_source_files():
    for app_dir in sorted(APPS_DIR.iterdir()):
        if not app_dir.is_dir():
            continue
        src = app_dir / "src"
        if not src.is_dir():
            continue
        for path in sorted(src.rglob("*")):
            if path.suffix not in (".ts", ".tsx"):
                continue
            if "node_modules" in path.parts or is_test_file(path):
                continue
            yield path


def strip_comments_and_strings(line: str) -> str:
    """去掉 // 行注释、块注释残留与字符串字面量，避免注释里的示例被当成真实命中。"""
    out = []
    i, n = 0, len(line)
    while i < n:
        ch = line[i]
        if ch == "/" and i + 1 < n and line[i + 1] == "/":
            break
        if ch in ("'", '"', "`"):
            quote = ch
            i += 1
            while i < n:
                if line[i] == "\\":
                    i += 2
                    continue
                if line[i] == quote:
                    i += 1
                    break
                i += 1
            out.append('""')
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def analyse(path: Path) -> tuple[list[tuple[int, str, str]], list[tuple[int, str]]]:
    """返回 (R1 违规, R2 违规)。"""
    raw_text = path.read_text(encoding="utf-8", errors="replace")
    lines = raw_text.splitlines()

    uses_raw_axios = bool(RAW_AXIOS_RE.search(raw_text))
    uses_unwrap_contract = bool(UNWRAP_HINT_RE.search(raw_text))

    r1: list[tuple[int, str, str]] = []
    r2: list[tuple[int, str]] = []

    # 预扫描：记录「变量 ← 某对象的 .response」的错误分支变量（如
    # `const response = e.response; if (response.status === 403)`）。
    # 这些变量的 .status 是 AxiosError.response.status，**未**被解包，读它正确。
    error_branch_vars: set[str] = set()
    for raw_line in lines:
        m = ERROR_RESPONSE_ASSIGN_RE.search(strip_comments_and_strings(raw_line))
        if m:
            error_branch_vars.add(m.group("var"))

    for lineno, raw_line in enumerate(lines, start=1):
        line = strip_comments_and_strings(raw_line)
        if not line.strip():
            continue

        # ---- R1：HTTP 状态误判 ----
        if not uses_raw_axios:
            for m in STATUS_NUMERIC_RE.finditer(line):
                var, num = m.group("var"), m.group("num")
                if var in DOMAIN_VARS:
                    continue
                # 变量来自错误分支的 `.response`（AxiosError，未被解包）→ 合法
                if var in error_branch_vars:
                    continue
                if re.search(r"\b(?:error|err|e)\s*\.\s*response\s*\.\s*status\s*(?:===|!==|==|!=)\s*" + num, line):
                    continue
                r1.append((lineno, raw_line.strip(), f"{var}.status === {num}"))

        # ---- R2：双重解包（.data 之上再取信封字段） ----
        if uses_unwrap_contract and not uses_raw_axios:
            if UNWRAP_DIRECT_RE.search(line) or DOUBLE_UNWRAP_RE.search(line):
                r2.append((lineno, raw_line.strip()))

    return r1, r2


def main() -> int:
    ap = argparse.ArgumentParser(description="前端响应解包契约守卫")
    ap.add_argument("--report-only", action="store_true", help="只报告，不阻断（恒 exit 0）")
    args = ap.parse_args()

    if not APPS_DIR.is_dir():
        print(f"[SKIP] 未找到前端目录：{APPS_DIR}")
        return 0

    r1_all: list[tuple[Path, int, str, str]] = []
    r2_all: list[tuple[Path, int, str]] = []
    scanned = 0

    for path in iter_source_files():
        scanned += 1
        r1, r2 = analyse(path)
        r1_all.extend((path, ln, line, why) for ln, line, why in r1)
        r2_all.extend((path, ln, line) for ln, line in r2)

    print(f"扫描 {scanned} 个前端源文件（已排除测试与 node_modules）")

    failed = False

    if r1_all:
        failed = True
        print(f"\n[R1] ❌ 把响应的 `.status` 当 HTTP 状态用（拦截器已丢弃它，恒 undefined）：{len(r1_all)} 处")
        for path, lineno, line, why in r1_all:
            print(f"     {path.relative_to(REPO_ROOT)}:{lineno}  [{why}]  {line[:100]}")
        print("     ↳ 解包拦截器是 `(response) => response.data`，HTTP status 在那层就没了。")
        print("     ↳ 判 HTTP 状态请在拦截器 error 分支读 `error.response.status`；")
        print("       业务状态（工单/模板/任务）请用与字符串枚举比较，不受本规则影响。")

    if r2_all:
        failed = True
        print(f"\n[R2] ❌ 对已解包的 ApiResponse 再取 `.data`（双重解包）：{len(r2_all)} 处")
        for path, lineno, line in r2_all:
            print(f"     {path.relative_to(REPO_ROOT)}:{lineno}  {line[:100]}")
        print("     ↳ 业务代码拿到的就是 `{success, code, message, data}`，payload 在 `.data`，")
        print("       再取一次 `.data` 会得到 undefined。")
        print("     ↳ Blob 下载尤其致命：`URL.createObjectURL(undefined)` 直接抛 TypeError。")

    if failed:
        print("\n❌ 前端响应解包契约校验失败。详见 doc/architecture/bone-前端架构.md §6.1.1 响应解包契约。")
        return 0 if args.report_only else 1

    print("\n✅ 前端响应解包契约校验通过（R1/R2 无违规）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
