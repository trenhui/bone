# Bone Agentic Engineering 团队宪法 v2.0

## 🎯 核心理念
人定义意图，AI 负责实现，契约保证质量，CI 担任裁判。

## 🏗️ 技术栈（约束而非锁定）
- 后端：Java ≥17 + Spring Boot 3.2.x + MyBatis Plus
- 前端：React ≥18 + Ant Design 5 + TypeScript ≥5.2 + @umijs/max
- 质量：JUnit 5 + JaCoCo（覆盖率 >80%）

## 🔄 标准工作流（4 个命令，禁止绕过）
| 命令 | 作用 | 产出 | 确认人 |
|:---|:---|:---|:---|
| `/plan` | 规划功能 | Contract.yaml（L1/L2） | 架构师（强制） |
| `/build` | 生成代码 + 单元测试 | 代码 + 单元测试 | AI 自检 |
| `/test` | 集成测试 + 契约验证 | 测试报告 | AI（L2 确认） |
| `/ship` | 交付审查 | PR + 双轨度量 | Guardian |

## ⚠️ 核心红线（CI 强制拦截）
1. 禁止修改 `generated/` 目录下的自动生成代码
2. 测试覆盖率 < 80% 禁止合并
3. 禁止返回 `null`（用 `Optional`）
4. Contract 所有者必须与 PR 作者一致（Contract 锁）
5. 禁止硬编码密钥、密码等敏感信息

## 🎯 编码规范（强制执行）

### Java 后端
- 遵循 Spring Boot  conventions
- Repository 接口定义在 domain，实现放在 infrastructure/persistence
- 优先使用构造函数注入，final 修饰依赖
- 异常使用 BusinessException，错误码统一管理
- 参数校验使用 Jakarta validation

### TypeScript 前端
- 遵循 React 函数组件 + Hooks 风格
- 使用 TypeScript 严格模式，禁止 `any`
- API 层和 UI 层分离，类型定义统一放在 types.ts
- 组件拆分粒度：单文件不超过 500 行

## 📝 Commit 规范
```
type(scope): description

type: feat|fix|docs|refactor|test|chore
scope: 模块名称（可选）
description: 小写开头，不超过 50 字符
```

**度量信息**（自动添加，由 Agentic Engine 生成）：
```
# Agentic: L1 attempts=N, L1 successes=N, build_time=Nmin
```

## 🚀 快速开始
```bash
git checkout -b feature/feature-name@yourname
claude "/plan 你的需求描述"
# 确认契约后
claude "/build"
claude "/test"
claude "/ship"
```
