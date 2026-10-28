#!/usr/bin/env python3
"""只读盘点：把 465 张表分成 [保留 / E2E残留 / 备份残留]，并统计 E2E 垃圾租户。

用法: python3 _classify.py [--json out.json]
不修改任何数据。
"""
import re, json, sys
import _conf


# ---------- 垃圾表判定规则（按优先级） ----------
JUNK_PATTERNS = [
    ("backup",   re.compile(r"^bak_|_bak_\d{8}$|_legacy_\d{8}$", re.I)),
    ("e2e_case", re.compile(r"_e2e_\d+$|_e2e_\d{14}$", re.I)),
    ("e2e_chain", re.compile(r"_chain_\d+$", re.I)),
    ("e2e_onb",  re.compile(r"_onb\d+$", re.I)),
    ("e2e_probe", re.compile(r"^(md_insp_\d+|md_repl_\d+|md_shield_\d+|md_uidesign_\d+|meta_iso_[0-9a-f]+|"
                             r"t_childcol_\d+|t_childcol_item_\d+|t_cc_probe\d+|t_cci_probe\d+|verify_p\d+_\d+|"
                             r"meta_mvp_\w+_\d{6,})$", re.I)),
]
# 生产/基线表白名单：即使被规则误伤也保留
KEEP_HARD = set("""
iam_tenant iam_dept iam_account iam_role iam_permission iam_account_role iam_role_permission
iam_menu iam_audit_log iam_audit_settings iam_policy iam_refresh_token
sys_dict sys_dict_type sys_dict_item sys_dict_hierarchy sys_dict_item_text
sys_config sys_config_history sys_alert_rule sys_alert_event sys_log
sys_schedule_task sys_schedule_task_log
meta_entity meta_field meta_entity_relation meta_entity_action_def meta_entity_ui_schema
meta_model_template meta_model_template_field meta_model_template_action meta_model_template_ui_history
meta_ui_overlay meta_code_template meta_runtime_action_execution meta_runtime_record_audit
md_entity md_field md_record md_lineage md_standard md_quality_rule
mdm_entity mdm_field mdm_record mdm_category mdm_domain_template mdm_template_version
mdm_steward mdm_steward_scope mdm_entity_subscription mdm_reference_set mdm_reference_value
mdm_reference_value_tenant mdm_quality_issue mdm_qcheck_task mdm_qcheck_detail mdm_qcheck_report
mdm_feedback mdm_model_drift
wf_process_def wf_process_draft wf_instance wf_active_instance wf_task wf_binding
wf_notification wf_status_outbox
int_connector int_flow int_flow_node int_flow_connection int_template int_execution_log
int_dead_letter int_outbox
gen_data_source gen_table_metadata gen_column_metadata gen_code_template gen_type_mapping
gen_generation_task gen_code_generation_history
bp_channel bp_channel_product bp_channel_buyer bp_channel_broadcast_task bp_inventory
bp_shipment bp_shipment_trace bp_payment bp_pricing_rule bp_supplier bp_outbox
bp_idempotency_record bp_processed_event
t_order t_order_item t_purchase_item t_expense_reimbursement_item
t_claim_case t_claim_item t_claim_evidence t_quota_account t_quota_transaction
settle_order settle_funding_event appeal_record
claim_decision_trace claim_decision_execution claim_idempotency_record
t_adj_calculation t_adj_calculation_line t_adj_liability
bone_module bone_application bone_app_permission
exts_extension_point exts_extension_impl exts_plugin_version exts_plugin_execution_log exts_audit_log
cnsl_dashboard_widget cnsl_notification cnsl_recent_access ntf_message
field_metadata column_allocation biz_sales_order biz_sales_order_item
""".split())


def classify(name):
    for kind, pat in JUNK_PATTERNS:
        if pat.search(name):
            return kind
    return None


def main():
    c = _conf.connect()
    cur = c.cursor()
    cur.execute("show tables")
    tabs = sorted(r[0] for r in cur.fetchall())

    junk = []
    keep = []
    for t in tabs:
        k = classify(t)
        if k and t not in KEEP_HARD:
            junk.append((t, k))
        else:
            keep.append(t)

    # 真实行数
    def rowcount(t):
        try:
            cur.execute("SELECT COUNT(*) FROM `%s`" % t)
            return cur.fetchone()[0]
        except Exception:
            return -1

    junk_stats = {}
    for t, k in junk:
        junk_stats.setdefault(k, []).append((t, rowcount(t)))

    print("=" * 70)
    print("表总数 %d  |  保留 %d  |  判定垃圾 %d" % (len(tabs), len(keep), len(junk)))
    print("=" * 70)
    total_junk_rows = 0
    for k in sorted(junk_stats):
        items = sorted(junk_stats[k])
        s = sum(x[1] for x in items if x[1] > 0)
        total_junk_rows += s
        print("\n[%s]  表 %d 张, 行 %d" % (k, len(items), s))
        for t, n in items[:8]:
            print("    %-58s %6d" % (t, n))
        if len(items) > 8:
            print("    ... 其余 %d 张" % (len(items) - 8))
    print("\n垃圾表总行数: %d" % total_junk_rows)

    # E2E 垃圾租户
    cur.execute("SELECT id, code, name, status FROM iam_tenant")
    tenants = cur.fetchall()
    junk_t = [t for t in tenants if re.match(r"(?i)^(e2e|uat\d+|verify\d+|meishan)", str(t[1]))]
    good_t = [t for t in tenants if t not in junk_t]
    print("\n" + "=" * 70)
    print("租户总数 %d  |  E2E/验证垃圾租户 %d  |  保留 %d" % (len(tenants), len(junk_t), len(good_t)))
    print("保留租户:", good_t)
    junk_t_ids = [t[0] for t in junk_t]
    print("垃圾租户 ID 数:", len(junk_t_ids))

    # 租户 1001 现状
    cur.execute("SELECT COUNT(*) FROM iam_account WHERE tenant_id=1001")
    print("\n租户1001 账号数:", cur.fetchone()[0])
    cur.execute("SELECT id, username, real_name FROM iam_account WHERE tenant_id=1001")
    for r in cur.fetchall():
        print("   ", r)

    if "--json" in sys.argv:
        path = sys.argv[sys.argv.index("--json") + 1]
        json.dump({"junk_tables": [{"name": t, "kind": k, "rows": dict((a, b) for a, b in [(x[0], x[1]) for x in junk_stats[k]])[t]}
                                   for k in junk_stats for t, _ in junk_stats[k]],
                   "keep_tables": keep,
                   "junk_tenant_ids": [str(x) for x in junk_t_ids]},
                  open(path, "w"), ensure_ascii=False, indent=1)
        print("\nwritten ->", path)


if __name__ == "__main__":
    main()
