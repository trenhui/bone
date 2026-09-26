#!/usr/bin/env python3
"""i18n 同步校验：后端 errorCode 常量 <-> 错误码台账 <-> 前端语言包 <-> 内置枚举清单。

设计依据: doc/design/国际化设计方案.md §8.2（v1.5）。

为什么用 python3 而不是 bash+rg:
    本机实测未安装 rg / jq（`command -v rg`、`command -v jq` 均无输出），shell 版接入
    scripts/check.sh 会直接 command not found；仓库既有门禁（check-sdk-persistence.py、
    check-doc-code-symbols.py 等十余个）已统一用 python3，跟它们保持一致最省事。

用法:
    python3 scripts/check-i18n-sync.py              # 阻断模式（缺译 → exit 1）
    python3 scripts/check-i18n-sync.py --report-only # 只出报告，恒 exit 0（存量清零阶段用）

校验项（编号与方案文档 §8.2.2 对应）:
    1  代码常量 ⊆ 台账 §6            🔴 阻断（新增码未登记）
    2  代码常量 ⊆ en-US.json errors   🔴 阻断
    3  代码常量 ⊆ zh-CN.json errors   🔴 阻断
    4  两份语言包 errors key 对称     🔴 阻断
    5  台账有、代码无（前瞻条目）      🟡 警告
    6  fallback.* 不混入 errorCode 形态 🟡 警告
    7  无裸 toLocaleString()          🟡 警告
    8  ApiResponse 顶层无 errorCode   🔴 阻断
    9  enums 覆盖率（内置枚举清单）    🔴 阻断（清单缺失时跳过并告警）
    10 禁止绕开取数入口拼接 enums key  🟡 警告

存量漂移处理（重要）:
    首期接通时后端有 100+ 存量码尚无译文，一次性全量翻译不现实。采用**显式白名单**
    config/i18n/errorcode-baseline.json：白名单内的码暂不参与阻断（不会隐式放行）。
    清零节奏见方案 §8.2.3「首次落地必做」。
"""

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SKIP = {"target", "node_modules", "dist", "build", ".git", "generated-code"}

LOCALE_DIR = "bone-frontend/packages/shared-utils/src/i18n/locales"
LEDGER = "doc/architecture/Bone-错误码登记.md"
DICT_CATALOG = "config/i18n/builtin-dicts.json"
BASELINE = "config/i18n/errorcode-baseline.json"
API_RESPONSE = "bone-framework/bone-core/src/main/java/com/bone/core/model/ApiResponse.java"

# 容忍多余空格/换行（bash+rg 版要求单空格，会漏掉对齐声明的常量）
CODE_RE = re.compile(r'public static final String\s+\w+\s*=\s*"([A-Z][A-Z0-9_]{2,})"')
# 只匹配 §6 台账表格的行首码列，避免把 §3.1 前缀表（| `IAM_` | ... |）抓成码
LEDGER_ROW_RE = re.compile(r"^\|\s*`([A-Z][A-Z0-9_]{2,})`\s*\|")
# errorCode 形态：全大写 + 下划线，用于识别"前端兜底文案被误放进 fallback 命名空间"
CODE_SHAPE_RE = re.compile(r"[A-Z][A-Z0-9_]{2,}")


def walk(suffixes, roots=None):
    """遍历仓库文件，跳过构建产物；roots 用于限定 apps/packages 等顶层目录。"""
    for p in REPO.rglob("*"):
        if not p.is_file() or p.suffix not in suffixes or (SKIP & set(p.parts)):
            continue
        if roots and not (roots & set(p.parts)):
            continue
        yield p


def code_codes():
    """代码侧：只扫 *ErrorCodes.java / *Errors.java（扫全仓会捞到 ADDRESS/EMAIL 等非码常量）。"""
    out = set()
    for p in walk({".java"}):
        if p.name.endswith("ErrorCodes.java") or p.name.endswith("Errors.java"):
            out |= set(CODE_RE.findall(p.read_text(encoding="utf-8", errors="ignore")))
    return out


def ledger_codes():
    """台账侧：限定在《Bone-错误码登记》§6 区间内取码。"""
    f = REPO / LEDGER
    if not f.exists():
        return set()
    lines = f.read_text(encoding="utf-8", errors="ignore").splitlines()
    try:
        start = next(i for i, l in enumerate(lines) if l.startswith("## 6."))
        stop = next(i for i, l in enumerate(lines[start + 1 :], start + 1) if l.startswith("## 7."))
    except StopIteration:
        return set()
    return {m.group(1) for l in lines[start:stop] if (m := LEDGER_ROW_RE.match(l))}


