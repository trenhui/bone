#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
claim_task_owner_backfill.py —— 在途案件任务归属回填（先探测、再写入、多条候选即中止）

背景
----
批 1 把「案件归属」从 `t_claim_case.first_reviewer_id` 一类单列升级为独立任务行
（`t_claim_task`）。上线前已在办的案件没有任务行——它们不会出现在任何人的待办里，
也不会出现在部门池里，等于从工作台凭空消失。本脚本一次性把存量在办案件回填成任务。

设计口径（design D6）
--------------------
* 有 `first_reviewer_id` ⇒ 回填为该人的 `ASSIGNED` 任务（这是**既有事实**，不是新决策）；
* `first_reviewer_id` 为空 ⇒ 回填为 `PENDING_CLAIM` 入池任务（**不编造指派**：
  没有归属人就该在池子里等人领，随便挑一个人是栽赃）；
* 同一案件已存在**多条**生效任务 ⇒ 说明数据已脏（唯一键本应只允许一条），
  打印清单并中止，交人工裁决——脚本不做「猜哪条对」。

为什么默认 dry-run
------------------
回填是写生产数据的一次性动作，且判据（first_reviewer_id 是否可信）依赖业务判断。
脚本先出清单（含 CSV）供运营确认，`--apply` 才落库；两者走同一段计划生成逻辑，
不存在「清单和实际写入不一致」的可能。

幂等
----
* 已有生效任务的案件直接跳过（不重复建任务）；
* 任务主键由 `sha1(tenant:claim:type)` 确定性推导 ⇒ 重跑同案得到同一个 id，
  即使并发重跑也只会撞唯一键而不是产生第二条任务（`INSERT IGNORE` + 唯一键兜底）。

用法
----
    # 1) 探测（默认，不写库）
    BONE_DB_PASSWORD=... python3 scripts/claim_task_owner_backfill.py [--csv plan.csv] [--tenant 1]

    # 2) 确认后回填
    BONE_DB_PASSWORD=... python3 scripts/claim_task_owner_backfill.py --apply

退出码：0 = 成功（或无事可做）；2 = 存在多条生效任务的脏案件，已中止；3 = 终态断言不符。
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import os
import sys

import pymysql

# 在办（工作台待办）案件状态：40 待理算 / 41 理算中。与 ClaimStatus 枚举名一致。
ACTIVE_CLAIM_STATUSES = ("PENDING_ADJUST", "ADJUSTING")
TASK_TYPE = "INITIAL_REVIEW"
REASON = "BACKFILL"


def _env(key: str, default: str) -> str:
    return os.environ.get(key, default)


def connect():
    return pymysql.connect(
        host=_env("BONE_DB_HOST", "127.0.0.1"),
        port=int(_env("BONE_DB_PORT", "3306")),
        user=_env("BONE_DB_USER", "root"),
        password=_env("BONE_DB_PASSWORD", "mysql123"),
        database=_env("BONE_DB_NAME", "bone"),
        charset="utf8mb4",
        autocommit=False,
    )


def deterministic_id(tenant_id: int, claim_id: int, task_type: str) -> int:
    """确定性主键：同 (租户, 案件, 类型) 永远得到同一个 id ⇒ 重跑幂等。"""
    digest = hashlib.sha1(f"{tenant_id}:{claim_id}:{task_type}".encode("utf-8")).hexdigest()
    # 取 15 位 hex ⇒ 60 bit，避开 63 位符号位；与雪花 ID 同为正 bigint。
    return int(digest[:15], 16)


def probe(cur, tenant: int | None) -> tuple[list[dict], list[str]]:
    """探测存量：返回待回填计划 + 脏案件清单（多条生效任务）。"""
    tenant_where = "" if tenant is None else " AND c.tenant_id = %(tenant)s"
    params = {} if tenant is None else {"tenant": tenant}

    # ① 脏数据探测：在办案件里已存在 ≥2 条生效任务的，必须先人工裁决。
    cur.execute(
        f"""
        SELECT t.tenant_id, t.claim_id, COUNT(*) AS active_cnt,
               GROUP_CONCAT(t.task_no ORDER BY t.id) AS task_nos
          FROM t_claim_task t
         WHERE t.deleted = 0
           AND t.status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING')
           {tenant_where.replace('c.tenant_id', 't.tenant_id')}
         GROUP BY t.tenant_id, t.claim_id
        HAVING COUNT(*) > 1
        ORDER BY t.claim_id
        """,
        params,
    )
    conflicts = [
        f"tenant={r[0]} claim={r[1]} active={r[2]} tasks=[{r[3]}]" for r in cur.fetchall()
    ]

    # ② 计划：在办且「无生效任务」的案件。
    cur.execute(
        f"""
        SELECT c.tenant_id, c.id, c.claim_no, c.first_reviewer_id,
               COALESCE(c.claim_type, '') AS claim_type,
               COALESCE(c.source_channel, '') AS source_channel,
               COALESCE(c.claim_amount, 0) AS claim_amount
          FROM t_claim_case c
         WHERE c.deleted = 0
           AND c.status IN {ACTIVE_CLAIM_STATUSES}
           {tenant_where}
           AND NOT EXISTS (
                 SELECT 1 FROM t_claim_task t
                  WHERE t.tenant_id = c.tenant_id
                    AND t.claim_id = c.id
                    AND t.task_type = %(task_type)s
                    AND t.deleted = 0
                    AND t.status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING'))
         ORDER BY c.tenant_id, c.id
        """,
        {**params, "task_type": TASK_TYPE},
    )
    plan = []
    for tenant_id, claim_id, claim_no, reviewer, claim_type, channel, amount in cur.fetchall():
        assigned = reviewer is not None and str(reviewer).strip() != ""
        plan.append(
            {
                "tenant_id": int(tenant_id),
                "claim_id": int(claim_id),
                "claim_no": claim_no,
                "task_type": TASK_TYPE,
                "status": "ASSIGNED" if assigned else "PENDING_CLAIM",
                "assignee_type": "USER" if assigned else None,
                "assignee_id": str(reviewer).strip() if assigned else None,
                "claim_type": claim_type,
                "source_channel": channel,
                "claim_amount": str(amount),
                "reason": REASON,
            }
        )
    return plan, conflicts


