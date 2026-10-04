#!/usr/bin/env python3
"""前端 ID 字段类型门禁：语义为标识符的字段不得声明为 `number`，必须 `string`。

为什么需要它
------------
后端 `MetadataAutoConfiguration.boneLongToStringCustomizer()`（`support/config/MetadataAutoConfiguration.java:60-67`）
全局注册 `serializerByType(Long.class / Long.TYPE, ToStringSerializer.instance)`，所有应用都 `@Import` 它，
是全平台唯一落点。其javadoc 写明动机：雪花 ID 18~19 位超出JS Number 上限 2^53，以JSON **number** 返回时
前端 ID 末几位被**静默截断**（758267976611790848 → ...800），回传后端即 404 且**无任何 JS 报错**。

⇒ **运行时后端所有 `Long` 字段下发的是 JSON 字符串**。前端若把 ID 声明为 `number`，就是type lie：
`===`、`Map` key、`Tree` `node.key`、路由参数拼接的行为全部不可预期，且编译器不报错。
实测（2026-10-03）HEAD 里曾有 **330 处**这样的声明，横跨 11 个包 —— 已全部修正为 `string`。
**修正后若无门禁，下次有人写新页面时会重新引入**，故建此门禁。

规则
----
1. 扫描 `bone-frontend/{apps,packages}/**/*.{ts,tsx}`（排除 `node_modules` / `dist` / 测试文件）。
2. 字段名语义为标识符（以 `Id`/`ID`/`Ids`/`IDs` 或 `_id`/`_ids` 结尾，或全等 `id`/`ids`）
   且类型标注为 `number` ⇒ **失败**。
3. 排除 UI 尺寸类名字（`width`/`height`/`size`/`index`/`span`/`offset` 等）—— 它们语义不是标识符。
4. 基线 `doc/architecture/frontend-id-type-baseline.json` 只可收缩：从基线移除一个条目后若它又出现 `number`，即失败。

例外（登记在基线 `exemptions` 中并写明理由）
-------------------------------------------
- 业务上确实是数值序号而非标识符的字段（需逐个写明，不能整体按目录豁免）。
- 测试文件里的泛型载荷占位（如 `PageResult<{ id: number }>`）—— 默认已按规则 1 排除 `*.test.ts`/`*.spec.ts`。

用法
----
  python3 scripts/frontend/check-frontend-id-types.py            # 报告
  python3 scripts/frontend/check-frontend-id-types.py --check    # 违例即失败（门禁用）
  python3 scripts/frontend/check-frontend-id-types.py --baseline # 把当前违例写入基线
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent.parent
FRONTEND = REPO / "bone-frontend"
BASELINE = REPO / "doc/architecture/frontend-id-type-baseline.json"

# UI 尺寸/布局类字段名：语义不是标识符。
# 收录标准：常见 CSS/图表/布局属性，或曾被误判为 ID 的短名。
NOT_ID = {
    "width", "height", "size", "grid", "span", "radius", "gap", "padding",
    "margin", "font", "weight", "color", "duration", "delay", "zIndex",
    "opacity", "flex", "letter", "line", "index", "order", "offset",
    "divider", "div", "col", "row", "tab", "step", "page", "level",
    "cell", "x", "y", "cx", "cy", "r", "w", "h", "count", "total",
}

# 字段名语义为标识符：驼峰或下划线风格的 Id/ID 结尾，或全等 id/ids。
IS_ID = re.compile(r"(?:[iI]d|Id|ID|Ids|IDs|_id|_ids)$")

# 字段标注：`name` `?` `:` `number`，只吃类型标注本身，便于精确替换与定位。
DECL = re.compile(
    r"(?P<name>[A-Za-z_$][\w$]*)(?P<opt>\?)?(?P<sep>\s*:\s*)(?P<num>number)\b"
)

SCAN_GLOBS = ("apps/**/*.ts", "apps/**/*.tsx", "packages/**/*.ts", "packages/**/*.tsx")
# `*.test.ts` / `*.spec.ts` 必须单独列出：`.test.` 这种带前缀点的写法匹配不到
# `pageResult.test.ts`（无前缀点），实测漏过一次。
SKIP = (
    "node_modules", "/dist/", "/build/", "/coverage/",
    ".test.", ".spec.", ".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx",
    "__tests__/", "/e2e/",
)


def iter_files() -> list[Path]:
    files: list[Path] = []
    for g in SCAN_GLOBS:
        files.extend(FRONTEND.glob(g))
    return sorted(
        f for f in files
        if f.is_file() and not any(s in str(f) for s in SKIP)
    )


def is_id_name(name: str) -> bool:
    if name in NOT_ID or name.lower() in NOT_ID:
        return False
    return name in ("id", "ids") or bool(IS_ID.search(name))


def scan(files: list[Path]) -> list[tuple[str, int, str, str]]:
    """返回 (相对路径, 行号, 字段名, 整行内容)。"""
    hits: list[tuple[str, int, str, str]] = []
    for f in files:
        try:
            text = f.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        rel = str(f.relative_to(REPO))
        for lineno, line in enumerate(text.splitlines(), 1):
            if "number" not in line:
                continue
            for m in DECL.finditer(line):
                name = m.group("name")
                if is_id_name(name):
                    hits.append((rel, lineno, name, line.strip()[:100]))
    return hits


def load_baseline() -> dict:
    if not BASELINE.is_file():
        return {"_comment": "", "exemptions": []}
    try:
        return json.loads(BASELINE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as ex:
        print(f"[ERROR] 基线不是合法 JSON: {BASELINE} — {ex}", file=sys.stderr)
        return {"_comment": "", "exemptions": []}


def exemption_keys(baseline: dict) -> set[tuple[str, str]]:
    return {
        (e["file"], e["field"])
        for e in baseline.get("exemptions", [])
        if "file" in e and "field" in e
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="违例即失败")
    ap.add_argument("--baseline", action="store_true", help="把当前违例写入基线")
    args = ap.parse_args()

    if args.check and args.baseline:
        ap.error("--check 与 --baseline 不能同时使用")

    files = iter_files()
    hits = scan(files)
    base = load_baseline()
    exempt = exemption_keys(base)

    real = [h for h in hits if (h[0], h[2]) not in exempt]
    skipped = [h for h in hits if (h[0], h[2]) in exempt]

    if args.baseline:
        payload = {
            "_comment": (
                "前端 ID 字段类型的例外登记（只可收缩）。exemptions 中每条须写明"
                "「为什么该字段确实是数值序号而非标识符」，不能按目录整体豁免。"
                "从本表移除一个条目后，若该字段又声明为 number，门禁即失败。"
                "规则依据：后端全平台 Long → JSON string（MetadataAutoConfiguration."
                "boneLongToStringCustomizer），雪花 ID 超 JS Number 上限 2^53。"
            ),
            "exemptions": [
                {"file": f, "field": n, "line": ln, "reason": "（请补写真实理由）"}
                for f, ln, n, _ in real
            ],
        }
        BASELINE.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"[BASELINE] 已写入 {len(real)} 条例外 → {BASELINE.relative_to(REPO)}")
        return 0

    print(f"扫描 {len(files)} 个前端文件")
    print(f"  ID 型 number 声明：{len(hits)} 处")
    if exempt:
        print(f"  其中已登记例外：{len(skipped)} 处")
    if not args.check:
        for f, ln, n, line in hits:
            tag = "(例外)" if (f, n) in exempt else "❌"
            print(f"  {tag} {f}:{ln}  {n}  | {line}")

    if real:
        by_file: dict[str, int] = {}
        for f, _, _, _ in real:
            by_file[f] = by_file.get(f, 0) + 1
        print(f"\n❌ {len(real)} 处 ID 字段声明为 number（应为 string）：")
        for f, c in sorted(by_file.items(), key=lambda x: -x[1]):
            print(f"    {c:>4}  {f}")
        print(
            "\n  依据：后端全平台把 Long 序列化为 JSON 字符串（雪花 ID 超 2^53，"
            "以number 返回会被静默截断）。修法：把该字段标注改为 `string`；"
            "若该字段确实是数值序号而非标识符，用 --baseline 登记例外并写明理由。"
        )
        if args.check:
            return 1
        return 0

    print("\n✅ 未发现 ID 型 number 声明")
    return 0


if __name__ == "__main__":
    sys.exit(main())
