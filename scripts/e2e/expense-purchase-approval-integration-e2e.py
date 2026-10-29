#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""费用报销 / 采购订单「产品级」审批集成端到端（经网关、真实登录会话、真实双账号）。

与 `two-scenario-full-chain-e2e.py` 的分工：
  * 那份从**建模**开始自建实体，验证的是「元数据平台能力」；
  * 本脚本打的是**种子产品实体**（`expense_reimbursement` / `purchase_order`）+ 种子流程绑定
    （`wf_binding` → `wf_process_def`），验证的是「审批流是否真的接进了业务单据」——
    即用户视角的「提交 → 待办 → 审批 / 驳回 / 撤回 → 单据状态」这条链路。

为什么必须两个账号（血泪教训，2026-10-29 实测）：
  引擎有两条独立的职责分离（SoD）红线：
    ① 自审批守卫（`WorkflowEngineImpl#isSelfApproval`）——审批人 == 发起人 ⇒ 实例 **SUSPENDED**，
       一条 `wf_task` 都不建。表现为「提交成功但审批中心永远看不到这张单」，
       极易被误诊成「审批人解析不出来」；
    ② 任务级 SoD（`*WorkflowDecisionHandler#requirePendingTaskOf`）——操作人 != 任务 assignee ⇒ 403。
  单账号下两者互斥 ⇒ 无论怎么调都走不通。故一律 use admin 提交、独立账号审批。

覆盖场景（真实企业审批的骨架）：
  S1 报销 提交 → 派单 → 我的待办 → 通过 → 单据 APPROVED + 轨迹留痕
  S2 报销 越权审批被拒（非当前审批人 → 403）
  S3 报销 驳回 → 单据 REJECTED、实例终态、待办清空
  S4 报销 撤回 → 回到 DRAFT、实例 WITHDRAWN → 可重新提交（不被死单锁死）
  S5 报销 自审批触发挂起 → 管理员强制指派 → 恢复 → 通过（挂起死单的人工出口）
  S6 采购 建单（含行项目）→ 提交 → 派单 → 通过 → 单据 APPROVED
  S7 报销 驳回**退回指定节点**（原地返工）→ 单据仍 SUBMITTED → 可继续审批通过（不死单）
  S8 采购 退回指定节点 → 单据不落 REJECTED → 可继续审批通过

用法：
  python3 scripts/e2e/expense-purchase-approval-integration-e2e.py
  BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 python3 ...   # 直连后端排查
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from e2e_lib import http  # noqa: E402
from e2e_lib.http import call, call_as, d_, ecode, jwt_user_id, login_as, session_headers  # noqa: E402
from e2e_lib.report import check, finish, note, section  # noqa: E402
from e2e_lib.runtime import act_as, child_replace, rec_create  # noqa: E402

EXP = "expense_reimbursement"
PO = "purchase_order"
PO_ITEM = "purchase_order_item"

# 平台种子里的第二账号（non-admin、独立审批人）。优先复用，缺失则按 IAM 契约补建。
APPROVER_USER = os.environ.get("BONE_E2E_APPROVER_USER", "mdapprover2")
APPROVER_PWD = os.environ.get("BONE_E2E_APPROVER_PWD", "Approver#2026")
APPROVER_DEPT_ID = int(os.environ.get("BONE_E2E_APPROVER_DEPT_ID", "758345112240521216"))

SUFFIX = str(int(time.time() * 1000))[-9:]

http.login()


# ------------------------------------------------------------------ 审批账号
def _find_account_id(username):
    for qs in (f"?username={username}&page=1&size=10", "?page=1&size=300"):
        s, d = call("GET", "/iam/accounts" + qs)
        if s != 200:
            continue
        for row in d_({}, "records") or []:
            if row.get("username") == username:
                return row.get("id")
        # d_ 的取法在分页结构上不稳定，兜底直接走原始体
        for row in ((d.get("data") or {}).get("records") or []):
            if row.get("username") == username:
                return row.get("id")
    return None


def ensure_approver():
    """返回 (identity_headers, user_id)；无法准备时返回 (None, None)，调用方跳过而非假绿。"""
    tok = login_as(APPROVER_USER, APPROVER_PWD)
    if not tok:
        aid = _find_account_id(APPROVER_USER)
        if aid is None:
            s, d = call("POST", "/iam/accounts", {
                "username": APPROVER_USER, "password": APPROVER_PWD,
                "email": APPROVER_USER + "@bone.local", "realName": "联调审批人",
                "tenantId": 0, "deptId": APPROVER_DEPT_ID,
                # 角色 1=平台角色、3=持有 metadata:runtime:write：缺写入域权限连动作控制器都进不去
                "roleIds": [1, 3],
            })
            note(f"补建审批账号 {APPROVER_USER} → {s} {ecode(d) or ''}")
            aid = d_(d)
        if aid is not None:
            call("POST", f"/iam/accounts/{aid}/reset-password", {"newPassword": APPROVER_PWD})
            tok = login_as(APPROVER_USER, APPROVER_PWD)
    if not tok:
        return None, None
    return session_headers(tok), jwt_user_id(tok)


