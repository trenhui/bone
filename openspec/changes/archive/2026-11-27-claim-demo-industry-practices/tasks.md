## 1. 设计令牌与公共样式骨架

- [x] 1.1 确定四页共用的设计令牌（语义色/字号/间距/圆角/阴影/状态标签），验证标准：写一份令牌清单并能在四页 `:root` 中一致复现。

## 2. 升级理赔审核详情页 claim-detail.html

- [x] 2.1 理算信息区改为"初始/已占用/冻结/可用"多列对照架构，金额右对齐；验证：浏览器中该区块以多列卡片呈现且无错位。
- [x] 2.2 顶部摘要区前置赔付金额与"待关注"风险清单（发票/理算/领款/主被保人为空的必填校验项）；验证：打开页面首屏可见风险提示。
- [x] 2.3 保持既有渐进式披露（语义分组/折叠卡片/锚点导航/证件号脱敏）与真实数据；验证：真实字段值与脱敏切换仍正常。

## 3. 升级赔案管理列表页 claim-manage.html

- [x] 3.1 顶部新增七节点阶段化作业导航（签收→初审→录入→质检→审核→复核→推送）+ 配置化 Tab（待处理/处理中/已超时）；验证：阶段导航与 Tab 正常渲染、可切换。
- [x] 3.2 新增批量操作条与分配规则可视化元素，保持既有统计卡/搜索表单/表格状态色标/三个弹窗；验证：批量条可见，既有 12 项搜索与弹窗未回归。

## 4. 升级赔案影像详情页 claim-image-detail.html

- [x] 4.1 打磨清晰度分级徽标、影像分类规则浮层、关联发票浮层与底部工具栏的一致性（沿用统一令牌）；验证：与另两页视觉语言一致，浮层不遮挡。

## 5. 新增理算规则配置页 claim-rule-config.html

- [x] 5.1 三层矩阵表格（计划/险种/责任两级表头 + 合并单元格 + "添加险种/添加责任"引导）；验证：表头分组正确、合并单元格渲染正常。
- [x] 5.2 因子笛卡尔积矩阵：勾选"区分医院等级/医保/金额区间"动态生成矩阵行列，改动条件保留已填值；验证：勾选后矩阵行列变化，切换前后已填值不丢失。
- [x] 5.3 语法引导式公式编辑器：只读表达式串 + 函数/业务字段双栏点击输入 + 括号/运算符校验提示；验证：点击字段能追加到表达式，非法括号有提示，并标注"交互示意"。
- [x] 5.4 分区聚合校验弹窗（适用范围/赔付规则/控额规则/理算信息四区）；验证：弹窗四区完整、可视。

## 6. 统一互跳与集成验证

- [x] 6.1 四页之间加入页面互跳链接（管理列表↔详情↔影像↔规则配置），形成完整系统观感；验证：从任一页可跳到其余页。
- [x] 6.2 HTML 标签配平校验 + 无头浏览器逐页截图，确认四页均无错位/重叠/遮挡、中文正常；验证：四页截图人工检查通过。

## 7. 第二轮完善（views 分析剩余合理建议落地，同日 16:55）

> 来源：views 全面分析报告中尚未落地且合理的建议；用户"基于业界最佳实践对以上合理建议执行落地优化和完善"。沿用上轮裁定：不触碰卡在 open.revalidate 的 SDK 死锁，以已确认产物为依据直接交付。

- [x] 7.1 分阶段详情表单（报告点名盲点）：claim-detail 新增"作业环节视图"切换器（初审/录入/质检/审核/复核），各环节独立表单 schema、字段范围、专属校验、关注区块与操作按钮联动高亮；验证：五环节 Tab 可切换，元信息条与操作按钮随环节变化，截图无错位。
- [x] 7.2 危险操作二次确认强化（P0-3）：claim-manage 新增"批量拒赔"弹窗，列出具体发票号清单（赔案号/发票号/出险人/金额/状态）+ 不可撤销警示 + 拒赔原因必选 + 勾选后确认按钮方可点击；验证：弹窗清单完整、确认按钮初始置灰、勾选后可用。
- [x] 7.3 新增主体专属作业配置台（P0-2）：job-config.html 落地"标准 vs 专属"继承四手段——双栏对比（平台标准只读/主体专属生效）、规则性质列（通用/专属/专属·改）、显式同步弹窗（默认不启用、已有配置不受影响）、发布前声明弹窗（已有字段/新增变更差异）；含七节点流程链可点击查看各节点分配规则/跳转条件/schema；验证：双栏表对齐、弹窗可视、Tab/节点可切换。
- [x] 7.4 新增导入记录页（P2 补强）：import-record.html 批次列表 + 状态色标/进度条 + 逐行错误定位抽屉（行号/错误字段红色高亮/错误原因/定位源行/按类型筛选/下载错误报告）；验证：抽屉滑出正常、7 行明细可视化、遮罩与底部按钮无遮挡。
- [x] 7.5 六页互通与集成验证：所有页顶栏导航补齐 6 个链接；六页标签配平 ALL OK、互跳 6/6 完整；无头浏览器逐页截图（含抽屉/拒赔弹窗/阶段切换条交互态）确认无错位重叠；两个新页 git add 保护。

## 8. 第四轮：P0/P1/P2 合理建议落地（同日 19:42 起）

> 用户 `/comet 基于业界最佳实践对以上合理的建议都执行优化`。沿用既有裁定：本 change 的 SDK 仍卡在 open.revalidate 死锁，不碰守卫、不伪造收据、不 cancel；以已确认产物与 Git 提交为事实依据直接落地。

- [x] 8.1 P0 写动作幂等闭环：前端 `pay/refund/cancel/decide/autoApprove` 全部带稳定业务键（结算单快照 / 案件状态快照）；后端 `SettlementController.cancel`、`ClaimCaseController.decide|autoApprove`、`ReviewTaskController.decide` 接入可选幂等（复用 `IdempotencyService` + 30 天 TTL），并新增 4 个判别力用例（同键不二次执行 / 不同键各自执行）。
- [x] 8.2 P0 消除阈值第二真源：删除前端 `REVIEW_THRESHOLD=10000`，两个工作台改由 `GET /ai/config/governance` 的 `limits.dualReviewAmount` 驱动大额徽标/计数；加载失败时隐藏阈值相关标记并显式告警，不回落硬编码。
- [x] 8.3 P0 E2E 入 CI：新增 `claim-four-chain-ui-smoke`（blocking）job + `bone-frontend/e2e/claim-four-chain-ui-smoke.mjs`，受控信封覆盖报案提交/核赔决策/支付/上传四条链路，本地实测 15/15；真实全栈 `synap-claim-e2e.cjs` 仍需 MySQL+6 端口+provider，保持独立手动回归，未伪装成已入 CI。
- [x] 8.4 P1 结构化拒赔：`ReviewWorkbenchPage` 五要素拆为独立必填字段并随 `rejection` 提交（原因码仍走 `services/rejectReason.ts` 单一真源），后端 `ReviewDecisionReq/Assembler` 已支持，零后端改动。
- [x] 8.5 P1 金额口径统一：新增 `services/money.ts` + `MoneyText/MoneyInput` + `AsyncBoundary`，替换决策、理算、进度、资金、额度、保单、计划等页的 `toFixed` 拼接；显式 0 仍是 ¥0.00，缺值一律非金额展示；`formatMoney` 纯函数单测 3 例。
- [x] 8.6 P1 影像作业能力：`/evidence` 增三栏作业台（服务端清单驱动的分类树 + 预览缩放旋转/全屏 + 关联发票定位），保留 OCR 列并接通此前从未被调用的 `markParsed` 人工补录；快捷键在输入控件聚焦时不触发。大文件 SHA-256 移入 Worker，摘要口径不变，生产构建已验证 chunk 产出。
- [x] 8.7 P2 可发现性/可访问性：关键按钮补 `data-testid` 与 `aria-label`（资金动作、核赔确认、案件查询、影像工具栏/分类树），异步列表失败由一次性 toast 改为持久错误 + 重试。
- [ ] 8.8 P2 `/push-fail` 真实路由：后端 controller 目前**没有任何推送失败列表/重推/退回审核端点**；先上静态骨架会伪造"可用"语义，故登记阻塞，待服务端契约落地后同批实现（含鉴权+幂等+留痕）。
- [x] 8.9 P2 案件页头 SLA：DB 已有 `t_claim_case_sla`（`sla_due_at` / `legal_due_at` / `remaining_work_hours`），已在 11.7 打通到 `ClaimWithItemsProjection` 与案件 360；未物化时仍诚实显示「未物化」，不估算。

