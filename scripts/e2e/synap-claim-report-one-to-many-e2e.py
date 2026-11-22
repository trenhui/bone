#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""报案:赔案 1:N 双号链路 E2E：报案登记 → 分案守卫 → 赔案列表 → 关闭。

覆盖端点（impact-map.json 由 scripts/e2e/impact.py --write-map 派生，勿手改派生文件）：
  POST /api/v1/claim-reports
  POST /api/v1/claim-reports/{reportNo}/cases
  GET  /api/v1/claim-reports/{reportNo}/cases
  POST /api/v1/claim-reports/{reportNo}/close

口径：分案要求报案下已有首案（防数据不一致）。本用例刻意验证「已报案未立案」这一
业界合法中间态下分案被 fail-closed 拒绝（404），而不是误放行；首案成功分案路径
由主链路用例覆盖。这样本用例不依赖保单造数，可在干净环境独立运行。

用法：
  python3 scripts/e2e/synap-claim-report-one-to-many-e2e.py
"""
from e2e_lib import config, http
from e2e_lib.report import check, finish, note, section

SUFFIX = config.run_suffix()


def _idem(tag):
    return f"rp-1tn-{tag}-{SUFFIX}"


def main():
    section("报案:赔案 1:N 双号链路")
    note(f"BASE={config.BASE}  SUFFIX={SUFFIX}")
    http.login()

    # 1) 登记报案（FNOL）→ 取 RP 报案号
    register_body = {
        "policyNo": "POL-E2E-1TN",
        "insuredIdHash": f"hash-e2e-{SUFFIX}",
        "outInsureTime": "2026-11-20T08:00:00Z",
        "lossDescription": "1:N 链路 E2E",
        "sourceChannel": "E2E",
        "sourceSystem": "e2e",
    }
    st, body = http.call(
        "POST", "/claim-reports", register_body, {"Idempotency-Key": _idem("register")}
    )
    check("登记报案返回 201", st == 201, f"status={st} body={str(body)[:200]}")
    data = (body or {}).get("data") or {}
    report_no = data.get("reportNo")
    check(
        "报案号为 RP 前缀",
        bool(report_no and str(report_no).startswith("RP")),
        f"reportNo={report_no}",
    )
    if not report_no:
        return 0  # finish 仍按已记录的断言退出

    # 2) 无首案时分案：fail-closed 404（守卫，不是误放行）
    split_body = {
        "reportNo": report_no,
        "invoiceCode": "FP-E2E",
        "invoiceNo": "INV-E2E",
        "invoiceDate": "2026-11-21",
        "hospitalCode": "H-E2E",
        "diagnosisCode": "D-E2E",
        "items": [
            {
                "itemName": "检查费",
                "itemCode": "",
                "quantity": 1,
                "unitPrice": 100,
                "amount": 100,
                "department": "",
                "feeCode": "",
            }
        ],
    }
    st, body = http.call(
        "POST",
        f"/claim-reports/{report_no}/cases",
        split_body,
        {"Idempotency-Key": _idem("split")},
    )
    check("无首案时分案被守卫拒绝(404)", st == 404, f"status={st} body={str(body)[:200]}")

    # 3) 报案下赔案列表：可读（当前为空，与守卫一致）
    st, body = http.call("GET", f"/claim-reports/{report_no}/cases")
    check("报案下赔案列表可读", st == 200, f"status={st} body={str(body)[:200]}")

    # 4) 关闭报案 → data=true；关闭后不再接受新赔案
    st, body = http.call("POST", f"/claim-reports/{report_no}/close")
    check("关闭报案成功", st == 200, f"status={st} body={str(body)[:200]}")

    # 负向对照：不存在的报案号不可被枚举
    st, _ = http.call("GET", "/claim-reports/RP-NOT-EXIST/cases")
    check("不存在的报案号返回 404", st == 404, f"status={st}")


if __name__ == "__main__":
    main()
    finish("报案:赔案 1:N 双号链路 汇总")
