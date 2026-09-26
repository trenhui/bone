-- 编号：0002
-- 目的：扩展实现归属应用（G1）+ 租户码遗留值治理（G2）
-- 依据：doc/design/modules/5a. 扩展管理模块核心场景及用例设计方案.md §3.3 / §7
-- 前置：0001_masterdata_mdm_convergence.sql
-- 回滚：ALTER TABLE exts_extension_impl DROP INDEX idx_exts_ei_app;
--       ALTER TABLE exts_extension_impl DROP COLUMN app_id;
-- 注意：第 3 步把遗留占位值 'DEFAULT' 映射为通配 '*'，使这些实现参与一级租户精确路由；
--       上线前须确认这些行均为兜底/通用实现（与 is_default 语义一致），否则先人工核对。
-- 依据 5a G2：合法值为 '*'（通配）或存在的 iam_tenant.code，'DEFAULT' 不在合法值集内。

-- 1. Expand：归属应用列（可空 = 平台通用插件）
ALTER TABLE exts_extension_impl
    ADD COLUMN app_id BIGINT DEFAULT NULL COMMENT '归属应用ID（bone_application.id，可空=平台通用插件）' AFTER extension_point_id;

ALTER TABLE exts_extension_impl
    ADD INDEX idx_exts_ei_app (app_id);

-- 2. 遗留租户码映射：'DEFAULT' → '*'
UPDATE exts_extension_impl SET tenant_code = '*' WHERE tenant_code = 'DEFAULT';
