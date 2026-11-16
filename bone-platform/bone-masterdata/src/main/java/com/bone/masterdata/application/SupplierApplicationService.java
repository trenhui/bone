package com.bone.masterdata.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.masterdata.application.command.RegisterSupplierCommand;
import com.bone.masterdata.application.command.TransitionSupplierCommand;
import com.bone.masterdata.application.command.TransitionVendorCommand;
import com.bone.masterdata.application.query.dto.SupplierRegistrationResultDTO;
import com.bone.masterdata.application.query.dto.SupplierStateDTO;
import com.bone.masterdata.application.query.dto.SupplierTransitionResultDTO;
import com.bone.masterdata.application.query.qry.SupplierPageQuery;
import com.bone.masterdata.domain.gateway.CurrentUserPort;
import com.bone.masterdata.domain.gateway.EntityKeyMappingPort;
import com.bone.masterdata.domain.gateway.LegalEntityRepositoryPort;
import com.bone.masterdata.domain.gateway.PaymentBlockReleaseReviewPort;
import com.bone.masterdata.domain.gateway.SupplierExitFactsPort;
import com.bone.masterdata.domain.gateway.SupplierExitGuard;
import com.bone.masterdata.domain.gateway.SupplierOnboardingFactsPort;
import com.bone.masterdata.domain.gateway.SupplierOnboardingGuard;
import com.bone.masterdata.domain.gateway.SupplierRepositoryPort;
import com.bone.masterdata.domain.gateway.VendorActivationGuard;
import com.bone.masterdata.domain.gateway.VendorGateFactsPort;
import com.bone.masterdata.domain.gateway.VendorRepositoryPort;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule;
import com.bone.masterdata.domain.model.goldenrecord.FieldSurvivorshipRule.Strategy;
import com.bone.masterdata.domain.model.goldenrecord.GoldenRecordMerger;
import com.bone.masterdata.domain.model.goldenrecord.MergeCommand;
import com.bone.masterdata.domain.model.goldenrecord.MergeResult;
import com.bone.masterdata.domain.model.goldenrecord.SourceRecord;
import com.bone.masterdata.domain.model.goldenrecord.SurvivorshipPolicy;
import com.bone.masterdata.domain.model.identity.EntityKeyMapping;
import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.LegalEntity;
import com.bone.masterdata.domain.model.supplier.ResolvedSupplierIdentity;
import com.bone.masterdata.domain.model.supplier.Supplier;
import com.bone.masterdata.domain.model.supplier.SupplierEvent;
import com.bone.masterdata.domain.model.supplier.SupplierExitContext;
import com.bone.masterdata.domain.model.supplier.SupplierExitFacts;
import com.bone.masterdata.domain.model.supplier.SupplierIdentityResolver;
import com.bone.masterdata.domain.model.supplier.SupplierOnboardingContext;
import com.bone.masterdata.domain.model.supplier.SupplierStateChange;
import com.bone.masterdata.domain.model.supplier.TransitionCommandSource;
import com.bone.masterdata.domain.model.supplier.Vendor;
import com.bone.masterdata.domain.model.supplier.VendorEvent;
import com.bone.masterdata.domain.model.supplier.VendorState;
import com.bone.masterdata.domain.model.supplier.VendorStateChange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购供应商主数据的应用用例集合（最小可验证切片的入站入口）。
 *
 * <p><b>五个用例</b>：合并解析登记 / Supplier 合作资格迁移 / Vendor 交易付款资格迁移 / 当前状态查询 / 列表分页查询。
 *
 * <p><b>不发布领域事件的理由（E-5.4 三类豁免之「内部状态迁移」）</b>：本切片的迁移只改变聚合自身状态，跨域通知依赖生产 Outbox （{@code t_proc_*} 表属
 * R-2），在本切片伪造一条进程内事件只会让下游误以为已接通分发链路。因此类上声明 {@link NoDomainEvent}，待接生产 adapter 时改由事务性 Outbox
 * 承载，聚合语义零变化。
 *
 * <p><b>租户隔离</b>：租户一律取登录态（{@link CurrentUserPort}），命令携带的 tenantId 只用于一致性比对，不一致即 fail-closed。
 *
 * <p><b>能力开关</b>：生产持久化端口未就绪，故 {@code bone.procurement.supplier-mdm.enabled} 默认关闭；关闭时本 bean 与 HTTP
 * 端点都不装配，不暴露「能调用但落不了库」的假能力。
 *
 * <p><b>零副作用口径</b>：合并裁决非 APPLIED（转人工 / 拒绝）时，不登记 xref、不落供应商、不建 Vendor 角色。顺序与 Golden Record 编排层一致 ——
 * 必须先在裁决成立后登记映射再落库，否则「映射已建但主体未成立」会让下次同本地键被错绑。
 *
 * <p><b>守卫事实一律来自端口，不接受调用方自报</b>（2026-11-16 独立审查 C-01 修复）：退出所需的上游三域事实、准入所需的变更请求批准事实、 Vendor
 * 闸门五项与停用两项、付款冻结解除的复核留痕，全部通过 domain 端口获取；命令只剩「对哪个主体做什么事件」。此前这些事实由 HTTP 请求体携带，等于让被守卫方自己声明合规，spec
 * 的「退出前置校验」「未经闸门不得开通」都会退化成恒真断言。
 *
 * <p><b>本域自己算得出的事实不外包</b>：{@code openVendorCount}（仍有未关闭的 Vendor 角色）与 {@code activeVendorCount} 直接由
 * Vendor 仓库点算，不经任何外部端口 —— 它们本来就是本域事实，走端口只会给伪造留入口（C-02 修复）。
 *
 * <p><b>版本语义</b>：{@code version} 是<b>供应商视图版本</b>（每次成功登记 / 迁移递增），不是平台 Golden Record 的版本。
 * 本切片不写平台快照（{@code before} 传 null），因此迁移历史只记录状态迁移，不记录字段胜出过程 —— 这是刻意的边界， 接平台 Golden Record
 * 持久化后应改为携带前序快照（独立审查 I-01，已作为可接受偏差记录在验证报告）。
 */
