# Bone 工程工具类优化方案

## 问题分析

通过对代码库的分析，发现存在以下主要问题：

1. **工具类重复实现**：
   - `com.bone.core.util.StringUtils` 和 `com.bone.smartmeta.engine.util.CommonUtils` 包含大量重复方法
   - `com.bone.core.util.CollectionUtils` 和 `com.bone.smartmeta.engine.util.CommonUtils` 也有重复方法
   - 方法实现几乎完全相同，如 `isEmpty()`, `deepCopyMap()`, `createConcurrentHashMap()` 等

2. **命名不规范**：
   - 包命名错误：`com.bone.integration.uitls`（应为 utils）
   - 类名不统一：有的叫 StringUtils，有的叫 CommonUtils

3. **功能划分混乱**：
   - 工具方法分散在不同模块，缺乏统一管理
   - 职责边界不清晰

4. **引入冗余**：
   - 相同功能的方法在多个地方重复实现
   - 维护成本高，容易产生不一致

## 优化方案

### 1. 统一工具类架构

```
com.bone.core.util
├── StringUtils.java     // 专注于字符串操作
├── CollectionUtils.java // 专注于集合操作
├── ObjectUtils.java     // 专注于对象操作（新增）
├── MapUtils.java        // 专注于Map操作（新增）
└── ValidationUtils.java // 专注于验证操作（新增）
```

### 2. 方法迁移与整合

| 方法 | 源位置 | 目标位置 |
|------|--------|----------|
| `isEmpty(Object)` | CommonUtils, StringUtils | ObjectUtils |
| `isNotEmpty(Object)` | CommonUtils, StringUtils | ObjectUtils |
| `hasText(String)` | StringUtils | StringUtils |
| `safeToString(Object)` | StringUtils | StringUtils |
| `equals(Object, Object)` | CommonUtils, StringUtils | ObjectUtils |
| `compare(Object, Object)` | CommonUtils, StringUtils | ObjectUtils |
| `concat(String...)` | StringUtils | StringUtils |
| `contains(Object, Object)` | StringUtils | CollectionUtils |
| `getStringValue(Map, String, String)` | CommonUtils, StringUtils | MapUtils |
| `getBooleanValue(Map, String, Boolean)` | CommonUtils, StringUtils | MapUtils |
| `getIntegerValue(Map, String)` | CommonUtils | MapUtils |
| `deepCopyMap(Map)` | CollectionUtils, CommonUtils | MapUtils |
| `deepCopyList(List)` | CollectionUtils, CommonUtils | CollectionUtils |
| `createConcurrentHashMap(int)` | CollectionUtils, CommonUtils | MapUtils |
| `validateNotEmpty(String, String)` | CommonUtils | ValidationUtils |
| `validateNotNull(Object, String)` | CommonUtils | ValidationUtils |

### 3. 步骤实施

1. **创建新的工具类结构**
2. **迁移方法实现**
3. **更新所有引用**
4. **移除重复的工具类**
5. **规范包命名**

## 最佳实践建议

1. **工具类设计原则**：
   - 遵循单一职责原则
   - 使用 final class 和 private 构造函数
   - 所有方法为静态方法
   - 提供完整的 JavaDoc 注释

2. **命名规范**：
   - 包名：使用小写，复数形式（如 utils，而非 util）
   - 类名：功能+Utils 格式（如 StringUtils）
   - 方法名：动词+名词格式（如 isEmpty, deepCopyMap）

3. **代码组织**：
   - 将所有核心工具类放在 bone-core 模块
   - 其他模块引入 bone-core 依赖
   - 避免在非核心模块中创建通用工具方法

4. **防止重复**：
   - 建立工具类使用检查机制
   - 定期进行代码审查，检查是否有新的重复工具类出现

## 迁移注意事项

1. **兼容性**：
   - 保留原方法签名以确保向后兼容
   - 使用 @Deprecated 标注旧方法

2. **测试**：
   - 对所有迁移的方法进行全面测试
   - 确保功能一致性

3. **文档更新**：
   - 更新 API 文档
   - 添加迁移指南

## 预期收益

1. **代码质量提升**：
   - 减少重复代码
   - 提高可维护性

2. **开发效率提升**：
   - 统一的工具方法查找位置
   - 降低学习成本

3. **减少错误**：
   - 避免不同实现之间的不一致
   - 集中维护，减少 bug