package com.bone.integration.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试 - 确保 DDD 依赖规则不被违反。
 *
 * <p>平台集成使用 Metadata SDK {@link com.bone.metadata.sdk.Repository}，领域层允许依赖
 * {@code com.bone.metadata.sdk..}；仓储实现由 SDK 代理生成，无 {@code *RepositoryImpl} 类。
 */
@AnalyzeClasses(packages = "com.bone.integration")
public class ArchitectureTest {

    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..");

        rule.check(classes);
    }

    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.bone.core..",
                        "com.bone.metadata.sdk..",
                        "lombok..",
                        "org.springframework.lang..",
                        "org.springframework.stereotype..");

        rule.check(classes);
    }

    @ArchTest
    static void domainServiceShouldResideInDomain(JavaClasses classes) {
        ArchRule rule = classes()
                .that()
                .haveNameMatching(".*Service")
                .and()
                .resideOutsideOfPackage("..adapter..")
                .and()
                .resideOutsideOfPackage("..application..")
                .and()
                .resideOutsideOfPackage("..infrastructure..")
                .should()
                .resideInAPackage("..domain..");

        rule.check(classes);
    }

    @ArchTest
    static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
        ArchRule rule = classes()
                .that()
                .haveNameMatching(".*Repository")
                .should()
                .resideInAPackage("..domain..");

        rule.check(classes);
    }

    @ArchTest
    static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
        ArchRule rule = classes()
                .that()
                .haveNameMatching(".*RepositoryImpl")
                .should()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true);

        rule.check(classes);
    }
}