@Service
@NoDomainEvent
@ConditionalOnProperty(
    prefix = "bone.procurement.supplier-mdm",
    name = "enabled",
    havingValue = "true")
public class SupplierApplicationService {

  static final String ROLE_SUPPLIER = "SUPPLIER";
  static final String ROLE_VENDOR = "VENDOR";
  static final String OUTCOME_MANUAL_REVIEW = "MANUAL_REVIEW_REQUIRED";
  static final int DEFAULT_PAGE_SIZE = 10;
  static final int MAX_PAGE_SIZE = 200;

  /** 身份主键字段码（与 Golden Record 内核的 fieldCode 一致）。 */
  static final String FIELD_UNIFIED_SOCIAL_CREDIT_CODE = "unified_social_credit_code";

  static final String FIELD_TAX_ID = "tax_id";

  /** 治理端只回放最近 N 条迁移摘要：历史无限回传会把响应体变成审计线索的旁路。 */
  static final int MAX_HISTORY_IN_RESPONSE = 20;

  private final SupplierRepositoryPort supplierPort;
  private final VendorRepositoryPort vendorPort;
  private final LegalEntityRepositoryPort legalEntityPort;
  private final EntityKeyMappingPort keyMappingPort;
  private final CurrentUserPort currentUserPort;
  private final SupplierExitFactsPort supplierExitFactsPort;
  private final SupplierExitGuard supplierExitGuard;
  private final SupplierOnboardingFactsPort supplierOnboardingFactsPort;
  private final SupplierOnboardingGuard supplierOnboardingGuard;
  private final VendorGateFactsPort vendorGateFactsPort;
  private final VendorActivationGuard vendorActivationGuard;
  private final PaymentBlockReleaseReviewPort paymentBlockReleaseReviewPort;
  private final GoldenRecordMerger merger = new GoldenRecordMerger();
  private final SupplierIdentityResolver identityResolver = new SupplierIdentityResolver();

