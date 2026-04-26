#!/bin/bash

# 修复编译错误的脚本

# 1. 修复 Table 注解导入
echo "修复 Table 注解导入..."
find bone-tpa-saas -name "*.java" -type f -exec sed -i 's/import com.bone.core.annotation.Table;/import com.bone.metadata.sdk.domain.annotation.Table;/g' {} \;

# 2. 修复 BaseRepositoryImpl 为 BaseRepository
echo "修复 BaseRepositoryImpl 为 BaseRepository..."
find bone-tpa-saas -name "*.java" -type f -exec sed -i 's/import com.bone.metadata.sdk.BaseRepositoryImpl;/import com.bone.metadata.sdk.BaseRepository;\nimport com.bone.metadata.sdk.extension.ExtensionCoordinator;\nimport com.bone.metadata.sdk.query.SqlBuilder;\nimport com.bone.metadata.sdk.sql.executor.SqlExecutor;/g' {} \;

# 3. 修复 RepositoryImpl 类的继承和构造函数
echo "修复 RepositoryImpl 类的继承和构造函数..."
find bone-tpa-saas -name "*RepositoryImpl.java" -type f | while read file; do
  # 替换类声明
  sed -i 's/extends BaseRepositoryImpl/extends BaseRepository/g' "$file"
  
  # 添加构造函数
  if ! grep -q "public.*RepositoryImpl" "$file"; then
    # 提取泛型参数
    generic_params=$(grep -o "extends BaseRepository<.*>" "$file" | sed 's/extends BaseRepository<//;s/>//')
    entity_class=$(echo "$generic_params" | cut -d, -f1)
    
    # 插入构造函数
    sed -i "/@Repository/a \
    @Autowired\n    public $(basename "$file" .java) (SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {\n        super(sqlBuilder, sqlExecutor, $entity_class.class, extensionCoordinator);\n    }" "$file"
  fi
done

# 4. 修复泛型参数错误
echo "修复泛型参数错误..."
find bone-tpa-saas -name "*.java" -type f -exec sed -i 's/extends TenantAbstractEntity<.*,/extends TenantAbstractEntity</g' {} \;

# 5. 修复 ExtraStoreBase 继承
echo "修复 ExtraStoreBase 继承..."
find bone-tpa-saas -name "*.java" -type f -exec sed -i 's/extends ExtraStoreBase<.*,/extends ExtraStoreBase</g' {} \;

echo "修复完成！"