def print_plan(plan: list[dict]) -> None:
    assigned = [p for p in plan if p["status"] == "ASSIGNED"]
    pooled = [p for p in plan if p["status"] != "ASSIGNED"]
    print(f"待回填案件 {len(plan)} 条：按既有归属人回填 {len(assigned)} 条，入池 {len(pooled)} 条")
    for p in plan[:20]:
        who = p["assignee_id"] or "(入池)"
        print(f"  tenant={p['tenant_id']} claim={p['claim_id']} {p['claim_no']} -> {p['status']} {who}")
    if len(plan) > 20:
        print(f"  ... 其余 {len(plan) - 20} 条见 CSV")


def apply_plan(cur, plan: list[dict]) -> int:
    """写入并做终态断言；返回写入行数（断言失败时异常上抛，由调用方回滚）。"""
    written = 0
    for p in plan:
        cur.execute(
            """
            INSERT IGNORE INTO t_claim_task
              (id, task_no, claim_id, task_type, status, assignee_type, assignee_id,
               assign_rule_id, assign_reason, priority, claim_type, source_channel, claim_amount,
               assigned_at, tenant_id, created_at, updated_at, version, deleted)
            VALUES
              (%(id)s, %(task_no)s, %(claim_id)s, %(task_type)s, %(status)s,
               %(assignee_type)s, %(assignee_id)s, NULL, %(reason)s, 100,
               %(claim_type)s, %(source_channel)s, %(claim_amount)s,
               IF(%(is_assigned)s, NOW(3), NULL), %(tenant_id)s, NOW(3), NOW(3), 0, 0)
            """,
            {
                **p,
                "id": deterministic_id(p["tenant_id"], p["claim_id"], p["task_type"]),
                "task_no": f"BT-{p['claim_id']}",
                # 入池任务没有指派时间；写成 1970 之类「假时间」会在灰度统计里被当成早已分配。
                "is_assigned": 1 if p["status"] == "ASSIGNED" else 0,
            },
        )
        written += cur.rowcount
    # 终态断言：每条形计划都必须恰有 1 条生效任务，且归属与计划一致。
    bad = []
    for p in plan:
        cur.execute(
            """
            SELECT status, COALESCE(assignee_id, '')
              FROM t_claim_task
             WHERE tenant_id = %s AND claim_id = %s AND task_type = %s
               AND deleted = 0 AND status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING')
            """,
            (p["tenant_id"], p["claim_id"], p["task_type"]),
        )
        rows = cur.fetchall()
        if len(rows) != 1:
            bad.append(f"claim={p['claim_id']} 生效任务数={len(rows)}（应为 1）")
            continue
        status, assignee = rows[0]
        if status != p["status"]:
            bad.append(f"claim={p['claim_id']} 状态={status}（计划 {p['status']}）")
        if (p["assignee_id"] or "") != (assignee or ""):
            bad.append(f"claim={p['claim_id']} 归属={assignee!r}（计划 {p['assignee_id']!r}）")
    if bad:
        raise AssertionError("终态断言不符：" + "；".join(bad[:10]))
    return written


def main() -> int:
    parser = argparse.ArgumentParser(description="在途案件任务归属回填（先探测、再写入）")
    parser.add_argument("--apply", action="store_true", help="确认落库（默认只出清单）")
    parser.add_argument("--tenant", type=int, default=None, help="只处理指定租户")
    parser.add_argument("--csv", default=None, help="把计划写成 CSV（供运营确认）")
    args = parser.parse_args()

    conn = connect()
    try:
        with conn.cursor() as cur:
            plan, conflicts = probe(cur, args.tenant)

        if conflicts:
            print("❌ 发现同一案件存在多条生效任务（唯一键本应只允许一条，数据已脏）：")
            for c in conflicts[:50]:
                print("   " + c)
            print("⇒ 中止：脚本不猜哪条正确，请人工裁决后重跑。")
            return 2

        if not plan:
            print("OK: 无需回填（所有在办案件都已有生效任务）")
            return 0

        print_plan(plan)
        if args.csv:
            with open(args.csv, "w", newline="", encoding="utf-8") as fh:
                writer = csv.DictWriter(fh, fieldnames=list(plan[0].keys()))
                writer.writeheader()
                writer.writerows(plan)
            print(f"计划已写入 {args.csv}")

        if not args.apply:
            print("DRY-RUN：未写入任何数据。确认清单后加 --apply 落库。")
            return 0

        with conn.cursor() as cur:
            written = apply_plan(cur, plan)
        conn.commit()
        print(f"✅ 回填完成：写入 {written} 行，终态断言通过（每案恰 1 条生效任务）")
        return 0
    except AssertionError as exc:
        conn.rollback()
        print(f"❌ {exc}")
        print("⇒ 已回滚，未留下半截数据。")
        return 3
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
