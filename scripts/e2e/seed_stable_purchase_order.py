#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""注册稳定的 purchase_order 元数据实体（克隆 e2e 实体 purchase_order_chain_* 的表头+明细 schema）。

背景
----
采购菜单(0070_seed_purchase_menu.sql)深链到 /metadata#/runtime?entity=purchase_order，但 dev 库此前只有
e2e 动态生成的 purchase_order_chain_<SUFFIX> / purchase_order_onb<XXX>，没有稳定 code 的 purchase_order 实体，
导致深链打开空白页。本脚本在元数据平台补一个**稳定 code** 的采购订单实体，使菜单深链可直达。

工作流：wf_binding(entity_code='purchase_order', def_key='purchase_order') 已由 0068 种子绑定，且
PurchaseWorkflowSubmitHandler 以固定 DEFINITION_KEY 启动流程（与实体 code 无关），故本脚本只负责建实体+
子表+动作定义+界面协议并发布，工作流即可在稳定实体上复跑。

schema 真源：直接复用 e2e 全链路脚本(two-scenario-full-chain-e2e.py)的 po_fields / po_item_fields /
po_policy / auto_number / 六个动作定义 / 界面协议样例(purchase-order-ui-schema.example.json)，保证与已验证
通过的采购建模口径完全一致。

用法
----
    BONE_E2E_BASE=http://127.0.0.1:8888/api/v1 python3 scripts/e2e/seed_stable_purchase_order.py

幂等：create_entity 编码冲突复用既有；字段/动作/界面协议均幂等；重复执行安全。
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from e2e_lib import http, modeling  # noqa: E402


PO = "purchase_order"
PO_ITEM = "purchase_order_item"

# ---- 表头字段（与 e2e 全链路脚本采购段完全一致）----
po_fields = [
    {"code": "po_no", "label": "采购单号"},
    {"code": "title", "label": "采购标题"},
    {"code": "vendor_code", "label": "供应商编码"},
    {"code": "vendor", "label": "供应商名称"},
    {"code": "buyer", "label": "采购员"},
    {"code": "approver_id", "label": "审批人（工作流 FIELD 指派，须等于审批操作人账户 id）", "type": "LONG"},
    {"code": "department", "label": "需求部门"},
    {"code": "need_date", "label": "需求日期"},
    {"code": "currency", "label": "币种"},
    {"code": "budget_subject", "label": "预算科目"},
    {"code": "budget_amount", "label": "预算金额", "type": "DECIMAL"},
    {"code": "contract_no", "label": "合同号"},
    {"code": "payment_terms", "label": "付款条款"},
    {"code": "po_amount", "label": "采购金额", "type": "DECIMAL"},
    {"code": "tax_amount", "label": "税额合计", "type": "DECIMAL"},
    {"code": "received_amount", "label": "累计收货金额", "type": "DECIMAL"},
    {"code": "invoiced_amount", "label": "累计开票金额", "type": "DECIMAL"},
    {"code": "invoice_no", "label": "发票号"},
    {"code": "remark", "label": "备注", "type": "TEXT"},
    {"code": "attachment", "label": "合同与验收附件", "type": "ATTACHMENT"},
    {"code": "status", "label": "状态"},
]

# ---- 行项目字段 ----
po_item_fields = [
    {"code": "material_code", "label": "物料编码"},
    {"code": "material_name", "label": "物料名称"},
    {"code": "spec", "label": "规格型号"},
    {"code": "qty", "label": "数量", "type": "DECIMAL"},
    {"code": "unit", "label": "单位"},
    {"code": "unit_price", "label": "单价", "type": "DECIMAL"},
    {"code": "amount", "label": "金额", "type": "DECIMAL"},
    {"code": "tax_rate", "label": "税率", "type": "DECIMAL"},
    {"code": "tax_amount", "label": "税额", "type": "DECIMAL"},
    {"code": "received_qty", "label": "已收货数量", "type": "DECIMAL"},
]

# ---- 行策略：放行进「创建人 OR 审批人」，否则审批人读不到单（2026-10-27 联调实测）----
po_policy = {
    "owner_field": "created_by",
    "rules": [{"name": "dept_or_buyer", "effect": "allow",
               "when": "created_by = :operator OR approver_id = :operator"}],
}

