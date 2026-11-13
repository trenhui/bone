# -*- coding: utf-8 -*-
"""企业级业务场景造数 —— 在 fixtures 之上组合出「一条讲得通的业务故事」。

为什么要有这一层
----------------
fixtures 提供的是**原子值**（一个人名、一个单号、一笔金额）。真实联调要的是
**一组互相引用的业务对象**：客户 → 销售订单 → 发货单 → 理赔单；金额守恒、
日期单调、审批人 ≠ 提交人。此前每个 agent 各写一遍拼装逻辑，字段名/口径/引用
关系全不同，既不能复用，也无法判定「这批数据是否合理」。

本模块把拼装固化成 4 个场景（见 SCENARIOS），每个场景：
  * 只用 fixtures 的受控词表与守恒算法，不引入新的随机源；
  * 同 seed 产出完全相同的数据（可复跑、可断言、可回归）；
  * 自带 invariants（金额守恒 / 日期单调 / SoD / 引用完整），机器可校验；
  * 输出纯 JSON，既能直接喂 API，也能 `datagen.py --format md` 给人看。

新增场景的步骤：写一个 `_build_xxx(ds) -> dict`，登记进 SCENARIOS，并在
`scripts/tests/test_e2e_datagen.py` 补不变量断言——没有断言的新场景等于没有契约。
"""
from . import fixtures as fx

# 租户口径：平台全局 = 0；主租户与子公司租户固定，便于跨场景串同一集团
TENANT_PLATFORM = 0
TENANT_GROUP = 1001
TENANT_SUBSIDIARY = 1002

# 状态取值对齐代码枚举（零幽灵状态，见 seed/uat/gen.py 红线 H）
ORDER_STATUS = ["DRAFT", "SUBMITTED", "APPROVED", "SHIPPED", "COMPLETED"]
CLAIM_STATUS = ["REPORTED", "SURVEYED", "ASSESSED", "APPROVED", "PAID"]
PO_STATUS = ["DRAFT", "APPROVED", "RECEIVED", "INVOICED", "PAID"]


def _dataset(seed):
    """新实例，而不是 `fx.dataset()` 的缓存实例。

    场景数据必须对同一 seed 完全可复现，而 `dataset()` 返回的是**单次运行内共享**
    的缓存实例（rng 会随连续出单号/金额持续推进）。用新实例才能保证
    「两个 agent 各自 `datagen.py sales-claim --seed demo` 拿到同一份数据」。
    """
    return fx.Dataset(str(seed))


def _line_items(ds, n):
    """明细行：SKU 名称/规格取自词表，单价非圆整，小计 = 单价 × 数量。"""
    items = []
    for i in range(n):
        name, unit_price = ds.sku()
        qty = ds.rng.randint(1, 4)
        items.append(
            {
                "line_no": i + 1,
                "sku_name": name,
                "unit_price": unit_price,
                "qty": qty,
                "subtotal": round(unit_price * qty, 2),
            }
        )
    return items


def _sum(items, key="subtotal"):
    return round(sum(x[key] for x in items), 2)


def _allocate(total, weights):
    """按权重把 total 精确分摊到各行（Σ 恒等于 total，末行承接尾差）。

    与 `fixtures.split()` 的区别：权重来自业务实质（明细小计），因此
    「每行理赔金额 ≤ 该行货值」天然成立；split() 是等权拆分，适合无明细语义的场景。
    """
    total = round(float(total), 2)
    base = sum(weights) or 1.0
    parts = [round(total * w / base, 2) for w in weights[:-1]]
    parts.append(round(total - sum(parts), 2))
    return parts


def _invariants(checks):
    """把 [(名称, 布尔, 说明)] 变成稳定的机器可读结构。"""
    return {
        "all_ok": all(ok for _, ok, _ in checks),
        "checks": [{"name": n, "ok": bool(ok), "detail": d} for n, ok, d in checks],
    }


