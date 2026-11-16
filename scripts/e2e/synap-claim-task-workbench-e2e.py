#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
作业台任务分配 E2E（批 1 · 我的待办 / 案件任务 / 自领互斥）
=====================================================================
针对 `t_claim_task` 的三个端点做真实联调，判定一律「接口 2xx + 直查 MySQL 第二证据源」，
避免「接口 200 但没落库」的假绿。

覆盖端点：
  GET  /api/v1/claims/tasks/my              我的待办（服务端分页，total 与列表同源）
  GET  /api/v1/claims/{claimId}/tasks       某案任务列表（案件 360 视图，含终态）
  POST /api/v1/claims/tasks/{taskId}/claim  自领（乐观锁 + 生成列唯一键 ⇒ 并发恰 1 成功）

不变量（每条都带反证，不只看状态码）：
  T1 分页同源        total == 逐页累加 == taskId 去重数，且相邻页无重叠
  T2 排序全序        priority 非降；同 priority 时 createdAt 非降；重复请求结果完全一致
  T3 归属即写入期事实 每行 assignee 与库内一致；PENDING_CLAIM 行 assignee 必须为空
  T4 案件视图一致    某案任务列表的 taskId 集合与库内该案（deleted=0）完全相等
  T5 同案同类型至多一条生效  库内 (claim_id, task_type) 的生效行数 <= 1
  T6 终态不占唯一性  status ∈ 终态 的行 active_key 必须为 NULL（生成列语义的强断言）
  T7 自领成功        POST 后 status=PROCESSING + assignee=本人，且库内 claimed_at 已落
  T8 重复领取       409 SC_CLAIM_TASK_CONFLICT，且库内 version/claimed_at 未被二次改写
  T9 并发恰 1 成功  双线程同任务，successes <= 1 且 successes + conflicts == 2
  T10 越权隔离      另一身份查「我的待办」，返回行在库内必须都归属该身份
  T11 雪花 ID 字符串   taskId 为字符串（JS number 只有 53 位精度）

