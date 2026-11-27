## 1. 迁移与模型

- [x] 1.1 新增迁移 0145 扩展 t_claim_sign_batch_item 建案要素列（policy_no/plan_id/insured_name/insured_id_no/out_insure_time/claim_type/source_channel/business_key/case_status/created_case_id）并加幂等唯一索引，终态断言列齐全；以 `python3 scripts/check-migration-manifest.py` 通过为完成判据 <!-- comet-task:bcd6f356-a315-432e-ae88-463ece8cd882 -->
- [x] 1.2 同步 bone-init.sql 并本地隔离应用迁移，以 `information_schema` 查到全部新列且 `schema_migration_journal` 记为 0145 为完成判据 <!-- comet-task:50ab63eb-a191-4c78-bbcb-29329afa3cbb -->
- [x] 1.3 扩展 SignBatchItem 领域模型与工厂（of/withCaseElements/linkCase），以 `mvn -o -pl <claim-app> -am -DskipTests compile` 通过为完成判据 <!-- comet-task:0b31050d-a419-4461-b5a9-ee654fc77e8a -->

## 2. 建案链路

- [x] 2.1 新增 ClaimSignCaseCreator 订阅 SignStatusChangedEvent(FINISHED)，按 business_key 幂等创建 ClaimReport+ClaimCase 并回写 created_case_id，以单测「重复事件只建一次案」通过为完成判据 <!-- comet-task:bfbf0a63-5c3c-4a87-b9f8-a54efff39a1a -->
- [x] 2.2 实现自动化流水线 accept→allocateToPreExam→startPreExam(automated)，失败写 AUTO_CASE_FAILED 轨迹且案件停在合法人工状态，以单测「规则失败降级人工且不回滚案件」通过为完成判据 <!-- comet-task:1677db65-751f-47fc-8b76-052a8672aac1 -->
- [x] 2.3 明细「引用已有案件」与「携带要素建案」双路径：claim_id 非空走原逻辑、为空走建案，以单测两路径各有断言为完成判据 <!-- comet-task:4ca3fd36-15a8-4c99-b67d-e5fab16fad5a -->
- [x] 2.4 签收详情/列表 DTO 返回建案结果摘要（新建/跳过/待人工），以契约测试与浏览器实测可见为完成判据 <!-- comet-task:00e5734c-4cd2-49e4-943c-d317a5fdfe4f -->

## 3. 数据回填

- [x] 3.1 编写 import_sign_batches 升级版：为 15 条 TPA 明细写建案要素，按 business_key 幂等补建 12 个缺失案件，以重复执行案件总数不变为完成判据 <!-- comet-task:3ac2ea4b-503a-4dcf-ac70-b01218eb255d -->
- [x] 3.2 在独立 8097 环境重建 jar 并重启，以 `Started ... in` 日志与新端点可用为完成判据 <!-- comet-task:183c7cf6-6636-4d73-af56-186967593476 -->

## 4. 验证与收口

- [x] 4.1 后端单测与全量测试通过（无失败/错误），以 `mvn test` 汇总为完成判据 <!-- comet-task:c8945211-1b30-43c9-a78b-092af3ffe7b9 -->
- [x] 4.2 全量门禁 `./scripts/check.sh` 59/59 通过，以其退出码 0 为完成判据 <!-- comet-task:a0702bf1-883f-483d-9a0d-2f2b2447a6f8 -->
- [x] 4.3 浏览器实测签收确认后案件自动生成、自动分配初审、可在理赔进度页查询，以 Playwright 断言与截图为完成判据 <!-- comet-task:ec751c2d-aaa0-4637-bbc8-1852b1d2dbd1 -->
- [x] 4.4 Comet Verify/Archive 通过并提交推送分支，以远端 SHA 与本地一致为完成判据 <!-- comet-task:dc1ed166-b4e2-47cd-b704-693444b16285 -->