## 9. 第五轮：旧系统 views 二轮深挖（2026-11-21 20:20，只分析未改码）

> 用户 `/comet 深度分析 /Users/renhui.trh/创业项目/code/pk-tpa-saas/tpa-sass-vue3/src/views 还有哪些优点可以借鉴，为什么`。沿用既有裁定：Comet SDK 仍卡 open.revalidate 死锁，不碰守卫、不伪造收据、不 cancel。本轮为只读取证 + 结论留痕，**不新增实现改动**；三路并行深挖后对关键结论逐条抽样复核（Read 回读行号）。

- [x] 9.1 责任关系图 + 图论校验：`policyConfig/.../SetLiabilityRelationDialog/nextRelation/index.vue:49-288`（已回读 :168-226：DFS 环检测 `hasCycle` + 反向图最大深度 ≤3 + 唯一先赔方），直接防"先后赔付成环 ⇒ 理算死循环 / 同一费用两次占用额度"。新系统 `claimConfigApi.ts` 无责任关系实体。建议：**校验移服务端**，前端做即时反馈。
- [x] 9.2 费用项表达式 DSL 输入状态机：`SetExpressionDialog/index.vue:15-52,227-279,337-386`（已回读 :227-279：按函数栈/括号栈限制下一步可选 token）+ `RestricOutInsure.vue:271-289,341-363`。新系统有 `formulaHash` 归因快照但配置侧无公式确认环节。建议：状态机防错输入，**编译/执行必须服务端**，发布前给公式预览+diff。
- [x] 9.3 因子组合矩阵 + 区间边界重叠校验：`AdjustmentDetail.vue:353-526,583-601`（已回读笛卡尔积实现）、`PayPercent.vue:89-192`（起<止 + 四种开闭区间重叠）。防"新增因子清空已填比例"和"一费命中两档比例"。建议：结构变化保留旧值 + 后端编码/校验 valueList。
- [x] 9.4 历史案件关联 + 风险颜色标记：`components/Claim/HistoryCaseDialog.vue:30-41,57-70`（已回读颜色映射；`:72-74` `toHistoryDetail` 实为 `console.log`，**下钻是空壳，不可当优点借鉴**）。按证件哈希+姓名检索同人历史赔案，防重复索赔/欺诈；新系统无对应 service。建议：只借鉴查询+标记，PII 只回传假名化 ID。
- [x] 9.5 案件复制字段级决策 + 台账：`claimManage/copyClaim/components/copyDialog.vue:138-193`（已回读四项复制/不复制开关）+ `copyClaim/index.vue:160-224` 复制台账。防已赔金额/保司报案号/流水号被带到新案造成重复赔付。新系统无复制能力。建议：需后端幂等接口 + 强制备注 + 审计留痕。
- [x] 9.6 批量操作前逐张明示清单：`components/ClaimImage/CheckDialog.vue:77-104`（已回读：类名+序号清单）与 `BatchLiabilityDialog.vue:79-86`。不可逆批量删除/推送的防误操作闸门；新系统 `BatchImportModal` 有逐行预览但影像批量无。建议：纯前端可借鉴。
- [x] 9.7 增量差异保存 + 旋转真实落 OSS：`claimImageDetail/Edit.vue:793-891`（只提交 angle/clearType/imageType 变化项；先上传新图再落库）。防多人并发覆盖和"预览旋转了、推送还是原图"。建议：复用已有 `ERROR_CODES.CONCURRENT_CONFLICT`。
- [x] 9.8 上传文件名正则 + 配置化数量：`RenderEngine/base/Upload/ImageCheckDialog.vue:28-30,116-150`（`FILE_NAME_PATTERN` + maxCount 后端下发）。防特殊字符导致 OSS URL 解码错乱/保司拉影像 404。新系统仅 MIME+20MB 硬编码。建议：正则纯前端、`claimConfigApi` 承载配置。
- [x] 9.9 命令式 Promise 弹窗 / 脏检查 / 编辑锁：`claimManage/components/RePushDialog.vue:43-70`（`open(): Promise`）、`systemManage/eventManage/.../EventDetailDrawer/index.vue:121-138`（关闭前脏检查）、`optionConfig/.../DetailDialog.vue:178-213`（cloneDeep 取消回滚）。纯前端机制，新系统 `useConfirmAction` 只覆盖"确认后等待"，缺命令式编排与关闭前确认。
- [x] 9.10 伪优点剔除（已回读反证）：`systemManage/optionConfig/index.vue:218-223` 发布/版本记录按钮整段注释（版本化是死代码）；`dashboard/index.vue:3` 只有 `<h1>TPA-SAAS首页</h1>`；旧系统**没有** OCR 结果确认（`api/claimImage.ts:42` 只有布尔 ocr_flag）也**没有**上传进度（异步上传后到导入中心看结果）；团单"预览"只有 `console.log`。不因"像旧系统"而堆页面。
- [x] 9.11 禁止照搬清单复核：`plugins/permission.ts:21-40` cipherText 免登、`store/modules/user.ts:26` localStorage token、`claimDetail/index.vue:50-71` 本地草稿 `Object.assign` 覆盖服务端（已回读）、`directive/permission` 客户端权限、硬编码字典、`redirect/index.vue:11-14` 开放重定向、`v-html`。新系统 `Auth/AuthButton` + 后端 `@PreAuthorize` + `dicts.generated.ts` 是正确形态。
- [ ] 9.12 待拍板：以上 9.1–9.9 本轮**只分析未落码**。优先级建议：先做 9.6（纯前端零契约）、9.4/9.5（业务风控缺口，需后端接口）；9.1–9.3 涉及规则语义与服务端真源，应作为独立后端契约项目，不与 DEMO 修订夹带。

