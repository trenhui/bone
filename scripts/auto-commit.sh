#!/bin/bash
# 自动提交 - Ralph Loop 完成后自动提交

# 检查是否有变更
if git diff --quiet; then
    echo "✅ 没有变更需要提交"
    exit 0
fi

# 获取当前分支
BRANCH=$(git symbolic-ref --short HEAD)

# 如果不是 feature 分支，不自动提交
if [[ "$BRANCH" != feature/* ]]; then
    echo "⚠️ 当前分支不是 feature 分支，跳过自动提交"
    exit 0
fi

# 获取 user
USER=$(git config user.name || echo "agent")

# 生成 commit message（不加 [skip ci]：自动提交同样必须过 CI 门禁）
COMMIT_MSG="feat(agentic): auto-commit by ralph-loop"

# 只暂存已跟踪文件的修改（git add . 会把误落在工作区的未跟踪垃圾一起提交）
git add -u

# 提交
git commit -m "$COMMIT_MSG"

echo "✅ 自动提交完成"
exit 0
