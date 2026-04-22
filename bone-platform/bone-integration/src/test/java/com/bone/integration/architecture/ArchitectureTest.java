package com.bone.integration.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试 - 确保DDD依赖规则不被违反
 *
 * 规则：
 * 1. 领域层不应该依赖外层（adapter、application、infrastructure）
 * 2. 领域层只允许依赖：java.*、com.bone.core.*、自身领域包
 * 3. 应用层不应该被领域层依赖
 * 4. 基础设施不应该被领域层依赖
 */
@AnalyzeClasses(packages = "com.bone.integration")
public class ArchitectureTest {

    @ArchTest
    static void domainLayerShouldNotDependOnOuterLayers(JavaClasses classes) {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "..infrastructure.."
                );

        rule.check(classes);
    }

    @ArchTest
    static void domainLayerShouldOnlyDependOnAllowedPackages(JavaClasses classes) {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "com.bone.core..",
                        "lombok..",
                        "org.springframework.lang.."
                );

        rule.check(classes);
    }

    @ArchTest
    static void domainServiceShouldResideInDomain(JavaClasses classes) {
        ArchRule rule = classes()
                .that().haveNameMatching(".*Service")
                .should().resideInAPackage("..domain.service..")
                .orShould().resideInAPackage("..domain..");

        rule.check(classes);
    }

    @ArchTest
    static void repositoryInterfacesShouldBeInDomain(JavaClasses classes) {
        ArchRule rule = classes()
                .that().haveNameMatching(".*Repository")
                .should().resideInAPackage("..domain..");

        rule.check(classes);
    }

    @ArchTest
    static void repositoryImplementationsShouldBeInInfrastructure(JavaClasses classes) {
        ArchRule rule = classes()
                .that().haveNameMatching(".*RepositoryImpl")
                .should().resideInAPackage("..infrastructure..");

        rule.check(classes);
    }
}
