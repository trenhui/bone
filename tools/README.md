# Bone 仓库工具（`tools/`）

| 目录 | 模块 | 说明 |
|------|------|------|
| [extension-compliance-collector](./extension-compliance-collector/) | 扩展管理 | OpenAPI + Studio 扫描；同步详设 `EXT_COMPLIANCE_*` 块 |
| [blueprint-compliance-collector](./blueprint-compliance-collector/) | bone-blueprint | DDD 样板能力扫描；无详设，见模块 README |
| [iam-compliance-collector](./iam-compliance-collector/) | IAM（bone-platform/bone-iam） | OpenAPI + Controller + @PreAuthorize + DDL 扫描；同步详设 `IAM_COMPLIANCE_*_BODY` 块 |
| [console-compliance-collector](./console-compliance-collector/) | 控制台（bone-platform/bone-system） | bone-system 控制台聚合 API + 权限码扫描；仅写 `_generated/console/`（无详设嵌入块） |
| [metadata-compliance-collector](./metadata-compliance-collector/) | 元数据 | server + sdk + metadata-runtime-v1 OpenAPI |
| [integration-compliance-collector](./integration-compliance-collector/) | 集成 | bone-integration + integration-v1 OpenAPI |
| [masterdata-compliance-collector](./masterdata-compliance-collector/) | 主数据 | bone-masterdata + masterdata-v1 OpenAPI |
| [generator-compliance-collector](./generator-compliance-collector/) | Studio Generator | studio-generator + generator-v1 OpenAPI |

共享库：[compliance_lib.py](./compliance_lib.py)

**平台模板**：[Docs-as-Code-模块合规模板](../doc/architecture/Docs-as-Code-模块合规模板.md)

**CI 入口**：

```bash
bash scripts/ci/collect-all-compliance.sh --sync-doc   # 本地 PR 前
bash scripts/ci/collect-all-compliance.sh --check       # 与 docs-compliance.yml 一致
```

单模块：`scripts/ci/collect-extension-compliance.sh` · `collect-blueprint-compliance.sh` · `collect-iam-compliance.sh` · `collect-console-compliance.sh`
