#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G0-3 冻结评测集骨架：结构真源 + 可校验哈希 + 防泄漏 / 防数字抢跑判据。

对应契约：
  · doc/ai-product/synapClaim/03-contract/AI技术方案.md §11.3「冻结集 / 切片评估 /
    独立盲标」、§12.2「非 ML 基线 + 切片指标 + 契约测试进入 CI；冻结集哈希可校验」
  · 复核报告 §7.3 G0-3「≥300 条分层抽样框架、切片定义、数据说明书；冻结集哈希
    可校验；任一样本被改动 ⇒ 评测流水线拒绝执行」

为什么只做「骨架」而不造 300 条样例
----------------------------------
真实脱敏票据的**来源 / 权属 / 脱敏标准**需业务侧确认（报告 §九 第 3 条）。在口径
未定之前编造样本，等于把「文档抢跑」从文字升级成数据。故本模块交付的是：
  · 一份 status=skeleton 的清单（cases 为空、collected=0、datasheet 全 pending）；
  · 一套在空集上也成立、且**任何一个字段被改动都会红**的判据；
  · 一个零依赖 --self-test，用内置 fixture 证明每条判据都有判别力。

骨架不宣称任何准确率 / 自动化率 / 采集进度。进入 active 态必须先满足 V7。

判据（每条都能被反例精确证伪，错误前缀即规则 ID）
------------------------------------------------
  V0  输入是 dict 且 schemaVersion 匹配（None / 空 / 格式漂移不得恒绿）
  V1  必备顶层键齐全
  V2  status ∈ {skeleton, active}
  V3  frozenHash == sha256(规范化 cases)（任一样本改动 ⇒ 流水线拒绝执行）
  V4  targetSize 为正整数且 collected == len(cases)（防「目标值冒充已采集」）
  V5  样本结构：caseId 非空唯一 / split 合法 / stratum 合法 / payloadHash 64hex /
      slices 五维齐全且非空
  V6  防泄漏：frozen 与 train|tune 不得共享 caseId 或 payloadHash
      （Hidden Feedback Loops：人工结论/AI 建议不得回流污染冻结集）
  V7  skeleton 态必须 cases 空 + collectionChecklist 非空；active 态必须 cases 非空，
      且每个声明的切片维度至少 2 个不同取值（总体准确率不得掩盖小样本切片）
  V8  数据说明书四字段（source / rights / desensitization / temporal）各有
      status ∈ {pending, ready} 与非空 owner（Datasheets for Datasets）
  V9  splitPolicy 必须声明 frozen / train / tune 三态
  V10 防文档抢跑：skeleton 态禁止出现 baselineResults / accuracyAchieved /
      gatePassed 等「已达标」字段

用法:
  python frozen_set.py --check  frozen_set.skeleton.json   # 校验清单，违规非零退出
  python frozen_set.py --emit                              # 生成合规 skeleton 清单（含哈希）
  python frozen_set.py --self-test                         # CI 必跑：判据有效性自证
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from typing import Any, Dict, Iterable, List, Tuple

SCHEMA_VERSION = 1
STATUSES = ("skeleton", "active")
SPLITS = ("frozen", "train", "tune")
STRATA = ("positive", "negative", "boundary", "adversarial")

# 切片维度单一真源：AI技术方案 §11.3 与复核报告 §7.3 G0-1 口径一致。
SLICE_DIMENSIONS: Tuple[str, ...] = (
    "ticketType",       # 票据类型：门诊发票 / 费用清单 / 医保结算单 ...
    "hospitalTier",     # 医院等级
    "amountBand",       # 金额段
    "tenant",           # 租户
    "rephotographed",   # 翻拍件
)
DATASHEET_FIELDS: Tuple[str, ...] = ("source", "rights", "desensitization", "temporal")
TOP_LEVEL_KEYS: Tuple[str, ...] = (
    "schemaVersion", "datasetVersion", "status", "frozenHash",
    "targetSize", "collected", "sliceDimensions", "strata", "cases",
    "datasheet", "splitPolicy", "collectionChecklist",
)
FORBIDDEN_SKELETON_KEYS: Tuple[str, ...] = (
    "baselineResults", "accuracyAchieved", "gatePassed", "metricsAchieved",
)

