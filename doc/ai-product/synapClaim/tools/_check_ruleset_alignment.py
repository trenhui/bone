#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""规则 ID 命名空间对账：Rulebook 条款 ↔ claim-ruleset.json 执行项。

背景（2026-11-07 实测）：
  C7 只校验「L4/L6 引用的规则 ID 是否在 Rulebook 定义」，两边都定义就放行。
  但实测发现 `R-FRAUD-02` 在 Rulebook 是「金额异常检测」、在资产里跑的是
  `hasEvidence == false`——**同一个 ID、两套语义**，C7 完全查不出来。
  客户按规则书理解赔付逻辑，实际系统按另一套规则跑，属于比「ID 缺失」
  更危险的一类契约漂移。

策略（业界做法）：**不重命名已生效 ID**（日志/评测集/客户交付都在用，
改名是不可逆口径断裂），改为登记命名空间对照表 + 用门禁锁住它。

判据：
  A1 §11.1 登记的 ID 集合 == 资产 ID ∩ Rulebook 条款 ID（双向对账）
  A2 每行 conclusion / risk 与资产当前值逐行一致
  A3 「同名不同义」集合 == 存量基线（防静默改成「一致」或新增未登记撞名）
  A4 解析不出集合即 fail-closed，不按空集合恒过
"""
import io
import json
import os
import re
import sys

import _manifest as M

RULEBOOK = M.path("delivery.04")
ASSET = os.path.abspath(os.path.join(
    M.ROOT, "..", "..", "..", "bone-apps", "synap-claim",
    "src", "main", "resources", "claim-ruleset.json"))

SEC11 = "## 十一、执行资产 ID 命名空间对照"
ROW_RE = re.compile(
    r"^\|\s*`(R-[A-Z]+-\d+)`\s*\|[^|]*\|\s*`([A-Z_0-9]+)`\s*\|"
    r"\s*`(True|False)`\s*\|\s*\*{0,2}(一致|同名不同义)")
# §11.2「仅资产」逐条登记：ID | `CATEGORY` | `CONCLUSION` | `True|False` | expression
# conclusion 允许数字（实测 `OUT_OF_PHASE1_SCOPE` 含 1；首版 `[A-Z_]+`
# 把它静默漏掉，导致 15 条只解析出 14 条——与 BSD grep / `match()` 同族）。
ROW_ONLY_RE = re.compile(
    r"^\|\s*`(R-[A-Z]+-\d+)`\s*\|\s*`[A-Z_0-9]+`\s*\|\s*`([A-Z_0-9]+)`\s*\|"
    r"\s*`(True|False)`\s*\|")
CLAUSE_RE = re.compile(r"^\|\s*(R-[A-Z]+-\d+)\s*\|")
SEC112 = "### 11.2"
SEC113 = "### 11.3"

# 2026-11-07 首次对账时，两层共有但语义不同的 10 个 ID（ratchet 基线）。
CONFLICT_BASELINE = {
    "R-DIR-01", "R-FRAUD-02", "R-FRAUD-03", "R-FRAUD-04", "R-FRAUD-05",
    "R-FRAUD-07", "R-FRAUD-08", "R-FRAUD-09", "R-FRAUD-10", "R-FRAUD-11",
}


def load_asset():
    with io.open(ASSET, encoding="utf-8") as f:
        data = json.load(f)
    items = data.get("rules") or data.get("items") or []
    out = {}
    for r in items:
        rid = r.get("code") or r.get("id")
        if rid:
            out[rid] = (str(r.get("conclusion", "")), str(r.get("risk", "")))
    return out


def parse(text):
    """返回 (§11.1 共有登记行, §11.2 仅资产登记行, §十一之前的条款 ID 集合)。"""
    if SEC11 not in text:
        return {}, {}, set()
    before, sec11 = text.split(SEC11, 1)
    clauses = {m.group(1) for l in before.splitlines()
               if (m := CLAUSE_RE.match(l))}
    # 按小节切片，避免 §11.2 / §11.3 的表格行串进彼此的解析
    part11_1 = sec11.split(SEC112, 1)[0]
    part11_2 = sec11.split(SEC112, 1)[1].split(SEC113, 1)[0] if SEC112 in sec11 else ""
    rows = {}
    for l in part11_1.splitlines():
        m = ROW_RE.match(l)
        if m:
            rows[m.group(1)] = (m.group(2), m.group(3), m.group(4))
    only = {}
    for l in part11_2.splitlines():
        m = ROW_ONLY_RE.match(l)
        if m:
            only[m.group(1)] = (m.group(2), m.group(3))
    return rows, only, clauses


def verdict(asset, rows, only, clauses):
    """纯函数判定，便于 --self-test 反证。返回错误列表。

    A1–A3 守共有 18 条；A5 是 2026-11-07 补的**全量覆盖率**判据：
    资产 33 条必须全部落在 §11.1 ∪ §11.2 里，否则门禁只守交集、静默丢覆盖。
    """
    errs = []
    if not asset:
        return ["A4 规则资产解析为空（路径或结构已变），fail-closed"]
    if not rows:
        return ["A4 §11.1 对照表解析为空（章节被删或表格格式已改），fail-closed"]
    if not clauses:
        return ["A4 Rulebook 条款 ID 解析为空，fail-closed"]

    common = set(asset) & clauses
    registered = set(rows)
    if common - registered:
        errs.append("A1 共有 ID 未在 §11.1 登记：%s" % sorted(common - registered))
    if registered - common:
        errs.append("A1 §11.1 登记了非共有 ID（或 ID 已从一侧删除）：%s"
                    % sorted(registered - common))
    for rid in sorted(registered & set(asset)):
        conclusion, risk, rel = rows[rid]
        real_c, real_r = asset[rid]
        if conclusion != real_c:
            errs.append("A2 %s conclusion 文档=%s / 资产=%s" % (rid, conclusion, real_c))
        if risk != real_r:
            errs.append("A2 %s risk 文档=%s / 资产=%s" % (rid, risk, real_r))
    conflicts = {rid for rid, (_, _, rel) in rows.items() if rel == "同名不同义"}
    if conflicts - CONFLICT_BASELINE:
        errs.append("A3 新增「同名不同义」未登记基线：%s" % sorted(conflicts - CONFLICT_BASELINE))
    if CONFLICT_BASELINE - conflicts:
        errs.append("A3 基线中的冲突项被静默改成「一致」或删除：%s"
                    % sorted(CONFLICT_BASELINE - conflicts))

    covered = registered | set(only)
    if set(asset) - covered:
        errs.append("A5 资产中有 ID 未在 §11.1 / §11.2 登记（门禁覆盖缺口）：%s"
                    % sorted(set(asset) - covered))
    if covered - set(asset):
        errs.append("A5 登记了资产里已不存在的 ID：%s" % sorted(covered - set(asset)))
    if registered & set(only):
        errs.append("A5 同一个 ID 同时登记在 §11.1 与 §11.2：%s"
                    % sorted(registered & set(only)))
    for rid in sorted(set(only) & set(asset)):
        conclusion, risk = only[rid]
        real_c, real_r = asset[rid]
        if conclusion != real_c:
            errs.append("A5 §11.2 %s conclusion 文档=%s / 资产=%s" % (rid, conclusion, real_c))
        if risk != real_r:
            errs.append("A5 §11.2 %s risk 文档=%s / 资产=%s" % (rid, risk, real_r))
    return errs


def main():
    if "--self-test" in sys.argv:
        return self_test()
    try:
        asset = load_asset()
        text = io.open(RULEBOOK, encoding="utf-8").read()
    except (OSError, ValueError) as e:
        print("=== 规则 ID 命名空间对账 ===")
        print("【FAIL】读取失败：%s" % e)
        return 2
    rows, only, clauses = parse(text)
    errs = verdict(asset, rows, only, clauses)
    print("=== 规则 ID 命名空间对账 ===")
    print("资产 %d 条 / Rulebook 条款 %d 条 / 共有 %d 条 / §11.1 登记 %d 行 / §11.2 登记 %d 行"
          % (len(asset), len(clauses), len(set(asset) & clauses), len(rows), len(only)))
    print("全量覆盖：%d/%d 条资产已逐条登记" % (len((set(rows) | set(only)) & set(asset)), len(asset)))
    if errs:
        for e in errs:
            print("【FAIL】" + e)
        print("共 %d 项失败" % len(errs))
        return 1
    print("【OK】资产全量覆盖 + 共有 ID 双向对账 + conclusion/risk 逐行一致 + 10 条同名不同义基线未漂移")
    return 0


def self_test():
    """反证：全绿不等于有判别力，改坏了必须判红。"""
    asset = {
        "R-FRAUD-02": ("NO_EVIDENCE", "True"),
        "R-FRAUD-06": ("VERIFY_NOT_PASSED", "True"),
        "R-AMOUNT-01": ("AMOUNT_OVER_LIMIT", "False"),
    }
    clauses = {"R-FRAUD-02", "R-FRAUD-06"}
    rows = {
        "R-FRAUD-02": ("NO_EVIDENCE", "True", "同名不同义"),
        "R-FRAUD-06": ("VERIFY_NOT_PASSED", "True", "一致"),
    }
    only = {"R-AMOUNT-01": ("AMOUNT_OVER_LIMIT", "False")}
    # 构造样本里只有 1 条冲突，基线是 10 条，A3 必判红。
    # 因此给 self_test 用一个「与构造样本一致」的临时基线，测完还原。
    global CONFLICT_BASELINE
    saved = CONFLICT_BASELINE
    CONFLICT_BASELINE = {"R-FRAUD-02"}
    passed, failed = 0, 0

    def expect(label, cond):
        nonlocal passed, failed
        if cond:
            passed += 1
            print("  ✅ " + label)
        else:
            failed += 1
            print("  ❌ " + label)

    try:
        print("=== _check_ruleset_alignment.py --self-test ===")
        expect("正则可解析含数字的 conclusion（OUT_OF_PHASE1_SCOPE）",
               bool(ROW_ONLY_RE.match(
                   "| `R-SCOPE-01` | `SCOPE` | `OUT_OF_PHASE1_SCOPE` | `True` | `x == false` |")))
        expect("合规正例（含 §11.2 仅资产登记）", verdict(asset, rows, only, clauses) == [])
        expect("变异① 删掉 §11.1 一行 → A1 判红",
               any("A1" in e for e in verdict(
                   asset, {"R-FRAUD-02": rows["R-FRAUD-02"]}, only, clauses)))
        expect("变异② 改了资产 conclusion 没改文档 → A2 判红",
               any("A2" in e for e in verdict(
                   dict(asset, **{"R-FRAUD-02": ("CHANGED", "True")}), rows, only, clauses)))
        expect("变异③ 把同名不同义改成一致 → A3 判红",
               any("A3" in e for e in verdict(
                   asset, dict(rows, **{"R-FRAUD-02": ("NO_EVIDENCE", "True", "一致")}),
                   only, clauses)))
        expect("变异④ 新增一个共有 ID 未登记 → A1 判红",
               any("A1" in e for e in verdict(
                   dict(asset, **{"R-FRAUD-99": ("X", "False")}), rows, only,
                   clauses | {"R-FRAUD-99"})))
        expect("变异⑤ 表格整段被删 → fail-closed",
               any("A4" in e for e in verdict(asset, {}, only, clauses)))
        expect("变异⑥ 资产读不出来 → fail-closed",
               any("A4" in e for e in verdict({}, rows, only, clauses)))
        expect("变异⑦ §11.2 删掉一条登记 → A5 判红（覆盖率缺口）",
               any("A5" in e for e in verdict(asset, rows, {}, clauses)))
        expect("变异⑧ §11.2 的 conclusion 与资产不符 → A5 判红",
               any("A5" in e for e in verdict(
                   asset, rows, {"R-AMOUNT-01": ("CHANGED", "False")}, clauses)))
        expect("变异⑨ 资产新增一条未登记 → A5 判红",
               any("A5" in e for e in verdict(
                   dict(asset, **{"R-NEW-01": ("X", "False")}), rows, only, clauses)))
        expect("变异⑩ 同一 ID 同时登记在两节 → A5 判红",
               any("A5" in e for e in verdict(
                   asset, rows, dict(only, **{"R-FRAUD-06": ("X", "False")}), clauses)))
    finally:
        CONFLICT_BASELINE = saved
    print("  %d 通过 / %d 失败" % (passed, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
