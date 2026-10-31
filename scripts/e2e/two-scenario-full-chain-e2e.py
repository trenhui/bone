#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""报销 + 智能理赔 + 采购订单 三样板场景「全功能」端到端串联（经网关，模拟人工建模与业务操作）。

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

测试数据：全部取 e2e_lib.fixtures 数据集（同后缀恒定）——
  单号 ds.doc_no("LP"/"POL"/"INV"/"CT")（报销 BX 语义由标题承载，采购走 PO- 自动编号），
  金额 ds.amount() + ds.split() 守恒，人名/部门/城市/税号/客户/供应商同词表，
  业务日期 ds.days_ago() 倒推 ⇒ 可读、像生产、能串链。

用法：
  python3 scripts/e2e/two-scenario-full-chain-e2e.py            # 默认走网关 :8888（真联调）
  BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 python3 ...        # 直连后端排查用
  BONE_E2E_KEEP_RESIDUE=1 python3 ...                            # 跑完保留残留现场（默认自动清理）
"""

import json
import os

from e2e_lib import config, http, report, teardown
from e2e_lib import fixtures as fx
from e2e_lib.http import call, call_as, d_, ecode, jwt_user_id, login_as, session_headers
from e2e_lib.modeling import (
    add_action,
    child_table_field,
    create_entity,
    publish,
    put_schema,
    validate,
)
from e2e_lib.report import check, note, section
from e2e_lib.runtime import NO_IDEM, act, act_as, child_replace, rec_create, rec_status

SUFFIX = config.run_suffix()  # 运行级后缀：所有产物带它，teardown 按后缀定点回收
ds = fx.dataset(SUFFIX)       # 生产仿真数据集：同后缀恒定，单号/金额/人名/部门/日期全部取它

# 主会话（admin）登录：失败即退出——没登录跑什么都是假红
http.login()


# ---------------------------------------------------------------- 第二审批人
# 为什么必须准备第二个账号：工作流引擎有两条**互相独立**的职责分离（SoD）守卫，
#   ① 自审批守卫（WorkflowEngineImpl#isSelfApproval）：审批人 == 发起人 ⇒ 实例挂起，不派单；
#   ② 任务级 SoD（PurchaseWorkflowDecisionHandler#requirePendingTaskOf）：操作人 != 任务 assignee ⇒ 403。
# 单账号下这两条互斥：审批人要 ≠ 发起人(admin=1)，又必须 == 操作人(也是 1) ⇒ 采购链恒不可能走通。
# 故准备一个独立审批账号：submit 用 admin，approve/reject 用该账号的 token。
APPROVER_USER = os.environ.get("BONE_E2E_APPROVER_USER", "e2e_po_appr2")
APPROVER_PWD = os.environ.get("BONE_E2E_APPROVER_PWD", "E2eApprover#2026")
# 归属部门：CreateAccountReq.deptId 必填且须为本租户已存在部门（默认取种子部门，可用环境变量覆盖）
APPROVER_DEPT_ID = int(os.environ.get("BONE_E2E_APPROVER_DEPT_ID", "758345112240521216"))


def ensure_approver():
    """幂等准备审批账号，返回 (token, userId)；失败返回 (None, None)，调用方据此跳过而非静默假绿。"""
    tok = login_as(APPROVER_USER, APPROVER_PWD)
    if not tok:
        s, d = call("POST", "/iam/accounts", {
            "username": APPROVER_USER, "password": APPROVER_PWD,
            # realName 用数据集固定第二人（自然中文名），与采购员等人名同词表
            "email": APPROVER_USER + "@bone.local", "realName": ds.manager,
            "tenantId": 0, "deptId": APPROVER_DEPT_ID,
            # roleIds：1=平台角色（runtime:* 等）、3=唯一持有 metadata:runtime:write 的角色；
            # RuntimeActionController#invoke 的 @PreAuthorize 只认 metadata:runtime:write / metadata:write，
            # 缺了它审批人连控制器都进不去（COMMON_FORBIDDEN），根本走不到任务级 SoD。
            "roleIds": [1, 3],
        })
        if s != 200:
            return None, None
        tok = login_as(APPROVER_USER, APPROVER_PWD)
    # 转 int：approver_id 字段按 LONG 建模，下发字符串会被运行时校验拒（「应为数值类型」）。
    return tok, (jwt_user_id(tok) if tok else None)


APPROVER_TOKEN, APPROVER_ID = ensure_approver()
APPROVER_HDR = session_headers(APPROVER_TOKEN)


# ---- 运行级残留 teardown ----
# 本次运行会在库里留下：带 <SUFFIX> 的动态物理表 + meta_entity 定义及子表 + 工作流行
# + 本脚本创建的审批账号 e2e_po_appr2。默认退出时（含异常/sys.exit）自动回收，
# 防止 E2E 反复联调把库越堆越脏；BONE_E2E_KEEP_RESIDUE=1 跳过清理、保留现场供排查。
teardown.register(SUFFIX, accounts=(APPROVER_USER,))


# ==================================================================
# 场景一：费用报销（expense-claim）
# ==================================================================
section("场景一：费用报销（expense-claim）— 建模 → 发布 → 业务全链路")

EXP = "expense_reimbursement_chain_" + SUFFIX
EXP_ITEM = "expense_reimbursement_item"  # 协议样例 props.childEntity 引用此名
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
    ("confirmPayout", "确认打款", "APPROVED", "PAID", True, "expenseReimbursementConfirmPayoutHandler", True, 4),
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
# 生产仿真数据：标题/人名/部门/日期/税号/金额全部取数据集，主表与明细共用同一批变量（防 rng 各抽各的对不上）
# 注意：ds.city / ds.dept 是数据集恒定属性（实例属性遮蔽同名方法），不带括号
EXP_CITY = ds.city
EXP_PERSON = ds.person()
EXP_DEPT = ds.dept
EXP_CUSTOMER = ds.customer["name"]  # 数据集共享客户：明细备注引用它，与采购/理赔串成同一条链
EXP_DATE = ds.days_ago(6)
EXP_START = ds.days_ago(9)
EXP_END = ds.days_ago(4)
EXP_TAX = ds.tax_no()
EXP_TITLE = f"{EXP_CITY}{int(EXP_START[5:7])}月客户拜访差旅报销"
EXP_TOTAL = ds.amount(lo=300, hi=6000)               # 主表申报金额（非圆整、带分位）
EXP_PARTS = ds.split(EXP_TOTAL, 2)                   # 守恒明细 Σ == EXP_TOTAL（过 submitRules）
EXP_PARTS_BAD = ds.split(EXP_TOTAL, 2, break_=True)  # 破坏版 Σ == EXP_TOTAL + 1.01（422 负向专用）
EXP_REMARKS = [f"拜访{EXP_CUSTOMER}住宿费", f"拜访{EXP_CUSTOMER}往返高铁票"]
# 共享明细实体 expense_reimbursement_item（协议样例 props.childEntity 固定引用此名，无法加运行后缀）
# 于 2026-10-28 被迁移种子补了必填「消费说明」note——明细负载必须带 note，否则 400 拦下全部
# 明细写入、下游 submit 断言级联假红（每个值只生成一次存变量，正负两版共用同一批 note）
EXP_ITEM_NOTES = [f"拜访{EXP_CUSTOMER}住宿费用说明（{EXP_DATE}）",
                  f"拜访{EXP_CUSTOMER}往返交通费用说明（{EXP_DATE}）"]
EXP_ITEMS = [{"expense_date": EXP_DATE, "amount": p, "remark": r, "note": n}
             for p, r, n in zip(EXP_PARTS, EXP_REMARKS, EXP_ITEM_NOTES)]
EXP_ITEMS_BAD = [{"expense_date": r["expense_date"], "amount": p,
                  "remark": r["remark"], "note": n}
                 for r, p, n in zip(EXP_ITEMS, EXP_PARTS_BAD, EXP_ITEM_NOTES)]

s, d = rec_create(EXP, {"title": EXP_TITLE, "amount": EXP_TOTAL, "currency": "CNY",
                        "applicant": EXP_PERSON, "department": EXP_DEPT,
                        "expense_date": EXP_DATE, "expense_start": EXP_START,
                        "expense_end": EXP_END, "approver_id": 1})
check("报销：创建记录", s in (200, 201), f"{s} {d.get('message')}")
rid = (d.get("data") or {}).get("id")

if rid:
    # ETag 并发保护
    s, d, hdr = http.call_full("GET", f"/runtime/entities/{EXP}/records/{rid}")
    etag = hdr.get("ETag") or hdr.get("etag")
    s, d = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"department": EXP_DEPT},
                {"If-Match": '"999999"'})
    check("ETag【负向】错版本 If-Match → 412", s == 412, f"{s} {str(d)[:120]}")
    s, d = call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"department": EXP_DEPT},
                {"If-Match": etag} if etag else None)
    check("ETag 正确版本更新成功", s == 200, f"{s} {str(d)[:120]}")

    s, d = child_replace(EXP, rid, EXP_ITEM, EXP_ITEMS)
    check(f"报销：写入明细（合计 {EXP_TOTAL}）", s in (200, 201), f"{s} {d.get('message')}")

    child_replace(EXP, rid, EXP_ITEM, EXP_ITEMS_BAD)
    s, d = act(EXP, rid, "submit")
    check("P0-5【负向】明细合计≠申报 → 422 submitRules 强制",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")
    child_replace(EXP, rid, EXP_ITEM, EXP_ITEMS)

    call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"invoice_type": "SPECIAL"})
    s, d = act(EXP, rid, "submit")
    check("P0-5【负向】专票缺税号 → 422 linkage 强制",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")

    call("PUT", f"/runtime/entities/{EXP}/records/{rid}", {"tax_no": EXP_TAX})
    s, d = act(EXP, rid, "submit")
    check("报销：合规后 submit（DRAFT→SUBMITTED）", s == 200 and rec_status(d) == "SUBMITTED",
          f"{s} status={rec_status(d)} {d.get('message', '')[:120]}")

    s, d = act(EXP, rid, "approve")
    check("报销：approve（SUBMITTED→APPROVED）", s == 200 and rec_status(d) == "APPROVED",
          f"{s} status={rec_status(d)}")

    s, d = act(EXP, rid, "confirmPayout", params={"channel": "BANK"}, idem=NO_IDEM)
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
TRASH_TITLE = f"{EXP_CITY}客户答谢礼品报销"
TRASH_AMOUNT = ds.amount(lo=50, hi=800)
s, d = rec_create(EXP, {"title": TRASH_TITLE, "amount": TRASH_AMOUNT})
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
    {"code": "reviewer_id", "label": "核赔人", "type": "LONG"},
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
# 明细实体物理表用样例后缀隔离：t_claim_item 是 bone-init.sql（DDL 真源）里 synap-claim 模块的产品表，
# 样例绝不能再绑它（列名不同 → 子表写入静默失败）。实体 code 仍为 claim_item（样例 props.childEntity 引用此名）。
clm_item_id = create_entity("理赔明细", CLM_ITEM, f"t_claim_item_e2e_{SUFFIX}", None, clm_item_fields)
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
    ("pay", "支付", "APPROVED", "PAID", True, "expenseReimbursementConfirmPayoutHandler", True, 5),
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
# 生产仿真数据：报案号/保单号/发票号走「业务前缀-日期-序号」，被保人数据集内固定（两张单串同一人）
CLM_INSURED = ds.employee
CLM_INVOICE_DATE = ds.days_ago(3)
CLM_NO1 = ds.doc_no("LP")
CLM1_POLICY = ds.doc_no("POL")
CLM1_EVIDENCE = ds.doc_no("INV")
CLM1_INV = ds.amount(lo=300, hi=4000)                 # 发票金额 < 1 万，避开 LINK-DUAL-01 双人复核
CLM1_PARTS = ds.split(CLM1_INV, 1)                    # 守恒清单 Σ == 发票金额
CLM1_PARTS_BAD = ds.split(CLM1_INV, 1, break_=True)   # 破坏版差 1.01 > tolerance(1) ⇒ SUB-CONS-01 拦下
CLM1_ITEMS = [{"name": "门诊诊查费", "amount": p, "category": "outpatient"} for p in CLM1_PARTS]
CLM1_ITEMS_BAD = [dict(r, amount=p) for r, p in zip(CLM1_ITEMS, CLM1_PARTS_BAD)]
s, d = rec_create(CLM, {"policy_no": CLM1_POLICY, "claim_no": CLM_NO1,
                        "insured_name": CLM_INSURED,
                        "invoice_no": CLM1_EVIDENCE, "invoice_date": CLM_INVOICE_DATE,
                        "invoice_amount": CLM1_INV, "claim_amount": CLM1_INV,
                        "coverage_clear": True, "evidences": CLM1_EVIDENCE})
check("理赔：创建记录", s in (200, 201), f"{s} {d.get('message')}")
crid = (d.get("data") or {}).get("id")

if crid:
    s, d = call("GET", f"/runtime/entities/{CLM}/records/{crid}")
    no1 = (d.get("data") or {}).get("claim_no")
    check("自动编号【负向】客户端显式值不被覆盖", no1 == CLM_NO1, f"claim_no={no1}")

    child_replace(CLM, crid, CLM_ITEM, CLM1_ITEMS)
    child_replace(CLM, crid, CLM_ITEM, CLM1_ITEMS_BAD)
    s, d = act(CLM, crid, "submit")
    check("理赔【负向】发票金额≠清单合计 → SUB-CONS-01 拦截",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED" and "SUB-CONS-01" in (d.get("message") or ""),
          f"{s} {ecode(d)} {d.get('message', '')[:200]}")
    child_replace(CLM, crid, CLM_ITEM, CLM1_ITEMS)
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
CLM2_POLICY = ds.doc_no("POL")
CLM2_EVIDENCE = ds.doc_no("INV")
CLM2_INV = ds.amount(lo=300, hi=4000)
CLM2_PARTS = ds.split(CLM2_INV, 1)   # 守恒清单 Σ == 发票金额（过 SUB-CONS-01）
CLM2_ITEMS = [{"name": "门诊药品费", "amount": p, "category": "outpatient"} for p in CLM2_PARTS]
s, d = rec_create(CLM, {"policy_no": CLM2_POLICY, "insured_name": CLM_INSURED,
                        "invoice_no": CLM2_EVIDENCE, "invoice_date": CLM_INVOICE_DATE,
                        "invoice_amount": CLM2_INV, "claim_amount": CLM2_INV,
                        "coverage_clear": True, "evidences": CLM2_EVIDENCE})
rid2 = (d.get("data") or {}).get("id")
if rid2:
    s, d = call("GET", f"/runtime/entities/{CLM}/records/{rid2}")
    no2 = (d.get("data") or {}).get("claim_no")
    check("自动编号：缺省时注入 CLM-NNNN", isinstance(no2, str) and no2.startswith("CLM-") and no2 != CLM_NO1,
          f"claim_no={no2}")
    child_replace(CLM, rid2, CLM_ITEM, CLM2_ITEMS)
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
CLM3_POLICY = ds.doc_no("POL")
CLM3_AMOUNT = ds.amount(lo=50, hi=300)
s, d = rec_create(CLM, {"policy_no": CLM3_POLICY, "invoice_amount": CLM3_AMOUNT,
                        "claim_amount": CLM3_AMOUNT})
rid3 = (d.get("data") or {}).get("id")
if rid3 and rid2:
    s, d3 = call("GET", f"/runtime/entities/{CLM}/records/{rid3}")
    no3 = (d3.get("data") or {}).get("claim_no")
    seq = lambda v: int(v.split("-")[-1]) if isinstance(v, str) and v.split("-")[-1].isdigit() else -1
    check("自动编号：序号原子递增（第三条 > 第二条）", seq(no3) > seq(no2) >= 0, f"{no2} → {no3}")

# ==================================================================
# 场景三：采购订单（purchase-order）— 行项目 · 预算 · 三单匹配 · 付款
# ==================================================================
section("场景三：采购订单（purchase-order）— 行项目 · 预算 · 三单匹配 · 付款")

PO = "purchase_order_chain_" + SUFFIX
PO_ITEM = "purchase_item"
po_fields = [
    {"code": "po_no", "label": "采购单号"},
    {"code": "title", "label": "采购事由", "required": True},
    {"code": "vendor_code", "label": "供应商编码"},
    {"code": "vendor", "label": "供应商"},
    {"code": "buyer", "label": "采购员"},
    # 审批人身份口径必须与运行时操作人一致：RuntimeActionContext#getOperatorId 取的是**账户数字 id**
    # （JWT sub 对应的 userId，本地 admin=1），不是登录名。早先此处注释写「须等于操作人 admin」并按
    # 用户名播种 "admin"，而引擎 FIELD 指派把该值原样落 wf_task.assignee_id ⇒ 任务级 SoD 用
    # 「操作人 id(1) == assignee_id(admin)」比对恒假 ⇒ approve 一律 403，整条采购链其余 7 步连锁失败。
    # 报销场景播种的是 1（可过），两处口径必须同源——这是 2026-10-27 联调实测的口径漂移缺陷。
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
# 行策略必须把**审批人**也放进来（与报销场景 owner_or_approver 同口径）：
# 只看创建人 ⇒ 审批人读不到这张单 ⇒ 引擎虽已把待办指派给他，平台仍以 COMMON_FORBIDDEN 拦下，
# 采购审批链无人可批（2026-10-27 联调实测）。审批流实体放行进「被指派审批人」是建模必要条件。
po_policy = {
    "owner_field": "created_by",
    # 规则名即 policyCode，必须与 UI 协议样例 dataScope.policyCode（dept_or_buyer）一致，
    # 改名会让校验报 UI-REF-03（引用了未声明的行策略）⇒ 发布闸门拦下。故保持原名，只放宽 when。
    "rules": [{"name": "dept_or_buyer", "effect": "allow",
               "when": "created_by = :operator OR approver_id = :operator"}],
}

po_id = create_entity("采购订单", PO, f"t_{PO}", po_policy, po_fields,
                      auto_number={"field": "po_no", "prefix": "PO", "reset": "none"})
po_item_id = create_entity("采购行项目", PO_ITEM, f"t_{PO_ITEM}", None, po_item_fields)
note(f"实体：采购订单={po_id} 行项目={po_item_id}")

child_table_field(po_id, "items", PO_ITEM, "order_id")
child_table_field(po_item_id, "order_id", physical=True)
child_table_field(po_item_id, "sort_order", physical=True, type="INT")

s, d = publish(po_item_id)
check("采购：行项目实体先发布", s == 200, f"{s} {d.get('message')}")

for code, label, frm, to, esc, hdl, idem, order in [
    # submit/approve/reject 接采购工作流引擎：必须 is_escape=True，RuntimeActionDispatcher 才会按 handler_bean 分发
    # （handler 副作用：启动/推进审批流；平台状态流转仍由状态机负责）
    ("submit", "提交审批", "DRAFT", "SUBMITTED", True, "purchaseWorkflowSubmitHandler", True, 1),
    ("approve", "审批通过", "SUBMITTED", "APPROVED", True, "purchaseWorkflowDecisionHandler", True, 2),
    ("reject", "驳回", "SUBMITTED", "REJECTED", True, "purchaseWorkflowDecisionHandler", True, 3),
    ("startReceive", "开始收货", "APPROVED", "RECEIVING", False, None, False, 4),
    ("confirmReceive", "确认收货", "RECEIVING", "RECEIVED", False, None, False, 5),
    ("pay", "付款", "RECEIVED", "PAID", True, "purchaseOrderPayHandler", True, 6),
]:
    s, d = add_action(po_id, code, label, frm, to, esc, hdl, idem, order)
    if s != 200:
        note(f"[warn] 动作 {code} 写入 {s} {str(d)[:160]}")

# 付款动作声明入参契约（平台据此校验必填，Handler 不再各自判空）
s, d = call("POST", f"/metadata/entities/{po_id}/action-defs", {
    "actionCode": "pay", "label": "付款", "fromStatus": "RECEIVED", "toStatus": "PAID",
    "escape": True, "handlerBean": "purchaseOrderPayHandler", "requiresIdempotency": True,
    "sortOrder": 6,
    "paramsSchema": json.dumps([
        {"name": "channel", "label": "付款渠道", "type": "enum", "required": True,
         "values": ["BANK", "ACCEPTANCE", "WECHAT"]},
        {"name": "payAmount", "label": "付款金额", "type": "number", "required": True, "min": 0.01},
        {"name": "note", "label": "备注", "type": "string", "required": False},
    ], ensure_ascii=False),
})
check("采购：付款动作声明 params_schema（平台统一校验入参）", s == 200, f"{s} {str(d)[:180]}")

s, d = put_schema(po_id, "purchase-order-ui-schema.example.json")
check("采购：界面协议 PUT 成功", s == 200, f"{s} {d.get('message')}")
s, errs, _ = validate(po_id)
check("采购：validate 零 ERROR", s == 200 and not errs,
      "; ".join(str(i.get("code")) for i in errs[:5]))
s, d = publish(po_id)
check("采购：publish 放行", s == 200, f"{s} {ecode(d)} {d.get('message')}")

# ---------------- 业务侧：建单 → 行项目 → 预算/合计/三单闸门 → 审批 → 收货 → 付款 ----------------
# 生产仿真数据：供应商取数据集 supplier（编码+名称同源串链），主表/行项目/断言共用同一批变量
PO_SUP = ds.supplier
PO_DEPT = ds.dept
PO_BUYER = ds.person()
# 采购金额约束在 v2 流程的「单级审批(SINGLE)档」(≤10000)：本场景验证的是「订单 approver_id 指定
# 的审批人能批」（SINGLE 档按 FIELD approver_id 指派），而非大额会签。>10000 的双人会签
# （purchase_cosign → 固定指派 9104/9105）由 expense-purchase-approval-integration-e2e.py 的
# S10 用例专门覆盖，避免两处重复且本场景误触会签导致审批人拿不到待办（403 连锁）。
PO_AMOUNT = ds.amount(lo=2000, hi=9000)                     # 采购金额（SINGLE 档·非圆整·带分位）
PO_PARTS = ds.split(PO_AMOUNT, 3)                          # 行项目金额守恒：Σ == PO_AMOUNT
PO_PARTS_BAD = ds.split(PO_AMOUNT, 3, break_=True)         # 破坏版 Σ == PO_AMOUNT + 1.01（SUB-ITEMS-01 负向）
PO_TAXES = [round(p * 0.13, 2) for p in PO_PARTS]
TAX_TOTAL = round(sum(PO_TAXES), 2)                        # 税额合计 = Σ 行税额（守恒，过 SUB-TAX-01）
TAX_TOTAL_BAD = ds.split(TAX_TOTAL, 3, break_=True)        # 破坏版行税额 Σ 差 1.01（SUB-TAX-01 负向）
PO_BUDGET = ds.amount(lo=int(PO_AMOUNT * 1.2), hi=int(PO_AMOUNT * 2))   # 合规预算：> 采购金额
PO_BUDGET_LOW = ds.amount(lo=500, hi=int(PO_AMOUNT * 0.6))              # 负向预算：< 采购金额
PO_NEED_DATE = ds.days_ago(-33)                            # 需求日期：运行日后 33 天（days_ago 负值=倒推未来）
PO_CONTRACT = ds.doc_no("CT")
PO_CONTRACT2 = ds.doc_no("CT")
PO_INVOICE_NO = ds.doc_no("INV")
PO_TITLE = f"{PO_DEPT}{int(PO_NEED_DATE[5:7])}月办公设备采购"
PO_LINE_META = [(2, "台"), (4, "件"), (5, "套")]           # 数量/单位（业务输入，行金额以 split 为准）
PO_SKUS = []
for _i in range(3):
    _sku, _ref_price = ds.sku()                            # 品名规格取自词表，参考单价仅作同源参考
    PO_SKUS.append(_sku.rsplit(" ", 1))

ITEMS_OK = []
for _i, _part in enumerate(PO_PARTS):
    _name, _spec = PO_SKUS[_i]
    _qty, _unit = PO_LINE_META[_i]
    ITEMS_OK.append({
        "material_code": "M-%04d" % (1001 * (_i + 1)),
        "material_name": _name, "spec": _spec,
        "qty": _qty, "unit": _unit,
        "unit_price": round(_part / _qty, 2),
        "amount": _part,
        "tax_rate": 0.13, "tax_amount": PO_TAXES[_i],
        "received_qty": 0,
    })
ITEMS_BAD = [dict(r, amount=p) for r, p in zip(ITEMS_OK, PO_PARTS_BAD)]
ITEMS_TAX_BAD = [dict(r, tax_amount=t) for r, t in zip(ITEMS_OK, TAX_TOTAL_BAD)]

s, d = rec_create(PO, {"title": PO_TITLE, "vendor_code": PO_SUP["code"],
                       "vendor": PO_SUP["name"], "buyer": PO_BUYER,
                       "approver_id": APPROVER_ID,
                       "department": PO_DEPT, "need_date": PO_NEED_DATE, "currency": "CNY",
                       "budget_subject": "6401-办公费", "budget_amount": PO_BUDGET,
                       "contract_no": PO_CONTRACT, "payment_terms": "NET30",
                       "po_amount": PO_AMOUNT, "tax_amount": TAX_TOTAL,
                       "received_amount": 0, "invoiced_amount": 0})
check(f"采购：创建订单（预算 {PO_BUDGET} / 采购 {PO_AMOUNT}）", s in (200, 201), f"{s} {d.get('message')}")
prid = (d.get("data") or {}).get("id")

if prid:
    s, d = call("GET", f"/runtime/entities/{PO}/records/{prid}")
    po_no = (d.get("data") or {}).get("po_no")
    check("采购：自动编号注入 PO-NNNN", isinstance(po_no, str) and po_no.startswith("PO-"),
          f"po_no={po_no}")

    # 【负向 1】无行项目 → SUB-COUNT-01
    child_replace(PO, prid, PO_ITEM, [])
    s, d = act(PO, prid, "submit")
    check("采购【负向】无行项目 → 422 SUB-COUNT-01",
          s == 422 and ecode(d) == "META_RUNTIME_UI_RULES_VIOLATED", f"{s} {ecode(d)}")

    # 【负向 2】行项目合计 ≠ 采购金额 → SUB-ITEMS-01（破坏版 split：Σ == 采购金额 + 1.01）
    child_replace(PO, prid, PO_ITEM, ITEMS_BAD)
    s, d = act(PO, prid, "submit")
    check("采购【负向】行项目合计≠采购金额 → 422 SUB-ITEMS-01",
          s == 422 and "SUB-ITEMS-01" in (d.get("message") or ""), f"{s} {d.get('message', '')[:200]}")

    # 【负向 3】税额合计不符 → SUB-TAX-01（破坏版 split：Σ 行税额 == 税额合计 + 1.01）
    child_replace(PO, prid, PO_ITEM, ITEMS_TAX_BAD)
    s, d = act(PO, prid, "submit")
    check("采购【负向】税额合计不符 → 422 SUB-TAX-01",
          s == 422 and "SUB-TAX-01" in (d.get("message") or ""), f"{s} {d.get('message', '')[:200]}")

    # 【负向 4】超预算 → SUB-BUDGET-01 + LINK-BUDGET-01 必填说明
    child_replace(PO, prid, PO_ITEM, ITEMS_OK)
    call("PUT", f"/runtime/entities/{PO}/records/{prid}", {"budget_amount": PO_BUDGET_LOW})
    s, d = act(PO, prid, "submit")
    check("采购【负向】超预算 → 422（SUB-BUDGET-01 且 remark 被 linkage 判必填）",
          s == 422 and "SUB-BUDGET-01" in (d.get("message") or "")
          and "remark" in (d.get("message") or ""), f"{s} {d.get('message', '')[:240]}")

    # 【负向 5】预付款缺合同号 → LINK-PREPAY-01
    call("PUT", f"/runtime/entities/{PO}/records/{prid}",
         {"budget_amount": PO_BUDGET, "payment_terms": "PREPAY", "contract_no": ""})
    s, d = act(PO, prid, "submit")
    check("采购【负向】预付款缺合同号 → 422（contract_no 必填）",
          s == 422 and "contract_no" in (d.get("message") or ""), f"{s} {d.get('message', '')[:240]}")

    # 合规态：恢复 NET30 + 合同号 → submit 放行
    call("PUT", f"/runtime/entities/{PO}/records/{prid}",
         {"payment_terms": "NET30", "contract_no": PO_CONTRACT})
    s, d = act(PO, prid, "submit")
    check("采购：submit（DRAFT→SUBMITTED）", s == 200 and rec_status(d) == "SUBMITTED",
          f"{s} status={rec_status(d)} {d.get('message', '')[:160]}")

    # 审批必须由**审批人本人**的会话发起：引擎任务级 SoD 比对「操作人 id == wf_task.assignee_id」，
    # 而 assignee 由 FIELD approver_id 解析 = APPROVER_ID，故此处不能再用 admin 的 HDR。
    s, d = act_as(APPROVER_HDR, PO, prid, "approve")
    check("采购：approve（SUBMITTED→APPROVED，由第二审批人会话发起）",
          s == 200 and rec_status(d) == "APPROVED", f"{s} status={rec_status(d)}")
    s, d = act(PO, prid, "submit")
    check("采购【负向】已审批再提交 → 409 非法流转",
          s == 409 and ecode(d) == "META_RUNTIME_ACTION_ILLEGAL_TRANSITION", f"{s} {ecode(d)}")

    s, d = act(PO, prid, "startReceive")
    check("采购：startReceive（APPROVED→RECEIVING）", s == 200 and rec_status(d) == "RECEIVING",
          f"{s} status={rec_status(d)} {d.get('message', '')[:200]}")

    # 收货：已收货金额 > 0 但缺发票号 → LINK-RECV-01
    call("PUT", f"/runtime/entities/{PO}/records/{prid}", {"received_amount": PO_AMOUNT})
    s, d = act(PO, prid, "confirmReceive")
    check("采购【负向】已收货缺发票号 → 422（LINK-RECV-01）",
          s == 422 and "invoice_no" in (d.get("message") or ""), f"{s} {d.get('message', '')[:200]}")

    call("PUT", f"/runtime/entities/{PO}/records/{prid}",
         {"invoice_no": PO_INVOICE_NO, "invoiced_amount": PO_AMOUNT})
    s, d = act(PO, prid, "confirmReceive")
    check("采购：confirmReceive（RECEIVING→RECEIVED）", s == 200 and rec_status(d) == "RECEIVED",
          f"{s} status={rec_status(d)} {d.get('message', '')[:160]}")

    # 【负向 6】三单不匹配 → SUB-MATCH-02 拦截付款
    call("PUT", f"/runtime/entities/{PO}/records/{prid}", {"invoiced_amount": PO_AMOUNT - 1000})
    s, d = act(PO, prid, "pay", params={"channel": "BANK", "payAmount": PO_AMOUNT},
               idem=f"popay-mismatch-{prid}")
    check("采购【负向】发票金额≠订单金额 → 422 SUB-MATCH-02（三单匹配拦截付款）",
          s == 422 and "SUB-MATCH-02" in (d.get("message") or ""), f"{s} {d.get('message', '')[:240]}")
    call("PUT", f"/runtime/entities/{PO}/records/{prid}", {"invoiced_amount": PO_AMOUNT})

    # 【负向 7】逃逸动作缺幂等键 → 400
    s, d = act(PO, prid, "pay", params={"channel": "BANK", "payAmount": PO_AMOUNT}, idem=NO_IDEM)
    check("采购【负向】付款缺幂等键 → 400",
          s == 400 and ecode(d) == "META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED", f"{s} {ecode(d)}")

    # 【负向 8】缺必填入参 channel（params_schema 平台校验）→ 400
    s, d = act(PO, prid, "pay", params={"payAmount": PO_AMOUNT}, idem=f"popay-nochannel-{prid}")
    check("采购【负向】付款缺必填 channel → 400（params_schema 平台校验）",
          s == 400 and ecode(d) == "META_RUNTIME_ACTION_INVALID_PARAMS",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")

    # 【负向 9】payAmount 非法（0）→ 400
    s, d = act(PO, prid, "pay", params={"channel": "BANK", "payAmount": 0},
               idem=f"popay-zero-{prid}")
    check("采购【负向】payAmount=0 → 400（min:0.01 约束）",
          s == 400 and ecode(d) == "META_RUNTIME_ACTION_INVALID_PARAMS",
          f"{s} {ecode(d)} {d.get('message', '')[:160]}")

    # 正式付款 + 幂等重放
    key = f"popay-{prid}"
    s, d = act(PO, prid, "pay", params={"channel": "BANK", "payAmount": PO_AMOUNT,
                                        "note": PO_TITLE}, idem=key)
    se = (d.get("data") or {}).get("result") or {}
    check("采购：pay（RECEIVED→PAID）并回传付款流水号",
          s == 200 and rec_status(d) == "PAID" and str(se.get("paymentSerial", "")).startswith("POPAY-"),
          f"{s} status={rec_status(d)} sideEffect={se}")

    s, d = act(PO, prid, "pay", params={"channel": "BANK", "payAmount": PO_AMOUNT}, idem=key)
    check("采购：同幂等键重放复用首次结果", s == 200 and (d.get("data", {}) or {}).get("idempotent") is True,
          f"{s} idempotent={(d.get('data', {}) or {}).get('idempotent')}")

    s, d = call("GET", f"/runtime/entities/{PO}/records/{prid}/action-executions")
    execs = d.get("data") or []
    # 5 条成功动作（submit/approve/startReceive/confirmReceive/pay）留痕；
    # 被 422/400 拦下的动作按设计不入执行日志（未触达状态机，记入审计会污染「谁真的动了单据」）
    check("采购：审计时间线留痕（5 条成功动作）", s == 200 and len(execs) >= 5, f"{s} 条数={len(execs)}")

# 第二张单：超预算但已填写说明 → 不得被 remark 必填拦（说明规则本身是条件命中才强制）
PO2_BUDGET = ds.amount(lo=600, hi=2000)                                  # 追加单预算（小额）
PO2_AMOUNT = ds.amount(lo=int(PO2_BUDGET * 1.2), hi=int(PO2_BUDGET * 3))  # 采购额 > 预算 ⇒ 超预算恒成立
PO2_TITLE = f"{PO_DEPT}临时追加采购"
PO2_SKU, _ref = ds.sku()
PO2_NAME, PO2_SPEC = PO2_SKU.rsplit(" ", 1)
s, d = rec_create(PO, {"title": PO2_TITLE, "vendor_code": PO_SUP["code"], "vendor": PO_SUP["name"],
                       "payment_terms": "NET30", "contract_no": PO_CONTRACT2,
                       "budget_amount": PO2_BUDGET, "po_amount": PO2_AMOUNT, "tax_amount": 0,
                       "remark": "预算外紧急采购，已获分管副总口头批准"})
prid2 = (d.get("data") or {}).get("id")
if prid2:
    child_replace(PO, prid2, PO_ITEM, [{"material_code": "M-9009", "material_name": PO2_NAME,
                                        "spec": PO2_SPEC, "qty": 1, "unit": "台",
                                        "unit_price": PO2_AMOUNT, "amount": PO2_AMOUNT,
                                        "tax_rate": 0, "tax_amount": 0}])
    s, d = act(PO, prid2, "submit")
    check("采购：超预算但已填说明 → 仅 SUB-BUDGET-01 拦截（remark 不再是拦点）",
          s == 422 and "SUB-BUDGET-01" in (d.get("message") or "")
          and "remark" not in (d.get("message") or ""), f"{s} {d.get('message', '')[:240]}")

# 回收站：软删 → /deleted → restore
if prid2:
    s, d = call("DELETE", f"/runtime/entities/{PO}/records/{prid2}")
    check("采购：软删成功", s in (200, 204), f"{s} {str(d)[:120]}")
    s, d = call("GET", f"/runtime/entities/{PO}/records/deleted?page=1&size=50")
    ids = [str(r.get("id")) for r in ((d.get("data") or {}).get("records") or [])]
    check("采购：GET /deleted 可见已归档订单", s == 200 and str(prid2) in ids, f"{s} ids={ids[:5]}")
    s, d = call("POST", f"/runtime/entities/{PO}/records/{prid2}/restore")
    check("采购：restore 恢复成功", s == 200, f"{s} {str(d)[:120]}")

    # 驳回路径验证：另建一张采购单，提交后由审批人驳回（演练 purchaseWorkflowDecisionHandler REJECT 分支）
    PO3_TITLE = f"{PO_DEPT}驳回演练采购"
    # 约束在 v2 单级审批(SINGLE)档(≤10000)：本断言验证「订单审批人 REJECT 分支」，不得误触大额会签
    # （>10000 → purchase_cosign 固定指派 9104/9105，e2e 审批人非待办对象 → 403，由 S10 集成用例覆盖）。
    PO3_AMOUNT = ds.amount(lo=2000, hi=9000)
    PO3_PARTS = ds.split(PO3_AMOUNT, 3)
    PO3_TAXES = [round(p * 0.13, 2) for p in PO3_PARTS]
    PO3_TAX_TOTAL = round(sum(PO3_TAXES), 2)   # 守恒：税额合计 = Σ 行税额（与主单 TAX_TOTAL 同口径，过 SUB-TAX-01；
                                               # 不能写 round(PO3_AMOUNT*0.13,2)，逐行取整与总额取整会漂移 1~2 分）
    PO3_ITEMS = []
    for _i, _part in enumerate(PO3_PARTS):
        _name, _spec = PO_SKUS[_i]
        _qty, _unit = PO_LINE_META[_i]
        PO3_ITEMS.append({
            "material_code": "M-%04d" % (3001 + _i), "material_name": _name, "spec": _spec,
            "qty": _qty, "unit": _unit, "unit_price": round(_part / _qty, 2), "amount": _part,
            "tax_rate": 0.13, "tax_amount": PO3_TAXES[_i], "received_qty": 0,
        })
    s, d = rec_create(PO, {"title": PO3_TITLE, "vendor_code": PO_SUP["code"], "vendor": PO_SUP["name"],
                           "buyer": PO_BUYER, "approver_id": APPROVER_ID, "department": PO_DEPT,
                           "need_date": PO_NEED_DATE, "currency": "CNY", "budget_subject": "6401-办公费",
                           "budget_amount": ds.amount(lo=int(PO3_AMOUNT * 1.5), hi=int(PO3_AMOUNT * 2)),
                           "contract_no": PO_CONTRACT, "payment_terms": "NET30",
                           "po_amount": PO3_AMOUNT, "tax_amount": PO3_TAX_TOTAL,
                           "received_amount": 0, "invoiced_amount": 0})
    prid3 = (d.get("data") or {}).get("id")
    if prid3:
        s, d = child_replace(PO, prid3, PO_ITEM, PO3_ITEMS)
        check(f"采购：驳回演练单写入行项目（合计 {PO3_AMOUNT}）", s in (200, 201), f"{s} {d.get('message')}")
        s, d = act(PO, prid3, "submit")
        check("采购：驳回演练 submit（DRAFT→SUBMITTED）", s == 200 and rec_status(d) == "SUBMITTED",
              f"{s} status={rec_status(d)} {d.get('message', '')[:160]}")
        s, d = act_as(APPROVER_HDR, PO, prid3, "reject", params={"reason": "供应商资质不符，驳回补正"})
        check("采购：reject（SUBMITTED→REJECTED，由审批人会话发起，purchaseWorkflowDecisionHandler REJECT 分支）",
              s == 200 and rec_status(d) == "REJECTED", f"{s} status={rec_status(d)} {d.get('message', '')[:140]}")

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
report.finish("汇总")
