package ${utils.getPackagePath(basePackage, moduleName)}.application.command;

<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>

/**
 * 创建${table.tableComment!'实体'}命令。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。命令对象用 {@code record}（不可变），对齐 blueprint
 * {@code application/command} 写法；同一个写意图只由 {@code ${table.customEntityName}ApplicationService} 内联处理，不另起 CommandHandler（ADR-0028）。
 */
public record Create${table.customEntityName}Command(<#list businessColumns as column>${column.javaType} ${column.fieldName}<#sep>, </#sep></#list>) {}
