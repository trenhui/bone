# ADR-0032: 发布包 — 以模块为单元的变更集、审批与回滚

**状态**：已接受 · 分批落地（2026-09-26，本期仅决策与表设计，实现另立 Change）｜ **关联**：2a 设计 §5.1（UC-MT5）、§7（G4）、ADR-0028、ADR-0031

## 背景与问题

发布是**单实体**动作（`POST /entities/{id}/publish` → `status=1` + `align()` 直接改物理表）：无变更集、无 dry-run、无审批、无快照、无回滚——「一次点击 = 一次生产库 DDL」。持有 `metadata:publish` 的账号一步改生产（2a §G4）。对标业界：Dataverse 以 **Solution** 为部署单元；Salesforce 以 **Change Set** 交付并要求部署审批；Hasura 在 metadata 写入前做 **consistency check**。

## 决策

1. **发布以「发布包」为唯一生产入口**：`meta_release_package`（状态机 `DRAFT → PENDING_APPROVAL → APPROVED → PUBLISHING → PUBLISHED / REJECTED / ROLLBACK_DONE`）+ `meta_release_item`（包内实体级变更：字段新增/类型变更/唯一约束等，含 DDL 预览）。单实体旧 `publish` API 隐式建单元素包并标记 deprecated。
2. **同包禁止自审（SoD）**：`created_by != approved_by`，由权限码 `metadata:release:create` / `metadata:release:approve` 分离（G5 拆分的后续码，本期不落）。
3. **发布前三道闸**（在 `PENDING_APPROVAL → PUBLISHING` 之间执行，任一失败包自动转 `REJECTED` 并附原因）：
   - **dry-run DDL**：对包内全部 `align()` 动作生成预览 SQL 并对影子库/事务内回滚执行（复用 `PhysicalStructureGateway` 的幂等非破坏约束：只增列不删列不改类型）；
   - **schema 漂移校验**：复用已落地的 `validateForPublish`（物理列 DATA_TYPE 比对）；
   - **影响面清单**：列出消费方（generator 产物、集成映射、主数据 convert 链接），首版仅展示不阻断。
4. **快照与回滚**：`APPROVED` 时写入 `meta_model_snapshot`（实体/字段 JSON 全量快照）；回滚 = 按快照逆向生成补偿动作。**结构性破坏变更（删列/改类型收窄）不自动回滚**，只生成人工工单——自动 DDL 保持幂等非破坏红线。
5. **审批留痕走平台审计总线**（ADR-0007）：包状态迁移全部落审计事件。

## 表设计（L3 DDL，随实现 Change 一并入 `bone-init.sql`）

```sql
CREATE TABLE meta_release_package (
    id            BIGINT NOT NULL,
    tenant_id     BIGINT NOT NULL,
    name          VARCHAR(200) NOT NULL,
    module_id     BIGINT DEFAULT NULL COMMENT '建议按模块打包（ADR-0031 归属维度）',
    status        VARCHAR(30) NOT NULL COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED/PUBLISHING/PUBLISHED/REJECTED/ROLLBACK_DONE',
    created_by    BIGINT NOT NULL,
    approved_by   BIGINT DEFAULT NULL,
    approved_at   DATETIME(3) DEFAULT NULL,
    reject_reason VARCHAR(500) DEFAULT NULL,
    published_at  DATETIME(3) DEFAULT NULL,
    -- 标准审计列 + deleted + version
    PRIMARY KEY (id), KEY idx_rel_pkg_tenant (tenant_id, status)
) COMMENT='元数据发布包';

CREATE TABLE meta_release_item (
    id             BIGINT NOT NULL,
    tenant_id      BIGINT NOT NULL,
    package_id     BIGINT NOT NULL,
    entity_id      BIGINT NOT NULL,
    change_summary JSON DEFAULT NULL COMMENT '字段级变更清单（含 dry-run DDL 预览）',
    -- 标准审计列
    PRIMARY KEY (id), KEY idx_rel_item_pkg (package_id)
) COMMENT='发布包条目';

CREATE TABLE meta_model_snapshot (
    id         BIGINT NOT NULL,
    tenant_id  BIGINT NOT NULL,
    entity_id  BIGINT NOT NULL,
    package_id BIGINT NOT NULL,
    snapshot   JSON NOT NULL COMMENT '实体+字段全量 JSON 快照',
    PRIMARY KEY (id), KEY idx_snap_entity (tenant_id, entity_id)
) COMMENT='发布快照（回滚同源）';
```

## 分批落地清单

| 批次 | 内容 | 依赖 |
|---|---|---|
| R1 | 三表 DDL + 发布包状态机 + 隐式建包（旧 API 兼容）+ dry-run DDL | 本 ADR |
| R2 | 审批流（SoD + 权限码 `release:create/approve`）+ 影响面清单 | R1、G5 |
| R3 | 快照回滚 + 回滚工单 + 前端发布台（包列表/diff/审批） | R2 |

## 后果

- 正面：生产 DDL 全部经审批与 dry-run；每次发布可追溯、可回滚；「模块」由此获得部署单元语义（2a 应用三重语义闭环）。
- 代价：发布从一次点击变为显式流程（对单人开发者可先走单元素包快速通道——隐式建包已兼容）；三张新表与状态机属重型变更，必须独立 Change（含 ArchUnit 基线自证与 `mvn clean test` 全量门禁）。
