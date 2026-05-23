package com.bone.engine.extension.studio.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

@AnalyzeClasses(
        packages = "com.bone.engine.extension.studio",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

    @ArchTest
    static final ArchRule no_bone_core_usecase = BoneDddArchRules.noBoneCoreUseCaseApiDependency();

    @ArchTest
    static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

    @ArchTest
    static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest
    static final ArchRule application_no_infra =
            BoneDddArchRules.applicationMustNotDependOnInfrastructure();

    @ArchTest
    static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

    @ArchTest
    static final ArchRule command_no_query_builder =
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

    @ArchTest
    static final ArchRule repository_methods_whitelist =
            BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

    @ArchTest
    static final ArchRule no_custom_business_exception =
            FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

    @ArchTest
    static final ArchRule no_business_exception_suffix =
            FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());
}
