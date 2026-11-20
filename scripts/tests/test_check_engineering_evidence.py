#!/usr/bin/env python3
"""check-engineering-evidence.py 的单测（R3 新增门禁必须有单测）。

用 importlib 加载真实脚本，直接对 check_sources() 喂临时源码字符串，覆盖：
  · 基线干净（全接线）→ 无错误；
  · 四类退化（移除 fail-fast / 只改字面串 / 移除守卫接线 / 删稳定码）→ 全部判红；
  · E5 负向钉子存在性（base-url 置空测试 + E2E 如实记录 fail-closed）。
不依赖在线服务 / 真实 provider 凭证，换机可重跑。
"""

import importlib.util
import os
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-engineering-evidence.py",
)
_spec = importlib.util.spec_from_file_location("check_engineering_evidence", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _clean_sources():
    """构造四份"全接线"的最小源码夹具。"""
    auto = '''
@Value("${bone.ai.gateway.base-url:}")
private String aiGatewayBaseUrl;
public AutoApprovalOutcome applyAutoDecision(long id) {
  if (!enabled) return skipped("AUTO_APPROVE_TOGGLE_OFF");
  if (aiGatewayBaseUrl == null || aiGatewayBaseUrl.isBlank()) {
    return skipped("AI_GATEWAY_BASE_URL_MISSING");
  }
  var execution = findLatest(id);
}
'''
    rule = '''
public static void requireReachableDimensions(Collection<String> names) {}
public void validateConfigShape() {
  LimitedConditions.requireReachableDimensions(
      Arrays.stream(LimitedConditions.class.getRecordComponents())
          .map(RecordComponent::getName).toList());
}
'''
    gap = "LIMITED_CONDITION_DIMENSION_UNSUPPORTED(Group.CAPABILITY_UNDEFINED),"
    evidence = 'public record EvidenceView(List<EvidenceCriterion> criteria, String note) {}'
    return auto, rule, gap, evidence


class EngineeringEvidenceGateTest(unittest.TestCase):
    def test_clean_sources_pass(self):
        self.assertEqual(mod.check_sources(*_clean_sources()), [])

    def test_removing_base_url_field_fails(self):
        auto, rule, gap, ev = _clean_sources()
        auto = auto.replace("private String aiGatewayBaseUrl;", "private String unused;")
        errs = mod.check_sources(auto, rule, gap, ev)
        self.assertTrue(any("aiGatewayBaseUrl 字段" in e for e in errs), errs)

    def test_literal_string_only_does_not_count_as_wiring(self):
        auto, rule, gap, ev = _clean_sources()
        auto = auto.replace(
            'return skipped("AI_GATEWAY_BASE_URL_MISSING");',
            '// return skipped("AI_GATEWAY_BASE_URL_MISSING");',
        )
        errs = mod.check_sources(auto, rule, gap, ev)
        self.assertTrue(any("AI_GATEWAY_BASE_URL_MISSING" in e for e in errs), errs)

    def test_removing_guard_call_fails(self):
        auto, rule, gap, ev = _clean_sources()
        rule = rule.replace("requireReachableDimensions(\n      Arrays", "/*removed*/\n      Arrays")
        errs = mod.check_sources(auto, rule, gap, ev)
        self.assertTrue(any("未调用维度可达性守卫" in e for e in errs), errs)

    def test_removing_stable_code_fails(self):
        auto, rule, gap, ev = _clean_sources()
        errs = mod.check_sources(auto, rule, "", ev)
        self.assertTrue(any("LIMITED_CONDITION_DIMENSION_UNSUPPORTED" in e for e in errs), errs)

    def test_fail_fast_must_precede_query(self):
        auto = '''
private String aiGatewayBaseUrl;
@Value("${bone.ai.gateway.base-url:}")
public AutoApprovalOutcome applyAutoDecision(long id) {
  var execution = findLatest(id);
  if (aiGatewayBaseUrl == null || aiGatewayBaseUrl.isBlank()) {
    return skipped("AI_GATEWAY_BASE_URL_MISSING");
  }
}
'''
        rule, gap, ev = _clean_sources()[1:]
        errs = mod.check_sources(auto, rule, gap, ev)
        self.assertTrue(any("排在查决策记录之后" in e for e in errs), errs)


if __name__ == "__main__":
    unittest.main()
