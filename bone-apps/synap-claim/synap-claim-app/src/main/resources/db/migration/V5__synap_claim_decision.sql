-- synap-claim 第五批 DDL：A3 决策执行（决策图一次执行 + 节点轨迹）
-- 字段真源：《工程详细设计（落地版）》§9（claim_decision_execution / claim_decision_trace）
-- 主键列名遵循 bone-metadata-sdk 约定（id，由实体 id 字段推导），业务标识另设独立列。
--
-- 两处对 §9 原稿的修正（原因随列注释登记）：
-- ① active_flag 允许 NULL：原 DDL 用 UNIQUE(claim_id, active_flag) + 0/1 取值，实际会连带限制
--    同案最多 2 行（0 与 1 各一行），无法保留多次执行历史；改 NULL=非活跃后，MySQL 唯一索引
--    只约束非 NULL 值，"每案仅一个活跃执行 + 多条历史"两语义同时成立。
-- ② 执行行非 append-only：需在 RUNNING→SUCCESS/FALLBACK/FAILED 间更新（active_flag 释放、
--    结论落位），故保留 version 乐观锁列与 updated_* 列；轨迹行才是真正的 append-only。

-- 决策图一次执行
CREATE TABLE claim_decision_execution (
  id                BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  trace_id          VARCHAR(64)   NOT NULL COMMENT '追溯ID(一次执行一个; 回放键)',
  claim_id          BIGINT        NOT NULL COMMENT '案件ID',
  graph_version     VARCHAR(32)   NOT NULL COMMENT '决策图版本(拓扑变更须发版, 禁止热改)',
  rule_version      VARCHAR(32)   NOT NULL COMMENT '规则版本快照',
  model_version     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '模型版本(AI节点, 回放与复算必需)',
  prompt_version    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '提示词版本(AI节点)',
  catalog_version   VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '目录版本(核责节点)',
  status            VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FALLBACK/FAILED',
  active_flag       TINYINT(1)    DEFAULT NULL COMMENT '1=活跃执行; 完成后置NULL(唯一约束只约束非NULL)',
  decision_result   VARCHAR(32)   DEFAULT NULL COMMENT 'AUTO_APPROVE/SUGGEST_REJECT/MANUAL_REQUIRED/NEED_MORE_EVIDENCE',
  decision_tier     VARCHAR(16)   DEFAULT NULL COMMENT '内部分档 AUTO/REVIEW/MANUAL(不外露)',
  ai_confidence     DECIMAL(6,4)  DEFAULT NULL COMMENT '有效置信度(AI成功取AI置信度; LLM降级取规则路径置信度)',
  started_at        DATETIME(3)   NOT NULL COMMENT '开始时间',
  finished_at       DATETIME(3)   DEFAULT NULL COMMENT '结束时间',
  duration_ms       INT           DEFAULT NULL COMMENT '耗时',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trace (tenant_id, trace_id),
  UNIQUE KEY uk_claim_active (claim_id, active_flag),
  KEY idx_claim (claim_id, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '决策执行记录';

-- 决策节点轨迹（append-only，不可更新不可删除）
CREATE TABLE claim_decision_trace (
  id                BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  trace_id          VARCHAR(64)   NOT NULL COMMENT '追溯ID(与执行一致; 按此回放全链)',
  claim_id          BIGINT        NOT NULL COMMENT '案件ID',
  execution_id      BIGINT        NOT NULL COMMENT '执行ID(关联 claim_decision_execution.id)',
  node_seq          INT           NOT NULL COMMENT '节点序号(证据→解析→核责→理算→决策)',
  node_type         VARCHAR(16)   NOT NULL COMMENT 'OCR/RULE/LLM/DECISION(对外4类; 内部节点名见 node_name)',
  node_name         VARCHAR(64)   NOT NULL COMMENT '内部节点名 EvidenceLoad/RuleCheck/AiAssess/Adjust/Arbitrate',
  input_summary     VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '输入摘要(≤200字; 禁落原始PII)',
  output            JSON          DEFAULT NULL COMMENT '节点完整输出',
  action_result     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '节点结论(受控短码)',
  duration_ms       INT           NOT NULL DEFAULT 0 COMMENT '节点耗时',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_at        DATETIME(3)   NOT NULL COMMENT '写入时间',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_trace_seq (trace_id, node_seq),
  KEY idx_execution (execution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '决策节点轨迹(append-only)';
