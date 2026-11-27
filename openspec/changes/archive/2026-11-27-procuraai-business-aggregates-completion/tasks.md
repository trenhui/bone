# Tasks

## 1. 投标聚合

- [ ] 1.1 实现 Bid 聚合的 DRAFT/SUBMITTED/WITHDRAWN/REJECTED 显式迁移、租户+RFx+供应商唯一与来源引用幂等；`BidTest` 覆盖到期提交、重复投标、撤回后恢复与重复来源投递，模块单测通过
- [ ] 1.2 实现 Bid 领域仓储端口与进程内适配器；`BidRepositoryTest` 验证租户隔离读回与重复来源只落一条，测试通过

## 2. 报价聚合与比价视图

- [ ] 2.1 实现 Quotation 聚合的明细、币种与有效期不变量；`QuotationTest` 覆盖过期报价与币种不一致报价被标记不可比，测试通过
- [ ] 2.2 实现只读比价投影与 AI 建议字段隔离；`QuotationProjectionTest` 断言 AI 推荐排序仅作建议字段且不触发任何授标/入围状态迁移，测试通过

## 3. 授标记录

- [ ] 3.1 实现 Award 聚合，只允许具名人工确认或拒绝；`AwardTest` 覆盖匿名、空白操作人、AI 建议授标被拒以及终态不可回写，测试通过
- [ ] 3.2 在 `SourcingController` 增加授标记录/比价相关的只读端点并复用既有权限码；`SourcingAwardEndpointTest` 验证请求体冒名租户/确认人被拒且状态不变，测试通过

## 4. CAPA 聚合

- [ ] 4.1 实现 CAPA 立项、责任人、期限、整改动作与关闭证据不变量；`CapaTest` 覆盖缺失责任人/期限拒绝立项、空白操作人或无证据关闭被拒，测试通过
- [ ] 4.2 实现 CAPA 超期转人工且不影响供应商准入、绩效或授信；`CapaOversightTest` 验证超期保留原期限并转人工跟进，测试通过
- [ ] 4.3 在 `QualificationController` 增加 CAPA 只读与具名关闭端点；`CapaEndpointTest` 验证登录态身份为唯一权威、请求体自报身份被拒，测试通过

## 5. 集成验收

- [ ] 5.1 运行采购后端模块测试与 procuraAI 主门禁，记录测试数、失败数与门禁结果，全部通过
- [ ] 5.2 运行 `comet classic openspec -- validate <change> --strict`，确认 5 个新增需求与全部场景通过严格校验
- [ ] 5.3 独立审查领域不变量、身份边界与字段白名单，修复全部 CRITICAL/IMPORTANT 问题后复测通过
