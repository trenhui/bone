#!/usr/bin/env python3
"""UAT 演示环境数据清理器（默认 dry-run，加 --apply 才真正执行）。

原则（业界最佳实践 · 环境护栏 + 最小惊讶）：
  1. 执行前必须有全库逻辑备份（_dump.py 产出），本脚本会校验备份文件存在且非空。
  2. 只删「可判定为测试残留」的对象：bak_/_bak_/_legacy_ 备份表、_e2e_/_chain_/_onb/探针表、
     e2e*/uatNNNN/verify* 租户，以及 username/role/menu 中带 e2e|qa|uitest|probe|verify|冒烟 的噪声。
  3. 平台基线（tenant 0 的 sys_*/meta_*/mdm_domain_template/int_*/gen_*/bone_*、iam_permission、
     租户 1001 的菜单树）一律保留。
  4. 全程 SET FOREIGN_KEY_CHECKS=0 避免顺序依赖；结束后恢复。

用法:
  python3 90_clean.py                 # dry-run，只报告
  python3 90_clean.py --apply         # 真执行
  python3 90_clean.py --apply --purge-demo-tenant   # 同时清空演示租户 1001 业务数据（灌数前用）
"""
import sys, os, re, json
import _conf

HERE = os.path.dirname(os.path.abspath(__file__))
BACKUP = os.path.join(HERE, "..", "..", "..", "outputs", "uat", "bone_backup_before_cleanup.sql")
DEMO_TENANT = 1001

JUNK_PATTERNS = [
    ("backup",   re.compile(r"^bak_|_bak_\d{8}$|_legacy_\d{8}$", re.I)),
    ("e2e_case", re.compile(r"_e2e_\d+$|_e2e_\d{14}$", re.I)),
    ("e2e_chain", re.compile(r"_chain_\d+$", re.I)),
    ("e2e_onb",  re.compile(r"_onb\d+$", re.I)),
    ("e2e_probe", re.compile(r"^(md_insp_\d+|md_repl_\d+|md_shield_\d+|md_uidesign_\d+|meta_iso_[0-9a-f]+|"
                             r"t_childcol_\d+|t_childcol_item_\d+|t_cc_probe\d+|t_cci_probe\d+|verify_p\d+_\d+|"
                             r"meta_mvp_\w+_\d{6,})$", re.I)),
]

# 事务型噪声表：tenant 0 上的机器残留（order_no=NULL / display_name=REC_<snowflake>）全清，
# 演示数据统一重建到租户 1001。
PURGE_T0_TABLES = """
t_order t_order_item
mdm_record mdm_quality_issue mdm_steward mdm_steward_scope mdm_category
wf_instance wf_task wf_active_instance wf_notification wf_status_outbox
bp_channel bp_channel_product bp_channel_buyer bp_inventory bp_shipment bp_shipment_trace
bp_payment bp_pricing_rule bp_outbox bp_idempotency_record bp_channel_broadcast_task bp_processed_event
t_claim_case t_claim_item t_claim_evidence t_quota_account t_quota_transaction
settle_order settle_funding_event appeal_record claim_decision_trace claim_decision_execution
claim_idempotency_record t_adj_calculation t_adj_calculation_line t_adj_liability
t_expense_reimbursement_item t_purchase_item
exts_plugin_execution_log exts_audit_log cnsl_recent_access cnsl_dashboard_widget ntf_message
""".split()

