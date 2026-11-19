#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G0/G1 评测证据构建器：从**真实盲标导出**构建 active 冻结集 + 回填基线 provenance。

为什么需要它
------------
复核器 (_ai_native_readiness.py) 与两条门禁 (frozen_set.py / check-eval-baseline.py)
的判据是**只读真源**：它们会拒绝「手写 status / 塞任意 hash / 无 provenance」这类刷绿。
这很好，但也意味着「数据到齐后怎么安全落地」没有路径——手写 JSON 会踩 E5/V3/V7，
伪造 provenance 又违反纪律。本脚本补齐这条路径：把**真实性**编码进构建过程本身。

三条铁律（本脚本的全部价值）
--------------------------
1. fail-closed：没有真实盲标导出 / 未解盲配对 / 载荷哈希对不上 / 命中 AI 痕迹禁区键 /
   切片维度不足 / 条数不足 ⇒ **拒绝生成**，不跳过、不补默认值、不手写哈希。
2. 实算：frozenHash = sha256(canonical(cases))；inputSnapshotHash = 导出文件实算 sha256；
   sourceSymbol 必须在真实源码有声明。没有任何一个值来自「声明」而非「实算/实证」。
3. 不碰判据真源：本脚本不修改 frozen_set.py / _ai_native_readiness.py /
   check-eval-baseline.py。它只生成两个**数据文件**，且默认 dry-run，须 --commit 才写。

数据到齐后一键链路
------------------
  python build_active_eval_evidence.py \
      --blind-export <真实盲标导出.json> \
      --datasheet-attestation <签核.json> \
      --collector-attestation <签核.json> \
      --commit

自测（CI 必跑，证明造假说不过、真数据能过）
-------------------------------------------
  python build_active_eval_evidence.py --self-test
自测用内存合成 fixture 走完整断言，**绝不写真源文件**；合成样本只存在于进程内存和
tempfile 中，用完即删——它们是判据自证的探针，不是评测数据。
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import re
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple

EVAL_DIR = Path(__file__).resolve().parent
ROOT = EVAL_DIR.parents[2]

MIN_REAL_CASES = 300  # 对齐 frozen_set.skeleton.json targetSize 与 AI技术方案 §11.1
SLICE_DIMENSIONS: Tuple[str, ...] = (
    "ticketType", "hospitalTier", "amountBand", "tenant", "rephotographed")
DATASHEET_FIELDS: Tuple[str, ...] = ("source", "rights", "desensitization", "temporal")
STRATA: Tuple[str, ...] = ("positive", "negative", "boundary", "adversarial")
HEX64_RE = re.compile(r"^[0-9a-f]{64}$")

# 与 BlindLabelTask.FORBIDDEN_PAYLOAD_KEYS 同源（AI 痕迹键，盲标载荷命中即污染）。
FORBIDDEN_PAYLOAD_KEYS: frozenset[str] = frozenset({
    "aiSuggestion", "aiConfidence", "aiAvailable", "aiBasis", "confidence",
    "confidenceSource", "ruleConfidence", "ruleHits", "decisionResult",
    "decisionBasis", "modelVersion", "promptVersion",
})

REQUIRED_EXPORT_FIELDS: Tuple[str, ...] = (
    "task_no", "claim_id", "assignee_id", "status", "label_payload_json",
    "payload_hash", "label_result_json", "ai_suggestion_json", "agreement",
    "unblinded_at", "slices", "stratum",
)


class EvidenceError(Exception):
    """fail-closed 统一出口：任何真实性校验失败都走这里，绝不静默跳过。"""

    def __init__(self, code: str, detail: str):
        self.code = code
        super().__init__(f"{code}: {detail}")


def _load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None or spec.loader is None:
        raise EvidenceError("MODULE_LOAD_FAILED", str(path))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def _engine():
    return _load_module("_bone_frozen_engine_for_build", EVAL_DIR / "frozen_set.py")


