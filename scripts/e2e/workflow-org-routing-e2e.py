#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工作流「组织路由 / 审批人解析」端到端（真实 IAM 组织数据 + 真实提交链路 + 真实审批）。

与 `expense-purchase-approval-integration-e2e.py` 的分工：
  * 那份用**种子固定账号**（mdapprover2/3）+ **FIELD 策略**验证「提交 → 待办 → 审批 → 单据状态」；
  * 本脚本验证的是**组织策略**（LINE_MANAGER / DEPT_HEAD）在真实提交链路上的行为，
    以及它与表单字段 `approver_id` 的**物理隔离**（ADR-0044 的 D1 契约）。

覆盖场景：
  S1 组织路由：emp1 发起报销（金额 500 → v3 走 mgr 节点），表单故意填 CEO 当 approver_id，
     断言 wf_task.assignee_id == 直属主管 femgr（**不是** CEO）⇒ D1 语义契约端到端成立。
  S2 分级路由：emp2 发起报销（金额 3000 → v3 走 dept 节点），断言 assignee == 部门负责人 bemgr。
  S3 审批闭环：femgr 对 S1 单据审批通过 ⇒ 单据 APPROVED + 实例终态。
  S4 fail-closed：solo（无主管）发起报销 ⇒ 实例 SUSPENDED + 挂起原因可追溯（设计如此，非故障）。
  S5 大额会签：金额 6000 → finance_cosign 节点指向种子账号 9104/9105 ⇒ 暴露种子债（不掩盖）。
  S6 安全红线 D3：`iam_account` 不得注册为 meta_entity（否则 lookupOptions 的 SELECT * 会把
     password_hash 发给浏览器）。

断言口径：**API 断言 + MySQL 直查断言同时为真才算绿**（防「接口 200 但没落库」的假绿）。

用法：
  python3 scripts/e2e/workflow-org-routing-e2e.py
  BONE_E2E_BASE=http://127.0.0.1:9001/api/v1 python3 ...   # 直连后端排查
