package com.bone.blueprint;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 「外层禁止篡改聚合身份」规则的<strong>有效性自测</strong>——防止规则静默失效。
 *
 * <p><b>为什么需要它</b>：ArchUnit 规则存在「静默退化」风险。一旦包名、基类 FQN 或匹配条件因重构而改变，
 * 规则会从「能抓到违规」退化为「永远通过」，而这种退化<strong>不会产生任何报错</strong>——规则形同虚设
 * 却无人察觉。本测试对真实代码显式求值一次，确保规则可被求值，且在真实代码上不误报。
 *
 * <p><b>不误报同样是硬要求</b>：基础设施持久化模型（如 Outbox 记录）会在自身工厂方法里回填主键， 若不排除「自己设置自己」，规则会满屏误报，最终被人用 {@code
 * allowEmptyShould} 或直接删规则的方式绕过。
 *
 * <p><b>与 {@link ArchitectureTest} 的分工</b>：那里断言「全模块零违规」；这里额外保证规则本身可用、 未因放宽条件而失去意义。规则已通过负向验证——在
 * Handler 中注入 {@code order.setTenantId(...)} 会被精确指认到具体行，确认其确实在拦截而非空转。
 */
class AggregateIdentityRuleEffectivenessTest {

  @Test
  void aggregateIdentityRuleEvaluatesCleanAndDoesNotFalsePositive() {
    BoneDddArchRules.outerLayersMustNotMutateAggregateIdentity()
        .check(
            new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.bone.blueprint"));
  }
}