# 演示租户 1001：灌数前清空业务数据（不动 iam_menu 基线）
PURGE_DEMO_TABLES = """
iam_dept iam_account iam_account_role iam_role iam_role_permission
mdm_entity mdm_field mdm_record mdm_quality_issue mdm_steward mdm_steward_scope mdm_category
meta_entity meta_field
bp_channel bp_channel_product bp_channel_buyer bp_inventory bp_shipment bp_shipment_trace
bp_payment bp_pricing_rule bp_outbox bp_idempotency_record
t_order t_order_item
wf_instance wf_task wf_active_instance wf_notification wf_binding wf_process_def wf_process_draft
int_flow int_flow_node int_flow_connection int_execution_log
gen_data_source gen_table_metadata gen_column_metadata gen_generation_task gen_code_generation_history
t_claim_case t_claim_item t_claim_evidence t_quota_account t_quota_transaction
settle_order settle_funding_event appeal_record claim_decision_trace
exts_extension_point exts_extension_impl exts_plugin_version exts_plugin_execution_log
cnsl_dashboard_widget ntf_message bone_app_permission
""".split()

NOISE_ACCOUNT = re.compile(r"(?i)^(e2e|qa_|uitest|probe|test|uivr|verify)|(e2e|_[0-9]{10}$)")
NOISE_ROLE = re.compile(r"(?i)^(e2e|uivr|verify)|(e2e|-[0-9]{10}|_[0-9]{10}$)")
NOISE_MENU = re.compile(r"(?i)(e2e|测试中心|测试看板|uivr|verify)")
NOISE_DEPT = re.compile(r"(?i)(e2e|测试部门|uivr|verify)")
NOISE_TENANT = re.compile(r"(?i)^(e2e|uat\d+|verify\d+|meishan)")

KEEP_ROLE_CODES = {"SUPER_ADMIN", "USER", "MESSAGE-CENTER", "READONLY_VIEWER"}
KEEP_ACCOUNTS = {"admin", "tenant_admin", "mdplatform", "mdapprover2", "meishan", "approver"}


def classify(name):
    for kind, pat in JUNK_PATTERNS:
        if pat.search(name):
            return kind
    return None


