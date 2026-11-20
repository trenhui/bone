#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工程证据底座门禁（synapclaim-engineering-evidence-baseline 4.1）。

守什么
------
把「fail-closed 姿态」从口头宣称变成可机检、可变异证伪的接线事实。本门禁只查**源码接线**，
不引入第二套门禁、不放宽 ``_ai_native_readiness.py`` 的 12 项判据：

  E1 provider fail-fast 接线：``AutoApprovalApplicationService.applyAutoDecision`` 在读取决策记录之前，
     对 ``aiGatewayBaseUrl`` 空值返回 ``AI_GATEWAY_BASE_URL_MISSING``（桩不冒充模型、不进入自动放款）。
  E2 不可达维度显式拒绝接线：``LimitedConditions.requireReachableDimensions`` 白名单守卫存在，且
     ``LiabilityRule.validateConfigShape`` 在配置写入期调用它（杜绝"配了不读"的静默第三态）。
  E3 稳定原因码：``RuleGap.Code.LIMITED_CONDITION_DIMENSION_UNSUPPORTED`` 已登记（受控枚举，禁自由文本）。
  E4 证据视图只读边界：``EngineeringEvidenceService`` 下发的判据不含放款/门禁结论字段。
  E5 无 provider 负向钉子：存在"base-url 置空 → 跳过且不碰聚合"的测试，且 E2E 如实记录 fail-closed。

反假绿（--self-test）
--------------------
对每条形变构造"接线被移除/被改成字面串"的负样本，断言 check() 会判红；只有负样本全部被抓，
主判据的绿灯才可信。只改状态字面串（如注释里写 AI_GATEWAY_BASE_URL_MISSING）不得翻绿。

用法：
  python3 scripts/check-engineering-evidence.py --check       # 主判据（接线缺失即 exit 1）
  python3 scripts/check-engineering-evidence.py --self-test   # 负向探针自证
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
ROOT = SCRIPT_DIR.parent

APP = ROOT / "bone-apps/synap-claim/synap-claim-app/src/main/java/com/bone/claim"
AUTO_APPROVE = APP / "application/AutoApprovalApplicationService.java"
LIABILITY_RULE = APP / "domain/model/adjustment/engine/LiabilityRule.java"
RULE_GAP = APP / "domain/model/adjustment/engine/RuleGap.java"
EVIDENCE_SERVICE = APP / "application/EngineeringEvidenceService.java"
AUTO_TEST = (
    ROOT
    / "bone-apps/synap-claim/synap-claim-app/src/test/java/com/bone/claim/application/AutoApprovalApplicationServiceTest.java"
)
E2E_DRIVER = ROOT / "scripts/e2e/e2e_synap_claim.py"

# 反假绿：这些字符串只允许出现在指定代码位置，注释/自由文本出现不算接线。
BASE_URL_ANNOTATION = re.compile(r'@Value\("\$ \{?bone\.ai\.gateway\.base-url:?[^}]*\}?"\)')
BASE_URL_FIELD = re.compile(r'\bprivate\s+String\s+aiGatewayBaseUrl\s*;')
FAIL_FAST_GUARD = re.compile(
    r'aiGatewayBaseUrl\s*==\s*null\s*\|\|\s*aiGatewayBaseUrl\.isBlank\(\)'
)
FAIL_FAST_REASON = "AI_GATEWAY_BASE_URL_MISSING"
REACHABLE_GUARD_DECL = re.compile(r'void\s+requireReachableDimensions\s*\(')
REACHABLE_GUARD_CALL = "requireReachableDimensions"
UNSUPPORTED_CODE = "LIMITED_CONDITION_DIMENSION_UNSUPPORTED"


