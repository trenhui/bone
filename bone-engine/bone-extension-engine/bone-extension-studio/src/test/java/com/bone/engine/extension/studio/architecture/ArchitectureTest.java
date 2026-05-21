package com.bone.engine.extension.studio.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(packages = "com.bone.engine.extension.studio")
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());
}
