-- ============================================================
-- 0093_drop_claim_plan_liability_menus.sql
--
-- 一句话：把「计划配置」「责任配置」从智能理赔侧栏菜单撤下——计划/险种/责任
--        强依附于具体保单，配置动线统一收敛为 保单管理 →「配置」下钻；
--        侧栏只保留 保单管理(11) 与 额度管理(13) 两个运营配置面入口。
--
-- 背景（为什么撤，而不是继续补齐）
-- ----------------------------------------------------------------
-- 0092 刚为「责任配置」补了菜单（order 14），动机是闭合探针的"页面有路由而菜单无
-- 入口 ⇒ 功能不可达"。但该形态本身是产品倒置：责任挂在计划下、计划挂在保单下，
-- 独立菜单强迫用户"先选菜单、再补保单上下文"，与参考站（pk-tpa-saas 的
-- policyRuleConfig：保单一页式配置）相反。2026-11-01 产品确认：
--   · 保单管理行内已有「配置」按钮（/plan?policyNo= 深链），
--     计划行内还有「责任配置」按钮（/liability?policyNo=&planId= 深链），
--     页面均带保单上下文，独立菜单是第三条冗余入口；
--   · 侧栏出现「计划配置/责任配置」与"配置面一体化"的产品认知冲突。
--
-- 因此本脚本软删（deleted=1，可审计、可回滚）这两条叶子菜单。页面与路由保留
-- （claim-single-nav-probe.mjs 同步改为 hidden 路由豁免菜单全等——hidden 的语义
-- 本就是"不进菜单、从对象列表深链进入"，此前靠"菜单恰好也有"碰巧全等）。
--
-- 口径
-- ----------------------------------------------------------------
--   · 软删而非硬删：iam_menu.deleted=1，与库内 bak_iam_menu_* 备份惯例一致；
--   · 幂等：重复执行 UPDATE 命中 0 行仍通过；
--   · 不动 额度管理(13)：个人额度/公账巡检是跨保单动线，独立入口成立。
--
-- 执行（本地/测试库直接应用；生产数据变更属 L3，由 DBA / CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0093_drop_claim_plan_liability_menus.sql
-- ============================================================

USE bone;

-- 软删「计划配置」与「责任配置」叶子（全租户；幂等）
UPDATE iam_menu
   SET deleted = 1, updated_by = NULL
 WHERE deleted = 0
   AND type = 1
   AND path IN ('/claim#/plan', '/claim#/liability');

-- ─────────────────────────────────────────────────────────────
-- 验收
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS sp_verify_0093;
DELIMITER $$
CREATE PROCEDURE sp_verify_0093()
BEGIN
    DECLARE v_leaf  INT DEFAULT 0;
    DECLARE v_left  INT DEFAULT 0;
    DECLARE v_alive INT DEFAULT 0;

    -- ① 平台/默认租户(0,1001)各恰好 12 条叶子：1..11 作业链路 + 13 额度管理
    SELECT COUNT(*) INTO v_leaf FROM iam_menu m
      JOIN (SELECT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d ON d.tenant_id = m.tenant_id
     WHERE m.deleted = 0 AND m.type = 1 AND m.path LIKE '/claim#/%';
    IF v_leaf <> 2 * 12 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0093 校验失败：平台/默认租户的理赔叶子菜单不是 12 条';
    END IF;

    -- ② 软删必须生效：不许残留 deleted=0 的 plan/liability 叶子
    SELECT COUNT(*) INTO v_alive FROM iam_menu
     WHERE deleted = 0 AND type = 1
       AND path IN ('/claim#/plan', '/claim#/liability');
    IF v_alive > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0093 校验失败：计划配置/责任配置菜单未被软删';
    END IF;

    -- ③ 保单管理与额度管理两个入口必须幸存
    SELECT COUNT(*) INTO v_left FROM (SELECT tenant_id FROM iam_menu
             WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
               AND tenant_id IN (0, 1001)) d
     WHERE EXISTS (SELECT 1 FROM iam_menu m
                    WHERE m.tenant_id = d.tenant_id AND m.deleted = 0
                      AND m.path = '/claim#/policy')
       AND EXISTS (SELECT 1 FROM iam_menu m
                    WHERE m.tenant_id = d.tenant_id AND m.deleted = 0
                      AND m.path = '/claim#/quota');
    IF v_left <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0093 校验失败：保单管理或额度管理入口缺失';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0093();
DROP PROCEDURE IF EXISTS sp_verify_0093;
