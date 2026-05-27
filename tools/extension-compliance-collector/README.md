# Extension Compliance Collector

扩展管理模块 **Docs-as-Code** 收集器（L1 SSOT + L3 CI 派生）。

## 职责

| 输出 | 说明 |
|------|------|
| `doc/_generated/extension/compliance.json` | 机器可读：As-Is 证据 + Backlog |
| `doc/_generated/extension/as-is-evidence.md` | 人类可读 As-Is 明细 |
| `doc/_generated/extension/backlog.md` | 人类可读 [Target]/[Vision] 清单 |
| 详设 `5. 扩展管理模块详细设计方案.md` | `--sync-doc` 时更新 `EXT_COMPLIANCE_*` 标记块 |

## 维护规则

1. **已落地能力**：不要写入 `backlog.yaml`；由 `collect.py` 扫描 OpenAPI / 源码派生。
2. **未落地能力**：只维护 `backlog.yaml`。
3. **详设附录 A**：仅 Backlog（生成块）；**附录 C**：仅 As-Is 证据（生成块）。
4. PR 修改 OpenAPI 或 Studio 删除/幂等/ArchUnit 相关代码后，须运行收集器并提交 `_generated`。

## 命令

```bash
# 生成产物
python3 tools/extension-compliance-collector/collect.py

# 同步详设内嵌表
python3 tools/extension-compliance-collector/collect.py --sync-doc

# CI：校验 compliance.json 是否与当前仓库一致
python3 tools/extension-compliance-collector/collect.py --check

# 或通过脚本（含 --sync-doc）
bash scripts/ci/collect-extension-compliance.sh --sync-doc
```

## CI

`.github/workflows/extension-studio.yml` 的 `docs-compliance` job 会执行 `--check`。
