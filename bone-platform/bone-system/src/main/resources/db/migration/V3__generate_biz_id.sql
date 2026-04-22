-- Bone System 模块生成 biz_id 数据迁移脚本
-- 为现有数据生成 UUID 格式的业务 ID

-- 1. 为系统配置表生成 biz_id
UPDATE sys_config SET biz_id = REPLACE(UUID(), '-', '');

-- 2. 为系统日志表生成 biz_id
UPDATE sys_log SET biz_id = REPLACE(UUID(), '-', '');

-- 3. 为系统监控表生成 biz_id
UPDATE sys_monitor SET biz_id = REPLACE(UUID(), '-', '');

-- 4. 为告警规则表生成 biz_id
UPDATE sys_alert_rule SET biz_id = REPLACE(UUID(), '-', '');

-- 5. 为告警事件表生成 biz_id
UPDATE sys_alert_event SET biz_id = REPLACE(UUID(), '-', '');
