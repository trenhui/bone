# Vue 3 到 React 转换指南

## 项目概述

本项目已成功将 Vue 3 + Element Plus 项目转换为 React + Ant Design 项目。

## 已完成的工作

### 1. 核心基础设施

#### 状态管理 (Zustand)
- ✅ 实现了应用状态管理 (`useAppStore`)
- ✅ 实现了用户状态管理 (`useUserStore`)
- ✅ 实现了设置状态管理 (`useSettingsStore`)
- ✅ 实现了标签栏状态管理 (`useTagsViewStore`)
- ✅ 实现了权限状态管理 (`usePermissionStore`)
- ✅ 使用持久化中间件保存状态

#### 路由 (React Router v6)
- ✅ 配置了完整的路由结构
- ✅ 实现了路由懒加载
- ✅ 支持路由重定向
- ✅ 包含所有页面路由

#### 布局组件
- ✅ 主布局组件 (Layout)
- ✅ 侧边栏导航 (Sidebar)
- ✅ 顶部导航栏 (NavBar)
- ✅ 标签栏 (TagsView)
- ✅ 设置面板 (Settings)
- ✅ 主内容区 (AppMain)
- ✅ 支持响应式布局和移动端

#### 请求工具
- ✅ Axios 请求拦截器
- ✅ Axios 响应拦截器
- ✅ 错误处理
- ✅ 使用 Ant Design 的通知组件

### 2. 页面转换

#### 核心页面
- ✅ 登录页面 (Login) - 完整实现
- ✅ 仪表盘 (Dashboard) - 完整实现
- ✅ 错误页面 (401, 404) - 占位符
- ✅ 其他页面 - 占位符

#### 页面结构
```
src/views/
├── login/          # 登录页面
├── dashboard/      # 仪表盘
├── error-page/     # 错误页面
├── myJob/          # 我的作业
├── jobManage/      # 作业管理
├── claimManage/    # 赔案管理
├── jobConfig/      # 作业配置
├── policyConfig/   # 团单个险管理
├── systemManage/   # 系统管理
├── codeTools/      # 代码工具
├── claimImageDetail/ # 赔案影像
├── claimDetail/    # 赔案详情
└── redirect/       # 重定向
```

### 3. 核心组件

#### 占位符组件
- ✅ Placeholder - 开发中页面占位符

### 4. 工具和配置

#### 配置文件
- ✅ Vite 配置
- ✅ TypeScript 配置
- ✅ ESLint 配置
- ✅ 别名配置 (@/)

#### 工具函数
- ✅ 请求工具 (request)
- ✅ 设置 (settings)
- ✅ 枚举 (enums)

## 技术栈对比

| 方面 | Vue 3 版本 | React 版本 |
|------|-----------|-----------|
| 框架 | Vue 3 | React 18 |
| 构建工具 | Vite | Vite |
| UI 库 | Element Plus | Ant Design 5 |
| 路由 | Vue Router | React Router v6 |
| 状态管理 | Pinia | Zustand |
| 语言 | TypeScript | TypeScript |
| 样式 | SCSS | CSS |

## 下一步工作

### 优先级 1 - 核心功能页面

#### 1. 登录页面优化
- [ ] 集成真实的 API
- [ ] 添加验证码功能
- [ ] 记住密码功能
- [ ] 国际化支持

#### 2. 仪表盘优化
- [ ] 集成真实的数据 API
- [ ] 添加图表组件 (ECharts/Recharts)
- [ ] 实现数据刷新
- [ ] 添加更多统计指标

#### 3. 我的作业页面
逐个转换以下页面：
- [ ] myJob/precheck - 我的初审
- [ ] myJob/entry - 我的录入
- [ ] myJob/qualityCheck - 我的质检
- [ ] myJob/audit - 我的审核
- [ ] myJob/review - 我的复核

### 优先级 2 - 业务功能页面

#### 作业管理
- [ ] jobManage/groupSign/list - 团险签收列表
- [ ] jobManage/groupSign/detail - 团险签收详情
- [ ] jobManage/groupSign/create - 新批次签收
- [ ] jobManage/claimHandOver - 转交赔案
- [ ] jobManage/claimDistribute - 分配赔案
- [ ] jobManage/uploadRecord - 导入记录

#### 赔案管理
- [ ] claimManage/pushFail - 推送失败
- [ ] claimManage/pushFail/HandleRecord - 处理记录
- [ ] claimManage/copyClaim - 复制赔案

#### 作业配置
- [ ] jobConfig/jobBaseConfig - 标准作业配置
- [ ] jobConfig/bizIdentityConfig/list - 主体专属列表
- [ ] jobConfig/bizIdentityConfig/config - 主体专属配置
- [ ] jobConfig/bizIdentityConfig/create - 创建主体专属

### 优先级 3 - RenderEngine 组件转换

