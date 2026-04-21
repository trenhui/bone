# /ship 命令 - 交付与交付

## 职责
执行 Guardian 审查，生成度量信息，创建 PR。

## 执行步骤
1. **调用 Guardian**：运行安全和质量审查
2. **收集度量数据**：更新 checkpoint 中的 metrics
- `total_files`：新增/修改文件数
- `l1_attempts`：L1 自愈尝试次数
- `l1_successes`：L1 自愈成功次数
- `build_time_real`：实际构建时间（分钟）
3. **生成 Commit Message**：包含类型、描述、度量信息
4. **Git 提交**：所有修改
5. **创建 PR**：调用 GitHub/Gitee API 创建 PR，设置为 Draft
6. **输出总结**：展示 PR 链接、度量数据

## 输出要求
- Commit Message 必须包含度量信息（以 `# Agentic: ...` 注释）
- PR 标题格式：`Feature: {feature-name} (Agentic Engineering)`
- PR 描述包含契约链接和度量摘要
