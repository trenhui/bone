#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""业界最佳实践 · 合成盲标集生成器（SYNTHETIC，仅用于评测管线联调与判据自证）。

重要边界
--------
本脚本生成的数据是**合成数据**，不是真实人工盲标：
  * 不得把它当作「≥300 条真实人工盲标」去对外宣称 L3 达标；
  * 不得静默覆盖生产真源 frozen_set.skeleton.json / baseline-metrics.json；
  * 数据说明书（datasheet）owner 与 confirmedBy 显式标注 SIMULATION，
    任何审计读者都能自证其合成来源。

它做什么
--------
1. 依业界最佳实践生成 300 条结构完整的解盲配对样本：
   - 5 个切片维度（ticketType/hospitalTier/amountBand/tenant/rephotographed）各有 ≥2 取值；
   - 4 层分层（positive/negative/boundary/adversarial）覆盖；
   - label_payload 只含「被盲化」的案件事实，绝不混入 AI 痕迹键；
   - payloadHash / frozenHash / inputSnapshotHash 全部实算，无一手写。
2. 调用官方 build_active_eval_evidence.py 的 fail-closed 全链路（负向探针全部仍生效）。
3. 在 tempfile 路径上用官方 _ai_native_readiness.py 复核，证明两门禁在数据到位时确实翻绿。
4. 产物写入 outputs/synap-eval-sim/（非真源），并打印可复现命令与采纳须知。
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import random
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EVAL_DIR = ROOT / "bone-ai" / "bone-ai-rag" / "eval"
OUT_DIR = ROOT / "outputs" / "synap-eval-sim"
READINESS = ROOT / "doc" / "ai-product" / "synapClaim" / "tools" / "_ai_native_readiness.py"
N_CASES = 300
SEED = 20261120

TICKET_TYPES = ["门诊发票", "住院发票", "费用清单", "医保结算单", "处方笺"]
HOSPITAL_TIERS = ["三甲", "三乙", "二甲", "二乙", "社区"]
AMOUNT_BANDS = ["0-1k", "1k-1w", "1w-5w", "5w-20w", "20w+"]
TENANTS = ["1", "2", "3"]
REPHOTO = ["是", "否"]
STRATA = ["positive", "negative", "boundary", "adversarial"]
DECISIONS = ["PASS", "REJECT", "MANUAL_REVIEW"]


