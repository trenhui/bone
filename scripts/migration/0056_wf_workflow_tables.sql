-- ============================================================
-- 0056_wf_workflow_tables.sql
-- 工作流引擎六表：wf_instance / wf_task / wf_active_instance /
--                wf_status_outbox / wf_process_def / wf_binding
--
-- 为什么：
--   bone-workflow-engine 阶段 1 落地后，引擎侧仍无任何持久化表——实例、待办、
--   「一单据一活动实例」原子闸门、状态投影发件箱、流程定义与实体绑定全部
--   落在测试 H2 里，生产无法启动任何流程。本脚本补齐生产 DDL。
--
-- 模型取舍（与测试 schema bone-workflow-spring-boot-starter/src/test/resources/wf-schema-h2.sql
-- 逐列同构，仅方言差异）：
--   - id 一律 VARCHAR(64)：引擎 id 走平台雪花（PlatformWorkflowIdGenerator →
--     String.valueOf(distributedId)），与平台其余表 BIGINT 不同是刻意分歧——
--     引擎 domain 层以 String 承载 id，不在适配器做数字/字符串往返。
--   - tenant_id BIGINT NOT NULL DEFAULT 0：全仓多租户列惯例（wf_* 不得例外）。
--   - deleted TINYINT(1)：平台软删布尔列惯例；wf_status_outbox.deleted 是
--     软删兜底，物理清理走 purgeDoneStatusIntents（DONE 行按保留期删）。
--   - definition_json 生产为 JSON（平台先例：写入期校验，损坏定义进不了库），
--     测试 H2 为 TEXT——损坏 JSON 种子用例需要能写入非法文本；
--     params_json 生产与测试均为 TEXT：它实际承载自由文本原因（驳回/挂起理由），
--     JSON 类型会拒掉合法写入。
--   - wf_active_instance 以 (tenant_id, entity_code, record_id) 自然主键存在，
--     无 id 列：指针行随实例同事务生灭，「一单据一活动实例」的判据就是插入撞主键
--     （DuplicateKeyException → 适配器 reclaimIfStale 对账后重试或失败）。
--   - 时间列 DATETIME(3)，全部由代码显式写入 now（不依赖 DB 默认值/ON UPDATE——
--     审计时间戳必须由领域决定，DB 触发器式更新会掩盖「忘了设 updated_at」的缺陷）。
--
-- 依据：doc/design/modules/12. 工作流引擎设计.md §5.2（持久化形态）/ §9.2 阶段 2
-- 前置：无（全新表，Expand-only，不触碰任何既有表，可重复执行）。
-- ============================================================

-- ---------- 阶段 1：建表（幂等）----------

