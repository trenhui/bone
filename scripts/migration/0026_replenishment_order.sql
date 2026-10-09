-- 0026 · 供应链补货单（bone-blueprint）
-- 场景：安全库存预警 → 建补货单 → 提交/审批 → 到货入库（Inventory.receive）
-- API 权限复用 commerce:inventory:read / commerce:inventory:write（不新增权限码）
-- 前端新路由 /commerce#/replenishments；菜单可手工挂「补货管理」或见 bone-init 种子

CREATE TABLE IF NOT EXISTS bp_replenishment_order (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    replenish_no        VARCHAR(32)     NOT NULL COMMENT '补货单号 RP+yyyyMMdd+id',
    product_id          BIGINT          NOT NULL COMMENT '商品ID',
    product_name        VARCHAR(200)    DEFAULT NULL COMMENT '商品名称（冗余）',
    warehouse_code      VARCHAR(32)     NOT NULL DEFAULT 'DEFAULT' COMMENT '仓库编码',
    quantity            INT             NOT NULL COMMENT '计划补货数量',
    suggested_qty       INT             DEFAULT NULL COMMENT '系统建议量（创建时快照）',
    available_snapshot  INT             DEFAULT NULL COMMENT '创建时可用库存快照',
    safety_snapshot     INT             DEFAULT NULL COMMENT '创建时安全库存快照',
    supplier_code       VARCHAR(64)     DEFAULT NULL COMMENT '供应商编码（演示）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/RECEIVED/CANCELLED',
    remark              VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    submitted_at        DATETIME(3)     DEFAULT NULL COMMENT '提交时间',
    approved_at         DATETIME(3)     DEFAULT NULL COMMENT '审批时间',
    received_at         DATETIME(3)     DEFAULT NULL COMMENT '到货入库时间',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_replenish_tenant_no (tenant_id, replenish_no, deleted),
    KEY idx_replenish_tenant_status (tenant_id, status),
    KEY idx_replenish_tenant_product (tenant_id, product_id, warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应链补货单（库存低于安全库存时的补货闭环）';