APPROVER_HDR, APPROVER_ID = ensure_approver()


# ------------------------------------------------------------------ 助手
def rec(entity, rid):
    """记录详情（真源状态读回，不采信动作响应）。"""
    s, d = call("GET", f"/runtime/entities/{entity}/records/{rid}")
    return (d.get("data") or {}) if s == 200 else {}


def wait_status(entity, rid, expect, tries=30, gap=0.4):
    got = None
    for _ in range(tries):
        got = rec(entity, rid).get("status")
        if got == expect:
            return got
        time.sleep(gap)
    return got


def describe(entity, rid):
    s, d = call("GET", f"/runtime/approval/records/{entity}/{rid}")
    return (d.get("data") or {}), s


def phase(desc):
    return (desc.get("instance") or {}).get("phase")


def pending(desc):
    return desc.get("pendingTasks") or []


def make_expense(title, amount, approver_id):
    return rec_create(EXP, {
        "claim_no": "BX-" + SUFFIX + "-" + str(int(time.time() * 1000))[-4:],
        "title": title,
        "amount": amount,
        "expense_date": "2026-10-29",
        "applicant": "系统管理员",
        "cost_center": "CC-RD-01",
        "remark": "审批集成联调（自动化）",
        "approver_id": approver_id,
    })


def make_purchase(amount=1200, budget=2000, tag=""):
    s, d = rec_create(PO, {
        "po_no": "PO-" + SUFFIX + tag,
        "title": "联调-采购订单审批",
        "vendor": "联调供应商",
        "vendor_code": "V-LIAN-01",
        "buyer": "系统管理员",
        "department": "采购部",
        "currency": "CNY",
        "budget_subject": "6401-办公费",
        "budget_amount": budget,
        "po_amount": amount,
        "tax_amount": 0,
        "payment_terms": "NET30",
        "need_date": "2026-11-15",
        "remark": "审批集成联调（自动化）",
        "approver_id": APPROVER_ID,
    })
    if s not in (200, 201):
        return s, d, None
    po = d_(d, "id")
    si, di = child_replace(PO, po, PO_ITEM, [{
        "material_code": "MAT-LIAN-01", "material_name": "联调物料", "spec": "标准",
        "unit": "个", "qty": 1, "unit_price": amount, "amount": amount,
        "tax_rate": 0, "tax_amount": 0, "received_qty": 0, "sort_order": 1,
    }])
    return s, d, (po if si in (200, 201) else None)


section("S0 会话准备")
check("主会话（发起人 admin）已登录", bool(http.default().token), "login failed")
if APPROVER_ID is None:
    check("审批账号可用（非 admin，职责分离前提）", False, "无法准备第二账号，后续 SoD 场景不可判")
    finish("审批集成（凭据未就绪）")
check("审批账号可用且非发起人", APPROVER_ID is not None and str(APPROVER_ID) != "1",
      f"approver_id={APPROVER_ID}")

