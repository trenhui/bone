# 0052 报销实体改名 · 存量迁移运行手册 & PR 说明

## 一、背景与目的
- 将原 `expense_claim`（费用报销）实体改名为 `expense_reimbursement`，避免与 `claim_case`（理赔）混淆，语义更贴合「费用报销 / reimbursement」。
- 改名范围：实体码 + 物理表 + Java 类名 + bean + 测试 + 种子 + 文档 + 前端（代码侧已在 `891375753` / `41d8b8497` / `0453214f` 完成）。
- 本脚本（0052）负责**存量数据迁移**：已落库但仍是旧码的数据与元数据。

## 二、脚本做了什么（7 步）
1. **物理表 RENAME**：`t_expense_claim`→`t_expense_reimbursement`、`t_expense_claim_item`→`t_expense_reimbursement_item`（保留数据，无外键可安全重命名）。
2. **`meta_entity.code / table_name`**：两个真实实体改名（按旧码精确匹配，不波及测试实体）。
3. **`meta_entity_action_def.entity_code` + `handler_bean`**：确认打款处理器 bean `expenseClaimConfirmPayoutHandler`→`expenseReimbursementConfirmPayoutHandler`（含测试实体的引用一并改，否则会指向不存在的 bean）。
4–6. **UI schema / 字段校验规则 / 模板 JSON** 内嵌旧实体码 REPLACE（`expense_claim_item`→`expense_reimbursement_item`，再 `expense_claim`→`expense_reimbursement`；顺序保证子串不被提前覆盖）。
7. **`iam_permission.code / resource_path`**：权限码为**基名**形态 `runtime:expense_claim:*`，故做基名 REPLACE（`expense_claim_item` 含 `expense_claim` 子串，基名替换可同时覆盖基名与子实体两种形态）。

## 三、幂等性与适用范围
- RENAME 前置存在性判断；其余 UPDATE 按旧码定位，无旧行即空转。**可重复执行**。
- 全新库：0037 直接建 `expense_reimbursement`，本脚本各步空转（幂等）。

## 四、自校验通过判据（脚本第 8 步 SELECT）
| 指标 | 期望 |
|---|---|
| `new_tables` | = 2（`t_expense_reimbursement` / `_item` 均已存在） |
| `renamed_entities_active` | ≥ 1（item 通常 active；header 视业务软删状态） |
| `renamed_actions` | ≥ 1 |
| `renamed_perms` | ≥ 6（基名 3 + 子实体 3；模式 `runtime:expense_reimbursement%` **不带末尾冒号**才能覆盖子实体权限） |
| `leftover_old_tables` / `leftover_old_perms` / `leftover_old_code` | **必须全 = 0**（非 0 说明旧名残留，须排查） |

## 五、执行步骤（DBA / CI，L3）
```bash
mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0052_rename_expense_claim_to_reimbursement.sql
```
1. **执行前必须对目标库做全量备份 / 快照**（硬性前置）。
2. 跑完核对第 8 步自校验 7 项是否达标；三项 `leftover` 必须为 0。
3. 全绿即可；若 `leftover ≠ 0`，先排查再放过，不可带旧码上线。
4. （可选复核）另跑独立校验脚本，纯 SELECT 重复确认结果（与主脚本第 8 步自校验同源）：
   ```bash
   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0052_verify.sql
   ```

## 六、回滚预案
- 本脚本为「改名」型一次性迁移，且应用代码已同步改名；**生产不可反向执行本脚本**（会使库名与已上线代码失配）。
- 唯一安全回滚：执行前备份 → 异常时整库恢复至迁移前。
- 仅当「代码尚未上线」的预发环境需回退元数据改名时，才可按第 2~7 步反向 REPLACE 手工构造，且必须同步回退应用代码。
- ⚠ 脚本含 DDL（RENAME TABLE），MySQL 下 DDL 会隐式提交，脚本整体**非原子**；中途失败会留下部分改动，故执行前备份是硬性前置。

## 七、验证记录
- **临时库端到端**：镜像真实旧码种子（主/子实体、action_def×4、iam 基名+子实体×6、ui_schema/field/template 内嵌旧码、待 RENAME 物理表）跑通，自校验 7 项全达标（`new_tables=2` / `renamed_perms=6` / 三项 `leftover=0`）。
- **dev 库实跑**：`127.0.0.1:3306/bone`（非生产）执行成功，RENAME + 两实体 + action_def（含 94 条 handler_bean 引用同步改名）+ iam×6 全部落地，自校验全绿。
- **集成测试**：`ServerContextLoadsTest` / `OrderPaymentMetadataE2ETest` / `MetadataManagementControllerTest` 共 12 例全绿；测试跑过后数据自校验仍完整（无污染）。

## 八、关联提交
- `891375753` / `41d8b8497` / `0453214f` 代码改名（实体码/物理表/类名/bean/测试/种子/文档/前端）
- `b585cda3` 0052 补 iam 基名替换 + 自检 `WHERE` 修复
- `69554e84` 0052 自检 `renamed_perms` 模式覆盖基名与子实体
- `4b1401dd` i18n 豁免并发提交者未登记码 `SC_CLAIM_DECISION_ACTIVE_CONFLICT`（清门禁，与本次无关）
- `710d4ac0` 本脚本头部可运维文档（自校验判据 / 回滚 / 验证记录）

## 九、待办 / 注意
- 生产库（`tpa-saas`）由 DBA 执行本脚本（L4 红线，AI 不连生产）。
- dev 实例遗留一个无害的 MySQL 数据字典幽灵条目 `bone_0052_scratch`（验证过程 DROP 被中断所致，数据目录已不存在、零数据、零功能影响）；如需清除需 DBA/运维 `sudo mkdir` 缺失目录后 DROP，或重启 MySQL 实例自愈，非必要。
