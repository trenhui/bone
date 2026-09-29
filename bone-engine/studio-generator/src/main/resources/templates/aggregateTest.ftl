package ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>
import org.junit.jupiter.api.Test;

/**
 * ${table.tableComment!'实体'}聚合纯单测。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 *
 * <p><b>为何必须带这个文件</b>：目标模块的 {@code AggregatePureUnitTestCoverageTest}（TEST-HYGIENE-01）
 * 会检查每个聚合是否有纯单测；只生成聚合不生成测试，模块一接上门禁就是红的。
 *
 * <p><b>纯单测口径</b>：不启 Spring 上下文、不连库、不 mock——聚合的不变量就应该在内存里验完。
 */
class ${table.customEntityName}Test {

<#assign hasRequired = false><#list businessColumns as column><#if !column.nullable><#assign hasRequired = true></#if></#list>
<#if hasRequired>
  @Test
  void createRejectsMissingRequiredField() {
    // 不变量在构造期校验：缺失必填字段必须抛 DomainException，而不是生成一个"半截"对象
    assertThrows(
        DomainException.class,
        () ->
            ${table.customEntityName}.create(<#list businessColumns as column><#if column.nullable><#if column.javaType == 'String'>"x"<#else>null</#if><#else>null</#if><#sep>, </#sep></#list>));
  }

</#if>
  @Test
  void createInitializesIdentityAndOptimisticLock() {
    ${table.customEntityName} entity =
        ${table.customEntityName}.create(<#list businessColumns as column>${utils.sampleOf(column.javaType)}<#sep>, </#sep></#list>);

    assertNotNull(entity);
    assertNotNull(entity.getId());
    // 版本初值必须与库一致（ADR-0031 D2：写路径靠 SET version = version + 1 / WHERE version = :old 判定冲突）
    assertEquals(0L, entity.getVersion());
    assertNotNull(entity.getCreatedAt());
    assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
  }

  @Test
  void applyUpdateRefreshesUpdatedAt() {
    ${table.customEntityName} entity =
        ${table.customEntityName}.create(<#list businessColumns as column>${utils.sampleOf(column.javaType)}<#sep>, </#sep></#list>);

    entity.applyUpdate(<#list businessColumns as column>${utils.sampleOf(column.javaType)}<#sep>, </#sep></#list>);

    assertEquals(0L, entity.getVersion());
    assertNotNull(entity.getUpdatedAt());
  }
}
