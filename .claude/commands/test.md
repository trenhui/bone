---
description: "运行集成测试，验证契约一致性"
arguments:
  - name: contract-only
    description: "仅验证契约"
    type: boolean
    default: false
  - name: skip-coverage
    description: "跳过覆盖率检查"
    type: boolean
    default: false
  - name: fix
    description: "自动修复 L2-A 问题"
    type: boolean
    default: false
---

## 执行流程

1. **集成测试执行**
   ```bash
   # 后端
   mvn verify -pl {module} -P integration-test

   # 前端
   npm run test:integration
   ```

2. **覆盖率检查**（如未跳过）
   ```bash
   # 后端 JaCoCo
   coverage = parse_jacoco_report()
   if coverage < threshold:
       if level == L2 and coverage >= 85% - 5:
           warn("覆盖率接近阈值，建议优化")
       else:
           fail("覆盖率 {coverage}% < {threshold}%")
   ```

3. **契约一致性校验**
   ```bash
   # 验证 API 路径、方法、字段、状态码
   violations = validate_contract(actual_api, contract.api_contract)
   for violation in violations:
       if violation.severity == "critical":
           block_and_report()
       elif violation.severity == "warning":
           warn()
   ```

4. **L2 智能处理**
   - L2-A 问题 → 自动修复（如果 --fix）
   - L2-B 问题 → 暂停，等待人工确认

5. **更新检查点**
   ```json
   {
     "phase": "review",
     "coverage_actual": 83.5,
     "test_time_sec": 345,
     "test_status": "passed"
   }
   ```

## 输出报告

```markdown
## /test 执行报告

### 测试摘要
- 单元测试: ✅ 245/245 通过
- 集成测试: ✅ 89/89 通过
- 契约一致性: ✅ 100%
- 覆盖率: 83.5% / 80% ✅

### L2 处理记录
- L2-A: 自动修复 2 处（断言参数顺序）
- L2-B: 需确认 1 处

### 🔴 需要确认 (L2-B)
问题：版本回滚后未生成新版本号
建议：在 rollback 方法中添加 versionNo 自动递增逻辑
[ ] 接受并修复
[ ] 拒绝
[ ] 手动处理

## 下一步
确认后执行 `/ship`
```
