# 前端适配指南：ID格式变更

## 背景

根据 DDD 重构，所有模块的 ID 格式从 Long 类型变更为 UUID 格式（字符串）。前端需要适配这一变更，以确保与后端 API 的正确交互。

## 变更内容

### 1. ID 格式变更

| 模块 | 旧 ID 格式 | 新 ID 格式 |
|------|-----------|-----------|
| IAM | Long | String (UUID) |
| MasterData | Long | String (UUID) |
| Integration | Long | String (UUID) |
| System | Long | String (UUID) |

### 2. API 端点变更

所有 API 端点中涉及 ID 的参数和返回值都需要从 Long 类型改为 String 类型。

#### 示例：IAM 用户 API

- 旧：`GET /api/iam/users/{id}` 其中 `id` 为 Long
- 新：`GET /api/iam/users/{id}` 其中 `id` 为 String (UUID)

#### 示例：创建用户响应

- 旧：`{"code": 200, "data": 123, "message": "success"}`
- 新：`{"code": 200, "data": "550e8400-e29b-41d4-a716-446655440000", "message": "success"}`

## 前端适配步骤

### 1. 类型定义更新

更新 TypeScript 类型定义，将所有 ID 字段从 `number` 改为 `string`。

#### 示例：用户类型

```typescript
// 旧
interface User {
  id: number;
  username: string;
  email: string;
  // 其他字段
}

// 新
interface User {
  id: string;
  username: string;
  email: string;
  // 其他字段
}
```

### 2. API 调用更新

更新所有 API 调用，确保：

- 请求参数中的 ID 作为字符串传递
- 响应处理中 ID 作为字符串接收
- URL 路径中的 ID 作为字符串拼接

#### 示例：获取用户详情

```typescript
// 旧
const getUserById = async (id: number): Promise<User> => {
  const response = await axios.get(`/api/iam/users/${id}`);
  return response.data;
};

// 新
const getUserById = async (id: string): Promise<User> => {
  const response = await axios.get(`/api/iam/users/${id}`);
  return response.data;
};
```

### 3. 表单处理更新

更新表单处理逻辑，确保：

- 表单提交时 ID 作为字符串处理
- 表单验证规则适应字符串格式

### 4. 路由参数更新

更新路由配置，确保：

- 路由参数中的 ID 作为字符串处理
- 路由跳转时传递字符串格式的 ID

#### 示例：React Router

```typescript
// 旧
<Route path="/users/:id" element={<UserDetail />} />

// 新（保持不变，但组件内部处理需要更新）
<Route path="/users/:id" element={<UserDetail />} />

// 组件内部
const { id } = useParams<{ id: string }>();
```

### 5. 状态管理更新

更新状态管理（如 Redux、Context API 等），确保：

- 状态中的 ID 字段类型为 string
- 状态更新时正确处理字符串格式的 ID

### 6. 本地存储更新

更新本地存储逻辑，确保：

- 存储和读取的 ID 为字符串格式
- 与后端 API 交互时正确转换格式

## 测试建议

1. **单元测试**：更新所有涉及 ID 处理的单元测试
2. **集成测试**：测试 API 调用和响应处理
3. **端到端测试**：测试完整的用户流程

## 常见问题

### Q: 如何处理旧数据？

A: 后端会为现有数据生成 UUID 格式的业务 ID，前端无需处理旧数据的转换。

### Q: 如何验证 UUID 格式？

A: 可以使用正则表达式验证 UUID 格式：

```typescript
const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const isValidUuid = (id: string): boolean => {
  return uuidRegex.test(id);
};
```

### Q: 如何处理 URL 中的 UUID？

A: UUID 是 URL 安全的，可以直接作为 URL 路径参数使用。

## 总结

前端需要将所有 ID 相关的代码从处理 Long 类型改为处理 String 类型，以适配后端的 ID 格式变更。这是一个系统性的变更，需要仔细检查所有涉及 ID 处理的代码。

建议在测试环境充分验证后再部署到生产环境。