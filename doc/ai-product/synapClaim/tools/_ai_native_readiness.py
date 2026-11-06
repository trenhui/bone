#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""synapClaim · AI 原生 L3 就绪度复核器（循环复核的可重复终止判据）。

为什么需要它
------------
前几轮复核反复得出「不是 AI 原生」，但结论靠人读文档推断，无法作为循环的终止条件。
本脚本只读取仓库里**可静态核验的事实**，逐项判定 §1.7 ③ 的前置条件，输出：
  · 每项判据状态（SATISFIED / BLOCKED / RUNTIME_ONLY）与受控原因码
  · 当前可判定的成熟度档位
  · l3Claimable（全部判据 SATISFIED 才为 true）

刻意不做的三件事（防假绿）：
  · 不编造准确率 / 自动化率 / aiCoverage —— 那是运行期数据，静态文件里没有；
    相关判据一律标 RUNTIME_ONLY 并阻断，不因「没数据」判定为「达标」；
  · 不读取活库、不起服务、不改任何文件；
  · 不因为「代码里已有判据」就把对应能力算作已发生（check.sh 里的门禁接线
    只证明可证伪，不证明模型跑过真实案件）。

运行：
  python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py
  python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test

--self-test 用内存变造的事实做负向探针：删门禁接线 / 改目录默认值 / 注入 provider /
改运行期参与率 / 分别摘除飞轮三要素，
断言对应判据发生翻转。探针不翻转 ⇒ 判据恒真 ⇒ 本脚本自身判红（exit 1）。
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CLAIM = ROOT / "bone-apps" / "synap-claim"

PATHS = {
    "app_yml": CLAIM / "src" / "main" / "resources" / "application.yml",
    "baseline": ROOT / "bone-ai" / "bone-ai-rag" / "eval" / "baseline-metrics.json",
    "evalset": CLAIM / "eval" / "ai-decision-evalset.json",
    "check_sh": ROOT / "scripts" / "check.sh",
    "v15": CLAIM / "src" / "main" / "resources" / "db" / "migration"
    / "V15__synap_claim_catalog_admission.sql",
    "golive": ROOT / "doc" / "ai-product" / "synapClaim" / "08-go-live",
    "java_main": CLAIM / "src" / "main" / "java" / "com" / "bone" / "claim",
    "e2e_result": ROOT / "outputs" / "claims-e2e-result.json",
}

SATISFIED = "SATISFIED"
BLOCKED = "BLOCKED"
RUNTIME_ONLY = "RUNTIME_ONLY"


def read_text(key: str) -> str:
    path = PATHS[key]
    return path.read_text(encoding="utf-8") if path.exists() else ""


def load_facts() -> dict:
    baseline_raw = read_text("baseline")
    evalset_raw = read_text("evalset")
    try:
        baseline = json.loads(baseline_raw) if baseline_raw else {}
    except json.JSONDecodeError:
        baseline = {}
    try:
        evalset = json.loads(evalset_raw) if evalset_raw else {}
    except json.JSONDecodeError:
        evalset = {}
    golive_md = sorted(PATHS["golive"].glob("*.md")) if PATHS["golive"].exists() else []
    java_files = list(PATHS["java_main"].rglob("*.java")) if PATHS["java_main"].exists() else []
    # 飞轮下游三要素：实现类 / 样本源生产调用 / 校准计算生产调用。
    # 只认非注释、非 import 的**真实调用**：import 一个数据类不算接线（2026-11-06 循环复核发现）。
    main_sources = []
    for src in java_files:
        if src.stem in ("AiConfidenceCalibration", "AiModelCard"):
            continue
        lines = [
            ln for ln in src.read_text(encoding="utf-8", errors="ignore").splitlines()
            if not ln.strip().startswith(("//", "/*", "*", "*/"))
            and not ln.strip().startswith("import ")
        ]
        main_sources.append((src.stem, lines))

    calibration_class_present = any(x.stem == "AiConfidenceCalibration" for x in java_files)
    compute_re = re.compile(
        r"\bAiConfidenceCalibration\s*\.\s*"
        r"(evaluate|expectedCalibrationError|brierScore|challengerAdmissible)\s*\("
    )
    calibration_compute_wired = any(
        compute_re.search(ln) for _, lines in main_sources for ln in lines
    )
    calibration_sample_source_wired = any(
        re.search(r"\bBlindLabelCalibrationSamples\s*\.\s*from\s*\(", ln)
        for _, lines in main_sources for ln in lines
    )
    e2e_raw = read_text("e2e_result")
    try:
        runtime = (json.loads(e2e_raw) or {}).get("runtime") if e2e_raw else None
    except json.JSONDecodeError:
        runtime = None
    return {
        "provider_env": os.environ.get("BONE_AI_GATEWAY_BASE_URL", "").strip(),
        "parse_mode_env": os.environ.get("BONE_CLAIM_DOCUMENT_PARSE_MODE", "").strip(),
        "app_yml": read_text("app_yml"),
        "check_sh": read_text("check_sh"),
        "v15": read_text("v15"),
        "baseline": baseline,
        "evalset": evalset,
        "golive": [(p.name, p.read_text(encoding="utf-8")[:400]) for p in golive_md],
        "java_names": [p.stem for p in java_files],
        "calibration_class_present": calibration_class_present,
        "calibration_compute_wired": calibration_compute_wired,
        "calibration_sample_source_wired": calibration_sample_source_wired,
        "e2e_runtime": runtime,
    }


