# Bone Wiki（导航型知识库）

面向贡献者与协作者的上手入口，偏"怎么跑起来、去哪找东西"。产品介绍与快速开始以仓库根 [README.md](../../README.md) 为准；代码级约定以 [AGENTS.md](../../AGENTS.md) 与 `doc/agents/` 为准。

## 页面导航

| 页面 | 内容 |
|------|------|
| [01-项目概览](./01-项目概览.md) | 定位、四大引擎、技术栈摘要 |
| [02-仓库结构与模块](./02-仓库结构与模块.md) | Maven / 前端 workspace 与聚合关系 |
| [03-本地开发与构建](./03-本地开发与构建.md) | 编译、测试、前后端启动、端口 |
| [04-数据与部署](./04-数据与部署.md) | 库表约定、初始化脚本、环境变量、制品形态 |
| [05-工程规范与安全](./05-工程规范与安全.md) | DDD/CQRS、质量工具、安全注意项 |
| [06-延伸阅读](./06-延伸阅读.md) | `doc/` 权威文档与设计索引 |
| [07-P0-TODO看板](./07-P0-TODO看板.md) | 平台内核未完成项与工程债 |
| [08-blueprint与主工程对齐](./08-blueprint与主工程对齐.md) | DDD 蓝图模块构建与 CI |
| [09-密钥与Git历史](./09-密钥与Git历史.md) | Gitleaks 工作区 / baseline、历史清理流程 |
| [10-MVP-范围清单](./10-MVP-范围清单.md) | MVP 范围定义（三方证据交叉：纳入 / 排除 / 验收） |
| [PR-feature-code-optimization516](./PR-feature-code-optimization516.md) | 本次 code-optimization 分支的 PR 说明 |

## 与其他文档的关系

- **[AGENTS.md](../../AGENTS.md)**：AI 助手的薄引用入口；完整说明在 [`doc/agents/`](../agents/README.md)。
- **[CONTRIBUTING.md](../../CONTRIBUTING.md)**：提交前检查、CI、Spotless / Gitleaks / blueprint 约定。
- **[doc/architecture/ 与 adr/](../architecture/README.md)**：架构与决策真源（HC 状态、门禁、ADR）。
- **[doc/prd/](../prd/) / [doc/design/modules/](../design/modules/README.md)**：需求与模块详设。
- **[doc/README.md](../README.md)**：`doc/` 总索引与本页的上层入口。

历史长文档 `CODE_WIKI.md` 已归档至 [`doc/archive/CODE_WIKI.md`](../archive/CODE_WIKI.md)，其内容与本 Wiki 重叠，不再维护。
