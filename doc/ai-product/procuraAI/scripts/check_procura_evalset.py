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
  A10 capabilityCode 登记与硬编码防误删下界一致（脚本内独立锚点）
  A11 G15 算法公平偏见用例下界与关键 ID 不可删
  A12 关键负向用例 ID 集合不可删 / 不可改成正向
  A13 ≥200 样本规模路线与合规依据目录登记
  A14 能力码**跨文档真源**比对：解析 D3-v2 §1.1 表第一列 → 与
      capabilityCodes / cases 三方比对（2026-11-10 第二轮：消除「脚本内常量自证」）
  A15 scalePlan 结构化：阶段 / 目标样本 / 标注规范 Kappa / 通过线占位 /
      交接条件 / 不得伪造 completed（2026-11-10 第二轮：消除「文本 token 判据」）

重要边界（诚实标注，勿过度声称）：
  - 本脚本校验的是**评测集结构与治理骨架**，不是模型效果；模型效果评测仍待 owner
    按 DA-3 补齐 ≥200 样本 + 对抗样本 + 偏见审计集并给出通过线。
  - groundTruthRef 在设计态指向文档真源锚点，代码落地后须重定向到源码契约。
  - status=seededDraft / needsOwnerReview=true 是**刻意保留**的：CI 绿 ≠ 可上线。
  - A14 的真源是 D3-v2 §1.1 表格，不是本脚本常量；常量只作**独立防误删下界**，
    避免「期望值也来自被校验对象」的闭环自证。
  - 退出码：校验通过=0；结构违规=1；--self-test 反证未全转红=1。
