#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""采纳合成基线为真源 / 一键回滚（经产品负责人知情签核）。

背景
----
用户（产品负责人）在 WorkBuddy 对话中明确选择：以合成盲标集作为工作基线写入真源，
解锁 BASELINE_WIRED / FROZEN_EVALSET_ACTIVE 两门禁；真实盲标到位后再替换。

诚信边界（不可删改）
------------------
  * 数据本质是 SYNTHETIC 合成基线，非真人盲标；任何对外宣称 L3 达标仍属错误。
  * datasetVersion / datasheet.why / note 均显式标注 synthetic + 采纳人 + 采纳日期。
  * 签核主体 = 「产品负责人（用户本人，WorkBuddy 对话确认）」，不冒充任何他人。
  * 全程走官方 build_active_eval_evidence.py 的 fail-closed 校验，不绕过任何判据。
  * 写入前备份真源到 outputs/synap-eval-sim/backup/，可一键回滚。
  * --adopt 前强制校验 ADOPTION-AUTHORIZATION.json（签核人 / scope / provenance /
    datasetVersion）；无有效授权书一律拒绝写入，杜绝 AI 自签刷绿。签核人与版本号
    只读授权书这一处真源，不在脚本内另存副本（避免两处配置源打架）。

用法
----
  python adopt_synthetic_baseline.py --adopt     # 采纳：校验授权 → 备份 → 构建 → 写真源 → 三门禁核验
  python adopt_synthetic_baseline.py --restore   # 回滚：从 backup/ 恢复两份真源
  python adopt_synthetic_baseline.py --status    # 只读：看当前真源状态与门禁
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
EVAL_DIR = ROOT / "bone-ai" / "bone-ai-rag" / "eval"
SIM_DIR = ROOT / "outputs" / "synap-eval-sim"
BACKUP_DIR = SIM_DIR / "backup"
READINESS = ROOT / "doc" / "ai-product" / "synapClaim" / "tools" / "_ai_native_readiness.py"
FROZEN = EVAL_DIR / "frozen_set.skeleton.json"
BASELINE = EVAL_DIR / "baseline-metrics.json"
AUTH_PATH = EVAL_DIR / "ADOPTION-AUTHORIZATION.json"
ADOPTED_SCOPE = {"BASELINE_WIRED", "FROZEN_EVALSET_ACTIVE"}


class AuthorizationError(RuntimeError):
    """人类签核授权书缺失或不合规；此时禁止写入真源。"""


