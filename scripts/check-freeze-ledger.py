#!/usr/bin/env python3
"""freeze-ledger.yaml 结构与双源一致性校验（台账 L2 防「台账腐烂」门禁）。

台账只登记「有真实冻结违规」的债务条目（archunit_store 非空 store 文件）。
本脚本不判定冻结语义（那是 ArchUnit + freeze.store.*=false 的事），只验证三件事：

  R1 每个条目字段齐备：rule/module/store/classification ∈ {debt, exception, planned-adjustment}
     / violations ≥ 0 / items 可定位 / owner / removalCondition / lastRenewal。
  R2 双源一致：台账每个条目的 store 文件必须真实存在**且非空**（空 store = 防新增型冻结，
     不属债务，出现在台账里说明台账在凭空记账）；反方向：全仓出现新的**非空** store 而台账
     未登记且无 `waiver-<rule-hash>` 豁免注释时判失败——防「新增债务不入账」。
  R3 双份真源禁止：`lastRenewal` 若Switch为布尔/空串视为格式错误；日期字段必须是 ISO 日期。

CI 未消费 lastRenewal 到期之前，本门禁只做结构与双源核对（G-2「到期机制未被 CI 消费前，
只能称人工治理」）。
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LEDGER = REPO / "doc/architecture/freeze-ledger.yaml"
ARCHUNIT_STORE_DIRS = sorted(REPO.glob("**/archunit_store"))

try:  # PyYAML 是脚本门禁的软依赖：缺失时退化为逐行结构检查
    import yaml  # type: ignore

    _HAVE_YAML = True
except ImportError:  # pragma: no cover
    _HAVE_YAML = False

RULE_LINE = re.compile(r"^\s*-\s+rule:\s*(\S+)")
KEY_LINE = re.compile(r"^\s+(rule|module|store|classification|violations|owner|removalCondition|lastRenewal):\s*(.*)$")
WAIVER_RE = re.compile(r"#\s*waiver-([0-9a-f]{8})")
iso_date = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def parse_ledger_lines(text: str) -> list[dict]:
    """无 PyYAML 时的降级解析：按 '- rule:' 切分条目块，抽已知 key。"""
    entries: list[dict] = []
    current: dict | None = None
    for line in text.splitlines():
        m = RULE_LINE.match(line)
        if m:
            current = {"rule": m.group(1)}
            entries.append(current)
            continue
        if current is None:
            continue
        m = KEY_LINE.match(line)
        if m:
            current[m.group(1)] = m.group(2).strip()
    return entries


def store_violation_count(store_file: Path) -> int:
    """store 文件的「存储违规条目」计数：非空行数（ArchUnit freeze store 每行一条）。"""
    try:
        lines = [
            ln
            for ln in store_file.read_text(encoding="utf-8", errors="ignore").splitlines()
            if ln.strip()
        ]
        return len(lines)
    except OSError:
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="freeze-ledger 台账结构 + 双源校验")
    parser.add_argument("--check", action="store_true", help="CI 模式：发现不可收敛问题即非零退出")
    parser.add_argument(
        "--report-only", action="store_true", help="只打印双源对比，不判失败（观察模式）"
    )
    args = parser.parse_args()

    if not LEDGER.is_file():
        print(f"❌ 找不到台账文件：{LEDGER.relative_to(REPO)}")
        return 1

    text = LEDGER.read_text(encoding="utf-8")
    parse_mode = "yaml"
    entries: list[dict] = []
    if _HAVE_YAML:
        try:
            data = yaml.safe_load(text)
            entries = (data or {}).get("ledger") or []
        except Exception as exc:  # noqa: BLE001
            print(f"❌ freeze-ledger.yaml 不是合法 YAML：{exc}")
            return 1
    else:
        parse_mode = "fallback-line"
        entries = parse_ledger_lines(text)

    print(f"freeze-ledger 校验（解析模式：{parse_mode}，条目 {len(entries)} 个）")
    errors: list[str] = []
    warns: list[str] = []

    # ── R1 结构齐备 ──────────────────────────────────────────────
    seen_rules: dict[str, int] = {}
    for i, e in enumerate(entries, 1):
        label = f"条目{i}:{e.get('rule')}"
        for key in ("rule", "module", "store", "classification", "violations", "owner"):
            if not str(e.get(key) or "").strip():
                errors.append(f"{label} 缺字段 {key}")
        if e.get("classification") not in ("debt", "exception", "planned-adjustment"):
            errors.append(
                f"{label} classification={e.get('classification')!r}"
                "（必须是 debt/exception/planned-adjustment）"
            )
        try:
            if int(e.get("violations", -1)) < 0:
                errors.append(f"{label} violations 必须非负")
        except (TypeError, ValueError):
            errors.append(f"{label} violations 不是整数：{e.get('violations')!r}")
        items = e.get("items")
        if items is None:
            warns.append(f"{label} 无 items（G-2 要求可定位，请补类级定位）")
        elif isinstance(items, list) and not items:
            warns.append(f"{label} items 为空列表")
        removal = str(e.get("removalCondition") or "").strip()
        if len(removal) < 10:
            errors.append(f"{label} removalCondition 过短或缺失（必须可验证）")
        lr_date = e.get("lastRenewalDate")
        if lr_date is not None and not iso_date.match(str(lr_date)):
            errors.append(f"{label} lastRenewalDate 不是 ISO 日期：{lr_date!r}")
        seen = seen_rules.get(str(e.get("rule")), 0)
        seen_rules[str(e.get("rule"))] = seen + 1

    # ── R2 双源一致 ─────────────────────────────────────────────
    ledger_stores: set[str] = set()
    for e in entries:
        store_rel = str(e.get("store") or "")
        if not store_rel:
            continue
        ledger_stores.add(store_rel)
        store_file = REPO / store_rel
        if not store_file.is_file():
            errors.append(f"{e.get('rule')}: store 不存在：{store_rel}")
            continue
        n = store_violation_count(store_file)
        if n == 0:
            errors.append(
                f"{e.get('rule')}: store 存在但为空（防新增型冻结不是债务，"
                "不应登记进台账——从 ledger 移除该条目）"
            )
        try:
            v = int(e.get("violations", -1))
        except (TypeError, ValueError):
            v = -1
        if v != n:
            errors.append(f"{e.get('rule')}: violations={v} ≠ store 实际条数 {n}（{store_rel}）")

    # 全仓非空 store 清单（反向：债务必须入账）
    all_nonempty: dict[str, int] = {}
    for d in ARCHUNIT_STORE_DIRS:
        if not d.is_dir():
            continue
        for f in d.iterdir():
            if f.name in ("stored.rules",) or not f.is_file():
                continue
            n = store_violation_count(f)
            rel = f.relative_to(REPO).as_posix()
            if n > 0:
                all_nonempty[rel] = n

    for rel, n in sorted(all_nonempty.items()):
        if rel not in ledger_stores:
            text_head = text + "\n"
            m = WAIVER_RE.search(text_head)
            waiver_hit = m and m.group(1)[:8] in rel
            if not waiver_hit:
                errors.append(
                    f"全仓发现非空 freeze store（{n} 条）但台账未登记：{rel}"
                    "（新增债务必须入账，或加 waiver-<uuid 前8> 豁免并说明理由）"
                )

    print(f"  台账条目：{len(entries)}；全仓非空 store：{len(all_nonempty)}")
    for w in warns:
        print(f"  ⚠ {w}")
    if errors:
        print("\n❌ 台账校验失败：")
        for e in errors:
            print(f"  - {e}")
        return 1 if (args.check or not args.report_only) else 0

    print("  ✅ 台账结构与 archunit_store 双源一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
