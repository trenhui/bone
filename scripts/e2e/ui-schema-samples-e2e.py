"""两样板场景端到端串联（模拟人工建模操作 + 业务操作）。

报销（expense-claim）与智能理赔（claim-outpatient-fasttrack）各走一遍完整链路：

  建模侧：建实体(含 rowPolicies) → 建子实体 → 建字段 → 配状态机动作定义
          → PUT 界面协议(样例 JSON) → validate → publish（闸门放行）
  业务侧：建记录 → 替换子表明细 → 触发动作流转（DRAFT→SUBMITTED→APPROVED→打款）
  断言：  ① validate 零 ERROR（引用完整性全通）
          ② publish 200（发布闸门放行）
          ③ 状态机按 from→to 真实流转
          ④ 协议 submitRules 服务端强制：明细合计不符 → 动作被 422 拦下
          ⑤ linkage required 服务端强制：专票缺税号 → 动作被 422 拦下
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:9001/api/v1"
TOKEN = open("/tmp/bone_token.txt").read().strip()
HDR = {
    "Authorization": "Bearer " + TOKEN,
    "X-Tenant-Id": "0",
    "Content-Type": "application/json",
}
SAMPLES = "/Users/renhui.trh/wps/bone/doc/design/lowcode/cases/ui-schema-samples"

PASS, FAIL = [], []


def call(method, path, body=None, extra=None):
    h = dict(HDR)
    if extra:
        h.update(extra)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else {}), dict(r.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw), dict(e.headers)
        except Exception:
            return e.code, {"raw": raw}, dict(e.headers)


def ecode(d):
    data = d.get("data")
    return data.get("errorCode") if isinstance(data, dict) else None


def check(name, cond, detail=""):
    if cond:
        PASS.append(name)
        print(f"  ✓ {name}")
    else:
        FAIL.append(f"{name} :: {detail}")
        print(f"  ✗ {name}  ← {detail}")


def section(t):
    print(f"\n{'=' * 78}\n{t}\n{'=' * 78}")


SUFFIX = str(int(time.time()))[-9:]


def create_entity(name, code, table, row_policies=None, fields=(), delivery=1):
    body = {
        "name": name,
        "code": code,
        "displayName": name,
        "tableName": table,
        "type": 1,
        "deliveryMode": delivery,
        "description": "E2E 样板场景",
    }
    if row_policies:
        body["rowPolicies"] = json.dumps(row_policies, ensure_ascii=False)
    s, d, _ = call("POST", "/metadata/entities", body)
    if s in (200, 201):
        eid = d["data"]
    elif ecode(d) == "META_ENTITY_CODE_CONFLICT":
        # 幂等复用：编码已存在（上一轮 E2E 或既有种子建的明细实体）——重复建模不该撞车
        eid = find_entity_id_by_code(code)
        print(f"    [复用] 实体 {code} 已存在 id={eid}")
        assert eid, f"实体 {code} 冲突且无法定位"
        for f in fields:
            ensure_field(eid, f)
        return eid
    else:
        raise AssertionError(f"create entity {code} failed {s} {d}")
    for f in fields:
        ensure_field(eid, f)
    return eid


def find_entity_id_by_code(code):
    # 全量拉取后本地精确匹配 code（keyword 模糊搜索对下划线编码不稳定，且要避免分页漏项）
    s, d, _ = call("GET", "/metadata/entities?page=1&size=200")
    recs = (d.get("data") or {}).get("records") or []
    for r in recs:
        if r.get("code") == code:
            return r.get("id")
    return None


def ensure_field(eid, f):
    """字段幂等创建：已存在（409/400 code 冲突）视为已具备。"""
    fb = {"name": f["code"], "code": f["code"], "displayName": f["label"], "type": f.get("type", "STRING")}
    if f.get("required"):
        fb["required"] = True
    if f.get("length"):
        fb["length"] = f["length"]
    if f.get("validationRules"):
        fb["validationRules"] = json.dumps(f["validationRules"], ensure_ascii=False)
    fs, fd, _ = call("POST", f"/metadata/entities/{eid}/fields", fb)
    if fs not in (200, 201):
        print(f"    [skip] 字段 {f['code']} 已存在或创建失败 {fs} {fd.get('message', '')[:60]}")

def add_action(eid, code, label, frm, to, escape=False, handler=None, idem=False, order=0, status_field="status"):
    body = {
        "actionCode": code,
        "label": label,
        "fromStatus": frm,
        "toStatus": to,
        "statusField": status_field,
        "escape": escape,
        "requiresIdempotency": idem,
        "sortOrder": order,
    }
    if handler:
        body["handlerBean"] = handler
    s, d, _ = call("POST", f"/metadata/entities/{eid}/action-defs", body)
    assert s == 200, f"action-def {code} failed {s} {d}"
    return d



def add_child_table_field(entity_id, field_code, child_entity_code, fk, physical=False, type="LONG"):
    """子表/外键字段声明（引擎方言：CHILD_TABLE 类型字段 + validation_rules.childTable）。"""
    if physical:
        fb = {"name": field_code, "code": field_code, "displayName": field_code, "type": type}
    else:
        rules = {
            "childTable": {
                "childEntityCode": child_entity_code,
                "foreignKey": fk,
                "orderColumn": "sort_order",
                "cascadeDelete": True,
                "minRows": 0,
                "maxRows": 200,
            }
        }
        fb = {
            "name": field_code,
            "code": field_code,
            "displayName": field_code,
            "type": "CHILD_TABLE",
            "validationRules": json.dumps(rules, ensure_ascii=False),
        }
    s, d, _ = call("POST", f"/metadata/entities/{entity_id}/fields", fb)
    print(f"    子表/外键字段 {field_code} -> {s}")
    return s


def put_schema(eid, sample):
    raw = open(os.path.join(SAMPLES, sample)).read()
    s, d, hdr = call("PUT", f"/metadata/entities/{eid}/ui-schema", {"json": raw}, {"If-Match": "-1"})
    if s == 412:
        s, d, hdr = call("GET", f"/metadata/entities/{eid}/ui-schema")
        lock = d["data"].get("lockVersion")
        s, d, hdr = call(
            "PUT", f"/metadata/entities/{eid}/ui-schema", {"json": raw}, {"If-Match": str(lock)}
        )
    return s, d


def validate(eid):
    s, d, _ = call("POST", f"/metadata/entities/{eid}/ui-schema/validate")
    issues = d.get("data") or []
    return s, [i for i in issues if i.get("level") == "ERROR"], issues


def publish(eid):
    s, d, hdr = call("POST", f"/metadata/entities/{eid}/publish")
    if s in (200, 409):
        pass
    return s, d


def rec_create(entity_code, body):
    s, d, _ = call("POST", f"/runtime/entities/{entity_code}/records", body)
    return s, d


def child_replace(entity_code, rid, child_code, rows):
    s, d, _ = call("POST", f"/runtime/entities/{entity_code}/records/{rid}/children/{child_code}", rows)
    return s, d


def act(entity_code, rid, action, params=None, idem=None):
    extra = {"Idempotency-Key": idem} if idem else None
    s, d, _ = call("POST", f"/runtime/entities/{entity_code}/records/{rid}/actions/{action}", {"params": params or {}}, extra)
    return s, d


# ==================================================================
section("场景一：费用报销（expense-claim）")

EXP = "expense_claim_e2e_" + SUFFIX
EXP_ITEM = "expense_claim_item"  # 协议样例 props.childEntity 引用此名
exp_fields = [
    {"code": "claim_no", "label": "单号"},
    {"code": "title", "label": "标题", "required": True},
    {"code": "amount", "label": "金额", "type": "DECIMAL", "precision": 2},
    {"code": "currency", "label": "币种"},
    {"code": "category_id", "label": "费用类别"},
    {"code": "applicant", "label": "申请人"},
    {"code": "department", "label": "部门"},
    {"code": "expense_date", "label": "费用日期"},
    {"code": "expense_start", "label": "开始日期"},
    {"code": "expense_end", "label": "结束日期"},
    {"code": "invoice_type", "label": "发票类型"},
    {"code": "tax_no", "label": "税号"},
    {"code": "approver_id", "label": "审批人", "type": "LONG"},
    {"code": "attachment", "label": "发票影像", "type": "ATTACHMENT"},
    {"code": "status", "label": "状态"},
]
item_fields = [
    {"code": "expense_date", "label": "日期"},
    {"code": "category_id", "label": "类别"},
    {"code": "amount", "label": "金额", "type": "DECIMAL", "precision": 2},
    {"code": "remark", "label": "备注"},
]

# 行策略（此前只能靠 SQL 改 —— 本轮打通 API 入口）
exp_policy = {
    "owner_field": "created_by",
    "rules": [
        {
            "name": "owner_or_approver",
            "effect": "allow",
            "when": "created_by = :operator OR approver_id = :operator",
        }
    ],
}

exp_id = create_entity("报销单", EXP, f"t_{EXP}", exp_policy, exp_fields)
item_id = create_entity("报销明细", EXP_ITEM, f"t_{EXP_ITEM}", None, item_fields)
print(f"  实体: 报销={exp_id} 明细={item_id}")

# 子表声明走「父实体的 CHILD_TABLE 字段 + validation_rules.childTable」（引擎方言，0037/0047 种子同款）
add_child_table_field(exp_id, "items", EXP_ITEM, "claim_id")
add_child_table_field(item_id, "claim_id", None, None, physical=True)
add_child_table_field(item_id, "sort_order", None, None, physical=True, type="INT")
sp, dp = publish(item_id)
check("报销：明细实体先发布（子表端点要求目标已发布 RUNTIME）", sp == 200, f"{sp} {dp.get('message')}")

for code, label, frm, to, esc, hdl, idem, order in [
    ("submit", "提交", "DRAFT", "SUBMITTED", False, None, False, 1),
    ("approve", "审批通过", "SUBMITTED", "APPROVED", False, None, False, 2),
    ("reject", "驳回", "SUBMITTED", "REJECTED", False, None, False, 3),
    (
        "confirmPayout",
        "确认打款",
        "APPROVED",
        "PAID",
        True,
        "expenseClaimConfirmPayoutHandler",
        True,
        4,
    ),
]:
    add_action(exp_id, code, label, frm, to, esc, hdl, idem, order)

s, d = put_schema(exp_id, "expense-claim-ui-schema.example.json")
check("报销：UI 协议 PUT 成功", s == 200, f"{s} {d.get('message')}")

s, errs, issues = validate(exp_id)
check(
    "报销：validate 零 ERROR",
    s == 200 and len(errs) == 0,
    f"{len(errs)} 个 ERROR: " + "; ".join(f"{i.get('code')} {i.get('message')}" for i in errs[:5]),
)

s, d = publish(exp_id)
check("报销：publish 放行（闸门无阻断）", s == 200, f"{s} {ecode(d)} {d.get('message')}")

# ---- 业务侧 ----
s, d = rec_create(EXP, {"title": "10 月差旅报销", "amount": 140, "currency": "CNY", "approver_id": 1})
check("报销：创建记录", s in (200, 201), f"{s} {d.get('message')}")
rid = d.get("data", {}).get("id") if s in (200, 201) else None

if rid:
    # 明细合计 140 = 申报 140 → 合规
    s, d = child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 40}])
    check("报销：写入明细（合计 140）", s in (200, 201), f"{s} {d.get('message')}")

    # 负向①：明细合计 150 ≠ 申报 140 → SUB 规则必须拦（协议 SUB 明细合计一致）
    s2, d2 = child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 50}])
    s, d = act(EXP, rid, "submit")
    check(
        "报销【负向】明细合计≠申报 → 动作 422 且带规则码",
        s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
        f"期望 422/RUNTIME_UI_RULES_VIOLATED，实际 {s} {ecode(d)} {d.get('message', '')[:160]}",
    )

    # 恢复合规明细
    s2, d2 = child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 40}])

    # 负向②：专票缺税号 → linkage required 必须拦
    s, d, _ = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"invoice_type": "SPECIAL"})
    s, d = act(EXP, rid, "submit")
    check(
        "报销【负向】专票缺税号 → linkage 必填拦截",
        s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
        f"期望 422，实际 {s} {ecode(d)} {d.get('message', '')[:160]}",
    )

    # 补税号后放行
    s, d, _ = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"tax_no": "91310000MA1FL0"})
    s, d = act(EXP, rid, "submit")
    check("报销：合规后 submit 成功（DRAFT→SUBMITTED）", s == 200, f"{s} {d.get('message')}")
    if s == 200:
        st = (d.get("data", {}).get("record", {}) or {}).get("status")
        check("报销：状态真实流转到 SUBMITTED", st == "SUBMITTED", f"实际 status={st}")

        s, d = act(EXP, rid, "approve")
        st = (d.get("data", {}).get("record", {}) or {}).get("status") if s == 200 else None
        check("报销：approve 流转到 APPROVED", s == 200 and st == "APPROVED", f"{s} status={st}")

        # 负向③：逃逸动作缺幂等键 → 必须拒（防重复打款）
        s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"})
        check(
            "报销【负向】打款缺幂等键 → 拒绝",
            s == 400 and ecode(d) == "META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED",
            f"期望 400/IDEMPOTENCY_REQUIRED，实际 {s} {ecode(d)}",
        )
        # 负向④：带幂等键但缺必填入参 channel → 400 可诊断（不得 500）
        s, d = act(EXP, rid, "confirmPayout", params={}, idem=f"payout-nochannel-{rid}")
        check(
            "报销【负向】打款缺必填入参 channel → 400（不得 500）",
            s == 400 and ecode(d) == "META_RUNTIME_ACTION_INVALID_PARAMS",
            f"期望 400/INVALID_PARAMS，实际 {s} {ecode(d)} {d.get('message', '')[:100]}",
        )

        key = f"payout-{rid}"
        s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"}, idem=key)
        st = (d.get("data", {}).get("record", {}) or {}).get("status") if s == 200 else None
        check("报销：confirmPayout 带幂等键成功（APPROVED→PAID）", s == 200 and st == "PAID", f"{s} status={st} {d.get('message', '')[:120]}")

        # 幂等重放：同键再调应复用首次结果而非二次打款
        s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"}, idem=key)
        check(
            "报销：同幂等键重放复用首次结果",
            s == 200 and (d.get("data", {}) or {}).get("idempotent") is True,
            f"{s} idempotent={(d.get('data', {}) or {}).get('idempotent')}",
        )

        # 非法流转：PAID 态再 approve → 409
        s, d = act(EXP, rid, "approve")
        check(
            "报销【负向】终态再审批 → 状态机拒绝",
            s == 409 and ecode(d) == "META_RUNTIME_ACTION_ILLEGAL_TRANSITION",
            f"期望 409/ILLEGAL_TRANSITION，实际 {s} {ecode(d)}",
        )

# ==================================================================
section("场景二：智能理赔·门诊快赔（claim-outpatient-fasttrack）")

CLM = "t_claim_case_e2e_" + SUFFIX
CLM_ITEM = "claim_item"  # 协议样例 props.childEntity 引用此名
clm_fields = [
    {"code": "claim_no", "label": "报案号"},
    {"code": "policy_no", "label": "保单号", "required": True},
    {"code": "insured_id_hash", "label": "投保人哈希"},
    {"code": "insured_name", "label": "被保人"},
    {"code": "claim_type", "label": "理赔类型"},
    {"code": "hospital_level", "label": "医院等级"},
    {"code": "invoice_no", "label": "发票号"},
    {"code": "invoice_date", "label": "发票日期"},
    {"code": "invoice_amount", "label": "发票金额", "type": "DECIMAL", "precision": 2},
    {"code": "claim_amount", "label": "申报金额", "type": "DECIMAL", "precision": 2},
    {"code": "payable_amount", "label": "应赔金额", "type": "DECIMAL", "precision": 2},
    {"code": "calc_amount", "label": "理算金额", "type": "DECIMAL", "precision": 2},
    {"code": "fraud_flag", "label": "风险标记"},
    {"code": "cap_reached", "label": "已达封顶"},
    {"code": "coverage_clear", "label": "责任明确"},
    {"code": "second_reviewer", "label": "第二复核人", "type": "LONG"},
    {"code": "reject_reasons", "label": "拒赔理由", "type": "TEXT"},
    {"code": "evidences", "label": "材料证据", "type": "ATTACHMENT"},
    {"code": "fraud_flags", "label": "风险标记", "type": "STRING"},
    {"code": "fasttrack_entry", "label": "快赔入口", "type": "STRING"},
    {"code": "status", "label": "状态"},
]
clm_item_fields = [
    {"code": "name", "label": "项目"},
    {"code": "amount", "label": "金额", "type": "DECIMAL", "precision": 2},
    {"code": "category", "label": "类别"},
]

clm_policy = {
    "owner_field": "created_by",
    "rules": [
        {"name": "reviewer_or_owner", "effect": "allow", "when": "created_by = :operator"}
    ],
}

clm_id = create_entity("理赔案件", CLM, f"t_{CLM}", clm_policy, clm_fields)
clm_item_id = create_entity("理赔明细", CLM_ITEM, f"t_{CLM_ITEM}", None, clm_item_fields)
print(f"  实体: 理赔={clm_id} 明细={clm_item_id}")

add_child_table_field(clm_id, "items", CLM_ITEM, "claim_id")
add_child_table_field(clm_item_id, "claim_id", None, None, physical=True)
add_child_table_field(clm_item_id, "sort_order", None, None, physical=True, type="INT")
sp, dp = publish(clm_item_id)
check("理赔：明细实体先发布", sp == 200, f"{sp} {dp.get('message')}")

for code, label, frm, to, esc, hdl, idem, order in [
    ("submit", "提交", "DRAFT", "REVIEWING", False, None, False, 1),
    ("approve", "核赔通过", "REVIEWING", "APPROVED", False, None, False, 2),
    ("reject", "拒赔", "REVIEWING", "REJECTED", False, None, False, 3),
    ("requestMoreEvidence", "补充材料", "REVIEWING", "PENDING_EVIDENCE", False, None, False, 4),
    ("pay", "支付", "APPROVED", "PAID", True, "expenseClaimConfirmPayoutHandler", True, 5),
    ("appeal", "申诉", "REJECTED", "APPEALING", False, None, False, 6),
]:
    add_action(clm_id, code, label, frm, to, esc, hdl, idem, order)

s, d = put_schema(clm_id, "claim-outpatient-fasttrack-ui-schema.example.json")
check("理赔：UI 协议 PUT 成功", s == 200, f"{s} {d.get('message')}")

s, errs, issues = validate(clm_id)
check(
    "理赔：validate 零 ERROR",
    s == 200 and len(errs) == 0,
    f"{len(errs)} 个 ERROR: " + "; ".join(f"{i.get('code')} {i.get('message')}" for i in errs[:5]),
)

s, d = publish(clm_id)
check("理赔：publish 放行", s == 200, f"{s} {ecode(d)} {d.get('message')}")

s, d = rec_create(
    CLM,
    {
        "policy_no": "P-2026-0001",
        "claim_no": "CLM-001",
        "invoice_amount": 300,
        "claim_amount": 300,
        "coverage_clear": True,
        "evidences": "INV-2026-0001",
    },
)
check("理赔：创建记录", s in (200, 201), f"{s} {d.get('message')}")
crid = d.get("data", {}).get("id") if s in (200, 201) else None

if crid:
    s, d = child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 300, "category": " outpatient"}])
    check("理赔：写入明细（合计 300）", s in (200, 201), f"{s} {d.get('message')}")

    # 负向：明细合计 350 ≠ 发票 300（容差 1）→ SUB-CONS-01 必须拦
    s2, d2 = child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 350, "category": "outpatient"}])
    s, d = act(CLM, crid, "submit")
    check(
        "理赔【负向】发票金额≠清单合计 → SUB-CONS-01 拦截",
        s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED" and "SUB-CONS-01" in (d.get("message") or ""),
        f"期望 422 且消息含 SUB-CONS-01，实际 {s} {ecode(d)} {d.get('message', '')[:200]}",
    )
    s2, d2 = child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 300, "category": "outpatient"}])

    s, d = act(CLM, crid, "submit")
    check("理赔：合规后 submit 成功（DRAFT→REVIEWING）", s == 200, f"{s} {d.get('message', '')[:160]}")
    if s == 200:
        st = (d.get("data", {}).get("record", {}) or {}).get("status")
        check("理赔：状态流转到 REVIEWING", st == "REVIEWING", f"status={st}")

        # 负向：拒赔完整性（when status=REJECTING 才跑）——REVIEWING 下不该误伤
        s, d = act(CLM, crid, "reject")
        st = (d.get("data", {}).get("record", {}) or {}).get("status") if s == 200 else None
        check("理赔：reject 流转到 REJECTED", s == 200 and st == "REJECTED", f"{s} status={st} {d.get('message', '')[:160]}")

        # REJECTED 态下 reject 动作 from_status=REVIEWING → 应被状态机拒
        s, d = act(CLM, crid, "reject")
        check(
            "理赔【负向】非 REVIEWING 态 reject → 状态机拒绝",
            s == 409 and ecode(d) == "META_RUNTIME_ACTION_ILLEGAL_TRANSITION",
            f"期望 409，实际 {s} {ecode(d)}",
        )

        # 另一条单：走完 approve → pay 全链路（资金支付）
        s, d = rec_create(
            CLM,
            {
                "policy_no": "P-2026-0002",
                "claim_no": "CLM-002",
                "invoice_amount": 500,
                "claim_amount": 500,
                "coverage_clear": True,
                "evidences": "INV-2026-0002",
            },
        )
        rid2 = (d.get("data", {}) or {}).get("id")
        if rid2:
            child_replace(CLM, rid2, CLM_ITEM, [{"name": "门诊费", "amount": 500, "category": "outpatient"}])
            s, d = act(CLM, rid2, "submit")
            check("理赔：单2 submit（DRAFT→REVIEWING）", s == 200, f"{s} {d.get('message', '')[:140]}")
            s, d = act(CLM, rid2, "approve")
            st = (d.get("data", {}).get("record", {}) or {}).get("status") if s == 200 else None
            check("理赔：单2 approve（REVIEWING→APPROVED）", s == 200 and st == "APPROVED", f"{s} status={st}")
            s, d = act(CLM, rid2, "pay", params={"channel": "WECHAT"}, idem=f"claimpay-{rid2}")
            st = (d.get("data", {}).get("record", {}) or {}).get("status") if s == 200 else None
            check("理赔：单2 pay 带幂等键+channel（APPROVED→PAID）", s == 200 and st == "PAID", f"{s} status={st} {d.get('message', '')[:140]}")
            s, d = act(CLM, rid2, "pay", params={"channel": "WECHAT"}, idem=f"claimpay-{rid2}")
            check(
                "理赔：单2 同幂等键重放复用",
                s == 200 and (d.get("data", {}) or {}).get("idempotent") is True,
                f"{s} idempotent={(d.get('data', {}) or {}).get('idempotent')}",
            )

# ==================================================================
section("汇总")
print(f"通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
for f in FAIL:
    print(f"  ✗ {f}")
sys.exit(1 if FAIL else 0)