前置：网关 8888 / claim 8087 / iam 8081 在线；admin/123456 可登录；MySQL 可直连。
用法：python3 scripts/e2e/synap-claim-task-workbench-e2e.py [--keep]
"""
import importlib.util
import os
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))


def _load_sibling(name):
    """复用同目录既有 E2E 的登录 / 调用 / 直连数据库助手，不复制第三份。"""
    spec = importlib.util.spec_from_file_location(name, os.path.join(HERE, name + ".py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _load_sibling("e2e_synap_claim")

# 端点模板集中在此：既避免散落的裸字符串，也让「改了路径忘改用例」暴露成一处 diff。
API_MY_TASKS = "/claims/tasks/my"
API_CLAIM_TASKS = "/claims/{claimId}/tasks"
API_CLAIM_ONE = "/claims/tasks/{taskId}/claim"

ACTIVE = ("PENDING_CLAIM", "ASSIGNED", "PROCESSING")
TERMINAL = ("DONE", "CANCELLED")

# 跨租户探针身份：dev 库里 tenant 0 只有一个可用口令的账号（admin），
# 而同租户第二身份（mdapprover2/3）本库并不存在 ⇒ 改用另一租户的既有账号，
# 既不需要改别人密码，探针强度也更高（租户头与 token claim 必须一致）。
OTHER = ("tenant_admin", "123456")
OTHER_TENANT = 1001

RESULTS = []


def record(name, ok, detail=""):
    RESULTS.append((name, ok, detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" :: {detail}" if detail else ""))


def api(tpl, **kw):
    return tpl.format(**kw)


# ---------- 数据准备 ----------
def pick_pending_task(exclude=None):
    sql = ("SELECT id, claim_id FROM t_claim_task "
           "WHERE tenant_id = 0 AND status = 'PENDING_CLAIM' AND deleted = 0 "
           "AND assignee_id IS NULL")
    args = ()
    if exclude:
        sql += " AND id <> %s"
        args = (exclude,)
    rows = S.db_query(sql + " ORDER BY id LIMIT 1", args)
    return (int(rows[0]["id"]), int(rows[0]["claim_id"])) if rows else (None, None)


def login_in(account, tenant):
    """按显式租户登录。

    为什么不用同目录的 `S.login`：它固定 `X-Tenant-Id: 0`，跨租户探针需要另一个租户头。
    这里也不去改别人密码（同租户第二身份在本 dev 库里没有可用账号），跨租户隔离
    本身就是更强的探针：租户头与 token claim 不一致时必须看不到对方数据。
    """
    _, d = S.call("POST", "/iam/login", json_body={"username": account[0],
                                                   "password": account[1]},
                  extra={"X-Tenant-Id": str(tenant)}, expect=(200,))
    tok = (d.get("data") or {}).get("token")
    if not tok:
        raise RuntimeError(f"租户 {tenant} 登录失败: {d}")
    return tok


def ensure_claimable_task(tok):
    """优先用库里的入池任务（快）；库内没有时走产品路径新开一个案。

    走「新开案」而不是直接 INSERT：任务必须由 `ClaimTaskOpener` 在案件进入
    PENDING_ADJUST 的 AFTER_COMMIT 里建出来，否则本用例就绕过了被测逻辑。
    """
    tid, cid = pick_pending_task()
    if tid:
        return tid, cid, "库内入池任务"
    scn = {
        "policyNo": "E2E-POLICY-0001", "planId": 9001,
        "insuredIdHash": "e2e-insured-hash-0001",
        "hospitalCode": "HOSP_BJ_001", "diagnosisCode": "J18.9",
        "invoiceCode": "011001900", "invoiceNo": "T" + str(int(S.time.time()))[-6:],
        "invoiceDate": "2026-10-20", "batchNo": "BATCH-TASK",
        "items": [{"itemName": "挂号费", "itemCode": "A001", "category": "SERVICE",
                   "quantity": 1, "unitPrice": 50, "amount": 50, "department": "门诊"}],
        "invoice_total": 50,
    }
    S.setup_seed()
    cid, _ = S.run_intake_and_decision(tok, scn)
    rows = S.db_query(
        "SELECT id FROM t_claim_task WHERE claim_id = %s AND task_type = 'INITIAL_REVIEW' "
        "AND deleted = 0 AND status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING') LIMIT 1",
        (cid,),
    )
    return (int(rows[0]["id"]) if rows else None), cid, "产品路径新建案"


# ---------- T1/T2/T11：我的待办分页与排序 ----------
def check_my_tasks(tok, operator_id):
    code, body = S.call("GET", API_MY_TASKS, token=tok, expect=(200,))
    data = body.get("data") or {}
    records, total = data.get("records") or [], int(data.get("total") or 0)
    record("T1a 我的待办可读", code == 200 and isinstance(records, list) and total >= 0,
           f"http={code} total={total} size={len(records)}")
    record("T11 雪花 ID 字符串",
           bool(records) and all(isinstance(r.get("taskId"), str) and len(r["taskId"]) >= 15
                                 for r in records),
           f"首行 taskId={records[0].get('taskId') if records else '—'}")

    db_total = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_task WHERE tenant_id = 0 AND assignee_id = %s "
        "AND deleted = 0", (operator_id,))[0]["c"]
    record("T1b total 与库内同源", total == int(db_total), f"api={total} db={db_total}")

    # 逐页走完：size=7（质数、通常整除不尽）既让小数据量也能多页，又必然留下
    # 一个不满页的尾页——恰好能暴露「按 offset 切片但 total 与列表不同源」的缺陷。
    pages, seen, order_ok, prev = 0, [], True, None
    page = 1
    while True:
        _, b = S.call("GET", f"{API_MY_TASKS}?page={page}&size=7", token=tok, expect=(200,))
        d = b.get("data") or {}
        rs = d.get("records") or []
        if not rs:
            break
        pages += 1
        for r in rs:
            seen.append(r["taskId"])
            key = (int(r.get("priority") or 0), r.get("createdAt") or "")
            if prev is not None and key < prev:
                order_ok = False
            prev = key
        if pages > total + 5:  # 防御：分页不收敛会死循环
            break
        page += 1
    record("T1c 逐页累加 == total == 去重数",
           len(seen) == total == len(set(seen)),
           f"页数={pages} 行数={len(seen)} total={total} 去重={len(set(seen))}")
    record("T1d 跨页无重叠", len(seen) == len(set(seen)), f"行数={len(seen)}")
    record("T2 排序全序（priority, createdAt）", order_ok,
           f"页数={pages}（同 priority 时 createdAt 必须非降）")

    _, again = S.call("GET", f"{API_MY_TASKS}?page=1&size=20", token=tok, expect=(200,))
    first = [r["taskId"] for r in (data.get("records") or [])][:20]
    second = [r["taskId"] for r in ((again.get("data") or {}).get("records") or [])][:20]
    record("T2b 重复请求结果一致（无并列漂移）", first == second,
           f"{len(first)} 行逐位相同" if first == second else f"首行 {first[:1]} vs {second[:1]}")

    # T3：每行归属与库内一致
    ids = [r["taskId"] for r in records]
    if ids:
        rows = S.db_query(
            "SELECT id, status, assignee_id FROM t_claim_task WHERE id IN %s",
            (tuple(int(i) for i in ids),),
        )
        db = {str(r["id"]): r for r in rows}
        bad = [r["taskId"] for r in records
               if r["taskId"] not in db
               or (r["status"] == "PENDING_CLAIM" and db[r["taskId"]]["assignee_id"])]
        record("T3 归属为写入期事实（入池行不得有归属人）", not bad,
               f"核对 {len(records)} 行" + (f" 异常 {bad[:3]}" if bad else ""))
    else:
        record("T3 归属为写入期事实（入池行不得有归属人）", False, "本次返回 0 行，无法核对")
    return total, set(ids)


# ---------- T4/T5/T6：案件视图与唯一性 ----------
def check_claim_view(tok, claim_id):
    code, body = S.call("GET", api(API_CLAIM_TASKS, claimId=claim_id), token=tok,
                        expect=(200,))
    rows = body.get("data") or []
    db_rows = S.db_query(
        "SELECT id, status FROM t_claim_task WHERE claim_id = %s AND deleted = 0", (claim_id,))
    api_ids = {r["taskId"] for r in rows}
    db_ids = {str(r["id"]) for r in db_rows}
    record("T4 案件任务列表与库内一致", code == 200 and api_ids == db_ids,
           f"api={len(api_ids)} db={len(db_ids)}")

    dup = S.db_query(
        "SELECT claim_id, task_type, COUNT(*) c FROM t_claim_task "
        "WHERE deleted = 0 AND status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING') "
        "GROUP BY claim_id, task_type HAVING c > 1 LIMIT 5")
    record("T5 同案同类型至多一条生效任务", not dup,
           "全库无重复生效行" if not dup else f"重复 {dup}")

    bad_active = S.db_query(
        "SELECT COUNT(*) c FROM t_claim_task WHERE status IN ('DONE','CANCELLED') "
        "AND active_key IS NOT NULL")[0]["c"]
    record("T6 终态行 active_key 恒为 NULL（不占唯一性）", int(bad_active) == 0,
           f"违反行数={bad_active}")


# ---------- T7/T8/T9：自领 ----------
def check_claim(tok, task_id, operator_id):
    before = S.db_query(
        "SELECT status, assignee_id, claimed_at, version FROM t_claim_task WHERE id = %s",
        (task_id,))[0]
    if before["status"] != "PENDING_CLAIM":
        record("T7 自领成功（status→PROCESSING + claimed_at 落库）", False,
               f"前置不成立：任务状态 {before['status']}（应为 PENDING_CLAIM）")
        return
    code, body = S.call("POST", api(API_CLAIM_ONE, taskId=task_id), token=tok, expect=(200, 409))
    after = S.db_query(
        "SELECT status, assignee_id, claimed_at, version FROM t_claim_task WHERE id = %s",
        (task_id,))[0]
    row = (body.get("data") or {}) if code == 200 else {}
    record("T7 自领成功（status→PROCESSING + claimed_at 落库）",
           code == 200 and row.get("status") == "PROCESSING"
           and after["status"] == "PROCESSING" and after["assignee_id"] == operator_id
           and after["claimed_at"] is not None,
           f"http={code} status={after['status']} assignee={after['assignee_id']} "
           f"claimed_at={after['claimed_at']}")

    code2, body2 = S.call("POST", api(API_CLAIM_ONE, taskId=task_id), token=tok,
                          expect=(200, 409))
    after2 = S.db_query(
        "SELECT claimed_at, version FROM t_claim_task WHERE id = %s", (task_id,))[0]
    # 协议码在 `data.errorCode`（problem+json 结构），顶层只有 `code`
    err = (body2.get("data") or {}).get("errorCode") or body2.get("errorCode") or ""
    record("T8 重复领取 409 且不二次改写",
           code2 == 409 and err == "SC_CLAIM_TASK_CONFLICT"
           and after2["claimed_at"] == after["claimed_at"],
           f"http={code2} errorCode={err} claimed_at 未变={after2['claimed_at'] == after['claimed_at']}")


def check_concurrent(tok, task_id):
    """双线程同一任务：起始为入池 ⇒ 恰 1 成功 1 冲突；已被领走 ⇒ 恰 0 成功 2 冲突。

    两种情形都把期望写死（而不是只断言 `ok <= 1`）：后者在「两条都因别的错因拿 409」
    时也会绿，那正是本题要防的假绿。
    """
    before = S.db_query("SELECT status FROM t_claim_task WHERE id = %s", (task_id,))[0]["status"]
    results = []
    barrier = threading.Barrier(2)

    def worker():
        barrier.wait()
        try:
            code, _ = S.call("POST", api(API_CLAIM_ONE, taskId=task_id), token=tok,
                             expect=(200, 409))
            results.append(code)
        except Exception as e:  # 网络/序列化异常也必须被看见，不能当成"没成功"
            results.append(f"ERR:{e}")

    ts = [threading.Thread(target=worker) for _ in range(2)]
    [t.start() for t in ts]
    [t.join() for t in ts]
    ok = sum(1 for r in results if r == 200)
    conflict = sum(1 for r in results if r == 409)
    want_ok = 1 if before == "PENDING_CLAIM" else 0
    record("T9 并发领取无双重成功（期望由起始状态决定）",
           ok == want_ok and ok + conflict == 2,
           f"起始={before} 期望200={want_ok} 实际200={ok} 409={conflict} "
           f"原始={sorted(str(r) for r in results)}")


# ---------- T10：租户隔离 ----------
def check_isolation(tok, other_tok, other_tenant, my_ids):
    """两段探针，都不依赖改别人密码或授权。

    T10a 租户头不被采信：带 admin 的 token 却伪造 `X-Tenant-Id: 1001`，
         返回行必须仍全是本租户——租户以 token claim 为唯一权威（§10.3）。
    T10b 跨租户拒绝或只见自家数据：另一租户身份要么被授权层直接拒绝（401/403），
         要么返回的行全部属于它自己的租户，且与我的 taskId 零重叠。
    """
    _, body = S.call("GET", f"{API_MY_TASKS}?page=1&size=20", token=tok,
                     extra={"X-Tenant-Id": str(other_tenant)}, expect=(200,))
    rows = (body.get("data") or {}).get("records") or []
    ids = tuple(int(r["taskId"]) for r in rows)
    db = S.db_query("SELECT tenant_id FROM t_claim_task WHERE id IN %s", (ids,)) if ids else []
    foreign = [r for r in db if int(r["tenant_id"]) != 0]
    record("T10a 伪造租户头不生效（租户取 token claim）",
           not foreign and all(r["taskId"] in my_ids for r in rows),
           f"带 X-Tenant-Id={other_tenant} 返回 {len(rows)} 行，越租户 {len(foreign)} 行")

    code, body2 = S.call("GET", f"{API_MY_TASKS}?page=1&size=50", token=other_tok,
                         extra={"X-Tenant-Id": str(other_tenant)},
                         expect=(200, 401, 403))
    if code in (401, 403):
        record("T10b 跨租户访问被拒或仅见自家数据", True,
               f"http={code}（授权层直接拒绝，未泄露任何行）")
        return
    rows2 = (body2.get("data") or {}).get("records") or []
    ids2 = tuple(int(r["taskId"]) for r in rows2)
    db2 = S.db_query("SELECT tenant_id FROM t_claim_task WHERE id IN %s", (ids2,)) if ids2 else []
    leaked = [r for r in db2 if int(r["tenant_id"]) != int(other_tenant)]
    overlap = [r["taskId"] for r in rows2 if r["taskId"] in my_ids]
    record("T10b 跨租户访问被拒或仅见自家数据",
           not leaked and not overlap,
           f"租户{other_tenant} {len(rows2)} 行，越租户 {len(leaked)} 行，与我重叠 {len(overlap)} 行")


def main():
    print("=" * 72)
    print("作业台任务分配 E2E（批 1）")
    print("=" * 72)
    tok = S.login(*S.ADMIN)
    operator_id = str(S.db_query("SELECT id FROM iam_account WHERE username = %s",
                                 (S.ADMIN[0],))[0]["id"])
    other_tok = login_in(OTHER, OTHER_TENANT)

    total, my_ids = check_my_tasks(tok, operator_id)
    print(f"  · 我的待办 total={total}")

    tid, cid, how = ensure_claimable_task(tok)
    record("T0 可领取任务就绪", tid is not None, f"来源={how} taskId={tid} claimId={cid}")
    if tid is None:
        return report()
    check_claim_view(tok, cid)
    check_claim(tok, tid, operator_id)

    # 并发探针优先用**另一条**入池任务：任务已被领走时两条并发都拿 409，
    # 只能证明"没有双重成功"，证明不了"恰 1 成功"。有第二条就补上这一半。
    tid2, _ = pick_pending_task(exclude=tid)
    check_concurrent(tok, tid2 or tid)
    if tid2:
        print(f"  · 并发探针使用第二条入池任务 taskId={tid2}")

    check_isolation(tok, other_tok, OTHER_TENANT, my_ids)
    return report()


def report():
    ok = sum(1 for _, o, _ in RESULTS if o)
    print("\n" + "=" * 72)
    print(f"作业台任务分配 E2E：通过 {ok}/{len(RESULTS)} 项")
    print("=" * 72)
    for n, o, d in RESULTS:
        if not o:
            print(f"[FAIL] {n} :: {d}")
    if ok != len(RESULTS):
        sys.exit(1)
    print("✅ 作业台任务分配 E2E 全绿")


if __name__ == "__main__":
    main()
