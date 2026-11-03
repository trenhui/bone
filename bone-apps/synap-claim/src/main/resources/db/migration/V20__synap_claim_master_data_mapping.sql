-- 多保司主数据映射（设计真源：doc/ai-product/synapClaim/04-design/多保司保单配置面与主数据映射设计.md §6.3）
--
-- 三条与仓内既有建表习惯的**刻意差异**，每条都有 MySQL 8 实测依据（主设计 §6.3）：
--   1) 唯一键不含 deleted：deleted 进唯一键会让同一业务键"只能被软删一次"，
--      第二次停用直接 1062；而"保司改码 → 停用旧映射 → 建新映射"是常态操作。
--      列本身保留（SDK 的 SELECT 默认追加 `deleted = false`，缺列即 1054），只是不参与唯一性。
--   2) 标准基线用哨兵 '*' 而不是 NULL：唯一索引对 NULL 不判重，
--      用 NULL 表达"平台标准基线"会插进多条基线且条条生效，绑定结果变成随机的。
--   3) 映射的"当前有效"由 active_flag='Y' 表达（其余 NULL）：MySQL 唯一索引允许多个 NULL，
--      于是"同键任一时刻仅一条 ACTIVE"由数据库强制，历史行不占用唯一槽位，也不需要物理删除。

