-- Bone System 模块添加 biz_id 字段迁移脚本
-- 为双 ID 模型添加业务 ID 字段

-- 1. 系统配置表添加 biz_id 字段
ALTER TABLE sys_config ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE sys_config ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 2. 系统日志表添加 biz_id 字段
ALTER TABLE sys_log ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE sys_log ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 3. 系统监控表添加 biz_id 字段
ALTER TABLE sys_monitor ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE sys_monitor ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 4. 告警规则表添加 biz_id 字段
ALTER TABLE sys_alert_rule ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE sys_alert_rule ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 5. 告警事件表添加 biz_id 字段
ALTER TABLE sys_alert_event ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE sys_alert_event ADD UNIQUE INDEX idx_biz_id (biz_id);
