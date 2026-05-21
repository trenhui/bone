package com.bone.studio.generator.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.studio.generator")
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());
}
