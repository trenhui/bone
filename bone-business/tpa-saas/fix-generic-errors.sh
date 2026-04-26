#!/bin/bash

# 脚本功能：修复 tpa-saas 项目中的类型变量数目错误，将 <EntityType, ID> 改为 <ID>

# 定义目标目录
TARGET_DIR="/Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas"

# 查找所有 Java 文件
find "$TARGET_DIR" -name "*.java" | while read -r file; do
    echo "Processing file: $file"
    
    # 读取文件内容
    content=$(cat "$file")
    
    # 替换继承关系中的泛型参数
    # 匹配模式：extends AbstractEntity<[A-Za-z0-9_]+,\s*([A-Za-z0-9_]+)>
    modified_content=$(echo "$content" | sed 's/extends\s+AbstractEntity<[A-Za-z0-9_]*,\s*\([A-Za-z0-9_]*\)>\s*/extends AbstractEntity<\1> /')
    
    # 替换继承关系中的泛型参数（TenantAbstractEntity）
    # 匹配模式：extends TenantAbstractEntity<[A-Za-z0-9_]+,\s*([A-Za-z0-9_]+)>
    modified_content=$(echo "$modified_content" | sed 's/extends\s+TenantAbstractEntity<[A-Za-z0-9_]*,\s*\([A-Za-z0-9_]*\)>\s*/extends TenantAbstractEntity<\1> /')
    
    # 替换接口实现中的泛型参数
    # 匹配模式：implements Repository<[A-Za-z0-9_]+,\s*([A-Za-z0-9_]+)>
    modified_content=$(echo "$modified_content" | sed 's/implements\s+Repository<[A-Za-z0-9_]*,\s*\([A-Za-z0-9_]*\)>\s*/implements Repository<\1> /')
    
    # 写入修改后的内容
    echo "$modified_content" > "$file"
    echo "Updated $file"
done

echo "\nProcessing complete!"
