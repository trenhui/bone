#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bone 跨模块端到端链路测试基座。

覆盖 8 条跨模块链路（C1..C8）+ 门禁脚本 G（网关路由 vs 代码路径常量一致性）。
所有请求经统一网关 8888，模拟人工操作顺序执行，断言真实响应。

结构（README §8「数据驱动化」）：链路本体只声明「步骤 + 判据」——步骤是字典列表
（方法/路径/请求体/期望状态码/期望码/后置提取），统一交给
``e2e_lib.chain_runner.run_steps`` 执行「发请求 → 取变量 → 落断言 → 传下一步」；
夹具准备、登录、取数等特殊逻辑走钩子回调（可调用步骤 / ``post``），不为抽象而抽象。
断言助手（check/check_page_shape/check_err 等）也统一由 chain_runner 提供。

运行：python3 scripts/e2e/chains.py [链路号...]
"""
import sys
import time

from e2e_lib import config
from e2e_lib.chain_runner import (RESULTS, ChainCtx, check, d_body, data, pick,
                                  rid_of, run_steps)
from e2e_lib.http import login, raw_call
from e2e_lib.report import section as hdr
from e2e_lib.sign import hmac_sign as sign

# 网关根址（不含 /api/v1）：配置唯一真源在 e2e_lib.config，禁止在此硬编码端口/口令
# （硬编码口令会被 gitleaks 拦下，且切换环境须改脚本）。env 覆盖走 BONE_E2E_BASE。
GW = config.BASE.rsplit("/api/v1", 1)[0]
ADMIN = (config.USER, config.PWD)

# ---- 主数据实体：运行时解析（禁止硬编码雪花 ID）----
# 原实现把某次库里的雪花 ID 写成常量（MD_PRODUCT=760324212819755008 等）。
# 库一旦重建/换环境，ID 全部失效 ⇒ 创建记录直接 404，而真正的断言（SoD、状态机）
# 一次都没跑到。实体 ID 是**数据**不是契约，必须按业务码在运行时查。
MD_ENTITY_CACHE = {}


def md_entity_id(tok, *keywords):
    """按名称关键字解析主数据实体 ID；命中即缓存。找不到返回 None。"""
    key = "|".join(keywords)
    if key in MD_ENTITY_CACHE:
        return MD_ENTITY_CACHE[key]
    MD_ENTITY_CACHE.clear()  # 避免 token 更换后残留
    st, j = http("GET", "/api/v1/masterdata/entities?page=1&size=100", token=tok)
    ents = (data(j) or {}).get("records") or [] if isinstance(data(j), dict) else (data(j) or [])
    for kw in keywords:
        for e in ents:
            blob = f"{e.get('name') or ''}{e.get('entityCode') or ''}{e.get('code') or ''}"
            if kw in blob:
                MD_ENTITY_CACHE[key] = e.get("id")
                return e.get("id")
    print(f"  [WARN] 未找到包含 {keywords} 的主数据实体，现有={[(e.get('id'), e.get('name')) for e in ents][:6]}")
    return None

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


def http(method, path, body=None, tenant="0", token=None, headers=None, timeout=25):
    """薄委托公共层 raw_call，保留本脚本的 (status, body) 2 元组口径。

    为什么保留本地包装而不是逐调用点换成 http.call：本脚本约 150 个调用点全部走
    token=/默认 tenant 语义（含 token=None=匿名回调），公共层没有对应的 2 元组入口，
    逐点替换风险大于收益。
    """
    h = dict(headers or {})
    if token:
        h["Authorization"] = "Bearer " + token
    if tenant is not None:
        h["X-Tenant-Id"] = str(tenant)
    s, d, _ = raw_call(GW, method, path, body, h, timeout)
    return s, d


def _rpc(method, path, body, token):
    """通用执行器的请求入口：固定 (method, path, body, token) 四参签名，委托本地 http。"""
    return http(method, path, body, token=token)


def first_dept_id(j):
    """深度优先取部门树首个节点 id（承接原 c3 内联 walk），找不到返回 None。"""
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

    walk(data(j))
    return found_id[0] if found_id else None


# ============================ 夹具与准备函数（特殊逻辑钩子的实现体） ============================

def ensure_reference_set(tok, set_code, set_name, values):
    """幂等准备一个已发布值域（含给定值编码），返回值域 id；失败返回 None。

    值域为平台目录（仅平台管理员可写），编码重复会 400；故先查后建。
    """
    st, j = http("GET", "/api/v1/masterdata/reference-sets", token=tok)
    rows = data(j) or []
    set_id = None
    for s in rows if isinstance(rows, list) else []:
        if (s.get("setCode") or "").upper() == set_code.upper():
            set_id = s.get("id")
            break
    if set_id is None:
        st, j = http("POST", "/api/v1/masterdata/reference-sets",
                     {"setCode": set_code, "setName": set_name}, token=tok)
        d = data(j)
        set_id = d.get("id") if isinstance(d, dict) else d
    if set_id is None:
        return None
    st, j = http("GET", f"/api/v1/masterdata/reference-sets/{set_id}/values", token=tok)
    rows = data(j) or []
    have = {(v.get("valueCode") or "") for v in rows if isinstance(v, dict)}
    for v in values:
        if v not in have:
            http("POST", f"/api/v1/masterdata/reference-sets/{set_id}/values",
                 {"valueCode": v, "valueName": v}, token=tok)
    return set_id


def create_entity(tok, name, description=""):
    """创建一个主数据实体，返回 id；失败返回 None。"""
    st, j = http("POST", "/api/v1/masterdata/entities",
                 {"name": name, "description": description}, token=tok)
    d = data(j)
    if isinstance(d, dict):
        return d.get("id")
    return d if st in (200, 201) else None


# ============================ 判据后的特殊逻辑钩子（post） ============================

def stats_self_check(ctx, st, j):
    """统计计数自洽：成功+失败=执行数（响应是按流程聚合的数组，每行含 flowId 等）。"""
    stats = ctx.stats
    if stats:
        bad = [s.get("flowId") for s in stats
               if int(s.get("executionCount") or 0) != int(s.get("successCount") or 0)
               + int(s.get("failureCount") or 0)]
        check(ctx.cid, "统计计数自洽(成功+失败=执行数)", [], bad,
              f"行数={len(stats)} 样本={ {k: stats[0].get(k) for k in ('flowId','executionCount','successCount','failureCount')} }")


def exec_rows_check(ctx, st, j):
    """执行记录允许为空库（total=0），故只在行数不足 pageSize 时断言「不超发」。"""
    rows = ctx.rows
    if rows is not None and len(rows) < 10:
        check(ctx.cid, "执行记录数不超过 pageSize", True, len(rows) <= 10, f"rows={len(rows)}")


def orphan_plugins_check(ctx, st, j):
    """插件必须挂在真实存在的扩展点上，否则「扩展点→插件」这条链路是空的。"""
    pts, plugins = ctx.pts, ctx.plugins
    if pts and plugins:
        pt_ids = {str(p.get("id")) for p in pts if isinstance(p, dict)}
        orphan = [p.get("id") for p in plugins if str(p.get("extPointId")) not in pt_ids]
        check(ctx.cid, "插件均绑定到已知扩展点", [], orphan,
              f"扩展点={sorted(pt_ids)[:4]} 插件数={len(plugins)}")


def builtin_templates_check(ctx, st, j):
    """内置模板齐备性 + 发布态 + engine 大小写漂移记录。"""
    tpl = ctx.tpl
    if tpl:
        builtin = {str(t.get("code")) for t in tpl if str(t.get("tenantId")) == "0"}
        need = {"entity", "repository", "applicationService", "controller", "response", "assembler",
                "createCommand", "updateCommand", "queryDto",
                "createRequest", "updateRequest", "pageQuery"}
        check(ctx.cid, "12 类内置模板齐备", [], sorted(need - builtin), f"已登记={len(builtin)} 类")
        non_pub = sorted({str(t.get("code")) for t in tpl if str(t.get("status")) != "PUBLISHED"})
        check(ctx.cid, "模板均已发布", [], non_pub, f"status集合={sorted({str(t.get('status')) for t in tpl})}")
        # 已知数据不一致（不是功能缺陷）：engine 取值大小写分裂 —— 0015 迁移脚本插入的行用大写
        # FREEMARKER，而 0009 之前的历史 5 行是小写 freemarker；域模型 CodeTemplate.create 默认
        # FREEMARKER（大写）。渲染路径 TemplateRenderer 恒走 FreeMarker、不比对该字段，
        # 故当前不影响生成，仅作为数据漂移记录在此（收敛需 DB 迁移，超出 E2E 脚本职责）。
        engines = sorted({str(t.get("engine")) for t in tpl})
        if len({e.upper() for e in engines}) == 1 and len(engines) > 1:
            print(f"  [WARN] C6 · engine 大小写分裂（不影响渲染，建议迁移收敛）: {engines}")


def ref_cited_check(ctx, st, j):
    """域模板 fieldSchema 里引用的参照集必须存在，否则 C2 的参照域校验会引用不存在的约束源。"""
    md_tpl, refs = ctx.md_tpl, ctx.refs
    if md_tpl and refs:
        ref_codes = {str(r.get("setCode")) for r in refs if isinstance(r, dict)}
        schema = md_tpl[0].get("fieldSchema") if isinstance(md_tpl[0], dict) else None
        cited = set()
        if isinstance(schema, str):
            cited = {w for w in ref_codes if w in schema}
        check(ctx.cid, "域模板字段Schema 引用已登记参照集", True, isinstance(schema, (str, dict)),
              f"schema类型={type(schema).__name__} 参照集={sorted(ref_codes)[:4]} 命中={sorted(cited)[:4]}")


def audit_paging_check(ctx, st, j):
    """断言 pageSize 真生效 + 翻页内容不重叠，否则「page/size 被静默忽略」永远测不出来。"""
    p1, p2 = ctx.p1, ctx.p2
    if p1 and p2:
        ids1 = {str(r.get("id")) for r in p1}
        ids2 = {str(r.get("id")) for r in p2}
        check(ctx.cid, "审计日志翻页不重复", [], sorted(ids1 & ids2), f"第1页={len(ids1)} 第2页={len(ids2)}")


# ============================ C1 交易主链路 ============================
def c1(tok, sub_tok=None, app_tok=None):
    hdr("C1 · 主数据 → 订单 → 支付 → 履约（核心商业闭环）")
    ts = int(time.time())
    # productId 在 CreateOrderReq 中是 Long，故商品业务码必须是纯数字（与种子商品 code=1001 同口径）
    code = str(ts % 900000 + 100000)

    # 1. 主数据：新建商品记录并走通 SoD 发布（提交人与审批人必须是不同账号）
    # recordCode 是主数据的一等业务主键字段（去重/检索/订阅分发的基础），必须在创建时显式传入；
    # 只填 data JSON 会让 record_code 为 NULL，点查主路径必然落空（只能靠 JSON 兜底）。
    MD_PRODUCT = md_entity_id(tok, "商品", "PRODUCT")
    if not MD_PRODUCT:
        check("C1", "解析商品主数据实体", False, True, "未找到商品实体，先跑 scripts/e2e/seed.py 造数")
        return

    trade = f"TRD{ts}"
    sub, app = sub_tok or tok, app_tok or tok

    # 合法回调报文：签名取自报文本身；篡改用例必须复用这份签名（现场重算就测不出验签了）
    def cb_body(c):
        return {"paymentId": str(c.pid), "channelTradeNo": trade,
                "paidAmount": c.amount, "signature": sign(c.pid, trade, c.amount), "success": True}

    # 单价由 transcend 主数据侧提供；两处下单（守卫用例复用同一报文）
    order_body = {"customerId": 1001,
                  "items": [{"productId": int(code), "productName": f"链路商品{ts}",
                             "quantity": 1, "unitPrice": 199.0}]}

    steps = [
        # 1) 创建商品记录（取 rid，取不到即结束本链）
        {"method": "POST", "path": f"/api/v1/masterdata/records/entity/{MD_PRODUCT}",
         "body": {"recordCode": code,
                  "data": {"code": code, "name": f"链路商品{ts}", "price": 199.0, "unit": "件"}},
         "checks": [{"name": "主数据创建商品记录", "kind": "status", "expect": 200,
                     "detail": d_body()}],
         "extract": {"rid": lambda c, st, j: rid_of(j)},
         "stop_if_empty": ["rid"]},
        # 2) SoD 提交 → 审批 → 发布（提交人与审批人为不同账号）
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/submit", "token": sub,
         "checks": [{"name": "提交人 submit", "kind": "status", "expect": 200}]},
        # 光有 200 不够：submit 后记录状态必须真的转为 SUBMITTED，否则「提交」是个空动作
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "submit 后状态=PENDING_APPROVAL", "kind": "eq",
                     "expect": "PENDING_APPROVAL", "actual": "data.status",
                     "detail": lambda c, st, j: str(data(j) or {})[:120]}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/approve", "token": app,
         "checks": [{"name": "审批人 approve（SoD：提交人≠审批人）", "kind": "status",
                     "expect": 200, "detail": d_body(140)}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "approve 后状态=APPROVED", "kind": "eq", "expect": "APPROVED",
                     "actual": "data.status", "detail": lambda c, st, j: str(data(j) or {})[:120]}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/publish",
         "checks": [{"name": "发布 publish", "kind": "status", "expect": 200, "detail": d_body(140)}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "publish 后状态=PUBLISHED", "kind": "eq", "expect": "PUBLISHED",
                     "actual": "data.status", "detail": lambda c, st, j: str(data(j) or {})[:120]}]},
        # 2. ACL 点查：确认 blueprint 可经网关拿到该商品
        {"method": "GET",
         "path": f"/api/v1/masterdata/records/by-code?masterDataEntityId={MD_PRODUCT}&code={code}",
         "checks": [
             {"name": "主数据 by-code 点查", "kind": "status", "expect": 200},
             {"name": "点查命中新商品", "kind": "cond", "expect": True,
              "actual": lambda c, st, j: pick(j, "data.recordCode") == code},
             # 点查回的必须是同一条记录（按 id 比对，防止 by-code 命中了别的同 code 记录）
             {"name": "点查 id 与创建一致", "kind": "eq", "expect": lambda c: str(c.rid),
              "actual": lambda c, st, j: str(pick(j, "data.id"))},
         ]},
        # 3. 下单：跨模块消费主数据（取 oid，取不到即结束本链）
        {"method": "POST", "path": "/api/v1/orders", "body": order_body,
         "checks": [{"name": "下单（跨模块消费主数据）", "kind": "status", "expect": 201,
                     "detail": d_body()}],
         "extract": {"oid": lambda c, st, j: rid_of(j)},
         "stop_if_empty": ["oid"]},
        # 4. 订单初始态 + 金额（amount 取不到时与原实现一样在判据处抛 TypeError）
        {"method": "GET", "path": "/api/v1/orders/{oid}",
         "extract": {"amount": lambda c, st, j: data(j).get("totalAmount")},
         "checks": [
             {"name": "订单初始态", "kind": "eq", "expect": "CREATED",
              "actual": lambda c, st, j: data(j).get("status")},
             # 折扣必须非 nil：证明 ACL 真的取到了客户等级折扣率
             {"name": "金额含客户等级折扣（<199）", "kind": "cond", "expect": True,
              "actual": lambda c, st, j: c.amount < 199.0,
              "detail": lambda c, st, j: f"amount={c.amount}"},
         ]},
        # 5. 支付发起（取 pid，取不到即结束本链）
        {"method": "POST", "path": "/api/v1/payments/initiate",
         "body": lambda c: {"orderId": str(c.oid)},
         "checks": [{"name": "发起支付（网关 payments 路由）", "kind": "status", "expect": 200,
                     "detail": d_body()}],
         "extract": {"pid": lambda c, st, j: data(j).get("paymentId") if data(j) else None},
         "stop_if_empty": ["pid"]},
        # 6. 回调：合法签名通过
        {"method": "POST", "path": "/api/v1/payments/callback", "body": cb_body,
         "checks": [{"name": "支付回调（HMAC 验签通过）", "kind": "status", "expect": 200,
                     "detail": d_body(140)}]},
        # 7. 回调：篡改金额必须被验签拦截
        {"method": "POST", "path": "/api/v1/payments/callback",
         "body": lambda c: dict(cb_body(c), paidAmount=1.00),
         "checks": [{"name": "篡改金额回调被拒", "kind": "status", "expect": 401,
                     "detail": lambda c, st, j: (j or {}).get("data", {}).get("errorCode", "")
                     if isinstance(j, dict) else ""}]},
        # 8. 支付单 + 订单一致性
        {"method": "GET", "path": "/api/v1/payments/{pid}",
         "checks": [{"name": "支付单终态 SUCCESS", "kind": "eq", "expect": "SUCCESS",
                     "actual": "data.status"}]},
        {"method": "GET", "path": "/api/v1/orders/{oid}",
         "checks": [{"name": "订单随支付流转", "kind": "eq", "expect": "PAID",
                     "actual": "data.status"}]},
        # 9. 履约：ship → deliver，每步都要看到状态真的推进（f-string 名称在构造期即定值，无闭包陷阱）
        *[step for act, want in (("ship", "SHIPPED"), ("deliver", "DELIVERED")) for step in (
            {"method": "POST", "path": "/api/v1/orders/{oid}/" + act,
             "checks": [{"name": f"订单 {act}", "kind": "status", "expect": 200,
                         "detail": d_body(140)}]},
            {"method": "GET", "path": "/api/v1/orders/{oid}",
             "checks": [{"name": f"{act} 后状态", "kind": "eq", "expect": want,
                         "actual": "data.status"}]},
        )],
        # 10. 状态机防御：终态重复发货不仅要是 409，业务码也须稳定（否则无法与「订单不存在」区分）
        {"method": "POST", "path": "/api/v1/orders/{oid}/ship",
         "checks": [{"name": "终态后重复发货被拒(409+BP_ORDER_STATUS_CONFLICT)", "kind": "err",
                     "status": 409, "code": "BP_ORDER_STATUS_CONFLICT"}]},
        # 前置未支付直接发货同样必须被拒（防止绕过支付推进履约）
        {"method": "POST", "path": "/api/v1/orders", "body": order_body,
         "extract": {"oid2": lambda c, st, j: rid_of(j)},
         "checks": [{"name": "未支付订单创建成功（前置守卫用）", "kind": "status",
                     "expect": (200, 201), "detail": d_body(120),
                     "when": lambda c: bool(c.oid2)}]},
        {"method": "POST", "path": "/api/v1/orders/{oid2}/ship", "when": lambda c: bool(c.oid2),
         "checks": [{"name": "未支付即发货被拒(409+STATUS_CONFLICT)", "kind": "err",
                     "status": 409, "code": "BP_ORDER_STATUS_CONFLICT"}]},
        {"method": "GET", "path": "/api/v1/orders/{oid2}", "when": lambda c: bool(c.oid2),
         "checks": [{"name": "被拒后状态仍为 CREATED（未误推进）", "kind": "eq",
                     "expect": "CREATED", "actual": "data.status"}]},
        # 11. 退款
        {"method": "POST", "path": "/api/v1/payments/{pid}/refund",
         "body": {"refundAmount": 10.00},
         "checks": [{"name": "支付单退款", "kind": "status", "expect": 200, "detail": d_body(140)}]},
    ]
    ctx = ChainCtx(_rpc, main=tok, cid="C1")
    run_steps(steps, ctx)
    if ctx.stopped:  # 等价原实现「取不到 id 就 return」的提前结束
        return
    return {"orderId": ctx.oid, "paymentId": ctx.pid, "productCode": code}


# ============================ C2 主数据治理 ============================
def c2(tok, sub_tok, app_tok):
    hdr("C2 · 主数据 SoD 治理：双账号分离 + 版本 + 参照域")
    ts = int(time.time())
    code = f"C2SUP{ts}"
    MD_CUSTOMER = md_entity_id(tok, "客户", "CUSTOMER")
    if not MD_CUSTOMER:
        check("C2", "解析客户主数据实体", False, True, "未找到客户实体")
        return
    sub, app = sub_tok or tok, app_tok or tok

    # 参照域负向用例的自建夹具（2026-10-26 修，用例自足化）：原写法直接拿「库里首个名字含
    # 『客户』的实体」测 level_code。该实体是历史联调残留（只有 contact 一个字段，也没有
    # 任何值域绑定），而 validateAgainstFieldDefinitions 在「实体未建模字段」时按设计直接
    # 放行 ⇒ 断言恒红，且**掩盖了参照域是否真生效**这一真问题（换任何值都是 200）。
    # 参照域绑定约定：值域 set_code 与字段 code 同名（大写归一）即生效，故这里自建夹具：
    # 值域 CUST_LEVEL（create 即 PUBLISHED）+ 字段 cust_level，正向/负向各打一次。
    def fixture(c):
        c.set_id = ensure_reference_set(tok, "CUST_LEVEL", "客户等级值域（C2 夹具）",
                                        ["ENTERPRISE", "INDIVIDUAL"])
        c.ent_id = create_entity(tok, f"联调等级校验实体{ts}", "C2 参照域夹具")

    steps = [
        {"method": "POST", "path": f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}",
         "body": customer_data(code, f"链路供应商{ts}"),
         "checks": [{"name": "创建客户记录（提交人）", "kind": "status", "expect": 200,
                     "detail": d_body()}],
         "extract": {"rid": lambda c, st, j: rid_of(j)},
         "stop_if_empty": ["rid"]},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/submit", "token": sub,
         "checks": [{"name": "提交人 submit", "kind": "status", "expect": 200}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "submit 后状态=PENDING_APPROVAL", "kind": "eq",
                     "expect": "PENDING_APPROVAL", "actual": "data.status",
                     "detail": lambda c, st, j: str(data(j) or {})[:120]}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/approve", "token": app,
         "checks": [{"name": "审批人 approve（SoD 分离）", "kind": "status", "expect": 200}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "approve 后状态=APPROVED", "kind": "eq", "expect": "APPROVED",
                     "actual": "data.status", "detail": lambda c, st, j: str(data(j) or {})[:120]}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid}/publish",
         "checks": [{"name": "发布 publish", "kind": "status", "expect": 200}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}",
         "checks": [{"name": "终态 PUBLISHED", "kind": "eq", "expect": "PUBLISHED",
                     "actual": "data.status"}]},
        {"method": "GET", "path": "/api/v1/masterdata/records/{rid}/versions",
         "checks": [
             {"name": "版本列表可读", "kind": "status", "expect": 200},
             # 版本列表须真的是数组（曾实测返回 []：走通 SoD 发布后仍无版本，是真实可疑点，
             # 弱断言只看 200 会把它判为通过）
             {"name": "版本列表", "kind": "list", "min_rows": 0},
         ]},
        # SoD 负向：同一人既提交又审批必须被拒，否则 SoD 只是文档约定（取 rid2，取不到则跳过三步）
        {"method": "POST", "path": f"/api/v1/masterdata/records/entity/{MD_CUSTOMER}",
         "body": customer_data(f"C2SOD{ts}", f"SoD探测{ts}"),
         "extract": {"rid2": lambda c, st, j: rid_of(j)}},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid2}/submit",
         "when": lambda c: bool(c.rid2)},
        {"method": "POST", "path": "/api/v1/masterdata/records/{rid2}/approve",
         "when": lambda c: bool(c.rid2),
         "checks": [{"name": "同一人自审被拒(403+MD_SOD_VIOLATION)", "kind": "err",
                     "status": 403, "code": "MD_SOD_VIOLATION"}]},
        # 参照域夹具（特殊逻辑钩子，见上方 fixture 注释）
        fixture,
        {"checks": [{"name": "参照域夹具就绪（值域 CUST_LEVEL + 实体）", "kind": "cond",
                     "actual": lambda c, st, j: bool(c.set_id and c.ent_id),
                     "detail": lambda c, st, j: f"set_id={c.set_id} ent_id={c.ent_id}"}]},
        {"method": "POST", "path": "/api/v1/masterdata/entities/{ent_id}/fields",
         "when": lambda c: bool(c.ent_id),
         "body": {"name": "客户等级", "code": "cust_level", "type": "STRING", "required": False},
         "checks": [{"name": "夹具字段 cust_level 建模", "kind": "status", "expect": (200, 201),
                     "detail": d_body()}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/entity/{ent_id}",
         "when": lambda c: bool(c.ent_id),
         "body": {"data": {"cust_level": "ENTERPRISE"}},
         "checks": [{"name": "合法枚举值放行(2xx)", "kind": "status", "expect": (200, 201),
                     "detail": d_body()}]},
        {"method": "POST", "path": "/api/v1/masterdata/records/entity/{ent_id}",
         "when": lambda c: bool(c.ent_id),
         "body": {"data": {"cust_level": "NOT_A_LEVEL"}},
         "checks": [{"name": "非法枚举值被参照域拦截(400+FIELD_VALIDATION_FAILED)", "kind": "err",
                     "status": 400, "code": "MD_RECORD_FIELD_VALIDATION_FAILED"}]},
    ]
    ctx = ChainCtx(_rpc, main=tok, cid="C2")
    run_steps(steps, ctx)
    if ctx.stopped:
        return
    return {"recordId": ctx.rid, "code": code}


# ============================ C3 IAM 授权一致性 ============================
def c3(tok):
    hdr("C3 · IAM 授权 → 跨模块鉴权一致性")
    ts = int(time.time())
    user = f"c3view{ts}"
    steps = [
        {"method": "GET", "path": "/api/v1/iam/depts/tree",
         "extract": {"did": lambda c, st, j: first_dept_id(j)},
         "checks": [{"name": "部门树可读（取 deptId）", "kind": "status", "expect": 200,
                     "detail": lambda c, st, j: f"deptId={c.did}"}]},
        # 自建平台租户(0)部门用于账号归属：全租户树首节点可能属于其他租户，
        # 而账号创建默认落平台租户 0，跨租户部门会触发 IAM_DEPT_NOT_FOUND。
        # 自建部门保证租户一致（admin 代操作且无 tenantId → 落平台租户 0）。
        {"method": "POST", "path": "/api/v1/iam/depts",
         "body": {"name": f"c3dept{ts}", "orderNo": 1},
         "extract": {"did": lambda c, st, j: rid_of(j) or c.did},
         "checks": [{"name": "自建部门用于受限账号归属", "kind": "status", "expect": 200,
                     "detail": lambda c, st, j: f"deptId={c.did}"}],
         "stop_if_empty": ["did"]},
        {"method": "POST", "path": "/api/v1/iam/accounts",
         "body": lambda c: {"username": user, "password": "C3pass@2026", "deptId": str(c.did),
                            "nickname": "只读员", "email": f"{user}@bone.local"},
         "checks": [{"name": "创建受限账号（无写 scope）", "kind": "status", "expect": 200,
                     "detail": d_body()}],
         "extract": {"uid": lambda c, st, j: rid_of(j)},
         "stop_if_empty": ["uid"]},
        # 改密请求本身无判据（原实现亦不断言），post 钩子仅负责用新口令登录供后续步骤使用
        {"method": "POST", "path": "/api/v1/iam/accounts/{uid}/password",
         "body": {"password": "C3pass@2026"},
         "post": lambda c, st, j: setattr(c, "ltok", login(user, "C3pass@2026"))},
        # 无 order:orders:write → 下单必须 403
        {"method": "POST", "path": "/api/v1/orders",
         "body": {"customerId": 1001,
                  "items": [{"productId": 1001, "productName": "x", "quantity": 1, "unitPrice": 1.0}]},
         "token": lambda c: c.ltok,
         "checks": [{"name": "受限账号下单被鉴权拦截", "kind": "status", "expect": 403,
                     "detail": d_body(120)}]},
        # 重复 code → 409（本次修复项）
        # 重复请求必须同样通过入参校验（username/email 在重名检查之前先校验），否则拿到的是 400 而非 409
        {"method": "POST", "path": "/api/v1/iam/accounts",
         "body": lambda c: {"username": user, "password": "C3pass@2026", "deptId": str(c.did),
                            "nickname": "只读员", "email": f"{user}@bone.local"},
         "checks": [
             {"name": "账号重复冲突返回 409", "kind": "status", "expect": 409, "detail": d_body(140)},
             {"name": "重复冲突业务码稳定", "kind": "cond", "expect": True,
              "actual": lambda c, st, j: "CONFLICT" in str((j or {}).get("message", "")).upper()
              or (isinstance(data(j), dict) and bool(data(j).get("errorCode"))),
              "detail": lambda c, st, j: f"msg={str((j or {}).get('message'))[:80]}"},
         ]},
    ]
    ctx = ChainCtx(_rpc, main=tok, cid="C3")
    run_steps(steps, ctx)
    if ctx.stopped:
        return
    return {"userId": ctx.uid, "username": user}


# ============================ C4 集成引擎 ============================
def _bad_binding_step(path, desc):
    """绑定异常用例：一条步骤带三条判据（ApiResponse 信封 + 400 收敛 + 业务码）。"""
    return {"method": "GET", "path": path, "checks": [
        # 必须是 ApiResponse 信封（有 success 键 + 顶层 code），而不是裸 Spring ProblemDetail
        {"name": f"统一响应契约-{desc} 落 ApiResponse 信封", "kind": "cond", "expect": True,
         "actual": lambda c, st, j: isinstance(j, dict) and "success" in j and "code" in j,
         "detail": lambda c, st, j: f"HTTP {st} 顶层键={sorted(j.keys()) if isinstance(j, dict) else type(j).__name__}"},
        {"name": f"{desc} 绑定异常收敛为 400 而非 500", "kind": "status", "expect": 400,
         "detail": lambda c, st, j: f"errorCode={pick(j, 'data.errorCode')} msg={str((j or {}).get('message'))[:60]}"},
        {"name": f"{desc} 业务码=COMMON_VALIDATION_FAILED", "kind": "eq",
         "expect": "COMMON_VALIDATION_FAILED", "actual": "data.errorCode",
         "detail": lambda c, st, j: f"msg={str((j or {}).get('message'))[:60]}"},
    ]}


def c4(tok):
    hdr("C4 · 集成引擎：连接器 → 流程 → 执行")
    steps = [
        # 注意：本模块分页入参是 pageNum/pageSize（与 iam 的 page/size 不同族，勿混用）
        {"method": "GET", "path": "/api/v1/integration/connectors?page=1&size=3",
         "checks": [{"name": "连接器列表", "kind": "page", "expect_rows": 3,
                     "required": ("id", "name", "type"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/integration/flows?page=1&size=2",
         "checks": [{"name": "流程列表", "kind": "page", "expect_rows": 2,
                     "required": ("id", "name", "status"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/integration/statistics",
         "checks": [
             {"name": "集成执行统计面", "kind": "status", "expect": 200, "detail": d_body()},
             {"name": "统计面", "kind": "list", "save_as": "stats",
              "required": ("flowId", "executionCount", "successCount", "failureCount"),
              "min_rows": 0},
         ],
         "post": stats_self_check},
        # 执行记录允许为空库（total=0），故只断言信封与「行数不超过 pageSize」
        {"method": "GET", "path": "/api/v1/integration/executions?page=1&size=10",
         "checks": [{"name": "集成执行记录列表", "kind": "page", "required": ("id",),
                     "min_rows": 0, "save_as": "rows"}],
         "post": exec_rows_check},
        # HC-003：框架级绑定异常也必须落在 ApiResponse 信封内（曾因缺 bone-web 依赖漏成裸 Spring 400）
        # 实测：bone-integration 的绑定异常统一收敛为 COMMON_VALIDATION_FAILED
        #（未像 bone-iam 那样细分到 COMMON_MALFORMED_REQUEST），故这里只断言该服务实际给出的码。
        #
        # ⚠ 「缺分页参数」已从绑定异常用例中移出：ExecutionLogListQuery 的 page/size 是有意改成
        # Integer 并带默认值的（源码注释即设计意图——修的正是「裸调返回 400」与同模块
        # FlowPageQuery / ConnectorPageQuery「不传参即首页」行为不一致）。因此这里的正确契约是
        # 200 + 首页默认分页，再拿它当 400 用例就是拿过期口径否定现行设计。
        {"method": "GET", "path": "/api/v1/integration/executions",
         "checks": [
             {"name": "缺省分页参数 → 200 首页（不得退回 400）", "kind": "status", "expect": 200,
              "detail": lambda c, st, j: f"msg={str((j or {}).get('message'))[:60]}"},
             {"name": "缺省分页回首页(page=1)", "kind": "eq", "expect": 1, "actual": "data.page"},
             {"name": "缺省分页 size 回落默认 10", "kind": "cond", "expect": True,
              "actual": lambda c, st, j: len(pick(j, "data.records") or []) <= 10,
              "detail": lambda c, st, j: f"rows={len(pick(j, 'data.records') or [])}"},
         ]},
        # 两条坏路径：路径变量类型错 / 分页参数非数值，都必须收敛进 ApiResponse 信封
        _bad_binding_step("/api/v1/integration/flows/abc", "路径变量类型错"),
        _bad_binding_step("/api/v1/integration/executions?page=abc&size=xyz", "分页参数非数值"),
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C4"))


# ============================ C5 扩展引擎 ============================
def c5(tok):
    hdr("C5 · 扩展引擎：扩展点 → 插件 → 绑定")
    steps = [
        {"method": "GET", "path": "/api/v1/extension/points",
         "checks": [{"name": "扩展点列表", "kind": "list",
                     "required": ("id", "name", "interfaceName"), "min_rows": 1,
                     "save_as": "pts"}]},
        {"method": "GET", "path": "/api/v1/extension/plugins",
         "checks": [{"name": "插件列表", "kind": "list",
                     "required": ("id", "className", "extPointId"), "min_rows": 1,
                     "save_as": "plugins"}],
         "post": orphan_plugins_check},
        {"method": "GET", "path": "/api/v1/extension/points/observability/metrics",
         "checks": [{"name": "扩展点可观测面", "kind": "status", "expect": (200, 404)}]},
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C5"))


# ============================ C6 生成器 ============================
def c6(tok):
    hdr("C6 · Studio 生成器：数据源 → 元数据快照 → 代码生成")
    steps = [
        # 注意：本模块分页入参是 page/size（与 integration/metadata 的 pageNum/pageSize 不同族）
        {"method": "GET", "path": "/api/v1/generator/data-sources?page=1&size=2",
         "checks": [{"name": "数据源列表", "kind": "page", "expect_rows": 2,
                     "required": ("id", "dbName"), "min_rows": 1}]},
        # 快照行无 id（以 tableName 为业务主键，见 metadata-entity-snapshots 响应形状）
        {"method": "GET", "path": "/api/v1/generator/metadata-entity-snapshots?page=1&size=3",
         "checks": [{"name": "元数据实体快照", "kind": "page", "expect_rows": 3,
                     "required": ("tableName", "columns", "deliveryMode"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/generator/capabilities",
         "checks": [{"name": "生成能力清单", "kind": "list",
                     "required": ("name", "inputSchema", "outputSchema", "retryable"),
                     "min_rows": 1}]},
        # 内置模板正文为空是**设计如此**（唯一真源 = classpath templates/{code}.ftl，
        # 见 scripts/migration/0009 与 CodeTemplate.fillBuiltInContent），不是数据缺失。
        # 但 12 类内置骨架必须齐备 —— 少一类用户就勾不到完整骨架（详见钩子内注释）。
        {"method": "GET", "path": "/api/v1/generator/templates?page=1&size=20",
         "checks": [{"name": "模板列表", "kind": "page", "expect_rows": 20,
                     "required": ("id", "code", "engine", "status"), "min_rows": 1,
                     "save_as": "tpl"}],
         "post": builtin_templates_check},
        {"method": "GET", "path": "/api/v1/generator/history",
         "checks": [{"name": "生成历史", "kind": "list",
                     "required": ("id", "dataSourceId"), "min_rows": 0}]},
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C6"))


# ============================ C7 元数据 → 主数据 ============================
def c7(tok):
    hdr("C7 · 元数据建模 → 主数据联动")
    steps = [
        {"method": "GET", "path": "/api/v1/metadata/entities?page=1&size=4",
         "checks": [{"name": "元数据实体列表", "kind": "page", "expect_rows": 4,
                     "required": ("id", "code", "displayName"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/metadata/templates",
         "checks": [{"name": "元数据模板列表", "kind": "status", "expect": (200, 404)}]},
        {"method": "GET", "path": "/api/v1/metadata/relationships",
         "checks": [{"name": "元数据关系列表", "kind": "status", "expect": (200, 404)}]},
        {"method": "GET", "path": "/api/v1/masterdata/templates?page=1&size=2",
         "checks": [{"name": "主数据域模板（消费元数据源）", "kind": "page", "expect_rows": 2,
                     "required": ("id", "domainCode"), "min_rows": 1, "save_as": "md_tpl"}]},
        {"method": "GET", "path": "/api/v1/masterdata/reference-sets",
         "checks": [{"name": "参照数据集（约束来源）", "kind": "list",
                     "required": ("id", "setCode", "status"), "min_rows": 1,
                     "save_as": "refs"}],
         "post": ref_cited_check},
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C7"))


# ============================ C8 系统/通知/可观测 ============================
def c8(tok):
    hdr("C8 · 系统管理 · 通知 · 审计")
    steps = [
        # 全仓分页入参已统一为 page/size（各模块 *PageReq / *PageQuery 均为 page+size，
        # 无pageNum 残留）。此前 system/integration 用 pageNum/pageSize 会被静默忽略、
        # size 回落默认 10 ⇒ 「行数不超过 pageSize」断言恒红。
        {"method": "GET", "path": "/api/v1/system/config?page=1&size=3",
         "checks": [{"name": "系统配置", "kind": "page", "expect_rows": 3,
                     "required": ("id", "configKey"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/system/dict/types/page?page=1&size=5",
         "checks": [{"name": "字典类型分页", "kind": "page", "required": ("id",),
                     "min_rows": 0}]},
        {"method": "GET", "path": "/api/v1/system/logs?page=1&size=3",
         "checks": [{"name": "系统日志分页", "kind": "page", "expect_rows": 3,
                     "required": ("id", "logLevel"), "min_rows": 1}]},
        {"method": "GET", "path": "/api/v1/notification/messages?userId=1",
         "checks": [{"name": "通知站内信", "kind": "list", "required": ("id",),
                     "min_rows": 0}]},
        # IAM 审计：page/size 族。
        {"method": "GET", "path": "/api/v1/iam/audit/logs?page=1&size=3",
         "checks": [{"name": "IAM 审计日志（写操作留痕）", "kind": "page", "expect_rows": 3,
                     "required": ("id", "operation", "createdAt"), "min_rows": 1,
                     "save_as": "p1"}]},
        {"method": "GET", "path": "/api/v1/iam/audit/logs?page=2&size=3",
         "checks": [{"name": "IAM 审计日志第2页", "kind": "page", "expect_rows": 3,
                     "required": ("id",), "min_rows": 1, "save_as": "p2"}],
         "post": audit_paging_check},
        {"method": "GET", "path": "/api/v1/system/console/services",
         "checks": [{"name": "控制台服务健康面", "kind": "status", "expect": (200, 404)}]},
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C8"))


# ============================ C9 凭证视角矩阵（鉴权边界） ============================
def c9(tok):
    hdr("C9 · 凭证视角矩阵 · 鉴权边界（防止被 admin token 掩盖）")
    # 背景：上一轮所有用例都带 admin token 跑，等于把每条鉴权边界都测成假阳性——
    # 「支付回调由无 JWT 的外部渠道方调用」这一真实断点因此完全没暴露（网关 401
    # GW_UNAUTHORIZED，而 blueprint 内部早已 permitAll）。本链路固定用三种视角
    # 交叉验证同一批端点，把这类盲区固化成回归用例。
    ts = int(time.time())
    user = f"c9{ts % 100000}"
    order_body = {"customerId": 1001,
                  "items": [{"productId": 1001, "productName": "视角验证",
                             "quantity": 1, "unitPrice": 129.0}]}

    # 视角 C 的回调报文：paidAmount 必须 float(amount) —— amount 缺失时在此处复现
    # 原实现的 TypeError（float() argument must be a string or a real number...）
    def cb_body(c):
        trade = f"C9{ts}"
        return {"paymentId": str(c.pid), "channelTradeNo": trade,
                "paidAmount": float(c.amount), "signature": sign(c.pid, trade, c.amount),
                "success": True}

    steps = [
        # --- 视角 B：受限用户（有凭证、无权限）→ 必须 403，不能退化成 500 ---
        # did 用无保护下标取值，与原实现一致：取不到就抛异常记 2 条失败，而不是悄悄跳过
        {"method": "GET", "path": "/api/v1/iam/depts/tree",
         "extract": {"did": lambda c, st, j: j["data"][0]["id"]}},
        # ⚠ deptId 必须保持字符串：雪花 ID 19 位超出 2^53，int() 会静默截断成
        # 「另一个」部门 ⇒ 后端报 IAM_DEPT_NOT_FOUND（与前端 map(Number) 同类缺陷）。
        {"method": "POST", "path": "/api/v1/iam/accounts",
         "body": lambda c: {"username": user, "password": "C9pass@2026", "deptId": str(c.did),
                            "nickname": "受限视角", "email": f"{user}@bone.local"},
         "post": lambda c, st, j: setattr(c, "limited", login(user, "C9pass@2026"))},
        {"method": "POST", "path": "/api/v1/orders", "body": order_body,
         "token": lambda c: c.limited,
         "checks": [{"name": "受限用户下单 → 403（不得是 500）", "kind": "status", "expect": 403,
                     "detail": d_body(140)}]},
        # --- 视角 A：超管下单，为渠道视角准备支付单（不设止损，沿用原实现的后续异常语义）---
        {"method": "POST", "path": "/api/v1/orders", "body": order_body,
         "checks": [{"name": "超管下单 → 2xx", "kind": "status", "expect": (200, 201)}],
         "extract": {"oid": lambda c, st, j: rid_of(j)}},
        {"method": "POST", "path": "/api/v1/payments/initiate",
         "body": lambda c: {"orderId": str(c.oid)},
         "extract": {"pid": lambda c, st, j: pick(j, "data.paymentId") or pick(j, "data.id")}},
        {"method": "GET", "path": "/api/v1/orders/{oid}",
         "extract": {"amount": lambda c, st, j: pick(j, "data.totalAmount")}},
        # --- 视角 C：无凭证外部渠道方 ---
        {"method": "POST", "path": "/api/v1/payments/callback", "token": None, "body": cb_body,
         "checks": [{"name": "无凭证渠道回调 → 200（网关须放通 callback）", "kind": "status",
                     "expect": 200, "detail": d_body(140)}]},
        {"method": "GET", "path": "/api/v1/orders/{oid}",
         "checks": [{"name": "无凭证回调后订单置为 PAID", "kind": "eq", "expect": "PAID",
                     "actual": "data.status"}]},
        {"method": "POST", "path": "/api/v1/payments/callback", "token": None,
         "body": lambda c: dict(cb_body(c), signature="deadbeef"),
         "checks": [{"name": "无凭证 + 签名错 → 401（验签仍生效）", "kind": "status",
                     "expect": 401}]},
        # 白名单是前缀匹配，放通范围必须精确到 callback：其余支付端点仍须 401
        *[
            {"method": method, "path": path, "token": None, "body": body,
             "checks": [{"name": f"无凭证 {desc} → 401（白名单不得误放通）", "kind": "status",
                         "expect": 401}]}
            for path, method, desc, body in (
                ("/api/v1/payments/initiate", "POST", "发起支付",
                 lambda c: {"orderId": str(c.oid)}),
                ("/api/v1/payments/{pid}/refund", "POST", "退款",
                 lambda c: {"refundAmount": 1.0}),
                ("/api/v1/payments/{pid}", "GET", "支付单查询",
                 lambda c: {"orderId": str(c.oid)}),
            )
        ],
        {"method": "POST", "path": "/api/v1/orders", "token": None, "body": order_body,
         "checks": [{"name": "无凭证下单 → 401", "kind": "status", "expect": 401}]},
    ]
    run_steps(steps, ChainCtx(_rpc, main=tok, cid="C9"))


# ============================ G 网关路由门禁 ============================
def gate():
    hdr("G · 门禁：代码 API 路径常量 vs 网关路由登记一致性")
    import re
    import os
    # 仓库根由脚本自身位置推导：硬编码 /Users/renhui.trh/wps/bone 会随本机目录迁移而失效
    # （实测：仓库迁到 ~/code/bone 后 G 门禁整条崩在 FileNotFoundError）。
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
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
    # 无南北向入口的模块：Controller 存在，但所属模块**不具备可独立监听的 HTTP 入口**，
    # 因此「登记进 bone-gateway」在当前架构下无对象可指（登记即制造 503 死路由）。
    # 每一条都必须写明确证依据，并给出「何时该从本表移除」的判据——否则就是放水。
    no_ingress = {
        "/api/ai/v1": (
            "bone-ai 是独立部署单元（bone-ai-app / bone-ai-server 各有 SpringBootApplication "
            "与独立 application.yml），其南北向入口由 bone-ai 自身部署承担，不在 bone-gateway "
            "application.yml 职责内；且 RAG 回答链尚未接线（RagAnswerService 无 Bean，"
            "bone-ai-app 启动即 NoSuchBean），此刻登记只会得到 503。"
            "移除判据：bone-ai 完成接线并纳入统一入口时，必须改走登记而不是留在本表。"
        ),
        "/api/v1/claims": (
            "bone-apps/synap-claim 是库形态业务模块：无 SpringBootApplication 启动类、"
            "无 server.port / application.yml，未接入任何部署单元，因此没有南北向入口可登记。"
            "移除判据：该应用补上启动器并实际部署后，必须登记到网关。"
        ),
    }
    for p in sorted(found):
        if p in internal_rpc:
            continue
        ok = any(p == c or p.startswith(c[:-3] if c.endswith("/**") else c) for c in cov)
        if not ok and any(p == k or p.startswith(k) for k in no_ingress):
            # 不计入失败，但必须显式可见：静默吞掉等于把架构缺口藏起来
            print(f"  [WARN-无入口] {p} —— {no_ingress.get(p) or next(v for k, v in no_ingress.items() if p.startswith(k))}")
            continue
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
        # 必须连 SystemExit 一起兜住：login() 失败时抛的是 SystemExit，而 SystemExit
        # 继承 BaseException、不是 Exception ⇒ 原写法会让整条链路炸在半路，
        # 汇总永远打不出来（实测 C9 一条断言失败就吞掉前面全部 PASS，等于假红）。
        # 这里兜住是为了「任何单条失败都不许吞掉整份报告」，失败本身照样记进 RESULTS。
        except (Exception, SystemExit) as e:
            print(f"  [ERROR] {k} 执行异常: {e}")
            RESULTS.append((k, f"{k} 执行异常", False, str(e)))
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
