# 贡献指南（Bone）

感谢参与 Bone 仓库。本文约定与 **CI**、**AGENTS.md** 对齐，便于可重复构建与安全基线。

## 开发环境

- JDK **17+**、Maven **3.8+**
- 前端：`bone-frontend` 使用 **npm workspaces** + **`package-lock.json`**（`npm ci`，勿混用 pnpm 除非全仓迁移）
- **MySQL / Redis / JWT**：仓库根 [`.env.example`](.env.example) → 复制为 `.env`（已 gitignore），`source scripts/dev/load-env.sh`；说明见 [config/env/README.md](config/env/README.md)
- 库表：`bone-init.sql`；连通性 `./scripts/dev/db-verify.sh`

## 许可与贡献者授权（必读）

本仓库自 2026-10-11 起采用 [BSL 1.1](LICENSE) 授权（个人学习免费、商业使用需购买授权），
每个版本在 Change Date（当前为 2030-10-11）后自动转为 **Mozilla Public License 2.0**。

**你提交 PR 即表示同意以下条款：**

1. 你对你提交的内容拥有合法权利，并授权 Licensor 按 **BSL 1.1 及其 Change License（MPL 2.0）** 对外发布；
2. 你同意你的贡献在 Change Date 后随仓库一并转为 MPL 2.0，且该授权不可撤销；
3. 你提交的内容不得包含第三方受限制代码（与 MPL 2.0 不兼容的许可、商业 SDK、闭源片段）；
4. 商业授权不包含你的贡献——如需商业条款请单独与 Licensor 联系。

### AI 辅助产出

- AI **不构成著作权主体**，其产出本身不产生著作权。本仓库中 AI 辅助产出的内容由你以自身名义提交，
  并按上述条款授权给 Licensor，由 Licensor 统一主张与处分（见 [NOTICE](NOTICE) §四）。
- 提交前你应自行审核：不含第三方受版权保护代码的逐字复制、不含密钥与隐私数据、不含来路不明的闭源片段。
  **AI 生成不免除你的审核责任。**
- 请在 PR 描述中标注涉及 AI 辅助的部分，便于复核。

不接受上述条款的贡献请**不要**提交 PR。

## 提交前检查（推荐）

```bash
# 一键复现主 CI（推荐）
bash scripts/ci-local.sh
```

或分步执行：

```bash
bash scripts/scan-secrets.sh
mvn com.diffplug.spotless:spotless-maven-plugin:2.43.0:check --batch-mode
mvn -f bone-blueprint/pom.xml test --batch-mode
mvn clean verify -DskipITs=true -Dspotless.check.skip=true --batch-mode
cd bone-frontend && npm ci && npm run lint && npm run build --workspace=bone-shell
```

格式化修复（与 CI 相同模块范围，含 `bone-metadata-engine-*`）：

```bash
mvn com.diffplug.spotless:spotless-maven-plugin:2.43.0:apply --batch-mode \
  -pl bone-framework,bone-platform,bone-engine/studio-generator,\
bone-engine/bone-metadata-sdk,bone-engine/bone-metadata-server,\
bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-engine/bone-metadata-engine/bone-metadata-engine-ports,\
bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-engine/bone-metadata-engine/bone-metadata-engine-starter,\
bone-engine/bone-extension-engine/bone-extension-sdk,bone-engine/bone-extension-engine/bone-extension-studio \
  -am
```

## CI 说明

| Job | 作用 |
|-----|------|
| `backend-quality` | Spotless + `mvn verify` |
| `secrets-scan` | Gitleaks 工作区 + Git 历史（baseline 仅拦截**新增**泄露，见 [doc/wiki/09-密钥与Git历史.md](doc/wiki/09-密钥与Git历史.md)） |
| `blueprint-verify` | `bone-blueprint` 编译与测试 |
| `backend-security` | OWASP Dependency-Check |
| `frontend-quality` | `npm ci` + lint + shell build |

## 安全与配置

- **禁止**在 `src/main/resources` 提交真实密码、云 AK/SK、JWT secret。
- 本地/测试配置使用 **`${ENV_VAR:}`**；参考各模块 `application.yml` 与 `config/env/README.md`。
- 若密钥曾误提交 Git，须 **轮换**；历史存量见 `.gitleaks.baseline.json`，彻底清除用 [doc/wiki/09-密钥与Git历史.md](doc/wiki/09-密钥与Git历史.md) 中的 `git filter-repo` 流程。

## 架构与文档真源

**单一事实来源（SSOT）**：设计、规范与操作手册只维护在下列位置；勿在 `src/main/`、仓库根目录或 IDE 辅助目录新增平行长篇 Markdown。

| 范围 | 权威文档 |
|------|----------|
| 架构索引 | [doc/architecture/README.md](doc/architecture/README.md) |
| DDD / 分层 | [doc/architecture/Bone-DDD-最终实践方案.md](doc/architecture/Bone-DDD-最终实践方案.md) |
| 总体架构 | [doc/architecture/BONE-总体架构设计方案.md](doc/architecture/BONE-总体架构设计方案.md) |
| 数据库 | [doc/architecture/数据库开发规范.md](doc/architecture/数据库开发规范.md)；DDL **唯一真源**为根目录 `bone-init.sql`（开发全量重建，见 ADR-0001） |
| 模块设计 | [doc/design/modules/](doc/design/modules/) |
| Agentic 工程 | [doc/agenticx/Bone-Agentic-Engineering.md](doc/agenticx/Bone-Agentic-Engineering.md) |
| Metadata SDK | [bone-engine/bone-metadata-sdk/README.md](bone-engine/bone-metadata-sdk/README.md)（快速开始）→ [doc/](bone-engine/bone-metadata-sdk/doc/)（使用指南 + 最佳实践） |
| 元数据三模块定义与协作 | [doc/design/modules/元数据能力-实现映射与竞品对照.md](doc/design/modules/元数据能力-实现映射与竞品对照.md) |
| 扩展引擎 | [docs/使用指南.md](bone-engine/bone-extension-engine/docs/使用指南.md)（接入）→ [README](bone-engine/bone-extension-engine/README.md)（摘要） |
| 新人 / AI 总览 | [AGENTS.md](AGENTS.md)（薄引用入口）、[doc/agents/](doc/agents/README.md)（完整说明）、[doc/wiki/](doc/wiki/) |

- 平台未完成项：[doc/wiki/07-P0-TODO看板.md](doc/wiki/07-P0-TODO看板.md)
- 蓝图与主工程：[doc/wiki/08-blueprint与主工程对齐.md](doc/wiki/08-blueprint与主工程对齐.md)（`bone-blueprint` 为 DDD 参考实现；**不以「Blueprint v×」版本号**为门禁，见 Bone-DDD 附录 A）

**文档分层（推荐）**：模块根 `README.md` = 概述 + 快速开始 + 文档索引；详细内容放 `doc/` 或 `doc/architecture/`，避免多份重复长文。

**勿再提交**：`PROJECT_SUMMARY.md`、各模块 `*_SUMMARY.md`、根目录 `*PLAN*` / `*ANALYSIS*` 草稿、`dependency-tree.txt`、`**/*优化方案*.md` 放在 `src/main/` 下、`test_output.txt` / `build_output.txt` / `classpath.txt`、运行日志 `*.log`；以 `doc/architecture`、各模块 `README.md` / `doc/` 索引与源码为准。

## 提交信息

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 简要格式，例如：

```
feat(iam): add account detail query
fix(integration): harden rest client timeout
```
