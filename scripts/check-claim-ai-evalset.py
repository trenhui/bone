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

刻意不做：不校验模型准确率/自动化率、不要求 provider 已接线、不打开自动放款开关。
这些依赖真实样本与灰度证据，硬编码阈值只会逼出假数据。

用法:
  python3 scripts/check-claim-ai-evalset.py --check     # CI blocking
  python3 scripts/check-claim-ai-evalset.py             # 人类可读报告
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

REPO = Path(__file__).resolve().parent.parent
EVALSET_REL = Path("bone-apps/synap-claim/eval/ai-decision-evalset.json")
TEST_ROOT_REL = Path("bone-apps/synap-claim/src/test/java")

# 下界硬编码在脚本里，不读 JSON —— 否则「把 minCases 一起改小」就能让门禁变轻。
MIN_CASES = 30
MIN_NEGATIVE_RATIO = 0.5
REQUIRED_SUITES: Tuple[str, ...] = (
    "decisionTriage",
    "aiAssessNode",
    "capabilityReadiness",
    "readinessController",
)
# 监管最在意的七条负向探针：删掉任何一条都等于抹掉「无法判定≠拒赔 / 清单≠放款许可」的护栏。
REQUIRED_NEGATIVE_IDS: Tuple[str, ...] = (
    "AIASSESS-01",
    "AIASSESS-05",
    "READY-02",
    "READY-03",
    "READY-04",
    "READY-05",
    "CTRL-02",
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


def read_source(path: Path) -> Tuple[Any, str, List[str]]:
    """返回 (解析后的 JSON, 原文, 错误列表)；扫描不到真源时同样要红。"""
    if not path.is_file():
        return None, "", [f"A1: 找不到评测集真源 {path}"]
    try:
        text = path.read_text(encoding="utf-8")
        return json.loads(text), text, []
    except (OSError, ValueError) as exc:
        return None, "", [f"A1: 评测集真源解析失败 {path.name}: {exc}"]


# 单次 check() 内的扫描缓存：30 条用例若各自 rglob + 读文件，门禁会从毫秒级退化到分钟级。
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
    probes: Tuple[Tuple[str, Any], ...] = (
        ("A2", lambda: inventory_errors([base], [{"id": REQUIRED_SUITES[0]}])),
        ("A3", lambda: case_field_errors({**base, "piiFree": False}, "probe")),
        ("A4", lambda: symbol_errors({**base, "testSymbol": "NO_SUCH_SYMBOL_XYZ"}, root, "probe")),
        ("A5", lambda: ref_errors({**base, "groundTruthRef": "no/such/file.md#x"}, root, "probe")),
        ("A6", lambda: negative_errors([{**base, "kind": "positive"}])),
        ("A8", lambda: pii_errors('{"fake": "13800138000"}')),
    )
    errs: List[str] = []
    for rule, fn in probes:
        got = fn()
        if not any(e.startswith(rule + ":") for e in got):
            errs.append(f"A9: 独立哨兵未命中 {rule}；判据可能已退化成恒绿")
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
    errors.extend(pii_errors(raw))
    if isinstance(cases, list):
        for idx, case in enumerate(cases):
            tag = f"cases[{idx}]"
            errors.extend(case_field_errors(case, tag))
            if isinstance(case, dict):
                errors.extend(symbol_errors(case, root, tag))
                errors.extend(ref_errors(case, root, tag))
    return errors


def main(argv: List[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="理赔 AI 决策回归评测集门禁")
    ap.add_argument("--check", action="store_true", help="CI 模式：有违规即非零退出")
    ap.add_argument("--root", default=str(REPO), help="仓库根（单测注入用）")
    args = ap.parse_args(argv)

    errors = check(Path(args.root))
    if errors:
        print(f"❌ 理赔 AI 评测集门禁：{len(errors)} 项不合规")
        for e in errors:
            print("  · " + e)
        return 1
    print(
        "OK: 理赔 AI 决策回归评测集通过（30 例登记 / ground truth 可达 / 负向探针齐全 / "
        "PII 零命中 / 判据自证有效）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
