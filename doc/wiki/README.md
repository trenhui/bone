# Bone 项目 Wiki

面向贡献者与内部协作者的**导航型知识库**。营销与快速上手仍以仓库根目录 [README.md](../../README.md) 为准；代码级速查可配合根目录 [CODE_WIKI.md](../CODE_WIKI.md) 与 [AGENTS.md](../../AGENTS.md)。

---

## 本 Wiki 目录

| 页面 | 说明 |
|------|------|
| [01-项目概览](./01-项目概览.md) | 定位、四大引擎、技术栈摘要 |
| [02-仓库结构与模块](./02-仓库结构与模块.md) | Maven / 前端 workspace 与当前聚合关系 |
| [03-本地开发与构建](./03-本地开发与构建.md) | 编译、测试、前后端启动、端口 |
| [04-数据与部署](./04-数据与部署.md) | 库表约定、初始化脚本、环境变量、制品形态 |
| [05-工程规范与安全](./05-工程规范与安全.md) | DDD/CQRS、质量工具、安全注意项 |
| [06-延伸阅读](./06-延伸阅读.md) | `doc/` 权威文档与设计索引 |
| [07-P0-TODO看板](./07-P0-TODO看板.md) | 平台内核未完成项与工程债 |
| [08-blueprint与主工程对齐](./08-blueprint与主工程对齐.md) | DDD 蓝图模块构建与 CI |
| [09-密钥与Git历史](./09-密钥与Git历史.md) | Gitleaks 工作区 / baseline、历史清理流程 |

---

## 一句话

**Bone（Build Once, Natively Everywhere）**：企业级全栈平台，元数据驱动 + DDD/CQRS 后端，React + Qiankun 微前端。

---

## 与仓库其他文档的关系

- **[CLAUDE.md](../../CLAUDE.md)**：面向 Claude Code 的短指南（命令、红线、提交规范）。
- **[AGENTS.md](../../AGENTS.md)**：面向 AI 助手的完整项目说明（模块、依赖规则、测试与质量）。
- **[CODE_WIKI.md](../CODE_WIKI.md)**：技术栈、模块树、关键类、构建与数据库等**单文件长文档**（部分内容可能随版本演进，以本 Wiki 的「模块」页与根 `pom.xml` 为准）。
- **[CONTRIBUTING.md](../../CONTRIBUTING.md)**：提交前检查、CI 说明、Spotless / Gitleaks / blueprint 约定。
- **[doc/README.md](../README.md)**：`doc/` 权威文档与历史废止目录总索引。

---

*Wiki 生成基准：仓库内 `pom.xml` 聚合模块与 `bone-frontend` workspace（2026）。*
