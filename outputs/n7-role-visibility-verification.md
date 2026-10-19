# N7 字段级角色可见性 —— 实现与验证报告

> 范围：`control.props.roles` / `props.tier` 从「协议已声明」到「引擎真正消费」的闭环。
> 结论：**已闭环并可验证**。roles/tier 为真实生效的能力；mask 只做建模标注与 lint 提示，
> 不伪装成已生效的脱敏控制（理由见§5）。

---

## 1. 起点：这是一项「配了不生效」的假能力

协议 `bone-ui-schema.schema.json` 与前端 lint 白名单**早已声明** `roles` / `tier` / `mask`，
创作侧 UI 也能填，但引擎侧从未消费：填了角色限制，运行时该字段照样对所有人可见。

这类缺陷比"功能缺失"更危险——建模者会认为权限已生效。因此本次实施的口径是
**「真实能力，不做假实现，且必须可验证」**，每个语义决策都落到可判别的断言上。

## 2. 语义决策（每条都有对应测试）

| 场景 | 口径 | 依据 |
|---|---|---|
| `roles` 未声明 / 空数组 | **不限制**（人人可见） | 与 `resolveVariant` 的 `match.roles` 既有口径一致（空集合 = 通配） |
| `roles` 已声明但 `ctx.roles` 缺失或未命中 | **隐藏** | fail-closed：权限判定默认不放行 |
| `roles: ['a','b']` | **或**（命中任一即可见） | 多角色并列即"任一授权" |
| `tier` 与 `roles` 同时存在 | **与**（两者都命中才可见） | 角色管身份、档位管套餐，独立正交 |
| 联动 `then.visible=true` | **不得放宽**角色门禁 | 联动是业务显隐，角色是安全约束；可被联动覆盖 = 越权后门 |
| 同 `fieldCode` 多个控件 | `roleRequired` **取交集** | 更严者胜（同 `visible` 取 false 的取向） |

### 一个被测试纠正的直觉
原断言以为"空数组 `roles: []` 应不命中任何变体"，实测引擎相反，且与 `resolveVariant`
既有的空 `match` 兜底冲突——若改成"空数组不命中"，`match: { roles: [] }` 的变体将永远
不可达，而"缺省 match"又能命中，两者语义割裂。故按引擎既有语义锁定：**空集合 = 不限制**，
并在测试注释里写明该结论，避免后人重复踩坑。

## 3. 变更清单

**引擎**（`utils/uiSchemaEngine.ts`）
- `rolesVisible(roles, ctx)`：角色门禁判定的唯一真源
- `controlBaseStates(sections)`：统一抽取 visible / readonly / roles / tier 基线态
- `fieldVisible()` 接入角色门禁，且置于联动之后（联动无法放宽）
- `linkageState()` 保持 `roleRequired` 不被联动覆盖
- 角色名归一化：`trim` + 过滤空串（防脏数据致控件被整体隐藏）

**消费方**（两个渲染器统一走 `controlBaseStates`，避免口径分叉）
- `components/BoneDynamicForm.tsx`（运行时表单）
- `components/uiSchema/SchemaPreview.tsx`（创作侧预览）
- `components/RuntimeDetailView.tsx`（详情抽屉，一并修复数据泄露）

**创作侧与门禁**
- `StructurePanel.tsx`：新增「可见范围」卡，roles / tier / mask 编辑入口
- `constants.ts`：`MASK_OPTIONS`（复用既有 `TIER_OPTIONS`）
- `lint.ts`：roles/tier 值域校验；`mask` 存在时发「运行时未脱敏」warning
- 协议双份同步：`doc/design/lowcode/...` 与后端 classpath（sha256 一致）

## 4. 验证结果（全部实测）

| 项| 结果 |
|---|---|
| N7 目标单测 | **127/127** 通过（引擎 78 + lint 49） |
| 全量 vitest | **425/425** 通过，25 个文件全绿 |
| **N7 真实变异验证** | **6/6 变异全部被捕获** |
| N7 活体 UI 探针 | **10/10** 通过 |
| `tsc --noEmit` | 0 错误 |
| 协议对账门禁| 契约一（doc ↔ classpath sha256）、契约二（前端白名单 ↔ 协议 28 项）均通过 |
| G33/G34 门禁 | 通过 |

### 活体探针的关键设计
探针（`scripts/e2e/ui-designer-n7-role-probe.mjs`）走 shell :3000 真实登录，
每段都是「负向基线 → 注入 → 正向 → 恢复」：

1. **负向基线**：注入前受限字段照常可见 —— 否则「什么都没渲染」也会被误判为通过；
2. **双向对照**：`employee` 看不到 / `admin` 能看到 —— 防"整体不渲染"的假绿；
3. **fail-closed**：身份清空（`ctx.roles` 缺失）⇒ 隐藏；
4. **脏数据**：注入 `roles: []` ⇒ 自检面板报 error（引擎口径"不限制"与建模者直觉相反，须当场拦下）；
5. **恢复**：清掉注入后回到基线，finally 必还原草稿。

## 5. `mask` 为什么不做前端假脱敏

`props.mask` 在协议里已声明，但**后端没有字段级脱敏能力**，
`PublishedRuntimeEntity` 也不携带 ui-schema。前端打码只会制造
"界面上是星号、API 里仍是明文"的安全假象——比不做更危险。

因此本轮只做：建模标注 + lint warning 提示"运行时未脱敏"。
真正落地需要后端在发布态投影时脱敏，属独立需求。

## 6. 过程中发现并修掉的工具级缺陷

变异验证脚本自身是"改源码 → 跑测试 → 还原"的破坏性操作，本次暴露出两个缺陷，
它们**反而污染了被验证的源码**：

1. **变异体泄漏**：备份路径固定为 `/tmp/n7_engine_backup.ts`。两个实例并发跑时
   （本次：会话重试 + 后台任务），各自把对方的**变异体**当作"原始备份"写回。
   实测泄漏的是 MUT-6 的 `return out;`——`baseStates` 失去裁剪，联动热路径每轮
   都merge 上百个无约束控件。它"看起来只是更宽松的实现"，所以被当成可接受状态留存，
   直到全量 vitest 报出 3 个失败才暴露（假能力式的静默退化）。

2. **工作树互毁**：脚本用 `cp` 备份/还原，本机沙箱下偶发
   `ENOTSUP: operation not supported on socket`，且失败时**直接把目标文件留在不存在状态**
   ——本次实际把 `uiSchemaEngine.ts` 删了，需从备份抢救还原。

修法（提交 `05c8873c`）：备份改 `mkstemp` 进程唯一路径以消除交叉写回；备份/还原改用
Python 读写绕开 `cp` 的 ENOTSUP，还原后逐字节自证、含`MUTATED` 标记则响亮失败；
`trap EXIT/INT/TERM` 保证中断也还原；加 `O_EXCL` 互斥锁禁止同类脚本并发；
起点自证"源码若已含变异标记则拒绝开跑"。

> 教训：**验证工具的缺陷不会被被验证对象暴露**。脚本"跑完打印 6/6 通过"时，
> 它可能正在往源码里写变异体。变异验证必须在结束时断言源码完整性，而不是只看通过数。

## 7. 提交

- `04be68c5` feat(ui-schema-designer): N7 字段级角色可见性全链路闭环（含详情抽屉数据泄露修复）
- `05c8873c` fix(e2e): N7 变异验证脚本加固——修变异体泄漏与工作树互毁
