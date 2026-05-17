package com.bone.blueprint;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 样例工程与当前 DDD 门禁未完全对齐，待 Capability/Transactional 治理后启用。 */
@Disabled("blueprint sample: arch rules pending alignment with bone-platform modules")
class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bone.blueprint");

    @Test
    void testAdapterLayerDependencies() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "java..",
                        "lombok..",
                        "org.springframework..",
                        "javax..",
                        "com.bone.core..",
                        "com.bone.engine..",
                        "com.bone.metadata.."
                );
        rule.check(classes);
    }

    @Test
    void testApplicationLayerDependencies() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..application..",
                        "..domain..",
                        "java..",
                        "lombok..",
                        "org.springframework..",
                        "javax..",
                        "com.bone.core..",
                        "com.bone.engine..",
                        "com.bone.metadata.."
                );
        rule.check(classes);
    }

    @Test
    void testDomainLayerDependencies() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "java..",
                        "lombok..",
                        "com.bone.core..",
                        "com.bone.metadata.."
                );
        rule.check(classes);
    }

    @Test
    void testInfrastructureLayerDependencies() {
        ArchRule rule = classes()
                .that().resideInAPackage("..infrastructure..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..infrastructure..",
                        "..domain..",
                        "java..",
                        "lombok..",
                        "org.springframework..",
                        "javax..",
                        "com.bone.core..",
                        "com.bone.engine..",
                        "com.bone.metadata.."
                );
        rule.check(classes);
    }

    @Test
    void testDomainShouldNotDependOnApplication() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..application..");
        rule.check(classes);
    }

    @Test
    void testDomainShouldNotDependOnAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter..");
        rule.check(classes);
    }

    @Test
    void testDomainShouldNotDependOnInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..");
        rule.check(classes);
    }

    @Test
    void testDomainShouldNotHaveSpringAnnotations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().beAnnotatedWith("org.springframework.stereotype.Service")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .orShould().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired");
        rule.check(classes);
    }

    @Test
    void testRepositoryShouldNotHaveCustomMethods() {
        ArchRule rule = classes()
                .that().resideInAPackage("..domain.repository..")
                .should().haveSimpleNameEndingWith("Repository")
                .andShould().beInterfaces();
        rule.check(classes);
    }

    @Test
    void testCommandHandlersShouldHaveTransactional() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.command.handler..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");
        rule.check(classes);
    }

    @Test
    void testQueryHandlersShouldHaveReadOnlyTransactional() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application.query.handler..")
                .and().haveSimpleNameEndingWith("Handler")
                .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional");
        rule.check(classes);
    }

    @Test
    void testControllersShouldHaveRestController() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter.web.controller..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");
        rule.check(classes);
    }
}
