#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UAT 演示数据校验门禁（8 类断言，任一失败即 exit 1）。

用法: python3 95_validate.py [--json <报告路径>]
"""
import sys, os, json, datetime
import _conf

TENANT = 1001
FAILS, PASSES = [], []


def check(gate, name, sql, expect_zero=True, fmt=None):
    """expect_zero=True：SQL 返回单值，必须为 0。"""
    c = _conf.connect()
    cur = c.cursor()
    try:
        cur.execute(sql)
        row = cur.fetchone()
        val = row[0] if row else None
        ok = (val == 0) if expect_zero else bool(val)
        msg = fmt(val) if fmt else "%s = %s" % (name, val)
        (PASSES if ok else FAILS).append((gate, name, msg))
        print("  %s [%s] %s" % ("PASS" if ok else "**FAIL**", gate, msg))
    except Exception as e:
        FAILS.append((gate, name, "SQL ERROR: %s" % str(e)[:120]))
        print("  **FAIL** [%s] %s -> %s" % (gate, name, str(e)[:120]))
    finally:
        c.close()


def show(gate, name, sql):
    c = _conf.connect()
    cur = c.cursor()
    cur.execute(sql)
    r = cur.fetchone()
    print("  INFO  [%s] %s -> %s" % (gate, name, r))
    c.close()
    return r


def main():
    print("=" * 78)
    print("BONE UAT 演示数据校验门禁 · tenant=%d" % TENANT)
    print("=" * 78)

    # ---------- G1 参照完整性 ----------
    print("\n[G1] 参照完整性（零孤儿外键）")
    ri = [
        ("t_order.customer_id -> mdm_record",
         "SELECT COUNT(*) FROM t_order o WHERE o.tenant_id=%d AND o.customer_id NOT IN (SELECT id FROM mdm_record)" % TENANT),
        ("t_order_item.order_id -> t_order",
         "SELECT COUNT(*) FROM t_order_item i WHERE i.order_id NOT IN (SELECT id FROM t_order WHERE tenant_id=%d)" % TENANT),
        ("t_order_item.product_id -> mdm_record",
         "SELECT COUNT(*) FROM t_order_item i WHERE i.order_id IN (SELECT id FROM t_order WHERE tenant_id=%d) AND i.product_id NOT IN (SELECT id FROM mdm_record)" % TENANT),
        ("mdm_record.mdm_entity_id -> mdm_entity",
         "SELECT COUNT(*) FROM mdm_record WHERE tenant_id=%d AND mdm_entity_id NOT IN (SELECT id FROM mdm_entity)" % TENANT),
        ("mdm_field.master_data_entity_id -> mdm_entity",
         "SELECT COUNT(*) FROM mdm_field WHERE tenant_id=%d AND master_data_entity_id NOT IN (SELECT id FROM mdm_entity)" % TENANT),
        ("iam_account.dept_id -> iam_dept",
         "SELECT COUNT(*) FROM iam_account WHERE tenant_id=%d AND dept_id IS NOT NULL AND dept_id NOT IN (SELECT id FROM iam_dept WHERE tenant_id=%d)" % (TENANT, TENANT)),
        ("iam_dept.parent_id -> iam_dept",
         "SELECT COUNT(*) FROM iam_dept WHERE tenant_id=%d AND parent_id IS NOT NULL AND parent_id NOT IN (SELECT id FROM iam_dept WHERE tenant_id=%d)" % (TENANT, TENANT)),
        ("iam_account_role 两端",
         "SELECT COUNT(*) FROM iam_account_role ar WHERE ar.tenant_id=%d AND (ar.account_id NOT IN (SELECT id FROM iam_account) OR ar.role_id NOT IN (SELECT id FROM iam_role))" % TENANT),
        ("iam_role_permission 两端",
         "SELECT COUNT(*) FROM iam_role_permission rp WHERE rp.role_id IN (SELECT id FROM iam_role WHERE tenant_id=%d) AND (rp.role_id NOT IN (SELECT id FROM iam_role) OR rp.permission_id NOT IN (SELECT id FROM iam_permission))" % TENANT),
        ("bp_shipment.order_id -> t_order",
         "SELECT COUNT(*) FROM bp_shipment WHERE tenant_id=%d AND order_id NOT IN (SELECT id FROM t_order WHERE tenant_id=%d)" % (TENANT, TENANT)),
        ("bp_shipment_trace.shipment_id -> bp_shipment",
         "SELECT COUNT(*) FROM bp_shipment_trace WHERE tenant_id=%d AND shipment_id NOT IN (SELECT id FROM bp_shipment)" % TENANT),
        ("bp_payment.order_id -> t_order",
         "SELECT COUNT(*) FROM bp_payment WHERE tenant_id=%d AND order_id NOT IN (SELECT id FROM t_order WHERE tenant_id=%d)" % (TENANT, TENANT)),
        ("bp_inventory.product_id -> mdm_record",
         "SELECT COUNT(*) FROM bp_inventory WHERE tenant_id=%d AND product_id NOT IN (SELECT id FROM mdm_record)" % TENANT),
        ("bp_channel_product.product_id -> mdm_record",
         "SELECT COUNT(*) FROM bp_channel_product WHERE tenant_id=%d AND product_id NOT IN (SELECT id FROM mdm_record)" % TENANT),
        ("bp_channel_product.channel_code -> bp_channel",
         "SELECT COUNT(*) FROM bp_channel_product WHERE tenant_id=%d AND channel_code NOT IN (SELECT channel_code FROM bp_channel WHERE tenant_id=%d)" % (TENANT, TENANT)),
        ("wf_task.instance_id -> wf_instance",
         "SELECT COUNT(*) FROM wf_task WHERE tenant_id=%d AND instance_id NOT IN (SELECT id FROM wf_instance)" % TENANT),
        ("wf_task.assignee_id -> iam_account",
         "SELECT COUNT(*) FROM wf_task WHERE tenant_id=%d AND assignee_id NOT IN (SELECT id FROM iam_account)" % TENANT),
        ("wf_instance.started_by -> iam_account",
         "SELECT COUNT(*) FROM wf_instance WHERE tenant_id=%d AND started_by NOT IN (SELECT id FROM iam_account)" % TENANT),
        ("meta_field.entity_id -> meta_entity",
         "SELECT COUNT(*) FROM meta_field WHERE tenant_id=%d AND entity_id NOT IN (SELECT id FROM meta_entity)" % TENANT),
        ("int_flow_node.flow_id -> int_flow",
         "SELECT COUNT(*) FROM int_flow_node WHERE tenant_id=%d AND flow_id NOT IN (SELECT id FROM int_flow)" % TENANT),
        ("int_execution_log.flow_id -> int_flow",
         "SELECT COUNT(*) FROM int_execution_log WHERE tenant_id=%d AND flow_id NOT IN (SELECT id FROM int_flow)" % TENANT),
        ("t_claim_item.claim_id -> t_claim_case",
         "SELECT COUNT(*) FROM t_claim_item WHERE tenant_id=%d AND claim_id NOT IN (SELECT id FROM t_claim_case)" % TENANT),
        ("t_claim_evidence.claim_id -> t_claim_case",
         "SELECT COUNT(*) FROM t_claim_evidence WHERE tenant_id=%d AND claim_id NOT IN (SELECT id FROM t_claim_case)" % TENANT),
        ("settle_order.claim_id -> t_claim_case",
         "SELECT COUNT(*) FROM settle_order WHERE tenant_id=%d AND claim_id NOT IN (SELECT id FROM t_claim_case)" % TENANT),
        ("t_quota_transaction.quota_account_id -> t_quota_account",
         "SELECT COUNT(*) FROM t_quota_transaction WHERE tenant_id=%d AND quota_account_id NOT IN (SELECT id FROM t_quota_account)" % TENANT),
        ("settle_funding_event.settlement_id -> settle_order",
         "SELECT COUNT(*) FROM settle_funding_event WHERE tenant_id=%d AND settlement_id NOT IN (SELECT id FROM settle_order)" % TENANT),
        ("appeal_record.claim_id -> t_claim_case",
         "SELECT COUNT(*) FROM appeal_record WHERE tenant_id=%d AND claim_id NOT IN (SELECT id FROM t_claim_case)" % TENANT),
        ("mdm_steward.account_id -> iam_account",
         "SELECT COUNT(*) FROM mdm_steward WHERE tenant_id=%d AND account_id NOT IN (SELECT id FROM iam_account)" % TENANT),
        ("mdm_quality_issue.record_id -> mdm_record",
         "SELECT COUNT(*) FROM mdm_quality_issue WHERE tenant_id=%d AND record_id IS NOT NULL AND record_id NOT IN (SELECT id FROM mdm_record)" % TENANT),
        ("created_by 血缘（t_order）",
         "SELECT COUNT(*) FROM t_order WHERE tenant_id=%d AND created_by NOT IN (SELECT id FROM iam_account)" % TENANT),
    ]
    for n, s in ri:
        check("G1-RI", n, s)

    # ---------- G2 金额守恒 ----------
    print("\n[G2] 金额守恒（三口径 / 结算 / 额度）")
    check("G2-AMT", "订单总额 = Σ明细小计 + 运费 − 优惠",
          """SELECT COUNT(*) FROM (
             SELECT o.id, o.total_amount,
                    IFNULL((SELECT SUM(i.subtotal) FROM t_order_item i WHERE i.order_id=o.id),0) s,
                    IFNULL(o.freight_amount,0) f, IFNULL(o.discount_amount,0) d
             FROM t_order o WHERE o.tenant_id=%d) x
             WHERE ABS(x.total_amount - (x.s + x.f - x.d)) > 0.01""" % TENANT)
    check("G2-AMT", "明细小计 = 单价 × 数量",
          """SELECT COUNT(*) FROM t_order_item i JOIN t_order o ON o.id=i.order_id
             WHERE o.tenant_id=%d AND ABS(i.subtotal - i.unit_price*i.quantity) > 0.01""" % TENANT)
    check("G2-AMT", "支付金额 = 订单总额（已支付单）",
          """SELECT COUNT(*) FROM bp_payment p JOIN t_order o ON o.id=p.order_id
             WHERE p.tenant_id=%d AND p.status='SUCCESS' AND ABS(p.amount - o.total_amount) > 0.01""" % TENANT)
    check("G2-AMT", "结算金额 = 案件核定金额",
          """SELECT COUNT(*) FROM settle_order s JOIN t_claim_case c ON c.id=s.claim_id
             WHERE s.tenant_id=%d AND ABS(s.amount - c.approved_amount) > 0.01""" % TENANT)
    check("G2-AMT", "额度：已索赔 + 冻结 ≤ 初始额度",
          """SELECT COUNT(*) FROM t_quota_account WHERE tenant_id=%d
             AND (quota_claimed + quota_frozen) > quota_initial + 0.01""" % TENANT)
    check("G2-AMT", "额度流水：after = before − amount（OUT）",
          """SELECT COUNT(*) FROM t_quota_transaction WHERE tenant_id=%d AND direction='OUT'
             AND ABS(after_amount - (before_amount - amount)) > 0.01""" % TENANT)
    check("G2-AMT", "理赔明细：有效金额 ≤ 发生金额",
          """SELECT COUNT(*) FROM t_claim_item WHERE tenant_id=%d AND valid_amount > amount + 0.01""" % TENANT)

    # ---------- G3 职责分离 SoD ----------
    print("\n[G3] 职责分离 SoD（审批人 ≠ 发起人 / 提交人 ≠ 批准人）")
    check("G3-SOD", "wf_task 审批人 ≠ 流程发起人",
          """SELECT COUNT(*) FROM wf_task t JOIN wf_instance i ON i.id=t.instance_id
             WHERE t.tenant_id=%d AND t.assignee_id = i.started_by""" % TENANT)
    check("G3-SOD", "mdm_entity 提交人 ≠ 批准人",
          """SELECT COUNT(*) FROM mdm_entity WHERE tenant_id=%d
             AND submitted_by IS NOT NULL AND approved_by IS NOT NULL AND submitted_by = approved_by""" % TENANT)
    check("G3-SOD", "大额结算（≥5000）必须有第一复核人",
          """SELECT COUNT(*) FROM settle_order WHERE tenant_id=%d AND amount >= 5000
             AND (review_first_approver IS NULL OR review_first_approver='')""" % TENANT)
    check("G3-SOD", "终态理赔案件必须有初审人",
          """SELECT COUNT(*) FROM t_claim_case WHERE tenant_id=%d AND status='FINISHED'
             AND (first_reviewer_id IS NULL OR first_reviewer_id='')""" % TENANT)

    # ---------- G4 状态取值（零幽灵状态） ----------
    print("\n[G4] 状态取值 ∈ 代码枚举（零幽灵状态）")
    enums = [
        ("t_order.status", ["CREATED", "PAID", "SHIPPED", "DELIVERED", "CANCELLED"]),
        ("bp_shipment.status", ["CREATED", "SHIPPED", "IN_TRANSIT", "SIGNED", "FAILED"]),
        ("bp_payment.status", ["PENDING", "PAYING", "SUCCESS", "FAILED"]),
        ("mdm_record.status", ["DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED", "SUPERSEDED", "ARCHIVED"]),
        ("mdm_entity.status", ["DRAFT", "PENDING_REVIEW", "APPROVED", "PUBLISHED", "DISABLED", "RETIRED"]),
        ("wf_instance.phase", ["RUNNING", "APPROVED", "REJECTED", "WITHDRAWN", "SUSPENDED"]),
        ("wf_task.status", ["PENDING", "APPROVED", "REJECTED", "TRANSFERRED", "CANCELLED"]),
        ("settle_order.funding_stage", ["SOFT_RESERVED", "HARD_RESERVED", "REVIEW_FREEZE", "PAID", "RELEASED", "FAILED", "REFUNDED"]),
        ("appeal_record.status", ["SUBMITTED", "DECIDED"]),
    ]
    for col, allowed in enums:
        t, c = col.split(".")
        inlist = ",".join("'%s'" % x for x in allowed)
        check("G4-ENUM", col,
              "SELECT COUNT(*) FROM `%s` WHERE tenant_id=%d AND `%s` NOT IN (%s)" % (t, TENANT, c, inlist))
    CLAIM_ST = ["DRAFT", "ACCEPTED", "PENDING_PRE_EXAM", "PRE_EXAMINING", "AUTO_PRE_EXAMINING",
                "PRE_EXAMINED", "EVIDENCE_SUBMITTING", "PENDING_ADJUST", "ADJUSTING", "ADJUSTED",
                "REVIEWING", "CLOSED_REJECTED", "FINISHED", "APPEALING"]
    check("G4-ENUM", "t_claim_case.status",
          "SELECT COUNT(*) FROM t_claim_case WHERE tenant_id=%d AND status NOT IN (%s)"
          % (TENANT, ",".join("'%s'" % x for x in CLAIM_ST)))

    # ---------- G5 计数下限 ----------
    print("\n[G5] 体量下限")
    mins = [("iam_account", 200), ("iam_dept", 55), ("mdm_record", 2400), ("t_order", 2000),
            ("t_order_item", 4500), ("bp_channel", 6), ("bp_shipment_trace", 4000),
            ("wf_instance", 150), ("t_claim_case", 150), ("int_execution_log", 800)]
    for t, m in mins:
        has_tenant = t not in ("t_order_item",)
        w = "WHERE tenant_id=%d" % TENANT if has_tenant else ""
        check("G5-COUNT", "%s ≥ %d" % (t, m),
              "SELECT CASE WHEN COUNT(*) >= %d THEN 0 ELSE 1 END FROM `%s` %s" % (m, t, w))
    show("G5-COUNT", "各域主数据条数",
         """SELECT CONCAT_WS('  ', CONCAT('客户=',SUM(domain='CUSTOMER')), CONCAT('供应商=',SUM(domain='SUPPLIER')),
                            CONCAT('物料=',SUM(domain='MATERIAL')), CONCAT('商品=',SUM(domain='PRODUCT')),
                            CONCAT('项目=',SUM(domain='PROJECT')), CONCAT('员工=',SUM(domain='EMPLOYEE')),
                            CONCAT('资产=',SUM(domain='ASSET')), CONCAT('买家=',SUM(domain='CHANNEL_BUYER')))
            FROM (SELECT e.domain_code domain FROM mdm_record r JOIN mdm_entity e ON e.id=r.mdm_entity_id
                  WHERE r.tenant_id=%d) z""" % TENANT)

    # ---------- G6 时间轴自洽 ----------
    print("\n[G6] 时间轴自洽（数据老化与状态机单调）")
    check("G6-TIME", "t_order.created_at <= updated_at",
          "SELECT COUNT(*) FROM t_order WHERE tenant_id=%d AND created_at > updated_at" % TENANT)
    check("G6-TIME", "已支付订单 paid_time >= created_at",
          "SELECT COUNT(*) FROM t_order WHERE tenant_id=%d AND paid_time IS NOT NULL AND paid_time < created_at" % TENANT)
    check("G6-TIME", "发货 signed_at >= shipped_at",
          "SELECT COUNT(*) FROM bp_shipment WHERE tenant_id=%d AND signed_at IS NOT NULL AND signed_at < shipped_at" % TENANT)
    check("G6-TIME", "支付 paid_at >= 订单创建",
          """SELECT COUNT(*) FROM bp_payment p JOIN t_order o ON o.id=p.order_id
             WHERE p.tenant_id=%d AND p.paid_at IS NOT NULL AND p.paid_at < o.created_at""" % TENANT)
    check("G6-TIME", "终态流程 finished_at >= started_at",
          """SELECT COUNT(*) FROM wf_instance WHERE tenant_id=%d AND finished_at IS NOT NULL
             AND finished_at < started_at""" % TENANT)
    check("G6-TIME", "理赔结案 closed_at >= created_at",
          "SELECT COUNT(*) FROM t_claim_case WHERE tenant_id=%d AND closed_at IS NOT NULL AND closed_at < created_at" % TENANT)
    check("G6-TIME", "主数据提交早于审批",
          """SELECT COUNT(*) FROM mdm_entity WHERE tenant_id=%d AND submitted_at IS NOT NULL
             AND approved_at IS NOT NULL AND approved_at < submitted_at""" % TENANT)
    check("G6-TIME", "created_at 落在近 365 天窗口",
          "SELECT COUNT(*) FROM t_order WHERE tenant_id=%d AND created_at > NOW() OR created_at < DATE_SUB(NOW(), INTERVAL 365 DAY)" % TENANT)

    # ---------- G7 命名可读性（拒绝机器噪声） ----------
    print("\n[G7] 拟真可读（拒绝 UUID/随机串当显示名）")
    check("G7-NAME", "mdm_record.display_name 不含 REC_ 噪声",
          "SELECT COUNT(*) FROM mdm_record WHERE tenant_id=%d AND (display_name LIKE 'REC\\_%%' OR display_name REGEXP '^[0-9]+$')" % TENANT)
    check("G7-NAME", "主数据均有业务编码",
          "SELECT COUNT(*) FROM mdm_record WHERE tenant_id=%d AND (record_code IS NULL OR record_code='')" % TENANT)
    check("G7-NAME", "订单均有业务单号",
          "SELECT COUNT(*) FROM t_order WHERE tenant_id=%d AND (order_no IS NULL OR order_no='')" % TENANT)
    check("G7-NAME", "账号均有真实姓名",
          "SELECT COUNT(*) FROM iam_account WHERE tenant_id=%d AND (real_name IS NULL OR real_name='')" % TENANT)
    check("G7-NAME", "账号名不含 e2e/qa/uitest 残留",
          "SELECT COUNT(*) FROM iam_account WHERE username REGEXP '(?i)(e2e|qa_|uitest|probe|uivr)'")
    check("G7-NAME", "理赔域已假名化（无明文身份证/姓名列）",
          "SELECT COUNT(*) FROM t_claim_case WHERE tenant_id=%d AND insured_id_hash NOT REGEXP '^INS-HASH-[0-9A-F]{16}$'" % TENANT)

    # ---------- G8 黄金动线串联 ----------
    print("\n[G8] 黄金动线串联（一条数据贯穿全模块）")
    gold = [
        ("客户主数据 300001", "SELECT COUNT(*) FROM mdm_record WHERE id=300001 AND display_name='上海瀚海科技有限公司' AND status='PUBLISHED'", 1),
        ("商品主数据 303001", "SELECT COUNT(*) FROM mdm_record WHERE id=303001 AND display_name='鼎晖智能扫地机器人 X1'", 1),
        ("黄金订单 500001", "SELECT COUNT(*) FROM t_order WHERE id=500001 AND order_no='SO-2026-000001' AND customer_id=300001 AND channel_code='TAOBAO' AND total_amount=2999.00 AND status='DELIVERED'", 1),
        ("订单明细绑定商品 303001", "SELECT COUNT(*) FROM t_order_item WHERE order_id=500001 AND product_id=303001 AND subtotal=2999.00", 1),
        ("淘宝渠道已上架该商品", "SELECT COUNT(*) FROM bp_channel_product WHERE channel_code='TAOBAO' AND product_id=303001 AND listing_price=2999.00 AND listing_status='ON_SALE'", 1),
        ("该商品有库存记录", "SELECT COUNT(*) FROM bp_inventory WHERE product_id=303001", 1),
        ("发货单已签收", "SELECT COUNT(*) FROM bp_shipment WHERE order_id=500001 AND status='SIGNED'", 1),
        ("物流轨迹 ≥4 段", "SELECT CASE WHEN COUNT(*)>=4 THEN 1 ELSE 0 END FROM bp_shipment_trace WHERE shipment_id IN (SELECT id FROM bp_shipment WHERE order_id=500001)", 1),
        ("支付成功且金额一致", "SELECT COUNT(*) FROM bp_payment WHERE order_id=500001 AND status='SUCCESS' AND amount=2999.00", 1),
        ("集成流已消费该订单", "SELECT COUNT(*) FROM int_execution_log WHERE tenant_id=%d AND JSON_EXTRACT(input_data,'$.orderNo')='SO-2026-000001'" % TENANT, 1),
        ("主数据审批在途（李静发起→王芳待办）",
         "SELECT COUNT(*) FROM wf_instance i JOIN wf_task t ON t.instance_id=i.id WHERE i.id=610001 AND i.entity_code='CUSTOMER' AND i.record_id=300001 AND i.phase='RUNNING' AND t.status='PENDING'", 1),
        ("待办指派给审批人王芳",
         "SELECT COUNT(*) FROM wf_task t JOIN iam_account a ON a.id=t.assignee_id WHERE t.instance_id=610001 AND a.real_name='王芳'", 1),
        ("王芳有站内待办消息",
         "SELECT COUNT(*) FROM ntf_message m JOIN iam_account a ON a.id=m.user_id WHERE a.real_name='王芳'", 1),
        ("CEO 有高管看板",
         "SELECT COUNT(*) FROM cnsl_dashboard_widget w JOIN iam_account a ON a.id=w.user_id WHERE a.real_name='张伟' AND w.is_enabled=1", 1),
        ("理赔案件 930001 存在", "SELECT COUNT(*) FROM t_claim_case WHERE id=930001 AND tenant_id=%d" % TENANT, 1),
        ("额度账户 933001 存在且守恒", "SELECT COUNT(*) FROM t_quota_account WHERE id=933001 AND quota_claimed+quota_frozen <= quota_initial", 1),
        ("建模产物被代码生成引用",
         "SELECT COUNT(*) FROM gen_table_metadata WHERE tenant_id=%d AND original_table_name='t_order'" % TENANT, 1),
        ("主数据实体已绑定审批流", "SELECT COUNT(*) FROM wf_binding WHERE tenant_id=%d AND entity_code='CUSTOMER'" % TENANT, 1),
    ]
    for n, s, exp in gold:
        c = _conf.connect()
        cur = c.cursor()
        try:
            cur.execute(s)
            v = cur.fetchone()[0]
            ok = (v >= exp)
            print("  %s [G8-GOLDEN] %s -> %s" % ("PASS" if ok else "**FAIL**", n, v))
            (PASSES if ok else FAILS).append(("G8-GOLDEN", n, str(v)))
        except Exception as e:
            FAILS.append(("G8-GOLDEN", n, str(e)[:100]))
            print("  **FAIL** [G8-GOLDEN] %s -> %s" % (n, str(e)[:100]))
        finally:
            c.close()

    # ---------- 汇总 ----------
    print("\n" + "=" * 78)
    print("门禁结果：PASS %d 项，FAIL %d 项" % (len(PASSES), len(FAILS)))
    if FAILS:
        print("\n失败明细：")
        for g, n, m in FAILS:
            print("   [%s] %s -> %s" % (g, n, m))
    print("=" * 78)
    if "--json" in sys.argv:
        p = sys.argv[sys.argv.index("--json") + 1]
        os.makedirs(os.path.dirname(p), exist_ok=True)
        json.dump({"pass": len(PASSES), "fail": len(FAILS),
                   "fails": [{"gate": g, "name": n, "msg": m} for g, n, m in FAILS]},
                  open(p, "w"), ensure_ascii=False, indent=1)
        print("报告 ->", p)
    sys.exit(1 if FAILS else 0)


if __name__ == "__main__":
    main()