  public SupplierApplicationService(
      SupplierRepositoryPort supplierPort,
      VendorRepositoryPort vendorPort,
      LegalEntityRepositoryPort legalEntityPort,
      EntityKeyMappingPort keyMappingPort,
      CurrentUserPort currentUserPort,
      SupplierExitFactsPort supplierExitFactsPort,
      SupplierExitGuard supplierExitGuard,
      SupplierOnboardingFactsPort supplierOnboardingFactsPort,
      SupplierOnboardingGuard supplierOnboardingGuard,
      VendorGateFactsPort vendorGateFactsPort,
      VendorActivationGuard vendorActivationGuard,
      PaymentBlockReleaseReviewPort paymentBlockReleaseReviewPort) {
    this.supplierPort = supplierPort;
    this.vendorPort = vendorPort;
    this.legalEntityPort = legalEntityPort;
    this.keyMappingPort = keyMappingPort;
    this.currentUserPort = currentUserPort;
    this.supplierExitFactsPort = supplierExitFactsPort;
    this.supplierExitGuard = supplierExitGuard;
    this.supplierOnboardingFactsPort = supplierOnboardingFactsPort;
    this.supplierOnboardingGuard = supplierOnboardingGuard;
    this.vendorGateFactsPort = vendorGateFactsPort;
    this.vendorActivationGuard = vendorActivationGuard;
    this.paymentBlockReleaseReviewPort = paymentBlockReleaseReviewPort;
  }

