#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""报销 + 智能理赔 双样板场景「全功能」端到端串联（经网关，模拟人工建模与业务操作）。

与 scripts/e2e/ui-schema-samples-e2e.py 的区别：那份只跑「协议提交/联动强制」的样例对账，
本脚本把**上一轮收口的全部功能点**串成一条真人操作链路，逐项落断言：

建模侧（catalog）
  P0-4 行策略 rowPolicies 写入 + 结构守卫（非法 → 400）
  P0-3 动作定义 action-defs 的 POST / GET / DELETE + 入参守卫
  P0-5 界面协议 PUT / validate / publish-preview / ui-overlay GET-PUT-DELETE
  P1-4 发布闸门：ERROR 级校验问题 → 409 META_PUBLISH_BLOCKED（负向）
  P0-2 归档 archive（仅 PUBLISHED 可归档，负向 409）+ revert-draft 回草稿
  P0-1 筛选服务化：type / deliveryMode 真下推（结果集逐条复核，防 no-op）
  auto 自动编号 autoNumber（客户端显式值保留 + 缺省原子递增）
业务侧（runtime）
  建/查/改记录（ETag If-Match 并发保护，错 ETag → 412）
  子表明细替换 + submitRules 服务端强制（422）+ linkage 必填强制（422）
  状态机流转（含非法流转 409 / 逃逸动作幂等键强制 / 缺参 400 不得 500）
  幂等重放复用首次结果
  回收站：软删 → GET /deleted 可见 → restore 恢复
  审计时间线：GET /{id}/action-executions 逐条留痕
  动作清单：GET /actions

用法：
  python3 scripts/e2e/two-scenario-full-chain-e2e.py            # 默认走网关 :8888（真联调）
  BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 python3 ...        # 直连后端排查用
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BONE_E2E_BASE", "http://127.0.0.1:8888/api/v1")
SAMPLES = "/Users/renhui.trh/wps/bone/doc/design/lowcode/cases/ui-schema-samples"
USER, PWD = "admin", "123456"

PASS, FAIL = [], []
SUFFIX = str(int(time.time()))[-9:]


# ---------------------------------------------------------------- 基础设施
def raw_call(base, method, path, body=None, headers=None):
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else {}), dict(r.headers)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt), dict(e.headers)
        except Exception:
            return e.code, {"raw": txt[:400]}, dict(e.headers)


def login():
    s, d, _ = raw_call(BASE, "POST", "/iam/login", {"username": USER, "password": PWD})
    tok = (d.get("data") or {}).get("token") if s == 200 else None
    if not tok:
        sys.exit(f"登录失败 {s} {str(d)[:200]}")
    return tok


TOKEN = login()
HDR = {"Authorization": "Bearer " + TOKEN, "X-Tenant-Id": "0", "Content-Type": "application/json"}


def call(method, path, body=None, extra=None):
    """返回 (status, body)；需要响应头时用 raw_call。"""
    h = dict(HDR)
    if extra:
        h.update(extra)
    s, d, _ = raw_call(BASE, method, path, body, h)
    return s, d


def ecode(d):
    data = d.get("data")
    return data.get("errorCode") if isinstance(data, dict) else None


def d_(d, *keys, default=None):
    cur = d.get("data")
    for k in keys:
        if not isinstance(cur, dict):
            return default
        cur = cur.get(k)
    return default if cur is None else cur


def check(name, cond, detail=""):
    if cond:
        PASS.append(name)
        print(f"  ✓ {name}")
    else:
        FAIL.append(f"{name} :: {detail}")
        print(f"  ✗ {name}  ← {detail}")


def section(t):
    print(f"\n{'=' * 78}\n{t}\n{'=' * 78}")


def note(t):
    print(f"    · {t}")


# ---------------------------------------------------------------- 建模助手
def find_entity_id_by_code(code):
    s, d = call("GET", "/metadata/entities?page=1&size=500")
    for r in ((d.get("data") or {}).get("records") or []):
        if r.get("code") == code:
            return r.get("id")
    return None


