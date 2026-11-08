#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""理赔 AI 决策回归评测集门禁（监管 §4 测评用例集 / §24 PII 禁入 / §15 清单治理）。

背景（为什么补这条门禁）
------------------------
synap-claim 已有三处「事实上的回归用例」，但都只存在于 Java 测试代码里：
  · DecisionTriageGoldenSetTest（13 例分诊阈值/档位）
  · AiAssessNodeHandlerTest（7 例三态能力语义）
  · AiCapabilityReadiness*Test（10 例部署态 fail-closed 与只读契约）
`/rules/regression` 只覆盖理算金额一致性，明确不覆盖决策分诊；
`check-eval-baseline.py` 管的是 RAG 冻结集骨架与指标字典，也不看这些用例。
于是「测评用例集」在监管口径下仍是**无清单、无版本、无来源标注、无防退化护栏**：
删几条负向用例、把期望值改宽松、清单写有而代码已删，都没有机器会红。

本门禁钉死的是「评测集作为治理资产的状态诚实性」，不编造准确率指标：
  A1 真源结构（schemaVersion / status / scope）
  A2 用例库存下界硬编码在脚本里：禁止通过删条目让门禁变轻；id 唯一；suite 覆盖齐全
  A3 每条用例字段完整、kind 合法、piiFree=true、expected 非空
  A4 testClass/testSymbol 必须在**真实测试源码**中命中（清单不得抢跑于代码）
  A5 groundTruthRef 为 path#anchor 且 path 真实存在，regulatoryBasis 非空（来源可追溯）
  A6 关键负向探针不得被删、负向占比有下界（防只留全绿样本）
  A7 groundTruthPolicy 五要素齐全（来源/禁 PII/冻结集纪律/变更必跑）
  A8 PII 正则扫描：身份证/手机号/银行卡号命中即失败（§24）
  A9 独立哨兵 + 扫描失效闸：判据退化成恒绿、或源码路径失效，一律判失败
  A10 期望值冻结：每条用例的 (id, kind, expected) 哈希与脚本内基线逐一比对

为什么需要 A10（A1–A9 的真实缺口）
----------------------------------
A1–A9 管的是「有没有、够不够、来源可不可达」，都**不进 expected 的值**。于是
一条负向探针可以原地保留：id 没删、symbol 还在源码里、kind 仍写 negative、
piiFree 仍为 true，唯独把 `expected` 从 `{"result":"SUGGEST_REJECT"}` 改成
`{"result":"AUTO_APPROVE"}` —— 39 条用例一条不少、负向占比一分不降、全部门禁
绿着放行。这类改动比删用例更隐蔽：删用例会被 A2/A6 抓，改期望值此前无人看。

A10 是**篡改可见性（tamper-evidence），不是篡改阻止**：同时改评测集与脚本基线
仍能通过。它的价值在于把「一次静默的判据放宽」变成「两处 diff」——评测集那行
改了，基线那行也必须改，review 时看得见。改基线的动作本身即申报：谁改了哪条
用例的期望值、改成了什么，都留在 diff 里。真正的防放宽还需人工盲标与独立的
评测集审批人（组织措施，机器无法替代，不在此声称）。
基线刻意硬编码在脚本里而非放在评测集旁边的锁文件：与本文件 MIN_CASES 同理，
读 JSON 等于「把阈值和被测物放在同一份可编辑文件里」。

刻意不做：不校验模型准确率/自动化率、不要求 provider 已接线、不打开自动放款开关。
这些依赖真实样本与灰度证据，硬编码阈值只会逼出假数据。

用法:
  python3 scripts/check-claim-ai-evalset.py --check          # CI blocking
  python3 scripts/check-claim-ai-evalset.py                  # 人类可读报告
  python3 scripts/check-claim-ai-evalset.py --print-hashes   # 改判据后重出 A10 基线
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

REPO = Path(__file__).resolve().parent.parent
EVALSET_REL = Path("bone-apps/synap-claim/synap-claim-app/eval/ai-decision-evalset.json")
TEST_ROOT_REL = Path("bone-apps/synap-claim/synap-claim-app/src/test/java")

