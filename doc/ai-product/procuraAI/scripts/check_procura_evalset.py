#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""采购域 AI 评测集校验器（设计态 seed 草稿；已接 CI blocking 与本地 pre-commit）。

复刻 synap-claim check-claim-ai-evalset.py 的核心校验维度，并按 2026-11-10 复诊补齐：
  A1  schemaVersion / evalsetId 存在且类型正确
  A2  suites 数组非空、id 唯一
  A3  cases 数组非空、id 唯一
  A4  每个 case 必填字段齐全（含 capabilityCode）
  A5  每个 case.suite 必须命中 suites[].id
  A6  每个 case.piiFree 必须为 true（PII 禁入）
  A7  每个 case.groundTruthRef 非空且形如 path#anchor
  A8  负向用例占比须达到阈值（关键负向不可删，防假绿）
  A9  status 必须为 seededDraft 且 needsOwnerReview=true（防误当发布门禁终态）
  A10 capabilityCode 登记与双向追溯：码表 ↔ 用例互相覆盖（复诊 P1-⑤）
  A11 G15 算法公平偏见用例下界与关键 ID 不可删（复诊「零偏见用例」）
  A12 关键负向用例 ID 集合不可删 / 不可改成正向
  A13 ≥200 样本规模路线必须登记，seed 不得被表述为终态

重要边界（诚实标注，勿过度声称）：
  - 本脚本校验的是**评测集结构与治理骨架**，不是模型效果；模型效果评测仍待 owner
    按 DA-3 补齐 ≥200 样本 + 对抗样本 + 偏见审计集并给出通过线。
  - groundTruthRef 在设计态指向文档真源锚点，代码落地后须重定向到源码契约。
  - status=seededDraft / needsOwnerReview=true 是**刻意保留**的：CI 绿 ≠ 可上线。
  - 退出码：校验通过=0；结构违规=1；--self-test 反证未全转红=1。