def gate(gid: str, status: str, reason: str, evidence: str) -> dict:
    return {"id": gid, "status": status, "reason": reason, "evidence": evidence}


def evaluate(f: dict) -> list[dict]:
    gates = []

    # G0-1 真实 provider：环境变量为准；yml 里的空默认值不算配置。
    gates.append(
        gate(
            "PROVIDER_CONFIGURED",
            SATISFIED if f["provider_env"] else BLOCKED,
            "AI_GATEWAY_BASE_URL_MISSING" if not f["provider_env"] else "ADMITTED",
            "BONE_AI_GATEWAY_BASE_URL / application.yml:79",
        )
    )

    # G0-2 非 ML 基线：declared 只是声明口径，wired 才是真实埋点。
    metrics = f["baseline"].get("metrics", f["baseline"].get("baselines", []))
    if isinstance(metrics, dict):
        items = list(metrics.values())
    elif isinstance(metrics, list):
        items = metrics
    else:
        items = []
    statuses = [str(m.get("status", "")) for m in items if isinstance(m, dict)]
    wired = bool(statuses) and all(s == "wired" for s in statuses)
    gates.append(
        gate(
            "BASELINE_WIRED",
            SATISFIED if wired else BLOCKED,
            "BASELINE_DECLARED_NOT_WIRED" if statuses else "BASELINE_METRICS_MISSING",
            f"baseline-metrics.json: {len(statuses)} 项，wired={sum(s == 'wired' for s in statuses)}",
        )
    )

    # G0-3 冻结集：active + payloadHash 才可用于回归门禁。
    ev = f["evalset"]
    active = str(ev.get("status", "")).lower() == "active"
    payload = str(ev.get("payloadHash", ev.get("frozenSetHash", ""))).strip()
    gates.append(
        gate(
            "FROZEN_EVALSET_ACTIVE",
            SATISFIED if (active and payload) else BLOCKED,
            "FROZEN_SET_NOT_ACTIVE" if not active else "FROZEN_HASH_MISSING",
            f"ai-decision-evalset.json: status={ev.get('status', 'N/A')}",
        )
    )

    # G0-4 评测门禁接线：必须带 --check，report-only 恒绿是假绿。
    wired_lines = [
        ln
        for ln in f["check_sh"].splitlines()
        if not ln.lstrip().startswith("#")
        and ("check-eval-baseline.py" in ln or "check-claim-ai-evalset.py" in ln)
        and "--check" in ln
    ]
    gates.append(
        gate(
            "EVAL_GATES_WIRED",
            SATISFIED if len(wired_lines) >= 2 else BLOCKED,
            "EVAL_GATE_NOT_WIRED_OR_REPORT_ONLY" if len(wired_lines) < 2 else "ADMITTED",
            f"check.sh 真实调用行 {len(wired_lines)} 条",
        )
    )

    # G1 感知层：默认 text（PDF 文本层正则），图像/翻拍件转人工。
    parse_model = f["parse_mode_env"].lower() == "llm" and bool(f["provider_env"])
    gates.append(
        gate(
            "PERCEPTION_MODEL_DRIVEN",
            SATISFIED if parse_model else BLOCKED,
            "TEXT_LAYER_ONLY" if not parse_model else "ADMITTED",
            "BONE_CLAIM_DOCUMENT_PARSE_MODE / application.yml:57",
        )
    )

    # G2 知识层：目录准入默认 NONE = 配了不生效。
    default_none = "DEFAULT 'NONE'" in f["v15"]
    gates.append(
        gate(
            "CATALOG_ADMISSION_ACTIVE",
            BLOCKED if default_none else SATISFIED,
            "CATALOG_POLICY_DEFAULT_NONE" if default_none else "ADMITTED",
            "V15__synap_claim_catalog_admission.sql",
        )
    )

    # G4 合规就绪：08-go-live/01–13 分册不得停留在 draft。
    drafts = [
        name
        for name, head in f["golive"]
        if name[:2].isdigit() and name[:2] != "00" and re.search(r"^currency:\s*draft", head, re.M)
    ]
    gates.append(
        gate(
            "GOLIVE_NOT_DRAFT",
            SATISFIED if not drafts else BLOCKED,
            f"GOLIVE_DRAFT_{len(drafts)}" if drafts else "ADMITTED",
            "08-go-live/ frontmatter currency",
        )
    )

    # G4 飞轮下游：三要素同时成立才算接线（缺一即 BLOCKED，防 import 行假绿）。
    c_class = bool(f.get("calibration_class_present"))
    c_sample = bool(f.get("calibration_sample_source_wired"))
    c_compute = bool(f.get("calibration_compute_wired"))
    if not c_class:
        reason = "CALIBRATION_CONSUMER_MISSING"
    elif not c_compute:
        # 计算调用是飞轮的定义性动作，优先归因：import-only 假绿必须精确指向此处。
        reason = "CALIBRATION_COMPUTE_NOT_WIRED"
    elif not c_sample:
        reason = "SAMPLE_SOURCE_NOT_WIRED"
    else:
        reason = "ADMITTED"
    gates.append(
        gate(
            "FLYWHEEL_DOWNSTREAM",
            SATISFIED if (c_class and c_sample and c_compute) else BLOCKED,
            reason,
            f"实现类={c_class} 盲标样本源生产调用={c_sample} "
            f"校准计算生产调用={c_compute}（import / 注释 / 单测均不算接线）",
        )
    )

    # 运行期证据：消费前后端 E2E 实跑产物，不再只靠静态推断。
    rt = f["e2e_runtime"]
    if not rt or not isinstance(rt, dict) or rt.get("nodesScanned") is None:
        gates.append(
            gate(
                "RUNTIME_MODEL_COVERAGE",
                RUNTIME_ONLY,
                "RUNTIME_EVIDENCE_REQUIRED",
                "未找到 outputs/claims-e2e-result.json 的 runtime 段；须先跑前后端 E2E",
            )
        )
    else:
        nodes = int(rt.get("nodesScanned") or 0)
        rate = float(rt.get("participationRate") or 0)
        participated = int(rt.get("aiParticipated") or 0)
        gates.append(
            gate(
                "RUNTIME_MODEL_COVERAGE",
                SATISFIED if (nodes > 0 and rate > 0 and participated > 0) else BLOCKED,
                "MODEL_PARTICIPATION_ZERO" if rate <= 0 else "ADMITTED",
                f"E2E 实跑：nodes={nodes} aiParticipated={participated} "
                f"participationRate={rate:.4f} capabilityMissing={rt.get('capabilityMissing')}",
            )
        )
    return gates