# ============================================================ 场景 1：销售 → 发货 → 理赔
def sales_claim(seed):
    """销售订单全额 = Σ明细 + 运费 − 优惠；理赔金额 = 订单应付 × 80% 且明细守恒。"""
    ds = _dataset(seed)
    items = _line_items(ds, ds.rng.randint(2, 3))
    goods_total = _sum(items)
    freight = ds.amount(lo=12, hi=48)
    discount = ds.amount(lo=5, hi=30)
    payable = round(goods_total + freight - discount, 2)

    claim_amount = round(payable * 0.8, 2)
    # 理赔金额按各行货值分摊：每行理赔 ≤ 该行小计（不能出现「117 元的货赔 4000」）
    claim_parts = _allocate(claim_amount, [it["subtotal"] for it in items])
    for item, part in zip(items, claim_parts):
        item["claim_amount"] = part

    submitter = ds.employee
    approver = ds.manager
    order_date, delivery_date, claim_date = ds.days_ago(30), ds.days_ago(20), ds.days_ago(3)

    data = {
        "scenario": "sales-claim",
        "seed": str(seed),
        "tenant_id": TENANT_GROUP,
        "story": "客户下单 → 仓库发货 → 客户报损 → 查勘定损 → 理赔审批",
        "customer": ds.customer,
        "sales_order": {
            "order_no": ds.doc_no("SO"),
            "order_date": order_date,
            "status": "COMPLETED",
            "channel": {"code": ds.channel_code, "name": ds.channel_name},
            "items": items,
            "goods_total": goods_total,
            "freight": freight,
            "discount": discount,
            "payable": payable,
        },
        "delivery": {
            "delivery_no": ds.doc_no("DO"),
            "delivery_date": delivery_date,
            "warehouse": {"code": ds.warehouse_code, "name": ds.warehouse_name},
            "receiver": ds.person(),
        },
        "claim": {
            "claim_no": ds.doc_no("BX"),
            "claim_date": claim_date,
            "policy_no": ds.doc_no("PL"),
            "claim_amount": claim_amount,
            "status": "ASSESSED",
            "reporter": ds.person(),
            "surveyor": ds.person(),
            "approver": approver,
            "submitter": submitter,
            "item_claims": [
                {"sku_name": it["sku_name"], "claim_amount": it["claim_amount"]}
                for it in items
            ],
        },
    }
    data["invariants"] = _invariants(
        [
            ("订单应付 = Σ明细 + 运费 − 优惠", payable == round(goods_total + freight - discount, 2),
             "%s = %s + %s - %s" % (payable, goods_total, freight, discount)),
            ("理赔明细 Σ = 理赔总额", round(sum(claim_parts), 2) == claim_amount,
             "%s vs %s" % (round(sum(claim_parts), 2), claim_amount)),
            ("每行理赔金额 ≤ 该行货值",
             all(it["claim_amount"] <= it["subtotal"] for it in items),
             "按明细小计比例分摊"),
            ("日期单调：下单 < 发货 < 报案", order_date < delivery_date < claim_date,
             "%s < %s < %s" % (order_date, delivery_date, claim_date)),
            ("SoD：提交人 ≠ 审批人", submitter != approver, "%s / %s" % (submitter, approver)),
            ("理赔金额 ≤ 订单应付", claim_amount <= payable, "%s <= %s" % (claim_amount, payable)),
        ]
    )
    return data


# ============================================================ 场景 2：采购 → 收货 → 应付
def procurement(seed):
    """采购单金额 = Σ明细；三单分离：申请人 / 审批人 / 收货人互不相同。"""
    ds = _dataset(seed)
    items = _line_items(ds, ds.rng.randint(2, 3))
    amount = _sum(items)

    requester, approver, receiver = ds.employee, ds.manager, ds.person()
    contract_date, order_date, receipt_date = ds.days_ago(60), ds.days_ago(45), ds.days_ago(30)

    data = {
        "scenario": "procurement",
        "seed": str(seed),
        "tenant_id": TENANT_GROUP,
        "story": "签采购合同 → 下采购单 → 到货验收 → 收票挂账 → 付款",
        "supplier": ds.supplier,
        "contract": {
            "contract_no": ds.doc_no("HT"),
            "sign_date": contract_date,
            "settlement": "月结 30 天",
        },
        "purchase_order": {
            "po_no": ds.doc_no("CG"),
            "order_date": order_date,
            "status": "RECEIVED",
            "requester": requester,
            "approver": approver,
            "items": items,
            "amount": amount,
        },
        "receipt": {
            "receipt_no": ds.doc_no("RK"),
            "receipt_date": receipt_date,
            "warehouse": {"code": ds.warehouse_code, "name": ds.warehouse_name},
            "receiver": receiver,
        },
        "payable": {
            "payable_no": ds.doc_no("YF"),
            "invoice_no": ds.doc_no("FP"),
            "amount": amount,
            "due_date": ds.days_ago(1),
            "status": "INVOICED",
        },
    }
    data["invariants"] = _invariants(
        [
            ("采购金额 = Σ明细", amount == _sum(items), "%s vs %s" % (amount, _sum(items))),
            ("应付金额 = 采购金额", data["payable"]["amount"] == amount, "同单挂账"),
            ("日期单调：合同 < 下单 < 收货", contract_date < order_date < receipt_date,
             "%s < %s < %s" % (contract_date, order_date, receipt_date)),
            ("SoD：申请人/审批人/收货人互不相同",
             len({requester, approver, receiver}) == 3, "%s / %s / %s" % (requester, approver, receiver)),
        ]
    )
    return data


