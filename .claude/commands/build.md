---
description: "基于契约生成代码，执行切片构建和自愈"
arguments:
  - name: phase
    description: "指定切片阶段 (api|service|mapper|test)"
    required: false
  - name: no-auto-fix
    description: "跳过自动修复"
    type: boolean
    default: false
  - name: dry-run
    description: "预览变更不写入"
    type: boolean
    default: false
---

## 执行流程

1. **读取契约和检查点**
   ```bash
   contract = read(".claude/contracts/{feature}.yaml")
   checkpoint = read(".claude/state/{feature}/checkpoint.json")
   ```

2. **确定当前切片**
   ```
   if phase specified:
       current = phase
   else:
       current = checkpoint.next_phase
   ```

3. **执行切片**
   ```
   for phase in [current..last]:
       # 生成代码
       generate_code(phase)

       # 编译检查
       result = run_check(phase)

       # 自愈循环
       for attempt in 1..3:
           if result.success:
               break
           elif result.level in [L1, L2-A] and not no_auto_fix:
               auto_fix(result.errors)
               result = run_check(phase)
           else:
               break

       if not result.success and result.level in [L2-B, L3, L4]:
           pause_for_confirmation(result)

       checkpoint.next_phase = next_phase
       save_checkpoint()
   ```

4. **输出报告**
   ```markdown
   ## /build 执行报告

   ### 执行摘要
   - Feature: {feature}
   - Status: ✅ 成功 / ⚠️ 部分成功 / ❌ 失败
   - Total Time: {time} 秒

   ### 切片详情
   | Phase | Status | 自愈次数 | 耗时 |
   |-------|--------|----------|------|
   | api   | ✅     | 2        | 45s  |
   | service| ✅    | 0        | 30s  |
   | mapper| ✅     | 1        | 25s  |
   | test  | ⚠️     | 待处理   | -    |

   ### 自愈记录
   - L1: 3 次（缺少 import x2, 类型不匹配 x1）
   - L2-A: 1 次（Mock 配置）

   ## 下一步
   执行 `/test` 进行集成测试
   ```

## 示例

```bash
# 自动切片
/build

# 指定切片
/build --phase=api

# 跳过自愈
/build --no-auto-fix
```
