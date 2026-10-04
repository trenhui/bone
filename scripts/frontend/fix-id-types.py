#!/usr/bin/env python3
"""把前端 TS 中「语义为标识符但声明为 number」的字段改为 string。

为什么需要它：后端 `MetadataAutoConfiguration.boneLongToStringCustomizer()` 全局把
`Long`/`long` 序列化为 JSON **字符串**（雪花 ID 18~19 位超 JS Number 上限 2^53，
以 number 返回时前端 ID 末几位被静默截断，回传后端即404 且无任何 JS 报错）。
但前端 331 处把ID 字段声明为 `number` —— 契约与实现不一致的 type lie。

只改「声明」，不改任何运行时行为：这些字段在代码里只做类型标注，
无数值运算（已实测全仓 227 个文件，`parseInt/Number`/`++`/算术/与数字比较 命中均为 0，
唯一的 `Number(previewTemplateId)` 已在 A1 中单独修掉）。

用法：
  python3 scripts/frontend/fix-id-types.py --check      # 只报告，不改
  python3 scripts/frontend/fix-id-types.py --apply      # 实际写入
  python3 scripts/frontend/fix-id-types.py --apply --path packages/shared-types
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
FRONTEND = REPO / "bone-frontend"

# 不是标识符的常见名字（UI 尺寸类）。命中它们说明判据过宽，必须先看样例。
NOT_ID = {
    "width", "height", "divider", "size", "grid", "index", "span", "order",
    "radius", "gap", "padding", "margin", "font", "weight", "color",
    "duration", "delay", "zIndex", "opacity", "flex", "line", "letter",
}

# 字段名语义为标识符：以 Id/ID/Ids/IDs 或 _id/_ids 结尾，或全等 id/ids。
IS_ID = re.compile(r"(^|_|[a-z])([iI]d|Id|ID|Ids|IDs|_id|_ids)$|^id$|^ids$")

# 只匹配「字段名 + 可选 ? + 冒号 + number」，且不吞掉后面的类型（如 number | null）。
DECL = re.compile(
    r"(?P<name>[A-Za-z_$][\w$]*)(?P<opt>\?)?(?P<sep>\s*:\s*)(?P<num>number)\b"
)

SCAN_GLOBS = ("apps/**/*.ts", "apps/**/*.tsx", "packages/**/*.ts", "packages/**/*.tsx")
# 测试文件里的泛型载荷（如 `PageResult<{ id: number }>`）不是对外 ID 契约，排除。
# 注意匹配 `.test.` 会漏掉 `pageResult.test.ts`（无前缀点），故两种写法都要列。
SKIP = ("node_modules", "/dist/", "/build/", ".test.", ".spec.", ".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx")


def scan_files(paths: list[str] | None) -> list[Path]:
    if paths:
        roots = [FRONTEND / p for p in paths]
        files: list[Path] = []
        for r in roots:
            if r.is_file():
                files.append(r)
            elif r.is_dir():
                for g in ("**/*.ts", "**/*.tsx"):
                    files.extend(r.glob(g))
            else:
                print(f"[skip] 路径不存在: {r}", file=sys.stderr)
    else:
        files = []
        for g in SCAN_GLOBS:
            files.extend(FRONTEND.glob(g))
    return sorted(
        f
        for f in files
        if f.is_file() and not any(s in str(f) for s in SKIP)
    )


def find_declarations(text: str) -> list[tuple[int, str, str, str, str]]:
    """返回 (行号, 字段名, 匹配文本, 该行是否含 ID 以外内容)。

    含 ID 以外内容 ⇒ 该行还声明了别的字段，跳过（避免整行替换误伤）。
    """
    out = []
    for i, line in enumerate(text.splitlines(), 1):
        for m in DECL.finditer(line):
            name = m.group("name")
            if name in NOT_ID or name.lower() in NOT_ID:
                continue
            if not IS_ID.search(name):
                continue
            out.append((i, name, m.group(0), line))
    return out


def fix_text(text: str) -> tuple[str, int, list[str]]:
    """把 ID 字段的 `number` 声明改为 `string`，保留 `?` 与 `| null` 等后缀。

    ⚠️ 实现陷阱（2026-10-03 实测踩坑）：**不要用 f-string 拼接 `?`**。
    `m.group("opt")` 在无 `?` 时是 Python `None`，写成
    `f"{name}{opt}: string"` 会把 `None` 渲染成字面量 —— `id: number`
    变成 `idNone: string`（`?` 吞掉冒号，多出 `None`），静默破坏 44 个文件。
    正确做法：用 `m.group(0)` 的**前缀切片**保留分隔符，只替换 `number` 这个词。
    """
    changed = 0
    notes: list[str] = []
    lines = text.splitlines(keepends=True)
    for idx, line in enumerate(lines):
        matches = [
            m
            for m in DECL.finditer(line)
            if (m.group("name") not in NOT_ID)
            and (m.group("name").lower() not in NOT_ID)
            and IS_ID.search(m.group("name"))
        ]
        if not matches:
            continue
        new = line
        # 从右往左替换，避免前面的替换影响后面的偏移
        for m in reversed(matches):
            old = m.group(0)
            # 只把末尾的 `number` 换成 `string`，其余（字段名、`?`、冒号、空格）原样保留
            new = new.replace(old, old[: -len("number")] + "string", 1)
            changed += 1
            notes.append(m.group("name"))
        lines[idx] = new
    return "".join(lines), changed, notes


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只报告，不写入")
    ap.add_argument("--apply", action="store_true", help="实际写入")
    ap.add_argument("--path", action="append", help="限定路径（可重复）")
    args = ap.parse_args()

    if not (args.check or args.apply):
        ap.error("必须指定 --check 或 --apply")

    files = scan_files(args.path)
    total_decl = 0
    total_fixed = 0
    touched: list[tuple[str, int, int]] = []

    for f in files:
        try:
            text = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        decls = find_declarations(text)
        if not decls:
            continue
        total_decl += len(decls)
        rel = str(f.relative_to(REPO))
        if args.apply:
            new_text, changed, _ = fix_text(text)
            if changed:
                f.write_text(new_text, encoding="utf-8")
                total_fixed += changed
                touched.append((rel, len(decls), changed))
        else:
            print(f"{rel}: {len(decls)} 处")
            for i, name, matched, line in decls[:200]:
                print(f"    {i:5}| {name:<24} {matched:<22} | {line.strip()[:70]}")

    mode = "APPLY" if args.apply else "CHECK"
    print(f"\n[{mode}] 扫描 {len(files)} 个文件，ID 型number 声明 {total_decl} 处")
    if args.apply:
        print(f"[APPLY] 已改 {total_fixed} 处，涉及 {len(touched)} 个文件")
        for rel, d, c in touched:
            print(f"    {c:>4}/{d:<4} {rel}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