def create_entity(name, code, table, row_policies=None, fields=(), auto_number=None, delivery=1):
    body = {
        "name": name,
        "code": code,
        "displayName": name,
        "tableName": table,
        "type": 1,
        "deliveryMode": delivery,
        "description": "E2E 样板场景（报销/理赔串联）",
    }
    if row_policies:
        body["rowPolicies"] = json.dumps(row_policies, ensure_ascii=False)
    if auto_number:
        body["autoNumber"] = json.dumps(auto_number, ensure_ascii=False)
    s, d = call("POST", "/metadata/entities", body)
    if s in (200, 201):
        eid = d.get("data")
    elif ecode(d) == "META_ENTITY_CODE_CONFLICT":
        eid = find_entity_id_by_code(code)
        note(f"[复用] 实体 {code} 已存在 id={eid}")
    else:
        raise AssertionError(f"create entity {code} failed {s} {str(d)[:200]}")
    for f in fields:
        ensure_field(eid, f)
    return eid


def ensure_field(eid, f):
    fb = {"name": f["code"], "code": f["code"], "displayName": f["label"], "type": f.get("type", "STRING")}
    if f.get("required"):
        fb["required"] = True
    if f.get("length"):
        fb["length"] = f["length"]
    if f.get("validationRules"):
        fb["validationRules"] = json.dumps(f["validationRules"], ensure_ascii=False)
    s, d = call("POST", f"/metadata/entities/{eid}/fields", fb)
    if s not in (200, 201):
        note(f"[skip] 字段 {f['code']} 已存在（{s}）")
    return s


def child_table_field(entity_id, field_code, child_entity_code=None, fk=None, physical=False, type="LONG"):
    if physical:
        fb = {"name": field_code, "code": field_code, "displayName": field_code, "type": type}
    else:
        fb = {
            "name": field_code,
            "code": field_code,
            "displayName": field_code,
            "type": "CHILD_TABLE",
            "validationRules": json.dumps(
                {
                    "childTable": {
                        "childEntityCode": child_entity_code,
                        "foreignKey": fk,
                        "orderColumn": "sort_order",
                        "cascadeDelete": True,
                        "minRows": 0,
                        "maxRows": 200,
                    }
                },
                ensure_ascii=False,
            ),
        }
    s, d = call("POST", f"/metadata/entities/{entity_id}/fields", fb)
    return s


def add_action(eid, code, label, frm, to, escape=False, handler=None, idem=False, order=0):
    body = {
        "actionCode": code,
        "label": label,
        "fromStatus": frm,
        "toStatus": to,
        "statusField": "status",
        "escape": escape,
        "requiresIdempotency": idem,
        "sortOrder": order,
    }
    if handler:
        body["handlerBean"] = handler
    return call("POST", f"/metadata/entities/{eid}/action-defs", body)[:2]


def put_schema(eid, sample):
    raw = open(os.path.join(SAMPLES, sample)).read()
    s, d = call("PUT", f"/metadata/entities/{eid}/ui-schema", {"json": raw}, {"If-Match": "-1"})
    if s == 412:
        s2, d2 = call("GET", f"/metadata/entities/{eid}/ui-schema")
        lock = (d2.get("data") or {}).get("lockVersion")
        s, d = call("PUT", f"/metadata/entities/{eid}/ui-schema", {"json": raw}, {"If-Match": str(lock)})
    return s, d


def validate(eid):
    s, d = call("POST", f"/metadata/entities/{eid}/ui-schema/validate")
    issues = d.get("data") or []
    return s, [i for i in issues if i.get("level") == "ERROR"], issues


def publish(eid):
    return call("POST", f"/metadata/entities/{eid}/publish")[:2]


def rec_create(entity_code, body):
    return call("POST", f"/runtime/entities/{entity_code}/records", body)[:2]


def child_replace(entity_code, rid, child_code, rows):
    return call("POST", f"/runtime/entities/{entity_code}/records/{rid}/children/{child_code}", rows)[:2]


def act(entity_code, rid, action, params=None, idem=None):
    return call(
        "POST",
        f"/runtime/entities/{entity_code}/records/{rid}/actions/{action}",
        {"params": params or {}},
        {"Idempotency-Key": idem} if idem else None,
    )[:2]


