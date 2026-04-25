---
description: "Guardian 审查，创建 PR"
arguments:
  - name: dry-run
    description: "仅生成报告不创建 PR"
    type: boolean
    default: false
  - name: skip-guardian
    description: "跳过 Guardian（紧急修复）"
    type: boolean
    default: false
---

## 执行流程

1. **Guardian 审查**（如未跳过）
   - 安全扫描（OWASP Top 10）
   - 契约最终一致性校验
   - 代码质量检查

2. **生成双轨度量**
   - Commit Message（公开）：功能描述、主要变更
   - Checkpoint（本地）：耗时、自愈次数、覆盖率

3. **创建 PR**
   ```bash
   git add .
   git commit -m "[Agentic] {type}: {description}"
   git push origin feature/{feature}@{owner}
   gh pr create \
     --title "[Agentic] {feature}" \
     --body "{pr_body}" \
     --label "agentic-generated" \
     --label "needs-human-review" \
     --draft
   ```

## PR 模板

```markdown
## 🤖 AI 生成的 PR

### 功能描述
{mission}

### 契约摘要
- Level: {level}
- API Endpoints: {count}

### 质量报告
| 指标 | 结果 | 门槛 |
|------|------|------|
| 单元测试 | ✅ {passed}/{total} | 100% |
| 集成测试 | ✅ {passed}/{total} | 100% |
| 覆盖率 | {coverage}% | ≥80% |
| 安全检查 | ✅ 通过 | — |

### Agentic 度量
- Ralph Loop 迭代次数: {iterations}
- L1 自愈: {l1_count} 次
- 总耗时: {total_time} 分钟

### 审查清单
- [ ] 契约是否符合预期
- [ ] 代码逻辑是否正确
- [ ] 测试覆盖是否充分
```

## 输出报告

```markdown
## /ship 执行报告

### Guardian 审查
- Critical: 0
- High: 1（需确认）
- Medium: 2
- Low: 3

### PR 信息
- 分支: feature/{feature}@{owner}
- PR: #142
- URL: https://gitee.com/bone/bone/pulls/142

### 下一步
等待人工 Review 后合并
```
