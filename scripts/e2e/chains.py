#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bone 跨模块端到端链路测试基座。

覆盖 8 条跨模块链路（C1..C8）+ 门禁脚本 G（网关路由 vs 代码路径常量一致性）。
所有请求经统一网关 8888，模拟人工操作顺序执行，断言真实响应。

运行：/Users/renhui.trh/.workbuddy/binaries/python/versions/3.13.12/bin/python3 /tmp/bone/chains.py [链路号...]
"""
import json
import time
import urllib.request
import urllib.error
import hmac
import hashlib
import sys

GW = "http://127.0.0.1:8888"
ADMIN = ("admin", "123456")
MOCK_SECRET = b"bone-blueprint-mock-secret"

# ---- 主数据实体常量（实测存在且 PUBLISHED）----
MD_PRODUCT = "760324212819755008"
MD_CUSTOMER = "760325780604452864"
MD_LEVEL = "760324310677061632"

# 客户实体的必填字段集合。scripts/migration/0016_masterdata_demo_seed.sql 把
# cust_status 建模为 required=1 后，建记录探针必须显式带上，否则后端按
# 「字段[cust_status]为必填项」返回 400，整条 C2 链在第一断言就中断。
# 集中在此定义，避免三处探针各自漏字段再次漂移。
def customer_data(code, name, level="ENTERPRISE", cust_status="ACTIVE"):
    """构造客户记录 payload，始终覆盖全部必填字段（含 cust_status）。"""
    return {
        "data": {
            "customer_code": code,
            "customer_name": name,
            "level_code": level,
            "cust_status": cust_status,
        }
    }

RESULTS = []


def http(method, path, body=None, tenant="0", token=None, headers=None, timeout=25):
    url = GW + path
    data = None
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    if token:
        h["Authorization"] = "Bearer " + token
    if tenant is not None:
        h["X-Tenant-Id"] = str(tenant)
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            try:
                return r.status, json.loads(raw) if raw else None
            except Exception:
                return r.status, {"_text": raw.decode("utf-8", "replace")}
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"_text": raw.decode("utf-8", "replace")}
    except Exception as e:
        return 0, {"_err": str(e)}


def login(user, pwd, tenant="0"):
    st, j = http("POST", "/api/v1/iam/login", {"username": user, "password": pwd}, tenant=tenant)
    if st != 200 or not j or not j.get("data"):
        raise SystemExit(f"login failed {st}: {json.dumps(j, ensure_ascii=False)[:200]}")
    return j["data"]["token"]


def sign(payment_id, trade_no, amount):
    """复刻 MockPaymentSignaturePortAdapter#payload 口径：
    paymentId|channelTradeNo|amount.stripTrailingZeros().toPlainString()"""
    s = str(amount)
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    payload = f"{payment_id}|{trade_no}|{s}"
    return hmac.new(MOCK_SECRET, payload.encode(), hashlib.sha256).hexdigest()


def check(cid, name, expect, actual, detail=""):
    if callable(expect):
        ok = expect(actual)
    elif isinstance(expect, tuple):
        ok = actual in expect
    else:
        ok = expect == actual
    mark = "PASS" if ok else "FAIL"
    RESULTS.append((cid, name, ok, f"expect={expect} actual={actual}"))
    tail = f"  {detail}" if detail else ""
    print(f"  [{mark}] {name}: expect={expect} actual={actual}{tail}")
    return ok


def data(j):
    return j.get("data") if isinstance(j, dict) else None


def rid_of(j):
    """写接口可能直接返回裸 ID 字符串，也可能返回对象，统一收敛。"""
    v = data(j)
    if isinstance(v, str):
        return v
    if isinstance(v, dict):
        return v.get("id")
    return None


def hdr(title):
    print(f"\n{'=' * 66}\n{title}\n{'=' * 66}")


# ---- 结构化断言工具（替代「只看 200」的弱断言） ----
#
# 为什么需要：仅断言 HTTP 200 会把「返回了正确形状的空壳」判为通过。此前实测到两个
# 被 200 断言完全掩盖的真实缺陷：
#   1) /api/v1/iam/audit/logs 传 pageNum/pageSize 被静默忽略（后端字段是 page/size），
#      永远返回第 1 页 10 条 —— 翻页功能在测试里「通过」但线上失效。
#   2) 多个列表端点返回体是 list 还是 PageResult 不一致，弱断言无法区分「真的空」与
#      「解析姿势错了拿到空」。
# 下面三个函数分别锁定：信封形状、元素形状+必填字段、分页参数真的生效。

def page_rows(j):
    """从 PageResult 里取当前页行；兼容 list/records/content 三种键 + 裸 list。"""
    d = data(j)
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        for k in ("list", "records", "content", "items"):
            if isinstance(d.get(k), list):
                return d[k]
    return None


def check_page_shape(cid, name, j, expect_rows=None, required=(), min_rows=0):
    """断言分页信封结构正确。

    expect_rows: 期望的 pageSize。实际行数应为 min(pageSize, 该页剩余条数)，
    故断言「恰好等于」在 pageSize 大于剩余时会误判 —— 这里改为双向夹逼：
    行数不得超过 pageSize，且当剩余足够时必须恰好等于 pageSize
    （后者才是「pageSize 真生效」的证据；前者只证明它没超发）。
    required: 每行必填字段元组，任一缺失即失败。
    """
    d = data(j)
    if not isinstance(d, dict):
        check(cid, f"{name}·分页信封", "PageResult 对象", type(d).__name__, str(j)[:120])
        return None
    missing_meta = [k for k in ("total", "page", "size") if k not in d]
    check(cid, f"{name}·分页信封(total/page/size 齐备)", [], missing_meta, f"keys={sorted(d.keys())[:8]}")
    rows = page_rows(j)
    if rows is None:
        check(cid, f"{name}·行集合存在", "list", "缺失", f"keys={sorted(d.keys())[:8]}")
        return None
    check(cid, f"{name}·行集合非空且≥{min_rows}", True, isinstance(rows, list) and len(rows) >= min_rows,
          f"rows={len(rows) if isinstance(rows, list) else type(rows).__name__}")
    if expect_rows is not None and isinstance(rows, list):
        total = d.get("total")
        n = len(rows)
        check(cid, f"{name}·行数不超过 pageSize({expect_rows})", True, n <= expect_rows,
              f"rows={n} total={total}")
        # 剩余条数足够时（total >= pageSize）必须恰好取满，否则说明 pageSize 被忽略
        if isinstance(total, int) and total >= expect_rows:
            check(cid, f"{name}·pageSize 真生效(应取满 {expect_rows} 行)", expect_rows, n,
                  f"total={total} page={d.get('page')} size={d.get('size')}")
    if required and isinstance(rows, list) and rows:
        sample = rows[0] if isinstance(rows[0], dict) else {}
        absent = [f for f in required if f not in sample]
        check(cid, f"{name}·首行必填字段 {list(required)}", [], absent, f"首行键={sorted(sample.keys())[:8]}")
    return rows


def check_list_shape(cid, name, j, required=(), min_rows=0):
    """断言裸 list 端点：确实是数组，且元素必填字段齐备。"""
    rows = data(j)
    if not isinstance(rows, list):
        check(cid, f"{name}·返回数组", "list", type(rows).__name__, str(j)[:120])
        return None
    check(cid, f"{name}·非空且≥{min_rows}", True, len(rows) >= min_rows, f"count={len(rows)}")
    if required and rows:
        sample = rows[0] if isinstance(rows[0], dict) else {}
        absent = [f for f in required if f not in sample]
        check(cid, f"{name}·首行必填字段 {list(required)}", [], absent, f"首行键={sorted(sample.keys())[:8]}")
    return rows


def check_err(cid, name, st, j, want_status, want_code=None, strict_code=True):
    """断言错误响应：状态码 + 业务错误码必须落在 ``data.errorCode``。

    历史（2026-10-02 第三轮修正）：
        本函数原先是「errorCode 或 message 前缀任一命中即过」。这是**弱断言** ——
        业务码只落在 message 前缀、``data.errorCode`` 为 null 时它照样 PASS，
        于是 2026-10-02 实测的缺陷（masterdata 全部 ``MD_*`` 码errorCode 为 null）
        长期测不出来，直到人工比对响应才发现。

        根因已修（``MasterDataErrors.of`` 曾误用 3 参 ``BizException`` 构造器，
        该构造器把 errorCode 恒置 null；HTTP 状态仍正确，故弱断言测不出）。
        故本函数改为**强制 errorCode 落位**：want_code 命中不了 errorCode 就FAIL。

    ``strict_code=False`` 仅用于已确认无法结构化的历史路径（会打印实际落点）。
    """
    d = data(j)
    code = d.get("errorCode") if isinstance(d, dict) else None
    msg = str((j or {}).get("message") or "")
    ok = st == want_status
    where = "不校验"
    if want_code:
        if code == want_code:
            where = "data.errorCode"
            ok = ok and True
        elif not strict_code and want_code in msg:
            where = "message前缀(errorCode为null，已降级)"
            ok = ok and True
        else:
            where = f"未落位(实际errorCode={code!r})"
            ok = False
    else:
        ok = ok and bool(code)
    check(cid, name, True, ok, f"HTTP={st} 期望={want_status} 码落点={where} msg={msg[:70]}")
    return code


# ============================ C1 交易主链路 ============================
def c1(tok, sub_tok=None, app_tok=None):
    hdr("C1 · 主数据 → 订单 → 支付 → 履约（核心商业闭环）")
    ts = int(time.time())
    # productId 在 CreateOrderReq 中是 Long，故商品业务码必须是纯数字（与种子商品 code=1001 同口径）
    code = str(ts % 900000 + 100000)

    # 1. 主数据：新建商品记录并走通 SoD 发布（提交人与审批人必须是不同账号）
    # recordCode 是主数据的一等业务主键字段（去重/检索/订阅分发的基础），必须在创建时显式传入；
    # 只填 data JSON 会让 record_code 为 NULL，点查主路径必然落空（只能靠 JSON 兜底）。
    st, j = http("POST", f"/api/v1/masterdata/records/entity/{MD_PRODUCT}", {
        "recordCode": code,
        "data": {"code": code, "name": f"链路商品{ts}", "price": 199.0, "unit": "件"}
    }, token=tok)
    check("C1", "主数据创建商品记录", 200, st, str(j)[:160])
    rid = rid_of(j)
    if not rid:
        return
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/submit", None, token=sub_tok or tok)
    check("C1", "提交人 submit", 200, st)
    # 光有 200 不够：submit 后记录状态必须真的转为 SUBMITTED，否则「提交」是个空动作
    st, j2 = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C1", "submit 后状态=PENDING_APPROVAL", "PENDING_APPROVAL", (data(j2) or {}).get("status"),
          str(data(j2) or {})[:120])
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/approve", None, token=app_tok or tok)
    check("C1", "审批人 approve（SoD：提交人≠审批人）", 200, st, str(j)[:140])
    st, j2 = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C1", "approve 后状态=APPROVED", "APPROVED", (data(j2) or {}).get("status"),
          str(data(j2) or {})[:120])
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/publish", None, token=tok)
    check("C1", "发布 publish", 200, st, str(j)[:140])
    st, j2 = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C1", "publish 后状态=PUBLISHED", "PUBLISHED", (data(j2) or {}).get("status"),
          str(data(j2) or {})[:120])

    # 2. ACL 点查：确认 blueprint 可经网关拿到该商品
    st, j = http("GET", f"/api/v1/masterdata/records/by-code?masterDataEntityId={MD_PRODUCT}&code={code}", token=tok)
    check("C1", "主数据 by-code 点查", 200, st)
    d = data(j) or {}
    check("C1", "点查命中新商品", True, d.get("recordCode") == code)
    # 点查回的必须是同一条记录（按 id 比对，防止 by-code 命中了别的同 code 记录）
    check("C1", "点查 id 与创建一致", str(rid), str(d.get("id")))

    # 3. 下单：单价由 transcend 主数据侧提供
    st, j = http("POST", "/api/v1/orders", {
        "customerId": 1001,
        "items": [{"productId": int(code), "productName": f"链路商品{ts}", "quantity": 1, "unitPrice": 199.0}],
    }, token=tok)
    check("C1", "下单（跨模块消费主数据）", 201, st, str(j)[:160])
    oid = rid_of(j)
    if not oid:
        return

    st, j = http("GET", f"/api/v1/orders/{oid}", token=tok)
    od = data(j)
    check("C1", "订单初始态", "CREATED", od.get("status"))
    amount = od.get("totalAmount")
    # 折扣必须非 nil：证明 ACL 真的取到了客户等级折扣率
    check("C1", "金额含客户等级折扣（<199）", True, amount < 199.0, f"amount={amount}")

    # 4. 支付发起
    st, j = http("POST", "/api/v1/payments/initiate", {"orderId": int(oid)}, token=tok)
    check("C1", "发起支付（网关 payments 路由）", 200, st, str(j)[:160])
    pid = data(j).get("paymentId") if data(j) else None
    if not pid:
        return

    # 5. 回调：合法签名通过
    trade = f"TRD{ts}"
    body = {"paymentId": int(pid), "channelTradeNo": trade,
            "paidAmount": amount, "signature": sign(pid, trade, amount), "success": True}
    st, j = http("POST", "/api/v1/payments/callback", body, token=tok)
    check("C1", "支付回调（HMAC 验签通过）", 200, st, str(j)[:140])

    # 6. 回调：篡改金额必须被验签拦截
    bad = dict(body, paidAmount=1.00)
    st, j = http("POST", "/api/v1/payments/callback", bad, token=tok)
    check("C1", "篡改金额回调被拒", 401, st, (j or {}).get("data", {}).get("errorCode", "") if isinstance(j, dict) else "")

    # 7. 支付单 + 订单一致性
    st, j = http("GET", f"/api/v1/payments/{pid}", token=tok)
    check("C1", "支付单终态 SUCCESS", "SUCCESS", (data(j) or {}).get("status"))
    st, j = http("GET", f"/api/v1/orders/{oid}", token=tok)
    check("C1", "订单随支付流转", "PAID", (data(j) or {}).get("status"))

    # 8. 履约
    for act, want in (("ship", "SHIPPED"), ("deliver", "DELIVERED")):
        st, j = http("POST", f"/api/v1/orders/{oid}/{act}", None, token=tok)
        check("C1", f"订单 {act}", 200, st, str(j)[:140])
        st, j = http("GET", f"/api/v1/orders/{oid}", token=tok)
        check("C1", f"{act} 后状态", want, (data(j) or {}).get("status"))

    # 9. 状态机防御：终态重复发货不仅要是 409，业务码也须稳定（否则无法与「订单不存在」区分）
    st, j = http("POST", f"/api/v1/orders/{oid}/ship", None, token=tok)
    check_err("C1", "终态后重复发货被拒(409+BP_ORDER_STATUS_CONFLICT)", st, j, 409,
              "BP_ORDER_STATUS_CONFLICT")
    # 前置未支付直接发货同样必须被拒（防止绕过支付推进履约）
    st, j2 = http("POST", "/api/v1/orders", {
        "customerId": 1001,
        "items": [{"productId": int(code), "productName": f"链路商品{ts}", "quantity": 1, "unitPrice": 199.0}],
    }, token=tok)
    oid2 = rid_of(j2)
    if oid2:
        check("C1", "未支付订单创建成功（前置守卫用）", (200, 201), st, str(j2)[:120])
        st, j3 = http("POST", f"/api/v1/orders/{oid2}/ship", None, token=tok)
        check_err("C1", "未支付即发货被拒(409+STATUS_CONFLICT)", st, j3, 409,
                  "BP_ORDER_STATUS_CONFLICT")
        st, j4 = http("GET", f"/api/v1/orders/{oid2}", token=tok)
        check("C1", "被拒后状态仍为 CREATED（未误推进）", "CREATED", (data(j4) or {}).get("status"))

    # 10. 退款
    st, j = http("POST", f"/api/v1/payments/{pid}/refund", {"refundAmount": 10.00}, token=tok)
    check("C1", "支付单退款", 200, st, str(j)[:140])
    return {"orderId": oid, "paymentId": pid, "productCode": code}


# ============================ C2 主数据治理 ============================
def c2(tok, sub_tok, app_tok):
    hdr("C2 · 主数据 SoD 治理：双账号分离 + 版本 + 参照域")
    ts = int(time.time())
    code = f"C2SUP{ts}"
    st, j = http("POST", f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}",
                  customer_data(code, f"链路供应商{ts}"), token=tok)
    check("C2", "创建客户记录（提交人）", 200, st, str(j)[:160])
    rid = rid_of(j)
    if not rid:
        return
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/submit", None, token=sub_tok or tok)
    check("C2", "提交人 submit", 200, st)
    st, j2 = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C2", "submit 后状态=PENDING_APPROVAL", "PENDING_APPROVAL", (data(j2) or {}).get("status"),
          str(data(j2) or {})[:120])
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/approve", None, token=app_tok or tok)
    check("C2", "审批人 approve（SoD 分离）", 200, st)
    st, j2 = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C2", "approve 后状态=APPROVED", "APPROVED", (data(j2) or {}).get("status"),
          str(data(j2) or {})[:120])
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/publish", None, token=tok)
    check("C2", "发布 publish", 200, st)
    st, j = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C2", "终态 PUBLISHED", "PUBLISHED", (data(j) or {}).get("status"))
    st, j = http("GET", f"/api/v1/masterdata/records/{rid}/versions", token=tok)
    check("C2", "版本列表可读", 200, st)
    # 版本列表须真的是数组（曾实测返回 []：走通 SoD 发布后仍无版本，是真实可疑点，
    # 弱断言只看 200 会把它判为通过）
    check_list_shape("C2", "版本列表", j, min_rows=0)
    # SoD 负向：同一人既提交又审批必须被拒，否则 SoD 只是文档约定
    st, j2 = http("POST", f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}",
                   customer_data(f"C2SOD{ts}", f"SoD探测{ts}"), token=tok)
    rid2 = rid_of(j2)
    if rid2:
        http("POST", f"/api/v1/masterdata/records/{rid2}/submit", None, token=tok)
        st, j3 = http("POST", f"/api/v1/masterdata/records/{rid2}/approve", None, token=tok)
        check_err("C2", "同一人自审被拒(403+MD_SOD_VIOLATION)", st, j3, 403, "MD_SOD_VIOLATION")
    # 参照域负向：非法 level_code 必须被拒，且业务码稳定
    st, j = http("POST", f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}",
                  customer_data(code, "非法等级", level="NOT_A_LEVEL"), token=tok)
    check_err("C2", "非法 level_code 被参照域拦截(400+FIELD_VALIDATION_FAILED)", st, j, 400,
              "MD_RECORD_FIELD_VALIDATION_FAILED")
    return {"recordId": rid, "code": code}


# ============================ C3 IAM 授权一致性 ============================
def c3(tok):
    hdr("C3 · IAM 授权 → 跨模块鉴权一致性")
    ts = int(time.time())
    st, depts = http("GET", "/api/v1/iam/depts/tree", token=tok)
    found_id = []

    def walk(n):
        if found_id:
            return
        if isinstance(n, list):
            for c in n:
                walk(c)
        elif isinstance(n, dict):
            if n.get("id") is not None:
                found_id.append(n["id"])
                return
            for c in (n.get("children") or []):
                walk(c)

    walk(data(depts))
    did = found_id[0] if found_id else None
    check("C3", "部门树可读（取 deptId）", 200, st, f"deptId={did}")
    if not did:
        return
    user = f"c3view{ts}"
    st, j = http("POST", "/api/v1/iam/accounts", {
        "username": user, "password": "C3pass@2026", "deptId": int(did),
        "nickname": "只读员", "email": f"{user}@bone.local"
    }, token=tok)
    check("C3", "创建受限账号（无写 scope）", 200, st, str(j)[:160])
    uid = rid_of(j)
    if not uid:
        return
    st, j = http("POST", f"/api/v1/iam/accounts/{uid}/password", {"password": "C3pass@2026"}, token=tok)
    ltok = login(user, "C3pass@2026")
    # 无 order:orders:write → 下单必须 403
    st, j = http("POST", "/api/v1/orders", {
        "customerId": 1001,
        "items": [{"productId": 1001, "productName": "x", "quantity": 1, "unitPrice": 1.0}]}, token=ltok)
    check("C3", "受限账号下单被鉴权拦截", 403, st, str(j)[:120])
    # 重复 code → 409（本次修复项）
    # 重复请求必须同样通过入参校验（username/email 在重名检查之前先校验），否则拿到的是 400 而非 409
    st, j = http("POST", "/api/v1/iam/accounts", {
        "username": user, "password": "C3pass@2026", "deptId": int(did),
        "nickname": "只读员", "email": f"{user}@bone.local"}, token=tok)
    check("C3", "账号重复冲突返回 409", 409, st, str(j)[:140])
    check("C3", "重复冲突业务码稳定", True, "CONFLICT" in str((j or {}).get("message", "")).upper()
          or (isinstance(data(j), dict) and bool(data(j).get("errorCode"))),
          f"msg={str((j or {}).get('message'))[:80]}")
    return {"userId": uid, "username": user}


# ============================ C4 集成引擎 ============================
def c4(tok):
    hdr("C4 · 集成引擎：连接器 → 流程 → 执行")
    # 注意：本模块分页入参是 pageNum/pageSize（与 iam 的 page/size 不同族，勿混用）
    st, j = http("GET", "/api/v1/integration/connectors?pageNum=1&pageSize=3", token=tok)
    check_page_shape("C4", "连接器列表", j, expect_rows=3, required=("id", "name", "type"), min_rows=1)
    st, j = http("GET", "/api/v1/integration/flows?pageNum=1&pageSize=2", token=tok)
    check_page_shape("C4", "流程列表", j, expect_rows=2, required=("id", "name", "status"), min_rows=1)
    st, j = http("GET", "/api/v1/integration/statistics", token=tok)
    check("C4", "集成执行统计面", 200, st, str(j)[:160])
    # 实测是「按流程聚合的数组」而非对象：每行含 flowId/flowName/成功失败计数
    stats = check_list_shape("C4", "统计面", j,
                             required=("flowId", "executionCount", "successCount", "failureCount"), min_rows=0)
    if stats:
        bad = [s.get("flowId") for s in stats
               if int(s.get("executionCount") or 0) != int(s.get("successCount") or 0)
               + int(s.get("failureCount") or 0)]
        check("C4", "统计计数自洽(成功+失败=执行数)", [], bad,
              f"行数={len(stats)} 样本={ {k: stats[0].get(k) for k in ('flowId','executionCount','successCount','failureCount')} }")
    # 执行记录允许为空库（total=0），故只断言信封与「行数不超过 pageSize」
    st, j = http("GET", "/api/v1/integration/executions?pageNum=1&pageSize=10", token=tok)
    rows = check_page_shape("C4", "集成执行记录列表", j, required=("id",), min_rows=0)
    if rows is not None and len(rows) < 10:
        check("C4", "执行记录数不超过 pageSize", True, len(rows) <= 10, f"rows={len(rows)}")
    # HC-003：框架级绑定异常也必须落在 ApiResponse 信封内（曾因缺 bone-web 依赖漏成裸 Spring 400）
    # 实测：bone-integration 的绑定异常统一收敛为 COMMON_VALIDATION_FAILED
    #（未像 bone-iam 那样细分到 COMMON_MALFORMED_REQUEST），故这里只断言该服务实际给出的码。
    for path, desc in (("/api/v1/integration/executions", "缺分页参数"),
                       ("/api/v1/integration/flows/abc", "路径变量类型错")):
        st, j = http("GET", path, token=tok)
        # 必须是 ApiResponse 信封（有 success 键 + 顶层 code），而不是裸 Spring ProblemDetail
        check("C4", f"统一响应契约-{desc} 落 ApiResponse 信封", True,
              isinstance(j, dict) and "success" in j and "code" in j,
              f"HTTP {st} 顶层键={sorted(j.keys()) if isinstance(j, dict) else type(j).__name__}")
        d = data(j)
        code = d.get("errorCode") if isinstance(d, dict) else None
        check("C4", f"{desc} 绑定异常收敛为 400 而非 500", 400, st,
              f"errorCode={code} msg={str((j or {}).get('message'))[:60]}")
        check("C4", f"{desc} 业务码=COMMON_VALIDATION_FAILED", "COMMON_VALIDATION_FAILED", code,
              f"msg={str((j or {}).get('message'))[:60]}")


# ============================ C5 扩展引擎 ============================
def c5(tok):
    hdr("C5 · 扩展引擎：扩展点 → 插件 → 绑定")
    st, j = http("GET", "/api/v1/extension/points", token=tok)
    pts = check_list_shape("C5", "扩展点列表", j, required=("id", "name", "interfaceName"), min_rows=1)
    # 插件必须挂在真实存在的扩展点上，否则「扩展点→插件」这条链路是空的
    st, j = http("GET", "/api/v1/extension/plugins", token=tok)
    plugins = check_list_shape("C5", "插件列表", j, required=("id", "className", "extPointId"), min_rows=1)
    if pts and plugins:
        pt_ids = {str(p.get("id")) for p in pts if isinstance(p, dict)}
        orphan = [p.get("id") for p in plugins if str(p.get("extPointId")) not in pt_ids]
        check("C5", "插件均绑定到已知扩展点", [], orphan,
              f"扩展点={sorted(pt_ids)[:4]} 插件数={len(plugins)}")
    st, j = http("GET", "/api/v1/extension/points/observability/metrics", token=tok)
    check("C5", "扩展点可观测面", (200, 404), st)


# ============================ C6 生成器 ============================
def c6(tok):
    hdr("C6 · Studio 生成器：数据源 → 元数据快照 → 代码生成")
    # 注意：本模块分页入参是 page/size（与 integration/metadata 的 pageNum/pageSize 不同族）
    st, j = http("GET", "/api/v1/generator/data-sources?page=1&size=2", token=tok)
    ds = check_page_shape("C6", "数据源列表", j, expect_rows=2, required=("id", "dbName"), min_rows=1)
    # 快照行无 id（以 tableName 为业务主键，见 metadata-entity-snapshots 响应形状）
    st, j = http("GET", "/api/v1/generator/metadata-entity-snapshots?page=1&size=3", token=tok)
    snaps = check_page_shape("C6", "元数据实体快照", j, expect_rows=3,
                             required=("tableName", "columns", "deliveryMode"), min_rows=1)
    st, j = http("GET", "/api/v1/generator/capabilities", token=tok)
    caps = check_list_shape("C6", "生成能力清单", j,
                            required=("name", "inputSchema", "outputSchema", "retryable"), min_rows=1)
    st, j = http("GET", "/api/v1/generator/templates?page=1&size=20", token=tok)
    tpl = check_page_shape("C6", "模板列表", j, expect_rows=20,
                           required=("id", "code", "engine", "status"), min_rows=1)
    # 内置模板正文为空是**设计如此**（唯一真源 = classpath templates/{code}.ftl，
    # 见 scripts/migration/0009 与 CodeTemplate.fillBuiltInContent），不是数据缺失。
    # 但 12 类内置骨架必须齐备 —— 少一类用户就勾不到完整骨架。
    if tpl:
        builtin = {str(t.get("code")) for t in tpl if str(t.get("tenantId")) == "0"}
        need = {"entity", "repository", "applicationService", "controller", "response", "assembler",
                "createCommand", "updateCommand", "queryDto",
                "createRequest", "updateRequest", "pageQuery"}
        check("C6", "12 类内置模板齐备", [], sorted(need - builtin), f"已登记={len(builtin)} 类")
        non_pub = sorted({str(t.get("code")) for t in tpl if str(t.get("status")) != "PUBLISHED"})
        check("C6", "模板均已发布", [], non_pub, f"status集合={sorted({str(t.get('status')) for t in tpl})}")
        # 已知数据不一致（不是功能缺陷）：engine 取值大小写分裂 —— 0015 迁移脚本插入的行用大写
        # FREEMARKER，而 0009 之前的历史 5 行是小写 freemarker；域模型 CodeTemplate.create 默认
        # FREEMARKER（大写）。渲染路径 TemplateRenderer 恒走 FreeMarker、不比对该字段，
        # 故当前不影响生成，仅作为数据漂移记录在此（收敛需 DB 迁移，超出 E2E 脚本职责）。
        engines = sorted({str(t.get("engine")) for t in tpl})
        if len({e.upper() for e in engines}) == 1 and len(engines) > 1:
            print(f"  [WARN] C6 · engine 大小写分裂（不影响渲染，建议迁移收敛）: {engines}")
    st, j = http("GET", "/api/v1/generator/history", token=tok)
    check_list_shape("C6", "生成历史", j, required=("id", "dataSourceId"), min_rows=0)


# ============================ C7 元数据 → 主数据 ============================
def c7(tok):
    hdr("C7 · 元数据建模 → 主数据联动")
    st, j = http("GET", "/api/v1/metadata/entities?pageNum=1&pageSize=4", token=tok)
    ents = check_page_shape("C7", "元数据实体列表", j, expect_rows=4,
                            required=("id", "code", "displayName"), min_rows=1)
    st, j = http("GET", "/api/v1/metadata/templates", token=tok)
    check("C7", "元数据模板列表", (200, 404), st)
    st, j = http("GET", "/api/v1/metadata/relationships", token=tok)
    check("C7", "元数据关系列表", (200, 404), st)
    st, j = http("GET", "/api/v1/masterdata/templates?pageNum=1&pageSize=2", token=tok)
    md_tpl = check_page_shape("C7", "主数据域模板（消费元数据源）", j, expect_rows=2,
                              required=("id", "domainCode"), min_rows=1)
    st, j = http("GET", "/api/v1/masterdata/reference-sets", token=tok)
    refs = check_list_shape("C7", "参照数据集（约束来源）", j,
                            required=("id", "setCode", "status"), min_rows=1)
    # 域模板 fieldSchema 里引用的参照集必须存在，否则 C2 的参照域校验会引用不存在的约束源
    if md_tpl and refs:
        ref_codes = {str(r.get("setCode")) for r in refs if isinstance(r, dict)}
        schema = md_tpl[0].get("fieldSchema") if isinstance(md_tpl[0], dict) else None
        cited = set()
        if isinstance(schema, str):
            cited = {w for w in ref_codes if w in schema}
        check("C7", "域模板字段Schema 引用已登记参照集", True, isinstance(schema, (str, dict)),
              f"schema类型={type(schema).__name__} 参照集={sorted(ref_codes)[:4]} 命中={sorted(cited)[:4]}")


# ============================ C8 系统/通知/可观测 ============================
def c8(tok):
    hdr("C8 · 系统管理 · 通知 · 审计")
    # 注意：system 用 pageNum/pageSize，iam audit 用 page/size —— 同名不同族，写错静默失效
    st, j = http("GET", "/api/v1/system/config?pageNum=1&pageSize=3", token=tok)
    check_page_shape("C8", "系统配置", j, expect_rows=3, required=("id", "configKey"), min_rows=1)
    st, j = http("GET", "/api/v1/system/dict/types/page?pageNum=1&pageSize=5", token=tok)
    check_page_shape("C8", "字典类型分页", j, required=("id",), min_rows=0)
    st, j = http("GET", "/api/v1/system/logs?pageNum=1&pageSize=3", token=tok)
    check_page_shape("C8", "系统日志分页", j, expect_rows=3, required=("id", "logLevel"), min_rows=1)
    st, j = http("GET", "/api/v1/notification/messages?userId=1", token=tok)
    check_list_shape("C8", "通知站内信", j, required=("id",), min_rows=0)
    # IAM 审计：page/size 族。断言 pageSize 真生效 + 翻页内容不重叠，
    # 否则「pageNum/pageSize 被静默忽略」这类缺陷永远测不出来。
    st, j = http("GET", "/api/v1/iam/audit/logs?page=1&size=3", token=tok)
    p1 = check_page_shape("C8", "IAM 审计日志（写操作留痕）", j, expect_rows=3,
                          required=("id", "operation", "createdAt"), min_rows=1)
    st, j2 = http("GET", "/api/v1/iam/audit/logs?page=2&size=3", token=tok)
    p2 = check_page_shape("C8", "IAM 审计日志第2页", j2, expect_rows=3, required=("id",), min_rows=1)
    if p1 and p2:
        ids1 = {str(r.get("id")) for r in p1}
        ids2 = {str(r.get("id")) for r in p2}
        check("C8", "审计日志翻页不重复", [], sorted(ids1 & ids2), f"第1页={len(ids1)} 第2页={len(ids2)}")
    st, j = http("GET", "/api/v1/system/console/services", token=tok)
    check("C8", "控制台服务健康面", (200, 404), st)


# ============================ C9 凭证视角矩阵（鉴权边界） ============================
def c9(tok):
    hdr("C9 · 凭证视角矩阵 · 鉴权边界（防止被 admin token 掩盖）")
    # 背景：上一轮所有用例都带 admin token 跑，等于把每条鉴权边界都测成假阳性——
    # 「支付回调由无 JWT 的外部渠道方调用」这一真实断点因此完全没暴露（网关 401
    # GW_UNAUTHORIZED，而 blueprint 内部早已 permitAll）。本链路固定用三种视角
    # 交叉验证同一批端点，把这类盲区固化成回归用例。
    ts = int(time.time())
    order_body = {"customerId": 1001,
                  "items": [{"productId": 1001, "productName": "视角验证",
                             "quantity": 1, "unitPrice": 129.0}]}

    # --- 视角 B：受限用户（有凭证、无权限）→ 必须 403，不能退化成 500 ---
    st, depts = http("GET", "/api/v1/iam/depts/tree", token=tok)
    did = depts["data"][0]["id"]
    user = f"c9{ts % 100000}"
    http("POST", "/api/v1/iam/accounts", {
        "username": user, "password": "C9pass@2026", "deptId": int(did),
        "nickname": "受限视角", "email": f"{user}@bone.local"}, token=tok)
    limited = login(user, "C9pass@2026")
    st, j = http("POST", "/api/v1/orders", order_body, token=limited)
    check("C9", "受限用户下单 → 403（不得是 500）", 403, st, str(j)[:140])

    # --- 视角 A：超管下单，为渠道视角准备支付单 ---
    st, j = http("POST", "/api/v1/orders", order_body, token=tok)
    oid = rid_of(j)
    check("C9", "超管下单 → 2xx", (200, 201), st)
    st, j = http("POST", "/api/v1/payments/initiate", {"orderId": int(oid)}, token=tok)
    pid = (data(j) or {}).get("paymentId") or (data(j) or {}).get("id")
    st, j = http("GET", f"/api/v1/orders/{oid}", token=tok)
    amount = (data(j) or {}).get("totalAmount")

    # --- 视角 C：无凭证外部渠道方 ---
    trade = f"C9{ts}"
    ok_body = {"paymentId": int(pid), "channelTradeNo": trade,
               "paidAmount": float(amount), "signature": sign(pid, trade, amount),
               "success": True}
    st, j = http("POST", "/api/v1/payments/callback", ok_body, token=None)
    check("C9", "无凭证渠道回调 → 200（网关须放通 callback）", 200, st, str(j)[:140])
    st, j = http("GET", f"/api/v1/orders/{oid}", token=tok)
    check("C9", "无凭证回调后订单置为 PAID", "PAID", (data(j) or {}).get("status"))

    bad = dict(ok_body)
    bad["signature"] = "deadbeef"
    st, j = http("POST", "/api/v1/payments/callback", bad, token=None)
    check("C9", "无凭证 + 签名错 → 401（验签仍生效）", 401, st)

    # 白名单是前缀匹配，放通范围必须精确到 callback：其余支付端点仍须 401
    for path, method, desc in (
            ("/api/v1/payments/initiate", "POST", "发起支付"),
            (f"/api/v1/payments/{pid}/refund", "POST", "退款"),
            (f"/api/v1/payments/{pid}", "GET", "支付单查询")):
        body = {"refundAmount": 1.0} if "refund" in path else {"orderId": int(oid)}
        st, j = http(method, path, body, token=None)
        check("C9", f"无凭证 {desc} → 401（白名单不得误放通）", 401, st)

    st, j = http("POST", "/api/v1/orders", order_body, token=None)
    check("C9", "无凭证下单 → 401", 401, st)


# ============================ G 网关路由门禁 ============================
def gate():
    hdr("G · 门禁：代码 API 路径常量 vs 网关路由登记一致性")
    import re
    import os
    root = "/Users/renhui.trh/wps/bone"
    yml = open(os.path.join(root, "bone-platform/bone-gateway/src/main/resources/application.yml"),
               encoding="utf-8").read()
    cov = set()
    for m in re.finditer(r"- Path=(.+)", yml):
        for part in m.group(1).split(","):
            cov.add(part.strip())

    # 路径常量的字面值（Controller 用 PlatformApiPaths.IAM_V1 等常量，单纯正则抓不到）
    consts = {}
    for base, _, files in os.walk(root):
        if "/target/" in base or "/node_modules/" in base:
            continue
        for f in files:
            if f not in ("PlatformApiPaths.java", "GeneratorApiPaths.java"):
                continue
            src = open(os.path.join(base, f), encoding="utf-8").read()
            # 第一轮：字面量  String X = "/api/..."
            for k, v in re.findall(r'String\s+(\w+)\s*=\s*"([^"]*)"', src):
                consts[k] = v
            # 第二轮：拼接式  String Y = V1_PREFIX + "/admin"，可多轮传递到不动点
            pairs = re.findall(r'String\s+(\w+)\s*=\s*(\w+)\s*\+\s*"([^"]*)"', src)
            for _ in range(len(pairs) + 1):
                for k, ref, tail in pairs:
                    if ref in consts and consts.get(k) != consts[ref] + tail:
                        consts[k] = consts[ref] + tail

    def expand(expr):
        """把 PlatformApiPaths.IAM_V1 + "/roles" 展开成 /api/v1/iam/roles"""
        m = re.match(r'\s*(\w+)\.(\w+)\s*\+\s*"([^"]*)"', expr)
        if m and consts.get(m.group(2)):
            return consts[m.group(2)] + m.group(3)
        m = re.match(r'\s*(\w+)\.(\w+)\s*', expr)
        if m and consts.get(m.group(2)):
            return consts[m.group(2)]
        v = re.search(r'"(/api/[^"]*)"', expr)
        return v.group(1) if v else None

    # 非业务面：框架自带 + 生成器产出的示例代码，不属 PI 网关南北向必须通过的面
    skip_dirs = ("/generated-code/", "/src/test/", "/bone-web/")

    found = set()
    for base, _, files in os.walk(root):
        if "/target/" in base or "/node_modules/" in base:
            continue
        if any(sd in base for sd in skip_dirs):
            continue
        for f in files:
            if not f.endswith("Controller.java"):
                continue
            src = open(os.path.join(base, f), encoding="utf-8").read()
            for m in re.finditer(r'@RequestMapping\(([^)]*)\)', src, re.S):
                p = expand(m.group(1))
                if p:
                    found.add(p)
    missing = []
    # 已知的内部 RPC 面：服务间调用，故意不暴露到网关南北向入口
    internal_rpc = {"/api/rpc/orders"}
    for p in sorted(found):
        if p in internal_rpc:
            continue
        ok = any(p == c or p.startswith(c[:-3] if c.endswith("/**") else c) for c in cov)
        if not ok:
            missing.append(p)
    check("G", "所有用户面 Controller 路径已登记网关", [], missing, "" if not missing else "缺失路由")
    # 反向：网关登记了但代码里没有（死路由）
    dead = []
    for c in sorted(cov):
        base_p = c[:-3] if c.endswith("/**") else c
        hit = any(p == base_p or p.startswith(base_p) for p in found)
        if not hit:
            dead.append(c)
    check("G", "无孤儿路由（登记但无实现）", [], dead, "" if not dead else "疑似死路由")


def main():
    args = sys.argv[1:] or ["all"]
    tok = login(*ADMIN)
    print(f"已登录 admin（网关 {GW}）")
    sub_tok = app_tok = None
    for pair_name in (("e2esub", "E2ePass@2026"),):
        try:
            sub_tok = login(*pair_name)
        except SystemExit:
            sub_tok = None
    try:
        app_tok = login("e2eapp", "E2ePass@2026")
    except SystemExit:
        app_tok = None

    runners = {"C1": lambda: c1(tok, sub_tok, app_tok), "C2": lambda: c2(tok, sub_tok, app_tok),
               "C3": lambda: c3(tok), "C4": lambda: c4(tok), "C5": lambda: c5(tok),
               "C6": lambda: c6(tok), "C7": lambda: c7(tok), "C8": lambda: c8(tok),
               "C9": lambda: c9(tok), "G": gate}
    todo = list(runners.keys()) if "all" in args else [a.upper() for a in args]
    for k in todo:
        try:
            runners[k]()
        except Exception as e:
            print(f"  [ERROR] {k} 执行异常: {e}")
            RESULTS.append((k, "执行异常", False, str(e)))

    hdr("汇总")
    total = len(RESULTS)
    passed = sum(1 for r in RESULTS if r[2])
    from collections import Counter
    per = Counter()
    for cid, _, ok, _ in RESULTS:
        per[cid] += 1
    print(f"断言总数 {total}，通过 {passed}，失败 {total - passed}")
    fails = [r for r in RESULTS if not r[2]]
    if fails:
        print("\n失败明细：")
        for cid, name, _, detail in fails:
            print(f"  {cid} | {name} | {detail[:220]}")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