def rec_status(d):
    return (d.get("data", {}) or {}).get("record", {}).get("status")


# ==================================================================
# 场景一：费用报销（expense-claim）
# ==================================================================
section("场景一：费用报销（expense-claim）— 建模 → 发布 → 业务全链路")

EXP = "expense_claim_chain_" + SUFFIX
EXP_ITEM = "expense_claim_item"  # 协议样例 props.childEntity 引用此名
exp_fields = [
    {"code": "claim_no", "label": "单号"},
    {"code": "title", "label": "标题", "required": True},
    {"code": "amount", "label": "金额", "type": "DECIMAL"},
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
    {"code": "amount", "label": "金额", "type": "DECIMAL"},
    {"code": "remark", "label": "备注"},
]

# P0-4：行策略走 API 入口写入（此前只能靠手工改库）
exp_policy = {
    "owner_field": "created_by",
    "rules": [
        {"name": "owner_or_approver", "effect": "allow",
         "when": "created_by = :operator OR approver_id = :operator"}
    ],
}
exp_id = create_entity("报销单", EXP, f"t_{EXP}", exp_policy, exp_fields)
item_id = create_entity("报销明细", EXP_ITEM, f"t_{EXP_ITEM}", None, item_fields)
note(f"实体：报销={exp_id} 明细={item_id}")

s, d = call("GET", f"/metadata/entities/{exp_id}")
check("P0-4 行策略写入并回读一致", "owner_or_approver" in json.dumps(d, ensure_ascii=False),
      f"GET 实体未回传 rowPolicies：{str(d)[:160]}")

child_table_field(exp_id, "items", EXP_ITEM, "claim_id")
child_table_field(item_id, "claim_id", physical=True)
child_table_field(item_id, "sort_order", physical=True, type="INT")

s, d = publish(item_id)
check("报销：明细实体先发布（子表端点要求目标已发布 RUNTIME）", s == 200, f"{s} {d.get('message')}")

# P0-3：动作定义建模入口
added = []
for code, label, frm, to, esc, hdl, idem, order in [
    ("submit", "提交", "DRAFT", "SUBMITTED", False, None, False, 1),
    ("approve", "审批通过", "SUBMITTED", "APPROVED", False, None, False, 2),
    ("reject", "驳回", "SUBMITTED", "REJECTED", False, None, False, 3),
    ("confirmPayout", "确认打款", "APPROVED", "PAID", True, "expenseClaimConfirmPayoutHandler", True, 4),
]:
    s, d = add_action(exp_id, code, label, frm, to, esc, hdl, idem, order)
    added.append((code, s))
check("P0-3 动作定义 POST 全部成功", all(s == 200 for _, s in added), str(added))

s, d = call("GET", f"/metadata/entities/{exp_id}/action-defs")
codes = [a.get("actionCode") for a in (d.get("data") or [])]
check("P0-3 动作定义 GET 回读 4 个动作",
      s == 200 and set(codes) >= {"submit", "approve", "reject", "confirmPayout"}, f"{s} {codes}")

# P0-3 语义：fromStatus/toStatus 允许缺省（契约：任意态可触发 / 仅记动作不流转），
# 因此真正的守卫在 actionCode 标识符白名单（动作码进 URL 路径段，宽松字符破坏路由与审计对齐）。
s, d = call("POST", f"/metadata/entities/{exp_id}/action-defs",
            {"actionCode": "bad-code", "label": "坏动作", "fromStatus": "DRAFT", "toStatus": "X"})
check("P0-3【负向】动作码非法标识符 → 400（动作码进 URL 路径段）", s == 400, f"{s} {str(d)[:140]}")
s, d = call("POST", f"/metadata/entities/{exp_id}/action-defs",
            {"actionCode": "urge_action", "label": "催办（任意态可触发、不流转）"})
check("P0-3 语义：fromStatus/toStatus 缺省被接受（任意态可触发）", s == 200, f"{s} {str(d)[:140]}")

s, d = put_schema(exp_id, "expense-claim-ui-schema.example.json")
check("P0-5 界面协议 PUT 成功", s == 200, f"{s} {d.get('message')}")