def _read(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def _strip_comments(src: str) -> str:
    """去掉 // 行注释与 /* */ 块注释，确保'注释里写字样'不算接线。"""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    return src


def _method_body(src: str, signature: str) -> str:
    """取方法体（从 signature 起按花括号配平），失败返回空串。"""
    idx = src.find(signature)
    if idx < 0:
        return ""
    brace = src.find("{", idx)
    if brace < 0:
        return ""
    depth = 0
    for i in range(brace, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    return ""


def check_sources(auto: str, rule: str, gap: str, evidence: str) -> list[str]:
    """主判据：四项接线全部存在才算通过。"""
    errors: list[str] = []
    auto_code = _strip_comments(auto)
    rule_code = _strip_comments(rule)
    gap_code = _strip_comments(gap)
    evidence_code = _strip_comments(evidence)

    # E1a base-url 配置字段与 @Value 接线（允许 @Value("${bone.ai.gateway.base-url:}") 形态）
    if not BASE_URL_FIELD.search(auto_code):
        errors.append("E1: AutoApprovalApplicationService 缺少 aiGatewayBaseUrl 字段")
    if "bone.ai.gateway.base-url" not in auto_code:
        errors.append("E1: applyAutoDecision 未读取 bone.ai.gateway.base-url 配置")

    # E1b fail-fast 必须落在 applyAutoDecision 方法体内，且在 findLatest 之前
    body = _method_body(auto_code, "AutoApprovalOutcome applyAutoDecision")
    if not body:
        errors.append("E1: 找不到 applyAutoDecision 方法体")
    else:
        if not FAIL_FAST_GUARD.search(body):
            errors.append("E1: applyAutoDecision 缺少 base-url 空值 fail-fast 判据")
        if FAIL_FAST_REASON not in body:
            errors.append("E1: fail-fast 未返回受控原因码 AI_GATEWAY_BASE_URL_MISSING")
        guard_pos = body.find(FAIL_FAST_REASON)
        find_pos = body.find("findLatest")
        if guard_pos >= 0 and find_pos >= 0 and guard_pos > find_pos:
            errors.append("E1: fail-fast 排在查决策记录之后（须在最便宜的开关之后、任何查询之前）")

    # E2a 守卫声明 + E3 稳定码
    if not REACHABLE_GUARD_DECL.search(rule_code):
        errors.append("E2: LimitedConditions 缺少 requireReachableDimensions 白名单守卫")
    if UNSUPPORTED_CODE not in gap_code:
        errors.append("E3: RuleGap.Code 缺少 LIMITED_CONDITION_DIMENSION_UNSUPPORTED")

    # E2b 配置写入期接线：validateConfigShape 方法体必须调用守卫
    shape_body = _method_body(rule_code, "public void validateConfigShape()")
    if not shape_body:
        errors.append("E2: 找不到 validateConfigShape 方法体")
    elif REACHABLE_GUARD_CALL not in shape_body:
        errors.append("E2: validateConfigShape 未调用维度可达性守卫（配了不读的静默第三态）")

    # E4 证据视图不含放款/门禁结论字段（用字面串近似：类名不得出现 L3_CLAIMABLE/AUTO_APPROVE_ALLOWED 作为判据 ID）
    evidence_ids = re.findall(r'"([A-Z0-9_]+)"', evidence_code)
    for forbidden in ("L3_CLAIMABLE", "AUTO_APPROVE_ALLOWED"):
        if forbidden in evidence_ids:
            errors.append(f"E4: 证据视图含门禁结论字段 {forbidden}（证据不得翻绿门禁）")

    return errors


def check_e2e_nail() -> list[str]:
    """E5：无 provider 负向钉子（离线判据，非运行时 E2E）。"""
    errors: list[str] = []
    test_src = _strip_comments(_read(AUTO_TEST))
    # 必须存在"base-url 置空"的负向测试，且断言 reason 与"不碰聚合"双证据
    if "setField(service, \"aiGatewayBaseUrl\", \"\")" not in test_src:
        errors.append("E5: 缺少 base-url 置空的负向测试（无 provider 主链不中断）")
    if FAIL_FAST_REASON not in test_src:
        errors.append("E5: 负向测试未断言受控原因码 AI_GATEWAY_BASE_URL_MISSING")
    if "never()).create(" not in test_src or "never()).autoApprove(" not in test_src:
        errors.append("E5: 负向测试未断言不碰聚合（结案/建单）")
    # E2E 驱动须如实记录 fail-closed，不得宣称自动通过
    e2e_src = _read(E2E_DRIVER)
    if "MANUAL_REQUIRED" not in e2e_src or "fail-closed" not in e2e_src:
        errors.append("E5: E2E 驱动未如实记录 fail-closed(MANUAL_REQUIRED) 桩口径")
    return errors


def check(root: Path = ROOT) -> list[str]:
    errors = check_sources(
        _read(root / AUTO_APPROVE.relative_to(ROOT)),
        _read(root / LIABILITY_RULE.relative_to(ROOT)),
        _read(root / RULE_GAP.relative_to(ROOT)),
        _read(root / EVIDENCE_SERVICE.relative_to(ROOT)),
    )
    errors.extend(check_e2e_nail())
    return errors


def self_test() -> int:
    """负向探针：构造多种退化，每一种都必须被主判据判红。"""
    auto = _read(AUTO_APPROVE)
    rule = _read(LIABILITY_RULE)
    gap = _read(RULE_GAP)
    evidence = _read(EVIDENCE_SERVICE)
    baseline = check_sources(auto, rule, gap, evidence) + check_e2e_nail()
    if baseline:
        print("❌ self-test 基线自身不干净（主判据有红点），负向探针无意义：")
        for e in baseline:
            print("  · " + e)
        return 1

    mutations = [
        ("移除 base-url fail-fast 字段",
         ("auto", auto.replace("private String aiGatewayBaseUrl;", "private String baseUrlUnused;"))),
        ("把 fail-fast 原因码降级为注释（只改字面串不算接线）",
         ("auto", auto.replace('return AutoApprovalOutcome.skipped("AI_GATEWAY_BASE_URL_MISSING");',
                               '// return AutoApprovalOutcome.skipped("AI_GATEWAY_BASE_URL_MISSING");'))),
        ("移除 validateConfigShape 中的守卫调用",
         ("rule", rule.replace("requireReachableDimensions(\n        java.util.Arrays.stream", "/*removed*/\n        java.util.Arrays.stream"))),
        ("删除不可达维度稳定码",
         ("gap", gap.replace("LIMITED_CONDITION_DIMENSION_UNSUPPORTED(Group.CAPABILITY_UNDEFINED),", ""))),
    ]
    failed = 0
    for name, (which, mutated) in mutations:
        args = {"auto": auto, "rule": rule, "gap": gap, "evidence": evidence}
        args[which] = mutated
        errs = check_sources(args["auto"], args["rule"], args["gap"], args["evidence"])
        if not errs:
            print(f"❌ self-test 负向探针失效：{name} 未被判红（门禁可能恒绿）")
            failed += 1
        else:
            print(f"  ✓ 负向探针[{name}]被判红：{errs[0][:60]}")
    if failed:
        print(f"❌ self-test：{failed}/{len(mutations)} 负向探针失效")
        return 1
    print(f"OK: 工程证据门禁 self-test 通过（{len(mutations)}/{len(mutations)} 负向探针全部判红）")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="工程证据底座门禁")
    parser.add_argument("--check", action="store_true", help="主判据：接线缺失即 exit 1")
    parser.add_argument("--self-test", action="store_true", help="负向探针自证")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    errors = check()
    if errors:
        print(f"❌ 工程证据底座门禁：{len(errors)} 项接线缺失")
        for e in errors:
            print("  · " + e)
        return 1
    print(
        "OK: 工程证据底座门禁通过（provider fail-fast / 维度显式拒绝 / 稳定原因码 / 证据只读边界均接线，"
        "12 项成熟度判据未放宽）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
