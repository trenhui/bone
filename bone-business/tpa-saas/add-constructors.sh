#!/bin/bash

# 脚本功能：为 tpa-saas 项目中的 Repository 实现类添加正确的构造函数

# 定义目标目录
TARGET_DIR="/Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas"

# 查找所有继承 BaseRepository 的 Java 文件
find "$TARGET_DIR" -name "*.java" -exec grep -l "extends BaseRepository" {} \; | while read -r file; do
    echo "Processing file: $file"
    
    # 读取文件内容
    content=$(cat "$file")
    
    # 检查是否已经有构造函数
    if ! echo "$content" | grep -q "public.*(SqlBuilder" ; then
        # 提取类名
        class_name=$(echo "$content" | grep -E "public\s+class\s+[A-Za-z0-9_]+\s+extends\s+BaseRepository" | sed 's/public\s+class\s+\([A-Za-z0-9_]*\)\s+extends\s+BaseRepository.*/\1/')
        
        if [ -n "$class_name" ]; then
            # 提取实体类型
            entity_type=$(echo "$content" | grep -E "extends\s+BaseRepository<" | sed 's/.*BaseRepository<\([A-Za-z0-9_]*\),.*/\1/')
            
            if [ -n "$entity_type" ]; then
                echo "Adding constructor to $class_name with entity type $entity_type"
                
                # 构造函数
                constructor="    @Autowired\n    public $class_name(SqlBuilder sqlBuilder,\n                          SqlExecutor sqlExecutor,\n                          ExtensionCoordinator extensionCoordinator) {\n        super(sqlBuilder, sqlExecutor, $entity_type.class, extensionCoordinator);\n    }"
                
                # 使用 awk 来添加构造函数
                modified_content=$(echo "$content" | awk -v class="$class_name" -v constructor="$constructor" '{
                    if (/public\s+class\s+" class "\s+extends\s+BaseRepository</) {
                        print;
                        in_class=1;
                    } else if (in_class && /^\s*{/) {
                        print;
                        print constructor;
                        in_class=0;
                    } else {
                        print;
                    }
                }')
                
                # 写入修改后的内容
                echo "$modified_content" > "$file"
                echo "Added constructor to $file"
            fi
        fi
    fi
done

echo "\nProcessing complete!"