def canonical(obj: Any) -> str:
    return json.dumps(obj, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _parse_json_field(raw: Any, field: str) -> Any:
    if isinstance(raw, dict):
        return raw
    if not isinstance(raw, str) or not raw.strip():
        raise EvidenceError("FIELDS_INVALID", f"{field} 必须是非空 JSON 对象字符串")
    try:
        val = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise EvidenceError("FIELDS_INVALID", f"{field} 不是合法 JSON: {exc}")
    if not isinstance(val, dict):
        raise EvidenceError("FIELDS_INVALID", f"{field} 必须是 JSON 对象")
    return val


# ---------------------------------------------------------------------------
# 1. 载入 + 真实性校验
# ---------------------------------------------------------------------------
def load_blind_label_export(path: Path) -> Tuple[List[dict], str]:
    """读取真实盲标导出（findUnblindedPaired.sql 产物）；空/坏 ⇒ 拒绝。返回 (rows, 文件sha256)。"""
    if not path.exists():
        raise EvidenceError("NO_EXPORT", f"盲标导出不存在：{path}")
    raw = path.read_text(encoding="utf-8")
    if not raw.strip():
        raise EvidenceError("NO_EXPORT", "盲标导出为空文件")
    try:
        doc = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise EvidenceError("NO_EXPORT", f"盲标导出不是合法 JSON: {exc}")
    if isinstance(doc, dict):
        rows = doc.get("rows", doc.get("items", []))
    elif isinstance(doc, list):
        rows = doc
    else:
        rows = []
    if not isinstance(rows, list) or not rows:
        raise EvidenceError("NO_EXPORT", "盲标导出无 rows/items 数组或为空")
    return rows, sha256_text(raw)


def validate_provenance_and_pairing(rows: List[dict]) -> List[dict]:
    """逐条独立复核解盲配对 + 载荷哈希 + 禁区键 + 结构。任一失败 ⇒ 整批拒绝。"""
    seen: Set[str] = set()
    cleaned: List[dict] = []
    for idx, row in enumerate(rows):
        tag = f"rows[{idx}]"
        if not isinstance(row, dict):
            raise EvidenceError("ROW_INVALID", f"{tag} 必须为对象")
        missing = [k for k in REQUIRED_EXPORT_FIELDS if k not in row]
        if missing:
            raise EvidenceError("FIELDS_INVALID", f"{tag} 缺字段 {', '.join(missing)}")
        if str(row["status"]).upper() != "UNBLINDED" or str(row["agreement"]).upper() != "PAIRED":
            raise EvidenceError("NOT_UNBLINDED_PAIRED", f"{tag} 未解盲或未成对")
        task_no = str(row["task_no"]).strip()
        if not task_no or task_no in seen:
            raise EvidenceError("DUPLICATE_TASK", f"{tag} task_no 空或重复：{task_no!r}")
        seen.add(task_no)
        if not str(row["assignee_id"]).strip() or not str(row["claim_id"]).strip():
            raise EvidenceError("FIELDS_INVALID", f"{tag} 标注人/案件号为空")

        payload = _parse_json_field(row["label_payload_json"], f"{tag}.label_payload_json")
        hit = FORBIDDEN_PAYLOAD_KEYS & set(payload.keys())
        if hit:
            raise EvidenceError("FORBIDDEN_AI_TRACE", f"{tag} 载荷含 AI 痕迹键：{sorted(hit)}")
        declared_hash = str(row["payload_hash"]).strip()
        if not HEX64_RE.match(declared_hash):
            raise EvidenceError("PAYLOAD_HASH_FORMAT", f"{tag} payload_hash 非 64hex")
        if declared_hash != sha256_text(canonical(payload)):
            raise EvidenceError("PAYLOAD_HASH_MISMATCH", f"{tag} payload_hash 与载荷实算不符（篡改）")

        result = _parse_json_field(row["label_result_json"], f"{tag}.label_result_json")
        if not str(result.get("decision", "")).strip():
            raise EvidenceError("LABEL_RESULT_EMPTY", f"{tag} 人工结论 decision 为空")
        ai = _parse_json_field(row["ai_suggestion_json"], f"{tag}.ai_suggestion_json")
        conf = ai.get("aiConfidence")
        if not isinstance(conf, (int, float)) or isinstance(conf, bool) or not 0 <= conf <= 1:
            raise EvidenceError("AI_CONFIDENCE_INVALID", f"{tag} aiConfidence 非 [0,1] 数值")
        if not str(ai.get("decisionResult", "")).strip():
            raise EvidenceError("AI_SUGGESTION_EMPTY", f"{tag} AI 建议 decisionResult 为空")

        slices = row["slices"]
        if not isinstance(slices, dict):
            raise EvidenceError("SLICES_INVALID", f"{tag}.slices 必须为对象")
        for dim in SLICE_DIMENSIONS:
            if not str(slices.get(dim, "")).strip():
                raise EvidenceError("SLICES_INVALID", f"{tag}.slices.{dim} 为空")
        stratum = str(row["stratum"]).strip()
        if stratum not in STRATA:
            raise EvidenceError("STRATUM_INVALID", f"{tag} stratum={stratum!r} 非四层之一")
        if not str(row["unblinded_at"]).strip():
            raise EvidenceError("UNBLIND_TIME_INVALID", f"{tag} unblinded_at 为空")

        cleaned.append({
            "task_no": task_no,
            "claim_id": str(row["claim_id"]),
            "payload_hash": declared_hash,
            "slices": {d: str(slices[d]) for d in SLICE_DIMENSIONS},
            "stratum": stratum,
            "label_result": result,
            "ai_suggestion": ai,
        })
    return cleaned


# ---------------------------------------------------------------------------
# 2. 构建 active 冻结集（实算哈希，禁文档抢跑字段）
# ---------------------------------------------------------------------------
def build_frozen_cases(rows: List[dict]) -> List[dict]:
    """真实样本 → frozen cases；条数不足 / 切片太薄 ⇒ 拒绝（不做「够数就放过」）。"""
    if len(rows) < MIN_REAL_CASES:
        raise EvidenceError(
            "INSUFFICIENT_REAL_CASES", f"真实解盲样本 {len(rows)} < {MIN_REAL_CASES}")
    cases = [{
        "caseId": r["task_no"],
        "split": "frozen",
        "stratum": r["stratum"],
        "payloadHash": r["payload_hash"],
        "slices": r["slices"],
    } for r in rows]
    for dim in SLICE_DIMENSIONS:
        if len({c["slices"][dim] for c in cases}) < 2:
            raise EvidenceError("V7_SLICE_TOO_THIN", f"切片维度 {dim} 取值不足 2 个")
    return cases


def build_active_manifest(cases: List[dict], datasheet_attestation: dict,
                          dataset_version: str) -> dict:
    """基于引擎骨架生成 active 清单；datasheet 未全 ready ⇒ 拒绝。哈希由引擎实算。"""
    engine = _engine()
    doc = engine.skeleton_manifest()
    for field in DATASHEET_FIELDS:
        item = datasheet_attestation.get(field)
        if not isinstance(item, dict) or str(item.get("status", "")).lower() != "ready":
            raise EvidenceError("DATASHEET_NOT_READY", f"数据说明书 {field} 未签核为 ready")
        if not str(item.get("owner", "")).strip():
            raise EvidenceError("DATASHEET_NOT_READY", f"数据说明书 {field} 缺 owner")
        doc["datasheet"][field] = {
            "status": "ready",
            "owner": str(item["owner"]).strip(),
            "why": str(item.get("why", "已签核")).strip(),
        }
    if not dataset_version.strip():
        raise EvidenceError("DATASET_VERSION_EMPTY", "datasetVersion 不能为空")
    doc["status"] = "active"
    doc["datasetVersion"] = dataset_version.strip()
    doc["cases"] = cases
    doc["collected"] = len(cases)
    doc["frozenHash"] = engine.freeze_hash(cases)
    errs = engine.verify_manifest(doc)
    if errs:
        raise EvidenceError("MANIFEST_INVALID", "; ".join(errs))
    return doc


# ---------------------------------------------------------------------------
# 3. 回填基线（provenance 三字段 + 64hex 实算 + sourceSymbol 源码可达）
# ---------------------------------------------------------------------------
def _java_stems() -> Set[str]:
    java_dir = ROOT / "bone-apps" / "synap-claim" / "synap-claim-app" / "src" / "main" / "java"
    return {p.stem for p in java_dir.rglob("*.java")} if java_dir.exists() else set()


def backfill_baseline(metrics_doc: dict, attestation: dict,
                      export_sha256: str, java_stems: Optional[Set[str]] = None) -> dict:
    """6 项指标须逐项有真实 collector 声明；顶层/statusProfile 须 confirmed。缺一即拒。"""
    if not isinstance(metrics_doc, dict) or not isinstance(metrics_doc.get("metrics"), list):
        raise EvidenceError("BASELINE_DOC_INVALID", "基线字典缺 metrics 数组")
    stems = java_stems if java_stems is not None else _java_stems()
    collectors = attestation.get("collectors")
    if not isinstance(collectors, dict) or not collectors:
        raise EvidenceError("COLLECTOR_ATTESTATION_MISSING", "缺 collector 签核")
    collected_at = str(attestation.get("collectedAt", "")).strip()
    if not collected_at:
        collected_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    out = json.loads(canonical(metrics_doc))
    for metric in out["metrics"]:
        mid = str(metric.get("id", "")).strip()
        collector = collectors.get(mid)
        if not isinstance(collector, str) or not collector.strip():
            raise EvidenceError("COLLECTOR_MISSING", f"指标 {mid} 无真实采集类签核")
        if collector.strip() not in stems:
            raise EvidenceError(
                "COLLECTOR_NOT_IN_SOURCE", f"指标 {mid} 的 sourceSymbol={collector!r} 不在源码文件 stem 中")
        metric["status"] = "wired"
        metric["sourceSymbol"] = collector.strip()
        metric["blockingReason"] = None
        metric["provenance"] = {
            "collector": collector.strip(),
            "inputSnapshotHash": export_sha256,  # 实算导出文件哈希，非手写
            "collectedAt": collected_at,
        }
    confirmed_by = str(attestation.get("confirmedBy", "")).strip()
    if not confirmed_by:
        raise EvidenceError("CONFIRMER_MISSING", "统计口径须 confirmedBy 签核")
    out["status"] = "confirmed"
    out["statsProfile"]["status"] = "confirmed"
    out["statsProfile"]["confirmedBy"] = confirmed_by
    out["statsProfile"]["confirmedAt"] = collected_at
    return out


# ---------------------------------------------------------------------------
# 4. 写盘前复算复核器口径（双重保险：即便将来判据改了，这里也拦得住）
# ---------------------------------------------------------------------------
def verify_before_write(manifest: dict, baseline: dict, export_sha256: str,
                        java_stems: Optional[Set[str]] = None) -> None:
    """复算 FROZEN_EVALSET_ACTIVE 与 BASELINE_WIRED 的全部前置条件。"""
    engine = _engine()
    if engine.verify_manifest(manifest):
        raise EvidenceError("PRECHECK_FROZEN", "写盘前：冻结集未通过引擎 verify_manifest")
    if str(manifest.get("status", "")).lower() != "active":
        raise EvidenceError("PRECHECK_FROZEN", "写盘前：冻结集 status 非 active")
    if int(manifest.get("collected") or 0) <= 0 or not manifest.get("cases"):
        raise EvidenceError("PRECHECK_FROZEN", "写盘前：冻结集无样本")
    if manifest.get("frozenHash") != engine.freeze_hash(manifest.get("cases")):
        raise EvidenceError("PRECHECK_FROZEN", "写盘前：frozenHash 非实算")

    stems = java_stems if java_stems is not None else _java_stems()
    metrics = baseline.get("metrics", [])
    if not metrics or any(str(m.get("status", "")) != "wired" for m in metrics):
        raise EvidenceError("PRECHECK_BASELINE", "写盘前：基线指标未全 wired")
    if str(baseline.get("status", "")) in ("skeleton", "", "proposed"):
        raise EvidenceError("PRECHECK_BASELINE", "写盘前：基线顶层状态仍为骨架")
    for m in metrics:
        sym = str(m.get("sourceSymbol", "")).strip()
        pr = m.get("provenance")
        if sym not in stems:
            raise EvidenceError("PRECHECK_BASELINE", f"写盘前：sourceSymbol={sym!r} 不在源码")
        if not isinstance(pr, dict) or not all(
            str(pr.get(k, "")).strip() for k in ("collector", "inputSnapshotHash", "collectedAt")
        ) or not HEX64_RE.match(str(pr.get("inputSnapshotHash", ""))):
            raise EvidenceError("PRECHECK_BASELINE", f"写盘前：指标 {m.get('id')} provenance 不完整")
        if str(pr.get("inputSnapshotHash")) != export_sha256:
            raise EvidenceError("PRECHECK_BASELINE", "写盘前：inputSnapshotHash 非导出文件实算值")


def commit(manifest: dict, baseline: dict, dry_run: bool = True,
           manifest_path: Optional[Path] = None, baseline_path: Optional[Path] = None) -> dict:
    """默认 dry-run 只返回预览；--commit 才原子覆盖两个数据文件（真源不动）。"""
    manifest_path = manifest_path or EVAL_DIR / "frozen_set.skeleton.json"
    baseline_path = baseline_path or EVAL_DIR / "baseline-metrics.json"
    result = {"committed": False, "manifest": manifest_path, "baseline": baseline_path,
              "frozenHash": manifest["frozenHash"], "collected": manifest["collected"]}
    if dry_run:
        return result
    tmp_m = manifest_path.with_suffix(".json.tmp")
    tmp_b = baseline_path.with_suffix(".json.tmp")
    tmp_m.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp_b.write_text(json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp_m.replace(manifest_path)
    tmp_b.replace(baseline_path)
    result["committed"] = True
    return result


def build(export_path: Path, datasheet_attestation: dict, collector_attestation: dict,
          dataset_version: str, dry_run: bool = True,
          manifest_path: Optional[Path] = None, baseline_path: Optional[Path] = None) -> dict:
    """完整链路：载入 → 校验 → 构建 → 回填 → 写盘前复算 → commit。"""
    rows, export_sha = load_blind_label_export(export_path)
    cleaned = validate_provenance_and_pairing(rows)
    cases = build_frozen_cases(cleaned)
    manifest = build_active_manifest(cases, datasheet_attestation, dataset_version)
    baseline_doc = json.loads((EVAL_DIR / "baseline-metrics.json").read_text(encoding="utf-8"))
    baseline = backfill_baseline(baseline_doc, collector_attestation, export_sha)
    verify_before_write(manifest, baseline, export_sha)
    return commit(manifest, baseline, dry_run=dry_run,
                  manifest_path=manifest_path, baseline_path=baseline_path)


# ---------------------------------------------------------------------------
# 5. 自测：内存合成 fixture 证明「造假说不过、真数据能过」，绝不写真源
# ---------------------------------------------------------------------------
def _synthetic_rows(n: int) -> List[dict]:
    """仅用于自判据的**内存**合成样本：结构合规但明确不是真实票据，绝不落盘真源。"""
    rows = []
    for i in range(n):
        payload = {"ticketNo": f"T{i}", "amount": str(10 + i), "hospital": f"H{i % 3}"}
        label = {"decision": "PASS" if i % 2 == 0 else "REVIEW"}
        ai = {"aiConfidence": 0.5 + (i % 5) / 10, "decisionResult": label["decision"]}
        rows.append({
            "task_no": f"SELFTEST-{i}",
            "claim_id": f"C{i}",
            "assignee_id": "self-test",
            "status": "UNBLINDED",
            "agreement": "PAIRED",
            "label_payload_json": canonical(payload),
            "payload_hash": sha256_text(canonical(payload)),
            "label_result_json": canonical(label),
            "ai_suggestion_json": canonical(ai),
            "unblinded_at": "2026-01-01T00:00:00Z",
            "stratum": STRATA[i % len(STRATA)],
            "slices": {
                "ticketType": f"type-{i % 2}",
                "hospitalTier": f"tier-{i % 2}",
                "amountBand": f"band-{i % 2}",
                "tenant": f"tenant-{i % 2}",
                "rephotographed": f"rephoto-{i % 2}",
            },
        })
    return rows


def _synthetic_attestations(stems: Set[str]) -> Tuple[dict, dict, str, str]:
    datasheet = {
        f: {"status": "ready", "owner": "self-test", "why": "内存 fixture 自证"}
        for f in DATASHEET_FIELDS
    }
    # 选真实存在的源码 stem 作为 collector，证明「符号须真实可达」这条也被自测覆盖。
    real = sorted(s for s in stems if s.endswith("Metrics") or s.endswith("Adapter")) or sorted(stems)
    collectors = {
        "manual_case_minutes": real[0], "field_extraction_accuracy": real[0],
        "adjustment_accuracy": real[0], "leakage_rate": real[0],
        "ai_cost_per_case": real[0], "manual_review_rate": real[0],
    }
    att = {"collectors": collectors, "collectedAt": "2026-01-01T00:00:00Z",
           "confirmedBy": "self-test"}
    return datasheet, att, "selftest-dataset-v1", "0" * 64


def self_test() -> Tuple[bool, List[str]]:
    fails: List[str] = []

    def expect_error(code: str, fn, label: str) -> None:
        try:
            fn()
        except EvidenceError as exc:
            if exc.code != code:
                fails.append(f"{label}: 期望 {code}，实际 {exc.code}")
        except Exception as exc:  # 非 fail-closed 异常也算失败
            fails.append(f"{label}: 期望 EvidenceError({code})，实际 {type(exc).__name__}: {exc}")
        else:
            fails.append(f"{label}: 未拒绝（应 fail-closed {code}）")

    stems = _java_stems()
    rows = _synthetic_rows(MIN_REAL_CASES)
    datasheet, att, version, _ = _synthetic_attestations(stems)

    # --- 负向探针：每种造假都必须被精确拒绝 ---
    expect_error("FORBIDDEN_AI_TRACE", lambda: validate_provenance_and_pairing(
        _tamper_forbidden()), "禁区键")
    expect_error("PAYLOAD_HASH_MISMATCH", lambda: validate_provenance_and_pairing(
        _tamper_hash()), "载荷哈希篡改")
    expect_error("NOT_UNBLINDED_PAIRED", lambda: validate_provenance_and_pairing(
        _tamper_unblinded()), "未解盲")
    expect_error("INSUFFICIENT_REAL_CASES", lambda: build_frozen_cases(
        validate_provenance_and_pairing(_synthetic_rows(MIN_REAL_CASES - 1))), "条数不足")
    expect_error("V7_SLICE_TOO_THIN", lambda: build_frozen_cases(
        validate_provenance_and_pairing(_synthetic_single_slice(MIN_REAL_CASES))), "切片太薄")
    expect_error("DATASHEET_NOT_READY", lambda: build_active_manifest(
        build_frozen_cases(validate_provenance_and_pairing(rows)),
        {f: {"status": "pending", "owner": "x"} for f in DATASHEET_FIELDS}, version), "datasheet未签核")
    expect_error("COLLECTOR_NOT_IN_SOURCE", lambda: backfill_baseline(
        _baseline_doc(), {"collectors": {m: "NoSuchClassXYZ" for m in _metric_ids()},
                          "confirmedBy": "x", "collectedAt": "t"}, "0" * 64, stems), "采集类不在源码")
    expect_error("CONFIRMER_MISSING", lambda: backfill_baseline(
        _baseline_doc(), att_without_confirmer(), "0" * 64, stems), "缺确认人")

    # --- 正向：完整真实结构 fixture → 端到端到临时目录，判据应翻绿 ---
    cleaned = validate_provenance_and_pairing(rows)
    cases = build_frozen_cases(cleaned)
    manifest = build_active_manifest(cases, datasheet, version)
    baseline = backfill_baseline(_baseline_doc(), att, "a" * 64, stems)
    try:
        verify_before_write(manifest, baseline, "a" * 64, stems)
    except EvidenceError as exc:
        fails.append(f"正向 fixture 被误拒：{exc}")
    with tempfile.TemporaryDirectory() as td:
        mp = Path(td) / "frozen.json"
        bp = Path(td) / "baseline.json"
        res = commit(manifest, baseline, dry_run=False, manifest_path=mp, baseline_path=bp)
        if not res["committed"] or not mp.exists() or not bp.exists():
            fails.append("正向 commit 未写入临时文件")

        # 用复核器在临时 PATHS 上真跑：两判据应从 BLOCKED 翻到 SATISFIED。
        try:
            mod = _load_module("_readiness_for_selftest",
                               ROOT / "doc" / "ai-product" / "synapClaim" / "tools"
                               / "_ai_native_readiness.py")
            original = dict(mod.PATHS)
            try:
                mod.PATHS["frozen_manifest"] = mp
                mod.PATHS["baseline"] = bp
                gates = {g["id"]: g["status"] for g in mod.evaluate(mod.load_facts())}
                for gid in ("FROZEN_EVALSET_ACTIVE", "BASELINE_WIRED"):
                    if gates.get(gid) != mod.SATISFIED:
                        fails.append(f"正向端到端：{gid} 未翻绿，实际 {gates.get(gid)}")
            finally:
                mod.PATHS.clear()
                mod.PATHS.update(original)
        except Exception as exc:
            fails.append(f"正向端到端复核器调用失败：{type(exc).__name__}: {exc}")

    return (not fails), fails


def _tamper_forbidden() -> List[dict]:
    rows = _synthetic_rows(2)
    payload = json.loads(rows[0]["label_payload_json"])
    payload["aiConfidence"] = 0.9  # AI 痕迹键回流
    rows[0]["label_payload_json"] = canonical(payload)
    rows[0]["payload_hash"] = sha256_text(canonical(payload))
    return rows


def _tamper_hash() -> List[dict]:
    rows = _synthetic_rows(2)
    rows[0]["payload_hash"] = "0" * 64
    return rows


def _tamper_unblinded() -> List[dict]:
    rows = _synthetic_rows(2)
    rows[0]["agreement"] = "PENDING"
    return rows


def _synthetic_single_slice(n: int) -> List[dict]:
    rows = _synthetic_rows(n)
    for r in rows:
        for d in SLICE_DIMENSIONS:
            r["slices"][d] = "only-one-value"
    return rows


def _metric_ids() -> List[str]:
    doc = _baseline_doc()
    return [m["id"] for m in doc.get("metrics", [])]


def _baseline_doc() -> dict:
    return json.loads((EVAL_DIR / "baseline-metrics.json").read_text(encoding="utf-8"))


def att_without_confirmer() -> dict:
    stems = _java_stems()
    _, att, _, _ = _synthetic_attestations(stems)
    att.pop("confirmedBy", None)
    return att


def main(argv: Optional[List[str]] = None) -> int:
    ap = argparse.ArgumentParser(description="G0/G1 fail-closed 评测证据构建器")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--self-test", action="store_true", help="判据有效性自证（CI 必跑）")
    g.add_argument("--blind-export", metavar="PATH", help="真实盲标导出 JSON（findUnblindedPaired 产物）")
    ap.add_argument("--datasheet-attestation", metavar="PATH")
    ap.add_argument("--collector-attestation", metavar="PATH")
    ap.add_argument("--dataset-version", default="g0-active-1")
    ap.add_argument("--commit", action="store_true", help="真正写入两个数据文件（默认 dry-run）")
    args = ap.parse_args(argv)

    if args.self_test:
        ok, fails = self_test()
        if not ok:
            print("❌ 评测证据构建器自证失败：")
            for f in fails:
                print("  · " + f)
            return 2
        print(f"OK: 自证通过（{MIN_REAL_CASES} 条真实样本门槛、负向探针全拒、正向端到端两判据翻绿）")
        return 0

    if not args.datasheet_attestation or not args.collector_attestation:
        print("❌ 须提供 --datasheet-attestation 与 --collector-attestation")
        return 1
    try:
        ds = json.loads(Path(args.datasheet_attestation).read_text(encoding="utf-8"))
        ca = json.loads(Path(args.collector_attestation).read_text(encoding="utf-8"))
        res = build(Path(args.blind_export), ds, ca, args.dataset_version,
                    dry_run=not args.commit)
    except (EvidenceError, OSError, json.JSONDecodeError) as exc:
        print(f"❌ 构建被 fail-closed 拒绝：{exc}")
        return 3
    mode = "已写入" if res["committed"] else "dry-run（未写盘，须 --commit）"
    print(f"OK: {mode}　collected={res['collected']} frozenHash={res['frozenHash']}")
    print("  manifest=" + str(res["manifest"]) + "  baseline=" + str(res["baseline"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
