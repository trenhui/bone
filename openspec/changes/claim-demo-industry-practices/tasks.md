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
- [ ] 12.6 仍阻塞（不伪造，与上轮一致）：8.8 推送失败无后端端点；清晰度三态批量标注（`EvidenceController` 无批量更新端点、angle/clearType/imageType 字段不存在）；证据锚点页码定位（决策 trace 无证据位置结构化字段）；规则矩阵配置（无矩阵 valueList 端点）、主体专属覆盖、导入记录逐行错误、运营看板之外的驾驶舱指标（保单级 SLA 列等）。均需后端契约落地后同批实现。
