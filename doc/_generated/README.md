# CI 派生文档（勿手改）

本目录由仓库内工具生成，供详设与 CI 引用。

| 子目录 | 生成器 | 说明 |
|--------|--------|------|
| `extension/` | `tools/extension-compliance-collector/collect.py` | 扩展模块 As-Is 证据 + Backlog；同步详设 `EXT_COMPLIANCE_*` 块 |
| `blueprint/` | `tools/blueprint-compliance-collector/collect.py` | bone-blueprint 样板 As-Is + Backlog |
| `iam/` | `tools/iam-compliance-collector/collect.py` | IAM As-Is 证据 + Backlog；同步详设 `IAM_COMPLIANCE_*_BODY` 块 |
| `console/` | `tools/console-compliance-collector/collect.py` | bone-system 控制台聚合 API + 权限码扫描 |
| `metadata/` | `tools/metadata-compliance-collector/collect.py` | 元数据 server/sdk |
| `integration/` | `tools/integration-compliance-collector/collect.py` | 集成服务 |
| `masterdata/` | `tools/masterdata-compliance-collector/collect.py` | 主数据 |
| `generator/` | `tools/generator-compliance-collector/collect.py` | Studio Generator |

更新命令：

```bash
# 推荐：一次跑齐已注册模块
bash scripts/ci/collect-all-compliance.sh --sync-doc
bash scripts/ci/collect-all-compliance.sh --check
```
