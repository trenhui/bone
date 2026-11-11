# Tasks

- [x] 1. `.github/workflows/ci.yml` 的 `backend-quality` job 新增非阻断步骤「AI Impact & Metrics Self-Check」（`if: pull_request`、`continue-on-error: true`、步骤内 `git fetch --unshallow` 兜底、跑 `analyze-impact.py --base origin/<base_ref> --head HEAD` + `analyze-metrics.py`）。 <!-- comet-task:60107fb3-0808-401d-8a9f-fde302f080d6 -->
- [x] 2. `.github/PULL_REQUEST_TEMPLATE.md` 新增「AI 辅助影响面 / 度量自检（report-only，非阻断）」一节，含高风险路径 E2E 全量回归提示与「待补字段未编造」说明。 <!-- comet-task:32551d3c-9f05-4955-b3c7-c31b22c29149 -->
- [x] 3. 验证：YAML 合法（`yaml.safe_load` 不抛异常）、本地 dry-run 两脚本 exit 0、pre-commit 全绿、`comet guard ... build --apply` 后 GitHub Actions 非阻断步骤出现于 PR Checks。 <!-- comet-task:6c23d478-3300-4e93-b0ff-3d6031ae0719 -->
