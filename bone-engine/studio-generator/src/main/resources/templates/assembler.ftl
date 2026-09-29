package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.assembler;

import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Create${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request.Update${table.customEntityName}Req;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response.${table.customEntityName}Resp;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Create${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Update${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.query.dto.${table.customEntityName}Dto;
import org.springframework.stereotype.Component;

/**
 * ${table.tableComment!'实体'}装配器。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。职责同 blueprint {@code OrderAssembler}：负责
 * 「协议对象 ↔ 应用层契约」的双向翻译，使领域对象与应用层 Dto 都不出现在控制器里。
 *
 * <p><b>为何手写 {@code @Component} 而非 MapStruct</b>：生成骨架须「开箱即编即跑」。MapStruct 是编译期注解处理器，
 * 缺依赖时不会生成实现类，Spring 注入 Assembler 直接 {@code NoSuchBeanDefinition}，生成物沦为「半残」。
 * 手写映射与 MapStruct 字段按 {@code businessColumns} 同序对齐，逻辑完全等价；蓝图本模块自带 mapstruct 才用它，
 * 对生成物而言零依赖更稳妥（HC-003 要求聚合不外泄到 HTTP 出口，与映射实现方式无关）。
 */
@Component
public class ${table.customEntityName}Assembler {

  /** 请求体 → 创建命令（record 不可变入参）。 */
  public Create${table.customEntityName}Command toCreate${table.customEntityName}Command(Create${table.customEntityName}Req request) {
    if (request == null) {
      return null;
    }
    return new Create${table.customEntityName}Command(
<#list businessColumns as column>        request.${column.getter}()<#sep>,
</#sep></#list>);
  }

  /**
   * 请求体 + 路径 id → 更新命令。聚合 id 来自路径参数 {@code /{id}}，不在请求体里，这里显式补齐命令首参，
   * 避免「多源参数映射出 id=null」的暗坑（MapStruct 依赖 {@code -parameters} 编译开关，关掉即静默错配）。
   */
  public Update${table.customEntityName}Command toUpdate${table.customEntityName}Command(
      Long id, Update${table.customEntityName}Req request) {
    if (request == null) {
      return null;
    }
    return new Update${table.customEntityName}Command(
        id<#list businessColumns as column>,
        request.${column.getter}()</#list>);
  }

  /** 应用层读模型 → 对外响应契约（{@code *Resp}）。 */
  public ${table.customEntityName}Resp to${table.customEntityName}Resp(${table.customEntityName}Dto dto) {
    if (dto == null) {
      return null;
    }
    return ${table.customEntityName}Resp.builder()
        .id(dto.getId())
<#list businessColumns as column>        .${column.fieldName}(dto.${column.getter}())
</#list>        .createdAt(dto.getCreatedAt())
        .updatedAt(dto.getUpdatedAt())
        .build();
  }
}
