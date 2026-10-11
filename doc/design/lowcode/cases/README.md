# 场景 case（Mode B 探针）

| 文档 | 说明 |
|------|------|
| [费用报销系统-元数据建模方案.md](./费用报销系统-元数据建模方案.md) | L0 = PRD §2.5.9 / 原型；L1 = 缺口探针（不进 Must） |
| [optimized-model-design.png](./optimized-model-design.png) · [optimized-entity-management.png](./optimized-entity-management.png) | 2026-10-11 菜单 IA 定稿后「模型设计 / 业务对象」两页 UI 深度诊断优化效果图；HTML 源稿 `mock-*.html` 同目录，待立项（P0 词汇统一 → P1 单弹完型/批量结果追溯 → P2 ⌘K 全局搜索） |
| [expense-flow-act1.png](./expense-flow-act1.png) · [act2](./expense-flow-act2.png) · [act3](./expense-flow-act3.png) | 2026-10-11 费用报销场景端到端串联设计稿（三幕：模板起盘冷启动 → 结构建模即时预览 → 发布后业务流转审批打款），含 AS-IS/TO-BE 动线对比与交互细节清单；HTML 源稿 `mock-expense-act1~3.html` 同目录 |
| [业务建模三入口页UI优化方案.md](./业务建模三入口页UI优化方案.md) | 2026-10-11 三入口页（模型设计/业务对象/业务数据）UI 深度诊断与优化完整方案：含 D1–D4 / B1–B5 诊断、应用模块选择 O1–O5 最佳实践、业界对标与效果图索引（引用同目录 `optimized-*.png` / `mock-*.html`） |
| [业务建模三入口页最佳方案综述.md](./业务建模三入口页最佳方案综述.md) | 2026-10-11 综述各方案（v3/act1-3/expense-flow）优点，提炼"最佳方案"取舍与关键交互决策，作为 `mock-best-scheme.html` 设计依据 |
| [mock-best-scheme.html](./mock-best-scheme.html) | 2026-10-11 **最终交互稿（唯一落地基线，SPA 单页三视图 + 演示状态开关）**：模型设计（Action-first 磁贴+待发布草稿+概览）/ 首次使用空态（内联建应用/权限申请）「业务对象（左树+批量+导入▾+深链）+ 业务数据（最近实体 chips + 7 步全流程条 + 录入抽屉：五态步进、金额>0 语义、头表金额=明细合计只读联动、看板 v1.1 占位、⌘K/Esc、hash 深链）。词汇全量「业务对象/业务数据」，无应用/无数据空态闭环指向下文。基于评审合并优化（4 硬伤+3 缺口修复），12 份历史稿已 `.archived-` 前缀归档 |
| [最终稿-模型设计.png](./最终稿-模型设计.png) · [最终稿-业务对象.png](./最终稿-业务对象.png) · [最终稿-业务数据.png](./最终稿-业务数据.png) | 2026-10-11 **最终交付三屏截图**（由 `mock-best-scheme.html` 无头 Chromium 渲染）；旧单图 `最佳方案终稿-四联屏-v4.png` 及 synthesis 系列已废弃归档（`.trash-`/`.archived-` 前缀） |
| [mock-expense-flow.html](./mock-expense-flow.html) | 2026-10-11 **报销场景全流程串联交互稿**：模型设计→发布→业务对象→业务数据→提交→审批→打款 7 步动线 + 角色分工 + 串联设计要点 |
| [mock-model-design-v3.html](./mock-model-design-v3.html) | 2026-10-11 模型设计优化交互稿 v3：磁贴 hero（报销模板推荐）+ 双栏 rail（待发布草稿行列表 / 概览+继续建模）+ 无应用空状态 + 创建向导内联建应用模块 |
| [mock-entity-management-v3.html](./mock-entity-management-v3.html) | 2026-10-11 业务对象优化交互稿 v3：左树+单表（模块列/更新时间列）+ 操作列[打开][发布][删除][更多▾] + 导入合并[导入▾] + 左树选中模块自动带入 |
| [mock-runtime-data-expense-v2.html](./mock-runtime-data-expense-v2.html) | 2026-10-11 业务数据优化交互稿 v2：报销单录入为核心，「最近操作实体」chips + 记录列表（提交/状态）+ 录入抽屉（申请人自动带/金额校验/明细子表/附件）+ 提交→审批→打款衔接 |

后续场景 case（巡检/补货深化等）也放本目录，勿散落到 `design/` 根或其他模块目录。

| 最终稿-模型设计.png · [业务对象](./最终稿-业务对象.png) · [业务数据](./最终稿-业务数据.png) | 2026-10-11 _最终交互稿单页渲染图_：『mock-best-scheme.html』三入口页 2x 截图（无头 Chromium 渲染，hash 唯一性校验通过），作为评审快照 |

返回专题包：[../README.md](../README.md)
