# bone-metadata-engine 的 HC-006 修复草案（暂缓代码 patch）

> 状态：**被 Comet 占用，不产出独立代码 patch**。本文件是修复设计草案 + 决策记录，供架构师与
> `refactor/metadata-engine-boundary-ddd` 重构归并。

## 1. 现状 / 违规点（实测）

- 违规类：`bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/.../runtime/JdbcRuntimeRecordService.java`
  - 第 14-15 行：`import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate / MapSqlParameterSource;`
  - 第 34、76-79、139、206、246、336、379 行：直接持有 `NamedParameterJdbcTemplate` 并对**按已发布元数据动态生成的物理表**执行 `SELECT/INSERT/UPDATE/DELETE`（动态表名 + 动态列名，参数化防注入，租户/软删/审计列已注入）。
- 辅助类：`RuntimeQuerySupport.java`（第 8 行 `MapSqlParameterSource`）负责构造 WHERE/ORDER BY/SELECT 列片段，本身不持 JDBC 句柄，仅喂给上面的 service。
- 对照设计稿 `doc/design/modules/9. SmartMeta 引擎模块技术说明.md` §2.3「**禁止引擎内再实现一套 JDBC**」→ 此处与之冲突，锚定 HC-006 = `violated`。
- 基线：`grep -c bone-metadata-engine doc/architecture/sdk-persistence-bypass-baseline.json` = **0**（未登记）。
- 扫描：`check-sdk-persistence.py` 按模块排除未拦截该引擎（锚定 HC-006 证据已注明）。

## 2. 性质判断（这是"架构边界"问题，不是"业务模块偷跑 JDBC"）

引擎的职责正是「按已发布元数据对物理表执行动态 CRUD」，表名/列名在编译期不可知，**静态 SDK Repository 模式无法表达**。因此这里的 JDBC 有结构性必要性——它不等同于业务模块绕过 SDK 偷连库。但也正因如此，它必须被**显式纳入边界治理**，而非游离在基线之外。

## 3. 阻塞：Comet 占用（关键）

- 锚定：`has_active_comet_change: true`，`comet_change_id: refactor/metadata-engine-boundary-ddd`（design 阶段）。
- 规则（锚定 + 工作流）：**该模块必须跳过，直到 change 归档或分支合并**；夜间任务亦不得触碰。
- 结论：**不在本轮/夜间独立出代码 patch**，否则与进行中的重构冲突、互相覆盖。

## 4. 推荐修复形态（待 Comet change 归档后、或并入该 change 一并实现）

二选一，需架构师拍板：

- **方案 A（推荐，治本）**：在 `bone-metadata-sdk` 暴露一个「引擎内部授权端口」（如 `EnginePhysicalJdbcPort`），由 SDK 统一持有 `DataSource`/事务/租户解析；`JdbcRuntimeRecordService` 改为依赖该端口而非自己 new `NamedParameterJdbcTemplate`。
  - 收益：动态能力保留；`check-sdk-persistence.py` 能在端口层识别并纳入基线；满足 §2.3「引擎不自行持有 JDBC 栈」。
- **方案 B（妥协，登记豁免）**：若端口方案不被采纳，则在 `sdk-persistence-bypass-baseline.json` 中将 `JdbcRuntimeRecordService` 的 JDBC 使用登记为「引擎内部授权例外」，并附 ADR 说明动态元数据 CRUD 的必要性。
  - 注意：基线**只可收缩**，新增登记需架构师同意；且应同时修正 `check-sdk-persistence.py` 不再按整模块排除引擎，改为按登记项精确核对。

## 5. 决策点

- 架构师确认采用 A 还是 B。
- 若选 B，明确 `check-sdk-persistence.py` 的排除逻辑如何收敛（否则该模块 JDBC 永远游离在扫描之外）。

## 6. 夜间 / 本轮处置

- 锚定已标 `skip` + `comet-occupied`；HC-006 维持 `violated`，**待 `refactor/metadata-engine-boundary-ddd` 归档后由该 change 重置为 `implemented`**。
- 不要为本 HC-006 独立出代码 patch（与本文件立场一致）。
- `_review-status.yaml` 中 `bone-metadata-engine` 已是 `skip`，且其 skip 语义为「Comet 占用，条件消失后转 pending」——无需改动。
