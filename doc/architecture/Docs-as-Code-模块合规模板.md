# Docs-as-Code 模块合规模板

> **适用**：`doc/design/modules/*.md` 与对应 Maven 模块。  
> **首发参考**：扩展模块（详设 v2.5 + `tools/extension-compliance-collector`）。  
> **样板参考**：`bone-blueprint` + `tools/blueprint-compliance-collector`（无独立详设，仅 README + `_generated`）。

---

## 三定律（强制）

| 定律 | 做法 | 反模式 |
|------|------|--------|
| **L1 SSOT** | As-Is 只在 §2/§7/API/OpenAPI 定义一次；Backlog 只在 `backlog.yaml` | 附录 A 同时列 ✅ 与 [Target] |
| **L2 强链接** | 总览 → 详设 §x → 源码相对路径；ADR ↔ 详设双向链 | 「类加载隔离」无 As-Is/Target 分界 |
| **L3 CI 派生** | `collect.py` 扫描 → `doc/_generated/{module}/` + `--check` | 人工改 `compliance.json` |

---

## 目录约定

```text
tools/{module}-compliance-collector/
  collect.py          # 扫描 + 生成
  backlog.yaml        # 仅 [Target]/[Vision]
  README.md
doc/_generated/{module}/
  compliance.json
  as-is-evidence.md
  backlog.md
scripts/ci/collect-{module}-compliance.sh
```

有详设时，在 `doc/design/modules/N. xxx.md` 增加：

```markdown
<!-- EXT_COMPLIANCE_BACKLOG:START -->
<!-- EXT_COMPLIANCE_BACKLOG:END -->

<!-- EXT_COMPLIANCE_ASIS:START -->
<!-- EXT_COMPLIANCE_ASIS:END -->
```

（标记名可改为 `MOD_COMPLIANCE_*`，扩展模块沿用历史名 `EXT_*`。）

---

## 新增模块检查清单

1. 复制 `tools/extension-compliance-collector/` 为模板，改 `MODULE`、`ROOT` 路径与扫描规则。
2. 在 `backlog.yaml` **只写未落地项**；已落地项写成 `collect.py` 里的 `as_is` 扫描。
3. 注册 `scripts/ci/collect-*-compliance.sh` 与 `.github/workflows/*` job（`collect` + `--check`；有详设则加 `diff` GENERATED 块）。
4. 更新 `doc/_generated/README.md`、`AGENTS.md` §11、文档治理子任务表。
5. PR 说明附：`bash scripts/ci/collect-{module}-compliance.sh --sync-doc` 已执行。

---

## 本地一键（已注册模块）

```bash
bash scripts/ci/collect-all-compliance.sh --sync-doc   # 生成 + 同步全部 6 个详设的 GENERATED 块
bash scripts/ci/collect-all-compliance.sh --check      # 仅校验，CI 同款
```

## CI 门禁（推荐）

```yaml
- run: python3 tools/{module}-compliance-collector/collect.py
- run: python3 tools/{module}-compliance-collector/collect.py --check
# 有详设时：
- run: |
    cp doc/design/modules/....md /tmp/before.md
    python3 tools/.../collect.py --sync-doc
    diff -q /tmp/before.md doc/design/modules/....md
```

**禁止**在 CI 的 `--sync-doc` 中写入时间戳到 GENERATED 内嵌块（避免无意义 diff）；时间戳只进 `compliance.json`。

---

## 已注册模块

| 模块 | 收集器 | 详设 GENERATED 块 | CI Workflow |
|------|--------|-------------------|-------------|
| extension | `tools/extension-compliance-collector` | [5. 扩展管理模块详细设计方案](../design/modules/5.%20扩展管理模块详细设计方案.md)（`EXT_COMPLIANCE_*`） | `extension-studio.yml` · `docs-compliance.yml` |
| iam | `tools/iam-compliance-collector` | [6. IAM 账号权限管理模块详细设计方案](../design/modules/6.%20IAM账号权限管理模块详细设计方案.md)（`IAM_COMPLIANCE_*_BODY`） | `docs-compliance.yml` |
| metadata | `tools/metadata-compliance-collector` | [2. 元数据管理模块详细设计方案](../design/modules/2.%20元数据管理模块详细设计方案.md)（`META_COMPLIANCE_*`） | `docs-compliance.yml` |
| integration | `tools/integration-compliance-collector` | [4. 集成管理模块详细设计方案](../design/modules/4.%20集成管理模块详细设计方案.md)（`INT_COMPLIANCE_*`） | `docs-compliance.yml` |
| masterdata | `tools/masterdata-compliance-collector` | [3. 主数据管理模块详细设计方案](../design/modules/3.%20主数据管理模块详细设计方案.md)（`MDM_COMPLIANCE_*`） | `docs-compliance.yml` |
| generator | `tools/generator-compliance-collector` | [8. Studio Generator 详细设计方案](../design/modules/8.Studio%20Generator%20详细设计方案.md)（`GEN_COMPLIANCE_*`） | `docs-compliance.yml` |
| console | `tools/console-compliance-collector` | —（仅 `_generated/console/`） | `docs-compliance.yml` |
| blueprint | `tools/blueprint-compliance-collector` | —（仅 `_generated/blueprint/` + [bone-blueprint/README](../../bone-blueprint/README.md)） | `blueprint.yml` · `docs-compliance.yml` |

聚合校验：[`docs-compliance.yml`](../../.github/workflows/docs-compliance.yml) + `scripts/ci/collect-all-compliance.sh`。

---

## 相关文档

- [Bone-DDD 最终实践方案](./Bone-DDD-最终实践方案.md) §21 ArchUnit
- [Bone-API-规范](./Bone-API-规范.md) §15 OpenAPI
- [文档治理-三目录审查子任务](../文档治理-三目录审查子任务.md)