## 10. 第五轮执行：按取证结果落地可借鉴项（2026-11-21 20:40）

> 用户 `/comet 基于业界最佳实践对以上合理的建议执行优化`。沿用既有裁定：Comet SDK 死锁未碰守卫/未伪造收据。先做前后端双向取证再落码：**有契约的消费现成契约，无契约的登记阻塞，绝不造静态骨架**。

- [x] 10.1 文件名闸门（对应 9.8 可落地部分）：新增 `src/utils/fileName.ts`，口径**严格对齐**后端 `FileObjectKeyGenerator.assertSafeName`（空名/`..`/绝对路径/反斜杠/NUL/ISO 控制字符），不额外收窄字符集以避免误报；接入 `EvidenceUploadPage` 的 `beforeUpload`。3 例判别力单测（拒绝遍历与绝对路径、不误报 `#&%` 与中文括号）。
- [x] 10.2 不可逆操作的逐张明示清单（对应 9.6）：取证发现新系统**根本没有**批量删除/推送/OCR 入口（无契约可接），故不凭空造批量动作；把该实践落到现存的唯一单向动作——**锁定证据集**：Popconfirm 改为逐张 Modal，列出 `序号 + 类型 + fileRef`，失败时保留清单不清弹窗。`data-testid="lock-evidence-list"` 可机检。
- [x] 10.3 关闭前脏检查（对应 9.9）：新增 `useConfirmDiscard(): Promise<boolean>`（与确认写动作的 `useConfirmAction` 分工）。落在 `PolicyManagementPage` 编辑保单 Modal（`editForm.isFieldsTouched()`）与 `LiabilityConfigPage` 费用规则 Drawer（打开/保存后 JSON 基线对比，整批全量替换语义）。取消回滚原本已由 `preserve={false}` 天然成立，补的是"有未保存修改时点关闭"的拦截。
- [x] 10.4 历史案件关联（对应 9.4，发现现成契约）：后端 `ClaimSearchApplicationService.search` + `GET /api/v1/claims/search` 已按 `insured_id_hash` 检索且只回受控 `matchedKey`（无 PII 原值）。`Case360Page` 概览新增关联案件卡片：用详情返回的假名化 `insuredIdHash` 检索、排除当前案、三态边界 + 可重试；**不计算风险颜色**（后端无 colorMark，前端自算即第二真源），文案明确"未命中不等于无风险"。旧系统"查看历史案"按钮实为 `console.log`，未借鉴。
- [x] 10.5 顺手补齐上轮遗漏的金额口径：`Case360Page` 两处明细对账提示改用 `formatMoney`，缺值不再显示为 0 元。
- [x] 10.6 验证：typecheck 通过；前端全量单测 **15 文件 / 165 例全绿**（新增 fileName 3 例）；生产构建通过；四链路 UI 冒烟扩为 **20/20**（新增：非法文件名不发上传请求、锁定逐张清单、假名化 ID 检索且当前案排除、未保存关闭被拦截）。全部改动已 `git add` 暂存，**未提交**。
- [ ] 10.7 无契约项登记阻塞（取证结论，不伪造）：案件复制（全仓无 copy 端点/台账表，`is_copy` 仅在文档）、影像差异保存与旋转落 OSS（`EvidenceController` 无批量更新端点，字段 angle/clearType/imageType 不存在，`@Version` 有但未对外暴露）、上传策略配置（后端无 maxCount/文件名正则字段，`ai/config/governance` 不含）、责任关系图（无实体/表，现有 `prior_liability_uuid` 仅用于"识别并拒绝"）、表达式编译预览（仅 `formulaHash` 快照，无编译服务）、因子组合矩阵 valueList（全仓 0 命中）。这些需后端契约落地后同批实现。
- [ ] 10.8 批量删除/推送/OCR 本身：新系统既无入口也无契约，10.2 只借鉴"逐张明示"的确认机制；如未来要补批量动作，须先定义服务端批量端点 + 幂等键 + 审计，不能先上前端按钮。

## 11. 并行轮次：案件事实盘、SLA 打通与首屏拆分（2026-11-21 20:26 起）

> 与第 10 节为**并行会话**的独立轮次（同一 change，不覆盖第 10 节）。只做有契约依据的项：SLA 复用既有 `t_claim_case_sla` 读模型，无 DDL、无新端点；后端口径缺字段仍不伪造。

- [x] 11.1 核赔 KPI 计数改为服务端任务维口径（WAITING / PROCESSING / DONE 各以 `size:1` 取 total），「列表合计」改用服务端 `total`，大额卡改标「本页大额」；取不到显示「—」，不用 0 冒充。
- [x] 11.2 AI 结论卡上移到核赔主决策面：行内「AI 建议」抽屉复用 `AiSuggestionCard`（`scene=review`），与通过/拒赔/补件按钮不联动；修正卡内「仅本地不写库」与「已落库」的文案矛盾，最近一次裁决按真实写库结果展示 success / warning。
- [x] 11.3 案件 360 概览升级为「案件事实盘」：新增待关注事项（同意撤回 / 未登记、案件挂起、复核缺第二审核人、拒赔缺受控原因码），补充出险时间、事项阶段、外部状态、同意与挂起状态、第二审核人、拒赔原因码；金额统一 `MoneyText`，缺值不伪装成 0。
- [x] 11.4 案件 360 新增费用明细表：项目 / 编码 / 类别 / 数量 / 单价 / 金额 / 核定金额 + 合计行；明细合计与申请金额、核定金额不一致时显式告警（只提示、不自动改数）；无明细时明确空态而非空表。
- [x] 11.5 金额展示补千分位：`services/money.ts` 统一 `Intl.NumberFormat` 千分位 + 两位小数，并新增判别力单测；缺值语义不变（显式 0 仍是 `¥0.00`，未知仍是 `—`）。
- [x] 11.6 路由级代码分割：`routes.tsx` 21 个页面改 `lazy()`，`App.tsx` 在内容区加 `Suspense` + `RouteLoading` 兜底（不遮挡 DevNav，不整页闪白）。生产构建实测：主包 1,838.02 kB → 830.37 kB（gzip 594.10 kB → 276.92 kB），页面 chunk 13–28 kB，按需加载。页面保持具名导出，未改导出风格。
- [x] 11.7 SLA 字段打通（跨前后端，无 DDL）：
  - 后端 `findClaimWithItems.sql` 以 `(tenant_id, claim_id)` 唯一键 LEFT JOIN `t_claim_case_sla`，新增 `sla_due_at` / `legal_due_at` / `remaining_work_hours` / `last_refresh_at AS sla_refreshed_at` 四列；`ClaimWithItemsProjection` 增加 4 个字段，详情接口自动序列化，**无新增端点、无表结构变更**。
  - 前端 `ClaimWithItemsProjection` 类型补齐；案件 360 展示内部 SLA 截止 + 在时限内/已超期 + 剩余工时 + 物化时间，以及法定时限；未物化显示「未物化（刷新任务未产出，不做估算）」。
  - 待关注事项联动：内部 SLA 超期 → warning，法定时限超期 → error（监管风险）。
  - 验证：后端 `mvn -o -pl ... -am -DskipTests compile` + spotless 通过；前端 typecheck / lint / 165 用例 / build 全绿。
