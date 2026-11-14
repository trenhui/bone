# Comet SDK `check run` 在 macOS 下「核对检查输入」阶段锁死 — 阻塞记录

> 关联 change：`synapclaim-architecture-convergence`
> 创建日期：2026-11-09
> 状态：代码交付已完成（已 PUSH）；Comet 归档（verify / archive）因 SDK 锁 bug 阻断，待上游修复。

## 环境
- OS: macOS (Apple Silicon)
- Node: v24.10.0（`engines` 要求 `^22.16.0 || >=24.0.0`，满足）；另测 v22.11.0 同样卡。
- Comet: `@rpamis/comet` 0.5.2（npm 全局，与 `main` `a61a2e5b` 同版）。
- 工作流: Classic（`comet workflow resolve --activate`）。

## 复现
```bash
comet check run synapclaim-architecture-convergence build -- ./scripts/check.sh
# 或 comet check run synapclaim-architecture-convergence build --local -- ./scripts/check.sh
```

## 症状
1. 卡在 `核对检查输入：0 项`（0.5.1 为 `15 项`），等待全局写锁
   `.../.runtime-store-v2/<hash>/.write.lock` 达 **30 秒**后超时，退出码 `70`。
2. 0.5.2 新增告警：`file lock ... changed while held; kept it for inspection`。
3. 锁由 SDK 自身 spawn 的子进程创建后**静默崩溃**（pid 已死、`stderr` 为空、无 SDK 日志），主进程误判锁占用。

## 诊断（已排除外部因素）
- 无并发进程持锁：`pgrep` / `lsof` / `fuser` 均无活持有者；锁为"文件存在 + pid 检测"机制，非 OS `flock`。
- 清理锁文件无效——子进程每次都重建锁后崩溃。
- `--local`、官方 `mark-unknown` + `retry` 复位、持续清理僵死锁均无效。
- 升级 0.5.2（claim 阶段已不卡锁）后该 check 阶段仍卡；`main` 与 0.5.2 同版无修复；降级 0.5.0 因 npm 目录冲突失败且 store 不兼容风险高。
- 换 Node 22.11.0 / 24.10.0 均卡。
- `check.sh` 与 `mvn clean test` 独立运行均 PASS（59 步 / 1328 例 0 失败），非代码/门禁问题。

## 根因定论
SDK 在「核对检查输入」阶段 spawn 的子进程生命周期管理存在缺陷（崩溃后锁未释放、主进程 30s 锁等待无 fallback）。这是 SDK **固有 bug**，外部无法绕过；**不能伪造收据推进**（否则破坏工作流诚信）。

## 影响
`synapclaim-architecture-convergence` 的 **Build Check 无法推进** → change 卡在 `build` 阶段，无法 `verify` / `archive`。

## 已采取动作（按业界最佳实践）
- **升级**：Comet 0.5.1 → 0.5.2（最新稳定版 = `main`）；claim 阶段锁问题已修复，check 阶段未修复。
- **备份**：`.comet` 运行时完整备份于 `/tmp/comet-backup-20261109.tar.gz`（及 `-b`）。
- **状态复位**：Build Check Action 复位为待派发（attempt 9），提交 `38c1868f6` 已 PUSH。
- **替代验证**：`scripts/check.sh` 独立运行全 PASS（59 步），`mvn clean test` 1328 例 0 失败 —— 代码质量有独立保证。
- **缺陷上报**：已向 `rpamis/comet` 起草 issue（见下方"推动修复"）。

## 推动修复（缺陷上报）
- 预填 issue 链接（标题 + 正文已填好，登录 `trenhui` 账号点 Submit 即可）——
  见对话中已生成的 `https://github.com/rpamis/comet/issues/new?title=...&body=...`（完整 URL 可在本地由 `python3` 重新生成，或已于会话中给出）。
- 或在终端 `gh auth login`（关联 `trenhui` 账号生成 token）后由协作者提交。

## 当前交付状态
- ✅ 代码交付：28/28 任务、全部 PUSH（含状态复位提交 `38c1868f6`）。
- ⏸️ Comet 归档：待 SDK 修复后 `comet check run … build` → `verify` → `archive`；或在无此 bug 的环境（如 Linux CI）补跑。