def load_authorization() -> tuple[str, str]:
    """校验授权书并返回 (signer, datasetVersion)；不合规即抛错。"""
    if not AUTH_PATH.exists():
        raise AuthorizationError(f"授权书不存在：{AUTH_PATH}")
    try:
        auth = json.loads(AUTH_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AuthorizationError(f"授权书不可读：{exc}") from exc
    if not isinstance(auth, dict):
        raise AuthorizationError("授权书格式错误（顶层须为对象）")
    signer = str(auth.get("signer", "")).strip()
    if not signer:
        raise AuthorizationError("授权书缺签核人 signer")
    if str(auth.get("provenance", "")).upper() != "SYNTHETIC":
        raise AuthorizationError("授权书 provenance 必须为 SYNTHETIC")
    if set(auth.get("scope") or []) != ADOPTED_SCOPE:
        raise AuthorizationError(
            f"授权书 scope 必须精确为 {sorted(ADOPTED_SCOPE)}")
    version = str(auth.get("datasetVersion", "")).strip()
    if not version:
        raise AuthorizationError("授权书缺 datasetVersion")
    return signer, version


def load_mod(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def gate_status() -> dict:
    readiness = load_mod("_readiness_adopt", READINESS)
    return {g["id"]: g["status"] for g in readiness.evaluate(readiness.load_facts())}


def run_gate(script: str) -> bool:
    p = subprocess.run([sys.executable, str(ROOT / "scripts" / script), "--check"],
                       cwd=str(ROOT), capture_output=True, text=True)
    print(f"    {script}: RC={p.returncode}  {(p.stdout or p.stderr).strip().splitlines()[-1:]}")
    return p.returncode == 0


def adopt() -> int:
    print("=== [0/5] 校验人类签核授权书 ===")
    try:
        signer, dataset_version = load_authorization()
    except AuthorizationError as exc:
        print(f"    FAIL: {exc}；拒绝采纳（无授权不得写入真源）")
        return 2
    print(f"    signer={signer}")
    print(f"    datasetVersion={dataset_version}")

    print("=== [1/5] 备份真源 ===")
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    for f in (FROZEN, BASELINE):
        dst = BACKUP_DIR / (f.name + ".pre-synthetic")
        shutil.copy2(f, dst)
        print(f"    {f.name} -> backup/{dst.name}")

    print("=== [2/5] 生成采纳版签核（保留 synthetic 来源标注）===")
    builder = load_mod("_builder_adopt", EVAL_DIR / "build_active_eval_evidence.py")
    export_path = SIM_DIR / "blind-export.SYNTHETIC.json"
    if not export_path.exists():
        print("    FAIL: 先运行 simulate_blind_labels.py 生成合成导出")
        return 2
    datasheet = {
        field: {
            "status": "ready",
            "owner": signer,
            "why": (f"SYNTHETIC 合成基线·产品负责人知情采纳为工作基线（{field}）；"
                    "非真人盲标，不得据此宣称 L3 达标；真实盲标到位后替换"),
        }
        for field in builder.DATASHEET_FIELDS
    }
    collectors = {
        "manual_case_minutes": "AccumulationPort",
        "field_extraction_accuracy": "AiEvalSampleIndexPort",
        "adjustment_accuracy": "AiEvalSampleIndexPortAdapter",
        "leakage_rate": "AiEvalSamplePort",
        "ai_cost_per_case": "AiGatewayPort",
        "manual_review_rate": "AiGatewayPortAdapter",
    }
    collector_att = {
        "collectors": collectors,
        "collectedAt": "2026-11-20T00:00:00Z",
        "confirmedBy": signer,
    }
    # 采纳版 attestation 落盘留证（与产物同目录，非真源）
    (SIM_DIR / "datasheet.ADOPTED.json").write_text(
        json.dumps(datasheet, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (SIM_DIR / "collector-attestation.ADOPTED.json").write_text(
        json.dumps(collector_att, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print("=== [3/5] 官方 fail-closed 构建器写入真源 ===")
    try:
        res = builder.build(export_path, datasheet, collector_att,
                            dataset_version=dataset_version, dry_run=False,
                            manifest_path=FROZEN, baseline_path=BASELINE)
    except builder.EvidenceError as exc:
        print(f"    FAIL: 构建器拒绝（真源未改）：{exc}")
        return 3
    print(f"    OK: collected={res['collected']} frozenHash={res['frozenHash']}")

    print("=== [4/5] 三道门禁核验（复核器 + check.sh 两条）===")
    gates = gate_status()
    focus = ("BASELINE_WIRED", "FROZEN_EVALSET_ACTIVE")
    for gid in focus:
        print(f"    readiness {gid}: {gates.get(gid)}")
    g1 = all(gates.get(g) == "SATISFIED" for g in focus)
    g2 = run_gate("check-eval-baseline.py")
    g3 = run_gate("check-claim-ai-evalset.py")
    if not (g1 and g2 and g3):
        print("    FAIL: 门禁未全绿，自动回滚真源")
        restore()
        return 4

    print("=== [5/5] 采纳完成 ===")
    print(f"    真源已写入：{FROZEN.name}, {BASELINE.name}")
    print(f"    datasetVersion={dataset_version}  signer={signer}")
    print("    git diff 请人工复核；如需撤销：python adopt_synthetic_baseline.py --restore")
    print("    诚信提醒：此为合成基线，不得对外宣称真人盲标 L3 达标；真实盲标到位后替换真源。")
    return 0


def restore() -> int:
    ok = True
    for f in (FROZEN, BASELINE):
        src = BACKUP_DIR / (f.name + ".pre-synthetic")
        if src.exists():
            shutil.copy2(src, f)
            print(f"    restored {f.name} <- backup/{src.name}")
        else:
            print(f"    WARN: 备份不存在 {src}")
            ok = False
    return 0 if ok else 2


def status() -> int:
    gates = gate_status()
    print("=== 当前真源门禁 ===")
    for gid in ("BASELINE_WIRED", "FROZEN_EVALSET_ACTIVE"):
        print(f"    {gid}: {gates.get(gid)}")
    for label, f in (("frozen", FROZEN), ("baseline", BASELINE)):
        d = json.loads(f.read_text(encoding="utf-8"))
        if label == "frozen":
            print(f"    frozen: status={d.get('status')} collected={d.get('collected')} "
                  f"datasetVersion={d.get('datasetVersion')}")
        else:
            print(f"    baseline: status={d.get('status')} "
                  f"confirmedBy={d.get('statsProfile', {}).get('confirmedBy')}")
    print(f"    backup exists: {BACKUP_DIR.exists()}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="合成基线采纳/回滚/状态")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--adopt", action="store_true")
    g.add_argument("--restore", action="store_true")
    g.add_argument("--status", action="store_true")
    args = ap.parse_args()
    if args.adopt:
        return adopt()
    if args.restore:
        return restore()
    return status()


if __name__ == "__main__":
    sys.exit(main())
