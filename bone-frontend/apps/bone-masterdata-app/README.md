# BONE 主数据管理应用

这是 BONE 企业级全栈开源快速开发平台的主数据管理前端应用。

## 技术栈

- **React 18** - 用户界面库
- **TypeScript** - 类型安全的 JavaScript 超集
- **Ant Design 5** - 企业级 UI 组件库
- **Ant Design Pro Components** - 高级业务组件
- **React Router 6** - 路由管理
- **Axios** - HTTP 请求库
- **Vite** - 下一代前端构建工具
- **xlsx** - Excel 文件处理

## 功能特性

### 1. 主数据实体管理
- 实体的增删改查
- 实体发布功能
- 实体状态管理（草稿/已发布）
- 支持按名称、分类、状态筛选

### 2. 主数据字段管理
- 关联实体的字段管理
- 支持多种字段类型（字符串、数字、日期、布尔、文本）
- 字段必填性设置
- 字段默认值配置
- 字段描述说明

### 3. 数据质量规则管理
- 多种规则类型（唯一性、格式、范围、引用、自定义）
- 规则严重级别设置（错误、警告、信息）
- 执行质量检查
- 规则表达式配置

### 4. 主数据记录管理
- 记录的增删改查
- 记录发布功能
- Excel 导入导出
- 动态列显示（基于实体字段）
- 记录详情查看

## 项目结构

```
bone-masterdata-app/
├── src/
│   ├── pages/              # 页面组件
│   │   ├── EntityManagement.tsx
│   │   ├── FieldManagement.tsx
│   │   ├── QualityRuleManagement.tsx
│   │   └── RecordManagement.tsx
│   ├── services/           # API 服务
│   │   └── api.ts
│   ├── types/              # TypeScript 类型定义
│   │   └── index.ts
│   ├── App.tsx             # 应用根组件
│   ├── main.tsx            # 应用入口
│   ├── App.css             # 应用样式
│   └── index.css           # 全局样式
├── .env.example            # 环境变量示例
├── tsconfig.json           # TypeScript 配置
├── vite.config.ts          # Vite 配置
├── package.json            # 项目依赖
└── README.md               # 项目说明
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 配置环境变量

复制 `.env.example` 为 `.env` 并根据需要修改配置：

```bash
cp .env.example .env
```

### 启动开发服务器

```bash
npm run dev
```

应用将在 `http://localhost:3003` 启动。

### 构建生产版本

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

### 代码检查

```bash
npm run lint
```

### 类型检查

```bash
npm run type-check
```

## API 配置

API 请求通过 Vite 代理转发到后端服务。默认配置：

- 前端地址：`http://localhost:3003`
- API 代理目标：`http://localhost:8080`
- API 前缀：`/api`

如需修改，请编辑 `vite.config.ts` 中的代理配置。

## 菜单导航

应用包含以下功能模块：

1. **主数据实体**
   - 实体管理
   - 字段管理

2. **数据质量**
   - 规则管理

3. **数据记录**
   - 记录管理

## 开发规范

### 命名约定
- 组件名：PascalCase（如 `EntityManagement`）
- 文件名：PascalCase（与组件名一致）
- 类型名：PascalCase
- 常量：UPPER_SNAKE_CASE

### 代码风格
- 使用 TypeScript 严格模式
- 遵循 ESLint 规则
- 组件使用函数式写法
- 使用 Ant Design Pro Components 简化开发

## 浏览器支持

- Chrome (最新版)
- Firefox (最新版)
- Safari (最新版)
- Edge (最新版)

## 许可证

MIT License