"""
import argparse
import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_EVALSET = os.path.normpath(os.path.join(HERE, "..", "eval", "ai-decision-evalset.json"))
DEFAULT_D3V2 = os.path.normpath(os.path.join(HERE, "..", "采购AI-接口契约与数据字典-v2.md"))

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
# 独立防误删下界：硬编码在脚本内，不从评测集、也不从 D3-v2 推导。
# A14 另解析 D3-v2 §1.1 真源表，两者互为外部锚点，任一漂移都转红。
CAPABILITY_CODES = frozenset({
    "M1", "M2", "M3", "M4", "M5", "M6", "M7", "ORCHESTRATION",
})
# D3-v2 §1.1「AI 能力码 ↔ 采购模块码」表的第一列（唯一结构化真源）
CAP_TRUTH_ROW_RE = re.compile(r"^\|\s*(M\d+|ORCHESTRATION)\s*\|", re.M)
FAIRNESS_MIN_CASES = 2
CRITICAL_FAIR_IDS = frozenset({"FAIR-01", "FAIR-02"})
CRITICAL_NEGATIVE_IDS = frozenset({
    "CONTRACT-02", "MATCH-02", "RISK-01", "ANOMALY-02", "DEGRADE-01",
    "DISCLOSE-01", "DISCLOSE-02", "FAIR-01", "FAIR-02", "LABEL-01",
})
MIN_SCALE_PHASES = 3


def capability_truth(d3_text: str) -> set[str]:
    """从 D3-v2 §1.1 表格解析能力码真源；解析不到返回空集（调用方 fail-closed）。"""
    if "### 1.1" not in d3_text or "### 1.2" not in d3_text:
        return set()
    sec = d3_text.split("### 1.1", 1)[1].split("### 1.2", 1)[0]
    return set(CAP_TRUTH_ROW_RE.findall(sec))


def validate(data: dict, d3_text: str = "") -> tuple[bool, list[str]]:
    """纯函数：只吃 dict + 真源文本，便于 --self-test 在内存中注入反证，不读写磁盘。"""
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
                msgs.append(f"A10 case {cid} capabilityCode={cap!r} 未登记在独立防误删锚点")
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

    # A10 能力码登记 ↔ 独立防误删下界
    declared = set(data.get("capabilityCodes", {}))
    if not declared:
        msgs.append("A10 评测集缺 capabilityCodes 登记（能力码↔用例无法双向追溯）")
    elif declared != CAPABILITY_CODES:
        msgs.append(f"A10 capabilityCodes 登记 {sorted(declared)} != 独立锚点 "
                    f"{sorted(CAPABILITY_CODES)}")
    missing_caps = CAPABILITY_CODES - used_caps
    if missing_caps:
        msgs.append(f"A10 已登记能力码无任何评测用例覆盖：{sorted(missing_caps)}（悬空登记）")

    # A14 跨文档真源：D3-v2 §1.1 表 ↔ capabilityCodes ↔ cases 三方比对
    truth = capability_truth(d3_text)
    if not truth:
        msgs.append("A14 无法从 D3-v2 §1.1 解析能力码真源表（缺文件 / 表被删 / 章节锚点漂移）")
    else:
        if truth != CAPABILITY_CODES:
            msgs.append(f"A14 D3-v2 §1.1 真源表 {sorted(truth)} != 独立锚点 "
                        f"{sorted(CAPABILITY_CODES)}（真源增删码未同步评测集与外部锚点）")
        if declared and declared != truth:
            msgs.append(f"A14 capabilityCodes {sorted(declared)} != D3-v2 §1.1 真源表 "
                        f"{sorted(truth)}（登记与真源漂移）")
        uncovered = truth - used_caps
        if uncovered:
            msgs.append(f"A14 D3-v2 §1.1 真源表中的能力码无评测用例覆盖：{sorted(uncovered)}")

    # A11 G15 算法公平偏见
    fair = [c for c in cases if c.get("suite") == "algorithmicFairness"]
    if len(fair) < FAIRNESS_MIN_CASES:
        msgs.append(f"A11 G15 公平性偏见用例 {len(fair)} < {FAIRNESS_MIN_CASES}（零偏见用例回退）")
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

    # A13 规模路线文本与合规依据目录（与 A15 结构化判据互为双保险）
    corpus = " ".join(str(data.get(k, "")) for k in ("scope", "seedNote"))
    if f"≥{SCALE_TARGET}" not in corpus:
        msgs.append(f"A13 缺 ≥{SCALE_TARGET} 样本规模路线登记（seed 不得被当作终态）")
    reg = data.get("regulatoryBasisCatalog", {})
    if not isinstance(reg, dict) or not reg.get("sources"):
        msgs.append("A13 缺 regulatoryBasisCatalog（上位合规依据不可达 / 行业适用性未重挂）")

    # A15 scalePlan 结构化（防止「写一句 ≥200 就算有路线」）
    sp = data.get("scalePlan")
    if not isinstance(sp, dict):
        msgs.append("A15 缺 scalePlan 结构化规模路线（文本 token 不足以作为可验收路线）")
    else:
        if sp.get("targetCases") != SCALE_TARGET:
            msgs.append(f"A15 scalePlan.targetCases={sp.get('targetCases')} != {SCALE_TARGET}")
        if sp.get("currentCases") != len(cases):
            msgs.append(f"A15 scalePlan.currentCases={sp.get('currentCases')} != 实际用例数 {len(cases)}")
        if sp.get("status") == "completed":
            msgs.append("A15 scalePlan.status 不得标 completed（seed 期禁止伪造扩容完成）")
        phases = sp.get("phases")
        if not isinstance(phases, list) or len(phases) < MIN_SCALE_PHASES:
            msgs.append(f"A15 scalePlan.phases 少于 {MIN_SCALE_PHASES} 阶段")
        else:
            for i, ph in enumerate(phases, 1):
                if not isinstance(ph, dict) or not all(
                        str(ph.get(k, "")).strip() for k in ("phase", "cases", "gate", "exit")):
                    msgs.append(f"A15 scalePlan.phases[{i}] 缺阶段 / 样本数 / 门禁 / exit 判据")
        spec = str(sp.get("annotationSpec", ""))
        if "双人标注" not in spec or "Kappa" not in spec:
            msgs.append("A15 scalePlan.annotationSpec 缺双人标注 / Kappa 一致性判据（D6 标注规范未承接）")
        pc = sp.get("passCriteria")
        if not isinstance(pc, dict) or not pc:
            msgs.append("A15 scalePlan.passCriteria 缺指标与通过线占位（不得伪造数值）")
        if "发布门禁" not in str(sp.get("handover", "")):
            msgs.append("A15 scalePlan.handover 缺发布门禁接管条件（seed 不得被当作终态）")

    return (not msgs), msgs


def read_truth(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as f:
            return f.read()
    except Exception:  # noqa: BLE001 - 真源不可达必须 fail-closed，交由 A14 判红
        return ""


def check(evalset_path: str, d3v2_path: str) -> bool:
    try:
        with open(evalset_path, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"[FAIL] 无法解析 JSON: {e}")
        return False
    ok, msgs = validate(data, read_truth(d3v2_path))
    for m in msgs:
        print(f"[FAIL] {m}")

    cases = data.get("cases") if isinstance(data.get("cases"), list) else []
    n_neg = sum(1 for c in cases if c.get("kind") == "negative")
    suits = data.get("suites") if isinstance(data.get("suites"), list) else []
    truth = capability_truth(read_truth(d3v2_path))
    print(f"[INFO] evalset={data.get('evalsetId')} status={data.get('status')} "
          f"suites={len(suits)} cases={len(cases)} negative={n_neg} "
          f"negRatio={(n_neg / len(cases)) if cases else 0:.2f} "
          f"caps={len({c.get('capabilityCode') for c in cases})}/{len(CAPABILITY_CODES)} "
          f"truthCaps={len(truth)} scaleTarget={data.get('scalePlan', {}).get('targetCases')}")
    if data.get("status") == "seededDraft":
        print("=" * 68)
        print(" [SEED DRAFT] 本评测集为设计态种子草稿，非发布门禁终态。")
        print("  - 代码未落地：groundTruthRef 指向文档真源锚点，非源码 path。")
        print(f"  - 规模/通过线待 owner 按 DA-3 补全至 PRD ≥{SCALE_TARGET} + 对抗样本 + 偏见审计集。")
        print("  - 本脚本已接 CI blocking 与本地 pre-commit，只守结构 / 能力码真源 / 关键负向 /")
        print("    scalePlan 路线，**不守模型效果**；CI 绿不等于 AI 能力可上线。")
        print("=" * 68)
    print("RESULT:", "PASS" if ok else "FAIL")
    return ok


def self_test(evalset_path: str, d3v2_path: str) -> int:
    with open(evalset_path, "r", encoding="utf-8") as f:
        base = json.load(f)
    d3_base = read_truth(d3v2_path)
    ok0, msgs0 = validate(base, d3_base)
    if not ok0:
        print("[SELF-TEST] 基线未全绿，反证无意义：", msgs0[:3])
        return 1

    def mut(fn):
        box = {"d": copy.deepcopy(base), "d3": d3_base}
        fn(box)
        return box["d"], box["d3"]

    def drop_cap(b):
        b["d"]["cases"][0].pop("capabilityCode", None)

    def bad_cap(b):
        b["d"]["cases"][0]["capabilityCode"] = "M99"

    def drop_cap_registry(b):
        b["d"]["capabilityCodes"].pop("M6", None)

    def drop_fair(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c["id"] != "FAIR-01"]

    def fair_to_positive(b):
        for c in b["d"]["cases"]:
            if c["id"] == "FAIR-02":
                c["kind"] = "positive"

    def drop_critical_negative(b):
        b["d"]["cases"] = [c for c in b["d"]["cases"] if c["id"] != "MATCH-02"]

    def pii_leak(b):
        b["d"]["cases"][0]["piiFree"] = False

    def status_released(b):
        b["d"]["status"] = "released"

    def drop_scale_route(b):
        b["d"]["scope"] = b["d"]["scope"].replace(f"≥{SCALE_TARGET}", "")
        b["d"]["seedNote"] = b["d"]["seedNote"].replace(f"≥{SCALE_TARGET}", "")

    def drop_catalog(b):
        b["d"].pop("regulatoryBasisCatalog", None)

    def shrink_cases(b):
        b["d"]["cases"] = b["d"]["cases"][:SEED_MIN_CASES - 1]

    # --- A14 跨文档真源探针 ---------------------------------------------------
    def truth_add_cap(b):
        b["d3"] = b["d3"].replace("| ORCHESTRATION |", "| M8 | 幽灵能力 | M02 | 寻源 | 02 卷 |\n| ORCHESTRATION |", 1)

    def truth_drop_cap(b):
        b["d3"] = b["d3"].replace("| M6 | 投标响应助手", "| ~~M6~~ | 投标响应助手", 1)

    def truth_delete_section(b):
        # 整行换掉章节号：只改标题后缀仍会被 split("### 1.1") 命中，必须让锚点真正消失
        b["d3"] = re.sub(r"^### 1\.1 .*$", "### 1.9 能力码章节已废止",
                         b["d3"], count=1, flags=re.M)

    # --- A15 scalePlan 结构化探针 ---------------------------------------------
    def drop_scale_plan(b):
        b["d"].pop("scalePlan", None)

    def shrink_target(b):
        b["d"]["scalePlan"]["targetCases"] = 50

    def fake_completed(b):
        b["d"]["scalePlan"]["status"] = "completed"

    def drop_annotation_spec(b):
        b["d"]["scalePlan"]["annotationSpec"] = "待补"

    def drop_phase(b):
        b["d"]["scalePlan"]["phases"] = b["d"]["scalePlan"]["phases"][:1]

    probes = [
        ("删一条用例的 capabilityCode", drop_cap, "A4"),
        ("注入未登记能力码 M99", bad_cap, "A10"),
        ("删 capabilityCodes 中 M6 登记", drop_cap_registry, "A10"),
        ("删 G15 关键偏见用例 FAIR-01", drop_fair, "A11"),
        ("FAIR-02 改成正向", fair_to_positive, "A11"),
        ("删关键负向 MATCH-02", drop_critical_negative, "A12"),
        ("piiFree 改 false", pii_leak, "A6"),
        ("status 改成 released", status_released, "A9"),
        ("抹掉 ≥200 规模路线文本", drop_scale_route, "A13"),
        ("删合规依据目录", drop_catalog, "A13"),
        ("用例数压到种子下界以下", shrink_cases, "种子用例数"),
        ("D3-v2 §1.1 真源表新增未登记能力码 M8", truth_add_cap, "A14"),
        ("D3-v2 §1.1 真源表删 M6 行", truth_drop_cap, "A14"),
        ("D3-v2 §1.1 章节锚点被抹除", truth_delete_section, "A14"),
        ("删 scalePlan 整块", drop_scale_plan, "A15"),
        ("scalePlan.targetCases 压到 50", shrink_target, "A15"),
        ("scalePlan.status 伪造 completed", fake_completed, "A15"),
        ("scalePlan.annotationSpec 抹掉标注规范", drop_annotation_spec, "A15"),
        ("scalePlan.phases 删到只剩 1 阶段", drop_phase, "A15"),
    ]
    all_ok = True
    for desc, fn, expect in probes:
        d3, truth = mut(fn)
        ok, msgs = validate(d3, truth)
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
    ap.add_argument("--d3v2", default=DEFAULT_D3V2, help="能力码真源 D3-v2 §1.1 文档路径")
    ap.add_argument("--self-test", action="store_true", help="注入式负向反证")
    args = ap.parse_args()
    if not os.path.exists(args.evalset):
        print(f"[FAIL] 评测集文件不存在: {args.evalset}")
        return 1
    if args.self_test:
        return self_test(args.evalset, args.d3v2)
    return 0 if check(args.evalset, args.d3v2) else 1


if __name__ == "__main__":
    sys.exit(main())