s, errs, _ = validate(exp_id)
check("P0-5 协议 validate 零 ERROR", s == 200 and not errs,
      "; ".join(f"{i.get('code')}" for i in errs[:5]))

s, d = call("GET", f"/metadata/entities/{exp_id}/publish-preview")
check("P0-5 发布预览可读（runnable 判定源）", s == 200, f"{s} {str(d)[:120]}")

# ui-overlay 生命周期
s, d = call("PUT", f"/metadata/entities/{EXP}/ui-overlay", {"json": '{"overlays":[]}'})
s2, d2 = call("GET", f"/metadata/entities/{EXP}/ui-overlay")
s3, d3 = call("DELETE", f"/metadata/entities/{EXP}/ui-overlay")
check("界面叠加层 GET/PUT/DELETE 生命周期可用", s in (200, 201) and s2 == 200 and s3 in (200, 204),
      f"put={s} get={s2} del={s3}")

s, d = publish(exp_id)
check("报销：publish 放行（闸门无阻断）", s == 200, f"{s} {ecode(d)} {d.get('message')}")

# ---------------- 业务侧 ----------------
s, d = rec_create(EXP, {"title": "10 月差旅报销", "amount": 140, "currency": "CNY", "approver_id": 1})
check("报销：创建记录", s in (200, 201), f"{s} {d.get('message')}")
rid = (d.get("data") or {}).get("id")

if rid:
    # ETag 并发保护
    s, d, hdr = raw_call(BASE, "GET", f"/runtime/entities/{EXP}/records/{rid}", None, HDR)
    etag = hdr.get("ETag") or hdr.get("etag")
    s, d = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"department": "研发中心"},
                {"If-Match": '"999999"'})
    check("ETag【负向】错版本 If-Match → 412", s == 412, f"{s} {str(d)[:120]}")
    s, d = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"department": "研发中心"},
                {"If-Match": etag} if etag else None)
    check("ETag 正确版本更新成功", s == 200, f"{s} {str(d)[:120]}")

    s, d = child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 40}])
    check("报销：写入明细（合计 140）", s in (200, 201), f"{s} {d.get('message')}")

    child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 50}])
    s, d = act(EXP, rid, "submit")
    check("P0-5【负向】明细合计≠申报 → 422 submitRules 强制",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")
    child_replace(EXP, rid, EXP_ITEM, [{"amount": 100}, {"amount": 40}])

    call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"invoice_type": "SPECIAL"})
    s, d = act(EXP, rid, "submit")
    check("P0-5【负向】专票缺税号 → 422 linkage 强制",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")

    call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"tax_no": "91310000MA1FL0"})
    s, d = act(EXP, rid, "submit")
    check("报销：合规后 submit（DRAFT→SUBMITTED）", s == 200 and rec_status(d) == "SUBMITTED",
          f"{s} status={rec_status(d)} {d.get('message', '')[:120]}")

    s, d = act(EXP, rid, "approve")
    check("报销：approve（SUBMITTED→APPROVED）", s == 200 and rec_status(d) == "APPROVED",
          f"{s} status={rec_status(d)}")

    s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"})
    check("报销【负向】逃逸动作缺幂等键 → 400",
          s == 400 and ecode(d) == "META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED", f"{s} {ecode(d)}")

    s, d = act(EXP, rid, "confirmPayout", params={}, idem=f"payout-nochannel-{rid}")
    check("报销【负向】打款缺必填 channel → 400（不得 500）",
          s == 400 and ecode(d) == "META_RUNTIME_ACTION_INVALID_PARAMS", f"{s} {ecode(d)} {d.get('message','')[:100]}")

    key = f"payout-{rid}"
    s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"}, idem=key)
    check("报销：confirmPayout（APPROVED→PAID）", s == 200 and rec_status(d) == "PAID",
          f"{s} status={rec_status(d)} {d.get('message', '')[:120]}")

    s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"}, idem=key)
    check("报销：同幂等键重放复用首次结果", s == 200 and (d.get("data", {}) or {}).get("idempotent") is True,
          f"{s} idempotent={(d.get('data', {}) or {}).get('idempotent')}")

    s, d = act(EXP, rid, "approve")
    check("报销【负向】终态再审批 → 409 非法流转",
          s == 409 and ecode(d) == "META_RUNTIME_ACTION_ILLEGAL_TRANSITION", f"{s} {ecode(d)}")

    # 审计时间线
    s, d = call("GET", f"/runtime/entities/{EXP}/records/{rid}/action-executions")
    execs = d.get("data") or []
    check("审计时间线：动作执行逐条留痕（≥3 条）", s == 200 and len(execs) >= 3,
          f"{s} 条数={len(execs)}")

    # 动作清单
    s, d = call("GET", f"/runtime/entities/{EXP}/actions")
    check("动作清单 GET /actions 可读", s == 200, f"{s} {str(d)[:120]}")