- [ ] 11.8 仍阻塞（不伪造）：8.8 推送失败无后端端点；案件列表（`ClaimHeadProjection`）尚未带 SLA 列，队列排序/超时筛选待服务端契约。均保持诚实占位，不前端自算。

## 12. 第六轮：三方对照后的合理建议落地 + 前后端联调（2026-11-21 21:07 起）

> 用户 `/comet 基于业界最佳实践对以上合理的建议落地进行优化和完善，并完成前后端功能的联调测试`。
> 沿用既有裁定：Comet SDK 仍卡 `open.revalidate` 的 outcome-unknown 死锁（claim 16:33，4.5h，证据目录不存在，沙箱禁 ps 无法核实 executor）——不碰守卫、不 mark-unknown、不 cancel、不伪造回执；以已确认产物与 Git 提交/真机联调为事实依据直接落地。
> 取证先行（前后端双向 grep）：只对**有现成后端契约**或**纯前端零契约**的建议落码，无契约的登记阻塞，绝不造静态骨架。

- [x] 12.1 取证结论（逐条对照上轮 P0/P1/P2）：① 运营驾驶舱——后端 `GET /api/v1/claims/analytics/summary`（`ClaimAnalyticsController` + `ClaimAnalyticsApplicationService`，`@PreAuthorize('claim:claims:read')`）**已上线且前端从未消费** ⇒ 可落地；② 清晰度三态批量标注 / 证据锚点页码定位 / 规则矩阵配置 / 推送失败路由——后端无契约（与 §10.7、§8.8 一致）⇒ 登记阻塞；③ README 对齐表第 56 行与代码漂移（`/evidence` 三栏作业台已落地）⇒ 纯文档真源修复。
- [x] 12.2 运营分析看板落地（唯一有真契约的新页面）：新增 `services/analytics.ts`（API + 纯函数 `analyticsWindow`/受控标签/缺值 `—`）+ `pages/AnalyticsDashboardPage.tsx`（三态加载、终态分布、降级数、平均耗时、影子率），hidden 路由 `/analytics` 从工作台治理空间深链进入（不占 IAM 菜单，避免单导航探针菜单全等判红），`App.tsx` HIDDEN_ROUTE_REQUIRED 对齐后端 `claim:claims:read`。**后端 scopeNote 原样上屏**，影子率明确标「≠出金率」，绝不改写成自动结案/出金。
- [x] 12.3 **真机联调暴露并修复 long-as-string 缺陷**（本轮最高价值发现）：经网关调 `GET /analytics/summary` 返回 `converged="31"`、`byDecisionResult` 值为**字符串**（Jackson 全局 long→string，与 `normalizeTotal` 同因），但比率/耗时是数字。原 helper 只认 `number` ⇒ 真机会把全部计数显示成 `—`，而 mock 用数字时 E2E 恒绿。修复：类型改 `NumericLike = string | number` + `toFiniteNumber`，helper 同时接受两种形态；E2E mock 改为字符串形态复刻真机，单测加判别力用例。教训固化：**「mock 绿」≠「真机对」，新接后端聚合端点必须先真机看返回体形状再定类型**。
- [x] 12.4 README 对齐表真源修复：`07-delivery/README.md` 第 49 行路由数 21→22，第 56 行 `/evidence` 状态由「分类树/关联发票未迁移」改为「OCR + 三栏作业台已在；清晰度三态无后端契约未迁移」。`tools/_check_demo_parity.py` 机检口径（覆盖 9 页 + 14 态 + claimStatus.ts）未受影响。
- [x] 12.5 验证（多层）：前端 typecheck 绿；单测 **16 文件 / 173 例全绿**（新增 analytics 8 例，含 long-as-string 判别力）；生产构建通过（新增 `/analytics` chunk）；四链路 UI 冒烟扩为 **26/26**（新增 6 条运营看板断言）；后端 `ClaimAnalyticsApplicationServiceTest` **3/3 绿**；**真机浏览器级联调**（起 claim:8087 + gateway:8888，无 mock，真实 JWT 经网关）`REAL_INTEGRATION=PASS`——渲染真值 `converged=31 / fallback=31 / shadowRate=0.0%`、scopeNote 在屏、0 页面异常、2×HTTP 200。联调后服务已停（8087/8888 均回 000，未留残留进程）。
- [ ] 12.6 仍阻塞（**部分已于 §13 取证推翻并纠偏**，保留原文留痕）：8.8 推送失败无后端端点（仍属实）；~~清晰度三态批量标注无端点/字段不存在~~（**已证伪，见 §13.1**）；~~证据锚点页码定位无结构化字段~~（**已证伪，见 §13.2**）；规则矩阵配置（无矩阵 valueList 端点）、主体专属覆盖、导入记录逐行错误、运营看板之外的驾驶舱指标（保单级 SLA 列等，案件 360 的 SLA 已打通见 §13.3）。仍需后端契约落地后同批实现。

## 13. 第七轮：文档漂移纠偏 + 原型运行时缺陷 + P7 门禁补齐（2026-11-22）

> 用户续跑「继续完成未完成任务」。沿用既有裁定：Comet SDK 仍卡 `open.revalidate` outcome-unknown 死锁——不碰守卫、不 mark-unknown、不 cancel、不伪造回执；以当前源码实证为事实直接落地。
> 本轮为**三方重新取证**（07-delivery 原型 / bone-claim-app 实现 / 原理赔 views）后的低风险修正：不新增后端契约、不造静态骨架，只做「文档真源对齐 + 原型 bug 修复 + 门禁判别力补齐」。

