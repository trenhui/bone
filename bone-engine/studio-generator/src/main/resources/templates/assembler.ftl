package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.assembler;

import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Create${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Update${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response.${table.customEntityName}Resp;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Create${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Update${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.query.dto.${table.customEntityName}Dto;
import org.mapstruct.Mapper;

/**
 * ${table.tableComment!'实体'}装配器。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。对齐 blueprint {@code OrderAssembler}：MapStruct
 * {@code @Mapper(componentModel = "spring")} 接口，负责「协议对象 ↔ 应用层契约」的双向翻译，使领域对象与
 * 应用层 Dto 都不出现在控制器里。
 *
 * <p><b>依赖要求</b>：目标模块需引入 {@code org.mapstruct:mapstruct} 与 {@code mapstruct-processor}，
 * 配置方式见 {@code bone-blueprint/pom.xml} 的 dependencies + annotationProcessorPaths 两段。
 */
@Mapper(componentModel = "spring")
public interface ${table.customEntityName}Assembler {

  Create${table.customEntityName}Command toCreate${table.customEntityName}Command(Create${table.customEntityName}Req request);

  Update${table.customEntityName}Command toUpdate${table.customEntityName}Command(Update${table.customEntityName}Req request);

  /**
   * 路径上的 {@code id} 不在请求体里，显式补齐命令首参。
   *
   * <p>不靠 MapStruct 的多源参数推导：那依赖 {@code -parameters} 编译开关，关掉就静默映射出 {@code id=null}。
   */
  default Update${table.customEntityName}Command toUpdate${table.customEntityName}Command(
      Long id, Update${table.customEntityName}Req request) {
    Update${table.customEntityName}Command mapped = toUpdate${table.customEntityName}Command(request);
    return new Update${table.customEntityName}Command(id<#list businessColumns as column>, mapped.${column.fieldName}()</#list>);
  }

  ${table.customEntityName}Resp to${table.customEntityName}Resp(${table.customEntityName}Dto dto);
}
