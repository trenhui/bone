-- 0062 大额资金双人复核：落库「审核发起人」，让第二道认证有可判定的比较基准
--
-- 背景：settle_order 之前没有记录「这次大额审核是谁发起的」，于是双人复核只能靠请求体里
--       一份 reviewerIds 名单来"证明"——名单由调用方自填，把同一个人的两个马甲写进数组即可通过。
--       REVIEW_FREEZE 也因此没有出口：pay() 直接接受该状态，第二个人从未真正参与。
--
-- 处置：新增 review_first_approver，auditPass 时写入 token userId；复核（REVIEW_FREEZE →
--       HARD_RESERVED）时比对，同人即拒。两次认证分别落一条 settle_funding_event，台账即流水。
--
-- 幂等：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，故先查 information_schema 再决定是否执行，
--       重复跑不会报 Duplicate column name。

SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE settle_order ADD COLUMN review_first_approver VARCHAR(64) DEFAULT NULL COMMENT ''大额复核发起人(token userId)；复核须换人(§10.3)''',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'settle_order'
    AND COLUMN_NAME = 'review_first_approver'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