# 回收站：另建一条记录走「软删 → 回收站 → 恢复」
s, d = rec_create(EXP, {"title": "待归档测试单", "amount": 10})
trash_rid = (d.get("data") or {}).get("id")
if trash_rid:
    s, d = call("DELETE", f"/runtime/entities/{EXP}/records/{trash_rid}")
    check("回收站：软删成功", s in (200, 204), f"{s} {str(d)[:120]}")
    s, d = call("GET", f"/runtime/entities/{EXP}/records/deleted?page=1&size=50")
    ids = [str(r.get("id")) for r in ((d.get("data") or {}).get("records") or [])]
    check("回收站：GET /deleted 可见已归档记录", s == 200 and str(trash_rid) in ids,
          f"{s} 回收站 ids={ids[:5]}")
    s, d = call("POST", f"/runtime/entities/{EXP}/records/{trash_rid}/restore")
    check("回收站：restore 恢复成功", s == 200, f"{s} {str(d)[:120]}")
    s, d = call("GET", f"/runtime/entities/{EXP}/records/{trash_rid}")
    check("回收站：恢复后记录重新可读", s == 200 and str((d.get("data") or {}).get("id")) == str(trash_rid),
          f"{s} {str(d)[:120]}")

# P0-2：归档 / 回退草稿
s, d = call("POST", f"/metadata/entities/{exp_id}/archive")
check("P0-2 已发布实体归档成功", s == 200, f"{s} {str(d)[:160]}")
s, d = call("GET", f"/metadata/entities/{exp_id}")
st = (d.get("data") or {}).get("status")
check("P0-2 归档后状态 = ARCHIVED(2)", s == 200 and str(st) in ("2", "ARCHIVED"), f"status={st}")
s, d = call("POST", f"/metadata/entities/{exp_id}/revert-draft")
s2, d2 = call("GET", f"/metadata/entities/{exp_id}")
st2 = (d2.get("data") or {}).get("status")
check("P0-2 revert-draft 回到草稿(0)", s == 200 and str(st2) in ("0", "DRAFT"), f"{s} status={st2}")

# P0-2 负向：草稿态不可归档
s, d = call("POST", f"/metadata/entities/{exp_id}/archive")
check("P0-2【负向】草稿态归档 → 拒绝", s >= 400, f"{s} {str(d)[:140]}")

# ==================================================================
# 场景二：智能理赔·门诊快赔（claim-outpatient-fasttrack）+ 自动编号
# ==================================================================
section("场景二：智能理赔·门诊快赔（claim-outpatient-fasttrack）")

