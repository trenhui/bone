## 1. 数据地基

- [ ] 1.1 追加增量迁移脚本建 `t_claim_task` / `t_claim_assign_rule` 与三条索引，末尾 `information_schema` 强断言 + `SIGNAL`，并验证 `python3 scripts/check-migration-manifest.py` 与迁移脚本语法检查通过 <!-- comet-task:7f7bb6e0-567f-466a-9e67-be547cca0f5b -->
- [ ] 1.2 同批把新脚本登记到 `migration-manifest.json` 末尾空位、补 `bone-init.sql` 建表块，验证清单 M1/M2/M3/M4/M11 判据全绿且 `order` 与文件名词典序一致 <!-- comet-task:60ec4f13-b861-489a-b48e-1cc44b151b97 -->
- [ ] 1.3 新增任务与规则实体，`@Column` 与活库 `information_schema` 逐列求差，验证 `mvn -o compile` 通过且无缺失列告警 <!-- comet-task:57dc571b-ce1f-4208-9c67-6b2fe5ae10a6 -->

## 2. 仓储与分配器

- [ ] 2.1 新增任务/规则仓储接口及每个方法对应的 SQL 模板（首行为语句关键字、SELECT 列对齐实体、含 `/*bone:tenant*/` 锚点），验证启动期无 `TemplateNotFoundException` 且单测可读回写入值 <!-- comet-task:40f2ed49-2148-49f4-9858-da4cffd5e7fa -->
- [ ] 2.2 实现规则求解与候选打分（固定顺序、优先级、`USER|DEPT|ROLE` 目标），验证命中用例与「候选集全不可用」用例分别断言已指派与入池 <!-- comet-task:aa736cb6-c0bb-43df-8083-7b6c1c3f0d5d -->
- [ ] 2.3 实现并发自领互斥（生成列唯一键 + `@Version`），验证并发双领取探针恰 1 成功、另 1 返回 `SC_CLAIM_TASK_CONFLICT` <!-- comet-task:c10ced95-4132-427e-bd32-9ff8335c221c -->
- [ ] 2.4 新增 `AUTO_ASSIGN` 开关与停写行为，验证开关关闭后新任务为入池形态且旧审核路径无报错 <!-- comet-task:d514d6e5-59ff-4cc2-9583-a3a235cb4179 -->

## 3. 查询与分页

- [ ] 3.1 实现「我的待办」与统一查询端点（功能权限 + 租户隔离 + 结果字段最小化），验证跨租户探针返回空且 `EXPLAIN` 命中 `idx_tenant_assignee_status` <!-- comet-task:e74ff3af-7335-47a3-906f-04043affc90b -->
- [ ] 3.2 服务端 `total` 改由同一 `COUNT(*)` 口径产出，验证造 137 条待办时 `total == 137`、逐页之和与 ID 去重均为 137 <!-- comet-task:f0735d50-b747-4df1-b616-8cb412264738 -->
- [ ] 3.3 前端去掉固定 50 条切片、ID 字段声明为 `string`，验证 `tsc` / `eslint` 与前端 ID 类型门禁通过 <!-- comet-task:fe6c7fde-7550-40b8-9e7c-b2f244e833b4 -->

## 4. 回填与灰度

- [ ] 4.1 在途案件归属回填脚本先探测后写入、多条候选即打印清单并中止，验证终态断言与「不编造指派」用例 <!-- comet-task:b4f97e0c-31fe-4977-adbc-33d4dc58c6b6 -->
- [ ] 4.2 灰度窗口统计 SQL 落地并接监控，验证命中率 ≥0.90 与回落计数按小时可观测 <!-- comet-task:ed1e4ea0-fecb-4fc6-9058-46f865bd2b37 -->

## 5. 验收与门禁

- [ ] 5.1 逐条跑提案 §13.5 批 1 五条可执行判据并留存运行期证据，验证报告写入 `docs/superpowers/reports/` 且五项均有实测数值 <!-- comet-task:4f0133b3-a90f-4b68-946c-1a0d402e4bbc -->
- [ ] 5.2 跑仓库原生全量门禁（含 `check.sh`、DDL 文档一致性、造数资产同步、E2E 影响分析），验证所有红点归零或明确归因为与本 change 零交集的基线项 <!-- comet-task:0ef895a4-2dad-43f5-9c40-0ace78e43673 -->
- [ ] 5.3 变异验证「前端固定切片」与「整表判断」两处判据确实会转红，验证门禁有判别力而非恒绿 <!-- comet-task:649c4240-35ed-40d1-a9fe-32156c4bb2a9 -->
