# ADR-0031: 平台模型层 — 模板 + 三层归属 + 租户实例化

**状态**：已接受（2026-09-26）｜ **关联**：2a 设计 §3.1/§5.1（UC-MP1/UC-MT2）、ADR-0029（租户自动过滤）、ADR-0017（元数据→主数据同步）

## 背景与问题

`meta_entity.tenant_id NOT NULL`（无 DEFAULT 0），`is_builtin` 恒写死 false 且全仓无读取点 ⇒ **平台模型层完全缺失**：N 个租户 = N 遍重复建模，平台无法沉淀「客户 / 订单 / 支付」等可复用蓝图（2a §G3）。

## 决策

1. **三层归属**：平台模板（`tenant_id=0`，全租户共享只读）→ 租户共享 → 应用私有。以 `meta_entity` 新列 `scope`（`PLATFORM`/`TENANT`，默认 `TENANT`）显式表达，替代语义模糊的 `is_builtin`（保留列但不再赋予语义，后续清理）。
2. **模板结构**：`meta_model_template`（蓝图头：编码/名称/分类/版本/状态）+ `meta_model_template_field`（默认字段集）。版本演进（UC-MP2 的 diff/同步）不建独立 version 表，首版以 `current_version` 字符串 + 模板行内演进承载；需要多版本并存时再立 ADR。
3. **跨租户只读的合法通道**：模板查询统一 `Criteria.disableTenantFilter() + eq("tenantId", 0)`——这是 ADR-0029 显式声明的例外用法（门禁判据：全租户入口须显式声明，E-4.4），仅限模板读侧，租户数据一律不过此通道。
4. **实例化语义**：`POST /api/v1/metadata/templates/{id}/instantiate` 把模板复制为租户 `meta_entity`（`scope=TENANT`、`template_id` 追溯）+ 逐条复制默认字段。实体创建复用 `MetaEntityApplicationService`（唯一性校验 + G1② 建模准入），字段复制不支持选择性裁剪（首版全量复制，减项靠实例化后删除）。
5. **平台写面收敛**：模板的创建/发布（UC-MP1）本轮**不开写 API**，经种子（`bone-init.sql`，按 code 幂等）+ 平台运维通道维护；避免「平台写」权限面扩散，待模板目录具备多租户运营形态再立 Change。
6. **权限**：目录/字段查看 `metadata:template:read`；实例化=建模写（`metadata:template:write` OR `metadata:model:write`，兼容旧码）。

## 后果

- 正面：平台可沉淀行业蓝图；租户一键获得带字段集的可运行模型；`template_id` 为 UC-MP2 模板升级 diff 提供追溯锚点。
- 代价：`meta_entity` 多两列（DDL 已入 `bone-init.sql` + 本地库 ALTER）；模板读侧是全仓第二处合法 `disableTenantFilter`（第一处为 `@TenantScope(ALL)` 定时任务），ArchUnit 豁免理由已写入仓储 javadoc。
- 未做（后续 Change）：模板升级 diff/租户同步（UC-MP2）、模板版本表、平台写 API、前端模板目录页。
