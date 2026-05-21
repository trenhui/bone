package com.bone.masterdata.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureTest {

    private final com.tngtech.archunit.core.domain.JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.bone.masterdata");

    @Test
    void domainLayerShouldNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..application..", "..adapter..");
        rule.check(classes);
    }

    @Test
    void applicationLayerMayUseDomainCoreAndMetadataSdk() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("..application..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..domain..",
                        "..application..",
                        "..common..",
                        "com.bone.core..",
                        "com.bone.metadata.sdk..",
                        "..infrastructure..",
                        "java..",
                        "org.springframework..",
                        "org.slf4j..",
                        "lombok..",
                        "jakarta..",
                        "com.fasterxml.jackson..");
        rule.check(classes);
    }

    @Test
    void noNewUseCaseClasses() {
        FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication()).check(classes);
    }

    @Test
    void noApplicationUseCasePackage() {
        FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage()).check(classes);
    }

    @Test
    void adapterLayerMayUseApplicationDomainAndCore() {
        ArchRule rule = classes()
                .that()
                .resideInAPackage("..adapter..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter..",
                        "..application..",
                        "..domain..",
                        "..common..",
                        "com.bone.core..",
                        "java..",
                        "org.springframework..",
                        "org.slf4j..",
                        "lombok..",
                        "jakarta..",
                        "io.swagger..");
        rule.check(classes);
    }
}
