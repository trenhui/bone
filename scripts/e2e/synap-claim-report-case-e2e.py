#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
报案 1:0..N 赔案 E2E（报案详情 + 追加赔案 + 幂等重放 + 反例）
================================================================
覆盖端点（挂网关 /api/v1/claims 下）：
  GET  /claims/reports/{reportNo}          报案详情（报案头 + 名下赔案）
  POST /claims/reports/{reportNo}/cases    在同一报案下追加赔案

判据「接口状态 + MySQL 第二证据源」：
  E1 建案响应带回报案号（RP 前缀）且与赔案号（CL 前缀）互不相等
  E2 报案详情返回该事故的事故事实（保单/计划/出险时间）与 1 笔赔案
  E3 追加成功 201，信封 code 与 HTTP 一致，赔案号新生成、报案号不变
  E4 同 Idempotency-Key 重放返回同一笔赔案，且 t_claim_case 行数不变
  E5 请求体携带保单维度字段 ⇒ 400 SC_CLAIM_REPORT_CONTEXT_MISMATCH（不静默忽略）
  E6 不存在的报案号 ⇒ 404 SC_CLAIM_REPORT_NOT_FOUND（不用通用 NOT_FOUND）
  E7 第二证据源：该报案下每笔赔案的 report_id 均指向它，且全库无「无主赔案」
  E8 uk_tenant_invoice 仍为 (tenant_id, invoice_code, invoice_no, deleted)：
     发票唯一性是租户级事实，不随报案拆分放宽（否则同票可跨赔案重复赔付）

为什么单独成篇：这两条链路是「一次事故多次赔付」的入口，1:1 时代不存在；
内存替身测试里 report_id 恒有值、行数恒不重复，只有真库真 HTTP 才能暴露
「INSERT 列与活库不对齐」「重放又建了一笔」「唯一键被顺手加上 report_id」。

用法：python3 scripts/e2e/synap-claim-report-case-e2e.py
前置：网关 8888 / claim / iam 8081 在线且已部署含报案聚合的版本；
      基线数据 E2E-POLICY-0001 / plan 9001 / insured e2e-insured-hash-0001 已存在。
