---
comet_change: claim-sign-auto-case
role: implementation-plan
canonical_spec: openspec
status: active
---

# 签收自动建案 — 实施计划

<!-- comet-task-authority: openspec/changes/claim-sign-auto-case/tasks.md -->

设计来源：`openspec/changes/claim-sign-auto-case/design.md`。行为契约：`openspec/changes/claim-sign-auto-case/specs/claim/sign-auto-case/spec.md`。执行方式：direct + tdd + standard。

## 阶段 1：迁移与模型

- 新增迁移 0145，扩展 `t_claim_sign_batch_item` 建案要素列 + 幂等唯一索引 + 终态断言；同步 `bone-init.sql`；本地隔离应用并核验 journal。<!-- comet-task-ref:bcd6f356-a315-432e-ae88-463ece8cd882 -->
- 同步 bone-init.sql 并本地隔离应用迁移，核验 information_schema 与 journal。<!-- comet-task-ref:50ab63eb-a191-4c78-bbcb-29329afa3cbb -->
- 扩展 `SignBatchItem` 领域模型与工厂（of/withCaseElements/linkCase），编译通过。<!-- comet-task-ref:0b31050d-a419-4461-b5a9-ee654fc77e8a -->

## 阶段 2：建案链路

- 新增 `ClaimSignCaseCreator` 订阅 `SignStatusChangedEvent(FINISHED)`，按 business_key 幂等创建报案+赔案并回写 created_case_id；TDD 先写「重复事件只建一次案」测试。<!-- comet-task-ref:bfbf0a63-5c3c-4a87-b9f8-a54efff39a1a -->
- 实现自动化流水线 accept→allocateToPreExam→startPreExam(automated)，失败写 AUTO_CASE_FAILED 轨迹且案件停在合法人工状态；TDD 先写失败降级测试。<!-- comet-task-ref:1677db65-751f-47fc-8b76-052a8672aac1 -->
- 明细「引用已有案件」与「携带要素建案」双路径；TDD 覆盖两路径。<!-- comet-task-ref:4ca3fd36-15a8-4c99-b67d-e5fab16fad5a -->
- 签收详情/列表 DTO 返回建案结果摘要（新建/跳过/待人工）；契约测试与浏览器实测。<!-- comet-task-ref:00e5734c-4cd2-49e4-943c-d317a5fdfe4f -->

## 阶段 3：数据回填

- 升级 `import_sign_batches.py`：为 15 条 TPA 明细写建案要素，按 business_key 幂等补建 12 个缺失案件；重复执行总数不变。<!-- comet-task-ref:3ac2ea4b-503a-4dcf-ac70-b01218eb255d -->
- 独立 8097 环境重建 jar 并重启，日志出现 `Started ... in`。<!-- comet-task-ref:183c7cf6-6636-4d73-af56-186967593476 -->

## 阶段 4：验证与收口

- 后端全量测试通过。<!-- comet-task-ref:c8945211-1b30-43c9-a78b-092af3ffe7b9 -->
- 全量门禁 `./scripts/check.sh` 全绿。<!-- comet-task-ref:a0702bf1-883f-483d-9a0d-2f2b2447a6f8 -->
- 浏览器实测签收→自动建案→自动分配初审→理赔进度页可查。<!-- comet-task-ref:ec751c2d-aaa0-4637-bbc8-1852b1d2dbd1 -->
- Comet Verify/Archive 通过并提交推送分支。<!-- comet-task-ref:dc1ed166-b4e2-47cd-b704-693444b16285 -->