# 下界硬编码在脚本里，不读 JSON —— 否则「把 minCases 一起改小」就能让门禁变轻。
# 39 = 30（P0-2 基线）+ 9（P0-3 AI 参与决策披露回归）；只许上调，不许随清单一起下调。
MIN_CASES = 39
MIN_NEGATIVE_RATIO = 0.5
REQUIRED_SUITES: Tuple[str, ...] = (
    "decisionTriage",
    "aiAssessNode",
    "capabilityReadiness",
    "readinessController",
    "aiDisclosureDomain",
    "aiDisclosureService",
    "aiDisclosureController",
)
# 监管最在意的十条负向探针：删掉任何一条都等于抹掉
# 「无法判定≠拒赔 / 清单≠放款许可 / 无法确认≠AI 未参与 / 快照≠参与证据 / 跨案不得泄漏」的护栏。
REQUIRED_NEGATIVE_IDS: Tuple[str, ...] = (
    "AIASSESS-01",
    "AIASSESS-05",
    "READY-02",
    "READY-03",
    "READY-04",
    "READY-05",
    "CTRL-02",
    "DISC-02",
    "DISC-03",
    "DISC-06",
)
CASE_FIELDS: Tuple[str, ...] = (
    "id", "name", "suite", "kind", "expected", "groundTruthRef",
    "regulatoryBasis", "testClass", "testSymbol", "piiFree",
)
POLICY_FIELDS: Tuple[str, ...] = (
    "provenance", "piiFree", "piiPolicy", "frozenSetDiscipline", "changePolicy",
)
PII_PATTERNS: Tuple[Tuple[str, Any], ...] = (
    ("身份证号", re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)")),
    ("手机号", re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")),
    ("银行卡号", re.compile(r"(?<!\d)\d{16,19}(?!\d)")),
)

# A10 期望值冻结基线：caseId -> sha256(canonical{id, kind, expected})。
# 改任何一条用例的期望值或正负向属性都必须同步改这里，测评集评审才有 diff 可看。
# 重新生成：python3 scripts/check-claim-ai-evalset.py --print-hashes
EXPECTED_HASHES: Dict[str, str] = {
    "TRIAGE-01": "8aa5e2a1657d135a07259b1e7620f4036b00acdeacfd9b5005ffadd9ed51db32",
    "TRIAGE-02": "c35aa3fbecca6503575e1e9e3417bb115e5543c62a8c9bdeadf9084a9fa2eb8f",
    "TRIAGE-03": "73a8aed019dbb2da12a3260377569738d3c234b891ec83d1d6943c8181fa6ddb",
    "TRIAGE-04": "30e5d960ab9ef453d332430d663f281bd359c17899abd39333329cea70ae84c7",
    "TRIAGE-05": "e00b7764ca9248c6286431fe83f11502f40205fcf00a8141102a9885fbbaee53",
    "TRIAGE-06": "a17f04f09b1af68553f5f579e1ad601dfe1c2e46559ae2aae15bff7e56a5d21b",
    "TRIAGE-07": "754916100b10a9e77e4759b266bd8906c2ad4afce2fe855d03f1a0c237f56b91",
    "TRIAGE-08": "37f94238b5c8278c21bc46001919c002ad0a1c366fde4d5276b248f6d6ae74ea",
    "TRIAGE-09": "fe3f0d18093aea417d240b8e1ac33900116954875738fff5600bfb03db9ae8b8",
    "TRIAGE-10": "daf7a75820539a801402b805fa8754271054899f41bc5d1b24e6493001477395",
    "TRIAGE-11": "23cdb8baed39c38498ec263009412a4bb446a9968351a6b511ebb61e6b91c4d5",
    "TRIAGE-12": "350bcd14559913d92bf8c5713d86fbb3c409414f1a7bfb7c843bb470b4cb0f17",
    "TRIAGE-13": "b2d459ceb77dd58e07ffab7bdc9ae7c19364e7a492292ad64df0c15e61984e44",
    "AIASSESS-01": "98453762879907cd86ee820baeb507c0e26d5e26f7b8a4f93a317180dc2d934c",
    "AIASSESS-02": "1c472515b86b9f7ed9e3075fa2bc96171b69f0f6a4457890cda2f58fbe6266f8",
    "AIASSESS-03": "9aff5a54363fc497a50ed670351cc9d19c2e2a462810e8a496f28fc33747c6e6",
    "AIASSESS-04": "6b87aeb0038e079d3544f19a25cc0f16113c744bf875e3562a7b286c6eefa4f5",
    "AIASSESS-05": "6b69882521f9765353a246bfc1e9a5d5ccb6210d5d1dbfc07abf91215f710f3f",
    "AIASSESS-06": "b981774864d0e55deba12a983f4434ee87dc31f3901fb7b0bd0c5f2d181bd64d",
    "AIASSESS-07": "ba2119a3a33208d630fbd90225b10e8a7e4c8d6c319357891b6b7b2ff4122fc2",
    "READY-01": "075d9b29ffd52c422d3aa0395be95df9b191550400c850dc49107ac91072d693",
    "READY-02": "61103c799f3d8cf2d6ff43b089b22c1faa43e941cd3238f8ebce76de05594505",
    "READY-03": "760f159cf34b947bcb551e81f8cace2246310783e1624cb8d72d3598cc48f43f",
    "READY-04": "11f64e932e724ba765a5ffaa2cdf7d35174656088c63d6e36082a8c7cde0c33f",
    "READY-05": "b8047c2da6f93b6f0954b763bee48134c6c4001ce13ecc398c2dae46eac0bdd1",
    "READY-06": "48ee551fa5834905cd80ecebb4befce64fcd7cc7491b3aec2ec1e07d36cda0cc",
    "READY-07": "36baa1f82414620a8105369bd34cdff8e1fac6bc2768230d158c1cdbb4d49afd",
    "READY-08": "da7303349b39e11a7a68bef8e09273aade0c5ddc897fc745ece5c1343cc3b2f9",
    "CTRL-01": "657ac8ef5935bece25a7ec2176e749ed77fddc4cb6a831815280385e9c9fc6b8",
    "CTRL-02": "6d2697371501d16e588ecc93021d4282f99a42b6aafeafb4a33b4fda0a4b974e",
    "DISC-01": "8283ca79f3bb8ecbc7bb5869343689f2b98e36095032a7217b4cfefb6c656738",
    "DISC-02": "2603d2124f5dc2a2f121ef220fdfba65039516a1634947085d24c82e1465fa48",
    "DISC-03": "f04d515a05cb02f47b793cef039ea3af9d9b3aa5284546ceb5d8043b0e5bc827",
    "DISC-04": "600522d55d41a52b6512340ccf1793fac632aa5625ec0b67b2d97935707330e7",
    "DISC-05": "a460cd3c8b6ee317ed2a095cf5d31c5c91be6afab9f4518714eac6455af80e48",
    "DISC-06": "c9713b6d4f1aa9b647c989eb3720c08b13a19da93ddbe8c2033608b54d25cddf",
    "DISC-07": "502514417fe22967e5af6bd61960f65c632605707140ac75c552c8c4b89e5343",
    "DISC-08": "aefc13fcce46f1fafc6e753c98a5ee7c9dc395574388e32b86f549119e8c3fbf",
    "DISC-09": "3d2b3f3afe5966a5b8588f3110d2ed8c9b7bbd26b179dfa1c91faae3a591d521",
}


def read_source(path: Path) -> Tuple[Any, str, List[str]]:
    """返回 (解析后的 JSON, 原文, 错误列表)；扫描不到真源时同样要红。"""
    if not path.is_file():
        return None, "", [f"A1: 找不到评测集真源 {path}"]
    try:
        text = path.read_text(encoding="utf-8")
        return json.loads(text), text, []
    except (OSError, ValueError) as exc:
        return None, "", [f"A1: 评测集真源解析失败 {path.name}: {exc}"]


# 单次 check() 内的扫描缓存：每条用例若各自 rglob + 读文件，门禁会从毫秒级退化到分钟级。
# 缓存由 check() 在入口清空，跨调用绝不复用 —— 否则「删掉测试源码」的负向探针会命中旧缓存而恒绿。
_SOURCE_CACHE: Dict[str, List[Path]] = {}
_TEXT_CACHE: Dict[Path, str] = {}


def java_test_sources(root: Path) -> List[Path]:
    key = str(root)
    if key not in _SOURCE_CACHE:
        out: List[Path] = []
        base = root / TEST_ROOT_REL
        if base.is_dir():
            for path in base.rglob("*.java"):
                if "target" not in path.parts:
                    out.append(path)
        _SOURCE_CACHE[key] = sorted(out)
    return _SOURCE_CACHE[key]


def _source_text(path: Path) -> str:
    if path not in _TEXT_CACHE:
        _TEXT_CACHE[path] = path.read_text(encoding="utf-8", errors="ignore")
    return _TEXT_CACHE[path]


def case_field_errors(case: Any, tag: str) -> List[str]:
    """A3：单条用例字段契约。独立函数便于哨兵直接注入已知违规。"""
    if not isinstance(case, dict):
        return [f"A3: {tag} 必须为对象"]
    errs: List[str] = []
    missing = [f for f in CASE_FIELDS if f not in case]
    if missing:
        errs.append(f"A3: {tag} 缺少字段 {', '.join(missing)}")
    if case.get("kind") not in ("positive", "negative"):
        errs.append(f"A3: {tag}.kind 必须为 positive|negative")
    if case.get("piiFree") is not True:
        errs.append(f"A3: {tag}.piiFree 必须为 true（§24 PII 禁入评测集）")
    expected = case.get("expected")
    if not isinstance(expected, dict) or not expected:
        errs.append(f"A3: {tag}.expected 必须为非空对象（没有期望值＝没有判定基准）")
    for field in ("id", "name", "suite", "regulatoryBasis", "testClass", "testSymbol"):
        if not isinstance(case.get(field), str) or not case[field].strip():
            errs.append(f"A3: {tag}.{field} 必须非空")
    return errs


def symbol_errors(case: Dict[str, Any], root: Path, tag: str) -> List[str]:
    """A4：清单声明的用例必须在真实测试源码里找得到，防「清单抢跑于代码」。"""
    cls = case.get("testClass")
    sym = case.get("testSymbol")
    if not isinstance(cls, str) or not cls.strip() or not isinstance(sym, str) or not sym.strip():
        return []  # 归 A3 管，避免重复报错
    hits = [p for p in java_test_sources(root) if p.name == cls + ".java"]
    if not hits:
        return [f"A4: {tag}.testClass={cls} 找不到测试类文件；清单不得抢跑于代码"]
    text = "\n".join(_source_text(p) for p in hits)
    # 用子串而非正则：参数化场景名含全角符号/箭头/en dash，正则转义会引入假阴性。
    if sym not in text:
        return [f"A4: {tag}.testSymbol={sym!r} 未在 {cls}.java 中命中（用例可能已被删）"]
    return []


def ref_errors(case: Dict[str, Any], root: Path, tag: str) -> List[str]:
    """A5：ground truth 来源必须指向仓库内真实文件，防「随手编来源」。"""
    errs: List[str] = []
    ref = case.get("groundTruthRef")
    if not isinstance(ref, str) or "#" not in ref:
        return [f"A5: {tag}.groundTruthRef 必须为 path#anchor 形式（仓库内真源 + 定位锚点）"]
    path, anchor = ref.split("#", 1)
    if not path.strip() or not anchor.strip():
        errs.append(f"A5: {tag}.groundTruthRef 的 path 与 anchor 均不可为空")
    elif not (root / path).is_file():
        errs.append(f"A5: {tag}.groundTruthRef 指向的真源不存在：{path}")
    basis = case.get("regulatoryBasis")
    if not isinstance(basis, str) or not basis.strip():
        errs.append(f"A5: {tag}.regulatoryBasis 必须标注监管/契约依据")
    return errs


def inventory_errors(cases: Any, suites: Any) -> List[str]:
    """A2：库存下界、id 唯一、suite 覆盖。"""
    errs: List[str] = []
    if not isinstance(cases, list) or not cases:
        return ["A2: cases 必须为非空数组"]
    if len(cases) < MIN_CASES:
        errs.append(
            f"A2: 用例 {len(cases)} 条 < 硬编码下界 {MIN_CASES}；禁止通过删用例让门禁变轻"
        )
    ids = [c.get("id") for c in cases if isinstance(c, dict)]
    if len(ids) != len(set(ids)):
        errs.append("A2: 用例 id 重复")
    suite_ids = [s.get("id") for s in suites] if isinstance(suites, list) else []
    for required in REQUIRED_SUITES:
        if required not in suite_ids:
            errs.append(f"A2: 缺少回归套件 {required}（不得通过删套件缩小覆盖面）")
    for idx, case in enumerate(cases):
        if isinstance(case, dict) and case.get("suite") not in suite_ids:
            errs.append(f"A2: cases[{idx}].suite 未在 suites 中声明")
    return errs


def negative_errors(cases: Any) -> List[str]:
    """A6：关键负向探针不可删，负向占比有下界。"""
    if not isinstance(cases, list) or not cases:
        return []  # 归 A2 管
    by_id = {c.get("id"): c for c in cases if isinstance(c, dict)}
    errs: List[str] = []
    for required in REQUIRED_NEGATIVE_IDS:
        case = by_id.get(required)
        if case is None:
            errs.append(f"A6: 关键负向探针 {required} 缺失（护栏被抹掉）")
        elif case.get("kind") != "negative":
            errs.append(f"A6: 关键负向探针 {required} 的 kind 被改成 {case.get('kind')!r}")
    negatives = sum(1 for c in cases if isinstance(c, dict) and c.get("kind") == "negative")
    ratio = negatives / len(cases)
    if ratio < MIN_NEGATIVE_RATIO:
        errs.append(
            f"A6: 负向用例占比 {ratio:.2f} < {MIN_NEGATIVE_RATIO:.2f}；"
            "只留全绿样本等于没有回归能力"
        )
    return errs


def policy_errors(doc: Dict[str, Any]) -> List[str]:
    """A7：评测集治理政策五要素。"""
    policy = doc.get("groundTruthPolicy")
    if not isinstance(policy, dict):
        return ["A7: groundTruthPolicy 必须为对象"]
    errs: List[str] = []
    missing = [f for f in POLICY_FIELDS if f not in policy]
    if missing:
        errs.append(f"A7: groundTruthPolicy 缺少字段 {', '.join(missing)}")
    if policy.get("piiFree") is not True:
        errs.append("A7: groundTruthPolicy.piiFree 必须为 true")
    for field in POLICY_FIELDS:
        if field == "piiFree":
            continue
        if not isinstance(policy.get(field), str) or not policy[field].strip():
            errs.append(f"A7: groundTruthPolicy.{field} 必须非空")
    return errs


def pii_errors(text: str) -> List[str]:
    """A8：§24 PII 禁入评测集。"""
    errs: List[str] = []
    for name, pattern in PII_PATTERNS:
        hit = pattern.search(text)
        if hit:
            errs.append(f"A8: 评测集疑似含{name}（命中 {hit.group()[:4]}…）；§24 禁止 PII 入集")
    return errs


def _canonical(payload: Any) -> str:
    """稳定序列化：键排序 + 紧凑分隔，字段顺序无关、任何值改动必变哈希。"""
    return json.dumps(payload, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def case_verdict_hash(case: Any) -> str:
    """单条用例的判定语义哈希：只覆盖 id / kind / expected。

    刻意不含 name / suite / groundTruthRef / 来源标注 —— 改文案不该被判红，
    否则门禁会因为改错别字天天红，最后被关掉。id 参与哈希是为了让哈希不可
    在用例间移植（否则把 A 的哈希贴给 B 就能让 B 用 A 的期望值蒙混）。
    """
    body = {
        "id": case.get("id"),
        "kind": case.get("kind"),
        "expected": case.get("expected"),
    }
    return hashlib.sha256(_canonical(body).encode("utf-8")).hexdigest()


def expected_freeze_errors(cases: Any, baseline: Dict[str, str] | None = None) -> List[str]:
    """A10：期望值冻结。基线为空时判失败——空基线等于 A10 恒绿。"""
    base = EXPECTED_HASHES if baseline is None else baseline
    if not base:
        return ["A10: 期望值冻结基线为空，A10 恒绿（基线必须硬编码在脚本里）"]
    if not isinstance(cases, list) or not cases:
        return []  # 归 A2 管，避免重复报错
    errs: List[str] = []
    seen: set[str] = set()
    for idx, case in enumerate(cases):
        if not isinstance(case, dict):
            continue  # 归 A3 管
        cid = case.get("id")
        if not isinstance(cid, str) or not cid.strip():
            continue  # 归 A3 管
        seen.add(cid)
        frozen = base.get(cid)
        if frozen is None:
            errs.append(
                f"A10: cases[{idx}] 新用例 {cid} 未登记期望值基线；"
                "新增用例须跑 --print-hashes 同步基线并走评测集评审"
            )
        elif case_verdict_hash(case) != frozen:
            errs.append(
                f"A10: {cid} 的 kind/expected 与冻结基线不一致"
                "（期望值被改动？改判据必须同步改基线并说明理由）"
            )
    for cid in sorted(set(base) - seen):
        errs.append(f"A10: 基线登记的用例 {cid} 已从评测集消失（静默下线判据）")
    return errs


def sentinel(root: Path) -> List[str]:
    """A9 独立哨兵：门禁不能只问「你自证通过吗」——直接给判据函数喂已知违规。

    用**独立构造的最小夹具**（不读真源），任何判据被改成恒返回 [] 都会被当场抓住。
    """
    base = {
        "id": "PROBE",
        "name": "probe",
        "suite": REQUIRED_SUITES[0],
        "kind": "negative",
        "expected": {"ok": True},
        "groundTruthRef": EVALSET_REL.as_posix() + "#probe",
        "regulatoryBasis": "probe",
        "testClass": "DecisionTriageGoldenSetTest",
        "testSymbol": "baselineGreenScenario_AutoApprove",
        "piiFree": True,
    }
    probes: Tuple[Tuple[str, str, Any], ...] = (
        ("A2", "库存下界", lambda: inventory_errors([base], [{"id": REQUIRED_SUITES[0]}])),
        ("A3", "piiFree=非真", lambda: case_field_errors({**base, "piiFree": False}, "probe")),
        (
            "A4",
            "symbol 漂移",
            lambda: symbol_errors({**base, "testSymbol": "NO_SUCH_SYMBOL_XYZ"}, root, "probe"),
        ),
        (
            "A5",
            "来源不可达",
            lambda: ref_errors({**base, "groundTruthRef": "no/such/file.md#x"}, root, "probe"),
        ),
        ("A6", "负向探针被降级", lambda: negative_errors([{**base, "kind": "positive"}])),
        ("A8", "PII 命中", lambda: pii_errors('{"fake": "13800138000"}')),
        # A10 三条独立探针：期望值被改（mismatch 分支）、正负向被翻（kind 是否
        # 真的进哈希）、基线为空（恒绿分支）。基线显式传入，不依赖全局
        # EXPECTED_HASHES —— 否则基线一空，"为空"判据会冒领"已命中"，真正退化的
        # mismatch 分支永远测不到。
        (
            "A10",
            "期望值被改",
            lambda: expected_freeze_errors(
                [base],
                {"PROBE": case_verdict_hash({**base, "expected": {"ok": False}})},
            ),
        ),
        (
            "A10",
            "kind 被翻",
            lambda: expected_freeze_errors(
                [{**base, "kind": "positive"}],
                {"PROBE": case_verdict_hash(base)},
            ),
        ),
        ("A10", "基线为空", lambda: expected_freeze_errors([base], {})),
    )
    errs: List[str] = []
    for rule, label, fn in probes:
        got = fn()
        if not any(e.startswith(rule + ":") for e in got):
            errs.append(f"A9: 独立哨兵未命中 {rule}（{label}）；判据可能已退化成恒绿")
    return errs


def check(root: Path) -> List[str]:
    """纯函数：返回违规列表（空表示通过）。单测与 CI 共用，main 只做编排。"""
    root = Path(root)
    _SOURCE_CACHE.clear()
    _TEXT_CACHE.clear()
    doc, raw, errors = read_source(root / EVALSET_REL)
    sources = java_test_sources(root)
    if not sources:
        errors.append("A9: 测试源码扫描命中 0 个 .java；路径失效会让 A4 恒绿，必须判失败")
    errors.extend(sentinel(root))
    if doc is None:
        return errors
    if not isinstance(doc, dict):
        return errors + ["A1: 评测集真源必须为 JSON 对象"]

    if doc.get("schemaVersion") != 1:
        errors.append("A1: schemaVersion 必须为 1")
    if doc.get("status") not in ("declared", "wired"):
        errors.append("A1: status 必须为 declared|wired")
    for field in ("evalsetId", "owner", "scope"):
        if not isinstance(doc.get(field), str) or not doc[field].strip():
            errors.append(f"A1: {field} 必须非空")

    errors.extend(policy_errors(doc))
    cases = doc.get("cases")
    suites = doc.get("suites")
    errors.extend(inventory_errors(cases, suites))
    errors.extend(negative_errors(cases))
    errors.extend(expected_freeze_errors(cases))
    errors.extend(pii_errors(raw))
    if isinstance(cases, list):
        for idx, case in enumerate(cases):
            tag = f"cases[{idx}]"
            errors.extend(case_field_errors(case, tag))
            if isinstance(case, dict):
                errors.extend(symbol_errors(case, root, tag))
                errors.extend(ref_errors(case, root, tag))
    return errors


def baseline_block(cases: Any) -> str:
    """按用例序生成可直接粘贴的 EXPECTED_HASHES 字面量（避免手抄哈希出错）。"""
    if not isinstance(cases, list):
        return ""
    lines = []
    for case in cases:
        if not isinstance(case, dict) or not isinstance(case.get("id"), str):
            continue
        lines.append(f'    "{case["id"]}": "{case_verdict_hash(case)}",')
    return "EXPECTED_HASHES: Dict[str, str] = {\n" + "\n".join(lines) + "\n}"


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="理赔 AI 决策回归评测集门禁")
    ap.add_argument("--check", action="store_true", help="CI 模式：有违规即非零退出")
    ap.add_argument("--root", default=str(REPO), help="仓库根（单测注入用）")
    ap.add_argument(
        "--print-hashes",
        action="store_true",
        help="输出期望值冻结基线（改判据/加用例后同步 EXPECTED_HASHES 用）",
    )
    args = ap.parse_args(argv)

    if args.print_hashes:
        doc, _, errors = read_source(Path(args.root) / EVALSET_REL)
        if errors:
            for e in errors:
                print("  · " + e)
            return 1
        assert isinstance(doc, dict)  # read_source 成功时必为 dict
        print(baseline_block(doc.get("cases")))
        return 0

    errors = check(Path(args.root))
    if errors:
        print(f"❌ 理赔 AI 评测集门禁：{len(errors)} 项不合规")
        for e in errors:
            print("  · " + e)
        return 1
    print(
        f"OK: 理赔 AI 决策回归评测集通过（用例库存 ≥ {MIN_CASES} 条硬编码下界 / "
        "ground truth 可达 / 负向探针齐全 / 期望值未漂移 / PII 零命中 / 判据自证有效）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
