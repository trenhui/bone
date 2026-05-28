# Blueprint Compliance Collector

`bone-blueprint` 的 Docs-as-Code 收集器（**无独立详设**，真源为模块 README + 本目录 `backlog.yaml`）。

```bash
python3 tools/blueprint-compliance-collector/collect.py
python3 tools/blueprint-compliance-collector/collect.py --check
bash scripts/ci/collect-blueprint-compliance.sh
```

产出：`doc/_generated/blueprint/`。

平台模板：[Docs-as-Code-模块合规模板](../../doc/architecture/Docs-as-Code-模块合规模板.md)。