def baseline_codes():
    """存量白名单：**显式列出**，不允许隐式放行。

    untranslated —— 存量码尚无译文，暂不参与「语言包覆盖」阻断（清零后应为空）；
    unregistered —— 存量码未登记台账，暂不参与「代码 ⊆ 台账」阻断。
    """
    f = REPO / BASELINE
    if not f.exists():
        return set(), set(), False
    data = json.loads(f.read_text(encoding="utf-8"))
    return set(data.get("untranslated", [])), set(data.get("unregistered", [])), True


def error_keys(name):
    f = REPO / LOCALE_DIR / name
    if not f.exists():
        return None
    return set(json.loads(f.read_text(encoding="utf-8")).get("errors", {}) or {})


def load_locale(name):
    return json.loads((REPO / LOCALE_DIR / name).read_text(encoding="utf-8"))


def report(title, items):
    items = sorted(items)
    tail = " ..." if len(items) > 10 else ""
    print(f"❌ {title}（{len(items)} 条）: {' '.join(items[:10])}{tail}")


def grep_hits(suffixes, pattern, roots):
    rx = re.compile(pattern)
    return [
        f"{p.relative_to(REPO)}:{i}"
        for p in walk(suffixes, roots)
        for i, l in enumerate(p.read_text(encoding="utf-8", errors="ignore").splitlines(), 1)
        if rx.search(l)
    ]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--report-only",
        action="store_true",
        help="只出报告、恒退出 0（存量未清零阶段先跑这个，避免一开就阻断全部 PR）",
    )
    args = ap.parse_args()

    fail = 0
    codes = code_codes()
    ledger = ledger_codes()
    excluded, unregistered_baseline, has_baseline = baseline_codes()
    required = codes - excluded

    en, zh = error_keys("en-US.json"), error_keys("zh-CN.json")
    if en is None or zh is None:
        print(f"❌ 语言包不存在（P0-B 未完成）：{LOCALE_DIR}/en-US.json | zh-CN.json")
        return 0 if args.report_only else 1

    print(
        f"=== check-i18n-sync ===  代码常量 {len(codes)} / 台账 §6 {len(ledger)} "
        f"/ 白名单 {len(excluded)} / 待覆盖 {len(required)}"
    )
    if not has_baseline:
        print(f"⚠️ 未找到 {BASELINE}（无白名单，全部存量码都参与阻断）")

    if unregistered := (codes - ledger) - unregistered_baseline:
        report("有码未登记台账", unregistered)
        fail = 1
    if en_missing := required - en:
        report("en-US 缺 errorCode", en_missing)
        fail = 1
    if zh_missing := required - zh:
        report("zh-CN 缺 errorCode", zh_missing)
        fail = 1
    if en != zh:
        print(f"❌ en-US / zh-CN errors key 不对称（差 {len(en ^ zh)} 条）")
        fail = 1
    if phantom := ledger - codes:
        print(
            f"⚠️ 台账有、代码无（{len(phantom)} 条，确认是待实现还是该删）: "
            f"{' '.join(sorted(phantom)[:10])} ..."
        )

    if hits := grep_hits({".ts", ".tsx"}, r"\.toLocaleString\(", {"apps", "packages"}):
        print(f"⚠️ 仍有 toLocaleString，请改用 formatDate(): {hits[:5]}")

    for name in ("en-US.json", "zh-CN.json"):
        bad = [
            k
            for k in (load_locale(name).get("fallback") or {})
            if CODE_SHAPE_RE.fullmatch(k)
        ]
        if bad:
            print(f"⚠️ {name} 的 fallback.* 混入 errorCode 形态 key（应放 errors.*）: {bad[:5]}")

    if (REPO / API_RESPONSE).exists() and re.search(
        r"private\s+\w+\s+errorCode", (REPO / API_RESPONSE).read_text(encoding="utf-8")
    ):
        print("❌ ApiResponse 顶层不得含 errorCode（应放 ProblemDetail）")
        fail = 1

    df = REPO / DICT_CATALOG
    if df.exists():
        catalog = json.loads(df.read_text(encoding="utf-8"))
        miss = []
        for name in ("en-US.json", "zh-CN.json"):
            enums = load_locale(name).get("enums") or {}
            miss += [
                f"{name}:{d['type']}.{c}"
                for d in catalog
                for c in d.get("codes", [])
                if c not in (enums.get(d["type"]) or {})
            ]
        if miss:
            report("enums 缺译", miss)
            fail = 1
    else:
        print(f"⚠️ 未找到 {DICT_CATALOG}（P2 前置未完成），跳过 enums 覆盖率校验")

    if splice := grep_hits({".tsx"}, r"enums\.\$\{|`enums\.|\"enums\.", {"apps"}):
        print(f"⚠️ 存在绕开 useEnumLabel/EnumTag 的 enums key 拼接（§5.3）: {splice[:5]}")

    if args.report_only:
        print("ℹ️ --report-only：不阻断")
        return 0
    print("✅ check-i18n-sync 通过" if fail == 0 else "❌ check-i18n-sync 失败")
    return fail


if __name__ == "__main__":
    sys.exit(main())
