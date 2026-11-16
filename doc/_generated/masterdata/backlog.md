# masterdata [Target] / [Vision] Backlog

> **生成时间**：2026-11-13T09:04:52Z（UTC）  
> **维护源**：[`backlog.yaml`](../../../tools/masterdata-compliance-collector/backlog.yaml)

| Tier | ID | 项 | 引用 | 跟踪 |
|------|-----|-----|------|------|
| **Target** | `catalog-sync-adr-full` | ADR-0017 目录同步全链路（metadata 与主数据） | ADR-0017 | partial — convert/模板实例化已落地；全链路同步未完成，部分实体仍手工登记 |
| **Target** | `quality-rule-engine` | 批量质检任务与整改闭环 | 详设 4 | partial — 规则定义与单次质检已实现；批量质检任务、整改工单复检闭环零命中 |
| **P0-Target** | `golden-record-merge` | 黄金记录合并与匹配（survivorship / 误合并回滚） | 3a 详设 §11.3 / 终局裁决 2-A1 | partial — 领域内核（17 例纯单测）+ GoldenRecordApplicationService + GoldenRecordController + HTTP 联调 24 例已落地；mdm_golden_record 与 mdm_merge_audit 建表、JDBC adapter、消费侧接入未交付 |
| **P0-Target** | `entity-resolution-xref` | 跨系统实体身份映射（Key Mapping / xref） | 3a 详设 §11.4 / 终局裁决 3 | partial — 领域内核（8 例纯单测）+ EntityKeyMappingPort 编排接入 + 改绑 fail-closed 联调已落地；mdm_xref 建表、独立 xref 治理 API 与 ERP adapter 未交付 |
| **P0-Target** | `masterdata-outbox` | 主数据分发 Outbox 与版本化事件 | 3a UC-MD-T8 / 3a 详设 §11.4 | partial — 事件信封 + MasterDataOutboxPort + 编排幂等分发 + MasterDataOutboxRelay（幂等/版本单调/有界重试/死信/版本戳对账）与联调已落地；mdm_outbox 建表、生产消费侧 adapter 未交付；EntitySubscription 仍只登记授权不投递 |
| **P0-Target** | `field-level-lineage` | 字段值级 lineage 与类型化字段存储 | 3a 详设 §11.3 / 终局裁决 2-C1 | partial — FieldProvenance 字段级 lineage + 编排层 HMAC-SHA256 摘要（低熵 PII 不可离线反推）已落地；MasterDataRecord.data 仍为 JSON blob，mdm_field_lineage 建表与类型化字段存储未交付 |
| **P0-Target** | `change-request-segmented` | 分段并行变更请求（General/CompanyCode/PurchasingOrg 双人复核） | 3a 4.3 / ADR 修订 B1 | partial — 三段并行 CR 领域内核 + ChangeRequestPort/Activation/Workflow 三端口 + ChangeRequestApplicationService + ChangeRequestController + 纯单测/编排联调/HTTP 联调已落地；v1.1 的降 Vision 减法已在 3a §4.1 撤销；mdm_change_request* 建表、工作流引擎 adapter、Steward 具名署名未交付 |