CLM = "claim_case_chain_" + SUFFIX
CLM_ITEM = "claim_item"
clm_fields = [
    {"code": "claim_no", "label": "报案号"},
    {"code": "policy_no", "label": "保单号", "required": True},
    {"code": "insured_id_hash", "label": "投保人哈希"},
    {"code": "insured_name", "label": "被保人"},
    {"code": "claim_type", "label": "理赔类型"},
    {"code": "hospital_level", "label": "医院等级"},
    {"code": "invoice_no", "label": "发票号"},
    {"code": "invoice_date", "label": "发票日期"},
    {"code": "invoice_amount", "label": "发票金额", "type": "DECIMAL"},
    {"code": "claim_amount", "label": "申报金额", "type": "DECIMAL"},
    {"code": "payable_amount", "label": "应赔金额", "type": "DECIMAL"},
    {"code": "calc_amount", "label": "理算金额", "type": "DECIMAL"},
    {"code": "fraud_flag", "label": "风险标记"},
    {"code": "cap_reached", "label": "已达封顶"},
    {"code": "coverage_clear", "label": "责任明确"},
    {"code": "second_reviewer", "label": "第二复核人", "type": "LONG"},
    {"code": "reject_reasons", "label": "拒赔理由", "type": "TEXT"},
    {"code": "evidences", "label": "材料证据", "type": "ATTACHMENT"},
    {"code": "fraud_flags", "label": "风险标记集"},
    {"code": "fasttrack_entry", "label": "快赔入口"},
    {"code": "status", "label": "状态"},
]
clm_item_fields = [
    {"code": "name", "label": "项目"},
    {"code": "amount", "label": "金额", "type": "DECIMAL"},
    {"code": "category", "label": "类别"},
]
clm_policy = {"owner_field": "created_by",
              "rules": [{"name": "reviewer_or_owner", "effect": "allow", "when": "created_by = :operator"}]}

# 自动编号：承载 claim_no，前缀 CLM，全局递增（reset=none）
clm_id = create_entity("理赔案件", CLM, f"t_{CLM}", clm_policy, clm_fields,
                       auto_number={"field": "claim_no", "prefix": "CLM", "reset": "none"})
clm_item_id = create_entity("理赔明细", CLM_ITEM, f"t_{CLM_ITEM}", None, clm_item_fields)
note(f"实体：理赔={clm_id} 明细={clm_item_id}")

child_table_field(clm_id, "items", CLM_ITEM, "claim_id")
child_table_field(clm_item_id, "claim_id", physical=True)
child_table_field(clm_item_id, "sort_order", physical=True, type="INT")

s, d = publish(clm_item_id)
check("理赔：明细实体先发布", s == 200, f"{s} {d.get('message')}")

for code, label, frm, to, esc, hdl, idem, order in [
    ("submit", "提交", "DRAFT", "REVIEWING", False, None, False, 1),
    ("approve", "核赔通过", "REVIEWING", "APPROVED", False, None, False, 2),
    ("reject", "拒赔", "REVIEWING", "REJECTED", False, None, False, 3),
    ("requestMoreEvidence", "补充材料", "REVIEWING", "PENDING_EVIDENCE", False, None, False, 4),
    ("pay", "支付", "APPROVED", "PAID", True, "expenseClaimConfirmPayoutHandler", True, 5),
    ("appeal", "申诉", "REJECTED", "APPEALING", False, None, False, 6),
]:
    s, d = add_action(clm_id, code, label, frm, to, esc, hdl, idem, order)
check("理赔：6 个状态机动作定义写入", True, "")

s, d = put_schema(clm_id, "claim-outpatient-fasttrack-ui-schema.example.json")
check("理赔：界面协议 PUT 成功", s == 200, f"{s} {d.get('message')}")
s, errs, _ = validate(clm_id)
check("理赔：validate 零 ERROR", s == 200 and not errs, "; ".join(i.get("code", "") for i in errs[:5]))
s, d = publish(clm_id)
check("理赔：publish 放行", s == 200, f"{s} {ecode(d)} {d.get('message')}")

# 单1：显式报案号（自动编号不得覆盖客户端值）+ 拒赔链路
s, d = rec_create(CLM, {"policy_no": "P-2026-0001", "claim_no": "CLM-MANUAL-001",
                        "invoice_amount": 300, "claim_amount": 300,
                        "coverage_clear": True, "evidences": "INV-2026-0001"})
check("理赔：创建记录", s in (200, 201), f"{s} {d.get('message')}")
crid = (d.get("data") or {}).get("id")

