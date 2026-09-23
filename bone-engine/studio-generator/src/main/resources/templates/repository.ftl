package ${utils.getPackagePath(basePackage, moduleName)}.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};

/**
 * ${table.tableComment!'实体'}仓储。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。本聚合分页留在域仓储，不另建 QueryPort。
 */
public interface ${table.customEntityName}Repository extends Repository<${table.customEntityName}, Long> {

  default PageResult<${table.customEntityName}> findPage(int pageNum, int pageSize) {
    return pageByCriteria(
        Criteria.<${table.customEntityName}>create()
            .orderByDesc(${table.customEntityName}::getId)
            .page(pageNum, pageSize));
  }
}
