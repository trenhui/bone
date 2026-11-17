#!/usr/bin/env python3
"""seed-commerce-fixtures.py —— 幂等构建联调缺失的「商品/客户/客户等级」主数据夹具。

背景：本机 dev 库从未 seed 交易主数据（MD_PRODUCT / CUSTOMER / CUSTOMER_LEVEL 三个
entityCode 全部缺），导致经网关的真实联调出现「环境造数缺口、非代码缺陷」：
  - probe-commerce「发起支付」硬编码商品 code=1001 → 下单被 BP_ORDER_PRODUCT_NOT_PUBLISHED:1001 拒；
  - chains.py C1「金额含客户等级折扣」→ 客户 1001 未建档/未配等级 ⇒ 走标准价 199（无折扣）；
  - blueprint MasterDataAclAdapter 按 entityCode 点查三处全部落空。

夹具口径（与 bone-blueprint MasterDataConsumptionProperties + ACL 字段严格对齐）：
  MD_PRODUCT      : code=1001, data={code,name,price,unit}
  CUSTOMER        : customer_code=1001, data={customer_code,level_code=VIP}
  CUSTOMER_LEVEL  : level_code=VIP, data={level_code,discount_rate=0.88}
  发布态 PUBLISHED + 当前版本 + 生效窗口由主数据服务收敛（by-code 只回发布记录）。

本脚本只动 tenant 0 的种子行、全程幂等（已存在即校验/推进到 PUBLISHED，不覆盖用户数据），
走真实 API（与前端同网关 /api/v1），不直连 DB，不改生产路径代码。
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from e2e_lib import http, config

# 实体定义：entityCode → (显示名, category, 字段[(code,type,required)], 记录(recordCode,data))
FIXTURES = {
    "MD_PRODUCT": {
        "name": "联调商品主数据(MD_PRODUCT)", "category": "PRODUCT",
        "fields": [("code", "STRING", True), ("name", "STRING", True),
                   ("price", "NUMBER", True), ("unit", "STRING", False)],
        "record": ("1001", {"code": "1001", "name": "无线鼠标", "price": 99.0, "unit": "件"}),
    },
    "CUSTOMER": {
        "name": "联调客户主数据(CUSTOMER)", "category": "CUSTOMER",
        "fields": [("customer_code", "STRING", True), ("level_code", "STRING", True),
                   ("customer_name", "STRING", False)],
        "record": ("1001", {"customer_code": "1001", "level_code": "VIP", "customer_name": "联调VIP客户"}),
    },
    "CUSTOMER_LEVEL": {
        # 名称刻意不含「客户/CUSTOMER」字样：chains.py 的 md_entity_id 用模糊关键字
        # "客户" 解析 C2 客户实体，若本实体名含该词会被误匹配并撞 discount_rate 必填校验。
        # blueprint 仅按 entityCode=CUSTOMER_LEVEL 精确点查，与显示名无关。
        "name": "等级折扣字典", "category": "REFERENCE",
        "fields": [("level_code", "STRING", True), ("discount_rate", "NUMBER", True)],
        "record": ("VIP", {"level_code": "VIP", "discount_rate": 0.88}),
    },
}


def call(method, path, body=None):
    st, j = http.call(method, path, body)
    return st, j or {}


def data(j):
    d = j.get("data")
    return d if isinstance(d, (dict, list)) else None


def rid_of(j):
    d = (j or {}).get("data")
    if isinstance(d, dict):
        return d.get("id")
    return d if isinstance(d, (int, str)) else None


def ensure_entity(ecode, spec, app_hdr):
    """实体 + 字段 + 记录，推进到 PUBLISHED。返回 (eid, ok)。"""
    st, j = call("GET", "/masterdata/entities?page=1&size=100")
    ents = (data(j) or {}).get("records", []) if isinstance(data(j), dict) else []
    ent = next((e for e in ents if e.get("entityCode") == ecode), None)
    if not ent:
        st, j = call("POST", "/masterdata/entities",
                     {"name": spec["name"], "description": "E2E 联调夹具（幂等）",
                      "entityCode": ecode, "category": spec["category"]})
        eid = rid_of(j)
        print(f"  create entity {ecode}: http={st} id={eid}")
        if not eid:
            print("    !! 创建实体失败:", str(j)[:160]); return None, False
    else:
        eid = ent.get("id")
        print(f"  reuse entity {ecode}: id={eid} status={ent.get('status')}")
    if not ent or ent.get("status") != "PUBLISHED":
        st, _ = call("POST", f"/masterdata/entities/{eid}/publish")
        print(f"    publish entity: http={st}")

    # 字段
    st, j = call("GET", f"/masterdata/entities/{eid}/fields")
    have = {(f.get("code") or f.get("fieldCode")) for f in (data(j) or []) if isinstance(f, dict)}
    for code, ftype, required in spec["fields"]:
        if code in have:
            continue
        st, _ = call("POST", f"/masterdata/entities/{eid}/fields",
                     {"name": code, "code": code, "type": ftype, "required": required})
        print(f"    field {code}: http={st}")

    # 记录：by-code 只回 PUBLISHED；未发布时用列表兜底，保证幂等
    rcode, rdata = spec["record"]
    st, j = call("GET", f"/masterdata/records/by-code?masterDataEntityId={eid}&code={rcode}")
    rec = data(j)
    if not (isinstance(rec, dict) and rec.get("id")):
        st, j = call("GET", f"/masterdata/records?masterDataEntityId={eid}&page=1&size=100")
        rows = (data(j) or {}).get("records", []) if isinstance(data(j), dict) else []
        rec = next((r for r in rows if str(r.get("recordCode")) == rcode), None)
    if isinstance(rec, dict) and rec.get("id"):
        rid = rec["id"]
        print(f"    reuse record {rcode}: id={rid} status={rec.get('status')}")
        if rec.get("status") not in ("PUBLISHED", "APPROVED"):
            if rec.get("status") == "DRAFT":
                st, _ = call("POST", f"/masterdata/records/{rid}/submit")
                print(f"      submit: http={st}")
            if app_hdr:
                st, jj = http.call_as(app_hdr, "POST", f"/masterdata/records/{rid}/approve")
                print(f"      approve(e2eapp): http={st}" + ("" if st == 200 else f" {str(jj)[:100]}"))
            st, jj = call("POST", f"/masterdata/records/{rid}/publish")
            print(f"      publish: http={st}" + ("" if st == 200 else f" {str(jj)[:100]}"))
    else:
        st, j = call("POST", f"/masterdata/records/entity/{eid}",
                     {"recordCode": rcode, "data": rdata})
        rid = rid_of(j)
        print(f"    create record {rcode}: http={st} id={rid}")
        if not rid:
            print("      !! 创建记录失败:", str(j)[:200]); return eid, False
        st, _ = call("POST", f"/masterdata/records/{rid}/submit")
        print(f"      submit: http={st}")
        if st == 200 and app_hdr:
            st, jj = http.call_as(app_hdr, "POST", f"/masterdata/records/{rid}/approve")
            print(f"      approve(e2eapp): http={st}" + ("" if st == 200 else f" {str(jj)[:100]}"))
        st, jj = call("POST", f"/masterdata/records/{rid}/publish")
        print(f"      publish: http={st}" + ("" if st == 200 else f" {str(jj)[:100]}"))

    # 终态断言：by-code 必须命中 PUBLISHED
    st, j = call("GET", f"/masterdata/records/by-code?masterDataEntityId={eid}&code={rcode}")
    rec = data(j)
    ok = st == 200 and isinstance(rec, dict) and rec.get("status") == "PUBLISHED"
    print(f"    VERIFY {ecode}/{rcode}: http={st} -> {'PASS' if ok else 'FAIL'}")
    return eid, ok


def main():
    tok = http.login(config.USER, config.PWD, fatal=False)
    if not (isinstance(tok, str) and tok.count(".") == 2):
        print("!! 登录失败:", str(tok)[:160]); return 2
    # SoD：提交 admin，审批独立账号 e2eapp
    app_tok = http.login_as("e2eapp", "E2ePass@2026", fatal=False)
    app_hdr = http.session_headers(app_tok) if isinstance(app_tok, str) else None
    if not app_hdr:
        print("!! 审批账号登录失败，无法走 SoD"); return 2

    results = {}
    for ecode, spec in FIXTURES.items():
        print(f"[{ecode}]")
        _, results[ecode] = ensure_entity(ecode, spec, app_hdr)

    # 真值闭环：下单 1001 不再 409，且 VIP 折扣生效（199×0.88=175.12 量级，此处只校验 <199）
    st, j = call("POST", "/orders",
                 {"customerId": 1001,
                  "items": [{"productId": "1001", "productName": "无线鼠标", "quantity": 1, "unitPrice": 199.0}]})
    oid = (data(j) or {}).get("id") if isinstance(data(j), dict) else None
    print(f"[VERIFY] 下单(VIP客户1001×商品1001): http={st} oid={oid}")
    if oid:
        st, j = call("GET", f"/orders/{oid}")
        amount = (data(j) or {}).get("totalAmount") if isinstance(data(j), dict) else None
        print(f"[VERIFY] 订单金额含 VIP 折扣: amount={amount} -> {'PASS' if amount is not None and amount < 199 else 'FAIL(待 ACL 60s 刷新)'}")
    return 0 if all(results.values()) and st in (200, 201) else 2


if __name__ == "__main__":
    sys.exit(main())
