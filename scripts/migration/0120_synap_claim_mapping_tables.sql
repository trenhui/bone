-- ============================================================
-- 0120_synap_claim_mapping_tables.sql
--
-- 一句话：把多保司主数据映射域**缺位的 7 张表**补进生效通道 scripts/migration/。
--
-- 背景（为什么必须补）
-- ----------------------------------------------------------------
-- 这 7 张表的 DDL 此前只存在于 bone-init.sql（全量新装）与参考态
-- V20__synap_claim_master_data_mapping.sql（db/migration 无 runner，从不自动执行）。
-- 生效通道缺位 ⇒ 按增量升级的存量环境永远建不出这些表，承保主体/主数据映射/
-- 未命中处置等页面首次访问即 500，且缺表不报错、不告警（与 0112 修
-- t_claim_coverage_override 时同款「代码已上线、DDL 没跟上」）。
--
-- 第 8 张表 t_claim_coverage_override 已由 0112 补进生效通道，本脚本**不再声明**
-- 它的列：同一通道重复声明 (表,列) 会被 check-ddl-duplicate-column.py 判红，
-- 也让「谁负责这张表」变得含糊。
--
-- 单一真源
-- ----------------------------------------------------------------
-- 7 段 CREATE TABLE 均由 bone-init.sql 的对应块**逐字提取**（脚本生成、非手抄），
-- 仅把 `CREATE TABLE` 改为 `CREATE TABLE IF NOT EXISTS`。init 与参考态 V20 的
-- 7 段 DDL 已逐字节比对一致；活库列集合/类型/索引亦一致。
-- 幂等用 IF NOT EXISTS：既覆盖「从未建表」的升级环境，也覆盖开发库这类
-- 「表已存在」的环境，重跑不会碰存量数据。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- 不靠「SQL 没报错」判成功：校验 8 张表齐全（含 0112 负责的那张）、
-- uk_active 必含 active_flag（否则同键可存在多条生效映射）、
-- uk_miss 必不含 status（含了则 OPEN/RESOLVED/IGNORED 各占一行后再次未命中撞键、
-- 队列静默停摆）、mapping_miss 必有 resolved_by（V21 处置留痕口径）。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_claim_insurer (
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

CREATE TABLE IF NOT EXISTS t_claim_insurer_code_item (
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

CREATE TABLE IF NOT EXISTS t_claim_code_mapping (
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

CREATE TABLE IF NOT EXISTS t_claim_field_binding (
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

CREATE TABLE IF NOT EXISTS t_claim_std_code_alias (
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

CREATE TABLE IF NOT EXISTS t_claim_std_code_split (
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

CREATE TABLE IF NOT EXISTS t_claim_mapping_miss (
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
  resolved_by         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '处置人(operatorId;系统恢复=SYSTEM)',
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

-- ─────────────────────────────────────────────────────────────
-- 二、终态断言（任一不满足即 SIGNAL 中止，不留半套映射域）
-- ─────────────────────────────────────────────────────────────
DELIMITER $$

DROP PROCEDURE IF EXISTS sp_verify_0120$$
CREATE PROCEDURE sp_verify_0120()
BEGIN
    DECLARE v_n INT DEFAULT 0;

    SELECT COUNT(*) INTO v_n FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (
       't_claim_insurer', 't_claim_insurer_code_item', 't_claim_code_mapping',
       't_claim_field_binding', 't_claim_std_code_alias', 't_claim_std_code_split',
       't_claim_mapping_miss', 't_claim_coverage_override');
    IF v_n <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0120: 映射域 8 张表未齐(检查 0112 是否已执行)';
    END IF;

    SELECT COUNT(*) INTO v_n FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_code_mapping'
       AND INDEX_NAME = 'uk_active' AND COLUMN_NAME = 'active_flag';
    IF v_n <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0120: uk_active 缺 active_flag, 同键唯一生效行不保';
    END IF;

    SELECT COUNT(*) INTO v_n FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_mapping_miss'
       AND INDEX_NAME = 'uk_miss' AND COLUMN_NAME = 'status';
    IF v_n <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0120: uk_miss 误含 status, 未命中队列会撞键停摆';
    END IF;

    SELECT COUNT(*) INTO v_n FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_claim_mapping_miss'
       AND COLUMN_NAME = 'resolved_by';
    IF v_n <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0120: mapping_miss 缺 resolved_by(V21 处置留痕口径)';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0120();
DROP PROCEDURE IF EXISTS sp_verify_0120;
