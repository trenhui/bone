# 元数据和采购模块代码优化计划

## 1. 问题分析

### 1.1 工具类重复

**主要问题**：在bone-smartmeta和bone-procurement模块中存在大量重复的工具类实现，与bone-core中的工具类功能重叠：

| 重复方法 | bone-smartmeta | bone-core | 备注 |
|---------|---------------|----------|------|
| `isEmpty(Object)` | CommonUtils | ObjectUtils | 实现几乎完全相同 |
| `isNotEmpty(Object)` | CommonUtils | ObjectUtils | 实现几乎完全相同 |
| `getStringValue(Map, String, String)` | CommonUtils | MapUtils | 实现几乎完全相同 |
| `getBooleanValue(Map, String, Boolean)` | CommonUtils | MapUtils | 实现几乎完全相同 |
| `getIntegerValue(Map, String)` | CommonUtils | MapUtils | 实现几乎完全相同 |
| `deepCopyMap(Map)` | CommonUtils | MapUtils | 实现几乎完全相同 |
| `createConcurrentHashMap(int)` | CommonUtils | MapUtils | 实现完全相同 |
| `equals(Object, Object)` | CommonUtils | ObjectUtils | 实现完全相同 |
| `compare(Object, Object)` | CommonUtils | ObjectUtils | 实现几乎完全相同 |
| `safeToString(Object)` | CommonUtils | ObjectUtils | 实现完全相同 |

### 1.2 常量类重复

**主要问题**：错误代码和业务常量分散在多个模块中，缺乏统一管理：

- `bone-smartmeta` 中的 `ErrorCodes` 和 `ErrorCodeConstants` 功能重叠
- `bone-procurement` 中的 `ProcurementConstants` 与其他模块的常量管理方式不一致

### 1.3 内部工具方法重复

**主要问题**：在ExpressionEngine类中定义了与ObjectUtils重复的isEmpty方法

## 2. 优化目标

1. **消除代码重复**：移除bone-smartmeta和bone-procurement中的重复工具方法
2. **统一工具类架构**：遵循之前为bone-core创建的工具类架构
3. **规范常量管理**：建立统一的常量定义和使用标准
4. **提升可维护性**：集中管理通用功能，简化更新和维护
5. **确保向后兼容**：在优化过程中不破坏现有功能

## 3. 优化方案

### 3.1 工具类优化

**目标**：统一使用bone-core中的工具类，移除重复实现

**具体步骤**：

1. **更新bone-smartmeta中的CommonUtils类**：
   - 保留类但修改方法实现，使其调用bone-core中对应的方法
   - 添加@Deprecated注解，提示开发者将来使用bone-core中的工具类
   - 添加详细的JavaDoc说明迁移路径

2. **更新引用**：
   - 识别并更新bone-smartmeta中所有对CommonUtils的直接引用
   - 优先考虑使用bone-core中的对应工具类

### 3.2 常量类优化

**目标**：统一常量定义和管理方式

**具体步骤**：

1. **整合错误代码常量**：
   - 保留bone-smartmeta中的`ErrorCodes`作为主要错误代码常量类
   - 移除或废弃`ErrorCodeConstants`，将其内容合并到`ErrorCodes`
   - 建立清晰的错误代码命名规范和分类

2. **优化业务常量**：
   - 确保`ProcurementConstants`遵循与其他模块一致的常量命名和组织结构
   - 为所有常量添加详细的注释说明

### 3.3 内部工具方法清理

**目标**：移除内部类中重复的工具方法实现

**具体步骤**：

1. **修改ExpressionUtils**：
   - 将`isEmpty`方法修改为调用`ObjectUtils.isEmpty`
   - 考虑将其他可能的内部工具方法提取到适当的工具类中

## 4. 具体实施计划

### 4.1 第一阶段：更新CommonUtils类

1. 修改`/Users/renhui.trh/code/bone/bone-engine/bone-smartmeta/bone-smartmeta-engine/src/main/java/com/bone/smartmeta/engine/util/CommonUtils.java`：
   - 添加对bone-core工具类的导入
   - 修改方法实现，调用对应工具类方法
   - 添加@Deprecated注解

### 4.2 第二阶段：整合常量类

1. 修改`ErrorCodes`和`ErrorCodeConstants`，整合错误代码
2. 确保所有引用都更新到正确的常量类

### 4.3 第三阶段：清理内部重复方法

1. 修改`ExpressionEngine.ExpressionUtils`类中的`isEmpty`方法

### 4.4 第四阶段：验证和测试

1. 确保所有修改后的代码能够正常编译
2. 运行测试用例确保功能正常
3. 检查是否还有遗漏的重复代码

## 5. 最佳实践和规范

### 5.1 工具类使用规范

1. **优先使用骨核模块的工具类**：
   - 任何通用功能首先检查bone-core中是否已有实现
   - 新的通用功能应添加到bone-core对应工具类中

2. **工具类命名规范**：
   - 使用功能前缀+Utils命名（如：ObjectUtils, MapUtils）
   - 明确工具类职责范围，遵循单一职责原则

### 5.2 常量类规范

1. **常量命名**：
   - 使用全大写，下划线分隔
   - 前缀应反映常量类型（如：ERROR_, STATUS_, ACTION_）

2. **常量组织**：
   - 按功能或业务领域分组
   - 使用内部枚举类组织相关常量（对于有多种属性的常量）

3. **避免硬编码**：
   - 所有魔法数字、字符串等必须定义为常量
   - 配置项应通过配置文件加载，而不是硬编码为常量

### 5.3 方法命名和实现规范

1. **方法命名**：
   - 动词开头，清晰表达功能
   - 遵循Java标准命名惯例

2. **方法实现**：
   - 简洁明了，专注单一功能
   - 包含适当的参数验证
   - 异常处理应一致且适当

## 6. 迁移注意事项

1. **向后兼容性**：
   - 保留现有方法签名以确保向后兼容
   - 使用@Deprecated注解和适当的文档提示迁移

2. **依赖管理**：
   - 确保所有模块正确依赖bone-core
   - 检查依赖版本一致性

3. **测试覆盖**：
   - 对所有修改的代码进行充分测试
   - 特别关注边界情况和错误处理

4. **文档更新**：
   - 更新相关API文档
   - 为开发者提供迁移指南

---

通过以上优化计划，我们将有效减少代码重复，提高代码质量，改善可维护性，并为未来的功能扩展打下良好基础。