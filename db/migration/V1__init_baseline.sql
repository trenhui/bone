-- [DRAFT] 待架构师审批 (L3: DDL) —— 占位基线，正式内容需由 bone-init.sql 拆分并评审
-- 本文件仅作为 Flyway 基线占位，确保 migrate 流程可首次跑通。
-- 正式 schema 拆分请参见同目录 README.md「基线拆分」一节，由 DBA + 架构师评审后替换。

SET NAMES utf8mb4;

-- 示例：IAM 租户表（取自 bone-init.sql 的 iam 段，供评审样例；其余模块待补全）
-- CREATE TABLE iam_tenant (
--     id                  BIGINT          NOT NULL COMMENT '租户主键（Snowflake）',
--     name                VARCHAR(200)    NOT NULL COMMENT '租户名称',
--     code                VARCHAR(50)     NOT NULL COMMENT '租户编码',
--     level               TINYINT         NOT NULL DEFAULT 0 COMMENT '租户等级',
--     status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态',
--     admin_email         VARCHAR(200)    NOT NULL COMMENT '管理员邮箱',
--     max_accounts        INT             DEFAULT NULL COMMENT '账号配额上限，NULL=不限制',
--     PRIMARY KEY (id),
--     UNIQUE KEY uk_tenant_code (code)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户';

-- TODO(架构师/DBA): 将 bone-init.sql 全量 DDL 按模块拆分补入 V1 基线，并移除本占位注释。
SELECT 'Flyway V1 baseline placeholder — replace with real DDL from bone-init.sql' AS note;
