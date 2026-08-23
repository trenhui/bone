# Design: 流程设计器节点配置面板接连接器

## Context
FlowDesign（bone-integration-app）用 @antv/x6 画图，节点库已对齐后端 NodeType，但无节点配置面板：拖拽不落 type/config，节点无法绑定连接器。enrich 遗留缺口。

## Decision 1：节点配置面板
- 双击画布节点打开 Modal，含：名称（label）、类型（Select，对齐 nodeTypes）、绑定连接器（Select，数据来自 `connectorApi.getConnectors`）、config JSON（TextArea）。
- 保存时写入 `node.data`（type/config），并更新画布 label。

## Decision 2：DnD 落 type
- Dnd 拖入节点后，从 dataTransfer 读 nodeType/nodeLabel 设置 node.data.type 与初始 config。

## Decision 3：config 持久化
- 节点 config 存 `connectorId`（绑定连接器 id）。保存/回读流程时随 FlowNode.config 走（后端 FlowNode.config 为 Map，无需改契约）。

## 修改文件
- `bone-frontend/apps/bone-integration-app/src/pages/FlowDesign.tsx`

## 验收
- 双击节点出配置面板；可绑连接器；拖拽落 type；config 含 connectorId 保存/回读。
