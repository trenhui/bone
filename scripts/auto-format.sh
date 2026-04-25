#!/bin/bash
# 自动格式化脚本 - 根据文件类型自动格式化

FILE="$1"

if [ -z "$FILE" ]; then
    echo "❌ 缺少文件参数"
    exit 1
fi

if [ ! -f "$FILE" ]; then
    echo "⚠️ 文件不存在: $FILE"
    exit 0
fi

# Java 文件使用 spotless
if [[ "$FILE" =~ \.java$ ]]; then
    if command -v mvn &> /dev/null; then
        mvn spotless:apply -q -Dfiles="$FILE" 2>/dev/null
        echo "✅ Java 格式化完成: $FILE"
    fi
# TypeScript/TSX/JS/JSX 使用 Prettier + ESLint
elif [[ "$FILE" =~ \.(ts|tsx|js|jsx)$ ]]; then
    if command -v npx &> /dev/null; then
        npx prettier --write "$FILE" 2>/dev/null
        npx eslint --fix "$FILE" 2>/dev/null
        echo "✅ TypeScript/JavaScript 格式化完成: $FILE"
    fi
# CSS/SCSS 使用 Prettier
elif [[ "$FILE" =~ \.(css|scss|less)$ ]]; then
    if command -v npx &> /dev/null; then
        npx prettier --write "$FILE" 2>/dev/null
        echo "✅ CSS 格式化完成: $FILE"
    fi
# JSON/YAML 使用 Prettier
elif [[ "$FILE" =~ \.(json|yaml|yml)$ ]]; then
    if command -v npx &> /dev/null; then
        npx prettier --write "$FILE" 2>/dev/null
        echo "✅ JSON/YAML 格式化完成: $FILE"
    fi
fi

exit 0
