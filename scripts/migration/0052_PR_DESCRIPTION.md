# PR 描述草稿 · 0052 expense_claim→expense_reimbursement 改名迁移 + CI 可重复校验

> 标签：`migration` `L3-ci` `needs-architect-approval`
> 本文件随 PR 提交，供评审与架构师签批使用；合并时可整段贴入 PR 描述。

---

## 一、概述
将原 `expense_claim`（费用报销）实体改名为 `expense_reimbursement`，避免与 `claim_case`（理赔）混淆；同步做存量数据/元数据迁移，并把"迁移可重复校验"工程化进 CI。

## 二、改动清单
- **迁移脚本** `scripts/migration/0052_rename_expense_claim_to_reimbursement.sql`：物理表 RENAME + 元数据（实体/动作定义/权限码/UI schema/字段校验/模板 JSON）旧码→新码 REPLACE，幂等可重跑。
- **独立校验脚本** `scripts/migration/0052_verify.sql`：纯 SELECT 自校验（7 项指标），供生产跑完主脚本后单独复核。
- **可复用校验运行器** `scripts/migration/verify_0052.py`：建临时库→植入改名前旧码种子→执行 0052→跑自校验→断言→清理；本地与 CI 通用。
- **CI 工作流** `.github/workflows/migration-verify.yml`：**【L3 需架构师审批】** path-filtered 仅 `scripts/migration/**` 变更时触发，复用既有 `mysql:8.0` service 真机校验。
- **运行手册** `scripts/migration/0052_rename_expense_claim_to_reimbursement_RUNBOOK.md`：自校验判据、回滚预案、验证记录、关联提交。

## 三、为什么需要 L3 审批（CI 脚本）
本仓规范规定 CI 脚本属 L3，须经架构师审批。本次新增的工作流是全新 CI 能力，故单独提请签批。

## 四、CI 改动安全性论证
1. **零常态成本**：`on: paths: scripts/migration/**`，仅迁移目录变更才触发；普通提交不跑，不增加 CI 负载。
2. **复用既有基础设施**：`mysql:8.0` service 与 `ci.yml` 的 Backend Quality Check job 完全一致（同镜像、同 healthcheck），非新引入数据库。
3. **不触碰任何真实/生产数据**：运行器只建 `bone_0052_ci_<ts>` 临时库、种子化"改名前"最小镜像、自检后 `DROP` 清理，对 `bone`/`tpa-saas` 无任何读写。
4. **无构建链污染**：仅新增一个 Python 脚本 + 一个 YAML，不改动 Java 依赖、不引入 Maven 插件；`pip install pymysql` 在 CI 内隔离执行。
5. **失败即阻断**：断言不达标（如 `renamed_perms≠6` 或 `leftover≠0`）进程返回非 0，CI 标红，防止坏脚本被合并。

## 五、验证证据
- **本地实跑** `verify_0052.py`：`PASS`，临时库已清理；自校验 `new_tables=2 / renamed_perms=6 / 三项 leftover=0`。
- **dev 库（`127.0.0.1:3306/bone`，非生产）实跑 0052**：RENAME + 两实体 + action_def（含 94 条 handler_bean 引用）+ iam×6 全部落地，自校验全绿。
- **后端集成测试** 12 例全绿（ServerContextLoads / OrderPaymentMetadataE2E / MetadataManagementController）。
- **全仓改名审计**：除本迁移脚本与一处测试**方法名**外，无悬空旧名引用。

## 六、风险与回滚
- 迁移脚本含 DDL（RENAME TABLE），MySQL 下非原子；**执行前对目标库全量备份/快照**为硬性前置，唯一安全回滚=整库恢复。
- CI 工作流若评审不通过，删除 `.github/workflows/migration-verify.yml` 即可，不影响其余交付。

## 七、待办（非阻塞）
- dev 实例残留一个无害的 MySQL 数据字典幽灵条目 `bone_0052_scratch`（验证过程 DROP 被中断所致，零数据零影响），需 `sudo mkdir`+DROP 或重启实例自愈，已交 DBA/运维。

## 八、审批请求
请架构师就 **`.github/workflows/migration-verify.yml`（L3）** 予以评审签批；其余文件为迁移脚本、校验脚本与文档，按既有流程合入即可。
