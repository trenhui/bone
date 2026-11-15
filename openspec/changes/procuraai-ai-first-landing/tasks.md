# Tasks — procuraAI AI-first 落地（re-scoped：MDM 模块首交付线，2026-11-14）

> **范围重定界说明**：本 change 原 Open 时登记了 27 个任务（DoR/spike/MDM/AI审查/治理/收敛/集成）。经核实，本次实际交付**仅供应商主数据（MDM）模块子集**（任务 3.1–3.4），已于 2026-11-13 推送（9 笔提交 `6f0ea427f..9b3c70c70`）且门禁全绿。其余 23 个任务（组 1/2/4/5/6/7/8）按原 `tasks.md` 纪律本就"未启动、属 owner/L3 程序级工作"，已迁出至 `deferred-backlog.md`，**不在本 change 关闭**。
> 本文件即本 change 的最终任务清单（4 项，全部已交付）。下游 build/verify/archive 仅对本清单负责。
> 纪律：**R-1~R-6（DoR）未全 ✓ 前不得进入编码**——本次交付在 DoR 未闭合情况下提前落地了 MDM 骨架，属已发生的工程决策，风险由 owner 在 backlog 中跟踪。

## 3. 供应商主数据（MDM）— 本 change 交付范围

- [x] 3.1 建 `bone-masterdata` 模块骨架（启动装配 `@EnableSqlRepositories`+`@Import`）。验证：模块编译 + 装配冒烟测试通过；启动日志可确认仓储已注册。 <!-- comet-task:493dbcea-4478-4831-977b-face2dd9c559 -->
- [x] 3.2 实现 Supplier/Vendor/VendorActivation/LE 实体（`TenantAbstractEntity`+`@Version`+`@Deleted`）。验证：`check-tenant-entity-declaration`/`check-soft-delete-declaration`/`check-id-annotation` 通过；注入业务层覆写 `tenant_id` 的负向用例必被拒绝。 <!-- comet-task:eb8cf1dd-dcdc-4484-b70b-bd8985e6bcbd -->
- [x] 3.3 Repository + Golden Record 去重（统一信用代码唯一键）+ 合并裁决留痕。验证：去重单测 + 唯一约束测试 + 合并后引用一致性测试；重复写入留痕可回溯。 <!-- comet-task:663f478b-5479-47f3-942d-31b8869f223a -->
- [x] 3.4 Controller 返回 `ApiResponse<T>` + `@PreAuthorize('procurement:supplier:*')`。验证：返回信封契约测试 + 越权负向测试（无权限必 403，注入移除注解必红）。 <!-- comet-task:bfdbcf5a-c5f1-4b85-9428-3da1f176fbfe -->

## 验收判据（本交付）

| 类别 | 判据 | 权威来源 |
|---|---|---|
| 构建 | `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 250 例全绿 | 已记录 |
| 门禁 | `[17/59]` 配置键 / `[52/59]` 构件白名单 / `[9a/59]` 分页命名已修复；procura 主门禁 10/10 + 自检 146/146 | `scripts/check.sh`、`check-procura-ci.sh` |
| 边界 | AI 不碰钱（放款/付款）为基线；不声称 EU AI Act 高风险合规 | `design.md` D4/D6 |

## 任务依赖与 owner 边界

| 组 | 谁执行 | 状态 |
|---|---|---|
| 3.1–3.4 MDM 交付 | 工程 | **已交付并门禁绿** |
| 1.x DoR / 2.x spike / 4.x AI审查 / 5.x 治理 / 6.x 收敛 / 7.x 门禁探针 / 8.x 集成 | owner / 各域 | **迁出至 deferred-backlog.md，独立 WBS** |

## Workflow follow-up

- 归档前需完成 Verify 与一次最终集成审查。
- 23 个未启动任务的交付由 owner 排期，见 `deferred-backlog.md`。
- "演示通过 ≠ 生产可用"：跨域 Mock、评测 ≥200、SLO 仍属后续验收。
