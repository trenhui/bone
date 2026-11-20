# 设计：procuraAI 业务模块骨架（批次4）

## 架构决策

- **独立模块，平级扩展**：三个新上下文各为一个 Maven 模块（sourcing / qualification / risk），挂在 `bone-procurement` 父聚合下，与 transaction / fulfillment / contract / ai-governance 平级。不复用 governance 的横切包，避免业务语义与治理语义混模块。
- **DDD 四层**：adapter(web) → application → domain ← infrastructure。领域层不依赖任何外层；跨上下文 domain 依赖由 ArchUnit `noCrossContextDomainDependency` 守护。三个模块各带 ArchitectureTest（13 条共享规则 + 端点权限 + 无跨上下文依赖）。
- **持久化策略**：DDL 落 `bone-init.sql`（init-only，与既有 9 张采购表一致，不建增量迁移脚本以避免终态覆盖冲突）；运行期先用受控内存仓储跑通骨架，生产仓储替换属 L3。@Table/@Column 精确对齐 DDL 列。
- **状态机**：所有状态迁移集中在 domain，用显式 `Map<State, Set<State>>` 迁移表 + fail-closed（未列出即拒绝）；终态不可回写。关键不变量由领域方法守，不由调用方自律。

## 关键不变量与红线

| 模块 | 不变量 | fail-closed 行为 |
|------|--------|------------------|
| 寻源 | INV-01 PUBLISHED 后关键字段冻结；INV-03 RFx 截止单调、到期不收标；INV-09 授标必须具名人工 | 非法迁移 IllegalStateException；匿名/AI 授标 IllegalArgumentException |
| 准入 | INV-10 EXPIRED 不得作准入依据；INV-11 OCR 须人工复核；INV-13 单供应商仅一个 IN_REVIEW | 过期复核/终态回写被拒；DDL 生成列 uk_proc_onboarding_in_review 防并发 |
| 风险 | 来源幂等 (supplier,source,sourceRef)；UNKNOWN 严重度转人工；绩效不自动授信 | UNKNOWN 不压成 LOW；重复投递复用 ID；canDecideSupplierAutomatically() 恒 false |

**AI 红线**：AI 评分只作建议字段（onboarding.score），不得置 APPROVED/AWARDED；绩效权重冻结、外部征信接入、自动淘汰均属 owner，本批不实现。

## 门禁对齐

- DDL 三项：check-ddl-doc-sync（192 表一致）、check-ddl-required-columns（新增表 0 违规）、check-ddl-duplicate-column（无重复）。
- 迁移：check-migration-manifest（M1-M11，补登记 0127-0130）、check-migration-init-coverage（终态 91 张全覆盖，列缺口 0）。
- procuraAI 文档族：check_procura_alignment（10/10）、check_procura_evalset（PASS）、generate_asyncapi --check（29 事件零漂移）。端点已在 openapi.yaml §02/§03 卷占位，A10.9 双向覆盖无需新增豁免。
- Java：三模块各 22 例 clean test 全绿（含 13 条架构规则）。

## 负向探针

- DdlCoverageTest：实体 @Table 在 DDL 无建表 → offenders 非空判红（守卫建表存在性）。
- 领域测试：匿名授标 / 过期证照复核 / 终态回写 / 截止提前 / 重复预警均断言抛异常，反向证明 fail-closed 非摆设。
- 端点契约测试：反射校验每个 HTTP 方法带 @PreAuthorize('procurement:...')，漏标即红。
