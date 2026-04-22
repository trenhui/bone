package com.bone.blueprint;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构测试
 * <p>
 * 验证项目的架构设计是否符合DDD和CQRS的最佳实践
 * </p>
 */
public class ArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.bone.blueprint");
    
    /**
     * 验证适配器层依赖规则
     */
    @Test
    public void testAdapterLayerDependencies() {
        ArchRule rule = classes()
            .that().resideInAPackage("..adapter..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..application..", "org.springframework..");
        rule.check(classes);
    }
    
    /**
     * 验证应用层依赖规则
     */
    @Test
    public void testApplicationLayerDependencies() {
        ArchRule rule = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..application..", "..domain..", "org.springframework..");
        rule.check(classes);
    }
    
    /**
     * 验证领域层依赖规则
     */
    @Test
    public void testDomainLayerDependencies() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..");
        rule.check(classes);
    }
    
    /**
     * 验证基础设施层依赖规则
     */
    @Test
    public void testInfrastructureLayerDependencies() {
        ArchRule rule = classes()
            .that().resideInAPackage("..infrastructure..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..domain..", "org.springframework..");
        rule.check(classes);
    }
    
    /**
     * 验证没有common包依赖
     */
    @Test
    public void testNoCommonPackageDependency() {
        ArchRule rule = noClasses()
            .should().dependOnClassesThat()
            .resideInAPackage("..common..");
        rule.check(classes);
    }
}