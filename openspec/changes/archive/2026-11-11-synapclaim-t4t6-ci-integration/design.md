# 技术设计：T4/T6 接入 CI/PR（report-only）

## 范围与依据

落点对象：`doc/architecture/Bone-AI原生工程基础设施改造方案.md` 11.7.1（T4 变更影响面）、11.7.2（T6 质量度量），其口径模板已落成 `scripts/analyze-impact.py` / `scripts/analyze-metrics.py`（commit `f903c2048`，退出码恒 0）。本 change 只做**集成层**：把这两个 report-only 脚本暴露到 PR 流，不改脚本本体、不新增 check、不转阻断。

依据：DORA 四指标 / SPACE 五维（质量信号前置到 PR 而非事后审计）；GitHub「required but non-blocking checks」/ PR Checks 非阻断模式；gate-liveness / mutation-testing 共识（report-only 不掩盖门禁判别力，故本次严格保持非阻断、不因 CI 缺历史而假绿）。

## 关键取舍

| 决策点 | 采用的修法 | 为什么（业界依据） | 明确不采用 |
|---|---|---|---|
| 接入形态 | 非阻断步骤 `continue-on-error: true` | 11.7 纪律明确「report-only 两轮后再谈转阻断」；高风险路径提示已通过脚本输出 + PR 模板引导人工，无需机器阻断；非阻断保证零合并阻力 | 不把输出接成 required / blocking check（会拖慢合并、且浅克隆下可能假红） |
| 基准取法 | `--base origin/<base_ref>` 三点点 diff | PR 真实变更是相对基线的 Java 改动；`HEAD~1` 在 CI 合并提交下失真（`git diff HEAD~1..HEAD` 为空或仅合并层） | 不用 `HEAD~1`（CI 上下文错） |
| 浅克隆兼容 | 步骤内 `git fetch --unshallow`（兜底 `--deepen=300`） | `actions/checkout@v4` 默认 `fetch-depth:1`，`analyze-impact.py` 三点点 diff 需 head 历史算 merge-base，否则 `fatal: bad revision`（已实测）；`unshallow` 拉全历史、`deepen` 兜底 | 不改共享 checkout 的 `fetch-depth`（避免影响其它步骤、放大克隆体积） |
| PR 模板 | 新增独立「AI 辅助影响面 / 度量自检」节 | 清晰、不影响既有勾选项；与「风险与回滚」衔接，引导高风险路径 E2E 全量回归 | 不并入现有节（会稀释既有清单语义） |
| 度量数据缺口 | 字段严格标「待补」、PR 模板明示 | 11.7.2 已定：需 CI / PR 系统 / incident 系统的外部数据源，当前不可得，不编造 | 不接 DORA 外部采集（属 L3 后续立项） |

## 风险与边界

- **非阻断铁律**：`continue-on-error: true` + 脚本退出码恒 0 + `|| true` 三重保险，任何情况下不阻断 PR 合并；CI 缺历史 / 基线不可达时优雅 skip 并提示本地运行，绝不假绿。
- **共享树**：仓库多会话并行；只改这两个文件、只提交自己路径；其它并行会话改动原样保留、不代提交。
- **不越权**：不触碰 `06 §12` L4 文档、不改 `check.sh`、不新增 `check-*.py`、不动现有门禁判据。
- **CI only**：`if: github.event_name == 'pull_request'` 保证只在 PR 跑；push 到 dev 不触发（影响面分析对 push 无意义，且避免重复成本）。
- **YAML 合法性**：新增步骤必须 YAML 合法，否则整段 `ci.yml` 解析失败会拖垮整个 `backend-quality` job；提交前以 actionlint / `python3 -c "import yaml"` 校验。

## 验证策略

1. **YAML 合法**：`python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` 不抛异常；如环境有 actionlint 则跑一遍。
2. **本地 dry-run**：`python3 scripts/analyze-impact.py --base HEAD~3 --head HEAD` 与 `python3 scripts/analyze-metrics.py` 均 `exit 0` 且产出报告（已在前序验证）。
3. **pre-commit 仍全绿**：`ci.yml` / PR 模板改动不触发 `check.sh` pre-commit 的 mvn / 门禁（它们只认 `.java` / `.sql` 等）；但提交时仍跑完整 `check.sh` 确认无连带回归。
4. **comet guard build --apply 后**：在 GitHub Actions 实际跑通（非阻断步骤出现在 PR Checks，浅克隆兜底生效）；手动开一个测试 PR 验证步骤出现且不影响合并。