- [x] 13.1 **文档漂移纠偏（清晰度三态）**：§10.7/§12.6 登记的「清晰度三态无批量更新端点、字段不存在」已被当前源码证伪——后端 `EvidenceController.java:145-157` `POST /{claimId}/evidences/clarity` + `EvidenceApplicationService.java:185` + `Evidence.java:64-65` `clearType` + DDL `scripts/migration/0133_synap_claim_evidence_clear_type.sql:32`，前端 `evidence.ts:47` + `EvidenceUploadPage.tsx:331-337` 批量标注 + `EvidenceUploadPage.test.tsx:44-46` 三态用例，均已在 origin/dev（`b2d8a0f8e`）。README 对齐表第 56 行由「未迁移」改为「对齐」。
- [x] 13.2 **文档漂移纠偏（证据锚点页码）**：§12.6 登记的「决策 trace 无证据位置结构化字段」已证伪——`decisionExplain.ts:85-149` 定义 `EvidenceLocatorView.pageNo` + `evidenceLocatorsOf` 解析 `aiFacts`/`evidenceLocators` 去重 + `explainEvidenceLocators` 生成「影像第 N 页」，判别力单测 `decisionExplain.test.ts:85-149`（含旧轨迹无 pageNo 不产锚点）。口径如实保留：坐标来自决策 trace output，非独立端点。
- [x] 13.3 **文档漂移纠偏（案件 SLA）**：§11.8 只登记「案件列表未带 SLA 列」属实，但案件 360 的 SLA 已打通——`Case360Page.tsx:192,228-231,329-353` 读 `slaDueAt`/`legalDueAt` 并标「已超期/在时限内」；该部分不再列为阻塞。列表级 SLA 列仍待服务端契约。
- [x] 13.4 **原型运行时缺陷修复**：`evaluation-report.html:324-333` 存在从 `form-config.html` 复制残留的 `FIELDS`/`pickTpl`/`pickField`/`pubAck` 监听，但本页无 `id="pubAck"` 元素（`pubAck` 恒 `null`），加载即抛 `TypeError`、阻断后续脚本。删除该死代码块并留注释说明，命令面板/toast/指引抽屉仍由公共脚本提供。`node --check` 通过。
- [x] 13.5 **P7 门禁补齐**：`_check_demo_parity.py` 自称 P1–P8 但**无 P7 判据**——README:45 承诺九页共用 UX 骨架（互跳/命令面板/指引抽屉/toast）却无任何机检。新增 `check_ux_skeleton`：九页逐一校验公共锚点（`uxCmd`/`uxCmdList`/`uxCmdInput`/`uxFab`/`uxDrawer`/`uxToast`/`uxMask`）+ 互跳覆盖全部 9 页；缺页时交由 P1 计、不重复计数。自检探针 10→13（新增锚点缺失/互跳缺页/缺页不重复计数三例）。同步 `check_all.py:71` 标签与治理规约 §五登记。
- [ ] 13.6 仍阻塞（真无契约，不伪造）：8.8 推送失败列表/重推/退回审核端点全仓 0 命中；案件复制（无 copy 端点/台账表）、责任关系图（无实体/表）、表达式编译预览（仅 formulaHash 快照）、规则矩阵 valueList（前端 0 命中）、主体专属覆盖、导入记录逐行错误、列表级 SLA 列。这些需后端契约落地后同批实现，不以静态骨架伪造可用。

> 来源：原理赔 views 深挖另识别 A2 笛卡尔积值保留 / C13 快捷键流 / C15 差分保存 / B8 行内编辑回滚等机制，但均需后端契约或引入新交互栈，不属于本轮低风险纠偏范围；已登记于 §9/§10，待独立契约项目推进。

## 14. 第八轮：07-delivery 全量诊断后的建议落地 + 两条误判纠偏（2026-11-23）

> 用户 `/comet 基于业界最佳实践对以上合理的建议都执行优化`。沿用既有裁定：Comet SDK 卡 `open.revalidate` outcome-unknown 死锁——不碰守卫、不 mark-unknown、不 cancel、不伪造回执；以产物与 Git 为事实直接落地。
> 铁律：取证先行——只对**有真后端契约**或**纯前端零契约**的建议落码，无契约登记阻塞；否定结论逐行回读复核（本轮据此推翻两条初版误判，避免"把对的改错"）。

- [x] 14.1 **429 Retry-After 透传**（对应运维手册限流承诺）：共享 `packages/shared-services/src/apiClient.ts` 错误拦截器在 429 时把 `Retry-After`（兼容 AxiosHeaders.get 与纯对象两形态）透传到 `error.retryAfter`。**刻意不自动重试**——多数写操作无幂等键，自动重试会重复执行；退避/重试策略归消费端。新增 2 例判别力测试。验证：shared-services `npm run build` ✅ / `npm test` 13/13 ✅。
- [x] 14.2 **同意撤回前端操作入口**（对应 DPA PIPL 第15条）：取证确认后端真端点 `POST /{id}/consent/revoke`（ClaimCaseController:370，`claim:claims:write`，幂等）已存在，但前端只有状态展示无操作。新增 `claimApi.revokeConsent()`；Case360 概览"同意状态"旁加 `AuthButton` + 二次确认（明示"撤回后处理类动作 422、本系统内不可撤销"）+ 成功后 reload；已撤回则不渲染入口。新增 `revokeConsent.test.ts`（路径/无请求体/幂等 false 透传）2 例。验证：typecheck ✅ / 单测 2/2 ✅。
- [x] 14.3 **申诉注释口径纠偏**：初版诊断称"48h/14日双口径冲突"，逐行取证反转——真源是 **14 自然日受理窗口**（`AppealRecord.APPEAL_WINDOW=Duration.ofDays(14)`，专为保守覆盖对客"签收后10个工作日"），"48h"是转人工案件响应 SLA（规则书 R-MAN-02），维度不同、前后端一致。字典键 `APPEAL_WITHIN_48H` 名不副实但文案"14日内申诉"正确，**未改字典/页面**（改即制造 bug）。唯一真缺陷=`AppealController` 类注释/Swagger 仍写"48h内受理"，修正为14天并注明区分，纯注释零行为变更。
- [x] 14.4 **路由数误判纠偏**：初版诊断称"README 22 vs 实测23"，取证反转——真值 **22**（README 正确）。`grep -c "path:" routes.tsx` 得23是把接口类型定义 `path: string;`（routes.tsx:101）计入；`CLAIM_ROUTES` 数组（128-166行）实际22条。**未改 README**（改即制造 bug）。教训固化：`grep -c` 不区分定义与实例会产假结论，与 §13 的"否定结论用 cat 复核"同源。
- [x] 14.5 **诊断报告 V2.1 更新**：`doc/ai-product/synapClaim/智能理赔-三方诊断与优化建议.md` 纠偏 §1/§2/§4/§6/§7 的路由数与申诉表述，新增 §9 本轮落地记录（已落地 / 误判推翻 / 阻塞登记 / 验证汇总）。
- [ ] 14.6 仍阻塞（真无契约，不伪造）：快赔 ≤500 自动通过阈值（全仓仅 `DUAL_REVIEW_AMOUNT=10000` 与 config_json 的 `autoAmountLimit`，无 500 常量，需服务端定义阈值+口径+审计）；沿用 13.6 阻塞清单（推送失败端点/案件复制/责任关系图/表达式编译/矩阵 valueList/列表级 SLA 列）。DEMO 66% 硬编码属原型资产口径，需先裁决 demo/ 旧资产处置（README 声明已删但文件仍在）再定改数字还是清理，本轮不擅动。

## 15. 第九轮：前后端联调实证（2026-11-23）

> 用户 `/comet 基于业界最佳实践对以上合理的建议落地实现，并完成前后端功能的联调`。
> 环境已就绪：shell:3000 + claim 微应用:3013 + 理赔后端:8087 + 网关:8888 + MySQL/Redis。新建 2 个带 consentRecordId 的 E2E 联调案件，不碰真实业务数据。

