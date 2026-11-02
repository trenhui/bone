-- synap-claim 第二十二批 DDL：租户级流程/钩子配置（§6.7 流程配置化 + 「新保司接入≤7天」承载）
-- 字段真源：《工程详细设计（落地版）》§6.7 / §9.5 claim_flow_config
-- 说明：承载流程配置化与「新保司接入≤7天」；config_json 存流程/钩子配置，变更须带 config_version 发版号。

CREATE TABLE t_claim_flow_config (
  id             BIGINT       NOT NULL COMMENT '配置ID(分布式ID)',
  tenant_id      BIGINT       NOT NULL COMMENT '租户',
  flow_key       VARCHAR(64)  NOT NULL COMMENT '流程键(如 CLAIM_LIFECYCLE)',
  config_version INT          NOT NULL DEFAULT 1 COMMENT '配置版本(变更须发版号)',
  config_json    TEXT         COMMENT '流程/钩子配置(JSON)',
  active_flag    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否生效(1生效/0失效)',
  created_at     DATETIME(3)  NOT NULL COMMENT '创建时间',
  created_by     BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at     DATETIME(3)  NOT NULL COMMENT '更新时间',
  updated_by     BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version        BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_flow (tenant_id, flow_key),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户级流程/钩子配置(新保司接入≤7天承载)';
