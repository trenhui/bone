---
archived-with: 2026-08-23-flowdesign-connector-config
status: final
---
# Plan: 流程设计器节点配置面板接连接器

> 对应 `openspec/changes/flowdesign-connector-config/tasks.md`。
> 设计：`docs/superpowers/design/flowdesign-connector-config.md`

## 1. 节点配置面板
- [x] 双击节点打开配置 Modal（名称/类型/连接器/config JSON）
- [x] 连接器下拉来自 connectorApi.getConnectors

## 2. DnD 落 type/label
- [x] 画布 onDrop 读取 dataTransfer 创建节点并带 type/label，自动打开配置面板

## 3. config 持久化
- [x] 节点 config 存 connectorId，保存/回读随 FlowNode.config 不丢失

## 4. 校验
- [x] integration-app tsc 0 错误；全部 6 个前端 app 0 错误，无回归