# ============================================================ 场景 3：集团租户 → 部门 → 角色 → 用户
def tenant_iam(seed):
    """集团/子公司两级租户；用户必须挂在已定义部门与角色上，提交人与审批人分离。"""
    ds = _dataset(seed)
    group_name = ds.company()
    sub_name = group_name.replace("有限公司", "") + "（华东子公司）"

    dept_names = [ds.dept, ds.rng.choice(fx.DEPTS), ds.rng.choice(fx.DEPTS)]
    depts = [
        {"dept_code": "D%03d" % (i + 1), "dept_name": name, "tenant_id": TENANT_GROUP}
        for i, name in enumerate(dept_names)
    ]
    roles = [
        {"role_code": "ROLE_SYS_ADMIN", "role_name": "系统管理员"},
        {"role_code": "ROLE_SALES_MGR", "role_name": "销售经理"},
        {"role_code": "ROLE_FINANCE", "role_name": "财务专员"},
        {"role_code": "ROLE_CLAIM_APPROVER", "role_name": "理赔审批人"},
    ]

    def _user(idx, dept_idx, role_codes, job_title):
        person = ds.person()
        return {
            "user_id": "u%03d" % (idx + 1),
            "username": "%s_%03d" % ({"王": "wang", "李": "li", "张": "zhang"}.get(person[0], "user"), idx + 1),
            "real_name": person,
            "job_title": job_title,
            "dept_code": depts[dept_idx]["dept_code"],
            "role_codes": role_codes,
            "tenant_id": TENANT_GROUP,
            "password_source": "BONE_E2E_PWD（不落盘、不写进数据）",
        }

    users = [
        _user(0, 0, ["ROLE_SYS_ADMIN"], "IT 管理员"),
        _user(1, 1, ["ROLE_SALES_MGR"], "销售经理"),
        _user(2, 2, ["ROLE_FINANCE"], "财务专员"),
        _user(3, 0, ["ROLE_CLAIM_APPROVER"], "理赔主管"),
    ]
    submitter, approver = users[1], users[3]

    data = {
        "scenario": "tenant-iam",
        "seed": str(seed),
        "story": "集团建租户 → 子公司独立租户 → 配部门/角色 → 建用户 → 审批链分离",
        "tenants": [
            {"tenant_id": TENANT_GROUP, "tenant_name": group_name, "type": "GROUP"},
            {"tenant_id": TENANT_SUBSIDIARY, "tenant_name": sub_name, "type": "SUBSIDIARY",
             "parent_tenant_id": TENANT_GROUP},
        ],
        "depts": depts,
        "roles": roles,
        "users": users,
        "approval_chain": {
            "submitter": submitter["username"],
            "approver": approver["username"],
            "scene": "销售订单超额折扣审批",
        },
    }
    dept_codes = {d["dept_code"] for d in depts}
    role_codes = {r["role_code"] for r in roles}
    data["invariants"] = _invariants(
        [
            ("用户部门引用完整", all(u["dept_code"] in dept_codes for u in users),
             "所有 dept_code 命中 depts"),
            ("用户角色引用完整", all(set(u["role_codes"]) <= role_codes for u in users),
             "所有 role_code 命中 roles"),
            ("子公司租户挂在集团下",
             data["tenants"][1]["parent_tenant_id"] == TENANT_GROUP, "parent=1001"),
            ("SoD：提交人 ≠ 审批人", submitter["username"] != approver["username"],
             "%s / %s" % (submitter["username"], approver["username"])),
        ]
    )
    return data


