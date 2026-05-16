# 贡献指南（Bone）

感谢参与 Bone 仓库。本文约定与 **CI**、**AGENTS.md** 对齐，便于可重复构建与安全基线。

## 开发环境

- JDK **17+**、Maven **3.8+**
- 前端：`bone-frontend` 使用 **npm workspaces** + **`package-lock.json`**（`npm ci`，勿混用 pnpm 除非全仓迁移）
- **MySQL / Redis / JWT**：仓库根 [`.env.example`](.env.example) → 复制为 `.env`（已 gitignore），`source scripts/dev/load-env.sh`；说明见 [config/env/README.md](config/env/README.md)
- 库表：`bone-init.sql`；连通性 `./scripts/dev/db-verify.sh`

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
  -pl bone-framework,bone-platform,bone-business,bone-sdk,bone-tool,\
bone-engine/bone-metadata-sdk,bone-engine/bone-metadata-server,\
bone-engine/bone-metadata-engine/bone-metadata-engine-core,bone-engine/bone-metadata-engine/bone-metadata-engine-starter,\
bone-engine/bone-integration,\
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
- 本地/测试配置使用 **`${ENV_VAR:}`**；参考 `bone-business/tpa-saas/**/backup/README.md`。
- 若密钥曾误提交 Git，须 **轮换**；历史存量见 `.gitleaks.baseline.json`，彻底清除用 [doc/wiki/09-密钥与Git历史.md](doc/wiki/09-密钥与Git历史.md) 中的 `git filter-repo` 流程。

## 架构与文档真源

**单一事实来源（SSOT）**：设计、规范与操作手册只维护在下列位置；勿在 `src/main/`、仓库根目录或 IDE 辅助目录新增平行长篇 Markdown。

| 范围 | 权威文档 |
|------|----------|
| 架构索引 | [doc/architecture/README.md](doc/architecture/README.md) |
| DDD / 分层 | [doc/architecture/Bone-DDD-最终实践方案.md](doc/architecture/Bone-DDD-最终实践方案.md) |
| 总体架构 | [doc/architecture/BONE-总体架构设计方案.md](doc/architecture/BONE-总体架构设计方案.md) |
| 数据库 | [doc/architecture/数据库开发规范.md](doc/architecture/数据库开发规范.md)、[初始脚本.sql](doc/architecture/初始脚本.sql) |
| 模块设计 | [doc/design/modules/](doc/design/modules/) |
| Agentic 工程 | [doc/Agenticx编程/Bone-Agentic-Engineering.md](doc/Agenticx编程/Bone-Agentic-Engineering.md) |
| Metadata SDK | [bone-engine/bone-metadata-sdk/README.md](bone-engine/bone-metadata-sdk/README.md)（快速开始）→ [doc/](bone-engine/bone-metadata-sdk/doc/)（使用指南 + 最佳实践） |
| 扩展引擎 | [bone-engine/bone-extension-engine/README.md](bone-engine/bone-extension-engine/README.md)、[docs/使用指南.md](bone-engine/bone-extension-engine/docs/使用指南.md) |
| 新人 / AI 总览 | [AGENTS.md](AGENTS.md)、[doc/wiki/](doc/wiki/) |

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
