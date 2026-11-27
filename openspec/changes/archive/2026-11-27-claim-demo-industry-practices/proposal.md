## Why

`doc/ai-product/synapClaim/07-delivery/prototypes/claim-workbench/` 下已有三份理赔高保真设计稿（审核详情、赔案管理列表、影像详情），它们还原了 tpa-test 测试环境的真实 schema 与字段，但信息架构偏"字段平铺"，缺少能体现专业深度的复杂业务 UI 模式。前一轮对 `pk-tpa-saas` 仓库 96 个页面 / 13 个模块的全面分析识别出了三类可直接迁移的业界最佳实践模式（复杂配置矩阵、标准 vs 专属继承、阶段化工作台），尚未落地到 DEMO。本次用这些经过真实系统验证的模式升级 DEMO，使其从"字段还原稿"进化为"可直接指导实现、体现专业深度的方案级原型"。

## What Changes

- **升级理赔审核详情页 `claim-detail.html`**：引入渐进式披露与证据分级——顶部决策信息前置、待关注风险区强化、理算信息采用"初始/已占用/冻结/可用"多列对照的信息架构（取自系统选项配置页的额度五列模式），优化审核动线。
- **升级赔案管理列表页 `claim-manage.html`**：引入阶段化工作台视角（签收→初审→录入→质检→审核→复核→推送七节点导航）、配置化 Tab（待处理/处理中/已超时）、批量操作条与分配规则可视化，使列表页从纯 CRUD 升级为作业工作台。
- **升级赔案影像详情页 `claim-image-detail.html`**：强化影像审核作业闭环——清晰度分级徽标、影像分类规则浮层、关联发票浮层与底部工具栏的一致性打磨。
- **新增理算规则配置页 `claim-rule-config.html`**（本次核心增量）：迁移保单规则配置的三类高价值模式——三层矩阵表格（计划/险种/责任）、因子笛卡尔积矩阵（勾选条件自动生成矩阵行列且保留已填值）、语法引导式公式编辑器（函数/字段双栏点击输入 + 括号校验），体现系统 UI 表达能力的上限。
- **统一设计系统**：四页共用同一套 light 主题设计令牌（配色、间距、字号、状态色标、卡片/表格样式），保持视觉语言一致。

以上均为静态 HTML/CSS/原生 JS 单文件原型，不接入后端、不修改任何 Vue 源码或 schema。

## Capabilities

### New Capabilities

无运行时能力新增。本 change 为纯设计资产（静态原型）更新，属于文档/设计交付物范畴，不涉及用户可见的系统行为或 spec 级需求变更，故 `.openspec.yaml` 设置 `skip_specs: true`。

### Modified Capabilities

无。不修改 `openspec/specs/` 下任何既有能力规格。

## Impact

- **受影响文件**：`doc/ai-product/synapClaim/07-delivery/prototypes/claim-workbench/` 下三份既有 HTML + 一份新增 HTML（`claim-rule-config.html`）。
- **不受影响**：后端 Java 代码、前端 Vue 源码、数据库 schema、API 契约、OpenSpec 既有能力规格均未改动。
- **依赖关系**：无新增依赖；原型为单文件、零外部 CDN 依赖，可直接用浏览器打开。
