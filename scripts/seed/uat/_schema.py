#!/usr/bin/env python3
"""导出目标表的真实列定义（从 information_schema），供种子脚本对齐。

用法: python3 _schema.py [table ...]   不给参数则打印全部非垃圾表的列清单
输出: 表名 -> [(列名, 类型, 可空, 默认值, 键, 注释)]
"""
import sys, json
import _conf


TARGETS = """
iam_tenant iam_dept iam_account iam_role iam_permission iam_account_role iam_role_permission iam_menu
sys_dict_type sys_dict_item sys_dict_hierarchy sys_config sys_alert_rule sys_schedule_task
meta_entity meta_field meta_model_template meta_model_template_field meta_runtime_action_execution
md_entity md_field md_record md_standard md_quality_rule
mdm_entity mdm_field mdm_record mdm_category mdm_domain_template
mdm_steward mdm_steward_scope mdm_reference_set mdm_reference_value mdm_quality_issue
mdm_qcheck_task mdm_qcheck_detail mdm_qcheck_report mdm_feedback
wf_process_def wf_process_draft wf_instance wf_active_instance wf_task wf_binding wf_notification
int_connector int_flow int_flow_node int_flow_connection int_template int_execution_log
gen_data_source gen_table_metadata gen_column_metadata gen_code_template gen_generation_task gen_code_generation_history
bp_channel bp_channel_product bp_channel_buyer bp_inventory bp_shipment bp_shipment_trace bp_payment bp_pricing_rule
t_order t_order_item
exts_extension_point exts_extension_impl exts_plugin_version exts_plugin_execution_log
cnsl_dashboard_widget cnsl_recent_access ntf_message
bone_module bone_application bone_app_permission
t_claim_case t_claim_item t_claim_evidence t_quota_account t_quota_transaction
settle_order settle_funding_event appeal_record claim_decision_trace
""".split()


def main():
    c = _conf.connect()
    cur = c.cursor()
    names = sys.argv[1:] or TARGETS
    out = {}
    for t in names:
        cur.execute("""SELECT column_name, column_type, is_nullable, column_default, column_key, extra, column_comment
                       FROM information_schema.columns WHERE table_schema='bone' AND table_name=%s
                       ORDER BY ordinal_position""", (t,))
        rows = cur.fetchall()
        if not rows:
            print("\n### %s  <MISSING>" % t)
            continue
        out[t] = [list(r) for r in rows]
        print("\n### %s" % t)
        for r in rows:
            print("  %-28s %-22s null=%-3s def=%-12s key=%-3s %s" % (
                r[0], r[1], r[2], str(r[3])[:12], r[4], (r[6] or "")[:40]))
    if "--json" in sys.argv:
        p = sys.argv[sys.argv.index("--json") + 1]
        json.dump(out, open(p, "w"), ensure_ascii=False, indent=1)
        print("\n-> %s" % p)


if __name__ == "__main__":
    main()