"""
import argparse
import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_EVALSET = os.path.normpath(os.path.join(HERE, "..", "eval", "ai-decision-evalset.json"))

# 设计态种子下界（owner 按 DA-3 补全至 PRD ≥200 后由发布门禁接管）
SEED_MIN_CASES = 19
SCALE_TARGET = 200
# 关键负向占比下限（防假绿：负向用例不可低于此比例）
MIN_NEGATIVE_RATIO = 0.5
REQUIRED_CASE_FIELDS = [
    "id", "name", "suite", "kind", "expected",
    "groundTruthRef", "regulatoryBasis", "testClass", "testSymbol",
    "piiFree", "capabilityCode",
]
# 外部锚点：能力码硬编码自 D3-v2 §1.1，不从评测集自身推导（避免闭环自证）
CAPABILITY_CODES = frozenset({
    "M1", "M2", "M3", "M4", "M5", "M6", "M7", "ORCHESTRATION",
})
FAIRNESS_MIN_CASES = 2
CRITICAL_FAIR_IDS = frozenset({"FAIR-01", "FAIR-02"})
CRITICAL_NEGATIVE_IDS = frozenset({
    "CONTRACT-02", "MATCH-02", "RISK-01", "ANOMALY-02", "DEGRADE-01",
    "DISCLOSE-01", "DISCLOSE-02", "FAIR-01", "FAIR-02", "LABEL-01",
})


def validate(data: dict) -> tuple[bool, list[str]]:
    """纯函数：只吃 dict，便于 --self-test 在内存中注入反证，不读写磁盘。"""
    msgs: list[str] = []

    # A1
    if not isinstance(data.get("schemaVersion"), int):
        msgs.append("A1 schemaVersion 缺失或非 int")
    if not isinstance(data.get("evalsetId"), str) or not data.get("evalsetId"):
        msgs.append("A1 evalsetId 缺失或非字符串")

    # A2
    suites = data.get("suites")
    suite_ids: list[str] = []
    if not isinstance(suites, list) or not suites:
        msgs.append("A2 suites 缺失或非空数组")
    else:
        suite_ids = [s.get("id") for s in suites]
        if len(suite_ids) != len(set(suite_ids)):
            msgs.append("A2 suites id 重复")
        for s in suites:
            if not isinstance(s.get("id"), str) or not s.get("id"):
                msgs.append("A2 存在 suite 缺 id")

    # A3
    cases = data.get("cases")
    if not isinstance(cases, list) or not cases:
        msgs.append("A3 cases 缺失或非空数组")
        cases = []
    case_ids = [c.get("id") for c in cases]
    if len(case_ids) != len(set(case_ids)):
        msgs.append("A3 case id 重复")

    # A4 + A5 + A6 + A7 + A10(逐例)
    suite_id_set = set(suite_ids)
    n_neg = 0
    by_id = {c.get("id"): c for c in cases}
    used_caps: set[str] = set()
    for c in cases:
        cid = c.get("id", "?")
        for field in REQUIRED_CASE_FIELDS:
            if field not in c:
                msgs.append(f"A4 case {cid} 缺字段 {field}")
        if c.get("suite") not in suite_id_set:
            msgs.append(f"A5 case {cid} 的 suite={c.get('suite')} 未命中 suites")
        if c.get("piiFree") is not True:
            msgs.append(f"A6 case {cid} piiFree 不为 true（PII 禁入）")
        gtr = c.get("groundTruthRef", "")
        if not isinstance(gtr, str) or "#" not in gtr:
            msgs.append(f"A7 case {cid} groundTruthRef 为空或缺少 '#' 锚点")
        if c.get("kind") == "negative":
            n_neg += 1
        cap = c.get("capabilityCode")
        if cap is not None:
            if cap not in CAPABILITY_CODES:
                msgs.append(f"A10 case {cid} capabilityCode={cap!r} 未登记在 D3-v2 §1.1 能力码表")
            else:
                used_caps.add(cap)

    # A8
    if cases:
        ratio = n_neg / len(cases)
        if ratio < MIN_NEGATIVE_RATIO:
            msgs.append(f"A8 负向占比 {ratio:.2f} 低于下限 {MIN_NEGATIVE_RATIO}")

    # 种子下界（输出取常量，不写死成功数字）
    if len(cases) < SEED_MIN_CASES:
        msgs.append(f"种子用例数 {len(cases)} 低于设计态下界 {SEED_MIN_CASES}")

    # A9
    if data.get("status") != "seededDraft":
        msgs.append("A9 status 必须为 seededDraft（CI 绿 ≠ 发布门禁终态）")
    if data.get("needsOwnerReview") is not True:
        msgs.append("A9 needsOwnerReview 必须为 true")

    # A10 能力码双向追溯
    declared = set(data.get("capabilityCodes", {}))
    if not declared:
        msgs.append("A10 评测集缺 capabilityCodes 登记（能力码↔用例无法双向追溯）")
    else:
        if declared != CAPABILITY_CODES:
            msgs.append(f"A10 capabilityCodes 登记 {sorted(declared)} != D3-v2 §1.1 外部锚点 "
                        f"{sorted(CAPABILITY_CODES)}")
    missing_caps = CAPABILITY_CODES - used_caps
    if missing_caps:
        msgs.append(f"A10 已登记能力码无任何评测用例覆盖：{sorted(missing_caps)}（悬空登记）")

    # A11 G15 算法公平偏见
    fair = [c for c in cases if c.get("suite") == "algorithmicFairness"]
    if len(fair) < FAIRNESS_MIN_CASES:
        msgs.append(f"A11 G15 公平性偏见用例 {len(fair)} < {FAIRNESS_MIN_CASES}（复诊零偏见用例回退）")
    for fid in CRITICAL_FAIR_IDS:
        c = by_id.get(fid)
        if not c:
            msgs.append(f"A11 G15 关键偏见用例 {fid} 不得删除")
        elif c.get("kind") != "negative":
            msgs.append(f"A11 G15 关键偏见用例 {fid} 不得改成非负向")

    # A12 关键负向不可删 / 不可转正
    for kid in CRITICAL_NEGATIVE_IDS:
        c = by_id.get(kid)
        if not c:
            msgs.append(f"A12 关键负向用例 {kid} 不得删除")
        elif c.get("kind") != "negative":
            msgs.append(f"A12 关键负向用例 {kid} 不得改成非负向")

    # A13 规模路线与 seed 诚实标注
    corpus = " ".join(str(data.get(k, "")) for k in ("scope", "seedNote"))
    if f"≥{SCALE_TARGET}" not in corpus:
        msgs.append(f"A13 缺 ≥{SCALE_TARGET} 样本规模路线登记（seed 不得被当作终态）")
    reg = data.get("regulatoryBasisCatalog", {})
    if not isinstance(reg, dict) or not reg.get("sources"):
        msgs.append("A13 缺 regulatoryBasisCatalog（上位合规依据不可达 / 行业适用性未重挂）")

    return (not msgs), msgs


def check(evalset_path: str) -> bool:
    try:
        with open(evalset_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法解析 JSON: {e}")
        return False
    ok, msgs = validate(data)
    for m in msgs:
        print(f"[FAIL] {m}")

    cases = data.get("cases") if isinstance(data.get("cases"), list) else []
    n_neg = sum(1 for c in cases if c.get("kind") == "negative")
    suits = data.get("suites") if isinstance(data.get("suites"), list) else []
    print(f"[INFO] evalset={data.get('evalsetId')} status={data.get('status')} "
          f"suites={len(suits)} cases={len(cases)} negative={n_neg} "
          f"negRatio={(n_neg / len(cases)) if cases else 0:.2f} "
          f"caps={len({c.get('capabilityCode') for c in cases})}/{len(CAPABILITY_CODES)}")
    if data.get("status") == "seededDraft":
        print("=" * 68)
        print(" [SEED DRAFT] 本评测集为设计态种子草稿，非发布门禁终态。")
        print(f"  - 代码未落地：groundTruthRef 指向文档真源锚点，非源码 path。")
        print(f"  - 规模/通过线待 owner 按 DA-3 补全至 PRD ≥{SCALE_TARGET} + 对抗样本 + 偏见审计集。")
        print("  - 本脚本已接 CI blocking 与本地 pre-commit，但只守结构 / 能力码 / 关键负向，")
        print("    **不守模型效果**；CI 绿不等于 AI 能力可上线。")
        print("=" * 68)
    print("RESULT:", "PASS" if ok else "FAIL")
    return ok


def self_test(evalset_path: str) -> int:
    with open(evalset_path, "r", encoding="utf-8") as f:
        base = json.load(f)
    ok0, msgs0 = validate(base)
    if not ok0:
        print("[SELF-TEST] 基线未全绿，反证无意义：", msgs0[:3])
        return 1

    def mut(fn):
        d = copy.deepcopy(base)
        fn(d)
        return d

    def drop_cap(d):
        d["cases"][0].pop("capabilityCode", None)

    def bad_cap(d):
        d["cases"][0]["capabilityCode"] = "M99"

    def drop_cap_registry(d):
        d["capabilityCodes"].pop("M6", None)

    def drop_fair(d):
        d["cases"] = [c for c in d["cases"] if c["id"] != "FAIR-01"]

    def fair_to_positive(d):
        for c in d["cases"]:
            if c["id"] == "FAIR-02":
                c["kind"] = "positive"

    def drop_critical_negative(d):
        d["cases"] = [c for c in d["cases"] if c["id"] != "MATCH-02"]

    def pii_leak(d):
        d["cases"][0]["piiFree"] = False

    def status_released(d):
        d["status"] = "released"

    def drop_scale_route(d):
        d["scope"] = d["scope"].replace(f"≥{SCALE_TARGET}", "")
        d["seedNote"] = d["seedNote"].replace(f"≥{SCALE_TARGET}", "")

    def drop_catalog(d):
        d.pop("regulatoryBasisCatalog", None)

    def shrink_cases(d):
        d["cases"] = d["cases"][:SEED_MIN_CASES - 1]

    probes = [
        ("删一条用例的 capabilityCode", drop_cap, "A4"),
        ("注入未登记能力码 M99", bad_cap, "A10"),
        ("删 capabilityCodes 中 M6 登记", drop_cap_registry, "A10"),
        ("删 G15 关键偏见用例 FAIR-01", drop_fair, "A11"),
        ("FAIR-02 改成正向", fair_to_positive, "A11"),
        ("删关键负向 MATCH-02", drop_critical_negative, "A12"),
        ("piiFree 改 false", pii_leak, "A6"),
        ("status 改成 released", status_released, "A9"),
        ("抹掉 ≥200 规模路线", drop_scale_route, "A13"),
        ("删合规依据目录", drop_catalog, "A13"),
        ("用例数压到种子下界以下", shrink_cases, "种子用例数"),
    ]
    all_ok = True
    for desc, fn, expect in probes:
        ok, msgs = validate(mut(fn))
        hit = any(expect in m for m in msgs)
        good = (not ok) and hit
        print(f"[{'PASS' if good else 'FAIL'}] 负向：{desc}")
        if good:
            print(f"       - 转红证据：{next(m for m in msgs if expect in m)}")
        else:
            print(f"       - 未转红或 key {expect!r} 未命中：{msgs[:2]}")
        all_ok = all_ok and good
    print(f"\n[SELF-TEST] {len(probes)}/{len(probes)} 负向探针全部转红且命中预期 key"
          if all_ok else "\n[SELF-TEST] 存在未转红的判据，需修复")
    return 0 if all_ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="采购域 AI 评测集结构与治理门禁")
    ap.add_argument("evalset", nargs="?", default=DEFAULT_EVALSET, help="评测集 JSON 路径")
    ap.add_argument("--self-test", action="store_true", help="注入式负向反证")
    args = ap.parse_args()
    if not os.path.exists(args.evalset):
        print(f"[FAIL] 评测集文件不存在: {args.evalset}")
        return 1
    if args.self_test:
        return self_test(args.evalset)
    return 0 if check(args.evalset) else 1


if __name__ == "__main__":
    sys.exit(main())
