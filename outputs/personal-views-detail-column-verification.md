# 个人显示运行时 + 明细列升级 —— 联调验证报告

日期：2026-10-18
范围：`bone-metadata-app` 运行时数据页 S1 双轨（个人 overlay）+ CHILD_TABLE 明细列入口 + 明细行键去废弃参数

---

## 一、本轮修复的问题

### 1. 明细列场景探针红：行内「查看明细」入口匹配不到（根因＝探针选择器，非产品 bug）

- 现象：`ui-personal-views-probe.mjs` 断言「行内渲染『查看明细』入口」失败，但同场景的
  「items 契约列渲染为『明细行』列表头」「表头无排序器」均通过 —— 说明列本身在、只是行内按钮没匹配上。
- 定位：诊断输出 `firstDataRowTds=[]`。原选择器 `#subapp-viewport .ant-table:visible .ant-table-tbody tr`
  的第一个匹配落在 antd 滚动表头副表上，取不到数据行单元格。
- 验证：改用 `getByRole('button', { name: '查看明细' })` 统计视口内按钮 ⇒ `viewDetailButtons=1`、
  `incompleteTag=0`。**前端对 CHILD_TABLE 列的渲染本就正确**（恰 1 个入口，无「明细声明不完整」Tag）。
- 结论：产品代码零改动，仅修探针断言定位方式。

### 2. 真实产品小缺陷：antd 5 废弃 `rowKey` 的 index 参数

- 现象：探针「全流程无致命 console/page 错误」红，捕获
  `Warning: [antd: Table] 'index' parameter of 'rowKey' function is deprecated`。
- 定位：`RuntimeDataManagement.tsx` 明细行 `Table` 的 `rowKey={(row, i) => String(row.id ?? \`new-${i}\`)}`。
  新增行尚无后端 id，只能退化用数组下标做键 —— antd 5 已废弃该参数且不保证行为。
- 修复：改为「新增行带稳定临时行键 `__rowKey`」，`rowKey` 不再取 index：
  - 新增 `childRowSeq = useRef(0)` 自增序列；「添加明细行」写入 `__rowKey: new-${seq}`。
  - `rowKey={(row) => String(row.__rowKey ?? row.id ?? '')}`。
  - `__rowKey` 进 `CHILD_SKIP_KEYS`（否则 union 行键会把它当**可编辑业务列**渲染出来）。
  - `stripChildRowKey()` 在 `handleChildrenSave` 前剔除，`__rowKey` 不进后端载荷。
  - 编辑回写两处保留/补发键：`ChildRowEditorModal.onOk` 从 `initial` 继承；
    父组件 `onSave` 替换分支保留原键、追加分支补发新键（否则编辑一次后行键退化为空，
    多条新增行会撞 React key —— rowKey 已无 index 兜底）。
- 业界依据：行标识应来自数据本身（后端主键）或生成时的稳定标识，而非渲染位置。

### 3. 探针脚手架健壮性（测试侧，非产品）

自产父子实体的 `createEntity`/`ensureField` 各加 3 次重试，建实体后 settle 2s
（后端实体创建为最终一致，刚建完立即建字段会偶发 400）。修正 `ccChild.id`/`ccParent.id`
为 `createEntity` 直接返回的 id。

---

## 二、验证结果

| 项目 | 结果 |
| --- | --- |
| `tsc --noEmit`（bone-metadata-app） | 0 错误 |
| `eslint RuntimeDataManagement.tsx`（`--max-warnings 0`） | 0 问题 |
| `vitest run`（**必须从 `bone-frontend` 根跑**） | **281/281 通过** |
| `ui-personal-views-probe.mjs`（15 场景） | **56/56 通过** |
| `two-scenario-full-chain-e2e.py`（含采购明细 + 9 条负向） | **87/87 通过** |

探针 56/56 覆盖：视图新建/切换、个人列改名（改名→表头生效→服务端落盘 displayName→视图隔离→恢复默认删键）、
筛选与列可见性解耦（隐藏列仍在筛选下拉 / CHILD_TABLE 不在筛选下拉 fail-closed）、视图隔离、
自伤遮蔽实时判据（契约 v1→v2→v3 切换 + 一键自救）、CHILD_TABLE 明细列（列表头 + 无排序器 +
行内「查看明细」入口 + 点击打开明细编辑器）、**契约外字段提示（5.6 节，见下）**、
设计器⇄运行时双向入口、多视图全局恢复确认、收尾基线复位。

### 5.6 节（补齐）：契约外字段提示的 E2E 覆盖

复核发现该功能**只有单测（`columnProjection.test.ts`）没有 E2E 断言**——而它解决的正是
「我建的字段呢」这个交付工程师最高频困惑，单测证明不了运行时真的看得见。补 5 条断言：

1. 前置自证：`status`/`remark` 确已建模（否则"提示不出现"无判别力）；
2. 运行时出现低噪音提示「N 个字段未列入团队契约」；
3. **提示的字段确实不在数据表表头**——杀死"无条件渲染提示"的伪实现；
4. 契约加入两字段并发布成功；
5. 加入后提示消失且列进入表头（闭环可操作）。

探针实现要点：`putShieldSchema`/`publishShield` 原声明在 5 节的 `if (shieldId) {…}` 块内，
5.6 节在块外引用会 ReferenceError ⇒ 提到 `try` 作用域外（`let` 声明 + 块内赋值）；
`buildShieldSchema` 加 `addOutside` 选项以产出"含契约外字段"的第二版契约。

### 环境坑（复盘）

- **vitest 必须从 `bone-frontend` 根跑**：在 `apps/bone-metadata-app` 下跑会出现
  `localStorage is not defined` 等 14 个假红（环境/路径解析差异，非回归）。
- `page.evaluate` 内不能用 `:visible`（Playwright 专有伪类，原生 `querySelector` 不认）；
  `locator.outerHTML()` 不是 Playwright 方法（用 `innerHTML()`/`innerText()`）。
- Bash 工作目录每条命令都从仓库根起，`cd` 不跨命令持久。

---

## 三、涉及文件

产品代码：
- `bone-frontend/apps/bone-metadata-app/src/pages/RuntimeDataManagement.tsx`
  （CHILD_TABLE 行内入口 + 筛选解耦 `filterableFields` + `__rowKey` 行键改造）

测试代码：
- `scripts/e2e/ui-personal-views-probe.mjs`（明细列断言定位修正 + 脚手架重试/settle）

> 说明：本工作树为共享树，`git status` 中还混有并行会话及历史批次的未提交改动，
> 本批仅涉及上述两个文件。
