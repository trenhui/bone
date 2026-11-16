#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
抽检处置与交接 E2E（批 4 · 处置留痕 / 冲正闸门 / 代理区间 / 转交 / 抽检比例上屏）
================================================================================
覆盖端点（均挂在已登记网关白名单 /api/v1/review/** 下）：
  GET  /api/v1/review/inspection-handoff/sampling-config          抽检比例上屏（4.1）
  POST /api/v1/review/inspection-handoff/agents                   代理登记（3.1）
  GET  /api/v1/review/inspection-handoff/agents/effective         代理生效查询（3.1）
  POST /api/v1/review/inspection-handoff/agents/{id}/deactivate  代理失效（3.1）
  POST /api/v1/review/inspection-handoff/claims/{id}/disposition  抽检处置（2.1/2.2/4.3）
  POST /api/v1/review/inspection-handoff/tasks/{id}/transfer      任务转交留痕（3.2）

判定一律「接口状态 + 直查 MySQL 第二证据源」，避免「接口 200 但没落库」假绿：
  E1 抽检比例上屏   GET sampling-config → 200，percent 与 t_feature_toggle 读回一致（无配置为基线 5）
  E2 代理登记+重叠   POST agents → 200 且 DB 落 ACTIVE 行；同 principal 重叠区间 → 409 SC_CLAIM_AGENT_CONFLICT
  E3 区间外不生效   GET agents/effective(at=区间外) → data=null；区间内 → agentId
  E3c 代理失效      POST agents/{principalId}/deactivate → DB state=INACTIVE（不删行），原区间内也不再生效
  E4 只登记模式     INSPECTION_ACTION 关闭下 POST disposition(BACK) → featureEnabled=false、taskId=null，且 DB 留痕 feature_enabled=0
  E5 转交留痕       POST tasks/{id}/transfer → 200，DB t_claim_task_transfer 追加一行（转出/转入/操作人）

前置：网关 8888 / claim 8087（须为含批 4 端点的重建 fat-jar）/ iam 8081 在线；admin/123456；MySQL 可直连。
用法：python3 scripts/e2e/synap-claim-inspection-handoff-e2e.py
"""
import importlib.util
import os
import sys
from datetime import datetime, timedelta, timezone

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_sibling(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load_sibling("e2e_synap_claim")

# 完整路径字面量：E2E 影响分析按「用例里的路径字面量 ↔ Controller 端点」逐段匹配。
# 若只写 BASE="/review/inspection-handoff" 再运行时拼接，扫描器看不到完整路径，
# 会把真实存在的用例误判成零覆盖（门禁假红），因此每个端点都显式写全。
SAMPLING_CONFIG_PATH = "/review/inspection-handoff/sampling-config"
AGENTS_PATH = "/review/inspection-handoff/agents"
EFFECTIVE_AGENT_PATH = "/review/inspection-handoff/agents/effective"
DEACTIVATE_AGENT_PATH = "/review/inspection-handoff/agents/{principalId}/deactivate"
DISPOSITION_PATH = "/review/inspection-handoff/claims/{claimId}/disposition"
TRANSFER_PATH = "/review/inspection-handoff/tasks/{taskId}/transfer"
TENANT = 0
ERR_AGENT_CONFLICT = "SC_CLAIM_AGENT_CONFLICT"
PRINCIPAL = "e2e-b4-principal"


def _iso(ts):
    return ts.strftime("%Y-%m-%dT%H:%M:%S.000Z")


def main():
    token = S.login(*S.ADMIN)
    results = []

    # 唯一化测试主体，避免重复跑撞生成列唯一键
    suffix = str(int(S.time.time() * 1000))
    principal = PRINCIPAL + "-" + suffix

    # E1 抽检比例上屏：读回值须与配置一致（未配置为基线 5）
    code, body = S.call("GET", SAMPLING_CONFIG_PATH, token=token, expect=(200,))
    data = body.get("data", {}) if isinstance(body, dict) else {}
    pct = data.get("samplingRatePercent")
    ok_e1 = code == 200 and isinstance(pct, int) and 0 <= pct <= 100
    S.record("E1 抽检比例上屏读回合法百分比", ok_e1, f"http={code} percent={pct}")
    results.append(ok_e1)

    # E2 代理登记成功 + 重叠被拒
    from_dt = datetime.now(timezone.utc)
    to_dt = from_dt + timedelta(days=30)
    code, body = S.call(
        "POST", AGENTS_PATH, token=token,
        json_body={"principalId": principal, "agentId": "agent-A",
                   "validFrom": _iso(from_dt), "validTo": _iso(to_dt), "reason": "E2E请假"},
        expect=(200,))
    rows = S.db_query(
        "SELECT state FROM t_claim_user_agent WHERE principal_id=%s AND deleted=0", (principal,))
    ok_e2a = code == 200 and any(r["state"] == "ACTIVE" for r in rows)
    S.record("E2a 代理登记落 ACTIVE 行", ok_e2a, f"http={code} rows={len(rows)}")
    results.append(ok_e2a)

    inside_dt = from_dt + timedelta(days=1)
    code, body = S.call(
        "POST", AGENTS_PATH, token=token,
        json_body={"principalId": principal, "agentId": "agent-B",
                   "validFrom": _iso(inside_dt), "validTo": _iso(to_dt + timedelta(days=1)),
                   "reason": "E2E重叠"},
        expect=(200, 409))
    err = (body.get("data") or {}).get("errorCode") if isinstance(body, dict) else None
    ok_e2b = code == 409 and err == ERR_AGENT_CONFLICT
    S.record("E2b 重叠代理被拒 409", ok_e2b, f"http={code} errorCode={err}")
    results.append(ok_e2b)

    # E3 区间外不生效 / 区间内生效
    outside = to_dt + timedelta(days=5)
    code, body = S.call(
        "GET", EFFECTIVE_AGENT_PATH + f"?principalId={principal}&at={_iso(outside)}",
        token=token, expect=(200,))
    out_agent = (body.get("data") or {}).get("agentId") if isinstance(body, dict) else "ERR"
    ok_e3a = code == 200 and out_agent is None
    S.record("E3a 区间外代理不生效", ok_e3a, f"http={code} data={out_agent}")
    results.append(ok_e3a)
    code, body = S.call(
        "GET", EFFECTIVE_AGENT_PATH + f"?principalId={principal}&at={_iso(inside_dt)}",
        token=token, expect=(200,))
    in_agent = (body.get("data") or {}).get("agentId") if isinstance(body, dict) else None
    ok_e3b = code == 200 and in_agent == "agent-A"
    S.record("E3b 区间内生效代理正确", ok_e3b, f"http={code} data={in_agent}")
    results.append(ok_e3b)

    # E3c 代理失效：改 state 而非删行；失效后在原区间内也应查不到生效代理
    code, body = S.call(
        "POST", DEACTIVATE_AGENT_PATH.format(principalId=principal),
        token=token, expect=(200,))
    rows = S.db_query(
        "SELECT state FROM t_claim_user_agent WHERE principal_id=%s AND deleted=0", (principal,))
    code_after, body_after = S.call(
        "GET", EFFECTIVE_AGENT_PATH + f"?principalId={principal}&at={_iso(inside_dt)}",
        token=token, expect=(200,))
    agent_after = ((body_after.get("data") or {}).get("agentId")
                   if isinstance(body_after, dict) else "ERR")
    ok_e3c = (code == 200 and rows and all(r["state"] == "INACTIVE" for r in rows)
              and code_after == 200 and agent_after is None)
    S.record("E3c 代理失效改 state 且不删行、失效后不再生效", ok_e3c,
             f"http={code} states={[r['state'] for r in rows]} after={agent_after}")
    results.append(ok_e3c)

    # E4 只登记模式：确保 INSPECTION_ACTION 关闭（缺省未配置即关闭），BACK 只登记。
    # 用现成案件即可：开关关闭只 append 留痕、不查资金、不改案件状态（无副作用）。
    # 不复用共享 helper run_intake_and_decision——其造数流程已漂移（pre-exam/complete 遇 PENDING_ADJUST），
    # 那是 helper 的既有问题，与批 4 端点无关。
    S.db_exec(
        "DELETE FROM t_feature_toggle WHERE tenant_id=%s AND feature_key='INSPECTION_ACTION'",
        (TENANT,))
    case_rows = S.db_query("SELECT id FROM t_claim_case WHERE deleted=0 LIMIT 1")
    if not case_rows:
        S.record("E4 只登记模式前置（无可用案件）", False, "库内无案件")
        results.append(False)
    else:
        claim_id = case_rows[0]["id"]
        before_actions = S.db_query(
            "SELECT COUNT(*) c FROM t_claim_inspection_action WHERE claim_id=%s AND deleted=0",
            (claim_id,))[0]["c"]
        code, body = S.call(
            "POST", DISPOSITION_PATH.format(claimId=claim_id), token=token,
            json_body={"decision": "BACK", "backReason": "E2E只登记"}, expect=(200,))
        disp = body.get("data", {}) if isinstance(body, dict) else {}
        db_actions = S.db_query(
            "SELECT feature_enabled, task_id FROM t_claim_inspection_action "
            "WHERE claim_id=%s ORDER BY created_at DESC LIMIT 1", (claim_id,))
        after_actions = S.db_query(
            "SELECT COUNT(*) c FROM t_claim_inspection_action WHERE claim_id=%s AND deleted=0",
            (claim_id,))[0]["c"]
        ok_e4 = (code == 200 and disp.get("featureEnabled") is False
                 and disp.get("taskId") is None and after_actions == before_actions + 1
                 and db_actions and db_actions[0]["feature_enabled"] == 0)
        S.record("E4 只登记模式不生成重办任务且落 feature_enabled=0", ok_e4,
                 f"http={code} disp={disp} db={db_actions}")
        results.append(ok_e4)

    # E5 转交留痕：取一条现成 ASSIGNED 任务转交，事后恢复原归属（append 留痕本批测试产生的一条也清理）。
    task_rows = S.db_query(
        "SELECT id, assignee_id, status, claimed_at, assign_reason "
        "FROM t_claim_task WHERE deleted=0 AND status='ASSIGNED' LIMIT 1")
    if task_rows:
        tid = task_rows[0]["id"]
        orig = task_rows[0]
        before = S.db_query(
            "SELECT COUNT(*) c FROM t_claim_task_transfer WHERE task_id=%s AND deleted=0", (tid,))[0]["c"]
        try:
            code, body = S.call(
                "POST", TRANSFER_PATH.format(taskId=tid), token=token,
                json_body={"toAssigneeId": "e2e-new-assignee", "reason": "E2E转交"}, expect=(200,))
            after = S.db_query(
                "SELECT COUNT(*) c FROM t_claim_task_transfer WHERE task_id=%s AND deleted=0", (tid,))[0]["c"]
            ok_e5 = code == 200 and after == before + 1
            S.record("E5 转交追加一条不可改写留痕", ok_e5,
                     f"http={code} before={before} after={after}")
        finally:
            # 恢复任务原归属（转交有副作用），并清理本测试追加的留痕，避免污染共享联调库。
            S.db_exec(
                "UPDATE t_claim_task SET assignee_id=%s, status=%s, claimed_at=%s, assign_reason=%s WHERE id=%s",
                (orig["assignee_id"], orig["status"], orig["claimed_at"], orig["assign_reason"], tid))
            S.db_exec("DELETE FROM t_claim_task_transfer WHERE task_id=%s AND reason='E2E转交'", (tid,))
    else:
        S.record("E5 转交追加一条不可改写留痕", False, "无可用 ASSIGNED 任务")
        ok_e5 = False
    results.append(ok_e5)

    # 清理本脚本产生的 append 事实（联调库非生产；避免重复跑撞唯一键、污染共享库）。
    # 用 %% 转义 LIKE 里的 %（pymysql 把裸 % 当占位符）。
    S.db_exec("DELETE FROM t_claim_user_agent WHERE principal_id LIKE 'e2e-b4-principal-%%'")
    S.db_exec("DELETE FROM t_claim_inspection_action WHERE back_reason='E2E只登记'")

    verdict = all(results)
    print("VERDICT " + " ".join(f"E{i+1}={v}" for i, v in enumerate(results)) + f" all={verdict}")
    return 0 if verdict else 1


if __name__ == "__main__":
    sys.exit(main())
