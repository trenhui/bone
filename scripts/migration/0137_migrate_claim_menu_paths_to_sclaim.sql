-- ============================================================
-- 0137_migrate_claim_menu_paths_to_sclaim.sql
--
-- 一句话：把「智能理赔」菜单叶子的 path 从历史 hash 形态 `/claim#/<hash>`
--        统一改为 SynapClaim 规范干净路径 `/sclaim/claim/<hash>`（无 #）。
--
-- 背景（为什么必须做，且必须新增迁移而不是改旧脚本）
-- ----------------------------------------------------------------
-- SynapClaim 命名规范（doc/ai-product/synapClaim/03-contract/SynapClaim-命名规范.md）
-- 规定浏览器 URL 统一 `/sclaim/{module}/{page}`，不保留兼容别名。此前内嵌菜单页沿用
-- qiankun 时代的历史形态 `/claim#/pre-exam`（pathname=/claim + hash=#/pre-exam），
-- 与独立详情页 `/sclaim/claim/detail` 分属两套 URL 体系。本轮把内嵌微应用从 HashRouter
-- 改为 BrowserRouter + basename=/sclaim/claim，浏览器地址栏得到 `/sclaim/claim/pre-exam`；
-- 菜单 path 是侧栏跳转的唯一真源（shell 的 handleMenuClick 直接 navigate(item.path)），
-- 必须同批迁移，否则菜单点击跳到旧路径 → 微应用 basename 剥离失败 → 内容区空白。
--
-- 已发迁移（0064/0078/0087/0092/0102/0136）按 sha256 记账，**不可回改**；改数据一律新迁移。
--
-- 口径
-- ----------------------------------------------------------------
--   · 只改 `type=1`（叶子）且 `path LIKE '/claim#/%'` 且未删除的行；
--   · 目录节点（type=0，path NULL）与其它域（iam/procurement/...）不动；
--   · 纯字符串前缀替换：`/claim#/x` -> `/sclaim/claim/x`，name / order_no / permission / icon
--     一律不变（只换 URL 形态，不动可见性与授权）；
--   · iam_menu 无 (tenant_id, path) 唯一约束，UPDATE 不会撞唯一键；
--   · 幂等：已迁移行不再匹配 `LIKE '/claim#/%'`，重复执行 UPDATE 影响 0 行仍判绿。
--
-- 关联（必须同提交）
-- ----------------------------------------------------------------
--   · bone-claim-app/src/App.tsx：HashRouter -> BrowserRouter basename=/sclaim/claim
--   · bone-claim-app/src/workbench/sclaimRoutes.ts：CLAIM_MENU_BASENAME 常量
--   · bone-shell/src/App.tsx：activeRule / MICRO_APP_BASES / 菜单解析适配无 hash 形态
--   · bone-shell/src/utils/claimMenuGroups.ts：分组按 /sclaim/claim/ 前缀识别
--   · scripts/e2e/ui/claim-single-nav-probe.mjs：断言从 hash 改为 pathname
--
-- 执行（本地/测试库；生产数据变更属 L3，由 DBA/CI 在目标库运行）：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0137_migrate_claim_menu_paths_to_sclaim.sql
-- ============================================================

-- ---------- 阶段 1：基线快照（迁移前行数，写入账本供审计；终态对账不依赖它做「必须 >0」判断，
--            因为幂等重跑时待迁移行已是 0，强行断言 >0 会让重跑判红） ----------
DROP TABLE IF EXISTS tmp_claim_path_migrate_0137;
CREATE TABLE tmp_claim_path_migrate_0137 (
    before_old BIGINT NOT NULL,
    before_new BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_path_migrate_0137 (before_old, before_new)
SELECT
    (SELECT COUNT(*) FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%'),
    (SELECT COUNT(*) FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '/sclaim/claim/%');

-- ---------- 阶段 2：幂等迁移（唯一一次 UPDATE，纯前缀替换，无中间态） ----------
UPDATE iam_menu
   SET path = REPLACE(path, '/claim#/', '/sclaim/claim/')
 WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%';

-- ---------- 阶段 3：清理 ----------
DROP TABLE IF EXISTS tmp_claim_path_migrate_0137;

-- ---------- 阶段 4：终态断言（SIGNAL 防「影响 0 行却 EXIT 0」的假成功） ----------
DROP PROCEDURE IF EXISTS sp_verify_0137;
DELIMITER $$
CREATE PROCEDURE sp_verify_0137()
BEGIN
    DECLARE v_old     INT DEFAULT 0;
    DECLARE v_new     INT DEFAULT 0;
    DECLARE v_malform INT DEFAULT 0;

    -- 旧 hash 路径必须归零（幂等重跑的核心判据）
    SELECT COUNT(*) INTO v_old
      FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '/claim#/%';
    IF v_old <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0137 FAIL: legacy /claim#/ menu paths remain';
    END IF;

    -- 不许残留带 # 的错误形态（防前缀替换写错留下 '/sclaim/claim#/'）
    SELECT COUNT(*) INTO v_malform
      FROM iam_menu WHERE deleted = 0 AND type = 1 AND path LIKE '%#%'
       AND path LIKE '/sclaim/claim%';
    IF v_malform <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0137 FAIL: malformed /sclaim/claim# path found';
    END IF;

    -- 新路径必须存在且形态合法 /sclaim/claim/<page>（非空尾段）
    SELECT COUNT(*) INTO v_new
      FROM iam_menu
     WHERE deleted = 0 AND type = 1
       AND path LIKE '/sclaim/claim/%'
       AND LENGTH(SUBSTRING(path, LENGTH('/sclaim/claim/') + 1)) > 0;
    IF v_new = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0137 FAIL: no migrated /sclaim/claim/<page> menu paths';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0137();
DROP PROCEDURE IF EXISTS sp_verify_0137;
