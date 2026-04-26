#!/bin/bash

# 修复仓库接口的导入路径
find /Users/renhui.trh/创业项目/智能理赔/deep-claim/bone-business/tpa-saas/bone-tpa-saas -name "*Repository.java" -type f | while read file; do
    # 替换旧的Criteria导入路径
    sed -i '' 's/import com.bone.lowcode.metadata.sdk.query.criteria.Criteria;/import com.bone.metadata.sdk.query.criteria.Criteria;/g' "$file"
    
    # 替换旧的util导入路径
    sed -i '' 's/import com.bone.core.util.DateParserUtil;/import java.text.SimpleDateFormat;/g' "$file"
    sed -i '' 's/import com.bone.core.util.PkListUtil;/import java.util.List;/g' "$file"
    
    # 替换javax.annotation.Resource为jakarta.annotation.Resource
    sed -i '' 's/import javax.annotation.Resource;/import jakarta.annotation.Resource;/g' "$file"
done

echo "已修复仓库接口的导入路径"
