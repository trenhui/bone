# metadata-compliance-collector

元数据模块 Docs-as-Code 合规收集器（对标 `extension-compliance-collector`）。

## 用法

```bash
python3 tools/metadata-compliance-collector/collect.py
python3 tools/metadata-compliance-collector/collect.py --sync-doc
python3 tools/metadata-compliance-collector/collect.py --check
```

产出：`doc/_generated/metadata/{compliance.json,as-is-evidence.md,backlog.md}`

详设同步块：`doc/design/modules/2. 元数据管理模块详细设计方案.md` 附录 A/B（`META_COMPLIANCE_*` 标记）。
