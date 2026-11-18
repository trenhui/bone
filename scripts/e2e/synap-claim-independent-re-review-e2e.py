#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
独立复审 E2E（批 3 · 43/46 与 44 并列 / 同人不可自审 / RE_REVIEW 零流量）
=====================================================================
覆盖端点：
  GET  /api/v1/claims/re-review/queue              独立复审队列（只出 43 状态案件 + 租户隔离）
  POST /api/v1/claims/{claimId}/re-review/submit   提交独立复审（42→43）
  PUT  /api/v1/claims/{claimId}/re-review/complete 提交复审结论（43→46 通过 / 43→45 驳回）

判定一律「接口状态 + 直查 MySQL 第二证据源」，避免「接口 200 但没落库 / 状态没变」假绿。
  R1 提交成功       POST submit → DB status=43, second_reviewer_id=登记值，state 落 name
  R2 同人不可自审   初审人提交 → 409 SC_CLAIM_REREVIEWER_CONFLICT，DB 状态不变（仍是 42）
  R3 队列只出 43     GET queue 返回行在 DB 内 status 全为 43，且 second_reviewer_id=持权人
  R4 复审结论       PUT complete(approved) → DB status=46，独立于 44/50
  R5 结论人须登记   非登记人提交结论 → 409，DB 状态不变（仍 43）
（跨租户不可见由应用服务单测钉住：queue 把当前 tenantId 传给仓储过滤，E2E 不重复造第二租户账号。）

