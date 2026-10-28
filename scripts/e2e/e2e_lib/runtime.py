# -*- coding: utf-8 -*-
"""业务侧（runtime）公共助手：记录 CRUD 入口 / 子表明细替换 / 状态机动作。

统一口径：

  * 全部返回 `(status, body)`，断言自己判状态码与 errorCode；
  * `act` 用主会话，`act_as` 用**指定身份头**（职责分离 SoD 场景：submit=admin、approve=审批人）；
  * 采购工作流动作（submit/approve/reject）平台强制幂等键，`act_as` 自动补唯一键，
    避免每个调用点手工传 `idem`。
"""
from . import http


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
    """用指定身份头触发状态机动作，返回 `(status, body)`。"""
    # 采购 submit/approve/reject 已接工作流引擎（逃逸动作，平台强制要求幂等键）；
    # 此处对采购这三类动作自动补唯一幂等键，避免逐个调用处手工传 idem。
    if (
        idem is None
        and str(entity_code).startswith("purchase_order")
        and action in ("submit", "approve", "reject")
    ):
        _IDEM_SEQ[0] += 1
        idem = f"po-{action}-{rid}-{_IDEM_SEQ[0]}"
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
