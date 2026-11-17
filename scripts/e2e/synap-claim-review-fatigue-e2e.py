#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
审核疲劳防控 E2E（只读评估端点 + 配置消费证据）
================================================
覆盖端点（挂在已登记网关白名单 /api/v1/review/** 下）：
  GET /api/v1/review/fatigue-guard/evaluation  只读评估（soft prompt，不写库）

判据「接口状态 + MySQL 第二证据源」：
  E1 端点返回 200 且含四项配置与 rejected（不暴露裸 String）
  E2 连续时长达阈值 ⇒ restPrompt=true（等于阈值即提示）
  E3 敏感金额 + 班末不足阈值 ⇒ sensitiveEligible=false（仅提示）
  E4 端点前后 t_claim_task 行数不变（只读，无转派/改状态副作用）
  E5 返回 maxPerShift 与 t_feature_toggle 的配置读回一致（无配置则为基线 120）

完整路径字面量必要：E2E 影响分析按用例里的路径字面量匹配 Controller 端点。
用法：python3 scripts/e2e/synap-claim-review-fatigue-e2e.py
"""
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_sibling(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load_sibling("e2e_synap_claim")

EVALUATION_PATH = "/review/fatigue-guard/evaluation"
EVAL_REST_PATH = "/review/fatigue-guard/evaluation?continuousMinutes=120"
EVAL_SENSITIVE_PATH = (
    "/review/fatigue-guard/evaluation?claimAmount=10000&minutesToShiftEnd=0"
)
BASELINE_MAX = 120


def _evaluation(token):
    code, body = S.call("GET", EVALUATION_PATH, token=token, expect=(200,))
    data = body.get("data") if isinstance(body, dict) else None
    return code, data if isinstance(data, dict) else {}


def main():
    token = S.login(*S.ADMIN)
    results = []

    before = S.db_query("SELECT COUNT(*) c FROM t_claim_task WHERE deleted=0")[0]["c"]

    code, data = _evaluation(token)
    cfg = data.get("config", {}) if isinstance(data, dict) else {}
    keys = {"maxPerShift", "restPromptMinutes", "shiftEndExclusionMinutes",
            "sensitiveAmountYuan", "rejected"}
    ok_e1 = code == 200 and keys.issubset(cfg) and isinstance(cfg.get("rejected"), dict)
    S.record("E1 评估端点返回完整配置对象", ok_e1, f"http={code} config={cfg}")
    results.append(ok_e1)

    code, data = S.call("GET", EVAL_REST_PATH, token=token, expect=(200,))
    rest = (data.get("data") or {}).get("restPrompt")
    ok_e2 = code == 200 and rest is True
    S.record("E2 连续时长达阈值即提示休息", ok_e2, f"http={code} restPrompt={rest}")
    results.append(ok_e2)

    code, data = S.call("GET", EVAL_SENSITIVE_PATH, token=token, expect=(200,))
    sensitive = (data.get("data") or {}).get("sensitiveEligible")
    ok_e3 = code == 200 and sensitive is False
    S.record("E3 敏感金额+班末不足仅提示不适合派单", ok_e3,
             f"http={code} sensitiveEligible={sensitive}")
    results.append(ok_e3)

    after = S.db_query("SELECT COUNT(*) c FROM t_claim_task WHERE deleted=0")[0]["c"]
    ok_e4 = before == after
    S.record("E4 只读端点不写任务事实", ok_e4, f"before={before} after={after}")
    results.append(ok_e4)

    rows = S.db_query(
        "SELECT config_json, state FROM t_feature_toggle "
        "WHERE feature_key='REVIEW_FATIGUE_GUARD' AND deleted=0")
    expected = BASELINE_MAX
    if rows and rows[0].get("state") == "ENABLED" and rows[0].get("config_json"):
        try:
            parsed = json.loads(rows[0]["config_json"])
            if isinstance(parsed, dict) and isinstance(parsed.get("maxPerShift"), int):
                expected = parsed["maxPerShift"]
        except (ValueError, TypeError):
            pass
    actual = cfg.get("maxPerShift")
    ok_e5 = actual == expected
    S.record("E5 生效阈值与库内配置读回一致", ok_e5,
             f"db={expected} response={actual}")
    results.append(ok_e5)

    verdict = all(results)
    print("VERDICT " + " ".join(f"E{i+1}={v}" for i, v in enumerate(results)) + f" all={verdict}")
    return 0 if verdict else 1


if __name__ == "__main__":
    sys.exit(main())
