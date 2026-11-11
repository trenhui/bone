## Why

《Bone AI 原生工程基础设施改造方案》v3 第十一章 11.6 / 11.7 已把 T4 变更影响面与 T6 质量度量落地为 report-only 可运行脚本（`scripts/analyze-impact.py`、`scripts/analyze-metrics.py`，commit `f903c2048`）。但脚本目前仅能由开发者手动触发，PR review 流里没有任何质量信号暴露——作者不会主动跑、reviewer 拿不到影响面 / 度量提示。业界实践（DORA 四指标 + SPACE 五维强调把质量信号前置到 PR；GitHub PR checks 的 non-blocking 模式）要求把这类信号以**非阻断**方式接进 PR 流，既暴露信息又不拖慢合并。

## What Changes

- `.github/workflows/ci.yml` 的 `backend-quality` job 新增非阻断步骤「AI Impact & Metrics Self-Check」：仅对 `pull_request` 触发，`continue-on-error: true`，跑 `analyze-impact.py`（基准 `origin/<base_ref>`，三点点 diff 取 PR Java 变更）+ `analyze-metrics.py`；脚本本身退出码恒 0、只输出报告，失败不阻断流水线。
- `.github/PULL_REQUEST_TEMPLATE.md` 新增「AI 辅助影响面 / 度量自检（report-only，非阻断）」一节，引导作者在「风险与回滚」中针对高风险路径（claim / quota / permission / ddl / migration / event / iam / security / auth）注明 E2E 全量回归计划；并说明度量「待补」字段为外部数据源缺口、未编造。

明确不做（非目标）：不新增 `check-*.py`、不改现有门禁判据、不把输出转阻断门禁（11.7 纪律：report-only 两轮后再谈转阻断）；不接 DORA 外部数据源采集（属 L3 后续）；不触碰 `06 §12` L4 文档。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

> 本 change 为 CI / PR 集成的小幅改动，无 spec 级行为变化；已在 `.openspec.yaml` 设置 `skip_specs: true`。

## Impact

- 实现文件：`.github/workflows/ci.yml`、`.github/PULL_REQUEST_TEMPLATE.md`。
- 依赖：已落地的 `scripts/analyze-impact.py`、`scripts/analyze-metrics.py`（commit `f903c2048`）。
- 验证：YAML 语法合法（actionlint / `yaml.safe_load`）；本地 dry-run 两脚本产出报告；pre-commit（`check.sh`）仍全绿（`ci.yml` 改动不触发 pre-commit 的 mvn / 门禁，但需确认 YAML 不被其它门禁误判）；`comet guard ... build --apply` 后 GitHub Actions 非阻断步骤出现于 PR Checks。
- 审批级别：L3（CI 脚本 / PR 模板，非 §12 L4）。