- [x] 15.1 **同意撤回 HTTP 全链路 6 步对照通过**：新建 E2E 案件（consent 已登记/未撤回）→ 第一次撤回 `data=true` → 第二次幂等 `data=false` → 撤回后 `POST /{id}/decision` 得 `422 SC_CLAIM_CONSENT_WITHDRAWN` → 撤回后 `POST /{id}/hangup` 收尾动作仍 200 → 重查 `consentWithdrawnFlag=1` 持久化；无 token 直调得 `401 GW_UNAUTHORIZED`（端点 `claim:claims:write`）。
- [x] 15.2 **浏览器 UI 端到端通过**（Playwright 无头 Chrome，真实登录）：`/sclaim/claim/detail` 概览"同意状态=已登记"旁渲染"撤回同意"按钮 → 点击出二次确认弹窗（PIPL15/422后果/不可撤销/需重签）→ 确认发真请求 200 → 状态变"已撤回"、按钮消失。截图 `/tmp/claim-e2e-screens/case360-{before,confirm,after}.png`。2 个 404 为 SIGNING 阶段 `/review/tasks/{id}/execution` 空态预取，非本轮引入。
- [x] 15.3 **429 Retry-After 联调**（环境无真实限流，route 注入 429+Retry-After:7）：前端红色 Alert 正确显示限流文案，AI 结论卡 fail-closed 独立展示；detail 请求 2 次系 React.StrictMode dev 双渲染（唯一调用点 Case360:96），非自动重试。截图 case360-429.png。
- [x] 15.4 **联调新发现 → §18 已落地**：`error.retryAfter` 曾透传但无消费端（`resolveErrorMessage` 只按码取通用译文"请稍后重试"）。用户连续要求"完成所有合理建议"后于 §18 落地：新增限流专用 i18n key + Retry-After 秒数解析，浏览器实测显示"请约 7 秒后重试"。
- [x] 15.5 **留痕**：诊断报告新增 §10 联调实证；本轮未新增业务代码改动（15.1-15.3 为验证既有落地，15.4 为发现登记），git 暂存仍是第八轮 8 文件。

## 16. 第十轮：剩余建议取证 + 提交门禁基线债务修复（2026-11-23）

> 用户 `/comet 基于业界最佳实践对以上合理的建议执行优化`。铁律不变：只对有真契约/纯前端零契约的项落码，无契约登记阻塞，否定结论逐行回读复核。

- [x] 16.1 **剩余建议逐条取证，无可落地项**（诊断报告 §11.1）：置信度三档——后端 DTO 未映射 `aiConfidence`，`decisionTier` 明确"内部分档不对外/不解释为业务话术"，包装成置信度即制造口径冲突⇒阻塞；审核四区域证据高亮——`EvidenceLocator` 只有页码行号无坐标⇒阻塞；分步金额明细——trace Adjust 节点只有 `amount`⇒阻塞；`error.retryAfter` 秒数消费⇒待裁决（§15.4）；两个未消费 GET 端点（ai-eval-sample-index 自述易失、claim-assistant/tools 工具元数据）无业务价值⇒不接。经十轮，§7 "有真契约未落地"项已全数消化，剩余均属契约缺失或伪缺口，不造静态骨架。
- [x] 16.2 **G42 门禁污染根治**：`check-sql-entity-column-coverage.py` 的 `os.walk(REPO)` 未排除被 gitignore 的 `.worktrees/`，两个并行会话 worktree 的旧 SQL（缺 `clear_type`/`last_sync_version`）被误判为新增缺列，拦下本批提交。`SKIP_DIRS` 增补 `.worktrees` 后隔离复跑 58 模板 0 缺口——根治共享主树被他人 worktree 污染门禁的问题。
- [x] 16.3 **采购模块 ID number 修复**：`bone-procurement-app/.../qualificationApi.ts` 的 `id`/`tenantId` 由 number 改 string（后端雪花 ID 下发 string，number 会静默截断；同文件 findOne/reviewOcr 入参本就是 string）。[10/59] 复跑 23 处全部登记、0 违规。
- [x] 16.4 **dev 基线门禁自测债务（登记，不在本次范围）**：纯 dev HEAD（`32de702c6`，未应用 patch）复跑 `check.sh` 的 [42/59] 有 10 个失败（本批分支反而 8 个），根因是并行会话加 `check-sclaim-naming` 漏更新 `scripts/registry/checks.json`（counts 62 vs 63）等注册表漂移 + 活库 `iam_menu` 300≠种子 288 环境漂移。属仓库级 L3 工程债，修复会动多人共享的 check.sh 注册表，与理赔改动无关——如实登记，不假装 check 全绿。另前置检查（typecheck/204+13 单测/procura 10-10/E2E impact/G42+ID 隔离复跑）全绿，证明本批 8 改动本身干净。
- [ ] 16.5 **待架构师/并行会话处理**：dev 基线 [42/59] 门禁自测 8–10 例（checks.json 注册表漂移、verify/waivers/catalog 同步、ai_native_readiness）+ 活库种子漂移；本轮不跨领域修，避免与并行会话大面积冲突。

## 17. 第十二轮：P0-3 资产漂移闭环（2026-11-23）

- [x] 17.1 **取证**：README:29 声明旧 Demo 已删但 `demo/` 两 HTML 仍在（1597/819 行）；`_check_demo_parity.py` 只扫 `prototypes/` 抓不到；独特能力已迁移进 9 页原型、无功能引用、manifest 未登记——删除前置满足。
- [x] 17.2 **删旧资产**：`git rm` 两个过时 HTML（git 历史可恢复），清空遗留 `demo/` 目录。
- [x] 17.3 **P9 机检防回归**：`_check_demo_parity.py` 加 `RETIRED` 清单 + `rglob` 全目录扫描，退役文件重现或 README 缺"已删除"声明即判红；self-test 探针改动态计数 + P9 三探针（临时目录构造假树，不碰真实文件）。变异验证：删前判红 2 处、删后转绿。
- [x] 17.4 **同步登记**：`check_all.py:71` 标签 P1–P9、治理规约 §五、诊断报告 §5/附录删除前快照加注。


## 18. 第十三轮：剩余 P0/P2 建议深挖闭环（2026-11-23）

- [x] 18.1 **P0-1 口径取证**：诊断报告长期记「前端未加快赔阈值」，实读发现 `ClaimSubmitPage.tsx:250/275` 早已写死「≤500 元可自动通过」「30 秒内返回」确定性承诺——文档实现漂移。后端真源 `autoAmountLimit` 按租户配置+封顶 1 万，无 500 常量；30 秒权威口径为 P95≤30s 目标非承诺。
- [x] 18.2 **口径改写**：两文案提为导出常量 `FAST_TRACK_NOTICE`/`DECISION_LATENCY_NOTICE`（单一真源+可测），≤500→「阈值由服务端策略配置、不预设具体金额」，30 秒→「目标 P95 30 秒内返回（示意，非时效承诺）」。
- [x] 18.3 **P2-8 路由机检补全**：`_check_demo_parity.py` 新增 P10，读 `routes.tsx` 的 `{ path: '…' }` 核对条数（不计 `path: string;` 类型定义）+ 映射表第 2 列落点路由真实存在 + fail-closed；self-test 20/20，三类变异全判红（条数漂移/引用不存在/routes 空）。同步 check_all.py 标签 P1–P10、治理规约 §五、脚本 docstring/OK 提示。
- [x] 18.4 **排除项登记**：原型数字（P0-3 已闭环）、单独同意勾选（上游采集、造即假合规）、擦除入口/字典版本（后端无契约）、拆页/i18n/a11y/保存视图落库（高风险或无契约）——均不落码。
- [x] 18.5 **验证**：typecheck RC0（含修复并行会话双号字段致的 Case360FeeDetail mock 缺字段）；ClaimSubmitPage 单测 3 例新增、全量 26 文件 228 例全绿；变异验证 3 条口径测试全红；check_all 本轮 [17][18] 全绿，2 红为 dev 存量债务（manifest M3/ruleset A9）。


