#!/bin/bash

# 脚本功能：修复 tpa-saas 项目中的所有实体类，包括继承 AbstractEntity 和 TenantAbstractEntity 的类

# 定义目标目录
TARGET_DIR="/Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas"

# 查找所有 Java 文件
find "$TARGET_DIR" -name "*.java" | while read -r file; do
    echo "Processing file: $file"
    
    # 读取文件内容
    content=$(cat "$file")
    
    # 替换 AbstractEntity<EntityType, ID> 为 AbstractEntity<ID>
    modified_content=$(echo "$content" | sed 's/extends\s+AbstractEntity<[A-Za-z0-9_]*,\s*\([A-Za-z0-9_]*\)>\s*/extends AbstractEntity<\1> /')
    
    # 替换 TenantAbstractEntity<EntityType, ID> 为 TenantAbstractEntity<ID>
    modified_content=$(echo "$modified_content" | sed 's/extends\s+TenantAbstractEntity<[A-Za-z0-9_]*,\s*\([A-Za-z0-9_]*\)>\s*/extends TenantAbstractEntity<\1> /')
    
    # 检查 Table 注解的导入
    if echo "$modified_content" | grep -q "import com.bone.core.annotation.Table;"; then
        # 替换为新的 Table 注解导入
        modified_content=$(echo "$modified_content" | sed 's/import com.bone.core.annotation.Table;/import com.bone.metadata.sdk.domain.annotation.Table;/')
    fi
    
    # 检查其他注解的导入
    if echo "$modified_content" | grep -q "import com.bone.core.annotation."; then
        # 替换为新的注解导入
        modified_content=$(echo "$modified_content" | sed 's/import com.bone.core.annotation./import com.bone.metadata.sdk.domain.annotation./g')
    fi
    
    # 写入修改后的内容
    echo "$modified_content" > "$file"
    echo "Updated $file"
done

echo "\nProcessing complete!"