def current_level(gates: list[dict]) -> str:
    by_id = {g["id"]: g["status"] for g in gates}
    if by_id.get("PROVIDER_CONFIGURED") != SATISFIED:
        return "L0（运行态：AI 全链路降级，纯规则路径）"
    if by_id.get("PERCEPTION_MODEL_DRIVEN") != SATISFIED:
        return "L1（AI 辅助，可摘除）"
    return "L2（AI 承担部分主负载，仍待飞轮与 MRM）"


def self_test(f: dict) -> bool:
    base = {g["id"]: g["status"] for g in evaluate(f)}
    probes = []

    f1 = dict(f, check_sh=f["check_sh"].replace("--check", ""))
    probes.append(("删门禁 --check 接线", base["EVAL_GATES_WIRED"], evaluate(f1)))

    f2 = dict(f, v15=f["v15"].replace("DEFAULT 'NONE'", "DEFAULT 'MANDATORY'"))
    probes.append(("目录默认值改为 MANDATORY", base["CATALOG_ADMISSION_ACTIVE"], evaluate(f2)))

    f3 = dict(f, provider_env="http://localhost:8000/v1")
    probes.append(("注入 provider 环境变量", base["PROVIDER_CONFIGURED"], evaluate(f3)))

    rt = dict(f.get("e2e_runtime") or {})
    rt.update({"nodesScanned": 88, "participationRate": 0.5, "aiParticipated": 44})
    probes.append(("运行期参与率改为 0.5", base["RUNTIME_MODEL_COVERAGE"], evaluate(dict(f, e2e_runtime=rt))))
    for key, label in [
        ("calibration_class_present", "摘除校准实现类"),
        ("calibration_sample_source_wired", "摘除盲标样本源生产调用"),
        ("calibration_compute_wired", "摘除校准计算生产调用（import 假绿回归钉子）"),
    ]:
        probes.append((label, base["FLYWHEEL_DOWNSTREAM"],
                       evaluate(dict(f, **{key: not f.get(key, False)}))))

    ok = True
    for label, before, after in probes:
        gid = {
            "删门禁 --check 接线": "EVAL_GATES_WIRED",
            "目录默认值改为 MANDATORY": "CATALOG_ADMISSION_ACTIVE",
            "注入 provider 环境变量": "PROVIDER_CONFIGURED",
            "运行期参与率改为 0.5": "RUNTIME_MODEL_COVERAGE",
            "摘除校准实现类": "FLYWHEEL_DOWNSTREAM",
            "摘除盲标样本源生产调用": "FLYWHEEL_DOWNSTREAM",
            "摘除校准计算生产调用（import 假绿回归钉子）": "FLYWHEEL_DOWNSTREAM",
        }[label]
        now = {g["id"]: g["status"] for g in after}[gid]
        flipped = before != now
        print(f"  [{'OK' if flipped else 'FAIL'}] {label}: {before} → {now}")
        ok = ok and flipped
    return ok


