#!/bin/bash

# 修复实体类继承问题
find /Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas/bone-tpa-saas -name "*.java" -type f | xargs grep -l "extends AbstractEntity<" | while read file; do
    # 替换 AbstractEntity<EntityType, ID> 为 AbstractEntity<ID>
    sed -i '' 's/extends AbstractEntity<[A-Za-z]*\.class, \([A-Za-z0-9]*\)>/extends AbstractEntity<\1>/g' "$file"
    # 替换 AbstractEntity<EntityType, \([A-Za-z0-9]*\)> 为 AbstractEntity<\1>
    sed -i '' 's/extends AbstractEntity<EntityType, \([A-Za-z0-9]*\)>/extends AbstractEntity<\1>/g' "$file"
done

echo "已修复实体类继承问题"