RenderEngine 是核心的表单引擎，需要仔细转换：

#### 基础组件 (base)
- [ ] Block - 块组件
- [ ] ButtonGroup - 按钮组
- [ ] Custom - 自定义组件
- [ ] DateRange - 日期范围
- [ ] DateTime - 日期时间
- [ ] FieldSet - 字段集
- [ ] Form - 表单
- [ ] Input - 输入框
- [ ] InputNum - 数字输入
- [ ] MainBlock - 主块
- [ ] Page - 页面
- [ ] Render - 渲染组件
- [ ] SelectCtrl - 选择控件
- [ ] SelectCtrlWithoutForm - 无表单选择控件
- [ ] SelectDrop - 下拉选择
- [ ] SelectDropWithoutForm - 无表单下拉选择
- [ ] SmartInput - 智能输入
- [ ] Table - 表格
- [ ] Upload - 上传

#### Hooks
Vue hooks 需要转换为 React hooks：
- [ ] useLinkageRule - 联动规则
- [ ] useScopeData - 作用域数据
- [ ] useBaseComponentConfig - 基础组件配置
- [ ] useFieldTableLinkageRule - 字段表联动规则
- [ ] useBaseComponentProperty - 基础组件属性

### 优先级 4 - 系统管理和配置页面

#### 团单个险管理
- [ ] policyConfig/groupPolicyList - 团险保单管理
- [ ] policyConfig/policyRuleConfig - 保单规则配置

#### 系统管理
- [ ] systemManage/modelManage/modelList - 数据模型管理
- [ ] systemManage/modelManage/fieldList - 字段管理
- [ ] systemManage/optionConfig - 系统选项配置
- [ ] systemManage/eventManage - 事件管理
- [ ] systemManage/eventManage/create - 创建事件
- [ ] systemManage/groupIndividualConfig - 团单个险配置

#### 代码工具
- [ ] codeTools/codeGeneration - 代码生成
- [ ] codeTools/dataSourceConfiguration - 数据源配置

### 优先级 5 - 赔案详情页面

- [ ] claimImageDetail/Edit - 赔案影像管理
- [ ] claimImageDetail - 查看赔案影像
- [ ] claimDetail - 赔案详情

## 转换指南

### 1. Vue SFC 到 React 函数组件的转换

#### 模板 (template) → JSX
```vue
<!-- Vue -->
<template>
  <div class="container">
    <el-button @click="handleClick">点击</el-button>
    <p>{{ message }}</p>
  </div>
</template>
```

```jsx
// React
import { Button } from 'antd';
const Component = () => {
  return (
    <div className="container">
      <Button onClick={handleClick}>点击</Button>
      <p>{message}</p>
    </div>
  );
};
```

#### Script Setup → React 函数组件
```vue
<!-- Vue -->
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';

const count = ref(0);
const doubleCount = computed(() => count.value * 2);

onMounted(() => {
  console.log('mounted');
});
</script>
```

```jsx
// React
import { useState, useMemo, useEffect } from 'react';

const Component = () => {
  const [count, setCount] = useState(0);
  const doubleCount = useMemo(() => count * 2, [count]);

  useEffect(() => {
    console.log('mounted');
  }, []);
};
```

### 2. Element Plus → Ant Design 组件映射

| Element Plus | Ant Design | 说明 |
|-------------|-----------|------|
| `el-button` | `Button` | 按钮 |
| `el-input` | `Input` / `Input.Password` | 输入框 |
| `el-select` | `Select` | 选择器 |
| `el-table` | `Table` | 表格 |
| `el-form` | `Form` | 表单 |
| `el-form-item` | `Form.Item` | 表单项 |
| `el-dialog` | `Modal` | 对话框 |
| `el-drawer` | `Drawer` | 抽屉 |
| `el-message` | `message` | 消息提示 |
| `el-notification` | `notification` | 通知 |
| `el-switch` | `Switch` | 开关 |
| `el-card` | `Card` | 卡片 |
| `el-tag` | `Tag` | 标签 |
| `el-pagination` | `Pagination` | 分页 |
| `el-date-picker` | `DatePicker` / `RangePicker` | 日期选择 |
| `el-upload` | `Upload` | 上传 |

### 3. Vue Router → React Router

#### 路由配置
```typescript
// Vue Router
const routes = [
  {
    path: '/login',
    component: () => import('@/views/login/index.vue'),
  },
];
```

```typescript
// React Router
const router = createBrowserRouter([
  {
    path: '/login',
    element: <Login />,
  },
]);
```

#### 路由导航
```typescript
// Vue Router
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();
router.push('/dashboard');
const query = route.query;
```

```typescript
// React Router
import { useNavigate, useSearchParams, useParams, useLocation } from 'react-router-dom';

const navigate = useNavigate();
const [searchParams] = useSearchParams();
const params = useParams();
const location = useLocation();
navigate('/dashboard');
const query = Object.fromEntries(searchParams);
```