RULES: Tuple[str, ...] = tuple(f"V{i}" for i in range(11))
HEX64_RE = re.compile(r"^[0-9a-f]{64}$")


# ---------------------------------------------------------------------------
# 规范化与哈希
# ---------------------------------------------------------------------------
def canonical_cases(cases: Any) -> str:
    """稳定序列化：键排序 + 紧凑分隔，字段顺序无关、任何值改动必变哈希。"""
    return json.dumps(cases, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def freeze_hash(cases: Any) -> str:
    return hashlib.sha256(canonical_cases(cases).encode("utf-8")).hexdigest()


# ---------------------------------------------------------------------------
# 判据主体（纯函数：单测 / CI / 门禁共用，main 只做编排）
# ---------------------------------------------------------------------------
def verify_manifest(doc: Any) -> List[str]:
    """返回带规则 ID 前缀的违规列表；空列表表示通过。"""
    errors: List[str] = []

    if not isinstance(doc, dict):
        return ["V0: 清单必须为 JSON 对象（None / 数组 / 字符串不得恒绿）"]
    if not doc:
        return ["V0: 清单为空对象，等于没有校验"]
    if doc.get("schemaVersion") != SCHEMA_VERSION:
        return [f"V0: schemaVersion 必须为 {SCHEMA_VERSION}（版本漂移不得静默放行）"]

    missing = [k for k in TOP_LEVEL_KEYS if k not in doc]
    if missing:
        errors.append("V1: 缺少必备顶层键 " + ", ".join(missing))

    status = doc.get("status")
    if status not in STATUSES:
        errors.append(f"V2: status 必须为 {STATUSES} 之一，实际 {status!r}")

    cases = doc.get("cases")
    if not isinstance(cases, list):
        errors.append("V3: cases 必须为数组")
        cases = []
    expected_hash = freeze_hash(cases)
    if doc.get("frozenHash") != expected_hash:
        errors.append(
            f"V3: frozenHash 不匹配（声明 {doc.get('frozenHash')!r} / 实算 {expected_hash}）；"
            "任一样本改动都应使评测流水线拒绝执行"
        )

    target = doc.get("targetSize")
    if not isinstance(target, int) or isinstance(target, bool) or target <= 0:
        errors.append("V4: targetSize 必须为正整数（目标规模来自契约，不得省略）")
    if doc.get("collected") != len(cases):
        errors.append(
            f"V4: collected={doc.get('collected')!r} 与 cases 实际条数 {len(cases)} 不一致；"
            "禁止把目标值 / 计划值冒充已采集样本"
        )

    seen_ids: set[str] = set()
    for idx, case in enumerate(cases):
        tag = f"cases[{idx}]"
        if not isinstance(case, dict):
            errors.append(f"V5: {tag} 必须为对象")
            continue
        cid = case.get("caseId")
        if not isinstance(cid, str) or not cid.strip():
            errors.append(f"V5: {tag}.caseId 必须为非空字符串")
        elif cid in seen_ids:
            errors.append(f"V5: {tag}.caseId 重复：{cid}")
        else:
            seen_ids.add(cid)
        if case.get("split") not in SPLITS:
            errors.append(f"V5: {tag}.split 必须为 {SPLITS} 之一")
        if case.get("stratum") not in STRATA:
            errors.append(f"V5: {tag}.stratum 必须为 {STRATA} 之一")
        payload = case.get("payloadHash")
        if not isinstance(payload, str) or not HEX64_RE.match(payload):
            errors.append(f"V5: {tag}.payloadHash 必须为样本内容的 64 位小写 sha256")
        slices = case.get("slices")
        if not isinstance(slices, dict):
            errors.append(f"V5: {tag}.slices 必须为对象")
        else:
            for dim in SLICE_DIMENSIONS:
                val = slices.get(dim)
                if not isinstance(val, str) or not val.strip():
                    errors.append(f"V5: {tag}.slices.{dim} 缺失或为空（切片不得只看总体）")

    # V6 防泄漏：按 caseId 与 payloadHash 双通道（改 ID 绕不过内容哈希）
    frozen_ids = {c.get("caseId") for c in cases if isinstance(c, dict) and c.get("split") == "frozen"}
    frozen_payloads = {
        c.get("payloadHash") for c in cases if isinstance(c, dict) and c.get("split") == "frozen"
    }
    for idx, case in enumerate(cases):
        if not isinstance(case, dict) or case.get("split") not in ("train", "tune"):
            continue
        if case.get("caseId") in frozen_ids or case.get("payloadHash") in frozen_payloads:
            errors.append(
                f"V6: cases[{idx}] 同时出现在 frozen 与 {case.get('split')}；"
                "冻结集永不参与训练与调优（Hidden Feedback Loops）"
            )

    checklist = doc.get("collectionChecklist")
    declared_dims = doc.get("sliceDimensions")
    if not isinstance(declared_dims, dict) or tuple(declared_dims.keys()) != SLICE_DIMENSIONS:
        errors.append(f"V7: sliceDimensions 必须且只能声明 {SLICE_DIMENSIONS} 五维（顺序即口径）")
    if status == "skeleton":
        if cases:
            errors.append("V7: skeleton 态 cases 必须为空；有样本请转 active 并补齐数据说明书")
        if not isinstance(checklist, list) or not checklist:
            errors.append("V7: skeleton 态必须给出非空 collectionChecklist（采集前待办）")
    elif status == "active":
        if not cases:
            errors.append("V7: active 态 cases 不得为空（否则门禁恒绿）")
        for dim in SLICE_DIMENSIONS:
            values = {
                c.get("slices", {}).get(dim)
                for c in cases
                if isinstance(c, dict) and isinstance(c.get("slices"), dict)
            }
            values.discard(None)
            if len(values) < 2:
                errors.append(
                    f"V7: active 态切片维度 {dim} 只有 {len(values)} 个取值，"
                    "无法做切片评估（总体指标会掩盖小样本失效）"
                )

    datasheet = doc.get("datasheet")
    if not isinstance(datasheet, dict):
        errors.append("V8: datasheet 必须为对象")
    else:
        for field in DATASHEET_FIELDS:
            item = datasheet.get(field)
            if not isinstance(item, dict):
                errors.append(f"V8: datasheet.{field} 必须为对象")
                continue
            if item.get("status") not in ("pending", "ready"):
                errors.append(f"V8: datasheet.{field}.status 必须为 pending|ready")
            if not isinstance(item.get("owner"), str) or not item["owner"].strip():
                errors.append(f"V8: datasheet.{field}.owner 必须非空（没人负责的说明书等于没有）")

    policy = doc.get("splitPolicy")
    if not isinstance(policy, dict) or any(k not in policy for k in SPLITS):
        errors.append(f"V9: splitPolicy 必须声明 {SPLITS} 三态用途")

    if status == "skeleton":
        for key in FORBIDDEN_SKELETON_KEYS:
            if key in doc:
                errors.append(
                    f"V10: skeleton 态禁止声明 {key}；未采集样本不得出现「已达标」字段（防文档抢跑）"
                )

    return errors


# ---------------------------------------------------------------------------
# 骨架生成（哈希由实算写入，杜绝手写哈希与内容漂移）
# ---------------------------------------------------------------------------
def skeleton_manifest() -> Dict[str, Any]:
    doc: Dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "datasetVersion": "g0-skeleton-0",
        "status": "skeleton",
        "frozenHash": "",
        "targetSize": 300,  # 来自 AI技术方案 §11.1「基准集 ≥300 条脱敏样例」，是目标非进度
        "collected": 0,
        "sliceDimensions": {
            "ticketType": "票据类型（门诊发票/费用清单/医保结算单…）",
            "hospitalTier": "医院等级",
            "amountBand": "金额段",
            "tenant": "租户",
            "rephotographed": "翻拍件（是/否）",
        },
        "strata": list(STRATA),
        "cases": [],
        "datasheet": {
            "source": {"status": "pending", "owner": "G0-3", "why": "真实脱敏票据来源与权属待业务确认"},
            "rights": {"status": "pending", "owner": "G0-3", "why": "授权 / 使用范围待法务确认"},
            "desensitization": {"status": "pending", "owner": "G0-3", "why": "脱敏标准待确认"},
            "temporal": {"status": "pending", "owner": "G0-3", "why": "采集时间窗与样本时效待确认"},
        },
        "splitPolicy": {
            "frozen": "冻结集：只用于评测，永不参与训练与调优；每次发布绑定 datasetVersion + frozenHash",
            "train": "训练集：与冻结集按 caseId / payloadHash 双通道互斥",
            "tune": "调优集：阈值校准与提示词调优，与冻结集互斥且不得回流人工复核结论",
        },
        "collectionChecklist": [
            "G0-1 业务确认四项基线的统计窗口、切片维度取值域、每切片样本量下界",
            "G0-3 确认 ≥300 条真实脱敏票据来源 / 权属 / 脱敏标准（报告 §九 第 3 条）",
            "按五维切片 + 四层分层抽样建采样框，正负例 / 边界 / 对抗各自留量",
            "独立盲标队列屏蔽 AI 建议，人工结论与 AI 建议分字段存储",
            "每条样本落 payloadHash，computed frozenHash 写入本清单并纳入版本绑定",
            "status 改 active 前，CI 的 V7 会要求每个切片维度至少 2 个取值",
        ],
    }
    doc["frozenHash"] = freeze_hash(doc["cases"])
    return doc