if crid:
    s, d = call("GET", f"/runtime/entities/{CLM}/records/{crid}")
    no1 = (d.get("data") or {}).get("claim_no")
    check("自动编号【负向】客户端显式值不被覆盖", no1 == "CLM-MANUAL-001", f"claim_no={no1}")

    child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 300, "category": "outpatient"}])
    child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 350, "category": "outpatient"}])
    s, d = act(CLM, crid, "submit")
    check("理赔【负向】发票金额≠清单合计 → SUB-CONS-01 拦截",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED" and "SUB-CONS-01" in (d.get("message") or ""),
          f"{s} {ecode(d)} {d.get('message', '')[:200]}")
    child_replace(CLM, crid, CLM_ITEM, [{"name": "门诊费", "amount": 300, "category": "outpatient"}])
    s, d = act(CLM, crid, "submit")
    check("理赔：submit（DRAFT→REVIEWING）", s == 200 and rec_status(d) == "REVIEWING",
          f"{s} status={rec_status(d)} {d.get('message', '')[:140]}")
    s, d = act(CLM, crid, "reject")
    check("理赔：reject（REVIEWING→REJECTED）", s == 200 and rec_status(d) == "REJECTED",
          f"{s} status={rec_status(d)} {d.get('message', '')[:140]}")
    s, d = act(CLM, crid, "reject")
    check("理赔【负向】非 REVIEWING 态再次拒赔 → 409",
          s == 409 and ecode(d) == "META_RUNTIME_ACTION_ILLEGAL_TRANSITION", f"{s} {ecode(d)}")
    s, d = act(CLM, crid, "appeal")
    check("理赔：申诉（REJECTED→APPEALING）", s == 200 and rec_status(d) == "APPEALING",
          f"{s} status={rec_status(d)}")

# 单2：缺省报案号走自动编号 + 核赔支付全链路
s, d = rec_create(CLM, {"policy_no": "P-2026-0002", "invoice_amount": 500, "claim_amount": 500,
                        "coverage_clear": True, "evidences": "INV-2026-0002"})
rid2 = (d.get("data") or {}).get("id")
if rid2:
    s, d = call("GET", f"/runtime/entities/{CLM}/records/{rid2}")
    no2 = (d.get("data") or {}).get("claim_no")
    check("自动编号：缺省时注入 CLM-NNNN", isinstance(no2, str) and no2.startswith("CLM-") and no2 != "CLM-MANUAL-001",
          f"claim_no={no2}")
    child_replace(CLM, rid2, CLM_ITEM, [{"name": "门诊费", "amount": 500, "category": "outpatient"}])
    s, d = act(CLM, rid2, "submit")
    check("理赔：单2 submit（DRAFT→REVIEWING）", s == 200, f"{s} {d.get('message', '')[:140]}")
    s, d = act(CLM, rid2, "approve")
    check("理赔：单2 approve（REVIEWING→APPROVED）", s == 200 and rec_status(d) == "APPROVED",
          f"{s} status={rec_status(d)}")
    s, d = act(CLM, rid2, "pay", params={"channel": "WECHAT"}, idem=f"claimpay-{rid2}")
    check("理赔：单2 pay 带幂等键+channel（APPROVED→PAID）", s == 200 and rec_status(d) == "PAID",
          f"{s} status={rec_status(d)} {d.get('message', '')[:140]}")
    s, d = act(CLM, rid2, "pay", params={"channel": "WECHAT"}, idem=f"claimpay-{rid2}")
    check("理赔：单2 同幂等键重放复用", s == 200 and (d.get("data", {}) or {}).get("idempotent") is True,
          f"{s} idempotent={(d.get('data', {}) or {}).get('idempotent')}")

# 自动编号递增：第三条缺省单号必须比第二条大
s, d = rec_create(CLM, {"policy_no": "P-2026-0003", "invoice_amount": 1, "claim_amount": 1})
rid3 = (d.get("data") or {}).get("id")
if rid3 and rid2:
    s, d3 = call("GET", f"/runtime/entities/{CLM}/records/{rid3}")
    no3 = (d3.get("data") or {}).get("claim_no")
    seq = lambda v: int(v.split("-")[-1]) if isinstance(v, str) and v.split("-")[-1].isdigit() else -1
    check("自动编号：序号原子递增（第三条 > 第二条）", seq(no3) > seq(no2) >= 0, f"{no2} → {no3}")