## 18. 第十四轮：Retry-After 秒数消费落地（2026-11-24）

> 用户连续要求"基于业界最佳实践对所有合理建议落地 + 完成联调"+"继续完成未完成的任务"。
> **并行会话安全**：当前主树有 40+ 其他会话在途改动（bone-ai / claim 多页面 / prototypes），本轮只碰自己的 4 文件 + 2 文档，逐路径 `git add`，严禁 `git add -A`。经取证：§7 有真契约项已全数消化（§16.1），阻塞项重新扫描仍无契约（推送失败/复制/快赔500/责任图/SLA列），唯一可落地项=§15.4 遗留的 retryAfter 秒数消费。

- [x] 18.1 **Retry-After 秒数消费落地（§15.4 结案）**：`resolveErrorMessage` 原只按 errorCode 取 i18n 通用译文"请稍后重试"，透传的 `error.retryAfter` 成死字段。方案：新增限流专用 key `COMMON_RATE_LIMITED_RETRY`（zh「请求过于频繁，请约 {{seconds}} 秒后重试」/ en「...about {{seconds}} seconds」），`resolveErrorMessage` 对 `COMMON_RATE_LIMITED`/`GW_RATE_LIMITED` 且 Retry-After 为纯数字秒（1–86400）时优先用专用 key；HTTP-date 形态因无法可靠换算（客户端时钟不准）保守回退通用译文；**不在共享层硬编码中文后缀**（文件头明确要求避免英文界面出现中文）。改动 4 文件：shared-utils `errorMessage.ts` + 中英文言包、claim-app `retryAfterMessage.test.ts`（7 例判别力：数字秒/网关码/HTTP-date回退/非限流码不带秒/无秒零回归/英文文案无中文/异常秒数过滤）。
- [x] 18.2 **联调验证秒数真实可见**：route 注入 429+Retry-After:7 → Case360 页面文案由"请稍后重试"变为 **"请求过于频繁，请约 7 秒后重试"**（截图 `/tmp/claim-e2e-screens/case360-429.png` 已更新）；AI 结论卡仍 fail-closed 独立展示、无自动重试。验证：shared-utils `npm run build` ✅ / 新测试 7/7 ✅。
- [x] 18.3 **精确 git add**：只暂存本轮 4 文件（`git add -- <精确路径>`），其他会话 40+ 在途改动一字未动。

## 19. 收口状态（截至 2026-11-24）

- 诊断报告 §7 全部"有真契约未落地"建议已在前 13 轮消化完毕；剩余均为**真无后端契约**（推送失败端点、案件复制、快赔≤500 阈值、责任关系图、表达式编译、矩阵 valueList、列表级 SLA 列）或**仓库级门禁基线债务**（§16.5），不可伪造、不属本 change 单人可解。
- §15.4 本轮结案后，本 change 已无"可由单人落地"的剩余建议。

## 20. 第十五轮：完整前后端真机联调 + 两处环境/脚本债务修复（2026-11-24 18:22 起）

> 用户 `/comet 基于业界最佳实践对以上所有合理的建议落地实现，并完成前后端功能的联调`。
> 沿用既有裁定：Comet SDK 仍卡 `open.revalidate` running 等待态（resume-probe=ask_user/low；`state next`=wait executor）——不碰守卫、不 mark-unknown、不 cancel、不伪造回执；以产物与真机联调为事实直接落地。
> 取证先行：阻塞清单三项复核仍无契约（推送失败仅 analytics 计数、列表 SLA 列 0 命中、快赔 500 常量 0 命中）⇒ §19 结论属实，不伪造。本轮增量聚焦用户明确要求的「完成前后端功能联调」。

- [x] 20.1 **完整真机联调首跑 28/39**：跑仓库 `bone-frontend/e2e/synap-claim-e2e.cjs`（真实服务栈、零 mock、经网关 JWT、Playwright Chromium）。A 段 API 15/15 + C 段循环对账 3/3 全绿；B 段 UI 11 项 FAIL，根因集中在 qiankun 从未挂载（hits 空、entryCards=0、url 停在根）。
- [x] 20.2 **脚本过期路径纠偏（工程债务，非产品缺陷）**：逐层取证——shell 用 BrowserRouter，`CLAIM_MENU_PREFIX=/sclaim/claim`（App.tsx，明确「不保留任何历史别名」），默认落点是 hidden `/workbench`（routes.tsx isDefault）；脚本却用废弃别名 `/claim/review`（被 302 弹回根）+ hash 断言 `#/ai-governance`。真机验证正确路径 `/sclaim/claim/workbench`（我的工作台 4/4 标记、22 卡片）与卡片深链 `/sclaim/claim/ai-governance`（6 标记全中）。修正脚本 3 处路径后 UI 段 10/11 转绿，总 38/39。
- [x] 20.3 **联调暴露旧 jar 缺陷（真红，非误报）**：唯一残留 FAIL「5 个治理端点可用」=治理页「工程证据底座」显示「数据不可用」。抓真实响应定位 `GET /api/v1/claim-ops/engineering-evidence` 404；双向取证发现后端源码有 `EngineeringEvidenceController`（commit 540229a1f，11-20）但运行中 jar 是 11-19 构建、jar 内无该类——**构建产物过期**，非代码缺陷。用 `-am clean install` 跨模块重建（单模块构建缺 sdk/adjustment-engine 本地依赖）+ 重启 8087（`Started in 3.736s`），端点恢复 200 返回完整判据。
- [x] 20.4 **联调结果 39/39 PASS**：重跑全链——A 段 15/15（含负向探针 401/400、minSamples 下限不可绕过、演练零副作用）+ B 段 18/18（qiankun 挂载、深链、探活默认零请求、判词不伪造、参与率独立对账、数据可用）+ C 段 3/3（静态判据 vs 运行期就绪清单双向一致）。本轮 provider 真探活返回 `REACHABLE`（deepseek-flash，httpStatus 200 + 证据束），比上轮 `NOT_CONFIGURED` 更真实。产物 `outputs/claims-e2e-result.json` + 3 张截图。
- [x] 20.5 **全量回归**：前端 typecheck RC0、单测 34 文件 266/266 全绿；后端 `EngineeringEvidenceControllerTest` 2/2 + `EngineeringEvidenceServiceTest` 4/4 全绿。改动仅 1 文件（`bone-frontend/e2e/synap-claim-e2e.cjs`），未碰业务源码。
- [ ] 20.6 仍阻塞（沿用 §13.6/§14.6，真无契约不伪造）：推送失败端点、案件复制、快赔≤500 阈值、责任关系图、表达式编译、矩阵 valueList、列表级 SLA 列。本轮未改其状态。

## 21. 第十六轮：企业场景模拟数据 + 全业务链端到端串联（2026-11-24 18:37）