# ---------------------------------------------------------------------------
# 判据有效性自证：每条规则必须有「合规放行 + 违规被精确捕获」两组证据
# ---------------------------------------------------------------------------
def _compliant_active() -> Dict[str, Any]:
    """最小合规 active 清单：2 条 frozen 样本覆盖五维各 2 个取值。"""
    def case(cid: str, flip: bool) -> Dict[str, Any]:
        mark = "b" if flip else "a"
        return {
            "caseId": cid,
            "split": "frozen",
            "stratum": "positive" if not flip else "adversarial",
            "payloadHash": hashlib.sha256(cid.encode("utf-8")).hexdigest(),
            "slices": {d: f"{d}-{mark}" for d in SLICE_DIMENSIONS},
        }

    doc = skeleton_manifest()
    doc["status"] = "active"
    doc["cases"] = [case("C1", False), case("C2", True)]
    doc["collected"] = len(doc["cases"])
    doc["frozenHash"] = freeze_hash(doc["cases"])
    return doc


def self_test() -> Tuple[bool, List[str]]:
    """零依赖自证。返回 (是否全通过, 失败说明)。

    精确归因：每条规则的违规 fixture 必须命中**该规则**，不能靠「反正有错」蒙混；
    同时校验 RULES 全集合被覆盖（新增判据却没写用例 ⇒ 自测红）。
    """
    failures: List[str] = []
    compliant = [skeleton_manifest(), _compliant_active()]
    for i, doc in enumerate(compliant):
        errs = verify_manifest(doc)
        if errs:
            failures.append(f"合规 fixture #{i} 被误判：{errs}")

    hit: set[str] = set()

    def expect(doc: Any, rule: str, label: str) -> None:
        errs = verify_manifest(doc)
        matched = [e for e in errs if e.startswith(rule + ":")]
        if not matched:
            failures.append(f"{label}：期望命中 {rule}，实际 {errs}")
        else:
            hit.add(rule)

    d = skeleton_manifest(); d["schemaVersion"] = 99
    expect(d, "V0", "版本漂移")
    expect({}, "V0", "空清单")
    expect(None, "V0", "None 清单")

    d = skeleton_manifest(); del d["datasheet"]
    expect(d, "V1", "缺顶层键")

    d = skeleton_manifest(); d["status"] = "done"
    expect(d, "V2", "非法状态")

    d = skeleton_manifest(); d["frozenHash"] = "0" * 64
    expect(d, "V3", "哈希篡改")
    d = _compliant_active(); d["cases"][0]["slices"]["tenant"] = "tenant-hacked"
    expect(d, "V3", "样本被改动")

    d = skeleton_manifest(); d["targetSize"] = 0
    expect(d, "V4", "目标规模非法")
    d = skeleton_manifest(); d["collected"] = 300
    expect(d, "V4", "目标值冒充已采集")

    d = _compliant_active()
    d["cases"][0]["caseId"] = ""
    d["frozenHash"] = freeze_hash(d["cases"])
    expect(d, "V5", "样本结构非法")

    d = _compliant_active()
    d["cases"].append({
        "caseId": "LEAK", "split": "train", "stratum": "positive",
        "payloadHash": d["cases"][0]["payloadHash"],
        "slices": {dim: f"{dim}-a" for dim in SLICE_DIMENSIONS},
    })
    d["collected"] = len(d["cases"])
    d["frozenHash"] = freeze_hash(d["cases"])
    expect(d, "V6", "冻结集泄漏进训练集")

    d = skeleton_manifest(); d["cases"] = _compliant_active()["cases"]
    d["collected"] = len(d["cases"])
    d["frozenHash"] = freeze_hash(d["cases"])
    expect(d, "V7", "skeleton 态夹带样本")
    d = skeleton_manifest(); d["collectionChecklist"] = []
    expect(d, "V7", "skeleton 无采集清单")
    d = _compliant_active(); d["cases"] = []
    d["collected"] = 0
    d["frozenHash"] = freeze_hash(d["cases"])
    expect(d, "V7", "active 态空集")

    d = skeleton_manifest(); d["datasheet"]["source"] = "TBD"
    expect(d, "V8", "数据说明书缺责任人")

    d = skeleton_manifest(); d["splitPolicy"] = {"frozen": "only"}
    expect(d, "V9", "缺 split 策略")

    d = skeleton_manifest(); d["accuracyAchieved"] = 0.99
    expect(d, "V10", "skeleton 态宣称已达标")

    uncovered = [r for r in RULES if r not in hit]
    if uncovered:
        failures.append("以下判据没有对应的违规用例（可能已退化成恒绿）：" + ", ".join(uncovered))

    # 切片维度本身也要被钉死：少一维 ⇒ 合规 fixture 必须转红
    d = _compliant_active()
    d["sliceDimensions"] = {k: v for k, v in d["sliceDimensions"].items() if k != "tenant"}
    if not any(e.startswith("V7:") for e in verify_manifest(d)):
        failures.append("删除切片维度未被 V7 捕获")

    return (not failures), failures


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="G0-3 冻结评测集骨架：校验 / 生成 / 自证")
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--check", metavar="PATH", help="校验清单，违规非零退出")
    g.add_argument("--emit", action="store_true", help="输出合规 skeleton 清单 JSON")
    g.add_argument("--self-test", action="store_true", help="零依赖判据有效性自证（CI 必跑）")
    args = ap.parse_args(argv)

    if args.emit:
        print(json.dumps(skeleton_manifest(), ensure_ascii=False, indent=2))
        return 0
    if args.self_test:
        ok, failures = self_test()
        if not ok:
            print("❌ frozen_set 判据自证失败：")
            for f in failures:
                print("  · " + f)
            return 2
        print(f"OK: frozen_set 判据自证通过（{len(RULES)} 条规则全部有精确归因的反例）")
        return 0
    try:
        with open(args.check, "r", encoding="utf-8") as fh:
            doc = json.load(fh)
    except (OSError, ValueError) as exc:
        print(f"❌ 清单读取失败：{exc}")
        return 1
    errors = verify_manifest(doc)
    if errors:
        print(f"❌ 冻结集清单不合规：{len(errors)} 项")
        for e in errors:
            print("  · " + e)
        return 2
    print(f"OK: 冻结集清单通过（status={doc['status']}, collected={doc['collected']}）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
