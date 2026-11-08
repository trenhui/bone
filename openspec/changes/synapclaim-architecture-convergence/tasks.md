## 1. 基线复核与单一真源

- [x] 1.1 复核当前工作区与验证基线（1290 tests / 12 道提交门禁 / 文档机检 19-19 / 错误码 52-52），验证：四条命令实际执行且输出留存于本 change 的验证记录 <!-- comet-task:6359dced-e410-4eec-8e9d-01673381eb98 -->
- [x] 1.2 建立 20 子域 → 7 限界上下文的机器可读逻辑映射单一真源，验证：JSON 可解析且门禁脚本读取该文件而非硬编码列表 <!-- comet-task:d9ad7c52-0c42-4cfc-9837-fbfac223be2c -->
- [x] 1.3 新增包归属门禁并固定 `quota` 归 Funds 的既有裁决，验证：门禁单测通过，且故意把 quota 改挂 Config 时门禁转红 <!-- comet-task:943f1548-affd-48b5-8b9d-5558729e487c -->

## 2. 决策图与流程层

- [x] 2.1 把 `DecisionGraph.canonical()` 固定为节点名、顺序与 `node_type` 映射的唯一真源并补机检断言，验证：新增单测断言 canonical 与文档口径一致，改坏任一处即失败 <!-- comet-task:942cd9b4-a6b9-4065-9cb6-b02a847b90a0 -->
- [ ] 2.2 落地 Stage → FlowFireService → Trigger → Condition 流程层骨架，Condition 默认走声明式白名单操作符，验证：单测覆盖正常流转与求值异常 fail-closed 两条路径 <!-- comet-task:548e125a-892b-43cc-9538-fac7f3c20bdf -->
- [x] 2.3 决策轨迹改为显式响应 DTO + Assembler，消除 `ApiResponse<List<DecisionTraceNode>>` 领域对象直出，验证：响应契约机检零违规且字段映射单测通过 <!-- comet-task:c4465b6f-6c64-4213-9b00-bd054fab074b -->
- [x] 2.4 把落地版 §7.3 改写为「现状 5 节点 + Phase B 待评审扩展阶段及前置清单」，同步总体技术方案 §0 事实基线，验证：`_check_numbers.py` 与 `check_all.py` 全绿，全文无「7 步为现状」表述 <!-- comet-task:1b12be98-fcea-4882-b1eb-eb1e7a72e1d3 -->

## 3. M5b 脚本沙箱

- [ ] 3.1 定义受限求值器端口与白名单操作符集合，验证：合法声明式条件求值单测通过 <!-- comet-task:40c29810-5462-42d4-b852-a309f5f57a82 -->
- [ ] 3.2 实现超时、执行步数与可访问标识符白名单，阻断文件 / 网络 / 反射 / 类加载，验证：四类越界负向探针全部被阻断且返回可诊断错误 <!-- comet-task:304e5a3e-10f3-496b-9606-b89c046a9175 -->
- [ ] 3.3 沙箱不可用或求值异常时 fail-closed 且不写业务状态，验证：异常注入单测断言无状态变更、无领域事件 <!-- comet-task:c9c9e44c-d781-40f8-8e37-2ec0366fabad -->
- [ ] 3.4 在诊断报告与总体技术方案登记 M5b 实际状态，验证：文档机检通过且措辞区分现状 / 目标态 <!-- comet-task:7a51ad8f-41bd-4f26-967a-bdb7566479a2 -->

## 4. 理算引擎三策略与三模块边界

- [ ] 4.1 抽出策略接口与报销 / 定额 / 津贴三策略实现及枚举分派，验证：三类分派单测通过，未知类型被明确拒绝 <!-- comet-task:79c5155d-cd3a-4a12-9a40-b4ae7e9c215a -->
- [ ] 4.2 用既有双轨对拍 / 缺口哨兵 / 变异鉴别三轨锁住金额等价，验证：`Adjustment*Test` 全绿，人为改动一处金额时对拍用例失败 <!-- comet-task:528a034b-592d-457e-89c6-bb9d1ce97eee -->
- [ ] 4.3 旧 `AdjustmentEngine` 标 `@Deprecated` 并提供回退开关，验证：开关两路均能执行同一批用例 <!-- comet-task:7278f9e3-0e9a-4157-a978-b7585c0800df -->
- [ ] 4.4 `synap-claim-sdk` 填纯 Java 契约，`adjustment-engine` 落纯函数内核，验证：两模块单独 `mvn -o -pl ... -am test` 编译与测试通过 <!-- comet-task:336715a1-e7dc-44d3-abd8-0c6cec96bb28 -->
- [ ] 4.5 新增依赖边界机检（SDK 零框架依赖、引擎不依赖 Spring / 元数据 SDK、app 单向依赖），验证：故意加入反向依赖时机检转红 <!-- comet-task:a2bc813b-c089-48ee-9d3d-50cb1d02579a -->

