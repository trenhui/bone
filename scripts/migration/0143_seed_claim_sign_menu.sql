-- 0143_seed_claim_sign_menu.sql
--
-- 给「智能理赔」目录补 1 条作业面菜单「案件签收」（/sclaim/claim/sign），
-- 与 bone-claim-app/src/routes.tsx 的 visible 路由逐字对齐。
-- 口径：id = 目录 id + 29（27/28 已被 AI 治理/运营看板占用）；order_no = 20（接在 /analytics=19 之后）；
-- permission = claim:claims:read；icon = SafetyCertificateOutlined（已在 shell 白名单）。
-- 幂等：同租户同 path 已存在则跳过。菜单↔路由全等由 claim-single-nav-probe.mjs 把关。

USE bone;

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0143;
CREATE TEMPORARY TABLE tmp_claim_dir_0143 (
    tenant_id BIGINT NOT NULL,
    dir_id    BIGINT NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO tmp_claim_dir_0143 (tenant_id, dir_id)
SELECT tenant_id, MIN(id)
  FROM iam_menu
 WHERE name = '智能理赔' AND path IS NULL AND type = 0 AND deleted = 0
 GROUP BY tenant_id;

INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT d.dir_id + 29, d.tenant_id, '案件签收', d.dir_id, '/sclaim/claim/sign',
       'SafetyCertificateOutlined', 20, 'claim:claims:read', 1, NULL, NULL
  FROM tmp_claim_dir_0143 d
 WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
     WHERE m.tenant_id = d.tenant_id AND m.path = '/sclaim/claim/sign' AND m.deleted = 0
 );

DROP TEMPORARY TABLE IF EXISTS tmp_claim_dir_0143;

-- 终态断言：菜单存在且字段与 routes.tsx 一致。
SELECT COUNT(*) INTO @sign_menu_count
FROM iam_menu m
WHERE m.deleted = 0 AND m.type = 1 AND m.path = '/sclaim/claim/sign'
  AND m.name = '案件签收' AND m.order_no = 20 AND m.permission = 'claim:claims:read'
  AND m.tenant_id IN (0, 1001);

DROP PROCEDURE IF EXISTS sp_verify_0143;
DELIMITER $$
CREATE PROCEDURE sp_verify_0143()
BEGIN
  IF @sign_menu_count < 1 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0143 verify failed: claim sign menu missing or drifted';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0143();
DROP PROCEDURE IF EXISTS sp_verify_0143;