"""
import importlib.util
import os
import sys
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_sibling(name):
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load_sibling("e2e_synap_claim")

SUBMIT_PATH = "/claims"
DETAIL_PATH = "/claims/reports/{report_no}"
APPEND_PATH = "/claims/reports/{report_no}/cases"
MISSING_REPORT_NO = "RP20260101ZZZZZZZZ9999"
INVOICE_KEY_EXPECTED = ["tenant_id", "invoice_code", "invoice_no", "deleted"]


def _submit_body():
    return {
        "policyNo": "E2E-POLICY-0001",
        "planId": 9001,
        "insuredIdHash": "e2e-insured-hash-0001",
        "outInsureTime": "2026-11-01T00:00:00Z",
        "claimType": "OUTPATIENT",
        "items": [{"itemName": "诊查费", "amount": 100.00}],
    }


def _append_body(tag):
    # 发票代码/号码必须唯一：uk_tenant_invoice 是租户级唯一键，重复即被拒绝（这正是 E8 守的性质）
    return {
        "invoiceCode": "E2E" + tag[:8],
        "invoiceNo": tag[:16],
        "items": [{"itemName": "治疗费", "amount": 200.00}],
    }


def _detail(report_no, token, expect=(200,)):
    return S.call("GET", DETAIL_PATH.format(report_no=report_no), token=token, expect=expect)


def _append(report_no, body, token, key, expect=(201,)):
    return S.call(
        "POST",
        APPEND_PATH.format(report_no=report_no),
        token=token,
        json_body=body,
        extra={"Idempotency-Key": key},
        expect=expect,
    )


def main():
    token = S.login(*S.ADMIN)
    results = []
    tag = uuid.uuid4().hex

    # ---- E1 建案回报案号 ----
    _, submitted = S.call(
        "POST", SUBMIT_PATH, token=token, json_body=_submit_body(),
        extra={"Idempotency-Key": tag}, expect=(201,),
    )
    claim = submitted.get("data") or {}
    claim_no = claim.get("claimNo") or ""
    report_no = claim.get("reportNo") or ""
    ok_e1 = bool(report_no) and report_no.startswith("RP") and claim_no.startswith("CL") \
        and report_no != claim_no
    S.record("E1 建案带回报案号，且与赔案号互不相等", ok_e1,
             f"claimNo={claim_no} reportNo={report_no}")
    results.append(ok_e1)
    if not ok_e1:
        # 报案号缺失会导致后续步骤全部无法执行：直接判红收场，不制造一串连带假红
        print("VERDICT E1=False 后续依赖报案号，终止")
        return 1

    # ---- E2 报案详情 ----
    _, detail_body = _detail(report_no, token)
    detail = detail_body.get("data") or {}
    cases = detail.get("cases") or []
    ok_e2 = (
        detail.get("reportNo") == report_no
        and detail.get("status") == "REPORTED"
        and detail.get("caseCount") == 1
        and detail.get("allCasesFinished") is False
        and detail.get("policyNo") == "E2E-POLICY-0001"
        and detail.get("planId") == 9001
        and len(cases) == 1
        and cases[0].get("claimNo") == claim_no
    )
    S.record("E2 报案详情含事故事实 + 1 笔赔案（allCasesFinished=false）", ok_e2,
             f"http=200 status={detail.get('status')} caseCount={detail.get('caseCount')}")
    results.append(ok_e2)

    # ---- E3 追加赔案 ----
    body = _append_body(tag)
    append_key = uuid.uuid4().hex
    _, appended_body = _append(report_no, body, token, append_key)
    appended = appended_body.get("data") or {}
    second_no = appended.get("claimNo") or ""
    ok_e3 = (
        appended_body.get("code") == 201
        and second_no.startswith("CL")
        and second_no != claim_no
        and appended.get("reportNo") == report_no
    )
    S.record("E3 追加成功 201 且信封 code 一致、报案号不变", ok_e3,
             f"code={appended_body.get('code')} claimNo={second_no}")
    results.append(ok_e3)

    # ---- E4 幂等重放 ----
    before_rows = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_case WHERE report_id="
        "(SELECT id FROM t_claim_report WHERE report_no=%s)", (report_no,))[0]["c"]
    _, replay_body = _append(report_no, body, token, append_key)
    replayed = replay_body.get("data") or {}
    after_rows = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_case WHERE report_id="
        "(SELECT id FROM t_claim_report WHERE report_no=%s)", (report_no,))[0]["c"]
    ok_e4 = replayed.get("claimNo") == second_no and before_rows == after_rows == 2
    S.record("E4 同幂等键重放返回同一笔赔案，行数不变", ok_e4,
             f"replayed={replayed.get('claimNo')} rows {before_rows}->{after_rows}")
    results.append(ok_e4)

    # ---- E5 400：携带保单维度字段 ----
    _, rejected = _append(
        report_no, {"policyNo": "E2E-POLICY-0001",
                    "items": [{"itemName": "诊查费", "amount": 1.00}]},
        token, uuid.uuid4().hex, expect=(400,),
    )
    err = (rejected.get("data") or {}).get("errorCode")
    ok_e5 = err == "SC_CLAIM_REPORT_CONTEXT_MISMATCH"
    S.record("E5 携带保单维度字段被点名拒绝（400）", ok_e5, f"errorCode={err}")
    results.append(ok_e5)

    # ---- E6 404：不存在的报案 ----
    _, missing = _detail(MISSING_REPORT_NO, token, expect=(404,))
    err = (missing.get("data") or {}).get("errorCode")
    ok_e6 = err == "SC_CLAIM_REPORT_NOT_FOUND"
    S.record("E6 不存在报案走报案专属 404（防枚举，不泄露存在性）", ok_e6, f"errorCode={err}")
    results.append(ok_e6)

    # ---- E7 第二证据源：归属完整 ----
    orphans = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_case WHERE report_id IS NULL AND deleted=0")[0]["c"]
    attached = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_case c JOIN t_claim_report r ON r.id=c.report_id "
        "WHERE r.report_no=%s AND r.deleted=0", (report_no,))[0]["c"]
    ok_e7 = orphans == 0 and attached == 2
    S.record("E7 无主赔案为 0，且该报案下确有 2 笔赔案落库", ok_e7,
             f"orphans={orphans} attached={attached}")
    results.append(ok_e7)

    # ---- E8 发票唯一键未被报案维度稀释 ----
    rows = S.db_query(
        "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) cols "
        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
        "AND table_name='t_claim_case' AND index_name='uk_tenant_invoice' "
        "AND non_unique=0")
    cols = (rows[0]["cols"] or "").split(",") if rows else []
    ok_e8 = cols == INVOICE_KEY_EXPECTED
    S.record("E8 uk_tenant_invoice 仍为租户级（不含 report_id）", ok_e8, f"cols={cols}")
    results.append(ok_e8)

    verdict = all(results)
    print("VERDICT " + " ".join(f"E{i+1}={v}" for i, v in enumerate(results)) + f" all={verdict}")
    return 0 if verdict else 1


if __name__ == "__main__":
    sys.exit(main())
