#!/usr/bin/env python3
"""分页 `total` 归一门禁（R3）。

## 为什么需要这道门禁

后端 `PageResult.total` 是 `java.lang.Long`，被骨核全局 `Long` → String 序列化器
（`MetadataAutoConfiguration.boneLongToStringCustomizer`）输出为 **JSON 字符串**
（如 `"1055"`），以保护雪花 ID 不被 JS Number 截断。根因见
`doc/architecture/Bone-API-规范.md` §5.3。

后果：`total` 一旦参与算术即崩 —— JS 中 `'1055' / 10` 抛 `TypeError`（无隐式转换）。
典型触发：列表页翻到最后一页、删掉该页最后一条 → 翻页回退 `Math.ceil(total / pageSize)` 崩溃。

## 为什么 TypeScript 抓不到全部

`PageResult.total` 已诚实声明为 `string | number`，TS 只在**显式标注为 number 的位置**
（`setTotal(x: number)`）报错。以下两类漏点 TS 完全看不见：

- `total` 传入 antd `<Statistic value={total} />` —— 渲染正常（React 会字符串化），
  但任何后续算术/比较即崩；
- 中间变量 `const t = res.data.total; setTotal(t)` —— 类型被推断为联合类型，逃过检查。

⇒ 需要静态扫描「把后端 total 直接喂给 number 语境」的形态。

R1/ R2 由 `check-frontend-response-contract.py` 负责，本脚本只管 total 归一。

用法：
    python3 scripts/check-paging-total-normalize.py
    python3 scripts/check-paging-total-normalize.py --report-only   # 恒exit 0
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
APPS_DIR = REPO_ROOT / "bone-frontend" / "apps"

# 合法的 total 归一形态：显式调用 normalizeTotal / Number / unary +
NORMALIZED = r"(?:normalizeTotal\s*\(|Number\s*\(|\+\s*[\w.\[\]?]+\s*[,;)\s]|\?\?\s*0)"

# R3-1：把 `.total`（或 `data.total` / `pageData.total`）直接塞进 number 语境。
# 覆盖 setX(total) / value={total} / total: xxx 这三类最常见落点。
R3_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        "setState 未归一（R3-1）",
        re.compile(
            r"\bset[A-Z]\w*\s*\(\s*"
            r"(?:[\w$]+\s*\.\s*)?data\s*\??\.\s*total\b"
            r"(?!\s*\)" + NORMALIZED + r")"  # 归一后紧跟 ) 属合法
        ),
    ),
    (
        "number 字段赋值未归一（R3-2）",
        re.compile(
            r"\btotal\s*:\s*"
            r"(?:[\w$]+\s*\.\s*)?data\s*\??\.\s*total\b"
            r"(?!\s*[,}]\s*" + NORMALIZED + r")"
        ),
    ),
    (
        "antd Statistic/分页 value 未归一（R3-3）",
        re.compile(
            r"\bvalue\s*=\s*\{(?:[\w$]+\s*\.\s*)?data\s*\??\.\s*total\b"
        ),
    ),
    (
        "total 参与算术/比较未归一（R3-4）",
        re.compile(
            r"(?:(?:[\w$]+\s*\.\s*)?data\s*\??\.\s*total|"
            r"\btotal)\s*(?:[+\-*/%]|//)"
        ),
    ),
]

# 已知豁免：本地自增的累加器（与后端 total 无关）+ 已归一/兜底写法
EXEMPT_PATTERNS: list[re.Pattern[str]] = [
    # `let total = 0; ... total += 1;` 树形统计累加器（OrganizationManagement/MenuManagement 实测形态）
    re.compile(r"\btotal\s*\+=\s*1\b"),
    # 恒等初始化 `let total = 0`
    re.compile(r"\blet\s+total\s*=\s*0\b"),
    # 已是 normalizeTotal(...) 包裹
    re.compile(r"normalizeTotal\s*\("),
    # 显式 Number(...) 包裹
    re.compile(r"\bNumber\s*\("),
    # `?? 0` 空值兜底 —— 仓库既有惯用法（不追求类型严谨时用它），豁免以免误报
    re.compile(r"\?\?\s*0\b"),
]

LINE_COMMENT_RE = re.compile(r"//.*$")


def strip_comments(src: str) -> str:
    """去注释但**保留行结构**（逐行状态机，正确处理跨行块注释）。

    两个必须避开的坑（均为实测踩过）：
    1. 不可用 `re.S` 配`/\\*.*?\\*/` —— `.*`跨行会从「第一个 /*」吞到「最后一个 */」，
       把整个文件清空（7 行探针文件被 strip 成 0 行，门禁全绿假阴性）。
    2. 不可逐行独立 `re.sub` —— 块注释起始行在第 N 行时，第 N+1 行起的注释体
       不含 `/*`，会被当代码扫描，误报 javadoc 里的 `Math.ceil(total / pageSize)`。
    故用状态机：`in_block` 跨行延续，行数一一对应，报错行号才准确。
    """
    out: list[str] = []
    in_block = False
    for line in src.splitlines():
        if in_block:
            end = line.find("*/")
            if end == -1:
                out.append("")
                continue
            line = line[end + 2 :]
            in_block = False
        # 同一行内可能有 /* ... */ 完整块，也可能有 /* 开启的跨行块
        while True:
            start = line.find("/*")
            if start == -1:
                break
            end = line.find("*/", start + 2)
            if end == -1:
                line = line[:start]
                in_block = True
                break
            line = line[:start] + line[end + 2 :]
        out.append(LINE_COMMENT_RE.sub("", line))
    return "\n".join(out)


def is_exempt(line: str) -> bool:
    return any(p.search(line) for p in EXEMPT_PATTERNS)


# 声明了 `NormalizedPageResult` 返回值的 API 方法 —— 这些方法已在 api 层归一，
# 其返回值的 `.data.total` 运行期就是 number，页面侧直接读取是安全的。
NORMALIZED_API_RE = re.compile(
    r"\b[\w$.]+\s*\([^)]*\)\s*:\s*Promise<\s*ApiResponse<\s*NormalizedPageResult"
)
# 调用这些方法得到的变量名
NORMALIZED_VAR_RE = re.compile(
    r"(?:const|let)\s*[\w{},\s]+?=\s*await\s*([\w$.]+)\s*\("
)


def collect_normalized_vars(src: str) -> set[str]:
    """收集「已归一 API 调用结果」的变量名/解构名。

    静态判据：变量由一个「返回类型标注含 NormalizedPageResult 的方法」调用得来。
    这是把类型系统的信息搬到静态扫描里 —— 否则页面侧二次读取 `res.data.total`
    会被误报（实测 DomainWorkbench:113/114、RecordManagement:130 即此形态，
    它们读的是 `masterDataRecordApi.page()` 的返回值，api 层已归一）。
    """
    # 形如 `masterDataRecordApi: {` 的对象里，哪些方法返回 NormalizedPageResult
    safe_methods: set[str] = set()
    for m in NORMALIZED_API_RE.finditer(src):
        # 回溯到方法名 `page: async (...)`
        head = src[: m.start()]
        name_m = re.search(r"(\w+)\s*:\s*(?:async\s*)?\([^)]*\)\s*:\s*Promise<\s*ApiResponse<\s*$", head)
        if name_m:
            safe_methods.add(name_m.group(1))

    if not safe_methods:
        return set()

    # 找 `const x = await <api>.<safeMethod>(...)` / 解构 `[a, b] = await ...`
    safe_vars: set[str] = set()
    for m in re.finditer(
        r"(?:const|let)\s*(\{[^}]+\}|\[\s*[^\]]+\]|\w+)\s*=\s*await\s*[\w$.]+\.(\w+)\s*\(",
        src,
    ):
        if m.group(2) in safe_methods:
            for name in re.findall(r"\w+", m.group(1)):
                safe_vars.add(name)

    # `const [rec, pub, ...] = await Promise.all([...])` —— 保守起见不推断
    return safe_vars


def scan(files: list[pathlib.Path]) -> list[tuple[pathlib.Path, int, str, str]]:
    findings: list[tuple[pathlib.Path, int, str, str]] = []
    for path in files:
        raw = path.read_text(encoding="utf-8", errors="replace")
        src = strip_comments(raw)
        safe_vars = collect_normalized_vars(src)
        for lineno, line in enumerate(src.splitlines(), 1):
            if "total" not in line:
                continue
            if is_exempt(line):
                continue
            # 读的是已归一变量的 .data.total ⇒ 安全
            if any(re.search(rf"\b{re.escape(v)}\s*\??\.\s*data\s*\??\.\s*total\b", line) for v in safe_vars):
                continue
            for label, pattern in R3_PATTERNS:
                if pattern.search(line):
                    findings.append((path, lineno, label, line.strip()[:140]))
                    break
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="分页 total 归一门禁")
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="只报告不阻断（恒 exit 0）",
    )
    args = parser.parse_args()

    if not APPS_DIR.is_dir():
        print("ℹ️未找到 bone-frontend/apps，跳过")
        return 0

    files = [
        p
        for p in APPS_DIR.glob("*/src/**/*.ts*")
        if p.suffix in {".ts", ".tsx"}
        and ".test." not in p.name
        and "node_modules" not in p.parts
    ]
    if not files:
        print("ℹ️ 无待扫描文件，跳过")
        return 0

    findings = scan(files)
    rel = lambda p: p.relative_to(REPO_ROOT)  # noqa: E731

    if not findings:
        print(f"扫描 {len(files)} 个前端源文件（已排除测试与 node_modules）")
        print("\n✅ 分页 total 归一校验通过（R3 无违规）")
        return 0

    print(f"扫描 {len(files)} 个前端源文件（已排除测试与 node_modules）\n")
    by_label: dict[str, list] = {}
    for path, lineno, label, text in findings:
        by_label.setdefault(label, []).append((path, lineno, text))

    print("❌ 分页 total 归一违规：\n")
    for label, items in sorted(by_label.items()):
        print(f"── {label}（{len(items)} 处）")
        for path, lineno, text in items:
            print(f"   {rel(path)}:{lineno}")
            print(f"     {text}")
        print()

    print("修复方式：在该收敛点包一层归一，不要改后端 total 类型。")
    print("  import { normalizeTotal } from '@bone/shared-utils';")
    print("  setTotal(normalizeTotal(res.data.total));")
    print("规范依据：doc/architecture/Bone-API-规范.md §5.3")
    print("（后端 total 保持 java.lang.Long，全局 Long→String 序列化器不可动）")

    if args.report_only:
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