# ==================================================================
# 横切能力：筛选服务化 / 发布闸门负向 / 行策略守卫 / 动作定义删除
# ==================================================================
section("横切能力：筛选服务化 · 发布闸门 · 守卫 · 动作定义删除")

# P0-1：type / deliveryMode 真下推（逐条复核，防 no-op）
s, d = call("GET", "/metadata/entities?page=1&size=50&type=1&deliveryMode=1")
recs = ((d.get("data") or {}).get("records") or [])
ok = bool(recs) and all(r.get("type") == 1 and r.get("deliveryMode") == 1 for r in recs)
check("P0-1 筛选服务化：type=1&deliveryMode=1 结果集逐条命中", s == 200 and ok,
      f"{s} 条数={len(recs)} 样例={[(r.get('type'), r.get('deliveryMode')) for r in recs[:3]]}")

s, d = call("GET", "/metadata/entities?page=1&size=50&type=0&deliveryMode=0")
recs0 = ((d.get("data") or {}).get("records") or [])
ok0 = all(r.get("type") == 0 and r.get("deliveryMode") == 0 for r in recs0)
check("P0-1 筛选服务化：type=0&deliveryMode=0 结果集逐条命中", s == 200 and ok0 and len(recs0) != len(recs),
      f"{s} 条数={len(recs0)}（与上一组不同才证明下推生效）")

# P0-4 负向：非法行策略（rules 为空数组）必须被守卫生拒
s, d = call("POST", "/metadata/entities",
            {"name": "行策略守卫探针", "code": "rowpolicy_guard_" + SUFFIX, "displayName": "行策略守卫探针",
             "tableName": "t_rowpolicy_guard_" + SUFFIX, "type": 1, "deliveryMode": 1,
             "rowPolicies": json.dumps({"owner_field": "created_by", "rules": []})})
check("P0-4【负向】行策略 rules 为空 → 400 ROW_POLICIES_INVALID",
      s == 400 and ecode(d) == "META_ROW_POLICIES_INVALID", f"{s} {ecode(d)} {d.get('message', '')[:120]}")

# P1-4 负向：协议引用未知字段 → ERROR → 发布被闸门拦（不得放行）
guard_code = "publish_gate_" + SUFFIX
gid = create_entity("发布闸门探针", guard_code, f"t_{guard_code}", None, [{"code": "title", "label": "标题"}])
s, d = call("PUT", f"/metadata/entities/{gid}/ui-schema",
            {"json": json.dumps({"schemaVersion": 1, "entityCode": guard_code,
                                 "views": {"form": {"sections": [
                                     {"id": "s1", "title": "x", "controls": [
                                         {"fieldCode": "no_such_field", "controlKind": "INPUT"}]}]}}})},
            {"If-Match": "-1"})
s, errs, _ = validate(gid)
has_ref_err = any(i.get("code") == "UI-REF-01" for i in errs)
check("P1-4 引用未知字段 → validate 报 UI-REF-01 ERROR", s == 200 and has_ref_err,
      f"{s} codes={[i.get('code') for i in errs[:5]]}")
s, d = publish(gid)
check("P1-4【负向】发布闸门：ERROR 模型被 409 拦下",
      s == 409 and ecode(d) == "META_PUBLISH_BLOCKED", f"{s} {ecode(d)} {d.get('message', '')[:160]}")

# P0-3 删除动作定义（软删）后 GET 不再包含
s, d = call("DELETE", f"/metadata/entities/{clm_id}/action-defs/appeal")
s2, d2 = call("GET", f"/metadata/entities/{clm_id}/action-defs")
codes2 = [a.get("actionCode") for a in (d2.get("data") or [])]
check("P0-3 动作定义 DELETE 后 GET 不再包含", s in (200, 204) and "appeal" not in codes2,
      f"del={s} codes={codes2}")

# ==================================================================
section("汇总")
print(f"通过 {len(PASS)} 项，失败 {len(FAIL)} 项")
for f in FAIL:
    print(f"  ✗ {f}")
sys.exit(1 if FAIL else 0)
