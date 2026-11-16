#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI 配置治理 E2E（批 2 · 受控写 / 棘轮 / 写开关 / 盲标拒绝）
============================================================
针对批 2 新增的两个配置端点做真实联调，判定一律「接口状态码 + 直查 MySQL 第二证据源」，
避免「接口 200 但没落库」或「接口拒绝但库被改」的假绿。

覆盖端点：
  GET  /api/v1/claims/ai/config/governance       配置快照（当前生效阈值 + 写开关）
  PUT  /api/v1/claims/ai/config/triage-limits    受控写（仅收紧）
  POST /api/v1/claims/{claimId}/ai/feedback      盲标拒绝（2.2 既有能力留证）

不变量（每条都带反证，不只看状态码）：
  C1 快照 5 字段齐全，writeEnabled 是布尔（不得把未配置压成字符串）
  C2 放宽金额被拒：400 + SC_CLAIM_CONFIG_RATCHET_VIOLATION
     （相对「当前生效值」，而非代码基线——否则先收紧再放宽的后门测不出来）
  C3 放宽被拒后库内 config_json 逐字未变（第二证据源）
  C4 盲标场景 scene=blind_label 不得产生 AI 裁决回流

骨架纪律（来自 e2e 模板，勿删）：
  1. 地址/凭据只从 e2e_lib.config 取，脚本内不出现 host:port 与口令；
  2. 不自己造数：只读既有配置/案件，放宽探针被拒后零写入；
  3. 断言只走 e2e_lib.report，失败详情写实测值；
  4. 结果出口是退出码（+可选 JUnit XML），不靠人读控制台。

前置：网关 8888 / claim 8087 / iam 8081 在线；admin/123456 可登录；MySQL 可直连。
用法：python3 scripts/e2e/synap-claim-ai-config-governance-e2e.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from e2e_lib import config, http
from e2e_lib.report import check, finish, note, section


def _load(name):
    """复用同目录既有 E2E 的登录 / 调用 / 直连数据库助手，不复制第三份。"""
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load("e2e_synap_claim")

# 端点模板集中在此：impact.py 按这些字面量判覆盖，改路径只改一处。
API_CONFIG = "/claims/ai/config/governance"
API_LIMITS = "/claims/ai/config/triage-limits"
API_FEEDBACK = "/claims/{claimId}/ai/feedback"

LIMIT_KEYS = (
    "autoAmountLimit", "aiManualThreshold", "aiReviewThreshold",
    "ocrManualThreshold", "dualReviewAmount",
)


def db_config_json():
    rows = S.db_query(
        "SELECT config_json FROM t_feature_toggle "
        "WHERE tenant_id = 0 AND feature_key = 'TRIAGE_LIMITS'"
    )
    return rows[0]["config_json"] if rows else None


def pick_claim_id():
    rows = S.db_query(
        "SELECT id FROM t_claim_case WHERE tenant_id = 0 AND deleted = 0 ORDER BY id LIMIT 1"
    )
    return int(rows[0]["id"]) if rows else None


def error_code(body):
    if not isinstance(body, dict):
        return ""
    return (body.get("data") or {}).get("errorCode") or body.get("errorCode") or ""


def main():
    section("AI 配置治理 E2E（批 2）")
    note(f"BASE={config.BASE}  USER={config.USER}  TENANT={config.TENANT}")
    http.login()  # 主会话；失败直接退出（没登录跑什么都是假红，不要用「跳过」掩盖）

    # C1：快照 5 字段齐全 + writeEnabled 布尔语义
    st, body = http.call("GET", API_CONFIG)
    data = (body.get("data") or {}) if isinstance(body, dict) else {}
    limits = data.get("limits") or {}
    has5 = all(k in limits for k in LIMIT_KEYS)
    check("GET 配置快照 200 且 5 字段齐全", st == 200 and has5,
          f"status={st} limits={sorted(limits)} writeEnabled={data.get('writeEnabled')!r}")
    check("writeEnabled 是布尔而非字符串", isinstance(data.get("writeEnabled"), bool),
          f"raw={data.get('writeEnabled')!r}")
    if st != 200 or not has5:
        return 0

    # C2/C3：相对当前生效值放宽金额必须被拒，且库内逐字未变
    before = db_config_json()
    cur = float(limits["autoAmountLimit"])
    st2, body2 = http.call("PUT", API_LIMITS, {"autoAmountLimit": cur + 1})
    err2 = error_code(body2)
    after = db_config_json()
    check("放宽金额被拒 400 + RATCHET_VIOLATION",
          st2 == 400 and err2 == "SC_CLAIM_CONFIG_RATCHET_VIOLATION",
          f"status={st2} errorCode={err2} body={str(body2)[:160]}")
    check("放宽被拒后库内 config_json 未改写", before == after,
          f"before={before} after={after}")

    # C4：盲标场景不得产生 AI 裁决回流（2.2 既有能力，本批只留证不重写）
    cid = pick_claim_id()
    if cid is None:
        note("库内无可用案件，跳过盲标探针（非本批新增代码路径）")
    else:
        blind = {
            "eventId": "E2E-BLIND-" + config.run_suffix(),
            "eventType": "DISPOSITION",
            "involvement": "AI_ASSISTED",
            "scene": "blind_label",
            "disposition": "ADOPT",
            "traceId": "e2e-config-governance",
        }
        st3, body3 = http.call("POST", API_FEEDBACK.format(claimId=cid), blind)
        text3 = str(body3)
        check("盲标场景不得产生 AI 裁决回流",
              st3 in (400, 403, 422) and ("blind_label" in text3 or "盲标" in text3),
              f"status={st3} body={text3[:200]}")
    return 0


if __name__ == "__main__":
    main()
    finish("AI 配置治理 E2E（批 2）汇总")
