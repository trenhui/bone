package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.mapping.AliasRelation;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.claim.domain.model.mapping.CodeMappingResolver;
import com.bone.claim.domain.model.mapping.FieldBinding;
import com.bone.claim.domain.model.mapping.InsurerCodeItem;
import com.bone.claim.domain.model.mapping.InsurerStatus;
import com.bone.claim.domain.model.mapping.MappingMiss;
import com.bone.claim.domain.model.mapping.MappingResolution;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.NameNormalizer;
import com.bone.claim.domain.model.mapping.ResolutionStatus;
import com.bone.claim.domain.model.mapping.StdCodeAlias;
import com.bone.claim.domain.model.mapping.StdCodeSplit;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.CodeMappingRepository;
import com.bone.claim.domain.repository.FieldBindingRepository;
import com.bone.claim.domain.repository.InsurerCodeItemRepository;
import com.bone.claim.domain.repository.MappingMissRepository;
import com.bone.claim.domain.repository.RefDiseaseRepository;
import com.bone.claim.domain.repository.RefDrugRepository;
import com.bone.claim.domain.repository.RefFeeCategoryRepository;
import com.bone.claim.domain.repository.RefHospitalRepository;
import com.bone.claim.domain.repository.StdCodeAliasRepository;
import com.bone.claim.domain.repository.StdCodeSplitRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 多保司主数据映射应用服务：解析、发布、未命中队列处置。
 *
 * <p><b>为什么解析失败要落库而不是只打日志</b>：日志会被轮转、没人按保司×域聚合，长尾码值永远修不完。 落库后"哪个保司哪个域最缺映射"可以排序，先修高频才修得动长尾。
 *
 * <p><b>为什么发布是"关旧行 + 插新行"而不是改一行</b>：已结案案件要能回答"当时按哪条映射翻译"。 原地改标准码会让历史案件的解析结果凭空变化，且旧值不可追溯。旧行由 {@code
 * superseded_by_id} 留下链条。
 *
 * <p><b>发布前必须验标准码存在</b>：映射指向一个库里不存在的码，比未命中更糟——它不进队列、不报错， 只让理算拿着一个查不到的编码继续跑。
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class CodeMappingApplicationService {

  private final CodeMappingRepository codeMappingRepository;
  private final MappingMissRepository mappingMissRepository;
  private final StdCodeAliasRepository stdCodeAliasRepository;
  private final StdCodeSplitRepository stdCodeSplitRepository;
  private final ClaimInsurerRepository claimInsurerRepository;
  private final InsurerCodeItemRepository insurerCodeItemRepository;
  private final FieldBindingRepository fieldBindingRepository;
  private final RefHospitalRepository refHospitalRepository;
  private final RefDrugRepository refDrugRepository;
  private final RefDiseaseRepository refDiseaseRepository;
  private final RefFeeCategoryRepository refFeeCategoryRepository;
  private final TenantPort tenantPort;

  // ===================== 解析 =====================

  /**
   * 解析保司码 → 标准码。
   *
   * <p><b>调用约定</b>：收单侧拿到的结果只允许两种用法——{@code RESOLVED} 时把 {@code standardCode} 写进明细；
   * 其余状态一律转人工/待补件。<b>不得</b>把原始码当兜底写下去（那等于把"没配映射"变成"默认码值"）。
   */
  @Transactional
  public ResolveView resolve(ResolveCommand command) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain domain = MasterDataDomain.fromCode(command.domain());
    String rawCode = command.rawCode() == null ? "" : command.rawCode().trim();

    List<CodeMapping> candidates =
        codeMappingRepository.findCandidates(
            tenantId, command.insurerCode(), domain.code(), rawCode);
    List<StdCodeAlias> aliases = stdCodeAliasRepository.findByDomain(tenantId, domain.code());

    MappingResolution resolution =
        CodeMappingResolver.resolve(
            rawCode, command.disambiguationKey(), candidates, aliases, false);

    // 拆分父码是标准码维度的判定，必须在拿到标准码之后再查（父码不是保司码）
    if (resolution.status() == ResolutionStatus.RESOLVED
        && !stdCodeSplitRepository
            .findByParent(tenantId, domain.code(), resolution.standardCode())
            .isEmpty()) {
      resolution = MappingResolution.split();
    }

    boolean missRecorded = false;
    if (!resolution.status().isResolved()) {
      recordMiss(tenantId, command, domain, rawCode, resolution);
      missRecorded = true;
    }
    return new ResolveView(
        resolution.status().name(),
        resolution.standardCode(),
        resolution.mappingId(),
        resolution.reasonCode(),
        missRecorded);
  }

  private void recordMiss(
      long tenantId,
      ResolveCommand command,
      MasterDataDomain domain,
      String rawCode,
      MappingResolution resolution) {
    Instant now = Instant.now();
    MappingMiss existing =
        mappingMissRepository.findByKey(tenantId, command.insurerCode(), domain.code(), rawCode);
    if (existing == null) {
      mappingMissRepository.save(
          MappingMiss.firstSeen(
              nextId(),
              tenantId,
              command.insurerCode(),
              domain,
              rawCode,
              command.rawName(),
              command.caseNo(),
              now));
      return;
    }
    existing.recordOccurrence(now, command.caseNo());
    mappingMissRepository.save(existing);
  }

  // ===================== 发布 =====================

  /**
   * 发布一条映射（ACTIVE）。
   *
   * <p>同键已有 ACTIVE 时：<b>同一标准码视为幂等</b>（重复发布不制造新版本），不同标准码则关闭旧行并插入新行。 并发双写由唯一键 {@code uk_active}
   * 兜底为冲突（fail-closed），不使用行锁。
   */
  @Transactional
  public MappingView publish(PublishCommand command) {
    return MappingView.from(publishCore(command));
  }

  /** 发布的实体形态：供"处置未命中"复用，避免为了拿回主键再解析字符串。 */
  private CodeMapping publishCore(PublishCommand command) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain domain = MasterDataDomain.fromCode(command.domain());
    assertInsurerUsable(tenantId, command.insurerCode());
    assertStandardCodeExists(tenantId, domain, command.standardCode());

    String key = command.disambiguationKey() == null ? "" : command.disambiguationKey().trim();
    CodeMapping current =
        codeMappingRepository.findActive(
            tenantId, command.insurerCode(), domain.code(), command.insurerItemCode(), key);
    if (current != null && current.getStandardCode().equals(command.standardCode().trim())) {
      return current;
    }

    Instant now = Instant.now();
    long newId = nextId();
    if (current != null) {
      current.supersede(newId, now);
      codeMappingRepository.save(current);
    }
    CodeMapping created =
        CodeMapping.of(
            newId,
            tenantId,
            command.insurerCode(),
            domain,
            command.insurerItemCode(),
            key,
            command.standardCode(),
            command.mappingType() == null ? MappingType.EXACT : command.mappingType(),
            command.matchRule(),
            command.matchScore(),
            command.disambiguationJson(),
            command.source() == null ? MappingSource.MANUAL : command.source(),
            command.versionNo(),
            now,
            now);
    if (command.reviewerId() != null) {
      created.markReviewed(command.reviewerId(), now);
    }
    codeMappingRepository.save(created);
    return created;
  }

  /** 停用一条映射：保留行、留理由，历史案件仍可解释。 */
  @Transactional
  public MappingView disable(long mappingId, String reasonCode) {
    CodeMapping mapping = codeMappingRepository.findById(mappingId);
    if (mapping == null) {
      throw new DomainException("映射不存在: " + mappingId);
    }
    mapping.close(Instant.now(), reasonCode);
    codeMappingRepository.save(mapping);
    return MappingView.from(mapping);
  }

  @Transactional(readOnly = true)
  public PageView<MappingView> listMappings(String insurerCode, String domain, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<MappingView> records =
        codeMappingRepository
            .findPage(tenantId, insurerCode, domain, bounds.page(), bounds.size())
            .stream()
            .map(MappingView::from)
            .toList();
    long total = codeMappingRepository.countPage(tenantId, insurerCode, domain);
    return new PageView<>(records, total, bounds.page(), bounds.size());
  }

  // ===================== 未命中队列 =====================

  @Transactional(readOnly = true)
  public PageView<MissView> listMisses(String status, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<MissView> records =
        mappingMissRepository.findPage(tenantId, status, bounds.page(), bounds.size()).stream()
            .map(MissView::from)
            .toList();
    long total = mappingMissRepository.countPage(tenantId, status);
    return new PageView<>(records, total, bounds.page(), bounds.size());
  }

  /** 处置未命中：建立映射并把队列项标为已解决（同一事务，避免"映射建了但队列没关"）。 */
  @Transactional
  public MissView resolveMiss(long missId, PublishCommand command) {
    MappingMiss miss = requireMiss(missId);
    PublishCommand ensured =
        new PublishCommand(
            command.insurerCode() == null ? miss.getInsurerCode() : command.insurerCode(),
            command.domain() == null ? miss.getDomain() : command.domain(),
            command.insurerItemCode() == null ? miss.getRawCode() : command.insurerItemCode(),
            command.disambiguationKey(),
            command.standardCode(),
            command.mappingType(),
            command.matchRule(),
            command.matchScore(),
            command.disambiguationJson(),
            command.source(),
            command.versionNo(),
            command.reviewerId());
    CodeMapping mapping = publishCore(ensured);
    miss.resolve(mapping.getId(), Instant.now());
    mappingMissRepository.save(miss);
    return MissView.from(miss);
  }

  @Transactional
  public MissView ignoreMiss(long missId, String reason) {
    MappingMiss miss = requireMiss(missId);
    miss.ignore(reason, Instant.now());
    mappingMissRepository.save(miss);
    return MissView.from(miss);
  }

  private MappingMiss requireMiss(long missId) {
    MappingMiss miss = mappingMissRepository.findById(missId);
    if (miss == null) {
      throw new DomainException("未命中记录不存在: " + missId);
    }
    return miss;
  }

  // ===================== 承保主体 =====================

  @Transactional
  public InsurerView registerInsurer(InsurerCommand command) {
    long tenantId = tenantPort.currentTenantId();
    if (claimInsurerRepository.findByInsurerCode(tenantId, command.insurerCode()) != null) {
      throw new DomainException("保司编码已存在: " + command.insurerCode());
    }
    Instant now = Instant.now();
    ClaimInsurer insurer =
        ClaimInsurer.of(
            nextId(),
            tenantId,
            command.insurerCode(),
            command.insurerName(),
            command.insurerShortName(),
            command.insurerType(),
            command.creditCode(),
            command.parentCode(),
            now);
    claimInsurerRepository.save(insurer);
    return InsurerView.from(insurer);
  }

  @Transactional(readOnly = true)
  public PageView<InsurerView> listInsurers(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<InsurerView> records =
        claimInsurerRepository.findPage(tenantId, keyword, bounds.page(), bounds.size()).stream()
            .map(InsurerView::from)
            .toList();
    long total = claimInsurerRepository.countPage(tenantId, keyword);
    return new PageView<>(records, total, bounds.page(), bounds.size());
  }

  // ===================== 保司码表 =====================

  @Transactional
  public CodeItemView upsertCodeItem(CodeItemCommand command) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain domain = MasterDataDomain.fromCode(command.domain());
    InsurerCodeItem existing =
        insurerCodeItemRepository.findByCode(
            tenantId, command.insurerCode(), domain.code(), command.itemCode());
    Instant now = Instant.now();
    if (existing != null) {
      existing.applyEdit(command.itemName(), command.extraJson(), now);
      insurerCodeItemRepository.save(existing);
      return CodeItemView.from(existing);
    }
    InsurerCodeItem created =
        InsurerCodeItem.of(
            nextId(),
            tenantId,
            command.insurerCode(),
            domain,
            command.itemCode(),
            command.itemName(),
            command.extraJson(),
            command.sourceBatchNo(),
            now);
    insurerCodeItemRepository.save(created);
    return CodeItemView.from(created);
  }

  @Transactional(readOnly = true)
  public PageView<CodeItemView> listCodeItems(
      String insurerCode, String domain, String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<CodeItemView> records =
        insurerCodeItemRepository
            .findPage(tenantId, insurerCode, domain, keyword, bounds.page(), bounds.size())
            .stream()
            .map(CodeItemView::from)
            .toList();
    long total = insurerCodeItemRepository.countPage(tenantId, insurerCode, domain, keyword);
    return new PageView<>(records, total, bounds.page(), bounds.size());
  }

  // ===================== 标准码别名/拆分 =====================

  @Transactional
  public AliasView addAlias(AliasCommand command) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain domain = MasterDataDomain.fromCode(command.domain());
    Instant now = Instant.now();
    StdCodeAlias alias =
        StdCodeAlias.of(
            nextId(),
            tenantId,
            domain,
            command.aliasCode(),
            command.canonicalCode(),
            command.relation() == null ? AliasRelation.MERGE : command.relation(),
            now);
    stdCodeAliasRepository.save(alias);
    return AliasView.from(alias);
  }

  @Transactional
  public SplitView addSplit(SplitCommand command) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain domain = MasterDataDomain.fromCode(command.domain());
    StdCodeSplit split =
        StdCodeSplit.of(
            nextId(),
            tenantId,
            domain,
            command.parentCode(),
            command.childCode(),
            command.splitRuleJson(),
            Instant.now());
    stdCodeSplitRepository.save(split);
    return SplitView.from(split);
  }

  // ===================== 字段绑定 =====================

  @Transactional
  public BindingView bindField(BindingCommand command) {
    long tenantId = tenantPort.currentTenantId();
    String insurerCode =
        command.insurerCode() == null || command.insurerCode().isBlank()
            ? FieldBinding.BASELINE
            : command.insurerCode().trim();
    Instant now = Instant.now();
    FieldBinding existing =
        fieldBindingRepository.findByFieldAndInsurer(tenantId, command.fieldCode(), insurerCode);
    if (existing != null) {
      existing.rebind(command.sourceType(), command.refCode(), now);
      fieldBindingRepository.save(existing);
      return BindingView.from(existing);
    }
    FieldBinding created =
        FieldBinding.of(
            nextId(),
            tenantId,
            command.fieldCode(),
            insurerCode,
            command.sourceType(),
            command.refCode(),
            now);
    fieldBindingRepository.save(created);
    return BindingView.from(created);
  }

  // ===================== 内部校验 =====================

  private void assertInsurerUsable(long tenantId, String insurerCode) {
    ClaimInsurer insurer = claimInsurerRepository.findByInsurerCode(tenantId, insurerCode);
    if (insurer == null) {
      throw new DomainException("承保主体未注册: " + insurerCode);
    }
    if (!insurer.isActive()) {
      throw new DomainException("承保主体已停用，不能新增映射: " + insurerCode);
    }
  }

  /**
   * 标准码存在性：禁止映射指向幽灵码。
   *
   * <p>{@code CERT_TYPE} 暂由字典承载（无本地目录表），故跳过存在性校验并显式返回—— 这是<b>已知缺口</b>，不是"校验通过"。等证件类型主数据落地后必须补上。
   */
  private void assertStandardCodeExists(long tenantId, MasterDataDomain domain, String code) {
    if (code == null || code.isBlank()) {
      throw new DomainException("标准码不能为空");
    }
    String trimmed = code.trim();
    boolean exists =
        switch (domain) {
          case HOSPITAL -> refHospitalRepository.findByHospitalCode(tenantId, trimmed) != null;
          case DRUG -> refDrugRepository.findByDrugCode(tenantId, trimmed) != null;
          case DISEASE -> refDiseaseRepository.findByDiseaseCode(tenantId, trimmed) != null;
          case FEE_CATEGORY -> refFeeCategoryRepository.findByFeeCode(tenantId, trimmed) != null;
          case CERT_TYPE -> true;
        };
    if (!exists) {
      throw new DomainException("标准码不存在于" + domain.label() + "目录，禁止映射指向幽灵码: " + trimmed);
    }
  }

  private static long nextId() {
    return DistributedIdGenerator.generateLongId();
  }

  // ===================== 命令与视图 =====================

  public record ResolveCommand(
      String insurerCode,
      String domain,
      String rawCode,
      String rawName,
      String disambiguationKey,
      String caseNo) {}

  public record ResolveView(
      String status,
      String standardCode,
      Long mappingId,
      String reasonCode,
      boolean missRecorded) {}

  public record PublishCommand(
      String insurerCode,
      String domain,
      String insurerItemCode,
      String disambiguationKey,
      String standardCode,
      MappingType mappingType,
      com.bone.claim.domain.model.mapping.MatchRule matchRule,
      java.math.BigDecimal matchScore,
      String disambiguationJson,
      MappingSource source,
      String versionNo,
      Long reviewerId) {}

  public record MappingView(
      String id,
      String insurerCode,
      String domain,
      String insurerItemCode,
      String disambiguationKey,
      String standardCode,
      String status,
      boolean active,
      String versionNo) {
    static MappingView from(CodeMapping mapping) {
      return new MappingView(
          String.valueOf(mapping.getId()),
          mapping.getInsurerCode(),
          mapping.getDomain(),
          mapping.getInsurerItemCode(),
          mapping.getDisambiguationKey(),
          mapping.getStandardCode(),
          mapping.getStatus(),
          mapping.isActive(),
          mapping.getVersionNo());
    }
  }

  public record MissView(
      String id,
      String insurerCode,
      String domain,
      String rawCode,
      String rawName,
      int occurCount,
      int reopenCount,
      String status,
      String lastCaseNo,
      String resolvedMappingId) {
    static MissView from(MappingMiss miss) {
      return new MissView(
          String.valueOf(miss.getId()),
          miss.getInsurerCode(),
          miss.getDomain(),
          miss.getRawCode(),
          miss.getRawName(),
          miss.getOccurCount() == null ? 0 : miss.getOccurCount(),
          miss.getReopenCount() == null ? 0 : miss.getReopenCount(),
          miss.getStatus(),
          miss.getLastCaseNo(),
          miss.getResolvedMappingId() == null ? null : String.valueOf(miss.getResolvedMappingId()));
    }
  }

  public record InsurerCommand(
      String insurerCode,
      String insurerName,
      String insurerShortName,
      String insurerType,
      String creditCode,
      String parentCode) {}

  public record InsurerView(
      String id,
      String insurerCode,
      String insurerName,
      String insurerShortName,
      String insurerType,
      String creditCode,
      String parentCode,
      String status) {
    static InsurerView from(ClaimInsurer insurer) {
      return new InsurerView(
          String.valueOf(insurer.getId()),
          insurer.getInsurerCode(),
          insurer.getInsurerName(),
          insurer.getInsurerShortName(),
          insurer.getInsurerType(),
          insurer.getCreditCode(),
          insurer.getParentCode(),
          insurer.getStatus());
    }
  }

  public record CodeItemCommand(
      String insurerCode,
      String domain,
      String itemCode,
      String itemName,
      String extraJson,
      String sourceBatchNo) {}

  public record CodeItemView(
      String id,
      String insurerCode,
      String domain,
      String itemCode,
      String itemName,
      String nameNormalized,
      String status) {
    static CodeItemView from(InsurerCodeItem item) {
      return new CodeItemView(
          String.valueOf(item.getId()),
          item.getInsurerCode(),
          item.getDomain(),
          item.getItemCode(),
          item.getItemName(),
          item.getNameNormalized(),
          item.getStatus());
    }
  }

  public record AliasCommand(
      String domain, String aliasCode, String canonicalCode, AliasRelation relation) {}

  public record AliasView(String id, String domain, String aliasCode, String canonicalCode) {
    static AliasView from(StdCodeAlias alias) {
      return new AliasView(
          String.valueOf(alias.getId()),
          alias.getDomain(),
          alias.getAliasCode(),
          alias.getCanonicalCode());
    }
  }

  public record SplitCommand(
      String domain, String parentCode, String childCode, String splitRuleJson) {}

  public record SplitView(String id, String domain, String parentCode, String childCode) {
    static SplitView from(StdCodeSplit split) {
      return new SplitView(
          String.valueOf(split.getId()),
          split.getDomain(),
          split.getParentCode(),
          split.getChildCode());
    }
  }

  public record BindingCommand(
      String fieldCode, String insurerCode, String sourceType, String refCode) {}

  public record BindingView(
      String id, String fieldCode, String insurerCode, String sourceType, String refCode) {
    static BindingView from(FieldBinding binding) {
      return new BindingView(
          String.valueOf(binding.getId()),
          binding.getFieldCode(),
          binding.getInsurerCode(),
          binding.getSourceType(),
          binding.getRefCode());
    }
  }

  public record PageView<V>(List<V> records, long total, int page, int size) {}

  /** 归一化入口暴露给导入侧复用，避免两处实现漂移。 */
  public static String normalizeName(String raw) {
    return NameNormalizer.normalize(raw);
  }

  /** 承保主体状态枚举对外暴露（控制器用它做入参转换）。 */
  public static InsurerStatus insurerStatusOf(String code) {
    return InsurerStatus.fromCode(code);
  }
}