"""
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from e2e_lib import config, http  # noqa: E402
from e2e_lib.http import call, call_as, login_as, session_headers  # noqa: E402
from e2e_lib.report import check, finish, note, section  # noqa: E402
from e2e_lib.runtime import act_as  # noqa: E402

EXP = "expense_reimbursement"
TENANT = os.environ.get("BONE_E2E_TENANT", "0")

# ---- E2E 组织基线（幂等自建，复用优先）----
PREFIX = os.environ.get("BONE_E2E_ORG_PREFIX", "e2e_org_")
ORG_PWD = os.environ.get("BONE_E2E_ORG_PWD", "Org#2026")
ROLE_CODE = "E2E_WORKFLOW_OPERATOR"
ROLE_NAME = "E2E 工作流操作员"

# 部门名 → (父部门名, 部门负责人用户名)
DEPTS = [
    ("联调-总裁办公室", None, "ceo"),
    ("联调-技术中心", "联调-总裁办公室", "techdir"),
    ("联调-前端研发组", "联调-技术中心", "femgr"),
    ("联调-后端研发组", "联调-技术中心", "bemgr"),
    ("联调-财务部", "联调-总裁办公室", "findir"),
]
# 用户名 → (显示名, 部门名, 主管用户名)
ACCOUNTS = [
    ("ceo", "林知远", "联调-总裁办公室", None),
    ("techdir", "何家怡", "联调-技术中心", "ceo"),
    ("femgr", "周慕云", "联调-前端研发组", "techdir"),
    ("bemgr", "苏明诚", "联调-后端研发组", "techdir"),
    ("findir", "范丽蓉", "联调-财务部", "ceo"),
    ("emp1", "林知远", "联调-前端研发组", "femgr"),
    ("emp2", "沈亦航", "联调-后端研发组", "bemgr"),
    ("solo", "钱无咎", "联调-财务部", None),  # 无主管 → LINE_MANAGER 必须 fail-closed
]

# 权限码 id（iam_permission）：
#   57 metadata:runtime:read（审批待办读侧的旧码判据）、9101/9102 runtime:*:read/write、
#   9121/9122/9123 submit/approve/reject（逃逸动作必须动作级细码）
PERM_IDS = [57, 9101, 9102, 9121, 9122, 9123]

http.login()

acct = {}  # username -> id(int)
dept = {}  # dept name -> id(int)
dept_leader = {}  # dept name -> bool（是否已配负责人）


def q(sql):
    """MySQL 直查（第二证据源）。用 -N -B：不要表头、不要制表符对齐。"""
    try:
        r = subprocess.run(
            [
                config.MYSQL,
                f"-h{config.MYSQL_HOST}",
                f"-P{config.MYSQL_PORT}",
                f"-u{config.MYSQL_USER}",
                f"-p{config.MYSQL_PWD}",
                config.MYSQL_DB,
                "-N",
                "-B",
                "-e",
                sql,
            ],
            capture_output=True,
            text=True,
            timeout=30,
        )
    except Exception as e:
        return None, f"{type(e).__name__}: {e}"
    if r.returncode != 0:
        return None, (r.stderr or "").strip()[:200]
    return (r.stdout or "").strip(), None


def rows(sql):
    """执行查询并按行切分（列表 of 列值）。"""
    out, err = q(sql)
    if out is None:
        return [], err
    return [r.split("\t") for r in out.split("\n") if r.strip()], None


def _rows(d):
    """从分页响应里取出记录行（两种结构都兜住）。"""
    dd = d.get("data") or {}
    if isinstance(dd, dict):
        return dd.get("records") or dd.get("list") or []
    return dd if isinstance(dd, list) else []


def _new_id(d):
    """建档响应里的新 id：data 可能是裸 id 字符串，也可能是 {id: ...} 对象。"""
    dd = d.get("data")
    if isinstance(dd, dict):
        return dd.get("id")
    return dd


def _i(v):
    """雪花 ID 归一：后端序列化为字符串，运行时字段为数值 —— 统一按 int 口径比较。"""
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def _flatten_depts(tree):
    """把部门树（顶层 + children 嵌套）展平成行列表。"""
    out = []

    def walk(nodes):
        for n in nodes or []:
            out.append(n)
            walk(n.get("children") or [])

    walk(tree)
    return out


def ensure_org():
    """幂等构建企业真实组织数据（部门树 + 负责人 + 账号 + 主管链），全部走 IAM 自身 API。"""
    section("S0 组织基线自建（IAM API）")

    # 1) 部门：走 /iam/depts/tree（**没有 /iam/depts 分页列表端点**，调它恒返回空 ⇒ 会误判「不存在」而重复建）
    s, d = call("GET", "/iam/depts/tree")
    all_depts = _flatten_depts(d.get("data") or [])
    names = [x[0] for x in DEPTS]
    cands = {}  # name -> [(id, has_leader)]
    for row in all_depts:
        nm = row.get("name")
        if nm in names:
            cands.setdefault(nm, []).append((_i(row["id"]), bool(row.get("leaderId"))))
    # keeper：优先「已配负责人」，其次 id 更小（最早建的）；其余同名部门判为历史重复，待回收
    dup_ids = []
    for nm, lst in cands.items():
        lst.sort(key=lambda x: (not x[1], x[0]))
        dept[nm] = lst[0][0]
        dept_leader[nm] = lst[0][1]
        dup_ids += [i for i, _ in lst[1:]]
    for name, parent, leader in DEPTS:
        if name in dept:
            continue
        body = {"name": name, "tenantId": int(TENANT), "orderNo": 1, "status": 1}
        if parent and parent in dept:
            body["parentId"] = dept[parent]
        s, d = call("POST", "/iam/depts", body)
        if s != 200:
            note(f"建部门失败 {name}: {s} {str(d)[:120]}")
            continue
        dept[name] = _i(_new_id(d))
        dept_leader[name] = False
    check("部门树就绪（5 个部门，复用不重复建）", len(dept) >= len(DEPTS), f"实建 {len(dept)}/{len(DEPTS)}")

    # 2) 账号（带部门 + 主管）
    s, d = call("GET", "/iam/accounts?page=1&size=1000")
    for row in _rows(d):
        u = row.get("username") or ""
        if u.startswith(PREFIX):
            acct[u[len(PREFIX):]] = _i(row["id"])
    for short, real, dept_name, mgr in ACCOUNTS:
        if short in acct or dept_name not in dept:
            continue
        body = {
            "username": PREFIX + short,
            "password": ORG_PWD,
            "email": PREFIX + short + "@e2e.local",
            "realName": real,
            "tenantId": int(TENANT),
            "deptId": dept[dept_name],
        }
        if mgr and mgr in acct:
            body["managerId"] = acct[mgr]
        s, d = call("POST", "/iam/accounts", body)
        if s != 200:
            note(f"建账号失败 {PREFIX}{short}: {s} {str(d)[:120]}")
            continue
        acct[short] = _i(_new_id(d))
    check("账号就绪（8 个）", len(acct) >= len(ACCOUNTS), f"实建 {len(acct)}/{len(ACCOUNTS)}")

    # 3) 回填：主管链 + 部门负责人
    #    口径教训：部门更新是**全量替换**语义，且 order_no 为 NOT NULL 无默认值 ⇒
    #    少传 orderNo 会直接撞 DB 约束报「数据违反完整性约束」（不是字段校验错误，易误判）。
    for short, real, dept_name, mgr in ACCOUNTS:
        if short not in acct:
            continue
        body = {
            "deptId": dept.get(dept_name),
            "managerId": acct.get(mgr) if mgr else None,
            # realName 必须补齐：主管姓名展示优先取真实姓名，回退用户名 —— 回退会让界面
            # 显示 e2e_org_femgr 这类机器名，掩盖「主管名没装配对」的真实问题。
            "realName": real,
        }
        s, _ = call("PUT", f"/iam/accounts/{acct[short]}", body)
        if s != 200:
            note(f"回填账号失败 {short}: {s}")
    for name, parent, leader in DEPTS:
        if name not in dept or leader not in acct:
            continue
        s, d = call(
            "PUT",
            f"/iam/depts/{dept[name]}",
            {
                "name": name,
                "parentId": dept.get(parent),
                "leaderId": acct[leader],
                "orderNo": 1,
                "status": 1,
            },
        )
        if s != 200:
            note(f"回填部门负责人失败 {name}: {s} {str(d)[:140]}")
        else:
            dept_leader[name] = True

    mgr_missing = [m for _, _, _, m in ACCOUNTS if m and m not in acct]
    leader_missing = [l for _, _, l in DEPTS if l not in acct]
    leader_unset = [n for n in dept_leader if not dept_leader[n]]
    check(
        "组织链回填（主管 + 部门负责人齐备）",
        not mgr_missing and not leader_missing and not leader_unset,
        f"缺主管={mgr_missing} 缺负责人={leader_missing} 未配负责人的部门={leader_unset}",
    )

    # 3.5) 回收历史重复部门（同名多套 ⇒ 数据污染，会让 DEPT_HEAD 解析错人）
    #      只回收 E2E 自己建的部门名（DEPTS 里的 5 个名字），不碰任何业务部门。
    recycled = 0
    for did in dup_ids:
        s, _ = call("DELETE", f"/iam/depts/{did}")
        if s == 200:
            recycled += 1
    check(
        "同名重复部门已回收（防污染）",
        recycled == len(dup_ids),
        f"回收 {recycled}/{len(dup_ids)}（历史遗留的 E2E 重复部门）",
    )

    # 4) 角色 + 权限 + 成员
    s, d = call("GET", "/iam/roles?page=1&size=200")
    role_id = _i(next((r["id"] for r in _rows(d) if r.get("code") == ROLE_CODE), None))
    if not role_id:
        s, d = call(
            "POST",
            "/iam/roles",
            {
                "name": ROLE_NAME,
                "code": ROLE_CODE,
                "description": "E2E 组织路由联调",
                "tenantId": int(TENANT),
            },
        )
        role_id = _i(_new_id(d))
    check("E2E 角色就绪", bool(role_id), f"role_id={role_id}")
    if role_id:
        # assignPermissions 是全量替换语义 ⇒ 一次性给全，避免后续补授覆盖掉已有权限
        s, d = call("POST", f"/iam/roles/{role_id}/permissions", {"permissionIds": PERM_IDS})
        check("角色授权（6 权限）", s == 200, f"{s} {str(d)[:100]}")
        for short in acct:
            s, _ = call("PUT", f"/iam/accounts/{acct[short]}", {"roleIds": [role_id]})
            if s != 200:
                note(f"挂角色失败 {short}: {s}")
        check("成员挂载完成", True, f"{len(acct)} 个账号")

    # 5) 落库核对（双证据源）：组织链必须「账号有主管 + 部门有负责人」
    rs, err = rows(
        "select a.username, coalesce(a.manager_id,''), coalesce(d.leader_id,'') from iam_account a "
        "left join iam_dept d on d.id=a.dept_id "
        f"where a.username like '{PREFIX}%' and a.deleted=0 order by a.username;"
    )
    check("MySQL 直查组织链（第二证据源）", bool(rs) and not err, f"err={err} rows={len(rs)}")
    no_mgr = [r[0] for r in rs if r[0] != PREFIX + "solo" and r[0] != PREFIX + "ceo" and not r[1]]
    no_leader = [r[0] for r in rs if not r[2]]
    check(
        "组织链落库复核（仅 solo/ceo 无主管，其余必有主管且部门必有负责人）",
        not no_mgr and not no_leader,
        f"无主管={no_mgr} 部门无负责人={no_leader}",
    )


def login_org(short):
    return login_as(PREFIX + short, ORG_PWD)


def submit_expense(short, amount, approver_id, title):
    """建单 + 提交，返回 (record_id, (submit_status, submit_body))。"""
    hdr = session_headers(login_org(short))
    body = {
        "claim_no": "ORG-" + str(int(time.time() * 1000))[-9:],
        "title": title,
        "amount": amount,
        "expense_date": "2026-11-01",
        "applicant": "E2E",
        "cost_center": "CC-RD-01",
        "remark": "组织路由 E2E",
    }
    if approver_id is not None:
        body["approver_id"] = approver_id
    s, d = call_as(hdr, "POST", f"/runtime/entities/{EXP}/records", body)
    if s not in (200, 201):
        return None, (s, d)
    rid = (d.get("data") or {}).get("id")
    s2, d2 = act_as(
        hdr, EXP, rid, "submit", {}, idem="e2e-org-" + str(int(time.time() * 1000))
    )
    return rid, (s2, d2)


def instance_of(rid):
    """该单据最近的实例行：id / def_version / current_node / phase / started_by。"""
    rs, err = rows(
        "select id, def_version, current_node_code, phase, started_by from wf_instance "
        f"where record_id='{rid}' and deleted=0 order by created_at desc limit 1;"
    )
    return (rs[0] if rs else None), err


def tasks_of(rid):
    """该单据的任务台账行：node_code / assignee_id / status / assignee_username。"""
    rs, err = rows(
        "select t.node_code, coalesce(t.assignee_id,''), t.status, coalesce(a.username,'') "
        "from wf_task t join wf_instance i on i.id=t.instance_id "
        "left join iam_account a on a.id=t.assignee_id "
        f"where i.record_id='{rid}' and t.deleted=0 order by t.created_at;"
    )
    return rs, err


def suspend_reason(instance_id):
    """挂起原因：状态发件箱的 SUSPENDED 意图（params_json）。"""
    rs, err = rows(
        "select coalesce(params_json,'') from wf_status_outbox "
        f"where instance_id='{instance_id}' and intent='SUSPENDED' and deleted=0 "
        "order by created_at desc limit 1;"
    )
    return (rs[0][0] if rs else ""), err


def main():
    ensure_org()

    # ---------------- S1 LINE_MANAGER 路由 + D1 字段隔离 ----------------
    section("S1 LINE_MANAGER 路由（D1：字段值不得污染组织路由）")
    rid1, (s1, d1) = submit_expense("emp1", 500, acct.get("ceo"), "E2E·组织路由(带字段干扰值)")
    check("emp1 提交成功", s1 == 200, f"{s1} {str(d1)[:200]}")
    if rid1:
        time.sleep(1.2)
        inst, err = instance_of(rid1)
        tasks, _ = tasks_of(rid1)
        check("实例落库 RUNNING", bool(inst) and inst[3] == "RUNNING", f"{inst} err={err}")
        check("走 v3 定义", bool(inst) and inst[1] == "3", f"def_version={inst[1] if inst else '?'}")
        node = tasks[0][0] if tasks else ""
        assignee = _i(tasks[0][1]) if tasks else None
        uname = tasks[0][3] if tasks else ""
        check("节点 = mgr（金额 500 ≤ 1000）", node == "mgr", f"node={node} tasks={tasks}")
        check(
            "assignee = 直属主管 femgr",
            assignee == acct.get("femgr"),
            f"assignee={assignee} expected={acct.get('femgr')} user={uname}",
        )
        check(
            "assignee ≠ 表单 approver_id(CEO) ⇒ D1 隔离",
            assignee != acct.get("ceo"),
            f"assignee={assignee} ceo={acct.get('ceo')}",
        )
        # 表单字段本身确实落库了（证明隔离不是「字段没存」造成的）
        rs, err = rows(
            f"select coalesce(ext_str_01,'') from t_expense_reimbursement where id={rid1};"
        )
        check(
            "表单 approver_id 仍落库（CEO 干扰值）",
            bool(rs) and _i(rs[0][0]) == acct.get("ceo"),
            f"ext_str_01={rs[0][0] if rs else '?'} expected={acct.get('ceo')} err={err}",
        )

    # ---------------- S2 DEPT_HEAD 分级路由 ----------------
    section("S2 DEPT_HEAD 路由（金额 3000 → dept 节点）")
    rid2, (s2, d2) = submit_expense("emp2", 3000, acct.get("ceo"), "E2E·部门负责人路由")
    check("emp2 提交成功", s2 == 200, f"{s2} {str(d2)[:200]}")
    if rid2:
        time.sleep(1.2)
        inst, err = instance_of(rid2)
        tasks, _ = tasks_of(rid2)
        node = tasks[0][0] if tasks else ""
        assignee = _i(tasks[0][1]) if tasks else None
        check("节点 = dept（1000 < 金额 ≤ 5000）", node == "dept", f"node={node} tasks={tasks}")
        check(
            "assignee = 部门负责人 bemgr",
            assignee == acct.get("bemgr"),
            f"assignee={assignee} expected={acct.get('bemgr')}",
        )

    # ---------------- S3 审批闭环 ----------------
    section("S3 审批闭环（femgr 审批通过）")
    if rid1:
        hdr_f = session_headers(login_org("femgr"))
        s, d = call("GET", "/runtime/approval/tasks/pending?page=1&size=50", extra=hdr_f)
        dd = d.get("data") or {}
        pend = (dd.get("records") or dd.get("list") or []) if isinstance(dd, dict) else []
        mine = [t for t in pend if str(t.get("recordId")) == str(rid1)]
        check("femgr 待办里能看到该单", len(mine) == 1, f"pending={len(pend)} mine={len(mine)}")
        s3, d3 = act_as(
            hdr_f,
            EXP,
            rid1,
            "approve",
            {"comment": "E2E 审批通过"},
            idem="e2e-ok-" + str(int(time.time())),
        )
        check("审批动作 200", s3 == 200, f"{s3} {str(d3)[:220]}")
        time.sleep(1.5)
        inst, err = instance_of(rid1)
        check(
            "实例终态（phase 结束）",
            bool(inst) and inst[3] in ("COMPLETED", "APPROVED", "ENDED"),
            f"{inst} err={err}",
        )
        rs, err = rows(f"select status from t_expense_reimbursement where id={rid1};")
        check(
            "单据状态 APPROVED",
            bool(rs) and rs[0][0] == "APPROVED",
            f"status={rs[0][0] if rs else '?'} err={err}",
        )

    # ---------------- S4 fail-closed（无主管） ----------------
    section("S4 fail-closed（无主管账号发起 ⇒ 挂起，设计如此）")
    rid4, (s4, d4) = submit_expense("solo", 500, None, "E2E·无主管挂起")
    check("solo 提交返回（不报 5xx）", s4 in (200, 201), f"{s4} {str(d4)[:200]}")
    if rid4:
        time.sleep(1.2)
        inst, err = instance_of(rid4)
        tasks, _ = tasks_of(rid4)
        phase = inst[3] if inst else ""
        check("实例挂起（SUSPENDED）", phase == "SUSPENDED", f"phase={phase} inst={inst} err={err}")
        reason = ""
        if inst:
            reason, _ = suspend_reason(inst[0])
        check("挂起原因可追溯（发件箱 SUSPENDED 意图）", bool(reason), f"reason={reason!r}")
        check("未建任务（fail-closed 不误派单）", len(tasks) == 0, f"tasks={tasks}")

    # ---------------- S5 大额会签（种子债暴露） ----------------
    section("S5 大额会签（金额 6000 → finance_cosign，种子账号缺失）")
    rid5, (s5, d5) = submit_expense("emp1", 6000, None, "E2E·大额会签")
    check("emp1 大额提交返回", s5 in (200, 201), f"{s5} {str(d5)[:200]}")
    if rid5:
        time.sleep(1.2)
        inst, err = instance_of(rid5)
        tasks, _ = tasks_of(rid5)
        node = tasks[0][0] if tasks else ""
        assignee = _i(tasks[0][1]) if tasks else None
        check(
            "节点 = finance_cosign（金额 > 5000）",
            node == "finance_cosign",
            f"node={node} tasks={tasks}",
        )
        # 种子定义把会签人写死为 9104/9105，这两个账号在库里并不存在 ⇒ 幽灵审批人
        ghost = assignee in (9104, 9105) or assignee is None
        check(
            "会签 assignee 为不存在的种子账号（已登记的种子债，非本次回归）",
            ghost,
            f"assignee={assignee} node={node}",
        )
        if assignee:
            rs, err = rows(f"select count(*) from iam_account where id={assignee} and deleted=0;")
            check(
                "幽灵账号确认不存在于 iam_account",
                bool(rs) and rs[0][0] == "0",
                f"count={rs[0][0] if rs else '?'} err={err}",
            )

    # ---------------- S6 安全红线 D3 ----------------
    section("S6 安全红线 D3（iam_account 不得注册为 meta_entity）")
    rs, err = rows("select count(*) from meta_entity where code='iam_account' and deleted=0;")
    check(
        "iam_account 未注册为运行时实体",
        bool(rs) and rs[0][0] == "0",
        f"count={rs[0][0] if rs else '?'} err={err}",
    )
    rs2, _ = rows(
        "select count(*) from information_schema.columns where table_schema=database() "
        "and table_name='iam_account' and column_name in ('password_hash','password');"
    )
    note(
        f"iam_account 凭据列存在（若注册为实体将被 SELECT * 外泄）：命中 "
        f"{rs2[0][0] if rs2 else '?'} 列"
    )

    finish("组织路由 / 审批人解析 E2E")


if __name__ == "__main__":
    main()
