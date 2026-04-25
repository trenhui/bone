#!/bin/bash
# 设置 Bone Agentic Engineering hooks
# 这个脚本用于初始化 hooks 配置

echo "🚀 设置 Bone Agentic Engineering hooks"

# 给脚本添加执行权限
chmod +x scripts/*.sh

echo "✅ 权限设置完成"
echo ""
echo "hooks 已配置在 .claude/hooks/hooks.json"
echo "支持的 hooks:"
echo "  - PreToolUse: 安全检查 + 契约锁检查"
echo "  - PostToolUse: 自动格式化 + checkpoint 更新"
echo "  - Stop: 自动提交"
echo ""
echo "需要 Claude Code CLI >= 1.10.0 支持"
