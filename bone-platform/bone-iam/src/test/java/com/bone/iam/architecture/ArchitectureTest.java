package com.bone.iam.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.bone.iam", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

    @ArchTest
    static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

    // 《Bone-DDD》§14.5
    @ArchTest
    static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

    // 《Bone-DDD》§16.3
    @ArchTest
    static final ArchRule no_custom_business_exception =
            FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

    @ArchTest
    static final ArchRule no_business_exception_suffix =
            FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

    // 《Bone-DDD》P0-1：application -> infrastructure
    @ArchTest
    static final ArchRule application_no_infra =
            BoneDddArchRules.applicationMustNotDependOnInfrastructure();

    // 《Bone-DDD》P0-5 / P0-6
    @ArchTest
    static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

    @ArchTest
    static final ArchRule command_no_query_builder =
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

    // 《Bone-DDD》§18.2：仓储方法白名单
    @ArchTest
    static final ArchRule repository_methods_whitelist =
            BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
                .check(classes);
    }

    @ArchTest
    static void domainCoreShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        classes()
                .that()
                .resideInAPackage("..domain..")
                .and()
                .resideOutsideOfPackage("..domain.service..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.bone.core..",
                        "com.bone.metadata.sdk..",
                        "lombok..",
                        "org.springframework.lang..")
                .check(classes);
    }

    @ArchTest
    static void domainServicesMayUseSpringSecurity(JavaClasses classes) {
        classes()
                .that()
                .resideInAPackage("..domain.service..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.bone.core..",
                        "com.bone.metadata.sdk..",
                        "org.springframework.stereotype..",
                        "org.springframework.security..",
                        "lombok..")
                .check(classes);
    }

    @ArchTest
    static void domainServiceShouldResideInDomain(JavaClasses classes) {
        classes()
                .that()
                .haveNameMatching(".*Service")
                .and()
                .resideOutsideOfPackage("..adapter..")
                .and()
                .resideOutsideOfPackage("..application..")
                .and()
                .resideOutsideOfPackage("..infrastructure..")
                .should()
                .resideInAPackage("..domain..")
                .check(classes);
    }

    @ArchTest
    static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
        classes()
                .that()
                .haveNameMatching(".*Repository")
                .and()
                .areInterfaces()
                .should()
                .resideInAPackage("..domain..")
                .check(classes);
    }

    @ArchTest
    static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
        classes()
                .that()
                .haveNameMatching(".*RepositoryImpl")
                .should()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @ArchTest
    static void domainRepositoriesShouldNotDeclareCustomMethods(JavaClasses classes) {
        classes()
                .that()
                .areInterfaces()
                .and()
                .haveNameMatching(".*Repository")
                .and()
                .resideInAPackage("..domain.repository..")
                .should(notDeclareCustomRepositoryMethods())
                .check(classes);
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass> notDeclareCustomRepositoryMethods() {
        return new ArchCondition<>("not declare custom repository methods") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (method.getOwner().isEquivalentTo(Object.class) || method.isConstructor()) {
                        continue;
                    }
                    String name = method.getName();
                    if ("equals".equals(name) || "hashCode".equals(name) || "toString".equals(name)) {
                        continue;
                    }
                    String message = String.format(
                            "Repository %s declares %s — use Metadata SDK Repository/Criteria",
                            item.getSimpleName(), method.getName());
                    events.add(SimpleConditionEvent.violated(item, message));
                }
            }
        };
    }
}
