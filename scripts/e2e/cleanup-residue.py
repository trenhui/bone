#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""E2E 运行级残留清理器（默认 dry-run，加 --apply 才真正执行）。

两条 E2E 全链路脚本（two-scenario-full-chain-e2e.py / app-to-data-full-chain-e2e.py）
每次运行都会以「Unix 时间戳后 9 位」为后缀创建一批**运行级**对象，跑完若不回收就会
永久污染库：动态物理表、meta_entity 实体定义及其子表、应用/模块、租户 0 的工作流
实例、审批账号等。本脚本按后缀做**定点回收**，是两份 E2E 默认 atexit teardown 的
执行体，也可手工对历史残留逐后缀清理。

设计原则（最小惊讶 + 防过宽删除）：
  1. 后缀必须是 ^\\d{6,}$（时间戳位数下限），杜绝误配业务对象。
  2. 只动 tenant_id=0（E2E 全部产物都在租户 0），租户 1001 演示数据绝不触碰。
  3. 平台基线账号（KEEP_ACCOUNTS）拒绝删除。
  4. BONE_ENV=prod/production 直接拒绝执行（环境护栏，与 90_clean.py 同源思路）。
  5. 默认 dry-run 只报告；--apply 才提交。运行级清理对象小、可再生，
     不强制要求全库备份（全量清理请走 scripts/seed/uat/90_clean.py，那份强制备份）。

用法:
  python3 cleanup-residue.py --suffix 793149221 --apply           # 清一次运行的全部产物
  python3 cleanup-residue.py --suffix 793149221 --suffix 793150206 --apply
  python3 cleanup-residue.py --suffix 793150206 --account e2e_po_appr2 --apply
  python3 cleanup-residue.py --suffix 793150206                   # dry-run，只报告
