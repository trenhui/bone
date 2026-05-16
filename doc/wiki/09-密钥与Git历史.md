# 09 — 密钥与 Git 历史

[← Wiki 首页](./README.md)

## 当前策略

| 扫描 | 命令 / CI | 行为 |
|------|-----------|------|
| **工作区** | `gitleaks detect --no-git` | 当前文件不得含明文密钥；**无 baseline** |
| **Git 历史** | `gitleaks detect --baseline-path .gitleaks.baseline.json` | 已登记的历史泄露不阻断；**新增**泄露失败 |
| **字面量** | `scripts/scan-secrets.sh` 内 `rg` | 拦截已知高危字符串 |

本地一键：`bash scripts/scan-secrets.sh`（与 CI `secrets-scan` 对齐）。

## 维护 baseline

发现**新的**历史泄露且确认已轮换、可接受暂时登记时（不推荐滥用）：

```bash
gitleaks detect --source . --config .gitleaks.toml --report-path .gitleaks.baseline.json
# 审阅 JSON 后提交 PR，说明原因与轮换记录
```

**禁止**为规避检查而把真实密钥写入 baseline 而不轮换。

## 彻底清理历史（可选）

仓库提供一键替换脚本（**会重写所有 commit**，需 `force-push`）：

```bash
pip3 install git-filter-repo   # 首次
bash scripts/git-filter-secrets.sh
```

替换规则见 [`scripts/git-filter-secrets-replacements.txt`](../../scripts/git-filter-secrets-replacements.txt)（KT 密钥、历史 metadata secret、合作方 RequestKeyNo 等）。

清理完成后：

1. **轮换**所有受影响凭证（含 `KT_ACCESS_KEY` / `KT_ACCESS_SECRET`）  
2. 刷新 baseline：`gitleaks detect --config .gitleaks.toml --report-path .gitleaks.baseline.json`  
3. `git push --force-with-lease`（须团队确认）  
4. 通知协作者 **重新 clone**

## 相关文件

- [`.gitleaks.toml`](../../.gitleaks.toml) — 规则与路径 allowlist  
- [`.gitleaks.baseline.json`](../../.gitleaks.baseline.json) — 历史泄露指纹；经 `git-filter-secrets.sh` 清理后可为空数组 `[]`  
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md) — 提交前检查
