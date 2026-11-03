# Design: multi-insurer-biz-identity-catalog-mapping

> **权威设计**：`doc/ai-product/synapClaim/04-design/多保司保单配置面与主数据映射设计.md`（72KB 深度设计，数据模型 §6.3、不变量 §6.4、发布治理 §7、页面 §8、接口 §9）。本文件只记录本 change 相对该权威设计的接线决策与范围裁剪。

## Context

多保司主数据映射 P0 内核（7 表：主体/保司码表/映射/字段绑定/标准码别名/标准码拆分/未命中队列 + fail-closed 解析内核）由并行会话按权威设计与 build plan 实现（未提交工作区代码）。理赔域已有保单聚合（`t_claim_policy`，biz_identity_code 透传）、责任配置（`t_claim_coverage.limit_*_codes` 分隔串 + `t_adj_liability_fee_rule`）、额度池（`QuotaAccountRepository`）、目录四库（`t_ref_*`）。约束：bone-metadata-sdk、ADR-0028、HC-008、@Version、ApiResponse。

## Goals / Non-Goals

- Goals：主体引用收紧；映射 Excel 导入与管理查询；责任级两段式覆盖（实时合并+同步标准）；保单聚合详情；前端管理面（主体/映射工作台/详情 3 页）；P0 内核实现复核。
- Non-Goals：P0 内核本身（并行会话 plan 负责）；P2（保全联动/职业库/快捷理算/双保单号/配置控制台单页树/maker-checker/bitemporal）；改理算引擎；保单维度库名单；明文证件。

## Decisions

1. **复用内核 7 表体系，不新建主体/映射表**：本 change 一切主体与映射能力以内核聚合（`ClaimInsurer`/`CodeMapping`/`InsurerCodeItem`/`MappingMiss` 等）为唯一真源；与权威设计冲突时以权威设计为准。
2. **保单引用校验放应用服务**：`createPolicy`/`updatePolicy` 校验 bizIdentityCode 命中 ACTIVE 主体；读取不强校验；dry-run 对齐清单人工确认，不自动猜配。
3. **Excel 导入 = 全量校验 + 整批原子 + 版本化发布 + 内容指纹幂等**：POI（5.3.0 已管理）解析 → 逐行校验 → 任一失败整批拒绝；成功行逐条走 publish（source=IMPORT），天然获得 active_flag 唯一与 superseded_by_id 回放链；单批 ≤2000；同主体+域+内容指纹的重复上传直接返回首次结果（防误触重导制造版本噪音）。配套模板下载。
4. **收单热路径批量解析与治理留痕**：解析入口提供批量接口（同主体+域多码一次返回、逐码独立结果、语义与单码一致），避免收单逐码 N+1；健康度统计（覆盖率/OPEN 未命中数）走既有表聚合，不建新表；处置/导入/发布留痕（操作人+reason_code），处置独立权限点。
5. **两段式覆盖 = 新表 `t_claim_coverage_override`（责任粒度）+ 应用层实时合并**：覆盖项存标准值快照；发布=DISABLED→ACTIVE（附受影响保单数）；停用回落；「同步标准」为只读合并语义不产生新记录；不触碰 `configStatus` 三态；覆盖维度首版限定适用名单/费用类别规则。
6. **详情视图走聚合查询接口**：`PolicyDetailApplicationService` 一次聚合基础信息+责任全貌+假名名单+额度池（复用 `QuotaAccountRepository`），只读。
7. **前端路由单一真源**：3 个新页面注册进 `CLAIM_ROUTES`，权限常量同步；保单表单主体改可搜索 Select。

## Risks / Trade-offs

- [与并行会话并发写同一 change 状态] → 本会话只维护 specs/tasks 作为验收契约，phase 流转交给实现会话；复核阶段统一对账。
- [存量 biz_identity_code 脏数据] → dry-run 对齐清单人工确认。
- [覆盖与标准漂移] → 覆盖存标准值快照，预览页可视化对比。
- [导入大文件] → 2000 行上限 + 拆分提示。

## Migration Plan

1. 并行会话完成 P0 内核（建表/内核/REST）。
2. 本会话接线：引用校验、导入、管理查询、覆盖表与详情、前端 3 页。
3. 复核内核实现对照七条不变量，输出复核清单。
4. 回滚：新表与新增接口不影响既有链路；校验回滚即恢复透传。

## Open Questions

（无——字段级细节以权威设计 §6.3/§6.4 为准。）
