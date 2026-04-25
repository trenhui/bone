#!/bin/bash
# 契约验证脚本 - 验证当前分支的契约是否合法

FEATURE=$1

if [ -z "$FEATURE" ]; then
    echo "Usage: $0 <feature-name>"
    exit 1
fi

CONTRACT=".claude/contracts/${FEATURE}.yaml"

if [ ! -f "$CONTRACT" ]; then
    echo "❌ 契约文件不存在: $CONTRACT"
    exit 1
fi

# 验证 YAML 语法
if command -v yq &> /dev/null; then
    if ! yq e '.' "$CONTRACT" &>/dev/null; then
        echo "❌ YAML 语法错误"
        exit 1
    fi
fi

# 检查必填字段
META=$(yq e '.meta' "$CONTRACT")
if [ "$META" = "null" ]; then
    echo "❌ 缺少 meta 字段"
    exit 1
fi

MISSION=$(yq e '.mission' "$CONTRACT")
if [ "$MISSION" = "null" ] || [ -z "$MISSION" ]; then
    echo "❌ 缺少 mission 字段"
    exit 1
fi

API_CONTRACT=$(yq e '.api_contract' "$CONTRACT")
if [ "$API_CONTRACT" = "null" ]; then
    echo "❌ 缺少 api_contract 字段"
    exit 1
fi

echo "✅ 契约验证通过"
echo "   Feature: $(yq e '.meta.feature' "$CONTRACT")"
echo "   Owner: $(yq e '.meta.owner' "$CONTRACT")"
echo "   Level: $(yq e '.meta.level' "$CONTRACT")"
exit 0
