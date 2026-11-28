#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MRM 模型风险治理只读端点 E2E（impact --strict 的真实覆盖）。

覆盖端点（config.BASE 已含 /api/v1，路径写相对形式）：
  GET /claim-ops/mrm/governance
  GET /claim-ops/mrm/bias-monitoring

判定口径（每条都带反证，不只看 200）：
  C1 信封契约：success=true + data 为对象；空清单状态 NO_MODEL_CARDS（不得被读成治理健康）；
     psi 为 null 时漂移必须 NO_DATA；无样本时 champion-challenger=NO_EVIDENCE 且 conclusion=null。
  C2 偏差监测：真实维度分组，每组 sampleCount 可见；status=NO_EVIDENCE 时切片为空；
     任一低样本切片的 agreeCount / agreementRate / conclusion 必须为 null（fail-closed）。
  C3 维度边界：机构等级无真实字段 → DIMENSION_UNAVAILABLE + 说明；minSamplesPerSlice 下调被钳到 30、超大封顶 500。
  C4 只读契约：同参数两次 GET 逐字一致；不写任何数据。
  C5 鉴权负向：匿名 401；有凭证但无 claim:claims:read 的受限账号 403（不得退化成 500）。

前置：网关 8888 / claim 8087 / iam 8081 在线，admin/123456 可登录。服务不可达时明确 SKIP（退出 0 但打印 SKIP），
不把「没跑」伪装成「通过」。本脚本不做任何业务造数；受限账号仅为鉴权负向探针，用运行后缀命名以便残留回收。
用法：python3 scripts/e2e/claim-mrm-governance-e2e.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from e2e_lib import config, http  # noqa: E402
from e2e_lib.report import check, finish, note, section  # noqa: E402

# 端点模板集中在此：impact.py 按这些字面量判覆盖，改路径只改一处。
PATH_GOVERNANCE = "/claim-ops/mrm/governance"
PATH_BIAS = "/claim-ops/mrm/bias-monitoring"

SUFFIX = config.run_suffix()
DRIFT_STATES = {"NO_DATA", "WITHIN_TOLERANCE", "WATCH", "BREACH"}


def _data(body):
    return body.get("data") if isinstance(body, dict) and isinstance(body.get("data"), dict) else {}


def _limited_session():
    """有凭证、无 claim:claims:read 的受限账号；失败返回 None 由调用方记 SKIP，不伪装通过。"""
    try:
        st, body = http.call("GET", "/iam/depts/tree")
        depts = body.get("data") if isinstance(body, dict) else None
        dept_id = None
        if st == 200 and isinstance(depts, list) and depts:
            dept_id = depts[0].get("id")
        if dept_id is None:
            note("取不到部门 ID，越权探针跳过（不伪装通过）")
            return None
        user = "mrm" + SUFFIX[-6:]
        st, _ = http.call(
            "POST",
            "/iam/accounts",
            {
                "username": user,
                "password": "Mrm@Readonly2026",
                "deptId": str(dept_id),
                "nickname": "MRM 无权限探针",
                "email": user + "@bone.local",
            },
        )
        if st not in (200, 201):
            note(f"受限账号创建失败 status={st}，越权探针跳过（不伪装通过）")
            return None
        token = http.Session(user, "Mrm@Readonly2026").login(fatal=False)
        return token
    except Exception as exc:  # noqa: BLE001 - 鉴权探针不可达不毁掉主契约断言
        note(f"受限账号探针异常 {type(exc).__name__}: {exc}，跳过（不伪装通过）")
        return None