auto_number = {"field": "po_no", "prefix": "PO", "reset": "none"}

# ---- 动作定义 ----
ACTIONS = [
    ("submit", "提交审批", "DRAFT", "SUBMITTED", True, "purchaseWorkflowSubmitHandler", True, 1),
    ("approve", "审批通过", "SUBMITTED", "APPROVED", True, "purchaseWorkflowDecisionHandler", True, 2),
    ("reject", "驳回", "SUBMITTED", "REJECTED", True, "purchaseWorkflowDecisionHandler", True, 3),
    ("startReceive", "开始收货", "APPROVED", "RECEIVING", False, None, False, 4),
    ("confirmReceive", "确认收货", "RECEIVING", "RECEIVED", False, None, False, 5),
]
PAY_ACTION = {
    "actionCode": "pay", "label": "付款", "fromStatus": "RECEIVED", "toStatus": "PAID",
    "escape": True, "handlerBean": "purchaseOrderPayHandler", "requiresIdempotency": True,
    "sortOrder": 6,
    # paramsSchema 须为 JSON 字符串（与 e2e 全链路脚本口径一致），否则 400 MALFORMED
    "paramsSchema": json.dumps([
        {"name": "channel", "label": "付款渠道", "type": "enum", "required": True,
         "values": ["BANK", "ACCEPTANCE", "WECHAT"]},
        {"name": "payAmount", "label": "付款金额", "type": "number", "required": True, "min": 0.01},
        {"name": "note", "label": "备注", "type": "string", "required": False},
    ], ensure_ascii=False),
}


def ensure_actions(eid):
    s, d = http.call("GET", f"/metadata/entities/{eid}/action-defs")
    existing = {a.get("actionCode") for a in (d.get("data") or [])}
    for code, label, frm, to, esc, hdl, idem, order in ACTIONS:
        if code in existing:
            print(f"  [skip] 动作 {code} 已存在")
            continue
        s, d = modeling.add_action(eid, code, label, frm, to, esc, hdl, idem, order)
        print(f"  action {code}: {s} {http.ecode(d)}")
    if "pay" in existing:
        print("  [skip] 动作 pay 已存在")
    else:
        s, d = http.call("POST", f"/metadata/entities/{eid}/action-defs", PAY_ACTION)
        print(f"  action pay: {s} {http.ecode(d)}")


def put_ui_schema(eid):
    sample_path = os.path.join(http.config.SAMPLES, "purchase-order-ui-schema.example.json")
    with open(sample_path) as fh:
        raw = fh.read()
    # 样例协议里 entityCode 写成表名占位、childEntity 写死 purchase_item，替换成稳定 code
    raw = raw.replace('"t_purchase_order"', f'"{PO}"').replace('"purchase_item"', f'"{PO_ITEM}"')
    s, d = modeling.put_schema(eid, {"json": raw})
    print(f"  ui-schema PUT: {s} {http.ecode(d)}")


def main():
    http.login()
    po_id = modeling.create_entity("采购订单", PO, f"t_{PO}", po_policy, po_fields, auto_number)
    print(f"采购订单实体 id={po_id}")
    po_item_id = modeling.create_entity("采购行项目", PO_ITEM, f"t_{PO_ITEM}", None, po_item_fields)
    print(f"行项目实体 id={po_item_id}")

    modeling.child_table_field(po_id, "items", PO_ITEM, "order_id")
    modeling.child_table_field(po_item_id, "order_id", physical=True)
    modeling.child_table_field(po_item_id, "sort_order", physical=True, type="INT")
    print("子表关联已建")

    ensure_actions(po_id)
    put_ui_schema(po_id)

    s, errs, _ = modeling.validate(po_id)
    print(f"validate: {s} errors={[i.get('code') for i in errs]}")

    s, d = modeling.publish(po_item_id)
    print(f"publish 行项目: {s} {http.ecode(d)}")
    s, d = modeling.publish(po_id)
    print(f"publish 采购订单: {s} {http.ecode(d)}")

    s, d = http.call("GET", f"/metadata/entities/{po_id}")
    ent = (d.get("data") or {})
    print(f"校验实体: code={ent.get('code')} status={ent.get('status')} "
          f"fields={len(ent.get('fields') or [])}")
    print("DONE")


if __name__ == "__main__":
    main()
