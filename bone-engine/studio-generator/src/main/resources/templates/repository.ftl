package ${utils.getPackagePath(basePackage, moduleName)}.domain.repository;

import com.bone.metadata.sdk.Repository;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.entity.${table.customEntityName};

/**
 * ${table.tableComment!'实体'}仓储。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 */
public interface ${table.customEntityName}Repository extends Repository<${table.customEntityName}, Long> {}
