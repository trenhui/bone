#!/bin/bash
# 更新 checkpoint - 记录修改时间

FILE="$1"

# 如果修改的是 checkpoint 本身，不处理
if [[ "$FILE" == *"checkpoint.json" ]]; then
    exit 0
fi

# 查找对应的 feature 目录
FEATURE_DIR=$(dirname "$(dirname "$FILE")")
CHECKPOINT_FILE="$FEATURE_DIR/checkpoint.json"

if [ -f "$CHECKPOINT_FILE" ]; then
    # 更新 last_modified 时间戳
    NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    TMP=$(mktemp)
    jq --arg now "$NOW" '.last_modified = $now' "$CHECKPOINT_FILE" > "$TMP" && mv "$TMP" "$CHECKPOINT_FILE"
    echo "✅ Checkpoint 更新时间戳: $NOW"
fi

exit 0
