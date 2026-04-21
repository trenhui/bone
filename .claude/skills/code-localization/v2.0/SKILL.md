# code-localization - 代码定位技能 v2.0

## 职责
扫描现有代码库，理解项目结构、命名规范、技术选型，为生成契约提供上下文。

## 执行步骤
1. **理解需求**：从用户自然语言中提取模块、功能范围
2. **Glob 搜索**：查找相关目录、文件
3. **读取关键文件**：读取 pom.xml/package.json、现有接口、实体示例
4. **总结模式**：提取命名规范、分层方式、技术选型
5. **输出上下文**：提供给 architect 用于生成契约

## 输出格式
```
## 📋 代码本地化扫描结果

### 模块路径
- 根目录: `path/to/module`
- 包名: `com.bone.module...`

### 现有架构模式
- 分层: controller → service → repository
- 数据库: MyBatis Plus
- 前端框架: React + Ant Design

### 命名规范示例
- 实体: `User.java`
- 控制器: `UserController.java`
- 服务接口: `UserService.java` 实现 `UserServiceImpl.java`

### 技术栈匹配
- 需求需要的技术栈 ✓ 已经存在 / ✗ 需要新增
```

## 边界
- 不修改代码，只读
- 只提供上下文，不生成契约
- 不评估需求合理性，只理解现有结构