def main():
    section("MRM 治理只读端点 E2E")
    session = http.Session()
    if not session.login(fatal=False):
        print(f"SKIP: 登录不可达（{session.login_base}），MRM E2E 未执行（不伪装通过）")
        return 0
    http.set_default(session)
    note(f"BASE={config.BASE}  USER={config.USER}  SUFFIX={SUFFIX}")

    try:
        st, body = http.call("GET", PATH_GOVERNANCE)
    except Exception as exc:  # noqa: BLE001
        print(f"SKIP: 端点不可达（{exc}），MRM E2E 未执行（不伪装通过）")
        return 0
    if st != 200:
        check("GET 治理状态 200", False, f"status={st} body={str(body)[:200]}")
        return 0

    data = _data(body)
    check(
        "ApiResponse 信封 success=true",
        body.get("success") is True and bool(data),
        f"success={body.get('success')!r} data={str(data)[:200]}",
    )
    check(
        "空模型卡清单显式 NO_MODEL_CARDS（不误读为治理健康）",
        data.get("modelCardStatus") == "NO_MODEL_CARDS" and data.get("status") == "NO_MODEL_CARDS",
        f"status={data.get('status')!r} modelCardStatus={data.get('modelCardStatus')!r}",
    )
    note(f"inventory.clean={data.get('inventory', {}).get('clean') if isinstance(data.get('inventory'), dict) else None}（内核计数，不作为健康结论）")

    psi = data.get("psi")
    if psi is None:
        check("无 PSI 观测必须 NO_DATA", data.get("driftVerdict") == "NO_DATA",
              f"psi={psi!r} driftVerdict={data.get('driftVerdict')!r}")
    else:
        check("有 PSI 观测时漂移四态受控", data.get("driftVerdict") in DRIFT_STATES,
              f"psi={psi!r} driftVerdict={data.get('driftVerdict')!r}")

    cc = data.get("championChallenger") or {}
    check(
        "无样本时 champion-challenger=NO_EVIDENCE 且不给优劣结论",
        cc.get("status") == "NO_EVIDENCE" and cc.get("conclusion") is None,
        f"status={cc.get('status')!r} conclusion={cc.get('conclusion')!r} sampleCount={cc.get('sampleCount')!r}",
    )

    # 只读契约：同参数两次 GET 的 data 逐字一致（信封 timestamp 每次请求都会刷新，不纳入比对）
    st2, body2 = http.call("GET", PATH_GOVERNANCE)
    same_data = st2 == 200 and isinstance(body2, dict) and body2.get("data") == data
    check("治理查询只读：两次 GET 的 data 逐字一致", same_data,
          f"status={st2} same={same_data}")

    # C2/C3：偏差监测 + 样本不足 fail-closed + 维度边界 + 下限保护
    st3, body3 = http.call("GET", PATH_BIAS + "?dimension=CHANNEL")
    data3 = _data(body3)
    check("GET 偏差监测 200", st3 == 200, f"status={st3} body={str(body3)[:200]}")
    if st3 == 200:
        status3 = data3.get("status")
        slices3 = data3.get("slices") or []
        if status3 == "NO_EVIDENCE":
            check("无盲标样本：NO_EVIDENCE 且切片为空", slices3 == [],
                  f"status={status3!r} slices={str(slices3)[:160]}")
        elif status3 in ("INSUFFICIENT_SAMPLES", "OBSERVABLE"):
            check("逐组样本量可见", all(isinstance(s.get("sampleCount"), int) for s in slices3),
                  f"slices={str(slices3)[:200]}")
        else:
            check("偏差监测状态受控", False, f"status={status3!r}")
        thin = [s for s in slices3 if s.get("verdict") == "INSUFFICIENT_SAMPLES"]
        if thin:
            check(
                "低样本切片不给偏差数值/结论",
                all(
                    s.get("agreeCount") is None
                    and s.get("agreementRate") is None
                    and s.get("conclusion") is None
                    for s in thin
                ),
                f"thin={str(thin[:2])[:200]}",
            )
        else:
            note("活库无低样本切片，样本不足数值断言跳过（不伪造样本）")
        check("缺省 minSamplesPerSlice=30", data3.get("effectiveMinSamplesPerSlice") == 30,
              f"effective={data3.get('effectiveMinSamplesPerSlice')!r}")

    st4, body4 = http.call("GET", PATH_BIAS + "?dimension=INSTITUTION_LEVEL")
    data4 = _data(body4)
    check(
        "机构等级维度不可用（需样本携带字段，不臆造）",
        st4 == 200 and data4.get("status") == "DIMENSION_UNAVAILABLE" and "机构等级" in str(data4.get("note")),
        f"status={st4} data.status={data4.get('status')!r} note={str(data4.get('note'))[:120]}",
    )
    st5, body5 = http.call("GET", PATH_BIAS + "?minSamplesPerSlice=1")
    check("下调到 1 被钳制到治理下限 30",
          st5 == 200 and _data(body5).get("effectiveMinSamplesPerSlice") == 30,
          f"status={st5} effective={_data(body5).get('effectiveMinSamplesPerSlice')!r}")
    st6, body6 = http.call("GET", PATH_BIAS + "?minSamplesPerSlice=99999")
    check("超大阈值被封顶 500",
          st6 == 200 and _data(body6).get("effectiveMinSamplesPerSlice") == 500,
          f"status={st6} effective={_data(body6).get('effectiveMinSamplesPerSlice')!r}")

    # C5：鉴权负向。匿名必须 401；受限账号必须 403（不得是 500 或被 admin token 掩盖）。
    st_anon, body_anon = http.call_as(http.session_headers(""), "GET", PATH_GOVERNANCE)
    check("匿名访问被拒 401", st_anon == 401, f"status={st_anon} body={str(body_anon)[:120]}")
    limited = _limited_session()
    if limited is None:
        note("越权探针未建立，受限用户 403 断言跳过（不伪装通过）")
    else:
        st_limited, body_limited = http.call_as(
            http.session_headers(limited), "GET", PATH_GOVERNANCE
        )
        check("无 claim:claims:read 的受限账号 403（不得是 500）", st_limited == 403,
              f"status={st_limited} body={str(body_limited)[:160]}")
    return 0


if __name__ == "__main__":
    main()
    finish("MRM 治理 E2E 汇总")
