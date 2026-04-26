#!/bin/bash

# 修复仓库实现类
find /Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas/bone-tpa-saas -name "*RepositoryImpl.java" -type f | while read file; do
    # 替换 BaseRepositoryImpl 为 BaseRepository
    sed -i '' 's/BaseRepositoryImpl/BaseRepository/g' "$file"
    
    # 检查是否已包含必要的导入
    if ! grep -q "import com.bone.metadata.sdk.BaseRepository;" "$file"; then
        sed -i '' '/import /a\
import com.bone.metadata.sdk.BaseRepository;' "$file"
    fi
    
    if ! grep -q "import com.bone.metadata.sdk.query.SqlBuilder;" "$file"; then
        sed -i '' '/import /a\
import com.bone.metadata.sdk.query.SqlBuilder;' "$file"
    fi
    
    if ! grep -q "import com.bone.metadata.sdk.sql.executor.SqlExecutor;" "$file"; then
        sed -i '' '/import /a\
import com.bone.metadata.sdk.sql.executor.SqlExecutor;' "$file"
    fi
    
    if ! grep -q "import com.bone.metadata.sdk.extension.ExtensionCoordinator;" "$file"; then
        sed -i '' '/import /a\
import com.bone.metadata.sdk.extension.ExtensionCoordinator;' "$file"
    fi
    
    # 添加构造函数
    if ! grep -q "public.*RepositoryImpl(SqlBuilder sqlBuilder," "$file"; then
        # 提取类名和泛型参数
        class_name=$(grep -o "public class \([A-Za-z0-9]*\)RepositoryImpl" "$file" | cut -d ' ' -f 3)
        entity_class=$(grep -o "extends BaseRepository<\([A-Za-z0-9]*\)," "$file" | cut -d '<' -f 2 | cut -d ',' -f 1)
        
        if [ -n "$class_name" ] && [ -n "$entity_class" ]; then
            # 在类定义后添加构造函数
            sed -i '' "/public class $class_nameRepositoryImpl/a\
    @Autowired\n    public $class_nameRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {\n        super(sqlBuilder, sqlExecutor, $entity_class.class, extensionCoordinator);\n    }" "$file"
        fi
    fi
done

echo "已修复仓库实现类"