### 4. Pinia → Zustand

#### Store 定义
```typescript
// Pinia
import { defineStore } from 'pinia';

export const useUserStore = defineStore('user', {
  state: () => ({
    token: '',
    userInfo: null,
  }),
  actions: {
    setToken(token) {
      this.token = token;
    },
  },
});
```

```typescript
// Zustand
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

export const useUserStore = create<UserState>()(
  persist(
    (set, get) => ({
      token: '',
      userInfo: null,
      setToken: (token: string) => set({ token }),
    }),
    {
      name: 'user-store',
      storage: createJSONStorage(() => localStorage),
    }
  )
);
```

#### Store 使用
```typescript
// Pinia
const userStore = useUserStore();
userStore.setToken('abc123');
const token = userStore.token;
```

```typescript
// Zustand
const userStore = useUserStore();
userStore.setToken('abc123');
const token = userStore.token;
// 或者使用选择器优化渲染
const token = useUserStore(state => state.token);
const setToken = useUserStore(state => state.setToken);
```

### 5. Composition API → React Hooks 映射

| Vue | React | 说明 |
|-----|-------|------|
| `ref` | `useState` | 响应式数据 |
| `reactive` | `useState` / `useReducer` | 响应式对象 |
| `computed` | `useMemo` | 计算属性 |
| `watch` | `useEffect` | 监听器 |
| `watchEffect` | `useEffect` | 副作用 |
| `onMounted` | `useEffect(() => {}, [])` | 挂载 |
| `onUnmounted` | `useEffect(() => () => {}, [])` | 卸载 |
| `provide/inject` | `createContext/useContext` | 依赖注入 |

## 开发建议

### 1. 逐步迭代
- 不要一次性转换所有页面
- 优先转换核心功能页面
- 每个页面转换后进行测试

### 2. 保持 API 兼容性
- 复用现有的 API 接口
- 保持数据结构不变
- 使用相同的错误处理逻辑

### 3. 组件复用
- 创建可复用的业务组件
- 参考 Element Plus 的 API 设计 Ant Design 组件
- 保持组件的 Props 接口相似

### 4. 测试策略
- 单元测试组件逻辑
- 集成测试页面流程
- E2E 测试关键路径

### 5. 性能优化
- 使用 React.memo 优化渲染
- 使用 useMemo 和 useCallback
- 实现路由级别的代码分割

## 项目结构

```
tpa-sass-react/
├── src/
│   ├── api/              # API 接口
│   ├── assets/           # 静态资源
│   ├── components/       # 组件
│   │   ├── Layout/       # 布局组件
│   │   ├── Placeholder/  # 占位符组件
│   │   └── RenderEngine/ # 渲染引擎 (待转换)
│   ├── enums/            # 枚举
│   ├── hooks/            # 自定义 Hooks
│   ├── layouts/          # 布局 (旧)
│   ├── router/           # 路由配置
│   ├── store/            # Zustand 状态管理
│   ├── styles/           # 全局样式
│   ├── utils/            # 工具函数
│   ├── views/            # 页面组件
│   ├── App.tsx           # 应用入口
│   ├── main.tsx          # 入口文件
│   ├── README.md         # 旧文档
│   └── vite-env.d.ts     # Vite 类型定义
├── public/               # 公共资源
├── .eslintrc.json        # ESLint 配置
├── index.html            # HTML 模板
├── package.json          # 依赖配置
├── tsconfig.app.json     # TypeScript 配置
├── tsconfig.json         # TypeScript 配置
├── tsconfig.node.json    # TypeScript 配置
└── vite.config.ts        # Vite 配置
```

## 常见问题

### 1. 如何处理 Vue 的 slot？
```jsx
// React 使用 children 或 render props
const Component = ({ header, children, footer }) => (
  <div>
    <div className="header">{header}</div>
    <div className="content">{children}</div>
    <div className="footer">{footer}</div>
  </div>
);
```

### 2. 如何处理 Vue 的 v-model？
```jsx
// React 使用受控组件
const [value, setValue] = useState('');
<Input value={value} onChange={(e) => setValue(e.target.value)} />
```

### 3. 如何处理 Vue 的指令？
- `v-if` / `v-else` → 条件渲染
- `v-for` → map
- `v-show` → style display
- `v-bind` → props
- `v-on` → event handlers

## 参考资源

- [React 文档](https://react.dev/)
- [Ant Design 文档](https://ant.design/)
- [React Router 文档](https://reactrouter.com/)
- [Zustand 文档](https://zustand-demo.pmnd.rs/)
- [Vite 文档](https://vitejs.dev/)

---

**祝转换顺利！如有问题，请参考原 Vue 项目的代码结构和逻辑。**