前置：网关 8888 / claim 8087（须为含本端点的重建 fat-jar）/ iam 8081 在线；admin/123456；MySQL 可直连。
用法：python3 scripts/e2e/synap-claim-independent-re-review-e2e.py
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_sibling(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load_sibling("e2e_synap_claim")

API_QUEUE = "/claims/re-review/queue"
API_SUBMIT = "/claims/{claimId}/re-review/submit"
API_COMPLETE = "/claims/{claimId}/re-review/complete"

ERR_CONFLICT = "SC_CLAIM_REREVIEWER_CONFLICT"

# 独立复审开关（RE_REVIEW）：与 e2e_synap_claim 助手同租户（X-Tenant-Id=0）；提交前打开、结束恢复。
TENANT = 0
TOGGLE_SQL_ON = (
    "INSERT INTO t_feature_toggle (id, feature_key, state, rollout_pct, remark, tenant_id, created_at, updated_at, version, deleted) "
    "VALUES (%s, 'RE_REVIEW', 'ENABLED', 100, 'E2E 批3 独立复审', %s, NOW(3), NOW(3), 0, 0) "
    "ON DUPLICATE KEY UPDATE state='ENABLED', rollout_pct=100, updated_at=NOW(3)"
)
TOGGLE_SQL_RESTORE = (
    "UPDATE t_feature_toggle SET state='DISABLED', updated_at=NOW(3) "
    "WHERE tenant_id=%s AND feature_key='RE_REVIEW'"
)


def claim_status(cid):
    rows = S.db_query("SELECT status, second_reviewer_id FROM t_claim_case WHERE id=%s", (cid,))
    return rows[0] if rows else {}


def ensure_review_permission():
    """幂等: 确保 claim:re-review:write 存在且绑到 SUPER_ADMIN/CLAIM_REVIEWER。

    批 3 端点 @PreAuthorize 要这个码，但 live 库 seed 里漏了它（10_bootstrap_seed.sql 有
    9153006，联调库未灌）⇒ 所有人调用全 403，功能虽实现却不可达。用真实 IAM API 补种
    （不直连改库），任何环境按「缺则建、建后绑角色」自愈。已存在则不动。
    注意: 换机/清库后首个 token 的 scopes 可能已缓存旧值，补种后重新登录拿新 token。
    """
    code = "claim:re-review:write"
    tok = S.login(*S.ADMIN)
    _, listing = S.call("GET", "/iam/permissions?page=1&size=500", token=tok, expect=(200,))
    perms = (listing.get("data") or {}).get("records") or []
    perm = next((p for p in perms if p.get("code") == code), None)
    if perm is None:
        _, created = S.call("POST", "/iam/permissions", token=tok, expect=(200,), json_body={
            "code": code, "name": "理赔-独立复审写", "description": "独立复审写端点；职责分离分权",
            "resourceType": "claim", "resourcePath": "re-review", "action": "write",
            "type": "OPERATION", "sortOrder": 810})
        p_id = created.get("data")
        print(f"  · IAM 补种权限 {code} id={p_id}")
    else:
        p_id = perm.get("id")
    _, roles = S.call("GET", "/iam/roles?page=1&size=100", token=tok, expect=(200,))
    rlist = (roles.get("data") or {}).get("records") or []
    for role_code in ("SUPER_ADMIN", "CLAIM_REVIEWER"):
        role = next((r for r in rlist if r.get("code") == role_code), None)
        if not role:
            continue
        _, bound = S.call("GET", f"/iam/roles/{role['id']}/permissions", token=tok, expect=(200,))
        have = {p.get("id") for p in (bound.get("data") or [])}
        if p_id not in have:
            S.call("POST", f"/iam/roles/{role['id']}/permissions", token=tok, expect=(200,),
                   json_body={"permissionIds": [p_id, *have]})
            print(f"  · 角色 {role_code} 已授权 {code}")
    return p_id


def main():
    # 权限补种会改 scopes：必须在 main 登录前完成，再重新登录拿含新码的 token。
    ensure_review_permission()
    token = S.login(*S.ADMIN)

    results = []
    try:
        # 打开 RE_REVIEW 开关（用分布式 ID 占位，ON DUPLICATE 保证幂等）
        S.db_exec(TOGGLE_SQL_ON, (int(S.time.time() * 1000) % 10**15, TENANT))

        # 造一条 42 态案件（初审人 = admin）。
        # 不能传 S.scenario_small() 的返回值——那是「跑完整 S1 场景后的 claimId(int)」，
        # 不是场景字典；旧版把 int 当 scn 传进 run_intake_and_decision 会炸。这里自己造场景。
        scn = {
            "policyNo": "E2E-POLICY-0001", "planId": 9001,
            "insuredIdHash": "e2e-insured-hash-0001",
            "hospitalCode": "HOSP_BJ_001", "diagnosisCode": "J18.9",
            "invoiceCode": "011001900", "invoiceNo": "RR" + str(int(S.time.time()))[-6:],
            "invoiceDate": "2026-10-20", "batchNo": "BATCH-RR",
            "items": [{"itemName": "挂号费", "itemCode": "A001", "category": "SERVICE",
                       "quantity": 1, "unitPrice": 50, "amount": 50, "department": "门诊"}],
            "invoice_total": 50,
        }
        S.setup_seed()
        cid, total = S.run_intake_and_decision(token, scn)
        S.decide_approved(token, cid, total)
        before = claim_status(cid)
        assert before.get("status") == "ADJUSTED", f"前置应为 42 ADJUSTED，实际 {before}"

        reviewer = "e2e-independent-reviewer"

        # R2 同人不可自审：初审人 admin 提交，被拒且状态不变
        code, body = S.call("POST", f"/claims/{cid}/re-review/submit", token=token,
                            json_body={"reviewerId": "admin"}, expect=(200, 409))
        err = (body.get("errorCode") if isinstance(body, dict) else None)
        ok_r2 = code == 409 and err == ERR_CONFLICT and claim_status(cid).get("status") == "ADJUSTED"
        S.record("R2 同人不可自审 409 可辨认码且状态不变", ok_r2, f"http={code} errorCode={err}")
        results.append(ok_r2)

        # R1 提交独立复审：42 → 43
        code, body = S.call("POST", f"/claims/{cid}/re-review/submit", token=token,
                            json_body={"reviewerId": reviewer}, expect=(200,))
        after = claim_status(cid)
        ok_r1 = after.get("status") == "RE_REVIEW_PENDING" and after.get("second_reviewer_id") == reviewer
        S.record("R1 提交后 DB status=43 且复审人已登记", ok_r1,
                 f"http={code} status={after.get('status')} reviewer={after.get('second_reviewer_id')}")
        results.append(ok_r1)

        # R3 队列只出 43 状态案件且按持权人过滤
        code, body = S.call("GET", f"/claims/re-review/queue?reviewerId={reviewer}&page=1&size=20",
                            token=token, expect=(200,))
        rows = body.get("data", {}).get("records", []) if isinstance(body, dict) else []
        db_statuses = [claim_status(int(r["claimId"])).get("status") for r in rows if str(r.get("claimId", "")).isdigit()]
        ok_r3 = all(s == "RE_REVIEW_PENDING" for s in db_statuses) and any(
            str(r.get("claimId")) == str(cid) for r in rows)
        S.record("R3 复审队列只出 43 状态案件且含本案", ok_r3,
                 f"http={code} rows={len(rows)} dbStatuses={db_statuses}")
        results.append(ok_r3)

        # R5 非登记人提交结论被拒且状态不变（仍 43）
        code, body = S.call("PUT", f"/claims/{cid}/re-review/complete", token=token,
                            json_body={"approved": True, "reviewerId": "someone-else"}, expect=(200, 409))
        err = (body.get("errorCode") if isinstance(body, dict) else None)
        ok_r5 = code == 409 and claim_status(cid).get("status") == "RE_REVIEW_PENDING"
        S.record("R5 非登记人结论被拒且状态不变", ok_r5, f"http={code} errorCode={err}")
        results.append(ok_r5)

        # R4 独立复审通过：43 → 46（独立于 44/50）
        code, body = S.call("PUT", f"/claims/{cid}/re-review/complete", token=token,
                            json_body={"approved": True, "reviewerId": reviewer}, expect=(200,))
        ok_r4 = claim_status(cid).get("status") == "RE_REVIEW_PASSED"
        S.record("R4 复审通过后 DB status=46 独立于 44/50", ok_r4,
                 f"http={code} status={claim_status(cid).get('status')}")
        results.append(ok_r4)
    finally:
        # 恢复开关，避免污染其它用例
        try:
            S.db_exec(TOGGLE_SQL_RESTORE, (TENANT,))
        except Exception as e:
            print(f"  · 恢复 RE_REVIEW 开关失败（需人工确认）: {e}")

    verdict = all(results)
    print(f"VERDICT R1={results[0] if len(results)>0 else None} R2={results[1] if len(results)>1 else None} "
          f"R3={results[2] if len(results)>2 else None} R4={results[3] if len(results)>3 else None} "
          f"R5={results[4] if len(results)>4 else None} all={verdict}")
    return 0 if verdict else 1


if __name__ == "__main__":
    sys.exit(main())
