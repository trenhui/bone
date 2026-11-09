#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
采购域 AI 评测集本地校验器（设计态 seed 草稿版）。

复刻 synap-claim check-claim-ai-evalset.py 的核心校验维度（A1-A9）：
  A1 schemaVersion / evalsetId 存在且类型正确
  A2 suites 数组非空、id 唯一
  A3 cases 数组非空、id 唯一
  A4 每个 case 必填字段齐全（id/name/suite/kind/expected/groundTruthRef/regulatoryBasis/testClass/testSymbol/piiFree）
  A5 每个 case.suite 必须命中 suites[].id
  A6 每个 case.piiFree 必须为 true（监管 §24 PII 禁入）
  A7 每个 case.groundTruthRef 非空且形如 path#anchor
  A8 负向用例占比须达到阈值（关键负向不可删，防假绿）
  A9 status 必须为 seededDraft 且 needsOwnerReview=true（防止被误当成发布门禁终态）

重要边界：
  - 本脚本是**本地助手**，**不接入 CI**（D6 由平台组裁决），不影响 tools/check_procura_alignment.py 的 A1-A10。
  - SEED_MIN_CASES 是设计态种子下界（当前 12），owner 按 DA-3 补全至 PRD ≥200 后由门禁脚本统一接管。
  - 脚本退出码：校验通过=0；结构违规=1。seed 状态会打印醒目横幅提醒，但不改变退出码（仅提示）。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_EVALSET = os.path.normpath(os.path.join(HERE, "..", "eval", "ai-decision-evalset.json"))

# 设计态种子下界（owner 按 DA-3 补全至 ≥200）
SEED_MIN_CASES = 12
# 关键负向占比下限（防假绿：负向用例不可低于此比例）
MIN_NEGATIVE_RATIO = 0.5
REQUIRED_CASE_FIELDS = [
    "id", "name", "suite", "kind", "expected",
    "groundTruthRef", "regulatoryBasis", "testClass", "testSymbol", "piiFree",
]


def fail(msg):
    print(f"[FAIL] {msg}")
    return False


def check(evalset_path):
    ok = True
    try:
        with open(evalset_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法解析 JSON: {e}")
        return False

    # A1
    if not isinstance(data.get("schemaVersion"), int):
        ok = fail("A1 schemaVersion 缺失或非 int")
    if not isinstance(data.get("evalsetId"), str) or not data.get("evalsetId"):
        ok = fail("A1 evalsetId 缺失或非字符串")

    # A2
    suites = data.get("suites")
    if not isinstance(suites, list) or not suites:
        ok = fail("A2 suites 缺失或非空数组")
    else:
        suite_ids = [s.get("id") for s in suites]
        if len(suite_ids) != len(set(suite_ids)):
            ok = fail("A2 suites id 重复")
        for s in suites:
            if not isinstance(s.get("id"), str) or not s.get("id"):
                ok = fail("A2 存在 suite 缺 id")

    # A3
    cases = data.get("cases")
    if not isinstance(cases, list) or not cases:
        ok = fail("A3 cases 缺失或非空数组")
        cases = []
    case_ids = [c.get("id") for c in cases]
    if len(case_ids) != len(set(case_ids)):
        ok = fail("A3 case id 重复")

    # A4 + A5 + A6 + A7
    suite_id_set = set(suite_ids) if isinstance(suites, list) else set()
    n_neg = 0
    for c in cases:
        for field in REQUIRED_CASE_FIELDS:
            if field not in c:
                ok = fail(f"A4 case {c.get('id','?')} 缺字段 {field}")
        if c.get("suite") not in suite_id_set:
            ok = fail(f"A5 case {c.get('id','?')} 的 suite={c.get('suite')} 未命中 suites")
        if c.get("piiFree") is not True:
            ok = fail(f"A6 case {c.get('id','?')} piiFree 不为 true（监管 §24）")
        gtr = c.get("groundTruthRef", "")
        if not isinstance(gtr, str) or "#" not in gtr:
            ok = fail(f"A7 case {c.get('id','?')} groundTruthRef 为空或缺少 '#' 锚点")
        if c.get("kind") == "negative":
            n_neg += 1

    # A8
    if cases:
        ratio = n_neg / len(cases)
        if ratio < MIN_NEGATIVE_RATIO:
            ok = fail(f"A8 负向占比 {ratio:.2f} 低于下限 {MIN_NEGATIVE_RATIO}")

    # 种子下界
    if len(cases) < SEED_MIN_CASES:
        ok = fail(f"种子用例数 {len(cases)} 低于设计态下界 {SEED_MIN_CASES}")

    # A9
    if data.get("status") != "seededDraft":
        ok = fail("A9 status 必须为 seededDraft（防止被误当发布门禁终态）")
    if data.get("needsOwnerReview") is not True:
        ok = fail("A9 needsOwnerReview 必须为 true")

    # 汇总
    print(f"[INFO] evalset={data.get('evalsetId')} status={data.get('status')} "
          f"suites={len(suite_ids) if isinstance(suites, list) else 0} cases={len(cases)} "
          f"negative={n_neg} negRatio={ (n_neg/len(cases)) if cases else 0:.2f}")
    if data.get("status") == "seededDraft":
        print("=" * 60)
        print(" [SEED DRAFT] 本评测集为设计态种子草稿，非发布门禁终态。")
        print("  - 代码未落地：groundTruthRef 指向文档真源锚点，非源码 path。")
        print("  - 规模/通过线待 owner 按 DA-3 补全至 PRD ≥200 + 对抗样本。")
        print("  - 本脚本为本地助手，不接入 CI（D6 由平台组裁决）。")
        print("=" * 60)

    print("RESULT:", "PASS" if ok else "FAIL")
    return ok


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_EVALSET
    if not os.path.exists(path):
        print(f"[FAIL] 评测集文件不存在: {path}")
        sys.exit(1)
    ok = check(path)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