# ---------------------------------------------------------------- S1 审批通过
section("S1 报销：提交 → 派单 → 我的待办 → 审批通过")
s, d = make_expense("联调-报销-通过", 300, APPROVER_ID)
check("报销建单成功", s in (200, 201), f"{s} {ecode(d)} {d.get('message')}")
exp1 = d_(d, "id")
if exp1:
    s, d = act_as(http.default().headers(), EXP, exp1, "submit")
    check("报销提交成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    desc, ds = describe(EXP, exp1)
    check("实例进入 RUNNING（未挂起）", phase(desc) == "RUNNING", f"phase={phase(desc)} http={ds}")
    check("待办已派给独立审批人",
          any(str(t.get("assigneeId")) == str(APPROVER_ID) for t in pending(desc)),
          f"pending={pending(desc)}")
    s, d = call_as(APPROVER_HDR, "GET", "/runtime/approval/tasks/pending?page=1&size=50")
    rows = (d.get("data") or {}).get("records") or []
    check("审批中心「我的待办」能看到该单",
          any(str(r.get("recordId")) == str(exp1) for r in rows),
          f"rows={[r.get('recordId') for r in rows][:6]}")
    s, d = act_as(APPROVER_HDR, EXP, exp1, "approve", {"comment": "同意，费用合理"})
    check("审批通过成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("单据状态投影为 APPROVED", wait_status(EXP, exp1, "APPROVED") == "APPROVED",
          f"status={rec(EXP, exp1).get('status')}")
    desc, _ = describe(EXP, exp1)
    check("实例终态 APPROVED", phase(desc) == "APPROVED", f"phase={phase(desc)}")
    check("终态无遗留待办", not pending(desc), f"pending={pending(desc)}")
    check("审批轨迹留痕含意见",
          any(t.get("decision") == "APPROVE" and (t.get("comment") or "")
              for t in (desc.get("trail") or [])),
          f"trail={desc.get('trail')}")

# ---------------------------------------------------------------- S2 越权审批
section("S2 报销：非当前审批人审批被拒（任务级 SoD）")
s, d = make_expense("联调-报销-越权", 400, APPROVER_ID)
exp2 = d_(d, "id")
if exp2:
    act_as(http.default().headers(), EXP, exp2, "submit")
    s, d = act_as(http.default().headers(), EXP, exp2, "approve", {"comment": "我自己批"})
    check("发起人自行审批被拒（403/422）", s in (403, 422), f"{s} {ecode(d)} {d.get('message')}")

# ---------------------------------------------------------------- S3 驳回
section("S3 报销：驳回 → 单据 REJECTED、实例终态")
s, d = make_expense("联调-报销-驳回", 500, APPROVER_ID)
exp3 = d_(d, "id")
if exp3:
    act_as(http.default().headers(), EXP, exp3, "submit")
    s, d = act_as(APPROVER_HDR, EXP, exp3, "reject", {"reason": "金额与发票不符，请重报"})
    check("驳回成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("单据状态投影为 REJECTED", wait_status(EXP, exp3, "REJECTED") == "REJECTED",
          f"status={rec(EXP, exp3).get('status')}")
    desc, _ = describe(EXP, exp3)
    check("实例终态 REJECTED", phase(desc) == "REJECTED", f"phase={phase(desc)}")
    check("驳回后无遗留待办", not pending(desc), f"pending={pending(desc)}")

# ---------------------------------------------------------------- S4 撤回
section("S4 报销：撤回 → DRAFT → 可重新提交（死单不锁单）")
s, d = make_expense("联调-报销-撤回", 200, APPROVER_ID)
exp4 = d_(d, "id")
if exp4:
    act_as(http.default().headers(), EXP, exp4, "submit")
    s, d = act_as(http.default().headers(), EXP, exp4, "withdraw")
    check("撤回成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("单据回到 DRAFT", wait_status(EXP, exp4, "DRAFT") == "DRAFT",
          f"status={rec(EXP, exp4).get('status')}")
    desc, _ = describe(EXP, exp4)
    check("实例 WITHDRAWN", phase(desc) == "WITHDRAWN", f"phase={phase(desc)}")
    s, d = act_as(http.default().headers(), EXP, exp4, "submit")
    check("撤回后可重新提交", s == 200, f"{s} {ecode(d)} {d.get('message')}")

# ---------------------------------------------------------------- S5 挂起干预
section("S5 报销：自审批触发挂起 → 管理员指派 → 恢复 → 通过")
s, d = make_expense("联调-报销-挂起干预", 600, 1)  # 审批人 = 发起人本人
exp5 = d_(d, "id")
if exp5:
    act_as(http.default().headers(), EXP, exp5, "submit")
    desc, _ = describe(EXP, exp5)
    check("自审批按设计挂起（fail-closed，不派单）", phase(desc) == "SUSPENDED",
          f"phase={phase(desc)}")
    check("挂起时零待办（死单形态）", not pending(desc), f"pending={pending(desc)}")
    check("挂起原因可诊断（不是只显示「已挂起」）",
          bool(desc.get("suspendReason")), f"suspendReason={desc.get('suspendReason')!r}")
    inst = (desc.get("instance") or {}).get("instanceId")
    s, d = call("POST", f"/runtime/approval/instances/{inst}/assign",
                {"assigneeId": str(APPROVER_ID), "reason": "联调：指派独立审批人"})
    check("管理员强制指派成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    desc, _ = describe(EXP, exp5)
    check("指派后实例恢复 RUNNING", phase(desc) in ("RUNNING", "APPROVED"), f"phase={phase(desc)}")
    check("指派后待办落到指定人",
          any(str(t.get("assigneeId")) == str(APPROVER_ID) for t in pending(desc)),
          f"pending={pending(desc)}")
    s, d = act_as(APPROVER_HDR, EXP, exp5, "approve", {"comment": "指派后同意"})
    check("指派后审批通过", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("单据 APPROVED", wait_status(EXP, exp5, "APPROVED") == "APPROVED",
          f"status={rec(EXP, exp5).get('status')}")

# ---------------------------------------------------------------- S6 采购链
section("S6 采购订单：建单（含行项目）→ 提交 → 派单 → 通过")
s, d, po1 = make_purchase()
check("采购建单 + 行项目成功", po1 is not None, f"{s} {ecode(d)} {d.get('message')}")
if po1:
    s, d = act_as(http.default().headers(), PO, po1, "submit")
    check("采购提交成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    desc, _ = describe(PO, po1)
    check("采购实例 RUNNING", phase(desc) == "RUNNING", f"phase={phase(desc)}")
    check("采购待办派给独立审批人",
          any(str(t.get("assigneeId")) == str(APPROVER_ID) for t in pending(desc)),
          f"pending={pending(desc)}")
    s, d = act_as(APPROVER_HDR, PO, po1, "approve", {"comment": "同意采购"})
    check("采购审批通过", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("采购单 APPROVED", wait_status(PO, po1, "APPROVED") == "APPROVED",
          f"status={rec(PO, po1).get('status')}")
    desc, _ = describe(PO, po1)
    check("采购实例终态 APPROVED", phase(desc) == "APPROVED", f"phase={phase(desc)}")

# ---------------------------------------------------------------- S7 退回指定节点（报销）
# 为什么必须单独一段：这段钉住的是「原地返工 vs 整单驳回」的语义分叉。接入前实测到的缺陷形态是——
# 退回节点后实例已回到上游节点并重新派单（RUNNING/pending≥1），但平台照常把单据 status 推到
# REJECTED 终态 ⇒ 审批人再点「通过」恒 409 ILLEGAL_TRANSITION、待办永远清不掉（死单）。
# 只断言「动作 200」会漏掉它，必须同时断言**单据真源状态**与**后续能否走通**。
section("S7 报销：驳回退回指定节点（原地返工）→ 单据仍 SUBMITTED → 仍可审批通过")
s, d = make_expense("联调-报销-退回节点", 700, APPROVER_ID)
exp7 = d_(d, "id")
if exp7:
    act_as(http.default().headers(), EXP, exp7, "submit")
    s, d = act_as(APPROVER_HDR, EXP, exp7, "reject",
                  {"reason": "发票信息不全，退回补正后重提", "backToNodeCode": "start"})
    check("退回指定节点成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("动作响应如实回「状态未变」（不谎报已驳回）", d_(d, "toStatus") == "SUBMITTED",
          f"toStatus={d_(d, 'toStatus')!r}")
    check("单据仍为 SUBMITTED（原地返工，不落 REJECTED 终态）",
          rec(EXP, exp7).get("status") == "SUBMITTED", f"status={rec(EXP, exp7).get('status')}")
    desc, _ = describe(EXP, exp7)
    check("实例仍在 RUNNING", phase(desc) == "RUNNING", f"phase={phase(desc)}")
    check("返工待办仍在（未被清空）",
          any(str(t.get("assigneeId")) == str(APPROVER_ID) for t in pending(desc)),
          f"pending={pending(desc)}")
    s, d = act_as(APPROVER_HDR, EXP, exp7, "approve", {"comment": "补正后同意"})
    check("退回后仍可审批通过（不死单）", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("单据最终 APPROVED", wait_status(EXP, exp7, "APPROVED") == "APPROVED",
          f"status={rec(EXP, exp7).get('status')}")
    desc, _ = describe(EXP, exp7)
    check("实例终态 APPROVED 且无遗留待办", phase(desc) == "APPROVED" and not pending(desc),
          f"phase={phase(desc)} pending={pending(desc)}")

# ---------------------------------------------------------------- S8 退回指定节点（采购）
section("S8 采购订单：退回指定节点 → 不落 REJECTED → 仍可审批通过")
s, d, po2 = make_purchase(amount=800, budget=1500, tag="-R")
check("采购建单（退回场景）成功", po2 is not None, f"{s} {ecode(d)} {d.get('message')}")
if po2:
    act_as(http.default().headers(), PO, po2, "submit")
    s, d = act_as(APPROVER_HDR, PO, po2, "reject",
                  {"reason": "预算科目需复核，退回修改", "backToNodeCode": "start"})
    check("采购退回指定节点成功", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("采购单据仍为 SUBMITTED", rec(PO, po2).get("status") == "SUBMITTED",
          f"status={rec(PO, po2).get('status')}")
    desc, _ = describe(PO, po2)
    check("采购实例仍在 RUNNING 且有返工待办",
          phase(desc) == "RUNNING" and bool(pending(desc)),
          f"phase={phase(desc)} pending={pending(desc)}")
    s, d = act_as(APPROVER_HDR, PO, po2, "approve", {"comment": "复核后同意"})
    check("采购退回后仍可审批通过（不死单）", s == 200, f"{s} {ecode(d)} {d.get('message')}")
    check("采购单最终 APPROVED", wait_status(PO, po2, "APPROVED") == "APPROVED",
          f"status={rec(PO, po2).get('status')}")

finish("报销/采购审批集成")