-- 承保主体主档：替代 insurer_name 自由文本；分公司用 parent_code 挂在同一张表
CREATE TABLE t_claim_insurer (
  id                 BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  insurer_code       VARCHAR(64)  NOT NULL COMMENT '保司编码(唯一业务键,如 YCBX/PICC)',
  insurer_name       VARCHAR(128) NOT NULL COMMENT '保司全称',
  insurer_short_name VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '保司简称',
  insurer_type       VARCHAR(16)  NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT直保/BROKER经纪/AGENT代理',
  credit_code        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '统一社会信用代码(外部桥锚点)',
  parent_code        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分公司指向法人主体;法人为空',
  status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/TERMINATED',
  tenant_id          BIGINT       NOT NULL COMMENT '租户',
  created_by         BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at         DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by         BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at         DATETIME(3)  NOT NULL COMMENT '更新时间',
  version            BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted            TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记(SDK 读取过滤;不参与唯一键)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_insurer (tenant_id, insurer_code),
  KEY idx_credit (tenant_id, credit_code),
  KEY idx_parent (tenant_id, parent_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '承保主体主档';

-- 保司码表项：只承载"这家保司怎么叫"，不复制标准侧属性
CREATE TABLE t_claim_insurer_code_item (
  id              BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  insurer_code    VARCHAR(64)  NOT NULL COMMENT '保司编码',
  domain          VARCHAR(32)  NOT NULL COMMENT 'HOSPITAL/DISEASE/DRUG/FEE_CATEGORY/CERT_TYPE',
  item_code       VARCHAR(64)  NOT NULL COMMENT '保司侧码值',
  item_name       VARCHAR(256) NOT NULL COMMENT '保司侧名称',
  name_normalized VARCHAR(256) NOT NULL COMMENT '归一化名称(去空格/全半角/括号),供候选匹配',
  extra_json      JSON         DEFAULT NULL COMMENT '保司侧附加属性(等级/规格/剂型)',
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/RETIRED',
  source_batch_no VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '导入批次号',
  tenant_id       BIGINT       NOT NULL COMMENT '租户',
  created_by      BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by      BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
  version         BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记(同码复活走 UPDATE status,不插新行)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_item (tenant_id, insurer_code, domain, item_code),
  KEY idx_norm (tenant_id, insurer_code, domain, name_normalized)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保司码表项';

-- 映射：收单侧"保司码 → 标准码"
CREATE TABLE t_claim_code_mapping (
  id                  BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  insurer_code        VARCHAR(64)   NOT NULL COMMENT '保司编码',
  domain              VARCHAR(32)   NOT NULL COMMENT '主数据域',
  insurer_item_code   VARCHAR(64)   NOT NULL COMMENT '保司侧码值',
  disambiguation_key  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '消歧键(无则空串);须由收单字段确定性算出',
  standard_code       VARCHAR(64)   NOT NULL COMMENT '标准码',
  mapping_type        VARCHAR(16)   NOT NULL COMMENT 'EXACT/SYNONYM/GROUP',
  match_rule          VARCHAR(32)   NOT NULL COMMENT 'CODE_EQUAL/CREDIT_CODE/NAME_NORM/FUZZY/MANUAL/AI_SUGGEST',
  match_score         DECIMAL(5, 2) DEFAULT NULL COMMENT '机器匹配得分;人工为 NULL',
  disambiguation_json JSON          DEFAULT NULL COMMENT '裁决属性原文(键序规范化后与 key 对齐)',
  source              VARCHAR(16)   NOT NULL COMMENT 'MANUAL/IMPORT/SUGGEST_CONFIRMED',
  reason_code         VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '判定理由码',
  status              VARCHAR(16)   NOT NULL COMMENT 'DRAFT/ACTIVE/DISABLED/RETIRED',
  active_flag         CHAR(1)       DEFAULT NULL COMMENT 'ACTIVE=Y,其余 NULL(多 NULL 合法,Y 唯一)',
  effective_from      DATETIME(3)   NOT NULL COMMENT '业务生效起',
  effective_to        DATETIME(3)   NOT NULL DEFAULT '9999-12-31 23:59:59' COMMENT '业务生效止(闭合区间,不用 NULL)',
  superseded_by_id    BIGINT        DEFAULT NULL COMMENT '被哪条新行替代(回放链条)',
  version_no          VARCHAR(32)   NOT NULL COMMENT '发布批次(=release_no)',
  reviewed_by         BIGINT        DEFAULT NULL COMMENT '复核人',
  reviewed_at         DATETIME(3)   DEFAULT NULL COMMENT '复核时间',
  tenant_id           BIGINT        NOT NULL COMMENT '租户',
  created_by          BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at          DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by          BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at          DATETIME(3)   NOT NULL COMMENT '更新时间',
  version             BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted             TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记(映射只关闭不删除,该列恒 0)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_active (tenant_id, insurer_code, domain, insurer_item_code, disambiguation_key, active_flag),
  KEY idx_reverse (tenant_id, insurer_code, domain, standard_code, active_flag),
  KEY idx_std (tenant_id, domain, standard_code),
  KEY idx_release (tenant_id, version_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保司码-标准码映射';

-- 字段 → 选项集绑定（值域型字段；'*' = 平台标准基线）
CREATE TABLE t_claim_field_binding (
  id           BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  field_code   VARCHAR(64) NOT NULL COMMENT '系统业务字段(如 certType/beneficiaryCertType)',
  insurer_code VARCHAR(64) NOT NULL DEFAULT '*' COMMENT '保司编码;*=平台标准基线',
  source_type  VARCHAR(16) NOT NULL COMMENT 'DICT/INSURER_SET/INLINE',
  ref_code     VARCHAR(64) NOT NULL COMMENT '引用编码(选项集编码或内联值集键)',
  version_no   VARCHAR(32) NOT NULL DEFAULT '' COMMENT '发布批次',
  tenant_id    BIGINT      NOT NULL COMMENT '租户',
  created_by   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  created_at   DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_by   BIGINT      DEFAULT NULL COMMENT '修改人ID',
  updated_at   DATETIME(3) NOT NULL COMMENT '更新时间',
  version      BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted      TINYINT     NOT NULL DEFAULT 0 COMMENT '软删标记(不参与唯一键)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_binding (tenant_id, field_code, insurer_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字段-选项集绑定';

-- 标准码变更 1:1（归并 / 更名）
CREATE TABLE t_claim_std_code_alias (
  id             BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  domain         VARCHAR(32) NOT NULL COMMENT '主数据域',
  alias_code     VARCHAR(64) NOT NULL COMMENT '旧码/被合并码',
  canonical_code VARCHAR(64) NOT NULL COMMENT '现行码',
  relation       VARCHAR(16) NOT NULL COMMENT 'MERGE归并/RENAME更名',
  effective_from DATETIME(3) NOT NULL COMMENT '业务生效起',
  effective_to   DATETIME(3) NOT NULL DEFAULT '9999-12-31 23:59:59' COMMENT '业务生效止',
  status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  tenant_id      BIGINT      NOT NULL COMMENT '租户',
  created_by     BIGINT      DEFAULT NULL COMMENT '创建人ID',
  created_at     DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_by     BIGINT      DEFAULT NULL COMMENT '修改人ID',
  updated_at     DATETIME(3) NOT NULL COMMENT '更新时间',
  version        BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '软删标记(不参与唯一键)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_alias (tenant_id, domain, alias_code, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标准码别名(归并/更名)';

-- 标准码变更 1:N（拆分）：无单一目标，单独承载；命中即 fail-closed
CREATE TABLE t_claim_std_code_split (
  id              BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  domain          VARCHAR(32) NOT NULL COMMENT '主数据域',
  parent_code     VARCHAR(64) NOT NULL COMMENT '被拆分码',
  child_code      VARCHAR(64) NOT NULL COMMENT '拆分后码',
  split_rule_json JSON        DEFAULT NULL COMMENT '分流依据(院区/剂型…),供人工裁决参考',
  effective_from  DATETIME(3) NOT NULL COMMENT '业务生效起',
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  tenant_id       BIGINT      NOT NULL COMMENT '租户',
  created_by      BIGINT      DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_by      BIGINT      DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3) NOT NULL COMMENT '更新时间',
  version         BIGINT      NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT     NOT NULL DEFAULT 0 COMMENT '软删标记(不参与唯一键)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_split (tenant_id, domain, parent_code, child_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '标准码拆分(1:N)';

-- 收单未命中队列：一码一行、状态流转（唯一键不含 status，
-- 否则 OPEN/RESOLVED/IGNORED 各占一行后第三次未命中直接撞键，队列静默停摆）
CREATE TABLE t_claim_mapping_miss (
  id                  BIGINT       NOT NULL COMMENT '主键(分布式ID)',
  insurer_code        VARCHAR(64)  NOT NULL COMMENT '保司编码',
  domain              VARCHAR(32)  NOT NULL COMMENT '主数据域',
  raw_code            VARCHAR(64)  NOT NULL COMMENT '未命中的保司侧码值',
  raw_name            VARCHAR(256) NOT NULL DEFAULT '' COMMENT '未命中的保司侧名称',
  name_normalized     VARCHAR(256) NOT NULL DEFAULT '' COMMENT '归一化名称',
  occur_count         INT          NOT NULL DEFAULT 1 COMMENT '累计未命中次数',
  reopen_count        INT          NOT NULL DEFAULT 0 COMMENT '已解决后再次未命中次数(映射失效信号)',
  last_case_no        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '最近一次命中的案件号',
  first_seen_at       DATETIME(3)  NOT NULL COMMENT '首次出现',
  last_seen_at        DATETIME(3)  NOT NULL COMMENT '最近出现',
  status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/RESOLVED/IGNORED',
  ignore_reason       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '忽略理由',
  resolved_mapping_id BIGINT       DEFAULT NULL COMMENT '解决该未命中的映射ID',
  tenant_id           BIGINT       NOT NULL COMMENT '租户',
  created_by          BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at          DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_by          BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间',
  version             BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '软删标记(不参与唯一键)',
  PRIMARY KEY (id),
  UNIQUE KEY uk_miss (tenant_id, insurer_code, domain, raw_code),
  KEY idx_open (tenant_id, status, last_seen_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收单映射未命中队列';
