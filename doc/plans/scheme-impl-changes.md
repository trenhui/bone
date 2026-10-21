# scheme-impl 最终稿→代码 改动清单（Map：设计稿 ↔ 实际改进）

设计稿基线：doc/design/lowcode/cases/mock-best-scheme.html（六屏）。
原则：只做「业界最佳实践已验证、且当前代码确实缺失」的项；代码已实现的（B1 操作收敛、B4 更新时间列、G10 明细、批量发布反馈）不重做。

## 后端（1 项）

### BE-1 明细合计 → 头表回填（服务端权威）
- 设计稿对应：录入抽屉「金额 = 明细合计（自动）」「保存时以服务端计算为准」。
- 现状缺口：RuntimeRelationSupport.replaceChildren（L314）只 upsert+软删孤儿，无任何机制回填头表；RuntimeWriteHook SPI 仅挂主行写路径（JdbcRuntimeRecordService L719-744），子行路径收不到事件。
- 实现：新增 `ExpenseChildSumAggregator`（application 层，@Component）。在 RuntimeRelationController.replaceChildren 事务内，relationSupport.replaceChildren 完成后：
  1) requireChildSpec(parent, childCode) 已得 spec（含 sourceField/junction 信息，见 RuntimeRelationSpec.of(col)）；
  2) relationSupport.listChildren(parent, tenantId, id, spec) 取全量子行（同源连接，含刚写入的行）；
  3) 若 parent 存在名为 `amount` 的数值列，按子行 `amount` 求和 → service.update(entityCode, tenantId, id, {amount: sum}, null, operatorId)（走完整校验+审计链）；
  4) 兜底容错：子实体无 amount 列 / 头表无 amount 列 / update 抛 422（如头表状态冻结）→ 记 INFO 日志并跳过，不阻断子表保存（子表保存是用户显式操作，合计回填是增值动作，失败不应回滚明细）。
- 注册：无 SPI 变更，直接在 controller 注入 aggregator 调用（事务内）。
- 验证：E2E——创建 expense_reimbursement 头 + 2 行明细（553.00/727.50）→ GET 金额=1280.50；SUBMITTED 后 replaceChildren 不回填（422 容错路径）。

## 前端（3 项）

### FE-A 批量发布结果条（EntityManagement）
- 设计稿对应：批量结果 Alert「部分成功：成功 2 · 失败 1 − 查看明细」。
- 现状缺口：success/warning 有提示，但失败列表只进 console.log，用户无法得知失败原因。
- 实现：BatchPublishResult state（Alert + List 组件渲染 errors: {displayName, message}[]）；失败详情可折叠展开，关闭按钮可清。

### FE-B ModelingWorkspace 新建向导补「界面模式」
- 设计稿对应：单弹完型含「界面模式卡」（自动生成界面 / 手写界面）双选。
- 现状缺口：落点 Modal 只有 应用+模块（L526-589），界面模式要到 EntityManagement 新建表单才能选（跨页接力，断点）。
- 实现：Modal 内加 Segmented/Radio（RUNTIME 自动生成界面 / CUSTOM 手写界面），值落入 sessionStorage('bone.newEntityDraft') 或直接 navigate('/entities?new=1&deliveryMode=RUNTIME|CUSTOM')，EntityManagement openCreate 读参预填。

### FE-C RuntimeData 明细合计实时联动
- 设计稿对应：明细弹窗「合计 ¥1,450.00」+ 头表金额绿色只读徽标「= 明细合计」。
- 现状缺口：ChildRowEditorModal（L1584+）仅行级编辑，整组弹窗（L1327-1367）无合计行；头表金额照常渲染无联动提示。
- 实现：
  1) 整组弹窗底部加统计行「合计：¥{childRows.reduce(amount)}」（isNumericColumn 已有，判 amount 列）；
  2) 保存成功后 message.info(`已保存 ${n} 行，合计 ¥${sum}，头表金额将自动回填`) 提示 BE-1 生效；
  3) 头表金额字段（code==='amount'）在 detail 抽屉渲染时附后缀提示「= 明细合计（自动）」——不直接改值（服务端权威）。

## 不做（防过度设计，说明理由）

1. `SpelBusinessRuleEngine` 化聚合规则：现有 ExpressionEngine 是 Smart 扩展、未接 JDBC 运行时，为一处求和引入表达式引擎=过度设计（Chesterton fence：现有 hook 模式已够用，且 ExpenseReimbursementWriteHook 同层先例）。
2. 迁移脚本：amount/子表/状态机全部已由 0031/0032/0037 落库，无需新迁移。
3. ⌘K 全平台搜索真源/看板视图：设计稿本身标注 v1.1/P2（未排期），不做。
4. 三页 lazy 加载：性能优化与本次「交互串联」目标正交，独立小任务不混入。

## 验证（联调通过标准）

1. mvn -pl bone-engine/bone-metadata-server -am compile / spotless:check 全绿；
2. E2E：POST 头+2 明细行 → GET amount=1280.50 自动回填（服务端权威）；
3. vitest run：metadata-app 相关单测（含新增 batch result/child sum helper）通过；
4. npm run build（bone-metadata-app）通过；
5. Chromium 无头端到端：登录 → 实例化报销单 → 录入明细 → 头表金额 1280.50 展示 → SUBMITTED 后改明细不生效（422）截图留证。
