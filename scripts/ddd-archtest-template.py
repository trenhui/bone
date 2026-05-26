#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
为各应用模块生成统一的 ArchitectureTest，复用 BoneDddArchRules 16 条规则
（《Bone-DDD》§21 附录 B.3）。

用法：
    python3 scripts/ddd-archtest-template.py <module-root> <root-package>

示例：
    python3 scripts/ddd-archtest-template.py bone-platform/bone-iam com.bone.iam

行为：
1. 在 <module-root>/src/test/java/<root-package-path>/architecture/ArchitectureTest.java
   写入统一模板（已有则覆盖）。
2. 保留模块根目录的 archunit_store/（如有，不动）。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TEMPLATE = '''package {package}.architecture;

import com.bone.architecture.BoneDddArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * {module} 架构守护 — 统一来自 {{@link BoneDddArchRules}}（《Bone-DDD》§21 附录 B.3）。
 *
 * <p>首次集成或基线收缩命令见 {{@code bone-framework/bone-architecture-test/README.md}}。
 */
@AnalyzeClasses(packages = "{package}", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {{

    // P0-1
    @ArchTest
    static final ArchRule domain_independent = BoneDddArchRules.domainMustNotDependOnOuterLayers();

    @ArchTest
    static final ArchRule application_no_infra =
            BoneDddArchRules.applicationMustNotDependOnInfrastructure();

    // P0-5
    @ArchTest
    static final ArchRule domain_no_query_builder = BoneDddArchRules.domainMustNotUseQueryBuilder();

    // P0-6
    @ArchTest
    static final ArchRule command_no_query_builder =
            BoneDddArchRules.commandHandlersMustNotUseQueryBuilder();

    // P0-4 + §18.2
    @ArchTest
    static final ArchRule repository_methods_whitelist =
            BoneDddArchRules.domainRepositoriesShouldOnlyDeclareWhitelistedMethods();

    // P0-7 + §14.3
    @ArchTest
    static final ArchRule no_new_use_cases =
            FreezingArchRule.freeze(BoneDddArchRules.noUseCaseClassesInApplication());

    @ArchTest
    static final ArchRule no_usecase_package =
            FreezingArchRule.freeze(BoneDddArchRules.noApplicationUseCasePackage());

    @ArchTest
    static final ArchRule no_bone_core_usecase =
            BoneDddArchRules.noBoneCoreUseCaseApiDependency();

    // §14.5
    @ArchTest
    static final ArchRule no_new_domain_store =
            FreezingArchRule.freeze(BoneDddArchRules.noNewDomainStorePackage());

    // §16.3
    @ArchTest
    static final ArchRule no_custom_business_exception =
            FreezingArchRule.freeze(BoneDddArchRules.noCustomBusinessException());

    @ArchTest
    static final ArchRule no_business_exception_suffix =
            FreezingArchRule.freeze(BoneDddArchRules.noBusinessExceptionSuffix());

    // P0-7 + §15 + §23（存量 freeze，迁移后收缩基线）
    @ArchTest
    static final ArchRule adapter_no_application_service =
            FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnApplicationService());

    @ArchTest
    static final ArchRule adapter_no_domain_repository =
            FreezingArchRule.freeze(BoneDddArchRules.adapterControllersMustNotDependOnDomainRepository());

    @ArchTest
    static final ArchRule command_handler_naming =
            FreezingArchRule.freeze(BoneDddArchRules.commandHandlersShouldBeNamedCommandHandler());

    @ArchTest
    static final ArchRule query_handler_naming =
            FreezingArchRule.freeze(BoneDddArchRules.queryHandlersShouldBeNamedQueryHandler());

    @ArchTest
    static final ArchRule command_handler_transactional =
            FreezingArchRule.freeze(BoneDddArchRules.commandHandlersShouldBeTransactional());

    @ArchTest
    static final ArchRule query_handler_transactional =
            FreezingArchRule.freeze(BoneDddArchRules.queryHandlersShouldBeReadOnlyTransactional());
}}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("module_root")
    parser.add_argument("root_package")
    parser.add_argument(
        "--extra-archunit",
        action="append",
        default=[],
        help="额外 BoneDddArchRules.* 规则名（如 noStudioGeneratorUseCaseAnnotation）",
    )
    args = parser.parse_args()

    root = Path(args.module_root).resolve()
    pkg = args.root_package
    test_dir = root / "src" / "test" / "java" / Path(*pkg.split(".")) / "architecture"
    test_dir.mkdir(parents=True, exist_ok=True)
    target = test_dir / "ArchitectureTest.java"

    body = TEMPLATE.format(package=pkg, module=root.name)
    if args.extra_archunit:
        extra_block = "\n".join(
            f'    @ArchTest\n    static final ArchRule extra_{i} = '
            f'FreezingArchRule.freeze(BoneDddArchRules.{rule}());'
            for i, rule in enumerate(args.extra_archunit)
        )
        body = body.replace("}\n", extra_block + "\n}\n", 1)

    target.write_text(body, encoding="utf-8")
    print(f"[ok] wrote {target}")


if __name__ == "__main__":
    main()