  /** 用例一：解析身份 → 复用 Golden Record 内核合并 → 裁决成立才落供应商、建 Vendor 角色、登记 xref。 */
  @Transactional
  public SupplierRegistrationResultDTO register(RegisterSupplierCommand cmd) {
    if (cmd == null || cmd.getTenantId() == null || isBlank(cmd.getMergeId())) {
      throw new DomainException("供应商登记命令缺少 tenantId/mergeId");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    Instant at = cmd.getMergedAt() == null ? Instant.now() : cmd.getMergedAt();

    ResolvedSupplierIdentity identity = resolveIdentity(tenantId, cmd);
    String partyId = identity.partyId();

    Supplier existing = supplierPort.findCurrent(tenantId, partyId).orElse(null);
    long nextVersion = existing == null ? 1L : existing.version() + 1L;

    MergeResult result = merger.merge(toMergeCommand(cmd, tenantId, partyId, nextVersion, at));
    if (!result.isApplied()) {
      // 硬约束：转人工/拒绝时零落库、零登记，避免「部分应用」的供应商视图污染消费侧。
      return SupplierRegistrationResultDTO.builder()
          .outcome(result.getOutcome().name())
          .partyId(partyId)
          .supplierState(existing == null ? null : existing.state().code())
          .supplierVersion(existing == null ? 0L : existing.version())
          .fieldCount(0)
          .reason(result.getReason())
          .build();
    }

    // 身份主键的「不丢、不静默改」（独立审查 I-2）：
    // 1) 本次合并可能只带部分字段，若直接覆盖会把已落库的另一项（如税号）清空 → 缺省值须沿用已落库值；
    // 2) 已落库值非空、本次给出不同的非空值 → 属于身份主键变更，必须人工复核，不能静默换号（换号会让旧号在无人知晓的情况下被释放）。
    LegalEntity existingIdentity = legalEntityPort.findByPartyId(tenantId, partyId).orElse(null);
    String mergedUscc = result.getGolden().getValue(FIELD_UNIFIED_SOCIAL_CREDIT_CODE);
    String mergedTaxId = result.getGolden().getValue(FIELD_TAX_ID);
    String changedKey = changedIdentityKey(existingIdentity, mergedUscc, mergedTaxId);
    if (changedKey != null) {
      return SupplierRegistrationResultDTO.builder()
          .outcome(OUTCOME_MANUAL_REVIEW)
          .partyId(partyId)
          .supplierState(existing == null ? null : existing.state().code())
          .supplierVersion(existing == null ? 0L : existing.version())
          .fieldCount(0)
          .reason("身份主键 " + changedKey + " 与已落库值不一致，须人工复核后变更")
          .build();
    }
    LegalEntity legalEntity =
        LegalEntity.of(
            tenantId,
            partyId,
            mergedUscc != null
                ? mergedUscc
                : (existingIdentity == null ? null : existingIdentity.unifiedSocialCreditCode()),
            mergedTaxId != null
                ? mergedTaxId
                : (existingIdentity == null ? null : existingIdentity.taxId()));

    // 身份主键唯一性（spec:27）：同一租户内同一统一社会信用代码/税号不得被第二个 partyId 占用。
    // 放在落库之前、且失败时零副作用：错并主体一旦落库，后续所有下游视图都会被污染。
    LegalEntity identityOwner = findIdentityOwner(tenantId, legalEntity);
    if (identityOwner != null) {
      return SupplierRegistrationResultDTO.builder()
          .outcome(OUTCOME_MANUAL_REVIEW)
          .partyId(partyId)
          .supplierState(existing == null ? null : existing.state().code())
          .supplierVersion(existing == null ? 0L : existing.version())
          .fieldCount(0)
          .reason("身份主键已被 partyId=" + identityOwner.partyId() + " 占用，转人工复核")
          .build();
    }

    // 顺序：裁决成立 → 登记 xref → 落法人身份 → 落供应商 → 建/续 Vendor 角色。
    if (identity.pendingRegistration() != null) {
      keyMappingPort.register(identity.pendingRegistration());
    }
    // 期望版本取「本主体既有登记」：重复登记同一 partyId 时必须带上一版版本，否则乐观锁会把正常续登判成并发冲突。
    LegalEntity persistedIdentity =
        existingIdentity == null
            ? legalEntity
            : LegalEntity.rehydrate(
                tenantId,
                partyId,
                legalEntity.unifiedSocialCreditCode(),
                legalEntity.taxId(),
                existingIdentity.version() + 1L);
    legalEntityPort.save(
        persistedIdentity, existingIdentity == null ? 0L : existingIdentity.version());

    Supplier supplier =
        existing == null
            ? Supplier.prospect(tenantId, partyId)
            : Supplier.rehydrate(
                tenantId, partyId, existing.state(), nextVersion, existing.history());
    supplierPort.save(supplier, existing == null ? 0L : existing.version());

    Vendor currentVendor = vendorPort.findCurrent(tenantId, partyId).orElse(null);
    Vendor vendor =
        currentVendor == null
            ? Vendor.pending(tenantId, partyId)
            : Vendor.rehydrate(
                tenantId,
                partyId,
                currentVendor.state(),
                currentVendor.version() + 1L,
                currentVendor.history());
    vendorPort.save(vendor, currentVendor == null ? 0L : currentVendor.version());

    return SupplierRegistrationResultDTO.builder()
        .outcome(result.getOutcome().name())
        .partyId(partyId)
        .supplierState(supplier.state().code())
        .vendorState(vendor.state().code())
        .supplierVersion(supplier.version())
        .vendorVersion(vendor.version())
        .fieldCount(result.getGolden().getValues().size())
        .build();
  }

  /** 用例二：推进 Supplier 合作资格状态。 */
  @Transactional
  public SupplierTransitionResultDTO transitionSupplier(TransitionSupplierCommand cmd) {
    if (cmd == null
        || cmd.getTenantId() == null
        || isBlank(cmd.getPartyId())
        || isBlank(cmd.getEvent())) {
      throw new DomainException("供应商状态迁移命令缺少 tenantId/partyId/event");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    Supplier current =
        supplierPort
            .findCurrent(tenantId, cmd.getPartyId().trim())
            .orElseThrow(() -> new DomainException("合作资格不存在: " + cmd.getPartyId()));

    SupplierEvent event = supplierEventOf(cmd.getEvent());
    GuardVerdict verdict = resolveSupplierVerdict(tenantId, current.partyId(), event);

    Supplier next =
        current.transition(event, verdict, operatorOf(), occurredAt(cmd.getOccurredAt()));
    supplierPort.save(next, current.version());
    return SupplierTransitionResultDTO.builder()
        .partyId(next.partyId())
        .role(ROLE_SUPPLIER)
        .fromState(current.state().code())
        .toState(next.state().code())
        .version(next.version())
        .basis(verdict == null ? null : verdict.reasonCode())
        .build();
  }

  /** 用例三：推进 Vendor 交易付款资格状态（付款冻结解除只接受外部财务事件，见聚合层校验）。 */
  @Transactional
  public SupplierTransitionResultDTO transitionVendor(TransitionVendorCommand cmd) {
    if (cmd == null
        || cmd.getTenantId() == null
        || isBlank(cmd.getPartyId())
        || isBlank(cmd.getEvent())
        || isBlank(cmd.getSource())) {
      throw new DomainException("Vendor 状态迁移命令缺少 tenantId/partyId/event/source");
    }
    long tenantId = requireSameTenant(cmd.getTenantId());
    Vendor current =
        vendorPort
            .findCurrent(tenantId, cmd.getPartyId().trim())
            .orElseThrow(() -> new DomainException("交易付款资格不存在: " + cmd.getPartyId()));

    VendorEvent event = vendorEventOf(cmd.getEvent());
    TransitionCommandSource source = commandSourceOf(cmd.getSource());
    GuardVerdict verdict = resolveVendorVerdict(tenantId, current.partyId(), event);

    // 留痕必须绑定「本次冻结」：一次复核不能永久放行此后所有冻结解除（独立审查 I-4）。
    // 找不到本次冻结的发起时间就无法判定留痕是否针对它，一律 fail-closed，不做「无法判断就放行」。
    boolean releaseReviewed = false;
    if (event == VendorEvent.PAYMENT_BLOCK_RELEASED) {
      Instant blockedAt = lastBlockRaisedAt(current.history());
      if (blockedAt == null) {
        throw new DomainException("无法定位本次付款冻结的发起时间，禁止解除（fail-closed）");
      }
      releaseReviewed =
          paymentBlockReleaseReviewPort.releaseReviewTraced(tenantId, current.partyId(), blockedAt);
    }

    Vendor next =
        current.transition(
            event, source, verdict, operatorOf(), occurredAt(cmd.getOccurredAt()), releaseReviewed);

    vendorPort.save(next, current.version());
    return SupplierTransitionResultDTO.builder()
        .partyId(next.partyId())
        .role(ROLE_VENDOR)
        .fromState(current.state().code())
        .toState(next.state().code())
        .version(next.version())
        .basis(verdict == null ? null : verdict.reasonCode())
        .build();
  }

  /** 用例四：只读当前双角色状态（租户取登录态，不接受自报；不返回任何字段原值）。 */
  public Optional<SupplierStateDTO> currentState(String partyId) {
    Long tenantId = currentUserPort.currentTenantId();
    if (tenantId == null) {
      throw new DomainException("缺少登录租户上下文，禁止读取供应商状态");
    }
    if (isBlank(partyId)) {
      throw new DomainException("缺少 partyId，禁止读取供应商状态");
    }
    Optional<Supplier> supplier = supplierPort.findCurrent(tenantId, partyId.trim());
    Optional<Vendor> vendor = vendorPort.findCurrent(tenantId, partyId.trim());
    if (supplier.isEmpty() && vendor.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toStateDTO(partyId.trim(), supplier.orElse(null), vendor));
  }

  /**
   * 用例五：按租户分页列举合作资格（治理端列表视图）。
   *
   * <p><b>分页边界在应用层收口</b>：{@code page} 从 1 起、{@code size} 上限 200，越界值一律夹紧而不是抛错 —— 列表接口被脚本化调用时更常见的是传 0
   * 或超大 size，抛错会把「查询参数略偏」放大成 5xx，夹紧则保证语义可预期。
   *
   * <p>返回 {@link PageResult} 而非裸领域对象：列表元素同样是「状态 + 版本 + 迁移摘要」，不含字段原值。
   */
  public PageResult<SupplierStateDTO> pageSuppliers(SupplierPageQuery qry) {
    Long tenantId = currentUserPort.currentTenantId();
    if (tenantId == null) {
      throw new DomainException("缺少登录租户上下文，禁止读取供应商列表");
    }
    int size =
        qry == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(qry.getSize(), 1), MAX_PAGE_SIZE);
    // page 用 long 夹紧：page=Integer.MAX_VALUE 时 (page-1)*size 会溢出成负数，skip 负数会静默返回第 1 页
    // 而不是「超出范围的空页」—— 对账脚本会因此把重复数据当成新数据。
    long requestedPage = qry == null ? 1L : Math.max(qry.getPage(), 1L);
    long maxPage = Math.max(1L, ((long) Integer.MAX_VALUE / size) + 1L);
    int page = (int) Math.min(requestedPage, maxPage);
    int offset = (int) Math.min((long) (page - 1) * size, Integer.MAX_VALUE);

    List<SupplierStateDTO> list =
        supplierPort.listByTenant(tenantId, offset, size).stream()
            .map(
                supplier ->
                    toStateDTO(
                        supplier.partyId(),
                        supplier,
                        vendorPort.findCurrent(tenantId, supplier.partyId())))
            .toList();
    long total = supplierPort.countByTenant(tenantId);
    return PageResult.of(list, total, page, size);
  }

  /** 组装治理端只读状态视图：只含状态码、版本与迁移摘要，<b>不读也不回传任何字段原值</b>。 */
  private static SupplierStateDTO toStateDTO(
      String partyId, Supplier supplier, Optional<Vendor> vendor) {
    return SupplierStateDTO.builder()
        .partyId(partyId)
        .supplierState(supplier == null ? null : supplier.state().code())
        .supplierVersion(supplier == null ? 0L : supplier.version())
        .vendorState(vendor.map(v -> v.state().code()).orElse(null))
        .vendorVersion(vendor.map(Vendor::version).orElse(0L))
        .supplierHistory(supplier == null ? List.of() : tail(summarize(supplier.history())))
        .vendorHistory(vendor.map(v -> tail(summarizeVendor(v.history()))).orElse(List.of()))
        .build();
  }

  /**
   * 解析 Supplier 侧守卫结论：只有准入与退出两条迁移需要守卫，且事实全部来自端口或本域点算。
   *
   * <p>返回 null 表示该事件在迁移表里不需要守卫结论；迁移表本身会拒绝「需要守卫却没给结论」的调用， 因此这里不需要重复判断。
   */
  private GuardVerdict resolveSupplierVerdict(long tenantId, String partyId, SupplierEvent event) {
    if (event == SupplierEvent.EXIT_APPROVED) {
      SupplierExitFacts facts = supplierExitFactsPort.facts(tenantId, partyId);
      return supplierExitGuard.canExit(
          new SupplierExitContext(
              tenantId,
              partyId,
              facts.openPurchaseOrders(),
              facts.openContracts(),
              facts.payableBalance(),
              openVendorCount(tenantId, partyId)));
    }
    if (event == SupplierEvent.ONBOARDING_APPROVED) {
      return supplierOnboardingGuard.canActivate(
          new SupplierOnboardingContext(
              tenantId,
              partyId,
              supplierOnboardingFactsPort.changeRequestFullyApproved(tenantId, partyId),
              activeVendorCount(tenantId, partyId)));
    }
    return null;
  }

  /** 解析 Vendor 侧守卫结论：开通与停用两条迁移的闸门事实全部来自财务域端口。 */
  private GuardVerdict resolveVendorVerdict(long tenantId, String partyId, VendorEvent event) {
    if (event == VendorEvent.ACTIVATION_PASSED) {
      return vendorActivationGuard.canActivate(
          vendorGateFactsPort.activationContext(tenantId, partyId));
    }
    if (event == VendorEvent.DEACTIVATE) {
      return vendorActivationGuard.canDeactivate(
          vendorGateFactsPort.deactivationContext(tenantId, partyId));
    }
    return null;
  }

  /** 本域事实：仍有未关闭（非 INACTIVE_VENDOR）的 Vendor 角色则计 1。 */
  private int openVendorCount(long tenantId, String partyId) {
    return vendorPort
        .findCurrent(tenantId, partyId)
        .filter(vendor -> vendor.state() != VendorState.INACTIVE_VENDOR)
        .map(vendor -> 1)
        .orElse(0);
  }

  /** 本域事实：处于 ACTIVE_VENDOR 的角色数量。 */
  private int activeVendorCount(long tenantId, String partyId) {
    return vendorPort
        .findCurrent(tenantId, partyId)
        .filter(vendor -> vendor.state() == VendorState.ACTIVE_VENDOR)
        .map(vendor -> 1)
        .orElse(0);
  }

  /**
   * 身份主键占用者：统一社会信用代码与税号<b>分别</b>查，任一命中「他人」即算冲突。
   *
   * <p><b>为什么不能命中第一个就返回</b>：两个身份主键可能分别属于不同主体。只查 USCC 就在命中时返回，会让「自己是自己」把 「税号已属他人」的冲突盖掉 ——
   * 重复登记同一主体（自命中）时本次登记会一路通过，把别人的税号绑到自己名下。 因此这里逐项用 {@link LegalEntity#identityConflictsWith}
   * 判定真实冲突，并要求占用者不是本主体。
   */
  private LegalEntity findIdentityOwner(long tenantId, LegalEntity candidate) {
    LegalEntity byUscc =
        legalEntityPort
            .findByUnifiedSocialCreditCode(tenantId, candidate.unifiedSocialCreditCode())
            .orElse(null);
    LegalEntity byTaxId = legalEntityPort.findByTaxId(tenantId, candidate.taxId()).orElse(null);
    if (ownsAnotherParty(byUscc, candidate)) {
      return byUscc;
    }
    if (ownsAnotherParty(byTaxId, candidate)) {
      return byTaxId;
    }
    return null;
  }

  /** 该登记项与候选身份真实冲突、且属于另一个 partyId（自己与自己的冲突不算，走版本递增）。 */
  private static boolean ownsAnotherParty(LegalEntity owner, LegalEntity candidate) {
    return owner != null
        && owner.identityConflictsWith(candidate)
        && !owner.partyId().equals(candidate.partyId());
  }

  /**
   * 本次合并给出的身份主键是否与已落库值冲突（返回字段码；无冲突返回 null）。
   *
   * <p>只比较「两边都非空」的情形：本次合并未带该字段是「没意见」，应沿用已落库值而不是判冲突或清空。
   */
  private static String changedIdentityKey(
      LegalEntity existing, String mergedUscc, String mergedTaxId) {
    if (existing == null) {
      return null;
    }
    if (mergedUscc != null
        && existing.unifiedSocialCreditCode() != null
        && !mergedUscc.equalsIgnoreCase(existing.unifiedSocialCreditCode())) {
      return FIELD_UNIFIED_SOCIAL_CREDIT_CODE;
    }
    if (mergedTaxId != null
        && existing.taxId() != null
        && !mergedTaxId.equalsIgnoreCase(existing.taxId())) {
      return FIELD_TAX_ID;
    }
    return null;
  }

  /** 最近一次付款冻结的发起时间；历史里没有则 null（调用方 fail-closed）。 */
  private static Instant lastBlockRaisedAt(List<VendorStateChange> history) {
    for (int i = history.size() - 1; i >= 0; i--) {
      VendorStateChange change = history.get(i);
      if (change.event() == VendorEvent.PAYMENT_BLOCK_RAISED) {
        return change.at();
      }
    }
    return null;
  }

  /** 操作人一律取登录态，不接受命令自报（审计字段不能由被审计方填写）。 */
  private String operatorOf() {
    Long userId = currentUserPort.requireUserId();
    return "user:" + userId;
  }

  /** 只回放最近 N 条，避免响应体成为审计线索的旁路。 */
  private static List<String> tail(List<String> history) {
    if (history.size() <= MAX_HISTORY_IN_RESPONSE) {
      return history;
    }
    return List.copyOf(history.subList(history.size() - MAX_HISTORY_IN_RESPONSE, history.size()));
  }

  /** 命令租户与登录态一致才放行；不允许调用方自报租户读写他人数据。 */
  private long requireSameTenant(Long requested) {
    Long caller = currentUserPort.currentTenantId();
    if (caller == null) {
      throw new DomainException("缺少登录租户上下文，禁止供应商主数据治理操作");
    }
    if (requested == null || requested.longValue() != caller.longValue()) {
      throw new DomainException("命令 tenantId 与登录租户不一致，禁止跨租户治理");
    }
    return caller;
  }

  private ResolvedSupplierIdentity resolveIdentity(long tenantId, RegisterSupplierCommand cmd) {
    boolean hasLocal = !isBlank(cmd.getSourceSystem()) && !isBlank(cmd.getLocalId());
    List<EntityKeyMapping> existing =
        hasLocal
            ? keyMappingPort.findByLocalKey(tenantId, cmd.getSourceSystem(), cmd.getLocalId())
            : List.of();
    return identityResolver.resolve(
        tenantId, cmd.getSourceSystem(), cmd.getLocalId(), cmd.getPartyId(), existing);
  }

  private MergeCommand toMergeCommand(
      RegisterSupplierCommand cmd, long tenantId, String partyId, long nextVersion, Instant at) {
    List<SourceRecord> sources = new ArrayList<>();
    if (cmd.getSources() != null) {
      for (RegisterSupplierCommand.SourceRecordSpec spec : cmd.getSources()) {
        // JSON 数组里出现 null 元素时不能让它变成 NPE（500）：按「候选记录不完整」整笔拒绝。
        if (spec == null) {
          throw new DomainException("候选源记录列表含空元素，整笔拒绝");
        }
        // spec:33-35：候选源记录自带租户且与命令租户不一致时，整笔拒绝而不是静默丢弃该记录。
        if (spec.getTenantId() != null && spec.getTenantId().longValue() != tenantId) {
          throw new DomainException(
              "候选源记录 "
                  + spec.getRecordId()
                  + " 的 tenantId="
                  + spec.getTenantId()
                  + " 与命令租户不一致，整笔拒绝（不静默过滤）");
        }
        sources.add(
            SourceRecord.of(
                spec.getRecordId(),
                spec.getSourceSystem(),
                tenantId,
                spec.getTrustRank(),
                spec.getObservedAt() == null ? at : spec.getObservedAt(),
                spec.getValues() == null ? Map.of() : spec.getValues()));
      }
    }
    Map<String, FieldSurvivorshipRule> rules = new LinkedHashMap<>();
    if (cmd.getStrategies() != null) {
      for (Map.Entry<String, String> entry : cmd.getStrategies().entrySet()) {
        final Strategy strategy;
        try {
          strategy = Strategy.valueOf(entry.getValue());
        } catch (RuntimeException ex) {
          throw new DomainException("非法胜出策略: " + entry.getValue());
        }
        rules.put(entry.getKey(), FieldSurvivorshipRule.of(entry.getKey(), strategy));
      }
    }
    return MergeCommand.of(
        cmd.getMergeId(),
        tenantId,
        partyId,
        null,
        nextVersion,
        sources,
        SurvivorshipPolicy.of(rules),
        cmd.getStewardOverrides() == null ? Map.of() : cmd.getStewardOverrides(),
        cmd.getStewardAccountId(),
        at);
  }

  private static SupplierEvent supplierEventOf(String code) {
    for (SupplierEvent event : SupplierEvent.values()) {
      if (event.code().equals(code)) {
        return event;
      }
    }
    throw new DomainException("未知的供应商生命周期事件码: " + code);
  }

  private static VendorEvent vendorEventOf(String code) {
    for (VendorEvent event : VendorEvent.values()) {
      if (event.code().equals(code)) {
        return event;
      }
    }
    throw new DomainException("未知的 Vendor 生命周期事件码: " + code);
  }

  private static TransitionCommandSource commandSourceOf(String source) {
    try {
      return TransitionCommandSource.valueOf(source);
    } catch (RuntimeException ex) {
      throw new DomainException("非法的迁移命令来源: " + source);
    }
  }

  private static List<String> summarize(List<SupplierStateChange> history) {
    List<String> summary = new ArrayList<>();
    for (SupplierStateChange change : history) {
      summary.add(
          change.from().code() + "->" + change.to().code() + " (" + change.event().code() + ")");
    }
    return summary;
  }

  private static List<String> summarizeVendor(List<VendorStateChange> history) {
    List<String> summary = new ArrayList<>();
    for (VendorStateChange change : history) {
      summary.add(
          change.from().code() + "->" + change.to().code() + " (" + change.event().code() + ")");
    }
    return summary;
  }

  private static Instant occurredAt(Instant at) {
    return at == null ? Instant.now() : at;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
