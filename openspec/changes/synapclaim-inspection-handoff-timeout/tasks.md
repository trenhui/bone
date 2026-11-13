## 1. 数据地基

- [ ] 1.1 追加迁移建 `t_claim_inspection_action` / `t_claim_user_agent` / `t_claim_task_transfer`（代理用生成列唯一键），末尾 `information_schema` 强断言 + `SIGNAL`，验证 `check-migration-manifest.py` 通过 <!-- comet-task:8c8b7549-e722-4dd7-ba68-ff6c0e2829c0 -->
- [ ] 1.2 清单末尾空位登记 + `bone-init.sql` 同批补建表块 + 实体 `@Column` 与活库求差，验证 M1–M4/M11 全绿且 `mvn -o compile` 通过 <!-- comet-task:f70e5081-1085-4fc7-ac95-fc3347f21f93 -->
- [ ] 1.3 三个新仓储接口与对应 SQL 模板同批交付，验证启动期无 `TemplateNotFoundException`、SELECT 列可读回 <!-- comet-task:66abc568-c909-44be-8f8b-b824582e1d05 -->

## 2. 抽检打回闭环

- [ ] 2.1 实现打回处置 append-only 留痕与重办任务生成，验证处置记录关联任务且不可改写 <!-- comet-task:dd954de3-7f11-42a6-9df1-d65364424da3 -->
- [ ] 2.2 实现已支付冲正前置闸门，验证未冲正打回被拒、冲正后打回成功且资金流水含冲正事实 <!-- comet-task:fd7b3808-04bd-45aa-bd76-407d02386baf -->

## 3. 请假 / 转交 / 代理 / 超时

- [ ] 3.1 实现代理生效区间与唯一性，验证区间外不生效、重叠代理被拒 <!-- comet-task:212ee9cd-98be-4ac1-8d77-73786260285f -->
- [ ] 3.2 实现转交留痕（append-only），验证历史转交不可更新 <!-- comet-task:150f9d18-dd2e-4c93-a9aa-d67fa85191f5 -->
- [ ] 3.3 实现超时回池扫描（幂等 + 监控计数），验证重复扫描不重复回池 <!-- comet-task:a7db7aea-4c61-4f09-8da6-f45a1de463b5 -->

## 4. 配置上屏与门禁

- [ ] 4.1 抽检比例迁入 `t_feature_toggle` 并上屏，验证页面值与配置读回值相等、ID 字段为 `string` <!-- comet-task:dac81d36-89a9-4f8c-8dd6-e6d51f90d2db -->
- [ ] 4.2 新增 DDL 文档一致性门禁并全仓跑正向审计（避免 package-info.java 那类假红），验证 `search()` + `finditer` 计数对账且变异能判红 <!-- comet-task:482b9b7a-566d-4f9a-96d7-d043585c3624 -->
- [ ] 4.3 新增 `INSPECTION_ACTION` 开关与只登记模式，验证关闭时不生成重办任务、不改案件状态 <!-- comet-task:3a59a161-7359-4563-803d-ffa12d536aaa -->

## 5. 验收

- [ ] 5.1 跑仓库原生全量门禁并留存运行期证据，验证报告写入 `docs/superpowers/reports/` 且红点归零或明确归因为零交集基线项 <!-- comet-task:24b65510-7335-4469-808d-84d2b619384d -->
- [ ] 5.2 变异验证「已支付未冲正被拒」「重复扫描幂等」两处判据确实会转红，验证门禁有判别力 <!-- comet-task:54ea429e-5a68-4387-8317-df86b2d3c07b -->
