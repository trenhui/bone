---
name: frontend-react
description: React 18 + TypeScript 前端开发技能包（Bone 项目）
---

## 技术栈约束
- React 18.x + TypeScript 5.x
- Ant Design 5.x + @ant-design/pro-components
- @umijs/max 路由框架
- 函数组件 + Hooks（不用 class 组件）
- 项目结构：`pages/` 页面，`services/` API，`components/` 公共组件

## 项目结构（代码生成目标）
生成 CRUD 页面的标准结构：
```
src/pages/
  ${moduleName}/
    ${lowerClassName}/
      index.tsx                 # 页面路由入口
      List.tsx                  # 列表页（ProTable）
      types.ts                 # TypeScript 类型定义
      components/
        ${ClassName}FormModal.tsx  # 表单弹窗
src/services/
  ${moduleName}/
    ${lowerClassName}Api.ts     # API 服务
```

## 编码规范
- 严格 TypeScript，禁止 `any`，必须正确定义类型
- API 层和 UI 层分离，类型统一放在 `types.ts`
- ProTable 用于列表展示，支持搜索、分页
- FormModal 用于新增/编辑弹窗
- 请求/响应使用统一的接口封装
- 组件拆分，单文件不超过 500 行
- 使用函数式组件，箭头函数
- 导入顺序：第三方 → 项目内部 → 样式

## Ant Design Pro 最佳实践
- 使用 `ProTable` 自带搜索、分页、选择
- 使用 `ModalForm` 或 `Drawer` 编辑表单
- 使用 `request` 来自 `@umijs/max` 发送请求
- 表格操作列使用 `Action` 组件
- 表单自动根据字段生成表单项

## 命名规范
- 组件：大驼峰 `UserFormModal`
- 文件：小驼峰 `userApi.ts` 或大驼峰和组件同名
- 接口：遵循 RESTful 风格
- CSS 类名：kebab-case

## 常见模式
- 列表 + 弹窗编辑模式
- 搜索表单内置在 ProTable
- 删除支持二次确认
- 操作成功后自动刷新列表
- 错误提示使用 `message.error`
- 成功提示使用 `message.success`