# ============================================================ 场景 4：主数据平台全局 + 租户 overlay
def master_data(seed):
    """平台全局主数据只读，租户 overlay 必须引用平台编码（引用完整可校验）。"""
    ds = _dataset(seed)
    materials = []
    for i in range(3):
        name, unit_price = ds.sku()
        materials.append(
            {
                "material_code": "MAT-%04d" % (i + 1),
                "material_name": name,
                "uom": "台" if i % 2 == 0 else "件",
                "reference_price": unit_price,
                "tenant_id": TENANT_PLATFORM,
            }
        )
    customers = [
        {"customer_code": ds.customer["code"], "customer_name": ds.customer["name"],
         "tenant_id": TENANT_PLATFORM},
    ]
    suppliers = [
        {"supplier_code": ds.supplier["code"], "supplier_name": ds.supplier["name"],
         "tenant_id": TENANT_PLATFORM},
    ]
    overlay = {
        "tenant_id": TENANT_GROUP,
        "material_overlays": [
            {
                "source_material_code": materials[0]["material_code"],
                "local_name": materials[0]["material_name"] + "（华东专供）",
                "local_price": ds.amount(lo=100, hi=3000),
            }
        ],
        "customer_overlays": [
            {
                "source_customer_code": customers[0]["customer_code"],
                "credit_limit": ds.amount(lo=50000, hi=300000),
                "sales_owner": ds.employee,
            }
        ],
    }
    data = {
        "scenario": "master-data",
        "seed": str(seed),
        "story": "平台维护全局物料/客户/供应商 → 租户按需 overlay 本地价与授信",
        "platform": {"materials": materials, "customers": customers, "suppliers": suppliers},
        "tenant_overlay": overlay,
    }
    material_codes = {m["material_code"] for m in materials}
    customer_codes = {c["customer_code"] for c in customers}
    data["invariants"] = _invariants(
        [
            ("overlay 物料引用平台编码",
             all(o["source_material_code"] in material_codes for o in overlay["material_overlays"]),
             "source_material_code 命中 platform.materials"),
            ("overlay 客户引用平台编码",
             all(o["source_customer_code"] in customer_codes for o in overlay["customer_overlays"]),
             "source_customer_code 命中 platform.customers"),
            ("平台数据 tenant_id = 0",
             all(m["tenant_id"] == TENANT_PLATFORM for m in materials), "全局只读"),
            ("overlay 价格为正",
             all(o["local_price"] > 0 for o in overlay["material_overlays"]), "价格 > 0"),
        ]
    )
    return data


# ============================================================ 场景 5：AI 建议回流（曝光 → 裁决）
AI_INVOLVEMENTS = ("AI_ASSISTED", "AI_STUB", "AI_UNAVAILABLE", "UNDETERMINED")
# 入站 DTO 的字段名就是 reasonCode（AiFeedbackEventReq）；响应体里才叫 reasonType。
# 场景产出的是「可直接 POST 的请求体」，所以统一写 reasonCode。
AI_REASON_CODES = ("RULE_MISS", "CONTEXT_MISS", "SUGGESTION_WRONG", "POLICY_CONFLICT")
AI_PII_KEYS = {"claimant_name", "phone", "id_card", "amount", "conclusion", "suggest_amount"}


def _event_id(ds):
    """UUIDv4 形态的幂等键：同 seed 同值（复跑可复现），但不含任何业务含义。"""
    r = ds.rng
    return "%08x-%04x-4%03x-8%03x-%012x" % (
        r.getrandbits(32), r.getrandbits(16), r.getrandbits(12),
        r.getrandbits(12), r.getrandbits(48),
    )