def main():
    apply = "--apply" in sys.argv
    purge_demo = "--purge-demo-tenant" in sys.argv
    print("MODE: %s%s" % ("APPLY" if apply else "DRY-RUN", "  (+purge demo tenant)" if purge_demo else ""))

    if apply:
        if not (os.path.exists(BACKUP) and os.path.getsize(BACKUP) > 1_000_000):
            print("!! 拒绝执行：未找到有效全库备份 %s（请先跑 _dump.py）" % BACKUP)
            sys.exit(2)
        print("备份已确认: %s (%.1f MB)" % (BACKUP, os.path.getsize(BACKUP) / 1048576))

    c = _conf.connect(autocommit=False)
    cur = c.cursor()
    cur.execute("SET FOREIGN_KEY_CHECKS=0")
    stats = {}

    def run(sql, args=None, label=""):
        try:
            n = cur.execute(sql, args) if args else cur.execute(sql)
            stats[label or sql.split()[0]] = stats.get(label or sql.split()[0], 0) + n
            return n
        except Exception as e:
            print("   [skip] %s -> %s" % (label, str(e)[:90]))
            return 0

    # ---------- 1. 垃圾表 ----------
    cur.execute("show tables")
    all_tabs = [r[0] for r in cur.fetchall()]
    junk_tabs = [t for t in all_tabs if classify(t)]
    print("\n[1] 垃圾表 %d 张" % len(junk_tabs))
    for kind in ["backup", "e2e_case", "e2e_chain", "e2e_onb", "e2e_probe"]:
        ks = [t for t in junk_tabs if classify(t) == kind]
        if ks:
            print("    %-10s %d 张" % (kind, len(ks)))
    if apply:
        for t in junk_tabs:
            run("DROP TABLE IF EXISTS `%s`" % t, label="drop_table")
        print("    dropped:", len(junk_tabs))

    # ---------- 2. E2E 垃圾租户 ----------
    cur.execute("SELECT id, code FROM iam_tenant")
    tenants = cur.fetchall()
    junk_tenants = [t for t in tenants if NOISE_TENANT.match(str(t[1]))]
    junk_ids = [t[0] for t in junk_tenants]
    print("\n[2] E2E/验证垃圾租户 %d 个（保留 %d 个）" % (len(junk_tenants), len(tenants) - len(junk_tenants)))
    if apply and junk_ids:
        cur.execute("""SELECT table_name FROM information_schema.columns
                       WHERE table_schema='bone' AND column_name='tenant_id'""")
        for (t,) in cur.fetchall():
            if classify(t):
                continue
            ph = ",".join(["%s"] * len(junk_ids))
            run("DELETE FROM `%s` WHERE tenant_id IN (%s)" % (t, ph), junk_ids, label="del_tenant_data")
        ph = ",".join(["%s"] * len(junk_ids))
        run("DELETE FROM iam_tenant WHERE id IN (%s)" % ph, junk_ids, label="del_tenant")
        print("    deleted tenants:", len(junk_ids))

    # ---------- 3. 租户 0 事务噪声 ----------
    print("\n[3] 租户 0 事务噪声表 %d 张" % len(PURGE_T0_TABLES))
    if apply:
        for t in PURGE_T0_TABLES:
            run("DELETE FROM `%s` WHERE tenant_id=0" % t, label="t0_purge")

    # ---------- 4. IAM 噪声（租户 0） ----------
    print("\n[4] IAM 噪声清理（租户 0：E2E 账号/角色/菜单/部门）")
    cur.execute("SELECT id, username FROM iam_account WHERE tenant_id=0")
    accts = cur.fetchall()
    bad_accts = [a[0] for a in accts if a[1] not in KEEP_ACCOUNTS and NOISE_ACCOUNT.search(a[1] or "")]
    print("    账号 %d/%d 命中噪声" % (len(bad_accts), len(accts)))
    cur.execute("SELECT id, code FROM iam_role WHERE tenant_id=0")
    roles = cur.fetchall()
    bad_roles = [r[0] for r in roles if r[1] not in KEEP_ROLE_CODES and NOISE_ROLE.search(r[1] or "")]
    print("    角色 %d/%d 命中噪声" % (len(bad_roles), len(roles)))
    cur.execute("SELECT id, name FROM iam_menu WHERE tenant_id=0")
    menus = cur.fetchall()
    bad_menus = [m[0] for m in menus if NOISE_MENU.search(m[1] or "")]
    print("    菜单 %d/%d 命中噪声" % (len(bad_menus), len(menus)))
    cur.execute("SELECT id, name FROM iam_dept WHERE tenant_id=0")
    depts = cur.fetchall()
    bad_depts = [d[0] for d in depts if NOISE_DEPT.search(d[1] or "")]
    print("    部门 %d/%d 命中噪声" % (len(bad_depts), len(depts)))
    if apply:
        for grp, ids, tbl in [("acct", bad_accts, "iam_account"), ("role", bad_roles, "iam_role"),
                              ("menu", bad_menus, "iam_menu"), ("dept", bad_depts, "iam_dept")]:
            for i in range(0, len(ids), 400):
                part = ids[i:i + 400]
                ph = ",".join(["%s"] * len(part))
                run("DELETE FROM `%s` WHERE id IN (%s)" % (tbl, ph), part, label="del_" + grp)
        # 关联表孤儿
        if bad_accts:
            ph = ",".join(["%s"] * len(bad_accts))
            run("DELETE FROM iam_account_role WHERE account_id IN (%s)" % ph, bad_accts, label="del_ar")
        if bad_roles:
            ph = ",".join(["%s"] * len(bad_roles))
            run("DELETE FROM iam_role_permission WHERE role_id IN (%s)" % ph, bad_roles, label="del_rp")
        # 演示租户里的探针账号
        run("DELETE FROM iam_account WHERE tenant_id=%s AND real_name LIKE '%%探针%%'", (DEMO_TENANT,), label="del_demo_probe")
        run("DELETE FROM iam_account WHERE tenant_id=%s AND username='mdsmoke'", label="del_demo_smoke")

    # ---------- 4b. 白名单兜底：租户 0 只保留基线账号/角色 ----------
    print("\n[4b] 白名单兜底（IAM 基线只保留 %s / %s）" % (sorted(KEEP_ACCOUNTS), sorted(KEEP_ROLE_CODES)))
    cur.execute("SELECT id, username FROM iam_account WHERE tenant_id=0")
    rest_accts = [r[0] for r in cur.fetchall() if r[1] not in KEEP_ACCOUNTS]
    print("    账号待删 %d" % len(rest_accts))
    cur.execute("SELECT id, code FROM iam_role WHERE tenant_id=0")
    rest_roles = [r[0] for r in cur.fetchall() if r[1] not in KEEP_ROLE_CODES]
    print("    角色待删 %d" % len(rest_roles))
    if apply:
        for grp, ids, tbl in [("acct0", rest_accts, "iam_account"), ("role0", rest_roles, "iam_role")]:
            for i in range(0, len(ids), 400):
                part = ids[i:i + 400]
                ph = ",".join(["%s"] * len(part))
                run("DELETE FROM `%s` WHERE id IN (%s)" % (tbl, ph), part, label="del_" + grp)
        if rest_accts:
            ph = ",".join(["%s"] * len(rest_accts))
            run("DELETE FROM iam_account_role WHERE account_id IN (%s)" % ph, rest_accts, label="del_ar0")
        if rest_roles:
            ph = ",".join(["%s"] * len(rest_roles))
            run("DELETE FROM iam_role_permission WHERE role_id IN (%s)" % ph, rest_roles, label="del_rp0")

    # ---------- 5. 演示租户业务数据（可选） ----------
    if purge_demo:
        print("\n[5] 清空演示租户 %d 业务数据（%d 张表，保留 iam_menu 基线）" % (DEMO_TENANT, len(PURGE_DEMO_TABLES)))
        if apply:
            for t in PURGE_DEMO_TABLES:
                run("DELETE FROM `%s` WHERE tenant_id=%s" % (t, DEMO_TENANT), label="demo_purge")

    # ---------- 6. 无 tenant_id 表的孤儿兜底（依赖父表已清空） ----------
    print("\n[6] 孤儿兜底：无 tenant_id 列的子表按父表反查清理")
    orphan_sql = [
        ("t_order_item", "DELETE FROM t_order_item WHERE order_id NOT IN (SELECT id FROM t_order)"),
        ("iam_role_permission", "DELETE FROM iam_role_permission WHERE role_id NOT IN (SELECT id FROM iam_role)"),
        ("iam_account_role", "DELETE FROM iam_account_role WHERE account_id NOT IN (SELECT id FROM iam_account)"),
        ("iam_account_role", "DELETE FROM iam_account_role WHERE role_id NOT IN (SELECT id FROM iam_role)"),
        ("mdm_field", "DELETE FROM mdm_field WHERE master_data_entity_id NOT IN (SELECT id FROM mdm_entity)"),
        ("mdm_record", "DELETE FROM mdm_record WHERE mdm_entity_id NOT IN (SELECT id FROM mdm_entity)"),
        ("mdm_steward_scope", "DELETE FROM mdm_steward_scope WHERE steward_id NOT IN (SELECT id FROM mdm_steward)"),
        ("meta_field", "DELETE FROM meta_field WHERE entity_id NOT IN (SELECT id FROM meta_entity)"),
        ("bp_shipment_trace", "DELETE FROM bp_shipment_trace WHERE shipment_id NOT IN (SELECT id FROM bp_shipment)"),
        ("int_flow_node", "DELETE FROM int_flow_node WHERE flow_id NOT IN (SELECT id FROM int_flow)"),
        ("int_flow_connection", "DELETE FROM int_flow_connection WHERE flow_id NOT IN (SELECT id FROM int_flow)"),
        ("wf_task", "DELETE FROM wf_task WHERE instance_id NOT IN (SELECT id FROM wf_instance)"),
        ("t_claim_item", "DELETE FROM t_claim_item WHERE claim_id NOT IN (SELECT id FROM t_claim_case)"),
        ("t_claim_evidence", "DELETE FROM t_claim_evidence WHERE claim_id NOT IN (SELECT id FROM t_claim_case)"),
        ("gen_column_metadata", "DELETE FROM gen_column_metadata WHERE table_metadata_id NOT IN (SELECT id FROM gen_table_metadata)"),
    ]
    for label, sql in orphan_sql:
        run(sql, label="orphan:" + label)

    # ---------- 7. 孤儿租户清扫：tenant_id 指向已删除租户 ----------
    print("\n[7] 孤儿租户清扫（tenant_id 不在 iam_tenant 且 != 0）")
    cur.execute("""SELECT table_name FROM information_schema.columns
                   WHERE table_schema='bone' AND column_name='tenant_id' AND table_name NOT LIKE 'bak\\_%'""")
    for (t,) in cur.fetchall():
        if classify(t):
            continue
        run("DELETE FROM `%s` WHERE tenant_id <> 0 AND tenant_id NOT IN (SELECT id FROM iam_tenant)" % t,
            label="orphan_tenant")
    # IAM 三张表的租户归属可能已失联
    for t in ["iam_account", "iam_role", "iam_dept", "iam_menu"]:
        run("DELETE FROM `%s` WHERE tenant_id <> 0 AND tenant_id NOT IN (SELECT id FROM iam_tenant)" % t,
            label="orphan_iam:" + t)

    # ---------- 8. 运行期日志与孤儿元数据 ----------
    print("\n[8] 运行期日志 / 孤儿元数据（安全日志、孤儿反向工程元数据）")
    cur.execute("""SELECT table_name FROM information_schema.tables WHERE table_schema='bone'""")
    exist = set(r[0] for r in cur.fetchall())
    cur.execute("SELECT id, original_table_name FROM gen_table_metadata")
    gtm = cur.fetchall()
    dead_tm = [i for i, t in gtm if t not in exist]
    print("    gen_table_metadata 指向已删表: %d" % len(dead_tm))
    if apply and dead_tm:
        for i in range(0, len(dead_tm), 400):
            part = dead_tm[i:i + 400]
            run("DELETE FROM gen_table_metadata WHERE id IN (%s)" % ",".join(str(x) for x in part),
                label="del_dead_gtm")
        for i in range(0, len(dead_tm), 400):
            part = dead_tm[i:i + 400]
            run("DELETE FROM gen_column_metadata WHERE table_metadata_id IN (%s)" % ",".join(str(x) for x in part),
                label="del_dead_gcm")
    run("DELETE FROM gen_column_metadata WHERE table_metadata_id NOT IN (SELECT id FROM gen_table_metadata)",
        label="orphan_gcm")
    # 运行期日志：由系统自动再生，演示基线从零开始
    for t in ["iam_audit_log", "iam_refresh_token", "sys_alert_event", "sys_log", "sys_schedule_task_log",
              "cnsl_recent_access", "meta_runtime_record_audit", "meta_runtime_action_execution",
              "int_outbox", "int_dead_letter", "bp_outbox", "bp_processed_event",
              "bp_idempotency_record", "claim_idempotency_record", "exts_plugin_execution_log",
              "exts_audit_log", "wf_status_outbox"]:
        run("DELETE FROM `%s`" % t, label="purge_log:" + t)

    cur.execute("SET FOREIGN_KEY_CHECKS=1")
    if apply:
        c.commit()
        print("\nCOMMITTED. 影响统计:")
        for k, v in sorted(stats.items()):
            print("   %-16s %d" % (k, v))
        cur.execute("show tables")
        print("\n剩余表数:", len(cur.fetchall()))
        cur.execute("SELECT COUNT(*) FROM iam_tenant")
        print("剩余租户数:", cur.fetchone()[0])
    else:
        c.rollback()
        print("\nDRY-RUN 未提交。加 --apply 执行。")
    c.close()


if __name__ == "__main__":
    main()
