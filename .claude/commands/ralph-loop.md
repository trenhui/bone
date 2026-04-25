---
description: "自动迭代执行直到完成任务"
arguments:
  - name: task
    description: "任务描述"
    required: true
  - name: completion-promise
    description: "完成标志字符串"
    default: "DONE"
  - name: max-iterations
    description: "最大迭代次数"
    type: number
    default: 30
  - name: checkpoint-interval
    description: "检查点保存间隔"
    type: number
    default: 5
  - name: auto-commit
    description: "自动提交"
    type: boolean
    default: true
  - name: notify
    description: "通知地址"
    type: string
    default: ""
---

## 执行流程

1. **初始化**
   - 读取现有契约（如果不存在则自动执行 `/plan`）
   - 创建 checkpoint 状态文件
   - 初始化迭代计数器

2. **迭代循环**
   ```
   for iteration in 1..max-iterations:
     - 检测上一轮的问题
     - 执行 L1/L2-A 自动修复
     - 重新执行 `/build` 构建剩余切片
     - 执行 `/test` 验证
     - 检查是否满足完成条件
     - 如果完成，退出循环
     - 每隔 N 次迭代保存 checkpoint 并自动提交
   ```

3. **完成条件**
   - 所有测试通过
   - 覆盖率达标
   - 契约一致性验证通过
   - Guardian 审查无 Critical 问题

4. **交付**
   - 满足完成条件后自动执行 `/ship`
   - 输出完成标志 `AGENTIC_DONE`
   - 发送通知（如果配置）

## 示例

```bash
# 标准用法
/ralph-loop "为 bone-metadata 模块增加元数据实体的完整 CRUD 管理功能"

# 自定义完成标志和最大迭代
/ralph-loop "实现版本回滚功能" --completion-promise "VERSIONING_DONE" --max-iterations=50

# 关闭自动提交
/ralph-loop "重构认证模块" --auto-commit=false
```
