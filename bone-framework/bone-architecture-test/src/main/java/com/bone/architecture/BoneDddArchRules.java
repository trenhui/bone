package com.bone.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Bone DDD 共享 ArchUnit 规则（真源：{@code doc/architecture/Bone-DDD-最终实践方案.md} 的 CORE-01～CORE-10 核心规则与
 * G-1 门禁规则集）。
 *
 * <p>文档自 v4.6 起采用 P-/E-/G- 三段编号（v5 起核心规则改用 {@code CORE-*}）；本类注释中的 {@code §xx} 与 {@code R1–R9} 均为
 * <b>v4.x 历史章节号</b>，仅作追溯索引，不是现行条文编号，勿据此对外引用。
 *
 * <p>应用模块在 {@code ArchitectureTest} 中组合使用；对存量违规请配合 {@code
 * com.tngtech.archunit.library.freeze.FreezingArchRule}，仅拦截新增。
 */
public final class BoneDddArchRules {

  /**
   * E-4.1 写侧仓储方法允许的标量返回类型。
   *
   * <p>ADR-0030 R2 扩展：纳入计数类 {@code long}/{@code Long}/{@code int}/{@code Integer}——领域读模型合并进写侧仓储后，
   * 部分扫描类方法会返回受影响行数 / 命中计数，属合法标量，不应被「只返回聚合根」判据误伤。
   */
  private static final Set<String> REPOSITORY_SCALAR_RETURN_TYPES =
      Set.of(
          "void",
          "boolean",
          "java.lang.Boolean",
          "long",
          "java.lang.Long",
          "int",
          "java.lang.Integer");

  /**
   * 禁止进入方法名的两类词汇（E-4.1 主判据补充）：
   *
   * <ul>
   *   <li><b>持久化实现词汇</b>：{@code insert / update / delete / persist / flush / merge} —
   *       泄漏了技术实现细节，不属领域语言。
   *   <li><b>读侧意图词汇</b>：{@code exists} — 把"存在性判断"塞进写仓储，既绕过 CORE-05，又诱导应用层写出"先查后判"竞态。
   * </ul>
   *
   * 聚合级动词 {@code save} / {@code remove} 属 SDK 标准写法，不在禁用之列。
   */
  private static final Set<String> REPOSITORY_FORBIDDEN_METHOD_WORDS =
      Set.of("insert", "update", "delete", "persist", "flush", "merge", "exists");

  private static final String TRANSACTIONAL =
      "org.springframework.transaction.annotation.Transactional";

  /** bone-core 实体基类 FQN：聚合/实体的最终父类，用于识别「聚合身份 setter」的调用目标。 */
  private static final String ENTITY_BASE_CLASS = "com.bone.core.domain.entity.Entity";

  /** bone-core 分页结果类型 FQN（ADR-0030 R2：允许写侧仓储以 {@code PageResult<领域读模型>} 返回分页投影）。 */
  private static final String PAGE_RESULT_TYPE = "com.bone.core.model.PageResult";

  /** E-2 租户上下文 FQN：业务层禁止直调，仅 infrastructure 访问（v4.7 补门禁）。 */
  private static final String TENANT_CONTEXT_CLASS = "com.bone.core.tenant.context.TenantContext";

  /** 聚合身份 setter 名（{@code AggregateRoot.setId} / {@code TenantAggregateRoot.setTenantId}）。 */
  private static final Set<String> AGGREGATE_IDENTITY_SETTERS = Set.of("setId", "setTenantId");

  /** 聚合根基类 FQN（R9 判定：只有聚合根才计入「一事务一聚合」）。 */
  private static final String AGGREGATE_ROOT_CLASS = "com.bone.core.domain.AggregateRoot";

  /** SDK 仓储基接口 FQN（用于解析仓储的实体泛型参数）。 */
  private static final String SDK_REPOSITORY_CLASS = "com.bone.metadata.sdk.Repository";

  /**
   * ADR-0030 R5：SDK 自定义 SQL 注解 FQN。
   *
   * <p>读写合并进同一 {@code domain.repository} 接口后，{@code @Sql} 标注的方法属「自定义 SQL 通道」的读形态， 与 {@code
   * default}（有方法体）读方法一样，不得参与「一事务一聚合」的持久化计数。
   */
  private static final String SQL_ANNOTATION = "com.bone.metadata.sdk.domain.annotation.Sql";

  /** R9 判定用：写侧仓储的持久化方法名，出现即视为「持久化了一个聚合」。 */
  private static final Set<String> PERSIST_METHODS =
      Set.of(
          "save",
          "saveAll",
          "insert",
          "insertAll",
          "update",
          "updateAll",
          "remove",
          "removeAll",
          "delete",
          "persist");

  /** R9 判定用：写事务的入口方法名。 */
  private static final Set<String> TRANSACTION_ENTRY_METHODS = Set.of("handle", "execute");

  /** 持久化适配器角色后缀（#18 豁免）：这些类设置聚合身份属法定职责（主键回填 / ORM 恢复 / PO 映射）， 不属外层篡改。 */
  private static final List<String> PERSISTENCE_ADAPTER_SUFFIXES =
      List.of(
          "Repository",
          "RepositoryImpl",
          "Converter",
          "Mapper",
          "RowMapper",
          "Assembler",
          "Persister");

  /** 默认共享内核包（bone-core）：跨上下文规则恒不视为「其它上下文」。 */
  private static final String SHARED_KERNEL_PACKAGE = "com.bone.core";

  /** 默认共享内核包（bone-metadata-sdk，唯一持久化方案）：跨上下文规则恒不视为「其它上下文」。 */
  private static final String METADATA_SDK_PACKAGE = "com.bone.metadata.sdk";

  private BoneDddArchRules() {}

  public static ArchRule domainMustNotDependOnOuterLayers() {
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..adapter..", "..application..", "..infrastructure..")
        .because("DDD P0-1: domain must not depend on outer layers");
  }

