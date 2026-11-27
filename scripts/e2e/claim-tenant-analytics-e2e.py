#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""租户分析端点 GET /claims/analytics/by-tenant 的只读语义与服务端强制隔离回归。

对应实现：ClaimAnalyticsController#byTenant / ClaimAnalyticsApplicationService#summaryByTenant。
覆盖要点：
  1. 正向：信封成功、tenantId 由服务端下发、时间窗回显、scopeNote 必带；
  2. 越权负向：前端传 tenantId 不得改变结果归属（服务端 TenantPort 为唯一身份源）；
  3. 参数负向：缺参、倒置窗口、超 31 天窗口均被拒；
  4. 身份负向：匿名被拒；
  5. 口径对照：当前租户已收敛数不得超过全量口径。

用法：
  python3 scripts/e2e/claim-tenant-analytics-e2e.py
  BONE_E2E_JUNIT_DIR=/tmp/e2e-junit python3 scripts/e2e/claim-tenant-analytics-e2e.py

纪律：地址/凭据只从 e2e_lib.config 取；本端点是纯只读查询，不造数、无残留；
断言只走 e2e_lib.report，失败详情写实测值；退出码是唯一结果出口。
注意 config.BASE 已含 /api/v1，路径写 /claims/... 而非 /api/v1/claims/...。
"""
from datetime import datetime, timedelta, timezone

from e2e_lib import config, http
from e2e_lib.report import check, finish, note, section

PATH = "/claims/analytics/by-tenant"
FULL_PATH = "/claims/analytics/summary"
MAX_WINDOW_DAYS = 30


def _window(days=MAX_WINDOW_DAYS):
    """ISO-8601 时间窗（Z 结尾，Instant 可直接解析）；默认贴近 31 天上限。"""
    now = datetime.now(timezone.utc)
    return (
        (now - timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ"),
        now.strftime("%Y-%m-%dT%H:%M:%SZ"),
    )


def _payload(body):
    """只认 success=true 且 code=200 的成功信封，返回 data；否则 None。"""
    if (
        isinstance(body, dict)
        and body.get("success") is True
        and body.get("code") == 200
    ):
        return body.get("data")
    return None


def main():
    section("租户分析端点只读语义与服务端强制隔离")
    note(f"BASE={config.BASE}  USER={config.USER}  —— 纯只读端点，不造数、无残留")
    http.login()  # 失败直接退出；没登录跑什么都是假红
    frm, to = _window()
    url = f"{PATH}?from={frm}&to={to}"

    st, body = http.call("GET", url)
    data = _payload(body)
    check(
        "by-tenant 200 且信封成功",
        st == 200 and data is not None,
        f"status={st} body={str(body)[:240]}",
    )
    if data is None:
        finish("租户分析端点回归 汇总")
        return 1

    # ---- 正向契约 ----
    tid = data.get("tenantId")
    check(
        "tenantId 由服务端下发且为非负整数",
        isinstance(tid, int) and not isinstance(tid, bool) and tid >= 0,
        f"tenantId={tid!r}",
    )
    check(
        "时间窗原样回显",
        str(data.get("windowFrom", "")).startswith(frm[:19])
        and str(data.get("windowTo", "")).startswith(to[:19]),
        f"windowFrom={data.get('windowFrom')!r} windowTo={data.get('windowTo')!r} "
        f"expected={frm!r}/{to!r}",
    )
    groups = data.get("byDecisionResult")
    converged = data.get("converged")
    check(
        "converged 非负且分组结果是对象",
        isinstance(converged, int)
        and converged >= 0
        and isinstance(groups, dict),
        f"converged={converged!r} byDecisionResult={str(groups)[:160]}",
    )
    check(
        "FALLBACK 计数非负",
        isinstance(data.get("fallbackCount"), int) and data["fallbackCount"] >= 0,
        f"fallbackCount={data.get('fallbackCount')!r}",
    )
    rate = data.get("shadowAutomationRate")
    check(
        "影子自动化率落在 [0,1]",
        isinstance(rate, (int, float)) and 0 <= rate <= 1,
        f"shadowAutomationRate={rate!r}",
    )
    note_text = data.get("scopeNote")
    check(
        "scopeNote 必带且声明口径边界",
        isinstance(note_text, str)
        and len(note_text) > 0
        and "不等于实际出金率" in note_text,
        f"scopeNote={str(note_text)[:160]!r}",
    )

    # ---- 越权负向：前端 tenantId 不得改变归属 ----
    st2, body2 = http.call("GET", url + "&tenantId=999999999")
    data2 = _payload(body2)
    check(
        "前端传 tenantId 无法越权改变归属",
        st2 == 200
        and data2 is not None
        and data2.get("tenantId") == tid,
        f"status={st2} returnedTenantId="
        f"{(data2 or {}).get('tenantId')!r} expected={tid!r}",
    )

    # ---- 参数负向 ----
    st3, body3 = http.call("GET", f"{PATH}?from={frm}")
    check("缺 to 参数被拒", st3 != 200 and st3 < 500, f"status={st3} body={str(body3)[:120]}")

    st4, body4 = http.call("GET", f"{PATH}?from={to}&to={frm}")
    check(
        "from 晚于 to 被拒",
        st4 != 200 and st4 < 500,
        f"status={st4} body={str(body4)[:120]}",
    )

    too_wide_from, _ = _window(days=MAX_WINDOW_DAYS + 5)
    st5, body5 = http.call("GET", f"{PATH}?from={too_wide_from}&to={to}")
    check(
        "超过 31 天窗口被拒",
        st5 != 200 and st5 < 500,
        f"status={st5} body={str(body5)[:120]}",
    )

    # ---- 身份负向 ----
    st6, _ = http.call_as(http.session_headers(""), "GET", url)
    check("匿名访问被拒", st6 in (401, 403), f"status={st6}")

    # ---- 口径对照：租户是全集的子集 ----
    st7, body7 = http.call("GET", f"{FULL_PATH}?from={frm}&to={to}")
    full = _payload(body7)
    full_converged = (full or {}).get("converged")
    check(
        "当前租户已收敛数不超过全量口径",
        st7 == 200
        and full is not None
        and isinstance(full_converged, int)
        and int(converged) <= full_converged,
        f"tenantConverged={converged!r} fullConverged={full_converged!r} status={st7}",
    )
    return 0


if __name__ == "__main__":
    main()
    finish("租户分析端点回归 汇总")  # 内部按断言结果 sys.exit(0/1)
