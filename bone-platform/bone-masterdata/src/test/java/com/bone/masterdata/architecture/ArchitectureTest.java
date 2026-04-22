package com.bone.masterdata.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;

public class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.bone.masterdata");

    @Test
    void domainLayerShouldNotDependOnApplicationOrAdapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..");
        
        rule.check(classes);
    }

    @Test
    void applicationLayerShouldOnlyDependOnDomainAndCommon() {
        ArchRule rule = classes()
                .that().resideInAPackage("..application..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "..domain..", "..application..", "..common..", "com.bone.core..", "java..", "org.springframework..", "org.slf4j..", "lombok.."
                );

        rule.check(classes);
    }

    @Test
    void adapterLayerShouldOnlyDependOnApplicationAndCommon() {
        ArchRule rule = classes()
                .that().resideInAPackage("..adapter..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "..adapter..", "..application..", "..common..", "com.bone.core..", "java..", "org.springframework..", "org.slf4j..", "lombok.."
                );

        rule.check(classes);
    }
}
