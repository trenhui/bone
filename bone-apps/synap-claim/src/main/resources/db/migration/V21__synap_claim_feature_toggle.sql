-- synap-claim 第二十一批 DDL：租户级特性开关与灰度（§11.4 三态开关）
-- 字段真源：《工程详细设计（落地版）》§11.4
-- 说明：承载新能力的渐进发布与安全灰度（如 AUTO_APPROVE 自动放款按租户开启 / 按比例灰度）。
--       未配置某特性的租户视为「继承全局开关」（fail-closed：默认不开启资金策略）。

CREATE TABLE t_feature_toggle (
  id           BIGINT       NOT NULL COMMENT '开关ID(分布式ID)',
  tenant_id    BIGINT       NOT NULL COMMENT '租户',
  feature_key  VARCHAR(64)  NOT NULL COMMENT '特性键(如 AUTO_APPROVE)',
  state        VARCHAR(16)  NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED/DISABLED',
  rollout_pct  INT          NOT NULL DEFAULT 0 COMMENT '灰度比例(0-100)',
  remark       VARCHAR(255) DEFAULT '' COMMENT '备注',
  created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
  created_by   BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间',
  updated_by   BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version      BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_feature (tenant_id, feature_key),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户级特性开关与灰度';
