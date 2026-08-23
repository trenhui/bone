# 前端状态管理统一方案

## 背景

Bone 前端 8 个微应用此前各自维护 axios 实例、token 拦截器和状态管理方案，导致：
- 每个应用重复 ~60 行 axios 配置代码
- token 注入逻辑分散，bug 修复需要逐应用改动
- 部分应用用 Redux，部分无状态管理，风格不一致

## 方案

### 服务端状态：统一使用 @tanstack/react-query

通过 `@bone/shared-services` 提供：
- `createApiClient(baseURL)` — 统一的 axios 实例工厂（含 token 拦截、错误处理）
- `createQueryClient()` — 预配置的 React Query Client
- `createQueryKeys(scope, domains)` — 查询键命名空间工具

### 客户端状态：按需使用 Zustand

轻量场景直接用组件 state；需要跨页面共享的状态用 Zustand。

## 迁移步骤（以 bone-iam-app 为示范）

### 1. 添加依赖

```json
{
  "dependencies": {
    "@bone/shared-services": "1.0.0",
    "@bone/shared-types": "1.0.0",
    "@tanstack/react-query": "^5.51.0"
  }
}
```

### 2. 替换 axios 手写配置

**之前**（每个应用重复 ~60 行）：
```ts
import axios from 'axios';

let _qiankunToken: string | null = null;
export function setQiankunToken(token: string | null) { ... }

const api = axios.create({ baseURL: '...', timeout: 10000 });
api.interceptors.request.use(/* token 逻辑 */);
api.interceptors.response.use(/* 解包 + 401 处理 */);
```

**之后**（一行代码）：
```ts
import { createApiClient, setQiankunToken } from '@bone/shared-services';
export { setQiankunToken };
const api = createApiClient('/api/v1/iam');
```

### 3. 在 main.tsx 中接入 React Query

```tsx
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '@bone/shared-services';

const queryClient = createQueryClient();

ReactDOM.render(
  <QueryClientProvider client={queryClient}>
    <App />
  </QueryClientProvider>,
  document.getElementById('root')
);
```

### 4. 使用 React Query 替代手写 loading/error

**之前**：
```ts
const [loading, setLoading] = useState(false);
const [data, setData] = useState([]);
const [error, setError] = useState(null);

useEffect(() => {
  setLoading(true);
  getAccounts(1, 10)
    .then(res => setData(res.data.records))
    .catch(err => setError(err))
    .finally(() => setLoading(false));
}, []);
```

**之后**：
```ts
const { data, isLoading, error } = useQuery({
  queryKey: ['iam', 'accounts', { page: 1, size: 10 }],
  queryFn: () => getAccounts(1, 10),
});
```

## 迁移进度

| 应用 | shared-services 接入 | React Query 接入 | 备注 |
|------|---------------------|-----------------|------|
| bone-iam-app | ✅ | 待接入 | 示范应用 |
| bone-system-app | 待迁移 | - | |
| bone-masterdata-app | 待迁移 | - | |
| bone-integration-app | 待迁移 | - | |
| bone-metadata-app | 待迁移 | - | |
| bone-extension-app | 待迁移 | - | |
| bone-generator-app | 待迁移 | 已声明未使用 | |
| bone-shell | 待迁移 | - | |
