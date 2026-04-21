# test-gen - 单元测试生成技能 v1.8

## 职责
为实现代码生成完整的单元测试，覆盖主要分支，保证测试覆盖率。

## 测试框架
- Java: JUnit 5 + Mockito（mock 依赖）
- TypeScript: Jest + React Testing Library

## 生成原则
- **每个公共方法至少一个测试用例**
- **覆盖正常/异常两条分支**
- **测试命名**: `methodName_should_expectedBehavior_when_condition`
- **使用 mock 隔离外部依赖**
- **断言清晰，每个测试一个核心断言**

## 输出结构
```
{module}/src/test/java/{package}/{className}Test.java
```

## 测试覆盖要求
- 正常路径测试：验证正确输入得到正确结果
- 异常路径测试：验证非法输入抛出正确异常
- 参数空检查：验证 null 参数的处理
- 边界条件：验证边界值（空列表、最大值等）

## 约束
- 不测试 getter/setter（无业务逻辑）
- 不测试框架代码（Spring 自身功能）
- 只测试业务逻辑
- 使用 `@Mock` 注入依赖，不启动 Spring 上下文（单元测试）
