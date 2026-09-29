package ${utils.getPackagePath(basePackage, moduleName)}.application.command;

<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>

/**
 * 更新${table.tableComment!'实体'}命令。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。首参为聚合 id：更新是「先加载再改」，路径上的 id 与请求体字段
 * 是两个来源，装配到板子里就要求你必须分清——故显式放在命令首位。
 */
public record Update${table.customEntityName}Command(Long id<#list businessColumns as column>, ${column.javaType} ${column.fieldName}</#list>) {}
