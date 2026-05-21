package com.bone.architecture;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Bone DDD 共享 ArchUnit 规则（真源：doc/architecture/Bone-DDD-最终实践方案.md §12、§21）。
 *
 * <p>应用模块在 {@code ArchitectureTest} 中组合使用；对存量违规请配合 {@code
 * com.tngtech.archunit.library.freeze.FreezingArchRule}，仅拦截新增。
 */
public final class BoneDddArchRules {

    private BoneDddArchRules() {}

    public static ArchRule domainMustNotDependOnOuterLayers() {
        return noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
                .because("DDD P0-1: domain must not depend on outer layers");
    }

    public static ArchRule applicationMustNotDependOnInfrastructure() {
        return noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..")
                .because("DDD P0-1: application must not depend on infrastructure implementations");
    }

    public static ArchRule domainMustNotUseQueryBuilder() {
        return noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.bone.metadata.sdk.query.QueryBuilder")
                .because("DDD P0-5: QueryBuilder is read-side only");
    }

    public static ArchRule commandHandlersMustNotUseQueryBuilder() {
        return noClasses()
                .that()
                .resideInAPackage("..application.command.handler..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.bone.metadata.sdk.query.QueryBuilder")
                .because("DDD P0-6: CommandHandler must not use QueryBuilder");
    }

    public static ArchRule noUseCaseClassesInApplication() {
        return noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .haveSimpleNameEndingWith("UseCase")
                .allowEmptyShould(true)
                .because("DDD §14.3: forbid *UseCase; Controller injects Handler");
    }

    public static ArchRule noApplicationUseCasePackage() {
        return noClasses()
                .should()
                .resideInAPackage("..application.usecase..")
                .allowEmptyShould(true)
                .because("DDD §14.3: forbid application/usecase package");
    }

    public static ArchRule noBoneCoreUseCaseApiDependency() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("com.bone.core..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.bone.core.usecase..")
                .because("DDD P0-7: business modules must not depend on bone-core.usecase");
    }

    /** studio-generator 自造 {@code @UseCase} 注解，禁止业务模块新增依赖。 */
    public static ArchRule noStudioGeneratorUseCaseAnnotation() {
        return noClasses()
                .that()
                .resideOutsideOfPackage("com.bone.studio.generator.application.usecase..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.bone.studio.generator.application.usecase..")
                .because("studio-generator must not export custom UseCase SPI to other modules");
    }

    public static ArchRule noNewDomainStorePackage() {
        return noClasses()
                .should()
                .resideInAPackage("..domain.store..")
                .allowEmptyShould(true)
                .because("DDD §14.5: new code uses domain.repository only");
    }
}