> 用户「基于企业真实场景构建模拟数据，对智能理赔系统前后端所有功能进行端到端的联调测试，把整体智能理赔流程串联起来」。沿用既有裁定：Comet SDK 卡 open.revalidate 死锁——不碰守卫/不 mark-unknown/不 cancel/不伪造回执。

- [x] 21.1 **场景数据生产化**：复用 `fixtures.cjs`（词表真源 wordlist.json + 可种子 PRNG），同 STAMP 同数据、金额非圆整、Σ 明细守恒；经网关真实写 API 落库（非直插 DB），额度夹具只增不减整备；MySQL 直查第二证据源对账。
- [x] 21.2 **全业务链 67/67 PASS**：`claim-full-chain-e2e.mjs` 七分支串联——小额直付 / 大额双人复核（SoD）/ 拒赔维持 / 申诉推翻+挂起恢复 / 补件循环 / 决策图+自动通过 / 后督抽检；外加查询分页、假名化检索、保单聚合、MySQL 落库对账。真实落库 7 案件（FINISHED×2 等）。
- [x] 21.3 **前端数据串联**（`tools/poc/bone-fullchain-ui.cjs`）：qiankun 挂载下逐案 360 做 API↔UI 金额对账（243.42/22233.58/122.83/565.19 命中）；核赔列表读真实分页、我的工作台导航渲染。console 两类噪音（execution 404 已 allSettled 兜底、antd Spin tip 警告）取证定性为已知无害，判据纠偏后聚焦真错误。
- [x] 21.4 **全功能页面巡检 19/19**（`tools/poc/bone-fullchain-pages.cjs`）：19 页逐一挂载 0 pageerror 0 5xx；「理赔进度」初判红经取证为查询驱动页需带 `?claimId=`，用真实案件深链验证渲染 498 字符全链数据后修正判据全绿。
- [x] 21.5 **产物**：`outputs/chain-{ui,pages}-result.json` + `chain-{review-list,case360,progress}.png`；两探针沉淀 `tools/poc/`；诊断报告新增 §16。
- [ ] 21.6 阻塞清单（推送失败端点等）本轮未涉及、真无契约，不伪造。

## 22. 第十七轮：保单→打款八环节单案贯穿 + 广覆盖复跑（2026-11-24 21:30）

> 用户「从保单配置，到报案，签收，智能初审，智能理算，复核，推送保司，打款整个全链路打通，并构建模拟数据，进行端到端测试」。沿用既有裁定：Comet SDK 卡 open.revalidate 死锁——不碰守卫/不 mark-unknown/不 cancel/不伪造回执。

- [x] 22.1 **八环节单案贯穿 31/31 PASS**（`claim-journey-e2e.mjs`，本轮复跑两遍）：一条案件走完保单配置(真落库 t_claim_policy ACTIVE)→报案(HTTP 201，CL/RP 双号)→签收(PENDING_EVIDENCE+证据锁定)→初审(分配/开始/回填/转人工/领取)→理算(formulaHash+snapshot 非空)→复核(APPROVED)→推送保司(Outbox INSURER_PUSH PENDING→SENT)→打款(FINISHED，claim_amount=Σ明细)。每环 API + MySQL 双源对账。
- [x] 22.2 **广覆盖全链 67/67 PASS 复跑**：`claim-full-chain-e2e.mjs` 七分支 + 查询分页/假名化检索/保单聚合/MySQL 对账全绿，与第十六轮一致无回归。
- [x] 22.3 **判据纠偏**：`t_claim_case.report_no=NULL` 取证为设计如此——RP 报案号真源在 `t_claim_report.report_no`（唯一索引），case 表只存 CL 号；Outbox payload 两号齐全。未改业务代码。
- [x] 22.4 **脚本修复**：`claim-journey-e2e.mjs` 汇总行 `report()` 补 title，消除 `undefined：通过 31/31`；重跑确认标签正确且 31/31。
- [x] 22.5 **产物**：诊断报告 §17；复跑日志 `/tmp/claim-journey-final.log`、`/tmp/claim-fullchain-rerun.log`；服务恢复原状（8087/8888 停，3306/6379/8081 保留）。
- [ ] 22.6 边界保留：`SENT`=事件总线派发并被内部消费，非 HTTP 推外部保司（无 webhook 配置）；真接外系统属二期 MQ/HTTP 出站。

## 23. 第十八轮：推送保司升级为真实 HTTP 出站（2026-11-24 21:53）

> 用户裁定"推送保司升级为真外发"+"新增仓库内保司接收桩"。沿用 Comet SDK 死锁裁定：不碰守卫/不伪造回执。

- [x] 23.1 **真实 HTTP 出站适配器**：新增 `InsurerPushGatewayPort` + `InsurerPushGatewayPortAdapter`（JDK HttpClient 真 POST，信封+原 payload，稳定 eventId 幂等，fail-closed，可选 Bearer）；`SpringOutboxDispatcher` 按事件类型路由 INSURER_PUSH→HTTP（配置端点时），未配置回落一期进程内总线。
- [x] 23.2 **配置与接收桩**：`bone.claim.insurer-push.{endpoint,timeout-ms,auth-token}`；新增 `tools/mock-insurer-receiver.py`（真 HTTP+JSONL 落盘+/received 查询+422 契约校验，非 AI mock）。
- [x] 23.3 **验证全绿**：claim-app 单测 1700/1700（含 ArchUnit 28 规则）；适配器 3/3、路由 3/3；journey E2E 加"真实 HTTP 送达+契约"2 断言 → 33/33（最终 jar 重跑）；广覆盖 67/67 无回归。接收端 JSONL 真 POST 落盘双向对账。
- [x] 23.4 **判据纠偏**：报送金额=理算核定/结算实付额（非申请额），按 pay() 的 amount 参数取证；新 @Component 自建 ObjectMapper 避免切片测试装配失败。
- [ ] 23.5 边界：真外发当前指向仓库内保司桩（真网络出站+落盘，非假投递）；接真实保司只换配置。

## 24. 第十九轮：全链复跑 + 决策超时假红修复（2026-11-24 21:53）

> 用户第三次同一全链需求，本轮直接最终交付验证，无新增产品能力。

- [x] 24.1 最终验证：真外发栈跑 journey E2E 33/33（八环节+真实 HTTP 送达+契约）、广覆盖 67/67（修复后连跑两次）。样本 CL202611249QT36987CQXPS7 / 申请额 802.86。
- [x] 24.2 决策超时假红根因：首次广覆盖 62/67 的 5 条失败系决策真实 AI 耗时 10.19s > 网关 TimeLimiter 5s，网关超时返回空响应、后端仍成功落库（trace 0a07dac5 / PENDING_ADJUST 为证）。非产品缺陷。
- [x] 24.3 测试健壮性修复：`claim-full-chain-e2e.mjs` 决策 POST 同幂等键重试（≤6 次/2s）+ 读端点 `/review/tasks/{claimId}/execution` 回查兜底；两条恢复通道实测可用，修复后 67/67×2。
- [ ] 24.4 边界留痕：网关 5s TimeLimiter 对慢 AI 决策偏紧（既存配置，本轮未改）；产品级消除需评估端点豁免或异步轮询。
