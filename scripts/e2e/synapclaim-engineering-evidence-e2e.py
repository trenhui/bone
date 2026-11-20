#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工程证据底座只读端点 E2E（GET /api/v1/claim-ops/engineering-evidence）。

验证的是"证据视图只读契约"，不是能力达标：
  · HTTP 200 + ApiResponse 信封；
  · 判据列表含全部稳定 ID，状态/原因码均为受控值（无自由文本结论）；
  · 口径声明明确"证据齐备 ≠ 达标 ≠ 放款许可"；
  · 两次 GET 结果逐字相同（无副作用、无隐藏状态写入）；
  · 不含 L3_CLAIMABLE / AUTO_APPROVE_ALLOWED 等门禁结论字段（证据不得翻绿门禁）。

前置：网关 8888 / claim 8087 在线，admin/123456 可登录。服务不可达时明确 SKIP（退出 0 但打印 SKIP），
不把"没跑"伪装成"通过"——本用例由 impact.py --strict 作为该 Controller 端点的真实覆盖。
"""
import sys

sys.path.insert(0, "scripts/e2e")
from e2e_lib import http  # noqa: E402

PATH = "/api/v1/claim-ops/engineering-evidence"
EXPECTED_IDS = {
    "AUTO_APPROVE_TOGGLE",
    "C2_RULE_DECLARATION",
    "E1_VISUAL_DECLARATION",
    "E2_GRAPH_DECLARATION",
    "E3_KNOWLEDGE_DECLARATION",
    "C5_LLM_PROVIDER_CONFIG",
    "LIMITED_DIMENSION_REACHABILITY",
    "FAIL_CLOSED_TRACE_REASON",
}
ALLOWED_STATUS = {"EVIDENCED", "NOT_EVIDENCED"}
FORBIDDEN_IDS = {"L3_CLAIMABLE", "AUTO_APPROVE_ALLOWED"}


def main() -> int:
    session = http.Session()
    if not session.login(fatal=False):
        print(f"SKIP: 登录不可达（{session.login_base}），工程证据 E2E 未执行（不伪装通过）")
        return 0
    try:
        code, body = session.call("GET", PATH)
    except Exception as exc:  # noqa: BLE001 - 服务不可达属明确 skip 条件
        print(f"SKIP: 端点不可达（{exc}），工程证据 E2E 未执行")
        return 0
    if code != 200:
        print(f"FAIL: {PATH} 期望 200，实际 {code}: {str(body)[:200]}")
        return 1

    data = (body or {}).get("data")
    if not isinstance(data, dict):
        print(f"FAIL: ApiResponse.data 非对象: {str(data)[:200]}")
        return 1
    criteria = data.get("criteria")
    if not isinstance(criteria, list) or not criteria:
        print("FAIL: criteria 缺失或为空")
        return 1
    ids = {c.get("id") for c in criteria if isinstance(c, dict)}
    missing = EXPECTED_IDS - ids
    if missing:
        print(f"FAIL: 稳定判据 ID 缺失: {sorted(missing)}")
        return 1
    if ids & FORBIDDEN_IDS:
        print(f"FAIL: 证据视图含门禁结论字段: {sorted(ids & FORBIDDEN_IDS)}")
        return 1
    bad_status = [c for c in criteria if c.get("status") not in ALLOWED_STATUS]
    if bad_status:
        print(f"FAIL: 受控状态值违规: {bad_status[:2]}")
        return 1
    note = data.get("note") or ""
    for needle in ("不构成放款许可", "不替代"):
        if needle not in note:
            print(f"FAIL: 口径声明缺少「{needle}」")
            return 1

    # 只读契约：第二次 GET 逐字一致（无副作用）
    code2, body2 = session.call("GET", PATH)
    if code2 != 200 or body2 != body:
        print("FAIL: 两次 GET 结果不一致（只读端点出现副作用）")
        return 1

    print(
        f"OK: 工程证据只读端点契约通过（{len(criteria)} 项判据 / {len(EXPECTED_IDS)} 稳定 ID / "
        "状态受控 / 口径声明齐备 / 无门禁结论字段 / 二次 GET 无副作用）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