def load_mod(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def canonical(obj) -> str:
    return json.dumps(obj, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def gen_rows() -> list[dict]:
    rng = random.Random(SEED)
    rows = []
    for i in range(N_CASES):
        ticket = TICKET_TYPES[i % len(TICKET_TYPES)]
        tier = HOSPITAL_TIERS[(i // 2) % len(HOSPITAL_TIERS)]
        band = AMOUNT_BANDS[(i // 3) % len(AMOUNT_BANDS)]
        tenant = TENANTS[i % len(TENANTS)]
        rephoto = REPHOTO[i % len(REPHOTO)]
        stratum = STRATA[i % len(STRATA)]

        # 被盲化的案件事实载荷：只含客观事实键，绝无 AI 痕迹键。
        payload = {
            "claimId": f"2664{10000000 + i:08d}"[:12],
            "tenantId": tenant,
            "bizIdentityCode": "1:YCBX",
            "ticketType": ticket,
            "hospitalTier": tier,
            "amountBand": band,
            "rephotographed": rephoto,
            "diagnosisCode": f"SYM-{rng.randint(100, 999)}",
            "itemCount": rng.randint(1, 12),
        }
        payload_c = canonical(payload)
        payload_hash = sha256_text(payload_c)

        # 人工盲标结论（合成）：decision 必填。
        decision = DECISIONS[rng.randint(0, 2)]
        label_result = {
            "decision": decision,
            "reviewerNote": "SYNTHETIC 盲标结论（best-practice 模拟，非真人标注）",
            "labelerType": "SIMULATED",
        }
        # AI 建议（合成）：aiConfidence 必须在 [0,1]，decisionResult 必填。
        conf = round(rng.uniform(0.35, 0.98), 4)
        ai_suggestion = {
            "decisionResult": DECISIONS[rng.randint(0, 2)],
            "aiConfidence": conf,
            "model": "simulated-model-v0",
        }
        rows.append({
            "task_no": f"SIM-BLIND-{i:04d}",
            "claim_id": payload["claimId"],
            "assignee_id": f"SIM-LABELER-{i % 17:02d}",
            "status": "UNBLINDED",
            "agreement": "PAIRED",
            "label_payload_json": payload_c,
            "payload_hash": payload_hash,
            "label_result_json": canonical(label_result),
            "ai_suggestion_json": canonical(ai_suggestion),
            "unblinded_at": "2026-11-20T00:00:00Z",
            "stratum": stratum,
            "slices": {
                "ticketType": ticket,
                "hospitalTier": tier,
                "amountBand": band,
                "tenant": tenant,
                "rephotographed": rephoto,
            },
        })
    return rows


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    rows = gen_rows()
    export_path = OUT_DIR / "blind-export.SYNTHETIC.json"
    export_path.write_text(json.dumps({"rows": rows}, ensure_ascii=False, indent=2) + "\n",
                           encoding="utf-8")
    export_sha = sha256_text(export_path.read_text(encoding="utf-8"))

    # 数据说明书：诚实标注 synthetic 来源，任何读者可自证。
    datasheet = {
        field: {
            "status": "ready",
            "owner": "SIMULATION (best-practice synthetic, NOT human blind label)",
            "why": f"合成数据·仅用于评测管线联调/{field}；非真人标注，不得宣称 L3 达标",
        }
        for field in ("source", "rights", "desensitization", "temporal")
    }
    builder = load_mod("_builder_for_sim", EVAL_DIR / "build_active_eval_evidence.py")
    stems = builder._java_stems()
    pick = sorted(s for s in stems if s.endswith("Adapter") or s.endswith("Port"))[:6]
    if len(pick) < 6:
        pick = sorted(stems)[:6]
    metric_ids = [m["id"] for m in json.loads(
        (EVAL_DIR / "baseline-metrics.json").read_text(encoding="utf-8"))["metrics"]]
    collectors = {mid: pick[i % len(pick)] for i, mid in enumerate(metric_ids)}
    collector_att = {
        "collectors": collectors,
        "collectedAt": "2026-11-20T00:00:00Z",
        "confirmedBy": "SIMULATION - NOT a human sign-off; product owner must confirm to adopt",
    }
    ds_path = OUT_DIR / "datasheet.SYNTHETIC.json"
    ca_path = OUT_DIR / "collector-attestation.SYNTHETIC.json"
    ds_path.write_text(json.dumps(datasheet, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    ca_path.write_text(json.dumps(collector_att, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # 官方 fail-closed 构建器：写入模拟输出路径，绝不覆盖真源。
    manifest_path = OUT_DIR / "frozen_set.SYNTHETIC.json"
    baseline_path = OUT_DIR / "baseline.SYNTHETIC.json"
    try:
        res = builder.build(export_path, datasheet, collector_att,
                            dataset_version="synthetic-sim-v1", dry_run=False,
                            manifest_path=manifest_path, baseline_path=baseline_path)
    except builder.EvidenceError as exc:
        print(f"FAIL: 官方构建器 fail-closed 拒绝：{exc}")
        return 3
    print(f"OK: 官方构建器通过　collected={res['collected']} frozenHash={res['frozenHash']}")
    print(f"    export sha256={export_sha}")
    print(f"    manifest={manifest_path.name}  baseline={baseline_path.name}")

    # 官方复核器：PATHS 覆盖到模拟产物，验证两门禁确实翻绿（与 self_test 同法）。
    readiness = load_mod("_readiness_for_sim", READINESS)
    original = dict(readiness.PATHS)
    try:
        readiness.PATHS["frozen_manifest"] = manifest_path
        readiness.PATHS["baseline"] = baseline_path
        gates = {g["id"]: g["status"] for g in readiness.evaluate(readiness.load_facts())}
    finally:
        readiness.PATHS.clear()
        readiness.PATHS.update(original)

    focus = ("BASELINE_WIRED", "FROZEN_EVALSET_ACTIVE")
    print("--- 复核器（指向 SYNTHETIC 产物，证明数据到位即翻绿）---")
    for gid in focus:
        print(f"    {gid}: {gates.get(gid)}")
    all_green = all(gates.get(g) == readiness.SATISFIED for g in focus)
    print(f"    ⇒ {'两门禁均翻绿：评测管线端到端已通' if all_green else '未全绿，需排查'}")
    print("--- 生产真源未被触碰（仍为 skeleton/BLOCKED）---")
    print("    bone-ai/bone-ai-rag/eval/frozen_set.skeleton.json")
    print("    bone-ai/bone-ai-rag/eval/baseline-metrics.json")
    print("--- 采纳须知 ---")
    print("    合成数据不得冒充真实人工盲标宣称 L3；如需采纳为工作基线，")
    print("    须由产品负责人以真人身份签核 datasheet/collector 并显式确认合成来源。")
    return 0 if all_green else 4


if __name__ == "__main__":
    sys.exit(main())