def main() -> int:
    facts = load_facts()
    if "--self-test" in sys.argv:
        print("负向探针（判据必须翻转，否则判据恒真=假绿）：")
        passed = self_test(facts)
        print("自测结果：", "PASS" if passed else "FAIL")
        return 0 if passed else 1

    gates = evaluate(facts)
    counts = {s: sum(g["status"] == s for g in gates) for s in (SATISFIED, BLOCKED, RUNTIME_ONLY)}
    claimable = counts[BLOCKED] == 0 and counts[RUNTIME_ONLY] == 0
    print("synapClaim · AI 原生 L3 就绪度复核（静态事实 + 前后端 E2E 运行期证据）")
    print(f"判据 {len(gates)} 项：SATISFIED {counts[SATISFIED]} / "
          f"BLOCKED {counts[BLOCKED]} / RUNTIME_ONLY {counts[RUNTIME_ONLY]}")
    print(f"当前可判定成熟度：{current_level(gates)}")
    print(f"l3Claimable = {str(claimable).lower()}")
    print("-" * 72)
    for g in gates:
        print(f"  {g['status']:<13} {g['id']:<28} {g['reason']}")
    print("-" * 72)
    rt = facts.get("e2e_runtime")
    if rt:
        print(f"运行期证据（前后端 E2E 实跑）：nodes={rt.get('nodesScanned')} "
              f"aiParticipated={rt.get('aiParticipated')} "
              f"participationRate={rt.get('participationRate')}")
    print("说明：无 E2E 产物时该判据为 RUNTIME_ONLY 并阻断；有产物但参与率为 0 时直接判 BLOCKED。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
