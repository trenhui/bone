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

刻意不做的六件事（防假绿）：
  · 不编造准确率 / 自动化率 / aiCoverage —— 那是运行期数据，静态文件里没有；
    相关判据一律标 RUNTIME_ONLY 并阻断，不因「没数据」判定为「达标」；
  · 不把「配置态全齐」当成成熟度档位——L1/L2 描述运行态，必须有模型上场证据（第 11 轮）；
  · 不读取活库、不起服务、不改任何文件；
  · 不因为「代码里已有判据」就把对应能力算作已发生（check.sh 里的门禁接线
    只证明可证伪，不证明模型跑过真实案件）；
  · 不把「治理资产里的 status / currency 字面串」当成能力已发生的证据——
    2026-11-07 循环复核发现 4 条判据可被改字符串/删文件刷绿，已改为证据驱动；
  · 不把裸 `probeVerdict=REACHABLE` 当成模型真实上场（第 11 轮 GAP）——
    还须同一次调用返回的 httpStatus / model / provenance / token / requestId 自洽。

运行：
  python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py
  python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test

--self-test 用内存变造的事实做四类探针：摘除能力（须翻转）/ 注入合规事实
（须达 SATISFIED）/ 注入刷绿动作（须保持 BLOCKED，防假绿）/ 成熟度档位
（配置态不得冒充运行态，且必须有合规正例可达 L2）。
任一探针不符预期 ⇒ 判据判别力或防假绿能力失效 ⇒ 本脚本自身判红（exit 1）。
"""

from __future__ import annotations

import importlib.util
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
    # 生效通道真源：模块内 V1..V15 从未被 runner 执行（0090 头部原文），
    # 判据必须读这里，而不是孤儿 Flyway 脚本里的 DEFAULT 字面串。
    "init_sql": ROOT / "bone-init.sql",
    "migration_manifest": ROOT / "scripts" / "migration" / "migration-manifest.json",
    "migration_dir": ROOT / "scripts" / "migration",
    "frozen_engine": ROOT / "bone-ai" / "bone-ai-rag" / "eval" / "frozen_set.py",
    "frozen_manifest": ROOT / "bone-ai" / "bone-ai-rag" / "eval" / "frozen_set.skeleton.json",
}

SATISFIED = "SATISFIED"
BLOCKED = "BLOCKED"
RUNTIME_ONLY = "RUNTIME_ONLY"


def read_text(key: str) -> str:
    path = PATHS[key]
    return path.read_text(encoding="utf-8") if path.exists() else ""


def f_baseline_doc(raw: str) -> dict:
    """基线 JSON 单一解析入口（解析失败返回空 dict，判据此后必 BLOCKED）。"""
    try:
        doc = json.loads(raw) if raw else {}
    except json.JSONDecodeError:
        return {}
    return doc if isinstance(doc, dict) else {}


def gate_default_false(app_yml: str, env_var: str) -> bool:
    """三要素之「门控默认 false」：yml 里 ${ENV:false} 占位缺省即成立（缺键/默认 true = 未接线或行为变更）。"""
    return bool(re.search(r"\$\{" + re.escape(env_var) + r":false\}", app_yml))


def load_facts() -> dict:
    baseline_raw = read_text("baseline")
    evalset_raw = read_text("evalset")
    try:
        evalset = json.loads(evalset_raw) if evalset_raw else {}
    except json.JSONDecodeError:
        evalset = {}
    golive_md = sorted(PATHS["golive"].glob("*.md")) if PATHS["golive"].exists() else []
    baseline = f_baseline_doc(baseline_raw)
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
    # D2 / D3 三要素之「实现类存在」：适配器文件本体（供降级码静态核验）。
    image_adapter = next(
        (p for p in java_files if p.stem == "ImageDocumentParsePortAdapter"), None)
    rag_adapter = next(
        (p for p in java_files if p.stem == "RagKnowledgeRetrievalPortAdapter"), None)
    # 回滚演练（只读 dry-run）：控制器存在 + 仅 GET + 放量控制器无写端点（锁定只读纪律）。
    drill_controller = next(
        (p for p in java_files if p.stem == "AiRolloutDrillController"), None)
    rollout_controller = next(
        (p for p in java_files if p.stem == "AiRolloutController"), None)
    drill_src = drill_controller.read_text(encoding="utf-8", errors="ignore") if drill_controller else ""
    rollout_src = rollout_controller.read_text(encoding="utf-8", errors="ignore") if rollout_controller else ""
    drill_controller_present = drill_controller is not None
    drill_readonly = drill_controller_present and not re.search(
        r"@(Post|Put|Delete|Patch)Mapping", drill_src)
    rollout_no_write = rollout_controller is not None and not re.search(
        r"@(Post|Put|Delete|Patch)Mapping", rollout_src)
    # ---- 目录准入：跟随生效通道真源 + 生产端口接线（防「删 V15 反判绿」） ----
    manifest_raw = read_text("migration_manifest")
    try:
        manifest = json.loads(manifest_raw) if manifest_raw else {}
    except json.JSONDecodeError:
        manifest = {}
    sql_entries = sorted(
        (e for e in manifest.get("entries", [])
         if isinstance(e, dict) and str(e.get("file", "")).endswith(".sql")),
        key=lambda e: e.get("order") or 0,
    )
    migration_catalog_src = ""
    for entry in sql_entries:
        fp = PATHS["migration_dir"] / str(entry["file"])
        if not fp.exists():
            continue
        try:
            body = fp.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if "catalog_policy" in body and "DEFAULT" in body:
            migration_catalog_src += body + "\n"
    catalog_src = read_text("init_sql") + "\n" + migration_catalog_src
    # 动态 DDL 里引号被转义成 DEFAULT ''NONE''，故匹配一或多个单引号。
    catalog_defaults = re.findall(
        r"catalog_policy[^\n]{0,300}?DEFAULT\s+'+(NONE|MANDATORY)'+",
        catalog_src, re.I)
    catalog_effective_default = catalog_defaults[-1].upper() if catalog_defaults else "UNKNOWN"
    catalog_source_available = (
        PATHS["init_sql"].exists()
        and PATHS["migration_manifest"].exists()
        and bool(sql_entries)
        and bool(catalog_defaults)
    )
    catalog_port_wired = any(
        re.search(r"\bimplements\s+ItemCatalogPort\b", ln)
        for _, lines in main_sources for ln in lines
    )

    # ---- 非 ML 基线：status 之外必须带可复现采集证据 ----
    baseline_doc = f_baseline_doc(baseline_raw)
    baseline_items = baseline_doc.get("metrics", baseline_doc.get("baselines", []))
    if isinstance(baseline_items, dict):
        baseline_items = list(baseline_items.values())
    if not isinstance(baseline_items, list):
        baseline_items = []
    java_stems = {x.stem for x in java_files}
    def provenance_ok(m: dict) -> bool:
        pr = m.get("provenance")
        if not isinstance(pr, dict):
            return False
        if not all(str(pr.get(k, "")).strip()
                   for k in ("collector", "inputSnapshotHash", "collectedAt")):
            return False
        return bool(re.fullmatch(r"[0-9a-f]{64}", str(pr.get("inputSnapshotHash", ""))))
    baseline_all_wired = bool(baseline_items) and all(
        str(m.get("status", "")) == "wired" for m in baseline_items if isinstance(m, dict))
    baseline_evidence_ok = bool(baseline_items) and all(
        isinstance(m, dict)
        and str(m.get("sourceSymbol", "") or "").strip()
        and str(m.get("sourceSymbol", "")) in java_stems
        and not str(m.get("blockingReason", "") or "").strip()
        and provenance_ok(m)
        for m in baseline_items)
    baseline_top_status = str(baseline_doc.get("status", "")).strip()

    # ---- 冻结集：调用真实引擎 verify_manifest，不接受任意非空 hash ----
    frozen_raw = read_text("frozen_manifest")
    try:
        frozen_doc = json.loads(frozen_raw) if frozen_raw else {}
    except json.JSONDecodeError:
        frozen_doc = {}
    frozen_verified = False
    if PATHS["frozen_engine"].exists() and frozen_doc:
        try:
            spec = importlib.util.spec_from_file_location(
                "_bone_frozen_engine", PATHS["frozen_engine"])
            engine = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(engine)
            frozen_verified = (
                not engine.verify_manifest(frozen_doc)
                and str(frozen_doc.get("status", "")).lower() == "active"
                and int(frozen_doc.get("collected") or 0) > 0
                and bool(frozen_doc.get("cases"))
                and bool(engine.HEX64_RE.match(str(frozen_doc.get("frozenHash", ""))))
                and str(frozen_doc.get("frozenHash", "")) == engine.freeze_hash(
                    frozen_doc.get("cases"))
            )
        except Exception:
            frozen_verified = False
    frozen_status = str(frozen_doc.get("status", "")).lower()

    # ---- Go-Live 分册：状态 + 正文完成度，缺册即 fail-closed ----
    unfinished_re = re.compile(
        "须建设|0 命中|未定|待复现|不得留空|@Disabled|待签署|待评审|待压测|"
        "待 PoC|须签署|仍待架构评审|待建|留待")
    golive_facts = []
    for fp in golive_md:
        text = fp.read_text(encoding="utf-8")
        head = text[:400]
        cur = re.search(r"^currency:\s*(\S+)", head, re.M)
        role = re.search(r"^role:\s*(\S+)", head, re.M)
        golive_facts.append({
            "name": fp.name,
            "currency": cur.group(1) if cur else "",
            "role": role.group(1) if role else "",
            "unfinished": bool(unfinished_re.search(text)),
        })

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
        "golive": golive_facts,
        "catalog_source_available": catalog_source_available,
        "catalog_effective_default": catalog_effective_default,
        "catalog_port_wired": catalog_port_wired,
        "baseline_all_wired": baseline_all_wired,
        "baseline_evidence_ok": baseline_evidence_ok,
        "baseline_top_status": baseline_top_status,
        "frozen_verified": frozen_verified,
        "frozen_status": frozen_status,
        "java_names": [p.stem for p in java_files],
        "calibration_class_present": calibration_class_present,
        "calibration_compute_wired": calibration_compute_wired,
        "calibration_sample_source_wired": calibration_sample_source_wired,
        "image_adapter_present": image_adapter is not None,
        "rag_adapter_present": rag_adapter is not None,
        "image_adapter_src":
            image_adapter.read_text(encoding="utf-8", errors="ignore") if image_adapter else "",
        "rag_adapter_src":
            rag_adapter.read_text(encoding="utf-8", errors="ignore") if rag_adapter else "",
        "drill_controller_present": drill_controller_present,
        "drill_readonly": drill_readonly,
        "rollout_no_write": rollout_no_write,
        "e2e_runtime": runtime,
    }


def gate(gid: str, status: str, reason: str, evidence: str) -> dict:
    return {"id": gid, "status": status, "reason": reason, "evidence": evidence}


_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.:-]{7,127}$")
_PLACEHOLDER_REQUEST_IDS = {
    "unknown", "null", "none", "placeholder", "00000000", "test-request",
}


def probe_evidence_valid(ev: object) -> tuple[bool, str]:
    """同一次真实模型调用的证据束自检；缺项或自相矛盾一律 fail-closed。

    第 11 轮 GAP：裸 `probeVerdict` 是可手写的字符串，不能单独证明模型上场。
    这里要求 httpStatus=200 + 生产来源模型 + prompt/completion token 均 > 0 +
    供应商 requestId 合法，把「写一行字符串」抬成「必须伪造一份自洽的计费证据」。
    字段由 E2E 从 /ai-provider-probe 的同一次响应透传；采集侧尚未产出这些字段时
    产物为 null，判据按 PROBE_EVIDENCE_* 阻断，绝不回落到裸判词。
    """
    if not isinstance(ev, dict):
        return False, "PROBE_EVIDENCE_REQUIRED"
    if ev.get("httpStatus") != 200:
        return False, "PROBE_EVIDENCE_HTTP_NOT_OK"
    if not str(ev.get("model") or "").strip():
        return False, "PROBE_EVIDENCE_MODEL_MISSING"
    if str(ev.get("provenance") or "").strip().upper() != "PROVIDER":
        return False, "PROBE_EVIDENCE_STUB_NOT_ALLOWED"
    try:
        prompt_tokens = int(ev.get("promptTokens") or 0)
        completion_tokens = int(ev.get("completionTokens") or 0)
    except (TypeError, ValueError):
        return False, "PROBE_EVIDENCE_TOKEN_INVALID"
    if prompt_tokens <= 0 or completion_tokens <= 0:
        return False, "PROBE_EVIDENCE_EMPTY_COMPLETION"
    rid = str(ev.get("requestId") or "").strip()
    if not _REQUEST_ID_RE.fullmatch(rid) or rid.lower() in _PLACEHOLDER_REQUEST_IDS:
        return False, "PROBE_EVIDENCE_REQUEST_ID_INVALID"
    return True, "ADMITTED"


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

    # G0-2 非 ML 基线：status=wired 只是声明，还须采集证据（provenance + 源码可达）。
    # 2026-11-07：原判据只读 6 个 status 字符串，改字符串即可刷绿；且与
    # check-eval-baseline.py E5「skeleton 态不允许 wired」口径冲突（刷绿必让门禁判红）。
    metrics = f["baseline"].get("metrics", f["baseline"].get("baselines", []))
    statuses = [str(m.get("status", "")) for m in metrics if isinstance(m, dict)] \
        if isinstance(metrics, (list, dict)) else []
    if not statuses:
        reason = "BASELINE_METRICS_MISSING"
    elif not f["baseline_all_wired"]:
        reason = "BASELINE_DECLARED_NOT_WIRED"
    elif f["baseline_top_status"] in ("skeleton", "", "proposed"):
        reason = "BASELINE_SKELETON_CANNOT_CLAIM_WIRED"
    elif not f["baseline_evidence_ok"]:
        reason = "BASELINE_PROVENANCE_MISSING"
    else:
        reason = "ADMITTED"
    gates.append(
        gate(
            "BASELINE_WIRED",
            SATISFIED if reason == "ADMITTED" else BLOCKED,
            reason,
            f"baseline-metrics.json: {len(statuses)} 项 wired={f['baseline_all_wired']} "
            f"证据齐备={f['baseline_evidence_ok']} 顶层={f['baseline_top_status']}",
        )
    )

    # G0-3 冻结集：active + payloadHash 才可用于回归门禁。
    # G0-3 冻结集：原判据套在 evalset 的 status + 任意非空 payloadHash 上，
    # 既与 check-claim-ai-evalset.py A1（只允许 declared|wired）冲突，
    # 也给个 "aaa" 就算通过。改为调用 frozen_set.verify_manifest 的真源判定。
    ev = f["evalset"]
    ev_payload = str(ev.get("payloadHash", ev.get("frozenSetHash", ""))).strip()
    ev_payload_valid = (not ev_payload) or bool(re.fullmatch(r"[0-9a-f]{64}", ev_payload))
    if not f["frozen_verified"]:
        reason = "FROZEN_SET_NOT_ACTIVE" if f["frozen_status"] != "active" \
            else "FROZEN_HASH_OR_CASES_INVALID"
    elif not ev_payload_valid:
        reason = "EVALSET_PAYLOAD_HASH_NOT_HEX64"
    else:
        reason = "ADMITTED"
    gates.append(
        gate(
            "FROZEN_EVALSET_ACTIVE",
            SATISFIED if reason == "ADMITTED" else BLOCKED,
            reason,
            f"frozen_set.skeleton.json: status={f['frozen_status']} "
            f"verify_manifest通过={f['frozen_verified']}；evalset status={ev.get('status', 'N/A')}",
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

    # G2 知识层：原判据读 V15（从未被 runner 执行的孤儿 Flyway 脚本）里的
    # DEFAULT 字面串，删掉该文件反而判绿。改为读生效通道（bone-init + 清单 order）
    # 的最终 DEFAULT，并要求 ItemCatalogPort 有生产实现——否则 MANDATORY 会
    # 让全部责任 CATALOG_UNRESOLVED 退补件（把静默超赔换成全量停摆）。
    if not f["catalog_source_available"]:
        reason = "CATALOG_SOURCE_UNREADABLE"
    elif f["catalog_effective_default"] == "NONE":
        reason = "CATALOG_POLICY_DEFAULT_NONE"
    elif not f["catalog_port_wired"]:
        reason = "CATALOG_PORT_NOT_WIRED"
    else:
        reason = "ADMITTED"
    gates.append(
        gate(
            "CATALOG_ADMISSION_ACTIVE",
            SATISFIED if reason == "ADMITTED" else BLOCKED,
            reason,
            f"生效通道默认={f['catalog_effective_default']} "
            f"ItemCatalogPort生产实现={f['catalog_port_wired']}",
        )
    )

    # G4 合规就绪：原判据只查 frontmatter currency 前缀，是 12 项里唯一
    # 「改一个单词就能翻绿」的词法判据。改为 currency/role/正文完成度三查 +
    # 分册数量 fail-closed；正文仍写「须建设 / 0 命中 / 待复现」时不得标 current。
    booklets = [
        g for g in f["golive"]
        if re.match(r"^\d{2}", g["name"]) and not g["name"].startswith("00")
    ]
    bad = [
        g for g in booklets
        if g["currency"].startswith("draft")
        or g["role"].startswith("draft")
        or g["unfinished"]
    ]
    if len(booklets) < 13:
        reason = f"GOLIVE_BOOKLETS_MISSING_{13 - len(booklets)}"
    elif bad:
        reason = f"GOLIVE_DRAFT_{len(bad)}"
    else:
        reason = "ADMITTED"
    gates.append(
        gate(
            "GOLIVE_NOT_DRAFT",
            SATISFIED if reason == "ADMITTED" else BLOCKED,
            reason,
            "08-go-live/ frontmatter currency+role 与正文完成度标记",
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

    # D2 图像感知：接线 + 门控默认关 + 降级诚实 三要素静态核验（任一缺失即 BLOCKED）。
    image_gate = gate_default_false(f["app_yml"], "BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED")
    image_degrade = all(
        code in f.get("image_adapter_src", "")
        for code in ("IMAGE_MOCK_REJECTED", "IMAGE_GATEWAY_UNAVAILABLE", "IMAGE_OUTPUT_INVALID"))
    if not f.get("image_adapter_present"):
        image_reason = "IMAGE_ADAPTER_MISSING"
    elif not image_gate:
        image_reason = "IMAGE_GATE_NOT_DEFAULT_OFF"
    elif not image_degrade:
        image_reason = "IMAGE_DEGRADE_CODES_MISSING"
    else:
        image_reason = "ADMITTED"
    gates.append(
        gate(
            "IMAGE_PARSE_WIRED",
            SATISFIED if image_reason == "ADMITTED" else BLOCKED,
            image_reason,
            "ImageDocumentParsePortAdapter + bone.claim.document-parse.image.enabled(默认 false) + 归因码",
        )
    )

    # D3 受控 RAG：接线 + 门控默认关 + 降级诚实 三要素静态核验。
    rag_gate = gate_default_false(f["app_yml"], "BONE_CLAIM_KNOWLEDGE_RAG_ENABLED")
    rag_degrade = all(
        code in f.get("rag_adapter_src", "")
        for code in ("RAG_CORPUS_MISSING", "RAG_RETRIEVAL_UNAVAILABLE", "clauseRef"))
    if not f.get("rag_adapter_present"):
        rag_reason = "RAG_ADAPTER_MISSING"
    elif not rag_gate:
        rag_reason = "RAG_GATE_NOT_DEFAULT_OFF"
    elif not rag_degrade:
        rag_reason = "RAG_DEGRADE_CODES_MISSING"
    else:
        rag_reason = "ADMITTED"
    gates.append(
        gate(
            "RAG_WIRED",
            SATISFIED if rag_reason == "ADMITTED" else BLOCKED,
            rag_reason,
            "RagKnowledgeRetrievalPortAdapter + bone.claim.knowledge.rag.enabled(默认 false) + 成因码",
        )
    )

    # G5 回滚演练（只读 dry-run）：Google《ML Test Score》「dry-run rollbacks and verify
    # state integrity」+ NIST AI RMF 应急处置。能力落地即入判据：控制器存在 + 仅 GET +
    # 放量控制器无写端点（防「页面点上去、准入逻辑不认」的双写分叉）。不参与 l3Claimable
    # 阻断（属运行期安全准备），但缺位即登记，避免静默回退。
    if not f.get("drill_controller_present"):
        drill_reason = "ROLLOUT_DRILL_CONTROLLER_MISSING"
    elif not f.get("drill_readonly"):
        drill_reason = "ROLLOUT_DRILL_NOT_READONLY"
    elif not f.get("rollout_no_write"):
        drill_reason = "ROLLOUT_CONTROLLER_HAS_WRITE_ENDPOINT"
    else:
        drill_reason = "ADMITTED"
    gates.append(
        gate(
            "ROLLOUT_ROLLBACK_DRILL",
            SATISFIED if drill_reason == "ADMITTED" else BLOCKED,
            drill_reason,
            "AiRolloutDrillController(仅 GET) + AiRolloutController 无写端点",
        )
    )

    # 运行期证据：消费前后端 E2E 实跑产物，不再只靠静态推断。四项条件缺一不可：
    #   ① provider 配置态：节点自报模型标识 ≠ provider 真的配了（AiModelProvenance 用黑名单判定，
    #      未配 provider 的环境也可能留下非空标识），须叠加 PROVIDER_CONFIGURED；
    #   ② 探活 REACHABLE：AI技术方案 §11.7 记录过「探活绿灯、业务全量降级」事故——
    #      配了环境变量 ≠ 上游通了 ≠ 输出契约对。判词由同一次手动探活响应写入 runtime.probeVerdict，
    #      缺省 / 非 REACHABLE 一律 fail-closed，绝不把「没测过」当成「通了」；
    #   ③ 探活证据束自洽（第 11 轮 GAP）：② 的裸字符串可手写，须再要求同一次真实调用
    #      返回的 httpStatus / model / provenance / token / requestId 同时自洽；
    #   ④ 运行期参与率：nodes / rate / participated 三者均 > 0，证明模型真正在案件链路上过场。
    rt = f["e2e_runtime"]
    provider_configured = bool(f["provider_env"])
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
        coverage = nodes > 0 and rate > 0 and participated > 0
        probe_verdict = str(rt.get("probeVerdict") or "").strip().upper()
        probe_reachable = probe_verdict == "REACHABLE"
        evidence_ok, evidence_reason = probe_evidence_valid(rt.get("probeEvidence"))
        all_conditions = provider_configured and coverage and probe_reachable and evidence_ok
        if not provider_configured:
            reason = "PROVIDER_NOT_CONFIGURED"
        elif not probe_reachable:
            reason = "PROBE_NOT_REACHABLE"
        elif not evidence_ok:
            reason = evidence_reason
        elif not coverage:
            reason = "MODEL_PARTICIPATION_ZERO"
        else:
            reason = "ADMITTED"
        gates.append(
            gate(
                "RUNTIME_MODEL_COVERAGE",
                SATISFIED if all_conditions else BLOCKED,
                reason,
                f"E2E 实跑：nodes={nodes} aiParticipated={participated} "
                f"participationRate={rate:.4f} capabilityMissing={rt.get('capabilityMissing')} "
                f"providerConfigured={provider_configured} "
                f"probeVerdict={probe_verdict or 'MISSING'} "
                f"probeEvidence={evidence_reason}",
            )
        )
    return gates


def current_level(gates: list[dict]) -> str:
    """成熟度档位描述运行态，配置态不能替代运行期证据。

    第 11 轮循环复核发现原实现只看 PROVIDER_CONFIGURED 与
    PERCEPTION_MODEL_DRIVEN 两个配置态判据：只要「配好环境变量 + 打开 llm 模式」
    就会判 L2（AI 承担部分主负载），哪怕 E2E 证明模型从未在案件链路上场。
    故 RUNTIME_MODEL_COVERAGE 未达 SATISFIED 时一律回落 L0，并把三条件里
    真正缺失的原因码带进档位说明，杜绝「配了就算 AI 原生」。
    """
    by_id = {g["id"]: g for g in gates}
    if by_id.get("PROVIDER_CONFIGURED", {}).get("status") != SATISFIED:
        return "L0（运行态：AI 全链路降级，纯规则路径）"
    coverage = by_id.get("RUNTIME_MODEL_COVERAGE", {})
    if coverage.get("status") != SATISFIED:
        reason = coverage.get("reason", "UNKNOWN")
        return f"L0（配置态就绪但运行期未达标：{reason}，不得计入 L1/L2）"
    if by_id.get("PERCEPTION_MODEL_DRIVEN", {}).get("status") != SATISFIED:
        return "L1（AI 辅助，可摘除）"
    return "L2（AI 承担部分主负载，仍待飞轮与 MRM）"


def self_test(f: dict) -> bool:
    """负向探针必须翻转、合规正例必须可达、防刷绿探针必须保持 BLOCKED。

    2026-11-07：4 条判据改为证据驱动后，探针同步分三类：
      · flip                —— 摘除/破坏既有能力，判据必须翻转（判别力）；
      · flip_to_satisfied   —— 注入合规事实，判据必须达 SATISFIED（防恒假）；
      · stay                 —— 注入「只改字符串/改孤儿文件/删分册」等刷绿动作，
                               判据必须保持 BLOCKED（防假绿，本轮核心回归钉子）。

    第 12 轮补第四类：成熟度档位探针。`l3Claimable` 只回答「能不能宣称 L3」，
    档位 `current_level()` 是另一处可假绿的出口——配好 provider + 打开 llm 就可能
    被写成 L2。故命令行自检也须覆盖档位，不能只靠 pre-commit 单测。
    """
    base = {g["id"]: g["status"] for g in evaluate(f)}
    probes: list[tuple[str, str, dict, str]] = []

    def add(label: str, gid: str, mutated: dict, expect: str = "flip") -> None:
        probes.append((label, gid, mutated, expect))

    add("删门禁 --check 接线", "EVAL_GATES_WIRED",
        dict(f, check_sh=f["check_sh"].replace("--check", "")))

    # 目录准入：判据已改读生效通道 + 端口接线，改孤儿 V15 不得再翻绿。
    add("生效通道 MANDATORY 且端口已接线（合规正例）", "CATALOG_ADMISSION_ACTIVE",
        dict(f, catalog_source_available=True, catalog_effective_default="MANDATORY",
             catalog_port_wired=True), "flip_to_satisfied")
    add("只改孤儿 V15 的 DEFAULT（生效通道仍 NONE）", "CATALOG_ADMISSION_ACTIVE",
        dict(f, v15=f["v15"].replace("DEFAULT 'NONE'", "DEFAULT 'MANDATORY'")), "stay")
    add("删掉孤儿 V15 文件（原判据会假绿）", "CATALOG_ADMISSION_ACTIVE",
        dict(f, v15=""), "stay")
    add("生效默认 MANDATORY 但端口未接线（配了不生效）", "CATALOG_ADMISSION_ACTIVE",
        dict(f, catalog_effective_default="MANDATORY", catalog_port_wired=False), "stay")
    add("生效真源不可读（fail-closed）", "CATALOG_ADMISSION_ACTIVE",
        dict(f, catalog_source_available=False), "stay")

    add("注入 provider 环境变量", "PROVIDER_CONFIGURED",
        dict(f, provider_env="http://localhost:8000/v1"))

    rt = dict(f.get("e2e_runtime") or {})
    rt.update({"nodesScanned": 88, "participationRate": 0.5, "aiParticipated": 44,
               "probeVerdict": "NOT_CONFIGURED", "probeEvidence": None})
    # 三要素（provider / 裸判词 / 参与率）齐全但缺证据束：GAP 的核心伪造场景。
    rt_forged = dict(rt, probeVerdict="REACHABLE")
    good_evidence = {
        "httpStatus": 200, "model": "deepseek-chat", "provenance": "PROVIDER",
        "promptTokens": 12, "completionTokens": 8, "requestId": "chatcmpl-9f2a7c1d",
    }
    rt_ok = dict(rt_forged, probeEvidence=good_evidence)
    # 只刷参与率而 provider 未配，不得让判据局部假绿（stay）。
    add("仅伪造运行期参与率而 provider 未配（防假绿）", "RUNTIME_MODEL_COVERAGE",
        dict(f, e2e_runtime=rt), "stay")
    # 四条件齐备，判据须翻转为 SATISFIED（防恒假）。
    add("provider 已配 + 探活 REACHABLE + 自洽证据束 + 参与率 0.5（合规可达）",
        "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1", e2e_runtime=rt_ok), "flip_to_satisfied")
    # §11.7「配了 ≠ 通了」：provider 已配、参与率非零，但探活非 REACHABLE，仍须 BLOCKED。
    add("provider 已配 + 参与率 0.5 但探活非 REACHABLE（防假绿）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1", e2e_runtime=rt), "stay")
    # 单独钉住探活条件：其余三条件齐备但判词非 REACHABLE 仍须 BLOCKED。
    # 否则证据束条件会遮蔽「probe_reachable 被从判据里摘除」这一退化，变异测试抓不到。
    add("证据束齐备但探活判词非 REACHABLE（钉住探活条件不可摘除）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1",
             e2e_runtime=dict(rt_ok, probeVerdict="NOT_CONFIGURED")), "stay")
    add("探活判词缺失（未测过）不得当成 REACHABLE（fail-closed）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1",
             e2e_runtime={k: v for k, v in rt.items() if k != "probeVerdict"}), "stay")
    # 判词与参与率一起伪造、但 provider 未配，同样不得翻绿（钉住 provider 条件不可摘除）。
    add("provider 未配但把探活判词与参与率一起伪造（防假绿）", "RUNTIME_MODEL_COVERAGE",
        dict(f, e2e_runtime=rt_ok), "stay")

    # 第 11 轮 GAP：伪造鲁棒性探针。provider / 裸判词 / 参与率三样都能手写，
    # 缺 probeEvidence 或证据束自相矛盾时必须仍 BLOCKED。
    add("三要素全伪造但无 probeEvidence（防假绿）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1", e2e_runtime=rt_forged), "stay")
    add("probeEvidence 里 completionTokens=0（空返回不算跑过模型）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1",
             e2e_runtime=dict(rt_ok, probeEvidence=dict(good_evidence, completionTokens=0))),
        "stay")
    add("probeEvidence 里 provenance=STUB（替身响应不得计入）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1",
             e2e_runtime=dict(rt_ok, probeEvidence=dict(good_evidence, provenance="STUB"))),
        "stay")
    add("probeEvidence 里 requestId 为占位值（防假绿）", "RUNTIME_MODEL_COVERAGE",
        dict(f, provider_env="http://localhost:8000/v1",
             e2e_runtime=dict(rt_ok, probeEvidence=dict(good_evidence, requestId="unknown"))),
        "stay")

    # 基线：只刷 status 不得翻绿，须真实采集证据 + 顶层非 skeleton。
    add("基线合规正例（wired+provenance+confirmed）", "BASELINE_WIRED",
        dict(f, baseline_all_wired=True, baseline_evidence_ok=True,
             baseline_top_status="confirmed"), "flip_to_satisfied")
    add("只把 6 项 status 改 wired（顶层仍 skeleton）", "BASELINE_WIRED",
        dict(f, baseline_all_wired=True), "stay")
    add("改 status+顶层但无 provenance", "BASELINE_WIRED",
        dict(f, baseline_all_wired=True, baseline_top_status="confirmed"), "stay")

    # 冻结集：塞任意 hash 不得翻绿，须真实引擎 verify_manifest 通过。
    add("冻结集合规正例（引擎验证通过）", "FROZEN_EVALSET_ACTIVE",
        dict(f, frozen_verified=True, frozen_status="active"), "flip_to_satisfied")
    add("只给 evalset 塞任意 payloadHash + active", "FROZEN_EVALSET_ACTIVE",
        dict(f, evalset=dict(f["evalset"], status="active", payloadHash="aaa")), "stay")
    add("只给 evalset 塞 64hex 但冻结集仍 skeleton", "FROZEN_EVALSET_ACTIVE",
        dict(f, evalset=dict(f["evalset"], payloadHash="0" * 64)), "stay")

    # Go-Live：批量改 currency 不得翻绿；缺册须 fail-closed。
    add("Go-Live 合规正例（13 册状态+正文全合规）", "GOLIVE_NOT_DRAFT",
        dict(f, golive=[dict(g, currency="current", role="current", unfinished=False)
                        for g in f["golive"]]), "flip_to_satisfied")
    add("只把 13 册 currency 改 current（正文仍须建设）", "GOLIVE_NOT_DRAFT",
        dict(f, golive=[dict(g, currency="current") for g in f["golive"]]), "stay")
    add("删掉一册分册（缺册 fail-closed）", "GOLIVE_NOT_DRAFT",
        dict(f, golive=f["golive"][:-2]), "stay")

    for key, label in [
        ("calibration_class_present", "摘除校准实现类"),
        ("calibration_sample_source_wired", "摘除盲标样本源生产调用"),
        ("calibration_compute_wired", "摘除校准计算生产调用（import 假绿回归钉子）"),
    ]:
        add(label, "FLYWHEEL_DOWNSTREAM", dict(f, **{key: not f.get(key, False)}))

    add("图像适配器接线翻转（双向探针）", "IMAGE_PARSE_WIRED",
        dict(f, image_adapter_present=not f.get("image_adapter_present", False)))
    add("图像门控默认值改 true（负向探针）", "IMAGE_PARSE_WIRED",
        dict(f, app_yml=f["app_yml"].replace(
            "${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:false}",
            "${BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:true}")))
    add("图像 mock 拒绝码被移除（降级诚实探针）", "IMAGE_PARSE_WIRED",
        dict(f, image_adapter_src=f["image_adapter_src"].replace(
            "IMAGE_MOCK_REJECTED", "REDACTED")))
    add("RAG 适配器接线翻转（双向探针）", "RAG_WIRED",
        dict(f, rag_adapter_present=not f.get("rag_adapter_present", False)))
    add("RAG 门控默认值改 true（负向探针）", "RAG_WIRED",
        dict(f, app_yml=f["app_yml"].replace(
            "${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:false}",
            "${BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:true}")))
    add("摘除回滚演练控制器（负向探针）", "ROLLOUT_ROLLBACK_DRILL",
        dict(f, drill_controller_present=False))

    ok = True
    for label, gid, mutated, expect in probes:
        now = {g["id"]: g["status"] for g in evaluate(mutated)}[gid]
        before = base[gid]
        if expect == "stay":
            passed = now == before
            detail = f"须保持 {before}"
        elif expect == "flip_to_satisfied":
            passed = now != before and now == SATISFIED
            detail = "须翻转为 SATISFIED"
        else:
            passed = now != before
            detail = "须翻转"
        print(f"  [{'OK' if passed else 'FAIL'}] {label}: {before} → {now}（{detail}）")
        ok = ok and passed

    # 第四类：成熟度档位。配置态全齐但运行期无模型上场证据，不得写成 L1/L2；
    # 同时必须有合规正例可达 L2，防止把档位改成「永远 L0」来蒙混过关。
    level_probes = [
        ("配置态全齐但探活失败 ⇒ 档位须为 L0",
         dict(f, provider_env="http://localhost:8000/v1",
              parse_mode_env="llm", e2e_runtime=rt), "L0"),
        ("配置态全齐但无 E2E 产物 ⇒ 档位须为 L0",
         dict(f, provider_env="http://localhost:8000/v1",
              parse_mode_env="llm", e2e_runtime=None), "L0"),
        ("三要素全伪造但无证据束 ⇒ 档位须为 L0（防假绿）",
         dict(f, provider_env="http://localhost:8000/v1",
              parse_mode_env="llm", e2e_runtime=rt_forged), "L0"),
        ("四条件齐备 + llm ⇒ 档位须可达 L2（防恒假）",
         dict(f, provider_env="http://localhost:8000/v1",
              parse_mode_env="llm", e2e_runtime=rt_ok), "L2"),
    ]
    for label, mutated, required in level_probes:
        lv = current_level(evaluate(mutated))
        passed = lv.startswith(required)
        print(f"  [{'OK' if passed else 'FAIL'}] {label}: {lv}（须以 {required} 开头）")
        ok = ok and passed
    return ok


def main() -> int:
    facts = load_facts()
    if "--self-test" in sys.argv:
        print("四类探针（翻转 / 合规可达 / 刷绿须保持 BLOCKED / 成熟度档位）：")
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
        ev_ok, ev_reason = probe_evidence_valid(rt.get("probeEvidence"))
        print(f"运行期证据（前后端 E2E 实跑）：nodes={rt.get('nodesScanned')} "
              f"aiParticipated={rt.get('aiParticipated')} "
              f"participationRate={rt.get('participationRate')} "
              f"probeVerdict={rt.get('probeVerdict') or 'MISSING'} "
              f"probeEvidence={'OK' if ev_ok else ev_reason}")
    print("说明：无 E2E 产物时该判据为 RUNTIME_ONLY 并阻断；provider 未配、探活非 REACHABLE、"
          "证据束缺失/自相矛盾或参与率为 0 均判 BLOCKED。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
