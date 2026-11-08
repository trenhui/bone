-- synap-claim 第八批 DDL：后督抽检（§7.6 / §12.2.1 · 质检三件套之一）
-- 字段真源：《工程详细设计（落地版）》§9.5 claim_inspection（结论字段）+《待架构评审代码整改清单》事项 3（抽检单号/维度/状态机）
--
-- 与 §9.5 的口径关系（刻意差异，已在整改清单登记）：
--   §9.5 的 claim_inspection 定义为 **append-only 结论台账**（只 INSERT，result 必填）。
--   本表在其结论字段（claim_id / sample_stratum / inspector_id / inspect_type / diff_type /
--   diff_detail / result / action_taken / target_status）之上，补足抽检**工作流**所需的三列：
--     inspection_no（抽检单号，对外可引用键）、dimension（抽检维度）、status（待抽检/抽检中/已结论）。
--   一期一张表承载「任务 + 结论」，理由：抽检单在结论落库前没有任何内容可写，拆两张表会让 #13 变成空操作端点。
--   不可篡改的约束改由**聚合 + DB 双保险**保证（结论一旦 CONCLUDED，聚合拒绝任何再次改写）。

CREATE TABLE t_claim_inspection (
  id             BIGINT        NOT NULL COMMENT '抽检ID(分布式ID)',
  inspection_no  VARCHAR(64)   NOT NULL COMMENT '抽检单号(对外引用键, 租户内唯一)',
  claim_id       BIGINT        NOT NULL COMMENT '案件ID(租户内唯一, 一期一案一检)',
  claim_no       VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '案件号(冗余展示)',
  dimension      VARCHAR(32)   NOT NULL COMMENT '抽检维度: AMOUNT/DOCUMENT/MEDICAL/PROCESS',
  sample_stratum VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '分层抽样标识(金额段×渠道, §7.6; 医院等级待保单快照补列)',
  inspect_type   VARCHAR(16)   NOT NULL COMMENT 'AUTO 自动抽检 / MANUAL 人工抽检',
  status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/CONCLUDED',
  inspector_id   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '质检人(领取后写入; 结论人)',
  inspected_at   DATETIME(3)   DEFAULT NULL COMMENT '结论时间',
  result         VARCHAR(16)   NOT NULL DEFAULT '' COMMENT 'PASS/DIFF(未结论为空)',
  diff_type      VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT 'ADJUST_MISMATCH/RULE_MISS/AI_MISJUDGE/QUOTA_ABNORMAL/NONE',
  diff_detail    JSON          DEFAULT NULL COMMENT '差异明细(受控结构: 字段→本方值/复核值)',
  action_taken   VARCHAR(32)   NOT NULL DEFAULT 'NONE' COMMENT '处置: RECALCULATE/REVIEW/REFUND/CLOSE/NONE',
  target_status  VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '处置目标状态(§6.2; 一期仅登记, 回流执行属 V2)',
  tenant_id      BIGINT        NOT NULL COMMENT '租户(安全隔离)',
  created_at     DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_at     DATETIME(3)   NOT NULL COMMENT '更新时间',
  version        BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁(结论不可并发改写)',
  created_by     BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_by     BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_no (tenant_id, inspection_no),
  UNIQUE KEY uk_claim (tenant_id, claim_id),
  KEY idx_status (tenant_id, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '后督抽检(任务+结论)';
