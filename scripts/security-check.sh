#!/bin/bash
# 安全检查脚本 - 阻止危险命令

INPUT="$1"

# 阻止 rm -rf / 或 rm -rf *
if echo "$INPUT" | grep -qE "rm -rf /|rm -rf \*"; then
    echo "❌ 阻止：删除命令被安全拦截"
    exit 1
fi

# 阻止强制推送
if echo "$INPUT" | grep -q "git push --force"; then
    echo "❌ 阻止：强制推送被安全拦截"
    exit 1
fi

# 阻止直接推送 main/master
if echo "$INPUT" | grep -qE "git push origin (main|master)"; then
    echo "❌ 阻止：直接推送 main/master 被拦截，请通过 PR 合并"
    exit 1
fi

# 阻止 sudo 命令
if echo "$INPUT" | grep -q "sudo "; then
    echo "❌ 阻止：sudo 命令被拦截"
    exit 1
fi

echo "✅ 安全检查通过"
exit 0