CREATE TABLE IF NOT EXISTS wf_instance (
    id                VARCHAR(64)  NOT NULL COMMENT '实例主键（平台分布式ID）',
    tenant_id         BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    entity_code       VARCHAR(128) NOT NULL COMMENT '业务实体编码',
    record_id         VARCHAR(128) NOT NULL COMMENT '业务单据记录ID',
    def_key           VARCHAR(128) NOT NULL COMMENT '流程定义KEY',
    def_version       INT          NOT NULL COMMENT '流程定义版本（启动时快照）',
    current_node_code VARCHAR(128) NOT NULL COMMENT '当前节点编码',
    phase             VARCHAR(32)  NOT NULL COMMENT '实例阶段（RUNNING/APPROVED/REJECTED/WITHDRAWN/TERMINATED）',
    started_by        VARCHAR(64)  NOT NULL COMMENT '发起人',
    started_at        DATETIME(3)  NOT NULL COMMENT '发起时间',
    updated_at        DATETIME(3)  NOT NULL COMMENT '更新时间（对账窗口扫描依据）',
    created_by        VARCHAR(64)  NOT NULL COMMENT '创建人',
    created_at        DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_by        VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    deleted           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    version           INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_wf_instance_record (tenant_id, entity_code, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流实例';

CREATE TABLE IF NOT EXISTS wf_task (
    id          VARCHAR(64)  NOT NULL COMMENT '任务主键',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    instance_id VARCHAR(64)  NOT NULL COMMENT '所属实例',
    node_code   VARCHAR(128) NOT NULL COMMENT '节点编码',
    assignee_id VARCHAR(64)  NOT NULL COMMENT '办理人',
    status      VARCHAR(32)  NOT NULL COMMENT '任务状态（PENDING/APPROVED/REJECTED/CANCELLED）',
    decision    VARCHAR(32)  DEFAULT NULL COMMENT '决策（APPROVE/REJECT）',
    comment     VARCHAR(1024) DEFAULT NULL COMMENT '审批意见（自由文本，可含SQL字样）',
    due_at      DATETIME(3)  DEFAULT NULL COMMENT '截止时间',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间（待办排序依据）',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间',
    decided_at  DATETIME(3)  DEFAULT NULL COMMENT '决策时间',
    created_by  VARCHAR(64)  NOT NULL COMMENT '创建人',
    updated_by  VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    version     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_wf_task_assignee (tenant_id, assignee_id, status, deleted),
    KEY idx_wf_task_instance (tenant_id, instance_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流待办任务';

-- 活动实例指针：以 (tenant_id, entity_code, record_id) 为主键，是「一单据一活动实例」的原子闸门
-- created_at/updated_at/deleted 为 HC-008 平台通则列（指针行随实例同事务生灭，无独立生命周期）
CREATE TABLE IF NOT EXISTS wf_active_instance (
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    entity_code VARCHAR(128) NOT NULL COMMENT '业务实体编码',
    record_id   VARCHAR(128) NOT NULL COMMENT '业务单据记录ID',
    instance_id VARCHAR(64)  NOT NULL COMMENT '活动实例ID',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    PRIMARY KEY (tenant_id, entity_code, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流活动实例指针（一单据一活动实例）';

-- 状态投影发件箱：引擎状态意图先落库，由中继重试落地到业务单据 status（最终一致）
CREATE TABLE IF NOT EXISTS wf_status_outbox (
    id           VARCHAR(64)  NOT NULL COMMENT '意图主键',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID（跨租户轮询时由行内携带）',
    instance_id  VARCHAR(64)  NOT NULL COMMENT '实例ID',
    entity_code  VARCHAR(128) NOT NULL COMMENT '业务实体编码',
    record_id    VARCHAR(128) NOT NULL COMMENT '业务单据记录ID',
    intent       VARCHAR(32)  NOT NULL COMMENT '状态意图（STARTED/APPROVED/REJECTED/WITHDRAWN/TERMINATED）',
    action_code  VARCHAR(64)  DEFAULT NULL COMMENT '动作编码',
    params_json  TEXT         DEFAULT NULL COMMENT '意图参数（JSON或自由文本原因）',
    state        VARCHAR(16)  NOT NULL COMMENT '投递状态（NEW/CLAIMED/DONE/FAILED）',
    retry_count  INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME(3) DEFAULT NULL COMMENT '下次重试时间（指数退避）',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间（轮询顺序+队头守卫依据）',
    updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间（CLAIMED 超时判据）',
    deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    PRIMARY KEY (id),
    KEY idx_outbox_poll (tenant_id, state, next_retry_at),
    KEY idx_outbox_instance (tenant_id, instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流状态投影发件箱';

CREATE TABLE IF NOT EXISTS wf_process_def (
    id              VARCHAR(64)  NOT NULL COMMENT '定义主键',
    tenant_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    def_key         VARCHAR(128) NOT NULL COMMENT '流程定义KEY',
    def_name        VARCHAR(255) NOT NULL COMMENT '流程名称',
    def_version     INT          NOT NULL COMMENT '版本号（同KEY递增）',
    status          VARCHAR(32)  NOT NULL COMMENT '定义状态（DRAFT/PUBLISHED/DISABLED）',
    enabled         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用标记',
    definition_json JSON         NOT NULL COMMENT '流程定义JSON（写入期校验）',
    created_by      VARCHAR(64)  NOT NULL COMMENT '创建人',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_by      VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    updated_at      DATETIME(3)  DEFAULT NULL COMMENT '更新时间',
    deleted         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_wf_def_key (tenant_id, def_key, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流程定义';

-- 业务实体 → 流程定义绑定：entity_code 运营侧解析成 def_key（换流程改配置不发版，设计文档 §5.5）
-- v1 只读（写路径随建模界面排期）；同一实体配了多条生效绑定时查询抛 TooManyResults（fail-fast，
-- 不静默挑一条）——数据质量由运营配置保证，引擎不猜。
CREATE TABLE IF NOT EXISTS wf_binding (
    id           VARCHAR(64)  NOT NULL COMMENT '绑定主键',
    tenant_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    entity_code  VARCHAR(128) NOT NULL COMMENT '业务实体编码',
    def_key      VARCHAR(128) NOT NULL COMMENT '绑定的流程定义KEY',
    start_action VARCHAR(64)  DEFAULT NULL COMMENT '触发动作（为空表示默认入口）',
    enabled      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '启用标记',
    created_by   VARCHAR(64)  NOT NULL COMMENT '创建人',
    created_at   DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_by   VARCHAR(64)  DEFAULT NULL COMMENT '更新人',
    updated_at   DATETIME(3)  DEFAULT NULL COMMENT '更新时间',
    deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    version      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_wf_binding_entity (tenant_id, entity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流实体-流程绑定';

-- ---------- 阶段 2：核对（可选，人工执行）----------
-- SHOW CREATE TABLE wf_instance;
-- SHOW CREATE TABLE wf_task;
-- SHOW CREATE TABLE wf_active_instance;
-- SHOW CREATE TABLE wf_status_outbox;
-- SHOW CREATE TABLE wf_process_def;
-- SHOW CREATE TABLE wf_binding;