## 5. 响应契约与文档治理

- [x] 5.1 全量扫描 Controller 响应泛型并补齐剩余 `*Resp` / Assembler，验证：响应契约机检零领域对象直出、装配点唯一 <!-- comet-task:0483454a-eeb8-40d8-8138-38b6b7673033 -->
- [ ] 5.2 `AI技术方案.md` 瘦身，把版本历史与实施日志外置为独立记录文件，验证：`docs.manifest.json` / `AI-INDEX.md` 登记且 `check_all.py` 19-19 <!-- comet-task:0bc24bf3-e8d3-440f-8169-543306656130 -->
- [x] 5.3 回写诊断报告 §7/§8、整合历史 P38、文档地图与全部数字口径，验证：`_check_xref.py` / `_check_contract_drift.py` / `_check_ddl.py` / `_check_numbers.py` 全部通过 <!-- comet-task:0b509222-6ae2-4b55-9600-968a84653315 -->
- [x] 5.4 同步错误码登记与 zh-CN / en-US 文案（如有新增错误码），验证：错误码机检常量数 ↔ HTTP 映射数 ↔ 双语覆盖率 100% <!-- comet-task:67b7dd6d-974c-47c4-ba68-4b7e6dde7273 -->

## 6. L4 人工窗口交付物

- [x] 6.1 产出 M2 责任要素建议表设计与 R1–R8 待拍板清单，验证：design 附录与诊断报告有可逐项核对的条目，且 `scripts/migration/` 无本轮新增 DDL <!-- comet-task:f82a2d44-5216-415f-b74c-416cfcc08858 -->
- [x] 6.2 把 D4 物理包重构、05 契约 `node_type` 枚举扩展、`FraudScreen` 真实接入登记为独立后续 change，验证：目标态 / 设计态 / 现状三层措辞机检通过，无「已落地」误述 <!-- comet-task:dd07fd68-a218-488a-a407-39e8365819c6 -->

## 7. 全量验证

- [x] 7.1 执行 `mvn -o -q -pl bone-apps/synap-claim/synap-claim-app spotless:apply test`，验证：0 failures / 0 errors，测试数不低于基线 1290 <!-- comet-task:f3ee49eb-a585-45ca-9483-e2f3c618975c -->
- [x] 7.2 执行 `./scripts/check.sh`，验证：12 道提交门禁全部 PASS <!-- comet-task:f0853ff4-641a-45db-a9f5-472c54bfaaca -->
- [x] 7.3 执行 synapClaim 文档机检四件套、`check_all.py` 与错误码 / i18n 机检，验证：全部通过且无新增 baseline 例外 <!-- comet-task:3e175e24-4448-43ca-8338-081c9dcb115f -->
- [x] 7.4 执行 `mvn -o -q -pl bone-apps/synap-claim/synap-claim-app -am clean test` 核对「将被提交的树」，验证：clean 后编译与测试通过，排除增量编译假绿 <!-- comet-task:2afa2e76-9f60-4d46-a6a2-e647253af87c -->

## 8. 交付

- [ ] 8.1 确认 `git branch --show-current` 为 `dev`，按用户授权 `git add -A` 全量提交，验证：`git status --short` 干净，且 `git branch --contains <sha>` 包含 `dev` <!-- comet-task:93e2fadc-818b-4c6d-9e36-b2062abc9965 -->
- [ ] 8.2 执行 `git push origin dev`，验证：`git status -sb` 显示与 `origin/dev` 同步且远端包含该提交 <!-- comet-task:78b0302c-26a2-4fea-9fac-95d6e6e4c65f -->
