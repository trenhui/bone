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
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/approve", None, token=app_tok or tok)
    check("C1", "审批人 approve（SoD：提交人≠审批人）", 200, st, str(j)[:140])
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/publish", None, token=tok)
    check("C1", "发布 publish", 200, st, str(j)[:140])

    # 2. ACL 点查：确认 blueprint 可经网关拿到该商品
    st, j = http("GET", f"/api/v1/masterdata/records/by-code?masterDataEntityId={MD_PRODUCT}&code={code}", token=tok)
    check("C1", "主数据 by-code 点查", 200, st)
    d = data(j) or {}
    check("C1", "点查命中新商品", True, d.get("recordCode") == code)

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

    # 9. 状态机防御
    st, j = http("POST", f"/api/v1/orders/{oid}/ship", None, token=tok)
    check("C1", "终态后重复发货被拒", 409, st)

    # 10. 退款
    st, j = http("POST", f"/api/v1/payments/{pid}/refund", {"refundAmount": 10.00}, token=tok)
    check("C1", "支付单退款", 200, st, str(j)[:140])
    return {"orderId": oid, "paymentId": pid, "productCode": code}


# ============================ C2 主数据治理 ============================
def c2(tok, sub_tok, app_tok):
    hdr("C2 · 主数据 SoD 治理：双账号分离 + 版本 + 参照域")
    ts = int(time.time())
    code = f"C2SUP{ts}"
    st, j = http("POST", f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}", {
        "data": {"customer_code": code, "customer_name": f"链路供应商{ts}", "level_code": "ENTERPRISE"}
    }, token=tok)
    check("C2", "创建客户记录（提交人）", 200, st, str(j)[:160])
    rid = rid_of(j)
    if not rid:
        return
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/submit", None, token=sub_tok or tok)
    check("C2", "提交人 submit", 200, st)
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/approve", None, token=app_tok or tok)
    check("C2", "审批人 approve（SoD 分离）", 200, st)
    st, j = http("POST", f"/api/v1/masterdata/records/{rid}/publish", None, token=tok)
    check("C2", "发布 publish", 200, st)
    st, j = http("GET", f"/api/v1/masterdata/records/{rid}", token=tok)
    check("C2", "终态 PUBLISHED", "PUBLISHED", (data(j) or {}).get("status"))
    st, j = http("GET", f"/api/v1/masterdata/records/{rid}/versions", token=tok)
    check("C2", "版本列表可读", 200, st)
    # 参照域负向：非法 level_code 必须被拒
    st, j = http("POST", f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}", {
        "data": {"customer_code": code, "customer_name": "非法等级", "level_code": "NOT_A_LEVEL"}
    }, token=tok)
    check("C2", "非法 level_code 被参照域拦截", 400, st)
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
    return {"userId": uid, "username": user}


# ============================ C4 集成引擎 ============================
def c4(tok):
    hdr("C4 · 集成引擎：连接器 → 流程 → 执行")
    st, j = http("GET", "/api/v1/integration/connectors", token=tok)
    check("C4", "连接器列表", 200, st)
    st, j = http("GET", "/api/v1/integration/flows", token=tok)
    check("C4", "流程列表", 200, st)
    st, j = http("GET", "/api/v1/integration/statistics", token=tok)
    check("C4", "集成执行统计面", 200, st, str(j)[:160])
    st, j = http("GET", "/api/v1/integration/executions?pageNum=1&pageSize=10", token=tok)
    check("C4", "集成执行记录列表", 200, st, str(j)[:160])
    # HC-003：框架级绑定异常也必须落在 ApiResponse 信封内（曾因缺 bone-web 依赖漏成裸 Spring 400）
    for path, desc in (("/api/v1/integration/executions", "缺分页参数"),
                       ("/api/v1/integration/flows/abc", "路径变量类型错")):
        st, j = http("GET", path, token=tok)
        check("C4", f"统一响应契约-{desc}", True, isinstance(j, dict) and "success" in j,
              f"HTTP {st}")


# ============================ C5 扩展引擎 ============================
def c5(tok):
    hdr("C5 · 扩展引擎：扩展点 → 插件 → 绑定")
    st, j = http("GET", "/api/v1/extension/points", token=tok)
    check("C5", "扩展点列表", 200, st)
    pts = data(j) or []
    if isinstance(pts, dict):
        pts = pts.get("content") or pts.get("records") or []
    st, j = http("GET", "/api/v1/extension/plugins", token=tok)
    check("C5", "插件列表", 200, st)
    st, j = http("GET", "/api/v1/extension/points/observability/metrics", token=tok)
    check("C5", "扩展点可观测面", (200, 404), st)


# ============================ C6 生成器 ============================
def c6(tok):
    hdr("C6 · Studio 生成器：数据源 → 元数据快照 → 代码生成")
    st, j = http("GET", "/api/v1/generator/data-sources", token=tok)
    check("C6", "数据源列表", 200, st)
    ds = data(j) or []
    if isinstance(ds, dict):
        ds = ds.get("records") or ds.get("content") or []
    check("C6", "存在可用数据源", True, len(ds) > 0, f"count={len(ds)}")
    st, j = http("GET", "/api/v1/generator/metadata-entity-snapshots", token=tok)
    check("C6", "元数据实体快照", 200, st)
    st, j = http("GET", "/api/v1/generator/capabilities", token=tok)
    check("C6", "生成能力清单", 200, st)
    st, j = http("GET", "/api/v1/generator/templates", token=tok)
    check("C6", "模板列表", 200, st)
    st, j = http("GET", "/api/v1/generator/history", token=tok)
    check("C6", "生成历史", 200, st)


# ============================ C7 元数据 → 主数据 ============================
def c7(tok):
    hdr("C7 · 元数据建模 → 主数据联动")
    st, j = http("GET", "/api/v1/metadata/entities?pageNum=1&pageSize=10", token=tok)
    check("C7", "元数据实体列表", 200, st)
    st, j = http("GET", "/api/v1/metadata/templates", token=tok)
    check("C7", "元数据模板列表", (200, 404), st)
    st, j = http("GET", "/api/v1/metadata/relationships", token=tok)
    check("C7", "元数据关系列表", (200, 404), st)
    st, j = http("GET", "/api/v1/masterdata/templates", token=tok)
    check("C7", "主数据域模板（消费元数据源）", 200, st)
    st, j = http("GET", "/api/v1/masterdata/reference-sets", token=tok)
    check("C7", "参照数据集（约束来源）", 200, st)


# ============================ C8 系统/通知/可观测 ============================
def c8(tok):
    hdr("C8 · 系统管理 · 通知 · 审计")
    st, j = http("GET", "/api/v1/system/config", token=tok)
    check("C8", "系统配置", 200, st)
    st, j = http("GET", "/api/v1/system/dict/types/page?pageNum=1&pageSize=5", token=tok)
    check("C8", "字典类型分页", 200, st, str(j)[:120])
    st, j = http("GET", "/api/v1/system/logs?pageNum=1&pageSize=5", token=tok)
    check("C8", "系统日志分页", 200, st)
    st, j = http("GET", "/api/v1/notification/messages?userId=1", token=tok)
    check("C8", "通知站内信", 200, st)
    st, j = http("GET", "/api/v1/iam/audit/logs?pageNum=1&pageSize=5", token=tok)
    check("C8", "IAM 审计日志（写操作留痕）", 200, st)
    st, j = http("GET", "/api/v1/system/console/services", token=tok)
    check("C8", "控制台服务健康面", (200, 404), st)


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
               "G": gate}
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
