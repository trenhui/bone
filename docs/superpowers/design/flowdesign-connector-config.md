---
archived-with: 2026-08-23-flowdesign-connector-config
status: final
---
# Design Doc: 流程设计器节点配置面板接连接器

## 1. 背景

`bone-integration-app` FlowDesign 用 @antv/x6 画图，节点库已对齐后端 NodeType（此前 change），但无节点配置面板：拖拽不落 type/config，节点无法绑定连接器。此为 enhance-modules 遗留结构性缺口。

## 2. 关键决策

### Decision 1：节点配置面板
- 双击画布节点打开 Modal：名称（label）、类型（Select 对齐 nodeTypes）、绑定连接器（Select，数据来自 `connectorApi.getConnectors`）、config JSON（TextArea）。
- 保存写入 `node.data`（type/config + connectorId）并更新画布 label。

### Decision 2：DnD 落 type
- Dnd 拖入节点后从 dataTransfer 读 nodeType/nodeLabel 设置 node.data.type 与初始 config。

### Decision 3：config 持久化
- 节点 config 存 `connectorId`，随 FlowNode.config 保存/回读（后端 FlowNode.config 为 Map，契约不变）。

## 3. 修改文件

- `bone-frontend/apps/bone-integration-app/src/pages/FlowDesign.tsx`

## 4. 验收

- 双击节点出配置面板；可绑连接器；拖拽落 type；config 含 connectorId 保存/回读；integration-app tsc 0 错误。
