# Proposal: 流程设计器节点配置面板接连接器

## 目标（Why）

`integration-app` FlowDesign 画布可拖拽节点但无节点配置面板，新建节点不落 type/config，节点无法绑定具体连接器（enhance-modules 遗留结构性缺口）。补全节点配置能力，使设计器节点真正与连接器体系打通。

## 范围（What）

1. **节点配置面板**：双击/编辑画布节点弹出配置表单，编辑节点名称、类型、绑定的连接器（下拉复用 `connectorApi.getConnectors`）、config JSON。
2. **DnD 拖入落 type/label**：从节点库拖拽时读取 dataTransfer 的 nodeType/nodeLabel，为新建节点设置 type 与 config 默认值。
3. **config 中持久化 `connectorId`**：节点 config 记录绑定连接器，随流程保存/回读。

## 非目标（Non-Goals）

- 不改连接器注册/后端契约。
- 不重构 x6 画布渲染。

## 验收标准（Acceptance Criteria）

- [ ] 画布节点可双击打开配置面板。
- [ ] 配置面板可绑定连接器（下拉列表来自 connectorApi）。
- [ ] 新建节点从拖拽带出 type/label。
- [ ] 保存/回读流程时节点 config（含 connectorId）不丢失。