"""
import argparse
import os
import re
import sys

import pymysql

from e2e_lib import config

# 连接参数取 e2e_lib/config（BONE_E2E_MYSQL_* 环境变量注入，与其余 E2E 同口径；
# 口令不写字面量——gitleaks hardcoded-password 会拦，且 CI 可换真实凭据）
CONF = dict(
    host=config.MYSQL_HOST, port=int(config.MYSQL_PORT), user=config.MYSQL_USER,
    password=config.MYSQL_PWD, database=config.MYSQL_DB,
)

# 与 90_clean.py 对齐的平台基线账号：任何情况下不删
KEEP_ACCOUNTS = {"admin", "tenant_admin", "mdplatform", "mdapprover2", "meishan", "approver"}

SUFFIX_PAT = re.compile(r"^\d{6,}$")


def main():
    ap = argparse.ArgumentParser(description="E2E 运行级残留清理（按时间后缀定点回收）")
    ap.add_argument("--suffix", action="append", required=True, metavar="S",
                    help="运行时间后缀（6+ 位数字，可重复指定多个）")
    ap.add_argument("--account", action="append", default=[], metavar="USER",
                    help="要删除的 E2E 临时账号（如 e2e_po_appr2，可重复）")
    ap.add_argument("--apply", action="store_true", help="真正执行（缺省只 dry-run）")
    args = ap.parse_args()

    # ---- 环境护栏 ----
    env = (os.environ.get("BONE_ENV") or "").lower()
    if env in ("prod", "production"):
        print("!! 拒绝执行：BONE_ENV=%s，生产环境禁止运行 e2e 残留清理。" % env)
        sys.exit(2)
    for s in args.suffix:
        if not SUFFIX_PAT.match(s):
            print("!! 非法后缀 %r（必须 ^\\d{6,}$，防过宽删除）" % s)
            sys.exit(2)
    for u in args.account:
        if u in KEEP_ACCOUNTS:
            print("!! 拒绝删除基线账号 %r" % u)
            sys.exit(2)

    apply = args.apply
    print("MODE: %s   suffixes=%s   accounts=%s"
          % ("APPLY" if apply else "DRY-RUN", args.suffix, args.account or "-"))

    c = pymysql.connect(**CONF, autocommit=False)
    cur = c.cursor()
    cur.execute("SET FOREIGN_KEY_CHECKS=0")
    stats = {}

    def run(sql, args_=None, label=""):
        """执行并计数；单步失败只跳过不中断（teardown 不应因局部错误整体失败）。"""
        try:
            n = cur.execute(sql, args_) if args_ else cur.execute(sql)
            stats[label] = stats.get(label, 0) + n
            return n
        except Exception as e:
            print("   [skip] %s -> %s" % (label, str(e)[:90]))
            return 0

    def fetch(sql, args_=None):
        cur.execute(sql, args_) if args_ else cur.execute(sql)
        return cur.fetchall()

    # ================= 按后缀清理 =================
    for sfx in args.suffix:
        like_end = sfx + "$"
        print("\n==== 后缀 %s ====" % sfx)

        # ---- 1. 实体定义（meta_entity 及全部子表）----
        ents = fetch(
            "SELECT id, code FROM meta_entity WHERE tenant_id=0 AND code REGEXP %s",
            (like_end,))
        print("[1] meta_entity 实体定义 %d 条" % len(ents))
        if ents:
            ids = [e[0] for e in ents]
            codes = [e[1] for e in ents]
            for i in range(0, len(ids), 400):
                part = ids[i:i + 400]
                ph = ",".join(["%s"] * len(part))
                run("DELETE FROM meta_field WHERE entity_id IN (%s)" % ph, part,
                    label="meta_field")
                run("DELETE FROM meta_entity_ui_schema WHERE entity_id IN (%s)" % ph, part,
                    label="ui_schema")
                run("DELETE FROM mdm_entity WHERE meta_entity_id IN (%s)" % ph, part,
                    label="mdm_entity")
            run("DELETE FROM meta_entity_action_def WHERE tenant_id=0 AND entity_code REGEXP %s",
                (like_end,), label="action_def")
            run("DELETE FROM meta_ui_overlay WHERE tenant_id=0 AND entity_code REGEXP %s",
                (like_end,), label="ui_overlay")
            for code in codes:
                # seq_key 形如 0:<entity_code>:<field>:<date>，按实体编码精确匹配
                run("DELETE FROM meta_runtime_sequence WHERE seq_key LIKE %s",
                    ("%:" + code + ":%",), label="runtime_seq")
            ph = ",".join(["%s"] * len(ids))
            run("DELETE FROM meta_entity WHERE tenant_id=0 AND id IN (%s)" % ph, ids,
                label="meta_entity")

        # ---- 2. 运行期审计/动作执行 ----
        run("DELETE FROM meta_runtime_action_execution WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,), label="action_exec")
        run("DELETE FROM meta_runtime_record_audit WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,), label="record_audit")

        # ---- 3. 动态物理表（*chain_*/*e2e_*/t_*_<后缀>）----
        tabs = [r[0] for r in fetch(
            """SELECT table_name FROM information_schema.tables
               WHERE table_schema=%s AND table_name REGEXP %s
                 AND table_name REGEXP '^t_[a-z0-9_]+_[0-9]{6,}$|^publish_gate_[0-9]{6,}$'""",
            (CONF["database"], like_end))]
        print("[3] 动态物理表 %d 张: %s" % (len(tabs), ", ".join(tabs) or "-"))
        for t in tabs:
            # DDL 不受事务回滚保护：dry-run 只报告，--apply 才真 DROP。
            # DROP 的 rowcount 恒为 0，按张数显式计数。
            if apply:
                try:
                    cur.execute("DROP TABLE IF EXISTS `%s`" % t)
                    stats["drop_table"] = stats.get("drop_table", 0) + 1
                except Exception as e:
                    print("   [skip] drop_table:%s -> %s" % (t, str(e)[:90]))
            else:
                stats["drop_table"] = stats.get("drop_table", 0) + 1

        # ---- 4. 共享子表孤儿（父表刚被 DROP，按「父表已不存在」判定，不伤在途运行）----
        # t_expense_reimbursement_item / t_purchase_item 是跨运行共享表，父表带后缀；
        # 只删 tenant_id=0 且父记录已不存在的行——并发中的其他 E2E 运行父表仍在，其行保留。
        for child, fk, parent_pat in [
            ("t_expense_reimbursement_item", "claim_id", r"^t_expense_reimbursement"),
            ("t_purchase_item", "order_id", r"^t_purchase_order"),
        ]:
            parents = [r[0] for r in fetch(
                """SELECT table_name FROM information_schema.tables
                   WHERE table_schema=%s AND table_name REGEXP %s AND table_name <> %s
                     AND EXISTS (SELECT 1 FROM information_schema.columns c
                                 WHERE c.table_schema=%s AND c.table_name=table_name AND c.column_name='id')""",
                (CONF["database"], parent_pat, child, CONF["database"]))]
            if parents:
                union = " UNION ALL ".join("SELECT id FROM `%s`" % p for p in parents)
                run("DELETE FROM %s WHERE tenant_id=0 AND %s NOT IN (%s)" % (child, fk, union),
                    label="orphan:" + child)
            else:
                run("DELETE FROM %s WHERE tenant_id=0" % child, label="orphan0:" + child)

        # ---- 5. 工作流实例/任务/通知（tenant 0 + entity_code 匹配）----
        insts = [r[0] for r in fetch(
            "SELECT id FROM wf_instance WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,))]
        print("[5] wf_instance %d 条" % len(insts))
        if insts:
            for i in range(0, len(insts), 400):
                part = insts[i:i + 400]
                ph = ",".join(["%s"] * len(part))
                run("DELETE FROM wf_task WHERE tenant_id=0 AND instance_id IN (%s)" % ph, part,
                    label="wf_task")
                run("DELETE FROM wf_notification WHERE tenant_id=0 AND instance_id IN (%s)" % ph,
                    part, label="wf_notification")
                run("DELETE FROM wf_status_outbox WHERE tenant_id=0 AND instance_id IN (%s)" % ph,
                    part, label="wf_status_outbox")
            ph = ",".join(["%s"] * len(insts))
            run("DELETE FROM wf_instance WHERE tenant_id=0 AND id IN (%s)" % ph, insts,
                label="wf_instance")
        run("DELETE FROM wf_active_instance WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,), label="wf_active")
        run("DELETE FROM wf_notification WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,), label="wf_notify")
        run("DELETE FROM wf_status_outbox WHERE tenant_id=0 AND entity_code REGEXP %s",
            (like_end,), label="wf_outbox")

        # ---- 6. 应用 / 模块 / 应用权限（app-to-data 场景产物）----
        apps = [r[0] for r in fetch(
            "SELECT id FROM bone_application WHERE tenant_id=0 AND code REGEXP %s",
            (like_end,))]
        mods = [r[0] for r in fetch(
            "SELECT id FROM bone_module WHERE tenant_id=0 AND code REGEXP %s",
            (like_end,))]
        print("[6] bone_application %d 个 / bone_module %d 个" % (len(apps), len(mods)))
        all_app_ids = list(apps)
        if mods:
            ph = ",".join(["%s"] * len(mods))
            all_app_ids += [r[0] for r in fetch(
                "SELECT DISTINCT app_id FROM bone_module WHERE id IN (%s)" % ph, mods)]
        if all_app_ids:
            ph = ",".join(["%s"] * len(all_app_ids))
            run("DELETE FROM bone_app_permission WHERE tenant_id=0 AND app_id IN (%s)" % ph,
                all_app_ids, label="app_permission")
            run("DELETE FROM bone_module WHERE tenant_id=0 AND app_id IN (%s)" % ph,
                all_app_ids, label="bone_module")
        if apps:
            ph = ",".join(["%s"] * len(apps))
            run("DELETE FROM bone_application WHERE tenant_id=0 AND id IN (%s)" % ph, apps,
                label="bone_application")

    # ================= 按账号清理 =================
    for user in args.account:
        print("\n==== 账号 %s ====" % user)
        accs = [r[0] for r in fetch(
            "SELECT id FROM iam_account WHERE tenant_id=0 AND username=%s", (user,))]
        print("[A] iam_account %d 条" % len(accs))
        if accs:
            ph = ",".join(["%s"] * len(accs))
            run("DELETE FROM iam_refresh_token WHERE tenant_id=0 AND account_id IN (%s)" % ph,
                accs, label="refresh_token")
            run("DELETE FROM iam_account_role WHERE tenant_id=0 AND account_id IN (%s)" % ph,
                accs, label="account_role")
            run("DELETE FROM iam_account WHERE tenant_id=0 AND id IN (%s)" % ph, accs,
                label="iam_account")

    cur.execute("SET FOREIGN_KEY_CHECKS=1")
    if apply:
        c.commit()
        print("\nCOMMITTED. 影响统计:")
        for k, v in sorted(stats.items()):
            print("   %-18s %d" % (k, v))
        if not stats:
            print("   （无匹配残留，未产生任何删除）")
    else:
        c.rollback()
        print("\nDRY-RUN 未提交。将执行的语句统计:")
        for k, v in sorted(stats.items()):
            print("   %-18s %d" % (k, v))
        print("加 --apply 执行。")
    c.close()


if __name__ == "__main__":
    main()