  public static ArchRule applicationMustNotDependOnInfrastructure() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..infrastructure..")
        .because("DDD P0-1: application must not depend on infrastructure implementations");
  }

  public static ArchRule domainMustNotUseQueryBuilder() {
    // 例外：domain.repository 是 SDK 框架集成点——基类 Repository<T, ID> 自带
    // updateByCriteria(Criteria<T>) 与 Criteria/QueryBuilder 通道，ADR-0030 明确把"本聚合读"落在
    // 域仓储的 default 方法上（E-4.1「domain 内只允许 domain.repository 触碰读侧 DSL」）。
    // 该例外原先由 blueprint / iam / system 各自在模块级复制，2026-09-20 收敛回共享规则，
    // 避免"每个模块抄一遍、抄漏就静默失去约束"。
    return noClasses()
        .that()
        .resideInAPackage("..domain..")
        .and()
        .resideOutsideOfPackage("..domain.repository")
        .should()
        .dependOnClassesThat(areAnnotatedWithReadSideOnly())
        .allowEmptyShould(true)
        .because(
            "DDD P0-5 / E-4.1: read-side DSL (@ReadSideOnly) must not appear in domain, except in "
                + "domain.repository which is the SDK framework integration point "
                + "(updateByCriteria / Criteria channel, ADR-0030)");
  }

  public static ArchRule commandHandlersMustNotUseQueryBuilder() {
    return noClasses()
        .that()
        .resideInAPackage("..application.command.handler..")
        .should()
        .dependOnClassesThat(areAnnotatedWithReadSideOnly())
        .allowEmptyShould(true)
        .because("DDD P0-6: CommandHandler must not use read-side DSL");
  }

  public static ArchRule noUseCaseClassesInApplication() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .haveSimpleNameEndingWith("UseCase")
        .allowEmptyShould(true)
        .because("DDD §14.3: forbid *UseCase; Controller injects Handler");
  }

  public static ArchRule noApplicationUseCasePackage() {
    return noClasses()
        .should()
        .resideInAPackage("..application.usecase..")
        .allowEmptyShould(true)
        .because("DDD §14.3: forbid application/usecase package");
  }

  /** 防回滚：{@code com.bone.core.usecase} 已从 bone-core 删除。不纳入 freeze（无存量命中）。 */
  public static ArchRule noBoneCoreUseCaseApiDependency() {
    return noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.core.usecase..")
        .because(
            "DDD P0-7: com.bone.core.usecase was removed; use Handler + com.bone.core.capability");
  }

  /** studio-generator 自造 {@code @UseCase} 注解，禁止业务模块新增依赖。 */
  public static ArchRule noStudioGeneratorUseCaseAnnotation() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.studio.generator.application.usecase..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("com.bone.studio.generator.application.usecase..")
        .because("studio-generator must not export custom UseCase SPI to other modules");
  }

  public static ArchRule noNewDomainStorePackage() {
    return noClasses()
        .should()
        .resideInAPackage("..domain.store..")
        .allowEmptyShould(true)
        .because("DDD §14.5: new code uses domain.repository only");
  }

  /**
   * §16.3：业务模块禁止自建 {@code BusinessException} / {@code *BusinessException}， 统一使用 {@code
   * com.bone.core.exception.BizException}。允许 {@code *BizException} 后缀基类 （如 {@code
   * MetadataEngineBizException}）**继承** {@code BizException}，但禁止 simpleName 等于 {@code
   * BusinessException} 或以 {@code BusinessException} 结尾。
   */
  public static ArchRule noCustomBusinessException() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.core..")
        .should()
        .haveSimpleName("BusinessException")
        .allowEmptyShould(true)
        .because("DDD §16.3: business exception is com.bone.core.exception.BizException");
  }

  /**
   * §16.3 加强：禁止业务/引擎模块新增 {@code *BusinessException} 后缀类，鼓励改名为 {@code *BizException} 或继承 {@code
   * BizException}；引擎 SDK 使用 {@code ExtensionBizException} 等命名。
   */
  public static ArchRule noBusinessExceptionSuffix() {
    return noClasses()
        .that()
        .resideOutsideOfPackage("com.bone.core..")
        .should()
        .haveSimpleNameEndingWith("BusinessException")
        .allowEmptyShould(true)
        .because("DDD §16.3: prefer *BizException or extend com.bone.core.exception.BizException");
  }

  /**
   * <b>ADR-0028（2026-09-14）</b>：默认入站边界是语义化 {@code *ApplicationService}（Application Service
   * First），因此<strong>取消</strong>对 {@code ..application.service..} 的一刀切禁止；仅守护 {@code
   * ..application..} 层 {@code *Manager} / {@code Common*} / {@code Base*} / {@code Business*}
   * 命名的上帝对象反模式。{@code ..application.facade..} / {@code ..application.orchestration..} 门面与编排层不受影响。
   *
   * <p><b>命名说明</b>：本规则原名 {@code adapterControllersMustNotDependOnApplicationService}，其字面含义与
   * ADR-0028 相反（Controller 依赖合法 {@code *ApplicationService} 恰恰是被允许的），故改名为 {@code
   * adaptersMustNotDependOnGodObjects} 以准确表意；旧名保留为过渡别名。
   *
   * <p><b>谓词范围（2026-09-19 收口）</b>：原谓词为 {@code ..adapter..controller..}，导致 {@code adapter.schedule}
   * / {@code adapter.messaging} / {@code adapter.rpc} 等入站构件<strong>全部逃逸、永远绿</strong> （规则存在 ≠
   * 规则覆盖）。现放宽为 {@code ..adapter..}：任一层级的入站适配器注入上帝对象都会被拦下。
   *
   * <p>注意：{@code .because(...)} 文案保持不变，以维持各模块 {@code FreezingArchRule} 基线的冻结键稳定。
   */
  public static ArchRule adaptersMustNotDependOnGodObjects() {
    DescribedPredicate<JavaClass> forbiddenApplicationTargets =
        resideInAPackage("..application..")
            .and(
                simpleNameStartingWith("Common")
                    .or(simpleNameStartingWith("Base"))
                    .or(simpleNameStartingWith("Business"))
                    .or(simpleNameEndingWith("Manager")));
    return noClasses()
        .that()
        .resideInAPackage("..adapter..")
        .should()
        .dependOnClassesThat(forbiddenApplicationTargets)
        .allowEmptyShould(true)
        .because(
            "DDD §15 + ADR-0028: adapter must not inject anemic god-objects "
                + "(Common/Base/Business/*Manager) in application; a well-named "
                + "*ApplicationService is a valid default inbound entry");
  }

  /**
   * 过渡别名：语义由 {@link #adaptersMustNotDependOnGodObjects()} 承载（见 ADR-0028）。保留以兼容尚在引用旧名的模块 {@code
   * ArchitectureTest} 与工具脚本；新代码请改用新名。
   *
   * @deprecated 使用 {@link #adaptersMustNotDependOnGodObjects()}
   */
  @Deprecated
  public static ArchRule adapterControllersMustNotDependOnGodObjects() {
    return adaptersMustNotDependOnGodObjects();
  }

  /**
   * 过渡别名：语义已由 {@link #adaptersMustNotDependOnGodObjects()} 承载（见 ADR-0028）。保留以兼容尚在 引用旧名的模块 {@code
   * ArchitectureTest} 与工具脚本；新代码请改用新名。
   *
   * @deprecated 使用 {@link #adaptersMustNotDependOnGodObjects()}
   */
  @Deprecated
  public static ArchRule adapterControllersMustNotDependOnApplicationService() {
    return adaptersMustNotDependOnGodObjects();
  }

  /**
   * §15：入站 adapter 禁止直接注入 {@code domain.repository} 写侧仓储。
   *
   * <p><b>谓词范围（2026-09-19 收口）</b>：原谓词 {@code ..adapter..controller..} 使 {@code adapter.schedule} /
   * {@code messaging} / {@code rpc} 全部逃逸。现放宽为 {@code ..adapter..}，仅对 {@code ..adapter.schedule..}
   * 开一个<strong>受控例外</strong>——全租户运维扫描（超时关单 / 关闭超时支付 / 钱货对账）需直连域仓储，这是 ADR-0030 代价 C3
   * 已登记的授权形态；其滥用面由模块级 {@code schedule_only_calls_all_tenants_repository_methods} 类规则收紧 （限定只能调
   * {@code *AllTenants} 方法）。命名由 {@code adapterControllers...} 改为 {@code adapters...} 以匹配新谓词。
   */
  public static ArchRule adaptersMustNotDependOnDomainRepository() {
    return noClasses()
        .that()
        .resideInAPackage("..adapter..")
        .and()
        .resideOutsideOfPackage("..adapter.schedule..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.repository..")
        .allowEmptyShould(true)
        .because("DDD §15: adapter must not inject domain.repository; use Handler");
  }

  /**
   * 过渡别名：语义由 {@link #adaptersMustNotDependOnDomainRepository()} 承载。保留以兼容尚在引用旧名的模块 {@code
   * ArchitectureTest} 与工具脚本；新代码请改用新名。
   *
   * @deprecated 使用 {@link #adaptersMustNotDependOnDomainRepository()}
   */
  @Deprecated
  public static ArchRule adapterControllersMustNotDependOnDomainRepository() {
    return adaptersMustNotDependOnDomainRepository();
  }

  /**
   * P0-7 + §15：入站 adapter 禁止直接注入 {@code domain.service} 领域服务。
   *
   * <p>领域服务只能由 {@code *CommandHandler} / {@code *QueryHandler} / {@code *Orchestrator}
   * 在应用层编排时调用，不允许 adapter 层越层直接依赖，以保持分层边界清晰。
   *
   * <p><b>谓词范围（2026-09-19 收口）</b>：与仓储规则同形，由 {@code ..adapter..controller..} 放宽为 {@code
   * ..adapter..}；此处<strong>不开</strong> schedule 例外——定时任务也没有绕过应用层直调领域服务的授权理由。
   */
  public static ArchRule adaptersMustNotDependOnDomainService() {
    return noClasses()
        .that()
        .resideInAPackage("..adapter..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.service..")
        .allowEmptyShould(true)
        .because("DDD P0-7 + §15: adapter must not inject domain.service; use Handler");
  }

  /**
   * 过渡别名：语义由 {@link #adaptersMustNotDependOnDomainService()} 承载。保留以兼容尚在引用旧名的模块 {@code
   * ArchitectureTest} 与工具脚本；新代码请改用新名。
   *
   * @deprecated 使用 {@link #adaptersMustNotDependOnDomainService()}
   */
  @Deprecated
  public static ArchRule adapterControllersMustNotDependOnDomainService() {
    return adaptersMustNotDependOnDomainService();
  }

  /**
   * §23：{@code application.command.handler} 下类名应以 {@code CommandHandler} 结尾。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：命名合规不可机器验证语义质量，改用 R8 聚合纯单测 （{@link
   * AggregatePureUnitTestGuard}）作为反贫血主判据；本规则保留供 CR / 手动检查使用，各模块 {@code ArchitectureTest} 不再以
   * {@code @ArchTest} 引用（见 tasks 2.5）。
   */
  public static ArchRule commandHandlersShouldBeNamedCommandHandler() {
    return classes()
        .that()
        .resideInAPackage("..application.command.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .haveSimpleNameEndingWith("CommandHandler")
        .allowEmptyShould(true)
        .because("DDD §23: command handler classes must be named *CommandHandler");
  }

  /**
   * §23：{@code application.query.handler} 下类名应以 {@code QueryHandler} 结尾。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：同 {@link #commandHandlersShouldBeNamedCommandHandler()}， 保留供 CR
   * / 手动检查使用，各模块 {@code ArchitectureTest} 不再以 {@code @ArchTest} 引用。
   */
  public static ArchRule queryHandlersShouldBeNamedQueryHandler() {
    return classes()
        .that()
        .resideInAPackage("..application.query.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .haveSimpleNameEndingWith("QueryHandler")
        .allowEmptyShould(true)
        .because("DDD §23: query handler classes must be named *QueryHandler");
  }

  /**
   * §12.1 + §15：写侧 Handler 建议在类或 {@code handle}/{@code execute} 方法上标注 {@code @Transactional}。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：事务边界是编写习惯而非命名约束，改由 CR / 代码评审把关； 本规则保留供手动检查，各模块 {@code
   * ArchitectureTest} 不再以 {@code @ArchTest} 引用。
   */
  public static ArchRule commandHandlersShouldBeTransactional() {
    return classes()
        .that()
        .resideInAPackage("..application.command.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(haveTransactionalOnClassOrEntryMethod())
        .allowEmptyShould(true)
        .because("DDD §15: write transaction boundary on *CommandHandler");
  }

  /**
   * §12.1 P0-6：读侧 Handler 建议 {@code @Transactional(readOnly = true)}。
   *
   * <p><b>v4.5 已降级为 warn（非硬门禁）</b>：同 {@link #commandHandlersShouldBeTransactional()}， 保留供 CR /
   * 手动检查使用，各模块 {@code ArchitectureTest} 不再以 {@code @ArchTest} 引用。
   */
  public static ArchRule queryHandlersShouldBeReadOnlyTransactional() {
    return classes()
        .that()
        .resideInAPackage("..application.query.handler..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(haveTransactionalOnClassOrEntryMethod())
        .allowEmptyShould(true)
        .because("DDD §12.1 P0-6: QueryHandler should use @Transactional(readOnly = true)");
  }

  /**
   * §15 + §18.2（E-4.1 主判据）：写侧仓储是「聚合根的集合抽象」——方法返回类型只能是聚合根 / {@code Optional<聚合根>} / 标量（void、
   * boolean、Boolean、long、Long、int、Integer）；返回 application DTO / 跨层读模型即违规（那是读侧 QueryBuilder 的职责）。
   *
   * <p><b>ADR-0030 R2 扩展</b>：允许以 {@code List} / {@code Optional} / {@code PageResult}
   * 承载<strong>领域层内的读模型</strong> （投影 / 值对象，必须住在 {@code ..domain..} 内——P0-1 规定 domain 不得依赖
   * application，故读模型不可能来自 application 层）。 这是「领域读模型合并进写侧仓储」后的必要放宽，并非放开 DTO 泄露：若返回类型来自 {@code
   * ..application..} 仍判违规。
   *
   * <p>允许复合自然键（如 {@code findByIdInTenant(id, tenantId)}，只要返回聚合）；方法名不得携带持久化词汇 （{@code
   * insert/update/delete/persist/flush/merge}），聚合级动词 {@code save} / {@code remove} 除外。
   *
   * <p>方法名保留旧名 {@code ShouldOnlyDeclareWhitelistedMethods} 以兼容各模块 {@code ArchitectureTest}
   * 引用；判据已从「方法名白名单（脆弱启发式：{@code queryByStatusAndType} 可绕过）」升级为「返回类型」。
   */
  public static ArchRule domainRepositoriesShouldOnlyDeclareWhitelistedMethods() {
    return classes()
        .that()
        .areInterfaces()
        .and()
        .haveSimpleNameEndingWith("Repository")
        .and()
        .resideInAPackage("..domain.repository..")
        .should(onlyDeclareAggregateLoadMethods())
        .allowEmptyShould(true);
  }

  /**
   * E-1.3 / P-2.4：跨上下文边界守护——本模块 {@code ..domain..} 禁止直接依赖<strong>其它 Bone 上下文</strong>的 {@code
   * domain} 包（跨上下文集成须经公开 API / 事件契约 / 防腐层，而非直接 import 对方领域模型）。
   *
   * <p><b>判定方式</b>：按依赖目标的全限定名匹配（匹配字节码中记录的引用，与 classpath 无关）；排除 自身模块根包与共享内核包（默认 {@code
   * com.bone.core} 与 {@code com.bone.metadata.sdk}，可通过参数追加更多白名单）。
   *
   * <p><b>不加 {@code allowEmptyShould(true)}</b>：空匹配视为配置错误——若本模块没有任何 {@code domain}
   * 类，规则立即失败，防止「对方类不在 classpath 时规则空命中即通过」等于没有守护。
   *
   * @param selfRootPackage 本模块根包（如 {@code com.bone.blueprint}）；其下 {@code domain} 包中的类为判定主体
   * @param sharedKernelPackages 追加的共享内核白名单包（默认已含 {@code com.bone.core}、{@code
   *     com.bone.metadata.sdk}）
   */
  public static ArchRule noCrossContextDomainDependency(
      String selfRootPackage, String... sharedKernelPackages) {
    DescribedPredicate<JavaClass> otherContextDomain =
        otherContextDomainPredicate(selfRootPackage, sharedKernelList(sharedKernelPackages));
    return noClasses()
        .that()
        .resideInAPackage(selfRootPackage + ".domain..")
        .should()
        .dependOnClassesThat(otherContextDomain)
        .because(
            "DDD P-2.3 + P-2.4: cross-context integration must go through public API / event "
                + "contract / anti-corruption layer, not direct domain-type dependency; empty "
                + "match means the rule is misconfigured");
  }

  private static boolean isWithinOrEquals(String fqn, String root) {
    return fqn.equals(root) || fqn.startsWith(root + ".");
  }

  private static ArchCondition<JavaClass> onlyDeclareAggregateLoadMethods() {
    return new ArchCondition<>(
        "only return aggregate root / Optional<aggregate root> / boolean / void") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod method : item.getMethods()) {
          if (method.getModifiers().contains(JavaModifier.STATIC)) {
            continue;
          }
          String name = method.getName();
          if (hasForbiddenWordsInName(name)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "Repository %s declares method %s with forbidden vocabulary — write-side "
                            + "repository methods must read as domain operations; use QueryPort for "
                            + "read-side queries and atomic constraints for existence checks",
                        item.getSimpleName(), name)));
            continue;
          }
          if (!returnsAggregateRootOrScalar(method)) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "Repository %s declares method %s returning %s — write-side repositories must "
                            + "return aggregate root / Optional<aggregate root> / scalar (void, boolean, "
                            + "Boolean, long, Long, int, Integer) / List|Optional|PageResult of a domain "
                            + "read-model (projection or value object living in ..domain..); application "
                            + "DTOs and cross-layer read models are still forbidden — use QueryBuilder for "
                            + "those",
                        item.getSimpleName(),
                        name,
                        method.getReturnType().toErasure().getSimpleName())));
          }
        }
      }
    };
  }

  /**
   * 写侧仓储方法返回类型是否合规（E-4.1 基线 + ADR-0030 R2 扩展）。
   *
   * <p><b>基线</b>：聚合根 / {@code Optional<聚合根>} / 标量（void、boolean、Boolean、long、Long、int、Integer）。
   *
   * <p><b>ADR-0030 R2 扩展（领域读模型合并进写侧仓储）</b>：允许以 {@code List} / {@code Optional} / {@code PageResult}
   * 承载<strong>领域层内的读模型</strong>——投影（{@code ..domain..projection..}）与值对象（{@code
   * ..domain..valueobject..}）。这些类型必须住在 {@code ..domain..} 内：P0-1 规定 domain 不得依赖
   * application，故读模型不可能来自 application 层，以此与「返回 application DTO」严格区分，守住 E-4.1「写侧不泄露读侧 DTO」的本意。
   */
  private static boolean returnsAggregateRootOrScalar(JavaMethod method) {
    JavaType returnType = method.getReturnType();
    JavaClass rawType = returnType.toErasure();
    String typeName = rawType.getName();
    if (REPOSITORY_SCALAR_RETURN_TYPES.contains(typeName)) {
      return true;
    }
    if (rawType.isAssignableTo(ENTITY_BASE_CLASS)) {
      return true;
    }
    if (returnType instanceof JavaParameterizedType parameterized
        && parameterized.getActualTypeArguments().size() == 1) {
      JavaType arg = parameterized.getActualTypeArguments().get(0);
      boolean argOk = isEntityType(arg) || isDomainType(arg);
      if ("java.util.Optional".equals(typeName)
          || "java.util.List".equals(typeName)
          || PAGE_RESULT_TYPE.equals(typeName)) {
        return argOk;
      }
    }
    return false;
  }

  /**
   * ADR-0030 R2：返回类型的元素住在领域层（包名含 {@code .domain.}）即视为合规读模型（投影 / 值对象）。
   *
   * <p>用全限定名包含 {@code .domain.} 判定，而非依赖 classpath 上该类的实际解析——与规则其余部分「按字节码记录的引用匹配」 的口径一致，且天然排除 {@code
   * com.bone.core.model.PageResult} / {@code java.util.List} 等框架容器自身。
   */
  private static boolean isDomainType(JavaType type) {
    return type instanceof JavaClass clazz && clazz.getName().contains(".domain.");
  }

  private static boolean isEntityType(JavaType type) {
    return type instanceof JavaClass clazz && clazz.isAssignableTo(ENTITY_BASE_CLASS);
  }

  private static boolean hasForbiddenWordsInName(String methodName) {
    for (String word : camelCaseWords(methodName)) {
      if (REPOSITORY_FORBIDDEN_METHOD_WORDS.contains(word)) {
        return true;
      }
    }
    return false;
  }

  /** 驼峰拆词并小写：{@code findByIdInTenant} → [find, by, id, in, tenant]。 */
  private static List<String> camelCaseWords(String name) {
    return Arrays.stream(name.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
        .map(String::toLowerCase)
        .collect(Collectors.toList());
  }

  private static ArchCondition<JavaClass> haveTransactionalOnClassOrEntryMethod() {
    return new ArchCondition<>("declare @Transactional on class or handle/execute method") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.isAnnotatedWith(TRANSACTIONAL)) {
          return;
        }
        boolean entryMethodTransactional =
            item.getMethods().stream()
                .filter(
                    method ->
                        "handle".equals(method.getName()) || "execute".equals(method.getName()))
                .anyMatch(method -> method.isAnnotatedWith(TRANSACTIONAL));
        if (!entryMethodTransactional) {
          String message =
              String.format(
                  "%s must declare @Transactional on class or handle()/execute() method",
                  item.getSimpleName());
          events.add(SimpleConditionEvent.violated(item, message));
        }
      }
    };
  }

  private static DescribedPredicate<JavaClass> areAnnotatedWithReadSideOnly() {
    return new DescribedPredicate<>("annotated with @ReadSideOnly") {
      @Override
      public boolean test(JavaClass input) {
        return input.isAnnotatedWith("com.bone.core.annotation.ReadSideOnly");
      }
    };
  }

  /**
   * §3.1 聚合边界：外层（application / adapter / infrastructure）<strong>禁止直接调用</strong>聚合/实体的 {@code setId}
   * / {@code setTenantId}。
   *
   * <p><b>为何约束调用方，而不是把 setter 改成 protected</b>：{@code AggregateRoot.setId} 需供 SDK 反射回填 主键，{@code
   * TenantAggregateRoot.setTenantId} 是实现 {@code Tenantable} 接口的契约方法——二者<strong> 必须保持
   * public</strong>，降级为 protected 会破坏全平台编译。后果是聚合的身份与租户归属在语言层面
   * 可被任意外部代码篡改，对多租户系统尤其危险（改租户即越权跨租户访问）。既然无法约束<strong>被调用
   * 方</strong>，就改为约束<strong>调用方</strong>：只有领域层内部（工厂方法、聚合行为）可设置身份。
   *
   * <p>只匹配调用目标是 {@code Entity} 子类的 setter，故 DTO / Builder 上的 {@code setId} 不受影响。
   */
  public static ArchRule outerLayersMustNotMutateAggregateIdentity() {
    return noClasses()
        .that()
        .resideInAnyPackage("..application..", "..adapter..", "..infrastructure..")
        .and(notPersistenceAdapterRole())
        .should()
        // 注意：DescribedPredicate 是抽象类而非函数式接口，必须写匿名类，不能用 lambda。
        .callMethodWhere(
            new DescribedPredicate<JavaMethodCall>("set a domain aggregate's id or tenantId") {
              @Override
              public boolean test(JavaMethodCall call) {
                AccessTarget.MethodCallTarget target = call.getTarget();
                if (!AGGREGATE_IDENTITY_SETTERS.contains(target.getName())) {
                  return false;
                }
                JavaClass owner = target.getOwner();
                if (!owner.isAssignableTo(ENTITY_BASE_CLASS)) {
                  return false;
                }
                // 排除基础设施 PO：PO↔领域对象互转时设置主键 / 租户列是映射动作，PO 不是聚合、
                // 不承载一致性边界。（bone-extension-studio 的 StudioPersistenceConverter 即因此误报。）
                if (owner.getPackageName().contains(".infrastructure.")) {
                  return false;
                }
                // 排除「自己设置自己」：基础设施持久化模型（如 Outbox 记录）在自身工厂方法里回填主键
                // 是正常用法。真正要防的是外层代码篡改<strong>业务聚合</strong>的身份 / 租户归属。
                return !call.getOriginOwner().equals(call.getTargetOwner());
              }
            })
        .because(
            "DDD §3.1 / E-8: aggregate id/tenantId must only be set inside domain — SDK requires "
                + "the setters to stay public, so callers are constrained instead; persistence "
                + "adapters (Repository/Converter/Mapper) and PO mapping are exempt");
  }

  /**
   * 持久化适配器角色豁免：{@code *Repository} / {@code *Converter} / {@code *Mapper} 等类的职责就是 主键回填、ORM 恢复与
   * PO↔领域对象互转，设置身份是<strong>法定职责</strong>而非篡改——E-7.1 明确 SDK 在 {@code insert} / {@code save}
   * 时生成并回填主键，E-8 也承认 ORM 恢复经字段级反射。
   *
   * <p>真正的越权风险在<strong>业务代码</strong>（{@code application} / {@code adapter}）改聚合的租户 归属或主键，那部分不受本豁免影响。
   */
  private static DescribedPredicate<JavaClass> notPersistenceAdapterRole() {
    return new DescribedPredicate<JavaClass>(
        "not be a persistence adapter (Repository/Converter/Mapper/Assembler)") {
      @Override
      public boolean test(JavaClass input) {
        String simpleName = input.getSimpleName();
        return PERSISTENCE_ADAPTER_SUFFIXES.stream().noneMatch(simpleName::endsWith);
      }
    };
  }

  /**
   * R9（v4.6）：一个写事务内只持久化<strong>一个</strong>聚合根（真源：主规范 E-5.1）。
   *
   * <p><b>为何单列一条</b>：聚合在 DDD 中的定义即「一致性边界」。若一个事务内同时写多个聚合，等于宣称
   * 二者的不变量必须同时成立——那它们本就该是同一个聚合；若不是，则事务边界与一致性边界背离，既放大锁 竞争，又把最终一致伪装成强一致（本地事务只能保证本库原子）。
   *
   * <p><b>判定方式</b>：对 {@code ..application.command.handler..} / {@code ..application.service..} /
   * {@code ..application.orchestration..} 下具体类的<strong>入口方法</strong>（{@code handle} / {@code
   * execute}；不存在时回退到全部 public 实例方法），收集其直接调用中目标位于 {@code ..domain.repository..} 的持久化方法，按
   * <strong>Repository 接口类型</strong>去重计数， {@code > 1} 即违规。
   *
   * <p><b>为何扫描范围含 service / orchestration（v4.7 修正）</b>：Handler 委托给 ApplicationService /
   * Orchestrator 后， 直接在 Handler 上扫描 save 调用将完全不可见（v4.6 的盲区）——ApplicationService 同样可能是写事务入口，须受 R9 约束
   * （E-6.4 内容禁令 #5）。门禁按「直接调用」判定：Handler → Service → save(A)+save(B) 会在 Service 层被拦截。
   *
   * <p><b>为何按「聚合根类型」而非「Repository 类型」去重（v4.7 修正）</b>：R9 约束的对象是<strong>聚合</strong>，不是仓储接口。
   * 按仓储计数会把聚合内子实体的仓储（如 {@code OrderItemRepository}）误判为第二个聚合，进而诱导实现方把端口挪出 {@code domain.repository}
   * 包来"绕过门禁"——既违反 E-5.5，也让规则形同虚设。改为解析 {@code Repository<T, ID>} 的 泛型参数，只有 {@code T} 是 {@code
   * AggregateRoot} 子类时才计数，{@code Order} + {@code OrderItem} 因此正确地算作一个聚合。<b>按调用次数仍不去重</b>：同一聚合
   * {@code save} 两次仍属单聚合（幂等 upsert）。
   *
   * <p><b>ADR-0030 R5 改造（读写同接口后按写方法语义计数）</b>：{@code default}（有方法体）与 {@code @Sql} 标注的仓储方法
   * 属读侧/自定义通道，一律不计入持久化计数（见 {@link #isReadSideRepositoryMethod}）；否则「写 + 本聚合读」合并进同一接口后会被误报。
   */
  public static ArchRule oneAggregatePerTransaction() {
    return classes()
        .that()
        .resideInAnyPackage(
            "..application.command.handler..",
            "..application.service..",
            "..application.orchestration..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(persistAtMostOneAggregateTypePerTransaction())
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> persistAtMostOneAggregateTypePerTransaction() {
    return new ArchCondition<>("persist at most one aggregate type per write transaction") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaMethod entry : transactionEntryMethods(item)) {
          Set<String> aggregates = new TreeSet<>();
          for (JavaMethodCall call : entry.getMethodCallsFromSelf()) {
            AccessTarget.MethodCallTarget target = call.getTarget();
            if (!PERSIST_METHODS.contains(target.getName())) {
              continue;
            }
            if (!isDomainRepository(target.getOwner())) {
              continue;
            }
            if (isReadSideRepositoryMethod(target)) {
              continue;
            }
            // 按「被持久化的聚合根类型」计数：聚合内子实体的仓储不计入（见 persistedAggregateRootKey）
            String aggregateKey = persistedAggregateRootKey(target.getOwner());
            if (aggregateKey == null) {
              continue;
            }
            aggregates.add(aggregateKey);
          }
          if (aggregates.size() > 1) {
            events.add(
                SimpleConditionEvent.violated(
                    item,
                    String.format(
                        "%s.%s() persists %d aggregate types in one transaction %s — R9: one"
                            + " aggregate per transaction; use domain events / Outbox /"
                            + " Orchestrator for cross-aggregate changes",
                        item.getSimpleName(), entry.getName(), aggregates.size(), aggregates)));
          }
        }
      }
    };
  }

  /** 写事务入口方法：名为 handle / execute 的方法；不存在时回退到全部 public 实例方法。 */
  private static List<JavaMethod> transactionEntryMethods(JavaClass item) {
    List<JavaMethod> named =
        item.getMethods().stream()
            .filter(method -> TRANSACTION_ENTRY_METHODS.contains(method.getName()))
            .collect(Collectors.toList());
    if (!named.isEmpty()) {
      return named;
    }
    return item.getMethods().stream()
        .filter(
            method ->
                method.getModifiers().contains(JavaModifier.PUBLIC)
                    && !method.getModifiers().contains(JavaModifier.STATIC))
        .collect(Collectors.toList());
  }

  private static boolean isDomainRepository(JavaClass owner) {
    return owner.isInterface() && owner.getPackageName().contains(".domain.repository");
  }

  /**
   * ADR-0030 R5：判断被调用的仓储方法是否属于<strong>读侧</strong>，读侧方法不参与「一事务一聚合」计数。
   *
   * <p><b>为何要判</b>：ADR-0030 把「写 + 本聚合读」合并进同一 {@code domain.repository} 接口后，写事务入口里出现的
   * 读方法调用会与写方法混在同一仓储类型上。若仍按 Repository 类型计数，同一聚合的读形态可能被误算成「第二个聚合写入」， 反而逼实现方为了过门禁而拆掉合并形态。
   *
   * <p><b>判据</b>：目标方法是 {@code default}（有方法体，即 {@code !ABSTRACT}），或标注了 SDK 的 {@code @Sql} （自定义 SQL
   * 通道的读形态）。二者都是读侧/自定义通道；SDK 继承来的抽象写方法（{@code save}/{@code update}…）不受影响。
   *
   * <p><b>解析不到目标成员时保守放行</b>（返回 {@code false}）——宁可多报，不因解析失败而漏掉真实的多聚合写入。
   */
  private static boolean isReadSideRepositoryMethod(AccessTarget.MethodCallTarget target) {
    return target
        .resolveMember()
        .filter(
            member ->
                !member.getModifiers().contains(JavaModifier.ABSTRACT)
                    || member.isAnnotatedWith(SQL_ANNOTATION))
        .isPresent();
  }

  /**
   * R9 计数键：返回该仓储持久化的<strong>聚合根类型名</strong>；不是聚合根时返回 {@code null}（不计入 R9）。
   *
   * <p><b>为何按聚合根类型而非 Repository 类型计数（v4.7 修正）</b>：R9 约束的是「一个事务内只提交一个<strong>聚合</strong> 的变更」。若按
   * Repository 接口计数，聚合内子实体（如 {@code OrderItem}）的仓储会被误算成第二个"聚合"， 迫使实现方为了让门禁变绿而<strong>把仓储挪出 {@code
   * domain.repository} 包</strong>——那既违反 E-5.5（端口包唯一）， 又让门禁失去意义（改个包名就能绕过）。按类型判定后，「Order + OrderItem
   * 同属一个聚合」是自然结论， 包位置回归规范。
   *
   * <p><b>解析方式</b>：从仓储接口的 {@code Repository<T, ID>} 泛型实参取 {@code T}，再判断 {@code T} 是否继承 {@code
   * AggregateRoot}；解析不到泛型时<strong>保守按仓储名计数</strong>（宁可多报，不放过）。
   */
  private static String persistedAggregateRootKey(JavaClass repository) {
    JavaClass entity = resolveRepositoryEntityType(repository);
    if (entity == null) {
      return repository.getSimpleName(); // 保守兜底：泛型不可解析时仍计数
    }
    return isAggregateRoot(entity) ? entity.getSimpleName() : null;
  }

  /** 解析仓储接口的实体泛型参数 {@code T}（递归向父接口查找 {@code com.bone.metadata.sdk.Repository}）。 */
  private static JavaClass resolveRepositoryEntityType(JavaClass repository) {
    for (JavaType iface : repository.getInterfaces()) {
      if (SDK_REPOSITORY_CLASS.equals(iface.toErasure().getName())) {
        if (iface instanceof JavaParameterizedType parameterized) {
          List<JavaType> args = parameterized.getActualTypeArguments();
          if (!args.isEmpty()) {
            return args.get(0).toErasure();
          }
        }
        return null;
      }
      JavaClass nested = resolveRepositoryEntityType(iface.toErasure());
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static boolean isAggregateRoot(JavaClass entity) {
    if (AGGREGATE_ROOT_CLASS.equals(entity.getName())) {
      return true;
    }
    return entity.getAllRawSuperclasses().stream()
        .anyMatch(superClass -> AGGREGATE_ROOT_CLASS.equals(superClass.getName()));
  }

  /**
   * E-2（v4.7 补门禁）：租户取值收敛到模块统一端口（{@code TenantProvider}）。
   *
   * <p><b>为何新增门禁</b>：规范禁止业务代码散落 {@code TenantContext.get*()} 直调——线程本地直调读得分散、
   * 无法审计、异步线程丢失，且框架层写入、业务层只读。此前只有条文无门禁（违反 CORE-08 原则：规范不应宣称 尚未具备支撑能力的约束），本版补齐。
   *
   * <p><b>判定</b>：{@code application} / {@code domain} / {@code adapter} 调用 {@code TenantContext}
   * 任意方法即违规； 仅允许 {@code infrastructure}（写入过滤器、{@code TenantProvider} 端口实现）访问。领域层通过 {@code
   * domain/gateway/TenantProvider} 端口取值，实现下沉 infrastructure。
   */
  public static ArchRule businessLayersMustNotReadTenantContextDirectly() {
    return noClasses()
        .that()
        .resideInAnyPackage("..application..", "..domain..", "..adapter..")
        .should()
        .callMethodWhere(
            new DescribedPredicate<JavaMethodCall>(
                "call com.bone.core.tenant.context.TenantContext") {
              @Override
              public boolean test(JavaMethodCall call) {
                return call.getTargetOwner().getName().equals(TENANT_CONTEXT_CLASS);
              }
            })
        .allowEmptyShould(true)
        .because(
            "DDD E-2: tenant context reads must go through the module's single TenantProvider port"
                + " (domain/gateway) implemented in infrastructure; direct TenantContext calls in"
                + " application/domain/adapter bypass audit, propagation and null-guards");
  }

  /**
   * E-4.2（v4.6 主判据）：读侧 DSL 只允许出现在基础设施查询层，禁止进入 {@code application}。
   *
   * <p><b>为何用位置判据替代注解判据</b>：注解判据（{@code @ReadSideOnly}）属「标记坏人」式否定检测——新增 一个 DSL
   * 类型忘了标注，所有依赖它的类全部静默放行。位置判据按包路径判定，漏标无法绕过。注解规则 {@link #domainMustNotUseQueryBuilder()} / {@link
   * #commandHandlersMustNotUseQueryBuilder()} 保留为防回滚补充。
   *
   * <p><b>目标态</b>：{@code *QueryHandler} 只依赖读侧端口（{@code *ReadPort}），DSL 实现下沉 {@code
   * infrastructure/query}。存量在 QueryHandler 中直用 DSL 的代码以 {@code FreezingArchRule} 登记后逐步 收敛（E-4.2
   * 过渡期双轨）。
   */
  public static ArchRule readSideDslOnlyInQueryLayer() {
    return noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat(areReadSideDsl())
        .allowEmptyShould(true)
        .because(
            "DDD E-4.2: read-side DSL must live in infrastructure/query behind a *ReadPort; "
                + "the application layer must not depend on persistence DSL");
  }

  private static DescribedPredicate<JavaClass> areReadSideDsl() {
    return new DescribedPredicate<JavaClass>("be a read-side DSL type") {
      @Override
      public boolean test(JavaClass input) {
        return input.getPackageName().startsWith("com.bone.metadata.sdk.query")
            || input.isAnnotatedWith("com.bone.core.annotation.ReadSideOnly");
      }
    };
  }

  /**
   * E-6.4（v4.6 内容禁令）：{@code application/service} 可承载<strong>用例级编排</strong>，但不得承载 领域规则——具体禁止两件事：
   *
   * <ol>
   *   <li>{@code new} 领域对象（须经聚合工厂方法或 Repository 获取）；
   *   <li>调用聚合 setter 修改状态（须经领域行为方法，反贫血 R2）。
   * </ol>
   *
   * <p>v4.5 曾全面禁止 {@code application/service} 这一<strong>名称</strong>，结果真实需求被逼进 Orchestrator / Facade
   * / ApplicationService 三套例外通道。v4.6 改为约束<strong>内容</strong>——管住 「里面不许有什么」，比管「不许叫什么」更紧，且可机器判定。
   */
  public static ArchRule applicationServicesMustNotOwnDomainRules() {
    return classes()
        .that()
        .resideInAPackage("..application.service..")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(notOwnDomainRules())
        .allowEmptyShould(true);
  }

  private static ArchCondition<JavaClass> notOwnDomainRules() {
    return new ArchCondition<>("not instantiate or mutate domain objects directly") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
          JavaClass owner = call.getTarget().getOwner();
          if (!isDomainModel(owner)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  String.format(
                      "%s instantiates domain type %s — application services must obtain domain"
                          + " objects from aggregate factory methods or a Repository (E-6.4)",
                      item.getSimpleName(), owner.getSimpleName())));
        }
        for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
          AccessTarget.MethodCallTarget target = call.getTarget();
          if (!target.getName().startsWith("set")) {
            continue;
          }
          if (!target.getOwner().isAssignableTo(ENTITY_BASE_CLASS)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  item,
                  String.format(
                      "%s calls %s.%s() — application services must change state through domain"
                          + " behaviour methods, not setters (E-6.4 / R2 anti-anemia)",
                      item.getSimpleName(), target.getOwner().getSimpleName(), target.getName())));
        }
      }
    };
  }

  /** 领域模型类型：{@code ..domain..} 包下的 {@code Entity} 子类（排除基础设施 PO 与 DTO）。 */
  private static boolean isDomainModel(JavaClass owner) {
    return owner.isAssignableTo(ENTITY_BASE_CLASS)
        && (owner.getPackageName().contains(".domain.")
            || owner.getPackageName().endsWith(".domain"));
  }

  /**
   * E-1.3（v4.6）：跨上下文<strong>模型</strong>依赖守护——本模块<strong>任何层</strong>（含读侧 QueryHandler 与 {@code
   * infrastructure.query}）均禁止直接依赖其它 Bone 上下文的 {@code domain} 模型类型。
   *
   * <p><b>与 {@link #noCrossContextDomainDependency(String, String...)} 的区别</b>：后者只约束本模块 {@code
   * domain} 包的依赖（战术层守护），本条覆盖<strong>全模块</strong>，用于拦截「读侧直接 {@code
   * QueryBuilder.from(其他上下文的实体)}」——这是限界上下文最容易被穿透的地方：类型依赖好查，跨表读 难查，而后者恰恰是数据所有权失守的主要形式。
   *
   * <p>同样<b>不加</b> {@code allowEmptyShould(true)}：空匹配视为配置错误。
   *
   * @param selfRootPackage 本模块根包（如 {@code com.bone.integration}）
   * @param sharedKernelPackages 追加的共享内核白名单包（默认已含 {@code com.bone.core}、{@code
   *     com.bone.metadata.sdk}）
   */
  public static ArchRule noCrossContextModelDependency(
      String selfRootPackage, String... sharedKernelPackages) {
    DescribedPredicate<JavaClass> otherContextDomain =
        otherContextDomainPredicate(selfRootPackage, sharedKernelList(sharedKernelPackages));
    return noClasses()
        .that()
        .resideInAPackage(selfRootPackage + "..")
        .should()
        .dependOnClassesThat(otherContextDomain)
        .because(
            "DDD E-1.3: data ownership is per context — reading another context's domain model "
                + "(including read-side queries) must go through public API or event projection; "
                + "empty match means the rule is misconfigured");
  }

  private static DescribedPredicate<JavaClass> otherContextDomainPredicate(
      String selfRootPackage, List<String> sharedKernel) {
    return new DescribedPredicate<JavaClass>("belong to another Bone context's domain package") {
      @Override
      public boolean test(JavaClass target) {
        String name = target.getName();
        if (!name.startsWith("com.bone.")) {
          return false;
        }
        if (isWithinOrEquals(name, selfRootPackage)) {
          return false;
        }
        for (String shared : sharedKernel) {
          if (isWithinOrEquals(name, shared)) {
            return false;
          }
        }
        return name.contains(".domain.");
      }
    };
  }

  private static List<String> sharedKernelList(String... extraPackages) {
    List<String> sharedKernel = new ArrayList<>();
    sharedKernel.add(SHARED_KERNEL_PACKAGE);
    sharedKernel.add(METADATA_SDK_PACKAGE);
    sharedKernel.addAll(Arrays.asList(extraPackages));
    return sharedKernel;
  }

  /** Engine 模块（建模 / 扩展 / 生成）与 SDK 底座包根。 */
  private static final String[] ENGINE_PACKAGES = {
    "com.bone.metadata..", "com.bone.engine.extension..", "com.bone.studio.generator.."
  };

  /** Platform 可独立部署服务包根（bone-platform/*）。 */
  private static final String[] PLATFORM_PACKAGES = {
    "com.bone.iam..",
    "com.bone.masterdata..",
    "com.bone.integration..",
    "com.bone.system..",
    "com.bone.notification..",
    "com.bone.gateway.."
  };

  /** Engine 可部署应用壳包根（不含 SDK 底座，见《BONE 总体架构》§4.1.1 例外 2）。 */
  private static final String[] ENGINE_APP_PACKAGES = {
    "com.bone.metadata.server..",
    "com.bone.metadata.engine..",
    "com.bone.engine.extension.studio..",
    "com.bone.studio.generator.."
  };

  /**
   * 模块层级规则一（ARCH-LEVEL-01）：Engine 不得依赖 Platform。
   *
   * <p>真源：《BONE 总体架构设计方案》§4.1.1 依赖方向。现状应先扫 0 违规后启用；SDK 底座因仅依赖 bone-core，天然通过。
   */
  public static ArchRule engineModulesMustNotDependOnPlatform() {
    return noClasses()
        .that()
        .resideInAnyPackage(ENGINE_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(PLATFORM_PACKAGES)
        .because(
            "ARCH-LEVEL-01: engine must not depend on platform; direction is "
                + "Platform -> Engine -> Framework -> Kernel")
        .allowEmptyShould(true);
  }

  /**
   * 模块层级规则二（ARCH-LEVEL-02）：Platform 不得依赖 Engine 可部署应用壳，仅允许依赖 SDK 底座。
   *
   * <p>真源：《BONE 总体架构设计方案》§4.1.1 例外 1/2。metadata-sdk / extension-sdk 不在禁用列表， 天然白名单。
   */
  public static ArchRule platformMustNotDependOnEngineApps() {
    return noClasses()
        .that()
        .resideInAnyPackage(PLATFORM_PACKAGES)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ENGINE_APP_PACKAGES)
        .because(
            "ARCH-LEVEL-02: platform must not depend on engine deployable apps; "
                + "only the SDK base (metadata-sdk / extension-sdk) may be consumed")
        .allowEmptyShould(true);
  }

  // =========================================================================
  // E-5.4 DomainEvent 发布前置判断：save() 必须配 publishFrom() 或声明豁免
  // =========================================================================

  /**
   * E-5.4（Advisory → Hard gate）：application 层的 CommandHandler / ApplicationService / Orchestrator
   * 凡调用了 {@code *.Repository.save()}，必须满足以下二选一：
   *
   * <ol>
   *   <li>同一类中也有 {@code publishFrom()} 调用（不管是否在同一方法内——TransactionTemplate 两段式是合法的）
   *   <li>类级别声明 {@code @NoDomainEvent}，且被注解标记的聚合方法必须在 JavaDoc 中说明豁免理由（E-5.4）
   * </ol>
   *
   * <p>EventHandler 不参与此规则——事件驱动场景下 save → publishFrom 是不同 handler 的独立职责。
   */
  public static ArchRule applicationSaveMustPairWithPublishOrExempt() {
    return classes()
        .that()
        .resideInAPackage("..application..")
        .and()
        .haveSimpleNameNotEndingWith("EventHandler")
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should(savePairedWithPublishFromOrExempt())
        .as("E-5.4: application 层 Repository.save() 必须配 publishFrom() 或声明 @NoDomainEvent")
        .because("E-5.4 DomainEvent 发布前置判断：save() 后缺 publishFrom() 既非豁免也无 @NoDomainEvent 视为违规");
  }

  private static ArchCondition<JavaClass> savePairedWithPublishFromOrExempt() {
    return new ArchCondition<>(
        "have publishFrom() call for every Repository save/write call (or use @NoDomainEvent)") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        // @NoDomainEvent 注解标记的类整体豁免
        if (item.isAnnotatedWith("com.bone.core.annotation.NoDomainEvent")) {
          return;
        }
        boolean hasRepositorySave =
            item.getMethodCallsFromSelf().stream()
                .anyMatch(
                    call -> {
                      String name = call.getTarget().getName();
                      String owner = call.getTarget().getOwner().getName();
                      return (name.equals("save") || name.startsWith("save"))
                          && owner.endsWith("Repository");
                    });
        if (!hasRepositorySave) {
          return;
        }
        boolean hasPublishFrom =
            item.getMethodCallsFromSelf().stream()
                .anyMatch(call -> "publishFrom".equals(call.getTarget().getName()));
        if (!hasPublishFrom) {
          events.add(
              new SimpleConditionEvent(
                  item,
                  false,
                  String.format(
                      "%s 存在 Repository 写入（save / update 等）但无 publishFrom()。"
                          + "若该状态迁移属 E-5.4 三类豁免（内部状态迁移/终态/技术中间态），"
                          + "请在类上声明 @NoDomainEvent 并确保聚合方法 JavaDoc 说明豁免理由；"
                          + "否则需补充 publishFrom()。",
                      item.getFullName())));
        }
      }
    };
  }

  // =========================================================================
  // E-13.0 第二道防线：包表达协议之后，同模块 Spring bean 名必须唯一
  // =========================================================================

  /** Spring stereotype 注解 FQN；其显式 {@code value()} 即显式 bean 名（E-13.0）。 */
  private static final List<String> SPRING_STEREOTYPE_ANNOTATIONS =
      List.of(
          "org.springframework.stereotype.Component",
          "org.springframework.stereotype.Service",
          "org.springframework.stereotype.Repository",
          "org.springframework.stereotype.Controller",
          "org.springframework.web.bind.annotation.RestController",
          "org.springframework.context.annotation.Configuration");

  private static final String SPRING_COMPONENT = "org.springframework.stereotype.Component";

  /**
   * E-13.0：同一模块内 Spring 组件的**有效 bean 名**必须唯一。
   *
   * <p><b>它堵的是什么洞</b>：E-13.0 让包路径承担协议标识后，两个协议可以合法地使用同一个业务类名（{@code
   * adapter/web/controller/OrderController} 与 {@code adapter/rpc/controller/OrderController}）。类名合法，
   * 但 Spring 默认 {@code AnnotationBeanNameGenerator} 取**类短名**注册 bean，两个 {@code orderController}
   * 会让容器在启动期抛 {@code ConflictingBeanDefinitionException}。
   *
   * <p><b>为什么必须是机器门禁</b>：消解冲突的两种手段（显式 bean 名、MapStruct {@code implementationName}）
   * 都是**隐式补充**——默认名不撞就不写。漏写既无编译错误也无测试失败（多数模块没有容器级测试）， 只在启动时炸。本规则按同一口径推算有效 bean 名，把失败从启动期提前到构建期。
   *
   * <p><b>判定口径</b>：直接或经元注解标注 {@code @Component} 的**具体类**（含 MapStruct 生成的 {@code *Impl}——生成类带
   * {@code @Component}，故 {@code implementationName} 的效果自动体现在类短名里）； 注解显式 {@code value()}
   * 优先，否则取类短名首字母小写（与 {@code Introspector#decapitalize} 一致）。 接口、抽象类、内部类与仅由组合注解标注的类不参与判定。
   *
   * <p>空匹配不算配置错误（纯领域模块可以没有任何 Spring 组件），故 {@code allowEmptyShould(true)}。
   */
  public static ArchRule springComponentBeanNamesMustBeUnique() {
    return classes()
        .that()
        .areMetaAnnotatedWith(SPRING_COMPONENT)
        .should(new UniqueSpringBeanNameCondition())
        .as("E-13.0: 同一模块内 Spring 组件的 bean 名必须唯一")
        .because(
            "E-13.0 包表达边界：协议由包路径声明后，同模块两个同名类会按类短名注册 bean，"
                + "启动期抛 ConflictingBeanDefinitionException；冲突须在 DI 标识上消解"
                + "（显式 bean 名 / MapStruct implementationName），不要靠类名加协议标记")
        .allowEmptyShould(true);
  }

  /** 集合级判定：{@code init} 收全集并按 bean 名分组，{@code check} 逐类报告同组冲突。 */
  private static final class UniqueSpringBeanNameCondition extends ArchCondition<JavaClass> {

    private final Map<String, List<JavaClass>> classesByBeanName = new LinkedHashMap<>();

    private UniqueSpringBeanNameCondition() {
      super("have a unique Spring bean name within the module");
    }

    @Override
    public void init(Collection<JavaClass> allClasses) {
      classesByBeanName.clear();
      for (JavaClass javaClass : allClasses) {
        effectiveBeanName(javaClass)
            .ifPresent(
                beanName ->
                    classesByBeanName
                        .computeIfAbsent(beanName, key -> new ArrayList<>())
                        .add(javaClass));
      }
    }

    @Override
    public void check(JavaClass item, ConditionEvents events) {
      String beanName = effectiveBeanName(item).orElse(null);
      if (beanName == null) {
        return;
      }
      List<JavaClass> sameName = classesByBeanName.get(beanName);
      if (sameName == null || sameName.size() < 2) {
        return;
      }
      events.add(
          new SimpleConditionEvent(
              item,
              false,
              String.format(
                  "bean 名 '%s' 被 %d 个类占用：%s。E-13.0：同模块 bean 名必须唯一，"
                      + "请用显式 bean 名（如 @RestController(\"rpcOrderController\")）或 MapStruct "
                      + "implementationName 消解，不要改回给类名加协议标记。",
                  beanName,
                  sameName.size(),
                  sameName.stream()
                      .map(JavaClass::getFullName)
                      .sorted()
                      .collect(Collectors.joining("、")))));
    }

    /** 有效 bean 名：注解显式 value 优先，否则按 `AnnotationBeanNameGenerator` 取类短名首字母小写。 */
    private static Optional<String> effectiveBeanName(JavaClass javaClass) {
      if (javaClass.getSimpleName().indexOf('$') >= 0
          || javaClass.isInterface()
          || javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
          || !javaClass.isMetaAnnotatedWith(SPRING_COMPONENT)) {
        return Optional.empty();
      }
      for (JavaAnnotation<JavaClass> annotation : javaClass.getAnnotations()) {
        if (!SPRING_STEREOTYPE_ANNOTATIONS.contains(annotation.getRawType().getName())) {
          continue;
        }
        Object explicitName = annotation.get("value").orElse(null);
        if (explicitName instanceof String name && !name.isBlank()) {
          return Optional.of(name);
        }
        break;
      }
      return Optional.of(decapitalize(javaClass.getSimpleName()));
    }

    /** 与 `java.beans.Introspector#decapitalize` 对齐：前两个字母均大写时原样返回。 */
    private static String decapitalize(String simpleName) {
      if (simpleName.length() > 1 && Character.isUpperCase(simpleName.charAt(1))) {
        return simpleName;
      }
      return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
  }

  // ================================================================================================
  // 全租户（ALL）入口：事实判据、调用面收口与命名绑定（E-2 / E-4.4 / CORE-05，ADR-0030 / ADR-0034）
  //
  // 为什么要有这一组：全租户扫描绕过租户隔离，是唯一"调错一行就静默跨租户"的能力。它有两条实现通道，
  // 过去只有一条被门禁看得见——SQL 通道靠 @TenantScope(ALL)（TenantSqlRewriter 真读它），Criteria 通道
  // 靠 Criteria.disableTenantFilter()（TenantFilterInjector 不读注解）。只按注解判定会漏掉 Criteria 通道，
  // 只按方法名判定又会被"改个名字"绕过，所以这里用「注解 ∨ disableTenantFilter」作为事实判据，
  // 再用命名绑定规则把可读性要求（*AllTenants 后缀）钉在事实之上。
  // ================================================================================================

  /** SDK 租户策略注解：按名匹配，避免共享规则库 compile 期依赖 bone-metadata-sdk。 */
  private static final String TENANT_SCOPE_ANNOTATION =
      "com.bone.metadata.sdk.domain.annotation.TenantScope";

  /** 全租户入口的命名后缀（E-4.4 / E-13.3）。 */
  private static final String ALL_TENANTS_SUFFIX = "AllTenants";

  /**
   * 全租户入口的<b>事实判据</b>：{@code @TenantScope(ALL)}（SQL 通道）或方法体内调用 {@code
   * Criteria.disableTenantFilter()}（Criteria 通道）。仓储接口级的 {@code @TenantScope(ALL)} 同样生效。
   */
  public static DescribedPredicate<JavaMethod> allTenantEntryPoint() {
    return new DescribedPredicate<>(
        "an all-tenant entry point (@TenantScope(ALL) or Criteria.disableTenantFilter())") {
      @Override
      public boolean test(JavaMethod method) {
        return declaresAllTenantScope(method) || callsDisableTenantFilter(method);
      }
    };
  }

  /** {@link #allTenantEntryPoint()} 的补集（供"schedule 只许调全租户入口"使用）。 */
  private static DescribedPredicate<JavaMethod> notAllTenantEntryPoint() {
    return new DescribedPredicate<>("not an all-tenant entry point") {
      @Override
      public boolean test(JavaMethod method) {
        return !(declaresAllTenantScope(method) || callsDisableTenantFilter(method));
      }
    };
  }

  /**
   * 全租户入口只允许 {@code adapter.schedule} 调用（E-2：平台运维入口）。
   *
   * <p>web / handler / infrastructure 调用同样命中这条规则——定时任务线程没有请求上下文， 把全租户方法暴露给请求侧会直接造成跨租户数据泄漏。
   */
  public static ArchRule allTenantScanMethodsOnlyCalledBySchedule(String domainRepositoryPackage) {
    return allTenantEntryPointsOnlyCalledBy(
        domainRepositoryPackage, AllTenantCallers.ofPackages("..adapter.schedule.."));
  }

  /**
   * 全租户入口只允许<b>已登记</b>的调用方调用（定时运维 / 登录前置等）。
   *
   * <p>为什么是可登记白名单而不是"只有 schedule"：现实中至少有两类合法跨租户入口—— ① 定时运维扫描（{@code adapter.schedule}，租户上下文缺失）；②
   * 登录前置定位（租户在认证前未知，必须按用户名跨租户查账号）。 后者无法归入 ①，硬塞进 schedule 只会逼出"绕开门禁"的写法。白名单在模块的 {@code
   * ArchitectureTest} 显式声明，评审时逐条可见。
   *
   * <p>注意 ArchUnit 的包匹配器<b>不匹配类名</b>（{@code ..service.AuthService} 不会排除该类）， 要精确到类必须用 {@link
   * AllTenantCallers#andClasses(String...)}。
   */
  public static ArchRule allTenantEntryPointsOnlyCalledBy(
      String domainRepositoryPackage, AllTenantCallers callers) {
    return noClasses()
        .that(notClass(callers.asPredicate()))
        .should()
        .callMethodWhere(
            JavaCall.Predicates.target(
                callTargetInPackage(domainRepositoryPackage, allTenantEntryPoint())))
        .allowEmptyShould(true)
        .because(
            "E-2 / ADR-0030: an all-tenant entry bypasses tenant isolation, so only registered "
                + "callers may reach it; an unregistered caller leaks cross-tenant data or "
                + "silently processes the platform tenant only");
  }

  /** 全租户入口的已登记调用方：包模式与类全名取并集。 */
  public static final class AllTenantCallers {

    private final List<String> packagePatterns;
    private final List<String> classNames;

    private AllTenantCallers(List<String> packagePatterns, List<String> classNames) {
      this.packagePatterns = packagePatterns;
      this.classNames = classNames;
    }

    public static AllTenantCallers ofPackages(String... packagePatterns) {
      return new AllTenantCallers(Arrays.asList(packagePatterns), List.of());
    }

    public AllTenantCallers andClasses(String... fullyQualifiedClassNames) {
      return new AllTenantCallers(packagePatterns, Arrays.asList(fullyQualifiedClassNames));
    }

    private DescribedPredicate<JavaClass> asPredicate() {
      DescribedPredicate<JavaClass> byPackage =
          JavaClass.Predicates.resideInAnyPackage(packagePatterns.toArray(new String[0]));
      DescribedPredicate<JavaClass> byClassName =
          new DescribedPredicate<>("a registered all-tenant caller class") {
            @Override
            public boolean test(JavaClass javaClass) {
              return classNames.contains(javaClass.getName());
            }
          };
      return byPackage.or(byClassName);
    }
  }

  private static DescribedPredicate<JavaClass> notClass(DescribedPredicate<JavaClass> predicate) {
    return new DescribedPredicate<>("not " + predicate.getDescription()) {
      @Override
      public boolean test(JavaClass javaClass) {
        return !predicate.test(javaClass);
      }
    };
  }

  /**
   * {@code adapter.schedule} 调用域仓储时，只许调用全租户入口方法（ADR-0030 授权的运维扫描）。
   *
   * <p>方向与上一条相反：域仓储在 ADR-0030 合并读写后自带 {@code save} / {@code update} / {@code delete}，
   * 「拿到接口就等于握有写能力」，所以调用面必须按方法逐个收紧。
   */
  public static ArchRule scheduleOnlyCallsAllTenantScanMethods(String domainRepositoryPackage) {
    return noClasses()
        .that()
        .resideInAPackage("..adapter.schedule..")
        .should()
        .callMethodWhere(
            JavaCall.Predicates.target(
                callTargetInPackage(domainRepositoryPackage, notAllTenantEntryPoint())))
        // 无 adapter.schedule 的模块（没有定时任务）本就无事可查：空匹配是合法状态，不是配置错误。
        .allowEmptyShould(true)
        .because(
            "ADR-0030: adapter.schedule is authorized for all-tenant ops scans only "
                + "(*AllTenants / @TenantScope(ALL)); save/update/delete and tenant-scoped reads "
                + "are outside that authorization");
  }

  /**
   * 全租户入口必须叫 {@code *AllTenants}，且叫 {@code *AllTenants} 的必须是全租户入口（双向绑定）。
   *
   * <p>为什么绑死：命名是给<b>调用点</b>看的（调用方一眼知道这会跨租户），注解是给<b>运行时</b>用的。
   * 只写名字不改行为、或只写注解不留痕迹，都会让"危险的东西从签名里消失"。双向绑定把两者变成同一件事的两种表达。
   */
  public static ArchRule allTenantEntryPointsMustBeNamedAllTenants(String domainRepositoryPackage) {
    return classes()
        .that()
        .resideInAPackage(domainRepositoryPackage)
        .should(
            new ArchCondition<JavaClass>(
                "name all-tenant entry points with the '" + ALL_TENANTS_SUFFIX + "' suffix") {
              @Override
              public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                  if (!method.getOwner().equals(item)) {
                    continue;
                  }
                  boolean allTenant =
                      declaresAllTenantScope(method) || callsDisableTenantFilter(method);
                  boolean namedAllTenants = method.getName().endsWith(ALL_TENANTS_SUFFIX);
                  if (allTenant == namedAllTenants) {
                    continue;
                  }
                  events.add(
                      SimpleConditionEvent.violated(
                          method,
                          String.format(
                              "%s#%s: %s — all-tenant entries (@TenantScope(ALL) or "
                                  + "disableTenantFilter()) must be named *%s, and *%s methods must "
                                  + "actually bypass tenant isolation; callers must see the "
                                  + "cross-tenant scope from the call site",
                              item.getName(),
                              method.getName(),
                              allTenant
                                  ? "bypasses tenant isolation but is not named *AllTenants"
                                  : "is named *AllTenants but does not bypass tenant isolation",
                              ALL_TENANTS_SUFFIX,
                              ALL_TENANTS_SUFFIX)));
                }
              }
            })
        .because(
            "E-4.4 / E-13.3: cross-tenant scope must be visible at the call site and consistent "
                + "with the fact (annotation / disableTenantFilter), not a decoration");
  }

  /** 方法级 {@code @TenantScope(ALL)}，或仓储接口级 {@code @TenantScope(ALL)}。 */
  private static boolean declaresAllTenantScope(JavaMethod method) {
    return annotationsDeclareAllTenantScope(method.getAnnotations())
        || annotationsDeclareAllTenantScope(method.getOwner().getAnnotations());
  }

  private static boolean annotationsDeclareAllTenantScope(
      Collection<? extends JavaAnnotation<?>> annotations) {
    return annotations.stream()
        .filter(a -> TENANT_SCOPE_ANNOTATION.equals(a.getRawType().getName()))
        // 枚举值在类路径可用时是枚举实例、不可用时是 JavaEnumConstant，两者 String 化都含常量名。
        .anyMatch(
            a -> a.get("value").map(String::valueOf).map(v -> v.contains("ALL")).orElse(false));
  }

  /** Criteria 通道的事实判据：方法体里出现 {@code disableTenantFilter()} 调用。 */
  private static boolean callsDisableTenantFilter(JavaMethod method) {
    return method.getCallsFromSelf().stream()
        .anyMatch(call -> "disableTenantFilter".equals(call.getTarget().getName()));
  }

  /** 调用目标必须落在指定仓储包内，并满足给定的方法谓词。 */
  private static DescribedPredicate<AccessTarget.CodeUnitCallTarget> callTargetInPackage(
      String domainRepositoryPackage, DescribedPredicate<JavaMethod> methodPredicate) {
    return new DescribedPredicate<>(
        "a method in " + domainRepositoryPackage + " that is " + methodPredicate.getDescription()) {
      @Override
      public boolean test(AccessTarget.CodeUnitCallTarget target) {
        if (!target.getOwner().getPackageName().equals(domainRepositoryPackage)) {
          return false;
        }
        Optional<JavaMethod> resolved =
            target.resolveMember().filter(JavaMethod.class::isInstance).map(JavaMethod.class::cast);
        return resolved.filter(methodPredicate::test).isPresent();
      }
    };
  }
}
