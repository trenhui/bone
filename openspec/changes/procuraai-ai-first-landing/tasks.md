# Tasks — procuraAI AI-first 落地

> 纪律：**R-1~R-6（DoR）未全部 ✓ 前，不进入编码任务（第 2 组起）**。DDL 落库属 L3，AI 只出草案，由架构师审批后执行。task 1.x 为门禁前置，可先做。
> 每项"验证"均为**机器可判定断言**；涉及门禁的必须给出「注入必红 / 还原必绿」双向证据，只有单向通过记为未完成。
> 本版（2026-11-13）按业界最佳实践加固判据，**任务编号与总数 27 保持不变**；新增的量化指标、合规映射与门禁语义见文末「验收判据总表（非任务）」。

## 1. 就绪门（DoR：R-1~R-6）

- [ ] 1.1 组织正式架构评审并冻结 HLD+LLD+D3-v2+OpenAPI/AsyncAPI 版本戳，登记入资产表（R-1）。验证：评审记录存在；冻结版本戳与资产登记逐项一致（脚本比对，缺失即红）。
- [ ] 1.2 产出 `t_proc_*` 字段级 DDL 草案并提交架构师 L3 审批（**AI 只出草案，不执行落库**）（R-2）。验证：DDL 草案 + 审批签字齐备；`git diff -- bone-init.sql` 为空（证明 AI 未直接改）。
- [ ] 1.3 模型选型决策记录 + 评测集从 27 例扩到 ≥200（含对抗/偏见集）+ **分任务通过线冻结**（R-3）。验证：计数脚本 ≥200；通过线文档含 clause detection / extraction / citation accuracy 与 severity-weighted 口径；冻结集零交叉校验通过。
- [ ] 1.4 采购域数据分级目录 + 标注规范 + 训练/验证/冻结集三分离 + override 回流状态机（R-4）。验证：目录与状态机文档齐备；注入一条交叉样本 → 冻结集零污染校验必红，还原必绿。
- [ ] 1.5 跨域 ACL 对接：Finance `InvoiceMatched/PaymentRequested`、Item/Inventory/Warehouse 只读端口、门户排期确认（R-5）。验证：对接人与节奏确认记录；**Pact 风格契约文件**落库且 provider verification 在 CI 有执行记录。
- [ ] 1.6 NFR 容量/SLO/告警数值按真实场景推算并冻结（R-6）。验证：SLO 文档进入验收判据；新鲜度阈值被 D6 引用而非硬编码在业务代码；告警规则与冻结值一致。

## 2. bone-ai 复用 spike（timeboxed ≤5 人日）

- [ ] 2.1 用 ≥3 份脱敏真实合同（含扫描件/表格、跨页条款）跑 `bone-ai-document` 解析 + `ClauseSplitter` 切分，验证抽取质量与条款边界。验证：spike 报告含解析样例、**跨页条款不截断断言**、失败模式清单；到期即出结论，不延期。
- [ ] 2.2 用 `bone-ai-orchestration` + `bone-ai-gateway` 跑通审查编排与降级。验证：端到端 spike 记录 + 降级路径证据；降级不得产出"已审查通过"。
- [ ] 2.3 按 `design.md` D2 决策树输出「是否/何时引入 `procuraai-runtime`」结论。验证：spike 结论文档含成本/收益对比与三分支判定；若判定为分支 3，必须有 ADR 且标注为范围变更待确认；**禁止以降低通过线换取通过**。

## 3. 供应商主数据（MDM）

- [ ] 3.1 建 `bone-procurement-supplier-mdm` 模块骨架（启动装配 `@EnableSqlRepositories`+`@Import`）。验证：模块编译 + 装配冒烟测试通过；启动日志可确认仓储已注册。
- [ ] 3.2 实现 Supplier/Vendor/VendorActivation/LE 实体（`TenantAbstractEntity`+`@Version`+`@Deleted`）。验证：`check-tenant-entity-declaration`/`check-soft-delete-declaration`/`check-id-annotation` 通过；注入业务层覆写 `tenant_id` 的负向用例必被拒绝。
- [ ] 3.3 Repository + Golden Record 去重（统一信用代码唯一键）+ 合并裁决留痕。验证：去重单测 + 唯一约束测试 + 合并后引用一致性测试；重复写入留痕可回溯。
- [ ] 3.4 Controller 返回 `ApiResponse<T>` + `@PreAuthorize('procurement:supplier:*')`。验证：返回信封契约测试 + 越权负向测试（无权限必 403，注入移除注解必红）。
- [ ] 3.5 Vendor 开通闸门接 `WorkflowStatusPort` + 数据质量最小维度校验。验证：闸门未过不得激活的负向测试；未激活 Vendor 被 AI 审查引用必被拒绝；必填/唯一/有效性违规写入被拒绝。

## 4. AI 合同审查 walking skeleton

- [ ] 4.1 合同上传 + 解析端点（调用 `bone-ai-document`，解析失败 fail-closed）。验证：解析失败转人工负向测试；`trace_id` 生成断言；OCR 低置信不产出"无风险"。
- [ ] 4.2 风险标注与审查报告（条款锚点可定位页/段、跨页不截断、严重度、依据、置信度；不回传 PII 原文；**分任务报告**）。验证：报告内容断言 + PII 不落盘测试 + 引用锚点不成立即转人工的负向测试；报告不得只给单一准确率。
- [ ] 4.3 HITL 复核流 + 置信度分层路由（低置信度/高风险条款强制人工，**有意义监督三条件**：依据可见、无超时自动通过、可推翻）。验证：无复核不得裁定测试；AI 不触发付款测试；低置信度被自动放行的注入用例必红；缺少推翻入口时验收失败。
- [ ] 4.4 审查留痕 + AI 参与披露（三态，未确定不压成未参与，版本串截断）+ **日志留存期可配置** + 降级/停用/推翻事件入同一追溯链。验证：披露三态测试 + 回溯测试 + 留存策略测试 + 运行时事件可回溯测试；日志不含 PII 原文。

