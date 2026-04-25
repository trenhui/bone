---
description: "生成分层契约，初始化 Checkpoint"
arguments:
  - name: feature
    description: "功能描述"
    required: true
  - name: level
    description: "契约级别 (auto|L1|L2)"
    default: "auto"
  - name: dry-run
    description: "预览契约不写入"
    type: boolean
    default: false
---

## 执行流程

1. **代码库扫描**
   - 调用 `code-localization` 技能
   - 分析现有代码结构
   - 识别相关模块和依赖

2. **复杂度判断**（当 level=auto 时）
   ```
   if (isCRUD or isSimpleQuery):
       level = L1 (30秒)
   elif (hasVersioning or hasWorkflow or hasComplexRules):
       level = L2 (5分钟)
   else:
       level = L1 (默认)
   ```

3. **生成契约**
   - L1：生成 api_contract + guardrails
   - L2：生成 api_contract + domain_rules + guardrails

4. **初始化 Checkpoint**
   ```json
   {
     "feature": "{feature-name}",
     "owner": "{current-user}",
     "level": "L1|L2",
     "status": "planned",
     "next_phase": "api",
     "created_at": "{timestamp}",
     "metrics": {
       "plan_time_sec": 0,
       "build_time_sec": 0,
       "test_time_sec": 0
     }
   }
   ```

5. **输出确认**
   ```markdown
   ## /plan 执行报告

   ✅ 契约已生成: .claude/contracts/{feature}.yaml
   ✅ Checkpoint 已初始化: .claude/state/{feature}/checkpoint.json

   📋 契约摘要:
   - Level: L1/L2
   - API Endpoints: {count}
   - Domain Rules: {count}

   ⏱️ 预估时间: {estimated} 分钟

   ## 下一步
   确认契约后执行 `/build`
   ```

## 示例

```bash
# 标准用法
/plan 为 bone-metadata 模块增加元数据实体的 CRUD 管理功能

# 强制 L2
/plan 增加版本管理功能 --level=L2

# 预览模式
/plan 功能描述 --dry-run
```
