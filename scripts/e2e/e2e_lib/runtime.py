# -*- coding: utf-8 -*-
"""业务侧（runtime）公共助手：记录 CRUD 入口 / 子表明细替换 / 状态机动作。

统一口径：

  * 全部返回 `(status, body)`，断言自己判状态码与 errorCode；
  * `act` 用主会话，`act_as` 用**指定身份头**（职责分离 SoD 场景：submit=admin、approve=审批人）；
  * `act_as` 未显式传 `idem` 时**一律**自动补唯一幂等键——逃逸动作（工作流 submit/approve/reject/
    withdraw/confirm 等）平台强制 `requires_idempotency=1`，缺键直接 400；非幂等动作的键
    在执行日志里落 NULL，不会占用幂等槽，补键无副作用；
  * 负向测试要断言「缺幂等键 → 400」时显式传 `idem=NO_IDEM`（哨兵，表示本次故意不带键）。
"""
from . import http

# 哨兵：显式声明「本次调用不带 Idempotency-Key」（负向测试专用）。
# 与默认值 None（自动补唯一键）区分开，避免负向断言被自动补键意外救活。
NO_IDEM = ""


def rec_create(entity_code, body):
    """新建记录，返回 `(status, body)`；`body["data"].id` 为新记录 id。"""
    return http.call("POST", f"/runtime/entities/{entity_code}/records", body)


def child_replace(entity_code, rid, child_code, rows):
    """整体替换子表明细（软删旧行 + 插入新行），返回 `(status, body)`。"""
    return http.call(
        "POST",
        f"/runtime/entities/{entity_code}/records/{rid}/children/{child_code}",
        rows,
    )


_IDEM_SEQ = [0]


def act(entity_code, rid, action, params=None, idem=None):
    """主会话触发状态机动作，返回 `(status, body)`。"""
    return act_as(http.default().headers(), entity_code, rid, action, params, idem)


def act_as(hdr, entity_code, rid, action, params=None, idem=None):
    """用指定身份头触发状态机动作，返回 `(status, body)`。

    `idem=None` 自动补唯一键；`idem=NO_IDEM`（空串）表示故意不带键（负向断言）。
    """
    # 逃逸动作（已接工作流引擎的 submit/approve/reject/withdraw 等）平台强制幂等键，
    # 缺键直接 400。此处统一补唯一键，避免逐个调用点手工传 idem；
    # 非幂等动作的键在执行日志落 NULL，不占幂等槽，故无条件补是安全的。
    if idem is None:
        _IDEM_SEQ[0] += 1
        idem = f"e2e-{entity_code}-{action}-{rid}-{_IDEM_SEQ[0]}"
    return http.call_as(
        hdr,
        "POST",
        f"/runtime/entities/{entity_code}/records/{rid}/actions/{action}",
        {"params": params or {}},
        {"Idempotency-Key": idem} if idem else None,
    )


def rec_status(d):
    """从动作/记录响应中取状态：`d.data.record.status`。"""
    return ((d.get("data") or {}).get("record") or {}).get("status")
