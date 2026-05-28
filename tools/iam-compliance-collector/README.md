# IAM Compliance Collector

`bone-platform/bone-iam` 模块 **Docs-as-Code** 收集器（参考 [Docs-as-Code-模块合规模板](../../doc/architecture/Docs-as-Code-模块合规模板.md)）。

## 职责

| 输出 | 说明 |
|------|------|
| `doc/_generated/iam/compliance.json` | 机器可读：As-Is 证据 + Backlog |
| `doc/_generated/iam/as-is-evidence.md` | 人类可读 As-Is 明细（OpenAPI/Controller/@PreAuthorize/TenantContext/审计设置 等） |
| `doc/_generated/iam/backlog.md` | 人类可读 [Target]/[Vision] 清单 |
| 详设 `6. IAM账号权限管理模块详细设计方案.md` | `--sync-doc` 时更新 `IAM_COMPLIANCE_*_BODY` 标记块 |

## 维护规则

1. **已落地能力**：不要写入 `backlog.yaml`；由 `collect.py` 扫描 OpenAPI / 源码派生（避免 SSOT 漂移）。
2. **未落地能力**：只维护 `backlog.yaml`（与详设 §1.5 已知缺口对齐）。
3. **详设附录 A**：仅 Backlog（生成块）；**附录 C**：仅 As-Is 证据（生成块）。
4. PR 修改 `bone-init.sql` IAM 表 / OpenAPI / Controller @PreAuthorize / TenantContext 逻辑后，须本地运行 `--sync-doc` 并提交 `_generated`。

## 命令

```bash
# 生成产物（_generated/iam/*）
python3 tools/iam-compliance-collector/collect.py

# 同步详设内嵌表
python3 tools/iam-compliance-collector/collect.py --sync-doc

# CI：校验 compliance.json 是否与当前仓库一致
python3 tools/iam-compliance-collector/collect.py --check

# 或通过聚合脚本
bash scripts/ci/collect-all-compliance.sh --sync-doc
```

## CI

`.github/workflows/docs-compliance.yml` 中已加入 IAM 收集器（与扩展 / blueprint 一致）：

1. 运行 `collect.py` 重新生成 `compliance.json`；
2. `--check` 比对仓库提交版本，禁止手改 `_generated`；
3. `--sync-doc` 后对详设 GENERATED 块做 `diff` 比对，PR 前须本地同步。
