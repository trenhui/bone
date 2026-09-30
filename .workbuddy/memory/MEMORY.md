# Bone 项目长期要点

## Git 并发协作（夜间流水线共存）
- **夜间流水线会切分支/动 HEAD**：bone 仓库常有 codex/nightly-* 与 pr/* 分支在途。任何 git 写操作（add/commit/push）前先 `git branch --show-current`；commit 可能落到 PR 分支而非 dev。验证落点：`git branch --contains <sha>`。
- 推送模式：`git push origin HEAD:dev` 快进远程 dev（本地 HEAD 不在 dev 上时）；双远程一次 `git push origin <ref>` 同时到 Gitee+GitHub。
- 并发进程可能在我方提交与推送之间又推新提交：push 输出的 old..new 与本地 HEAD 不一致时，立刻 `git ls-remote origin refs/heads/dev` + `git branch --show-current` 排查。

## 模块修复模式（2026-09-29 实战）
- spotless 必须与 check.sh 同方式跑：**在线 + 根作用域** `mvn spotless:apply`；模块级离线 apply 会与根级在线 check 漂移（javadoc reflow 需两遍才收敛）。`tail` 管道会吃掉真实退出码，用 `> file 2>&1; echo $?`。
- HC-006 基线"只可收缩"：新增 JdbcTemplate 绕过一律改走 SDK `Repository.updateByCriteria(entity, Criteria)`（CAS 条件更新）。
- 错误码三件套：`{Module}ErrorCodes`（稳定码）+ `{Module}Errors`（码→HTTP 唯一表，static 块 fail-fast）+ i18n 台账；IDOR 用「仓储 findByIdAndTenant 双条件 + 服务层 userId 归属断言」两道闸。
- zsh 裸 `--include=*.java` 报 no matches（仍静默）→ 用 Grep 工具 glob 参数；`$PIPESTATUS` 是 bash 的，zsh 用 `$pipestatus[1]`。

## 分支纪律（2026-09-29 用户裁定，已固化 AGENTS.md §三）
- **AI 禁止自行创建/切换/重命名/删除分支**——仅限用户明确指示分支名时操作；默认在当前 checkout 分支工作；git 写操作前先 `git branch --show-current`。
- `pr/0010-0011-rollback` 分支已按用户指示合并进 dev（快进至 039f7e6ff）并删除（本地+双远程）；当前工作分支回归 dev。

## WorkBuddy 沙箱启动本地服务的三个坑（2026-09-29）
- **沙箱注入 `SERVER__PORT=50723`**（Spring 松弛绑定会接管 server.port）→ 启动任何 Spring Boot 用 `env -u SERVER__PORT -u SERVER__HOST java -jar ...`，且进程要用 run_in_background 独立任务跑（nohup 批量 & 会被回收）。
- **zsh `source .env` 在 URL `&` 处 parse error** → 环境变量显式传入命令行。
- **Vite dev server 会被 safe-delete shim 杀死**（重优化依赖时 bulk delete node_modules/.vite 超阈值）→ 启动前先 `rm -rf node_modules/.vite`。lsof/ps 在沙箱看不到部分进程（MySQL 实际在跑但端口显示 free），以 curl/客户端连通为准。

## Bone 本地全栈运行要点（2026-09-29 实测）
- 启动 jar 需 env：BONE_DB_PASSWORD=mysql123、BONE_JWT_SECRET=dev-only-secret-key-minimum-32-bytes-long、BONE_REDIS_HOST/PORT、SPRING_PROFILES_ACTIVE=dev；extension-studio 用 `dev,metadata`（持久化在 **H2 文件库** `data/extension-studio-metadata`，非 MySQL）。
- bone-notification 是内嵌库（无 main 类/端口），随 bone-system(8083)、bone-integration(8085) 运行；站内信 API `/api/v1/notification/messages?userId=`。metadata-server dev 端口必须 9001（网关路由默认指向）。
- 账号：admin/123456（平台租户0，有 iam:tenants:read 可用 X-Acting-Tenant-Id）、tenant_admin/123456（租户1001 DEMO）。
- 前端拓扑：`localhost:3000/<module>#/<route>`（qiankun，activeRule=/iam 等）；shell /api 代理 → 8888。绕网关直连后端必须带 X-Tenant-Id 头，否则 fail-closed 500（MissingTenantContextException）——这是设计非 bug。
- **实库会漂移于 bone-init.sql**：比对 information_schema 补齐（ntf_message.tenant_id、sys_alert_rule.version 曾缺失 → 站内信/告警 500）。改 DDL 时同步改 init.sql。
- 账号创建必须带 deptId（iam /depts/tree 取）；无密码本地 Redis 的配置里不要写 password 键（空串也会触发 AUTH 报错）。