def claim_ai_feedback(seed):
    """AI 建议曝光 → 人工裁决回流：幂等键唯一、仅 AI_ASSISTED 可裁决、REJECT 必带原因码、事件体不含 PII。"""
    ds = _dataset(seed)
    claim_id = 800000 + ds.rng.randint(1, 9999)
    claim_no = ds.doc_no("BX")
    trace_id = "trace-" + _event_id(ds).replace("-", "")[:24]
    actor = ds.person()

    events = [
        {"eventId": _event_id(ds), "claimId": claim_id, "eventType": "IMPRESSION",
         "traceId": trace_id, "involvement": "AI_ASSISTED", "scene": "case",
         "disposition": None, "reasonCode": None, "comment": None, "modifiedFields": None},
        {"eventId": _event_id(ds), "claimId": claim_id, "eventType": "DISPOSITION",
         "traceId": trace_id, "involvement": "AI_ASSISTED", "scene": "case",
         "disposition": "ADOPT", "reasonCode": None,
         "comment": "建议与人工判断一致，采纳", "modifiedFields": None},
        {"eventId": _event_id(ds), "claimId": claim_id, "eventType": "DISPOSITION",
         "traceId": trace_id, "involvement": "AI_ASSISTED", "scene": "case",
         "disposition": "REJECT", "reasonCode": "SUGGESTION_WRONG",
         "comment": "建议漏看既往病史，人工驳回", "modifiedFields": ["diagnosis", "suggest_amount"]},
    ]
    # 字段名逐字对齐 AiFeedbackSummaryResp：没有 adoptRate（采纳率由前端按 adopts/dispositions 算）。
    summary = {
        "impressions": 1,
        "dispositions": 2,
        "adopts": 1,
        "rejects": 1,
        "modifies": 0,
        "windowDays": 30,
        "serverSide": True,
    }
    data = {
        "scenario": "claim-ai-feedback",
        "seed": str(seed),
        "story": "理赔工作台曝光 AI 建议 → 人工采纳一单、驳回一单 → 汇总采纳率 50%",
        "tenant_id": TENANT_GROUP,
        "claim": {
            "claim_id": claim_id,
            "claim_no": claim_no,
            "actor": actor,
            "actor_source": "认证主体（不接受客户端声明）",
        },
        "events": events,
        "summary_expect": summary,
    }
    ids = [e["eventId"] for e in events]
    dispositions = [e for e in events if e["eventType"] == "DISPOSITION"]
    adopt = [e for e in dispositions if e["disposition"] == "ADOPT"]
    data["invariants"] = _invariants(
        [
            ("幂等键租户内唯一", len(ids) == len(set(ids)), "eventId 去重 %d 条" % len(ids)),
            ("参与态取值合法",
             all(e["involvement"] in AI_INVOLVEMENTS for e in events),
             "involvement ∈ %s" % (AI_INVOLVEMENTS,)),
            ("裁决仅允许 AI_ASSISTED",
             all(e["involvement"] == "AI_ASSISTED" for e in dispositions),
             "非 AI_ASSISTED 不得计入采纳率"),
            ("REJECT/MODIFY 必带受控原因码",
             all(e["reasonCode"] in AI_REASON_CODES for e in dispositions
                 if e["disposition"] in ("REJECT", "MODIFY")),
             "reasonCode ∈ %s" % (AI_REASON_CODES,)),
            ("曝光不带裁决、裁决必带结论",
             all((e["disposition"] is None) == (e["eventType"] == "IMPRESSION") for e in events),
             "eventType 与 disposition 成对"),
            ("事件体不含 PII/金额", all(not (AI_PII_KEYS & set(e)) for e in events),
             "只存 claimId/traceId/原因码/备注"),
            ("备注不超过 200 字符",
             all(len(e["comment"] or "") <= 200 for e in events), "列宽 varchar(200)"),
            ("汇总与事件一致",
             summary["impressions"] == sum(e["eventType"] == "IMPRESSION" for e in events)
             and summary["dispositions"] == len(dispositions)
             and summary["adopts"] == len(adopt)
             and summary["rejects"] == len(dispositions) - len(adopt)
             and summary["serverSide"] is True and summary["windowDays"] == 30,
             "曝光/采纳/驳回计数与服务端口径"),
            ("被改字段白名单为合法载体（不含值）",
             all(e["modifiedFields"] is None
                 or (isinstance(e["modifiedFields"], list)
                     and all(isinstance(x, str) for x in e["modifiedFields"]))
                 for e in events),
             "modified_fields_json 为 None 或 字段名数组（不含值）"),
            ("REJECT/MODIFY 必带被改字段白名单",
             all(e["modifiedFields"] is not None for e in dispositions
                 if e["disposition"] in ("REJECT", "MODIFY")),
             "驳回/修改必须记录改了哪些字段（不含值）"),
            ("scene 取值合法（禁 blind_label）",
             all(e["scene"] in ("case", "review") for e in events),
             "scene ∈ {case, review}"),
        ]
    )
    return data


SCENARIOS = {
    "claim-ai-feedback": {
        "builder": claim_ai_feedback,
        "desc": "理赔 AI 建议曝光 → 人工裁决回流：幂等键唯一、仅 AI_ASSISTED 可裁决、REJECT 必带原因码",
    },
    "sales-claim": {
        "builder": sales_claim,
        "desc": "销售订单 → 发货 → 理赔：金额守恒、日期单调、提交人≠审批人",
    },
    "procurement": {
        "builder": procurement,
        "desc": "采购合同 → 采购单 → 收货 → 应付：三单分离 SoD",
    },
    "tenant-iam": {
        "builder": tenant_iam,
        "desc": "集团/子公司租户 → 部门 → 角色 → 用户：引用完整 + 审批链分离",
    },
    "master-data": {
        "builder": master_data,
        "desc": "平台全局主数据 + 租户 overlay：引用完整、平台只读",
    },
}


def names():
    """全部场景名（排序后，供 CLI / 文档稳定展示）。"""
    return sorted(SCENARIOS)


def describe():
    """[(场景名, 一句话说明)]，供 `datagen.py list` 直接打印。"""
    return [(name, SCENARIOS[name]["desc"]) for name in names()]


def build(name, seed):
    """按场景名构造数据；未知场景抛 ValueError（不静默回退）。"""
    if name not in SCENARIOS:
        raise ValueError("未知场景: %s（可选: %s）" % (name, ", ".join(names())))
    return SCENARIOS[name]["builder"](seed)
