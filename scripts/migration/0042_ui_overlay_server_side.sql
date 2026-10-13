-- 0042：界面列投影覆盖服务端化（P2-④ · D2 升级）
--
-- 背景：PRD v0.32 §12.2 D2 定案「MVP 覆盖存 localStorage」，遗留问题是换设备/清缓存即丢失，
-- 且无法跨端同步。业界对标（Jira / 飞书表格）：列偏好是账号级服务端数据。
--
-- expand 阶段：新建 meta_ui_overlay，不影响任何存量表；localStorage 键保留为离线缓存，
-- 前端 hydrate 策略 = 服务端优先 → 本地缓存兜底 → 默认投影（projectColumns 纯函数不变）。
--
-- 契约：唯一键 (tenant_id, user_id, entity_code) 不含 deleted —— 与
-- uk_action_tenant_entity_code 同口径（软删行不占唯一键，reset 后重建不撞键）。

CREATE TABLE IF NOT EXISTS meta_ui_overlay (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增ID（覆盖表为轻量偏好，无需雪花业务语义）',
  tenant_id    BIGINT       NOT NULL COMMENT '租户（与 meta_entity 同链路）',
  user_id      BIGINT       NOT NULL COMMENT '账号ID（CurrentUserProvider；无主体时落 0）',
  entity_code  VARCHAR(128) NOT NULL COMMENT '实体编码（覆盖键第三段）',
  overlay_json JSON         NOT NULL COMMENT 'UiOverlay 序列化（version + overrides + density）',
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '0-在用 1-已删（reset 语义）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_overlay_tenant_user_entity (tenant_id, user_id, entity_code),
  KEY idx_overlay_entity (entity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='界面列投影覆盖（用户级 UI 偏好，服务端化）';

-- 存量数据迁移：无（localStorage 数据在浏览器端，首次登录后由前端按需上传；
-- 不上传则按默认投影渲染，与升级前「从未配置过」等价，无功能损失）。
