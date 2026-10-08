# bone-metadata-server 数据治理扩展验证报告（订单/支付蓝图场景）

> 日期：2026-10-02 ｜ 分支：dev ｜ 验证方式：MockMvc 全链路 E2E（真实本地 MySQL，profile=test）+ 前端 tsc 类型检查

## 一、需求落点

| 原始需求 | 实现方式 |
|---|---|
| bone-blueprint 的表模拟真实场景 | 以 `Order`/`Payment` 聚合为真源，建模为 metadata-server 目录实体 + 字段（既有 E2E 已覆盖建模/发布/运行时 CRUD） |
| 基于业界元数据对字段扩展 | `meta_field` 新增 6 个数据治理属性列 + 复用既有 `enum_values`/`validation_rules` JSON 列 |
| 前后端功能串联验证 | 新增治理属性闭环 E2E（模拟前端调用序列）+ 前端字段向导/实体详情 UI 扩展 + tsc 通过 |

## 二、治理属性集（业界通用口径）

| 属性 | 列名 | 取值/说明 |
|---|---|---|
| 数据分级 | `data_classification` | PUBLIC/INTERNAL/CONFIDENTIAL/SECRET/TOP_SECRET |
| 个人敏感信息 | `is_pii` | TINYINT(1)，PII 标记 |
| 敏感级别 | `sensitivity_level` | L1 一般/L2 较敏感/L3 敏感/L4 极敏感 |
| 数据管家 | `data_steward` | 责任人 |
| 业务术语 | `business_term` | 数据标准/业务词汇 |
| 来源系统 | `source_system` | 血缘（如 CRM/POS） |
| 枚举/码表 | `enum_values`（已有） | JSON，标准码表 |
| 校验/质量规则 | `validation_rules`（已有） | JSON |

## 三、改动清单（14 个文件）

**后端 bone-metadata-server（7）**
- `catalog/domain/model/meta/MetaField.java` — 治理字段 + `create`(22 参)/`update`/`createFromPhysical` 透传
- `catalog/application/command/cmd/CreateMetaFieldCommand.java`、`UpdateMetaFieldCommand.java` — 治理字段
- `catalog/application/query/dto/MetaFieldDTO.java`、`query/mapper/CatalogDtoMapper.java` — 出参暴露
- `catalog/application/MetaEntityApplicationService.java` — createField/updateField/copyEntity 透传；新增 `validateFieldGovernance` 校验；常量集 `ALLOWED_DATA_CLASSIFICATIONS`/`ALLOWED_SENSITIVITY_LEVELS`
- `src/test/.../OrderPaymentMetadataE2ETest.java` — 新增治理闭环用例

**DDL（1）**
- `bone-init.sql` — meta_field 新增 6 列（AFTER validation_rules）；**实库 bone.meta_field 已 ALTER 同步**

**前端 bone-frontend（3）**
- `packages/shared-types/src/metadata.ts` — MetaField/Create/Update 类型 + `META_DATA_CLASSIFICATIONS`/`META_SENSITIVITY_LEVELS` 常量
- `apps/bone-metadata-app/src/components/FieldWizardModal.tsx` — 字段向导第 2 步「数据治理」表单（分级/PII/敏感级/管家/术语/来源）
- `apps/bone-metadata-app/src/pages/EntityDetail.tsx` — 字段表治理 Tag 列、编辑 Modal 治理表单、详情 Descriptions 展示；inline-save 保留治理属性不被覆盖

## 四、验证结果

| 项 | 结果 |
|---|---|
| `mvn spotless:check`（在线，模块级） | ✅ PASS |
| 治理闭环 E2E `governanceAttributes_roundTripAndValidation` | ✅ PASS（6.4s） |
| 全类回归 `OrderPaymentMetadataE2ETest`（6 用例） | ✅ 6/6 PASS，BUILD SUCCESS |
| 前端 `tsc --noEmit`（bone-metadata-app） | ✅ PASS |
| 实库 `bone.meta_field` | ✅ 6 治理列已补齐，插入/查询正常 |

**E2E 覆盖链路**（HTTP 层，即前端实际调用序列）：
创建带治理属性字段（PII+机密+L3+管家）→ GET 详情全量回读 → PUT 升密级/管家移交 → 复制实体治理属性随迁 → 校验规则（PII 无管家→`FIELD_STEWARD_MISSING` warning；非法分级→`FIELD_DC_INVALID` error）。

## 五、遗留与建议

1. **其余环境升级**：test/dev 之外的库（生产）上线前需执行同样 ALTER（可沉淀为 `scripts/migration/` 增量脚本，按 ADR-生产数据库增量迁移策略）。
2. **治理校验目前为 WARNING**：PII 无管家不阻断发布；如需强管控可升级为 error（当前设计取舍：不与 core 列校验耦合）。
3. **运行时侧未挂治理属性**：`FieldMetadata`（SDK runtime 模型）未扩展；治理属性目前止步于建模目录层，如需血缘/脱敏联动需二期。
4. 本报告未提交 git（dev 分支上另有其他会话在途改动，避免混提）。
