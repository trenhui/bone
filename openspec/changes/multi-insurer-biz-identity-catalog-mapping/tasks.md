# Tasks: multi-insurer-biz-identity-catalog-mapping

> 分工说明：P0 内核（建表/实体仓储/解析内核/未命中队列/应用服务/REST）由并行会话按 `docs/superpowers/plans/2026-11-03-multi-insurer-master-data-mapping.md` 执行，不在本清单内重复。本清单是内核之上的接线、管理面与验收复核。

## 1. 主体引用收紧（P0-1）

- [ ] 1.1 `ClaimConfigApplicationService.createPolicy/updatePolicy` 校验 bizIdentityCode 引用本租户 ACTIVE 的 `ClaimInsurer`（读取不强校验），补单测（DISABLED/TERMINATED/不存在主体被拒、存量空值读取不受影响）
- [ ] 1.2 存量对齐 dry-run 脚本：按 code/name 模糊匹配生成对齐清单（只输出不落库）；验证：dry-run 输出可读清单

## 2. 映射导入与管理查询（P0-2）

- [ ] 2.1 `CodeMappingApplicationService` 补映射/保司码表/未命中队列的分页管理查询（主体+域筛选、关键字检索、未命中按 last_seen_at/occur_count 排序）+ 主体×域健康度统计（覆盖率/OPEN 数，聚合查询不建新表），REST 返回 `ApiResponse<T>`；单测
- [ ] 2.2 Excel 批量导入（POI 解析 → 全量逐行校验 → 任一失败整批拒绝返回行号+原因 → 成功行走版本化发布语义 source=IMPORT；单批 ≤2000；同文件内容指纹幂等去重；模板下载），REST 接口 + 单测覆盖全部成功/整批失败/超限/幂等四分支；`mvn spotless:apply`
- [ ] 2.3 批量解析接口：同主体+域多码一次解析、逐码独立结果、与单码语义一致（补一致性与混合结果单测）、处置/导入/发布操作留痕（操作人+reason_code）与处置独立权限点

## 3. 两段式覆盖（P1-3）

- [ ] 3.1 新增 `CoverageOverride` 聚合与 `t_claim_coverage_override` 表（biz_identity_code/coverage_uuid/override_field/standard_value 快照/override_value/DISABLED-ACTIVE，UK(tenant_id,biz_identity_code,coverage_uuid,override_field)，@Version；DDL 落 bone-init.sql + scripts/migration/，通过 EntityTableDdlCoverageTest），repository + 单测
- [ ] 3.2 `CoverageOverrideApplicationService`：覆盖项管理、合并预览（实时合并：标准/覆盖/生效/来源）、发布（受影响保单数确认后 DISABLED→ACTIVE）、停用回落、「同步标准」（不覆盖显式覆盖项）；`EffectiveConfigViewService` 合并查询供理算前消费；REST + 合并逻辑单测（无覆盖继承/有覆盖合并/停用回落/PAUSE 兼容）

## 4. 保单聚合详情（P1-4）

- [ ] 4.1 `PolicyDetailApplicationService` 聚合查询：基础信息+责任全貌（plan→risk→coverage 树含 limit_*_codes 与费用规则）+被保人假名名单+额度池（复用 QuotaAccountRepository），只读、权限沿用列表口径；REST + 单测

## 5. 前端管理面（bone-claim-app）

- [ ] 5.1 PolicyManagementPage：主体改可搜索 Select（数据源=ACTIVE 主体分页），新建/编辑接入后端校验错误提示；npm run build 通过
- [ ] 5.2 主体管理页（列表/检索/新建/编辑/启停/终止，含关联保单数），路由 CLAIM_ROUTES + 权限常量；npm run build 通过
- [ ] 5.3 映射工作台页：映射/码表/未命中队列三视图（主体+域筛选）、Excel 上传+行级错误展示、未命中处置（补映射/忽略）；npm run build 通过
- [ ] 5.4 两段式配置交互：覆盖项管理 + 合并预览对照表 + 发布确认（受影响保单数）+ 同步标准；npm run build 通过
- [ ] 5.5 保单详情页：列表跳转详情（Tab：基础信息/责任全貌/适用名单/额度池，只读）；npm run build 通过

## 6. 实现复核与收尾（等并行会话 P0 内核完成后）

- [ ] 6.1 复核 P0 内核实现对照 specs/权威设计七条不变量：fail-closed 无空串回落、未命中一码一行不撞键、active_flag='Y' 唯一、superseded_by_id 回放链、身份型表无物理删除、发布前标准码验存在、软删偏离的 SDK 兼容（deleted 恒 0 + 删除方法覆盖为置状态）；输出复核清单
- [ ] 6.2 全量自检：`mvn spotless:apply` + `./scripts/check.sh` + `mvn test`（synap-claim 模块）全绿；前端 `npm run build` 通过