## 5. AI 决策治理

- [ ] 5.1 能力三态 fail-closed（缺省 UNKNOWN，未确定拒绝自动决策）+ **具名责任人登记 + 一键停用/降级开关与演练**。验证：缺省态拒绝负向测试；停用后新请求不进入 AI 链路；停用演练记录（触发人/时间/影响/恢复）存在，缺失即未通过；部署态通过不等于放款/付款许可的断言。
- [ ] 5.2 人工最终决策权 + 推翻留痕 + **override 回流台账**。验证：人工推翻 AI 结论的用例；推翻样本进入回流台账且关联 `trace_id`；静默丢弃推翻样本的注入用例必红。
- [ ] 5.3 分任务评测门禁接入（precision/recall 并重、severity-weighted、关键负向=0、冻结集隔离、**门禁自身负向自检**）+ **实质变更触发回归重测** + drift 告警。验证：注入关键负向样本必红、还原必绿；整体合格但高风险漏检的样本必判负；模型/提示词/解析链路变更后自动重测且未过阻断发布；门禁恒绿视为失效。

## 6. 存量 `bone-app-purchase` 收敛

- [ ] 6.1 付款/审批 handler 加 `procura.purchase-*-handler.enabled` 开关，默认 false + **启动期 fail-closed 三态语义**（缺失/解析异常/非 true 均拒绝装配）+ 启动状态日志。验证：缺省即禁用测试；注入非法开关值 → 启动拒绝或 handler 不装配；启动日志逐 handler 打印生效状态；flag 关闭后新链路不可达。
- [ ] 6.2 明确 PO 真源=新 Java 聚合，存量 handler 收敛为 legacy 演示并文档化边界；回滚三条验证。验证：边界说明 + 无双写测试（存量 handler 写新聚合状态字段的注入用例必红）+ 存量行为回归全绿 + `t_proc_*` 不参与存量 SQL 的 schema 兼容检查。

## 7. 门禁与负向探针

- [ ] 7.1 N1–N6 六条负向探针逐条转红并留痕。验证：每条均有「注入-转红-还原-转绿」双向证据，缺任一方向记为未完成。
- [ ] 7.2 `tools/check_procura_alignment.py` 全套 + `--self-test` 双绿并在 CI 有 blocking 执行记录。验证：CI 运行结果可查；**本地 `check.sh` 绿不得作为 CI 绿判据**；新增判据若涉及三入口，须同步 `ci.yml`/`check.sh`/`check-procura-ci.sh` 与门禁常量（本期不新增）。

## 8. 集成验收

- [ ] 8.1 walking skeleton 端到端演示（上传→解析→标注→报告→复核→留痕）按 `design.md` D1-DoD 执行。验证：**脚本化** E2E 一次跑完 6 条断言 + 5 条负向必红 + 留痕可导出；明确记录"演示通过 ≠ 生产可用"（跨域 Mock、评测 ≥200、SLO 仍属后续验收）。
- [ ] 8.2 跨域 Mock ACL 契约化联调（staleness fail-closed 触发，阈值取自 R-6 冻结 SLO）。验证：陈旧快照阻断需新鲜数据操作的测试；契约文件与 provider verification 一致；生产 adapter 缺失被如实标注为 WBS 待办而非"已联调"。

## 验收判据总表（非任务，不改变任务数）

| 类别 | 判据 | 权威来源 |
|---|---|---|
| 价值 | 初审耗时降幅由 owner 冻结；价值假设不成立则不发布 | `design.md §成功度量` |
| 安全 | 高风险条款漏检（关键负向）= 0；误报率/引用正确率 ≤ R-3 冻结线 | `ai-decision-governance` spec |
| 合规 | HITL 覆盖 100%；推翻原因记录 100%；具名责任人 + 停用演练存在 | §合规映射矩阵 |
| 评测 | 分任务披露、冻结集隔离、模型版本/日期/评审人齐备、实质变更重测 | `ai-decision-governance` spec |
| 门禁 | CI blocking 权威 > 本地 check.sh > 文档门禁；门禁恒绿即失效 | `design.md` D7 |
| 存量 | 无双写、flag 缺省禁用、回滚三条可验证 | `design.md` D4 / Migration |
| 边界 | Mock 设计态 ≠ 生产端到端；不声称 EU AI Act 高风险合规 | `design.md` D6 / §0 |

## 任务依赖与 owner 边界

| 组 | 依赖 | 谁执行 | 状态 |
|---|---|---|---|
| 1.x DoR | 无（可先做） | owner / 架构师 / 各域负责人 | 产物已定义判据，**证据由 owner 产出** |
| 2.x spike | 1.1、1.3（模型与通过线口径） | 工程 + 算法 | 判据已定义，**未执行** |
| 3–8 编码与验收 | **1.1–1.6 全 ✓** | 工程 | **未启动，且不得越门** |
| R-2 DDL 落库 | L3 审批 | 架构师 | **AI 只出草案，不执行** |
| 生产跨域 adapter / Finance 契约 | R-5 | 各域 owner | 独立 WBS，非本 change 关闭项 |

## Workflow follow-up

- 归档前需完成 Verify 与一次最终集成审查。
- 生产 adapter 与 Finance 契约交付由 owner 排期，独立 WBS 行。
- 本版产物加固后需重新取守卫预检哈希并确认；当前 Run `continuation.mode: wait`，阶段推进由原执行器上下文完成，不在本会话重放。
