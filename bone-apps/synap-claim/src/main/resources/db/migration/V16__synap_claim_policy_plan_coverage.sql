-- synap-claim 第三批 DDL：保单 / 计划 / 险种 配置面三张主表
-- 字段与约束真源：domain/model/policy/ClaimPolicy、ClaimPlan、ClaimCoverage
-- 通用列（tenant_id / created_by / created_at / updated_by / updated_at / version / deleted）
-- 由 TenantAggregateRoot / SDK 框架注入（与 V2 既有 t_quota_account / t_adj_liability 同约定）。
--
-- 说明：此前三层配置的代码（实体 + 应用服务 + 控制器 + 仓储 SQL）已完整落地，
-- 但本批三张表**从未有任何 CREATE TABLE 迁移**（仅有 t_claim_case.policy_no 等外键列引用），
-- Flyway 受控的生产库不会自动建表 ⇒ 配置面部署即失败。本迁移补齐该缺口。
-- 合并前请在真实 MySQL 上跑一次 Flyway 校验列形态（含 created_by/updated_by/deleted）与 SDK 期望一致。

-- 保单（配置面主数据：被保人身份 + 承保期间 + 规则开关三态）
CREATE TABLE t_claim_policy (
  id                BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  policy_no         VARCHAR(64)  NOT NULL COMMENT '保单号',
  policy_name       VARCHAR(128) DEFAULT '' COMMENT '保单名称',
  insurer_name      VARCHAR(128) DEFAULT '' COMMENT '保险公司',
  branch_name       VARCHAR(128) DEFAULT '' COMMENT '保险分公司',
  insure_name       VARCHAR(128) DEFAULT '' COMMENT '投保公司(被保企业)',
  biz_identity_code VARCHAR(128) DEFAULT '' COMMENT '业务主体码(保司/分公司/投保公司/保单四级匹配)',
  product_type      VARCHAR(64)  DEFAULT '' COMMENT '产品类型(如 OUTPATIENT)',
  effective_start   DATETIME(3)  NOT NULL COMMENT '生效起',
  effective_end     DATETIME(3)  NOT NULL COMMENT '生效止',
  waiting_period    INT          DEFAULT 0 COMMENT '等待期(天)',
  policy_version    VARCHAR(32)  DEFAULT '' COMMENT '保单版本',
  insured_snapshot  VARCHAR(4000) DEFAULT '' COMMENT '被保人清单(假名化ID数组JSON; 不落明文证件)',
  config_status     VARCHAR(16)  NOT NULL DEFAULT 'INACTIVE' COMMENT 'INACTIVE/ACTIVE/PAUSE',
  stale_flag        INT          NOT NULL DEFAULT 0 COMMENT '1=快照过期,拒新报案',
  synced_at         DATETIME(3)  NOT NULL COMMENT '同步时间',
  tenant_id         BIGINT       NOT NULL COMMENT '租户',
  created_by        BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at        DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by        BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at        DATETIME(3)  NOT NULL COMMENT '更新时间',
  version           BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_policy_no (tenant_id, policy_no),
  KEY idx_tenant_status (tenant_id, config_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保单(配置面)';

-- 保障计划（保单下的计划层；状态机 DRAFT → ACTIVE → RETIRED）
CREATE TABLE t_claim_plan (
  id               BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  policy_no        VARCHAR(64)  NOT NULL COMMENT '保单号',
  plan_code        VARCHAR(64)  NOT NULL COMMENT '计划编码(同保单下唯一)',
  plan_name        VARCHAR(128) DEFAULT '' COMMENT '计划名称',
  plan_limit       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '计划额度(0=不限)',
  waiting_period   INT          DEFAULT 0 COMMENT '等待期(天)',
  version_no       VARCHAR(32)  DEFAULT '' COMMENT '计划版本',
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/RETIRED',
  tenant_id        BIGINT       NOT NULL COMMENT '租户',
  created_by       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at       DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by       BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间',
  version          BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan (tenant_id, policy_no, plan_code),
  KEY idx_tenant_status (tenant_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保障计划(配置面)';

-- 险种 / 保障项（计划下的第二层；状态机与计划同语义）
CREATE TABLE t_claim_coverage (
  id               BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  plan_id          BIGINT       NOT NULL COMMENT '计划ID',
  coverage_code    VARCHAR(64)  NOT NULL COMMENT '险种编码(同计划下唯一)',
  coverage_name    VARCHAR(128) DEFAULT '' COMMENT '险种名称',
  coverage_limit   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '险种额度(0=不限)',
  liability_type   VARCHAR(32)  NOT NULL DEFAULT 'REIMBURSEMENT' COMMENT 'REIMBURSEMENT/FIXED_AMOUNT/ALLOWANCE',
  status           VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/RETIRED',
  tenant_id        BIGINT       NOT NULL COMMENT '租户',
  created_by       BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at       DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by       BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at       DATETIME(3)  NOT NULL COMMENT '更新时间',
  version          BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_coverage (tenant_id, plan_id, coverage_code),
  KEY idx_plan_status (tenant_id, plan_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '险种/保障项(配置面)';
