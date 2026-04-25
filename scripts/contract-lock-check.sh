#!/bin/bash
# 契约锁检查 - 检查锁定的契约是否被修改

FILE="$1"

# 只检查契约文件
if [[ "$FILE" != *".claude/contracts/"* ]]; then
    exit 0
fi

# 获取当前 Git 用户
GIT_USER=$(git config user.name 2>/dev/null || git config user.email 2>/dev/null)
if [ -z "$GIT_USER" ]; then
    exit 0
fi

# 检查契约是否存在并被锁定
if [ -f "$FILE" ]; then
    LOCK=$(grep -E "lock:\s*(true|yes)" "$FILE" | head -1)
    if [ -n "$LOCK" ]; then
        OWNER=$(grep -E "owner:\s*" "$FILE" | head -1 | sed -E 's/owner:\s*//' | tr -d '"')
        if [ -n "$OWNER" ] && [[ "$GIT_USER" != *"$OWNER"* ]]; then
            echo "❌ 契约已被锁定，所有者: $OWNER，当前用户: $GIT_USER"
            echo "请联系 $OWNER 解锁后再修改"
            exit 1
        fi
    fi
fi

exit 0
