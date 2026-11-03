package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.mapping.AliasRelation;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.claim.domain.model.mapping.CodeMappingResolver;
import com.bone.claim.domain.model.mapping.FieldBinding;
import com.bone.claim.domain.model.mapping.InsurerCodeItem;
import com.bone.claim.domain.model.mapping.InsurerStatus;
import com.bone.claim.domain.model.mapping.MappingMiss;
import com.bone.claim.domain.model.mapping.MappingMissResolvedEvent;
import com.bone.claim.domain.model.mapping.MappingResolution;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.MatchRule;
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
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
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
  private final com.bone.claim.domain.repository.ClaimPolicyRepository claimPolicyRepository;
  private final TenantPort tenantPort;
  private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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
    // 命中映射的版本号随解析一并返回：收单侧落库 mapping_version 用，
    // 使已结案案件能回答"当时按哪条映射翻译"（candidates 里现成有，不额外查库）。
    final MappingResolution settled = resolution;
    String mappingVersion = "";
    if (settled.status() == ResolutionStatus.RESOLVED) {
      mappingVersion =
          candidates.stream()
              .filter(m -> m.getId() != null && m.getId().equals(settled.mappingId()))
              .map(CodeMapping::getVersionNo)
              .findFirst()
              .orElse("");
    }
    return new ResolveView(
        settled.status().name(),
        settled.standardCode(),
        resolution.mappingId(),
        resolution.reasonCode(),
        mappingVersion,
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

  // ===================== 批量导入 =====================

  public record ImportRowError(int rowNo, String reason) {}

  /**
   * 导入结果：REJECTED 时 errors 非空且<b>未写入任何行</b>；IDEMPOTENT_DUPLICATE 表示同内容文件
   * 重复上传（指纹命中既有批次），返回首次批次统计，不产生新行。
   */
  public record ImportResultView(
      String status, // IMPORTED / IDEMPOTENT_DUPLICATE / REJECTED
      int totalRows,
      int createdCount,
      int unchangedCount,
      String versionNo,
      List<ImportRowError> errors) {}

  /**
   * Excel 批量导入映射：模板下载 → 上传 → 整批校验 → 全部合法才发布。
   *
   * <p><b>整批拒绝而非逐行写入</b>：半个文件的导入状态最难解释——运营面对「83 成功 17 失败」
   * 通常直接重传全量，此时逐行写入已把当前生效行改成半旧半新。先校验后写入（validate-then-commit） 让「文件要么整体生效、要么整体不动」，与发布的事务语义一致。
   *
   * <p><b>幂等靠指纹</b>：version_no = 内容 SHA-256。重传同一文件 → 命中同批次 → 返回首次统计， 不再写行。校验使用逐域目录存在性检查（CERT_TYPE
   * 跳过，同单条发布口径 §6.8）。
   */
  @Transactional
  public ImportResultView importMappings(String insurerCode, String domain, byte[] fileBytes) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain d = MasterDataDomain.fromCode(domain);
    assertInsurerUsable(tenantId, insurerCode);

    List<MappingImportExcel.ImportRow> rows;
    try {
      rows = MappingImportExcel.parse(insurerCode, d.code(), fileBytes);
    } catch (BizException e) {
      return new ImportResultView(
          "REJECTED", 0, 0, 0, null, List.of(new ImportRowError(0, e.getMessage())));
    }
    if (rows.isEmpty()) {
      return new ImportResultView(
          "REJECTED", 0, 0, 0, null, List.of(new ImportRowError(0, "文件无有效数据行")));
    }

    // ---- 整批校验：所有行合法才进入写入 ----
    List<ImportRowError> errors = new ArrayList<>();
    java.util.Set<String> seenKeys = new java.util.HashSet<>();
    for (MappingImportExcel.ImportRow row : rows) {
      if (row.insurerItemCode().isBlank() || row.standardCode().isBlank()) {
        errors.add(new ImportRowError(row.rowNo(), "保司码值与标准码均不能为空"));
        continue;
      }
      String key = row.insurerItemCode();
      if (!seenKeys.add(key)) {
        errors.add(new ImportRowError(row.rowNo(), "保司码值在文件内重复: " + key));
        continue;
      }
      try {
        assertStandardCodeExists(tenantId, d, row.standardCode());
      } catch (DomainException e) {
        errors.add(new ImportRowError(row.rowNo(), "标准码不存在于目录: " + row.standardCode()));
      }
    }
    if (!errors.isEmpty()) {
      return new ImportResultView("REJECTED", rows.size(), 0, 0, null, List.copyOf(errors));
    }

    // ---- 指纹幂等：内容完全相同的文件重传，返回首次批次统计 ----
    String versionNo = MappingImportExcel.fingerprint(insurerCode, d.code(), rows);
    List<CodeMapping> existingBatch = codeMappingRepository.findByRelease(tenantId, versionNo);
    if (!existingBatch.isEmpty()) {
      return new ImportResultView(
          "IDEMPOTENT_DUPLICATE", rows.size(), 0, existingBatch.size(), versionNo, List.of());
    }

    // ---- 发布（复用单条发布内核：同码幂等、异码回放链） ----
    // 批次内新写行的 version_no 即本次指纹；版本号对不上的是「本批之前已生效」的行 → 计入 unchanged
    int created = 0;
    int unchanged = 0;
    for (MappingImportExcel.ImportRow row : rows) {
      CodeMapping result =
          publishCore(
              new PublishCommand(
                  insurerCode,
                  d.code(),
                  row.insurerItemCode(),
                  "",
                  row.standardCode(),
                  MappingType.EXACT,
                  MatchRule.MANUAL,
                  null,
                  null,
                  MappingSource.IMPORT,
                  versionNo,
                  null));
      if (versionNo.equals(result.getVersionNo())) {
        created++;
      } else {
        unchanged++;
      }
    }
    return new ImportResultView("IMPORTED", rows.size(), created, unchanged, versionNo, List.of());
  }

  /** 下载导入模板（列头 + 示例行）。 */
  @Transactional(readOnly = true)
  public byte[] importTemplate() {
    return MappingImportExcel.template();
  }

  @Transactional(readOnly = true)
  public PageView<MappingView> listMappings(
      String insurerCode,
      String domain,
      String keyword,
      boolean includeHistory,
      int page,
      int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<CodeMapping> records =
        codeMappingRepository.findPage(
            tenantId, insurerCode, domain, keyword, includeHistory, bounds.page(), bounds.size());
    List<MappingView> views =
        records.stream().map(MappingView::from).map(this::withStandardName).toList();
    long total =
        codeMappingRepository.countPage(tenantId, insurerCode, domain, keyword, includeHistory);
    return new PageView<>(views, total, bounds.page(), bounds.size());
  }

  /** 回填标准名称：spec 要求列表含「标准名称」，而映射表只存码。按域批量查目录（每页一次查询）， 目录里已不存在的码显示空名称——这正是「幽灵码」的可见信号，不是错误。 */
  private MappingView withStandardName(MappingView view) {
    MasterDataDomain domain = MasterDataDomain.fromCode(view.domain());
    String name =
        switch (domain) {
          case HOSPITAL -> refHospitalRepository
              .findByCodes(tenantPort.currentTenantId(), List.of(view.standardCode()))
              .stream()
              .findFirst()
              .map(r -> r.getHospitalName())
              .orElse("");
          case DRUG -> refDrugRepository
              .findByCodes(tenantPort.currentTenantId(), List.of(view.standardCode()))
              .stream()
              .findFirst()
              .map(r -> r.getDrugName())
              .orElse("");
          case DISEASE -> refDiseaseRepository
              .findByCodes(tenantPort.currentTenantId(), List.of(view.standardCode()))
              .stream()
              .findFirst()
              .map(r -> r.getDiseaseName())
              .orElse("");
          case FEE_CATEGORY -> refFeeCategoryRepository
              .findByCodes(tenantPort.currentTenantId(), List.of(view.standardCode()))
              .stream()
              .findFirst()
              .map(r -> r.getFeeName())
              .orElse("");
          case CERT_TYPE -> "";
        };
    return new MappingView(
        view.id(),
        view.insurerCode(),
        view.domain(),
        view.insurerItemCode(),
        view.disambiguationKey(),
        view.standardCode(),
        name,
        view.status(),
        view.active(),
        view.versionNo());
  }

  // ===================== 健康度 =====================

  /**
   * 主体×域映射健康度：覆盖率 = 已映射的标准码数（distinct）/ 标准目录总数。
   *
   * <p>CERT_TYPE 无本地目录表（已知缺口 §6.8）：catalogTotal = -1 表示「分母未知」，前端不展示 覆盖率，只展示未命中数——拿 0 当分母会算出「覆盖率
   * ∞」这种荒谬数字。
   */
  @Transactional(readOnly = true)
  public MappingHealthView mappingHealth(String insurerCode, String domain) {
    long tenantId = tenantPort.currentTenantId();
    MasterDataDomain d = MasterDataDomain.fromCode(domain);
    long catalogTotal =
        switch (d) {
          case HOSPITAL -> refHospitalRepository.countAll(tenantId);
          case DRUG -> refDrugRepository.countAll(tenantId);
          case DISEASE -> refDiseaseRepository.countAll(tenantId);
          case FEE_CATEGORY -> refFeeCategoryRepository.countAll(tenantId);
          case CERT_TYPE -> -1L;
        };
    long mappedStandardCodes =
        catalogTotal < 0
            ? 0
            : codeMappingRepository
                .findActiveByInsurerDomain(tenantId, insurerCode, d.code())
                .stream()
                .map(CodeMapping::getStandardCode)
                .distinct()
                .count();
    long openMissCount = mappingMissRepository.countOpen(tenantId, insurerCode, d.code());
    long recentMissCount =
        mappingMissRepository.countFirstSeenSince(
            tenantId, insurerCode, d.code(), Instant.now().minus(java.time.Duration.ofDays(7)));
    double coverage = catalogTotal > 0 ? (double) mappedStandardCodes / catalogTotal : -1.0;
    return new MappingHealthView(
        insurerCode,
        domain,
        catalogTotal,
        mappedStandardCodes,
        coverage,
        openMissCount,
        recentMissCount);
  }

  // ===================== 未命中队列 =====================

  @Transactional(readOnly = true)
  public PageView<MissView> listMisses(String status, String sortBy, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<MissView> records =
        mappingMissRepository
            .findPage(tenantId, status, sortBy, bounds.page(), bounds.size())
            .stream()
            .map(MissView::from)
            .toList();
    long total = mappingMissRepository.countPage(tenantId, status);
    return new PageView<>(records, total, bounds.page(), bounds.size());
  }

  /** 处置未命中：建立映射并把队列项标为已解决（同一事务，避免"映射建了但队列没关"）。操作人独立留痕。 */
  @Transactional
  public MissView resolveMiss(long missId, PublishCommand command, String operatorId) {
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
    miss.resolve(mapping.getId(), operatorId, Instant.now());
    mappingMissRepository.save(miss);
    // 广播"已解决"（判据⑨）：赔案侧在事务提交后按 (保司, 域, 原始码) 重扫在办案件回填
    // 标准码并恢复挂起。事件在事务内发布、AFTER_COMMIT 执行——重解析读到的映射必然可见。
    eventPublisher.publishEvent(
        new MappingMissResolvedEvent(
            miss.getTenantId(),
            miss.getInsurerCode(),
            miss.getDomain(),
            miss.getRawCode(),
            missId));
    return MissView.from(miss);
  }

  @Transactional
  public MissView ignoreMiss(long missId, String reason, String operatorId) {
    MappingMiss miss = requireMiss(missId);
    miss.ignore(reason, operatorId, Instant.now());
    mappingMissRepository.save(miss);
    return MissView.from(miss);
  }

  // ===================== 批量解析 =====================

  /** 单条批量解析上限：解析是逐条目录/映射查询，200 条以内一次请求可接受，超出应走导入而非解析。 */
  static final int MAX_BATCH_RESOLVE = 200;

  public record BatchResolveItem(
      String domain, String rawCode, String rawName, String disambiguationKey) {}

  public record BatchResolveItemView(
      String domain,
      String rawCode,
      String status,
      String standardCode,
      String standardName,
      String disambiguationKey,
      String reasonCode) {}

  public record BatchResolveResultView(int total, int resolved, List<BatchResolveItemView> items) {}

  /**
   * 批量解析（试算 / 收单前预检）：逐项独立，单项 fail-closed 不中断整批。
   *
   * <p>未命中记账与单条解析同一入口（recordMiss）——批量试算同样喂养未命中队列， 这是「队列来自真实流量」口径的一部分；但试算场景 caseNo 为空，只累次数不留案件号。
   */
  @Transactional
  public BatchResolveResultView resolveBatch(String insurerCode, List<BatchResolveItem> items) {
    if (items != null && items.size() > MAX_BATCH_RESOLVE) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_MAPPING_IMPORT_REJECTED,
          "批量解析上限 " + MAX_BATCH_RESOLVE + " 条，当前 " + items.size() + " 条");
    }
    List<BatchResolveItemView> views = new ArrayList<>();
    int resolvedCount = 0;
    if (items != null) {
      List<ResolveView> results = new ArrayList<>();
      for (BatchResolveItem item : items) {
        results.add(
            resolve(
                new ResolveCommand(
                    insurerCode,
                    item.domain(),
                    item.rawCode(),
                    item.rawName(),
                    item.disambiguationKey(),
                    "")));
      }
      // 标准名称批量回填：按域收集 distinct 标准码，每域一次目录查询（避免逐条 N+1）
      java.util.Map<String, String> nameByCode = standardNamesByCode(items, results);
      for (int i = 0; i < items.size(); i++) {
        BatchResolveItem item = items.get(i);
        ResolveView result = results.get(i);
        if (ResolutionStatus.valueOf(result.status()).isResolved()) {
          resolvedCount++;
        }
        String stdCode = result.standardCode() == null ? "" : result.standardCode();
        views.add(
            new BatchResolveItemView(
                item.domain(),
                item.rawCode(),
                result.status(),
                stdCode,
                nameByCode.getOrDefault(item.domain() + "|" + stdCode, ""),
                item.disambiguationKey(),
                result.reasonCode()));
      }
    }
    return new BatchResolveResultView(views.size(), resolvedCount, List.copyOf(views));
  }

  /** key = domain|standardCode → 标准名称。目录缺项不加键（getOrDefault 空）。 */
  private java.util.Map<String, String> standardNamesByCode(
      List<BatchResolveItem> items, List<ResolveView> results) {
    java.util.Map<String, java.util.List<String>> codesByDomain = new java.util.LinkedHashMap<>();
    for (int i = 0; i < items.size(); i++) {
      String stdCode = results.get(i).standardCode();
      if (stdCode == null || stdCode.isBlank()) {
        continue;
      }
      codesByDomain.computeIfAbsent(items.get(i).domain(), k -> new ArrayList<>()).add(stdCode);
    }
    java.util.Map<String, String> names = new java.util.HashMap<>();
    long tenantId = tenantPort.currentTenantId();
    codesByDomain.forEach(
        (domainCode, codes) -> {
          List<String> distinct = codes.stream().distinct().toList();
          switch (MasterDataDomain.fromCode(domainCode)) {
            case HOSPITAL -> refHospitalRepository
                .findByCodes(tenantId, distinct)
                .forEach(
                    r -> names.put(domainCode + "|" + r.getHospitalCode(), r.getHospitalName()));
            case DRUG -> refDrugRepository
                .findByCodes(tenantId, distinct)
                .forEach(r -> names.put(domainCode + "|" + r.getDrugCode(), r.getDrugName()));
            case DISEASE -> refDiseaseRepository
                .findByCodes(tenantId, distinct)
                .forEach(r -> names.put(domainCode + "|" + r.getDiseaseCode(), r.getDiseaseName()));
            case FEE_CATEGORY -> refFeeCategoryRepository
                .findByCodes(tenantId, distinct)
                .forEach(r -> names.put(domainCode + "|" + r.getFeeCode(), r.getFeeName()));
            case CERT_TYPE -> {}
          }
        });
    return names;
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
    return InsurerView.from(insurer, 0);
  }

  /** 编辑主体主档（编码不可改——它是保单/映射的外键锚点；其余字段走 {@link ClaimInsurer#applyEdit}）。 */
  @Transactional
  public InsurerView updateInsurer(String insurerCode, InsurerCommand command) {
    long tenantId = tenantPort.currentTenantId();
    ClaimInsurer insurer = requireInsurer(tenantId, insurerCode);
    insurer.applyEdit(
        command.insurerName(),
        command.insurerShortName(),
        command.insurerType(),
        command.creditCode(),
        Instant.now());
    claimInsurerRepository.save(insurer);
    return InsurerView.from(
        insurer, claimPolicyRepository.countByBizIdentity(tenantId, insurerCode));
  }

  /** 启用/停用/终止合作：不删行，只改状态（主体被保单与映射引用，物理删除会产生孤儿）。 */
  @Transactional
  public InsurerView changeInsurerStatus(String insurerCode, InsurerStatus target) {
    long tenantId = tenantPort.currentTenantId();
    ClaimInsurer insurer = requireInsurer(tenantId, insurerCode);
    insurer.changeStatus(target, Instant.now());
    claimInsurerRepository.save(insurer);
    return InsurerView.from(
        insurer, claimPolicyRepository.countByBizIdentity(tenantId, insurerCode));
  }

  private ClaimInsurer requireInsurer(long tenantId, String insurerCode) {
    ClaimInsurer insurer = claimInsurerRepository.findByInsurerCode(tenantId, insurerCode);
    if (insurer == null) {
      throw new DomainException("承保主体不存在: " + insurerCode);
    }
    return insurer;
  }

  @Transactional(readOnly = true)
  public PageView<InsurerView> listInsurers(String keyword, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<ClaimInsurer> insurers =
        claimInsurerRepository.findPage(tenantId, keyword, bounds.page(), bounds.size());
    // 关联保单数按页批量补齐：一页最多 100 行，每行一次 count；管理页低频访问，可接受。
    List<InsurerView> records =
        insurers.stream()
            .map(
                i ->
                    InsurerView.from(
                        i, claimPolicyRepository.countByBizIdentity(tenantId, i.getInsurerCode())))
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
      String mappingVersion,
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
      String standardName,
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
          "",
          mapping.getStatus(),
          mapping.isActive(),
          mapping.getVersionNo());
    }
  }

  /** 主体×域健康度视图（覆盖率 < 0 表示分母未知，前端不展示覆盖率）。 */
  public record MappingHealthView(
      String insurerCode,
      String domain,
      long catalogTotal,
      long mappedStandardCodes,
      double coverage,
      long openMissCount,
      long recentMissCount) {}

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
      String resolvedMappingId,
      String resolvedBy) {
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
          miss.getResolvedMappingId() == null ? null : String.valueOf(miss.getResolvedMappingId()),
          miss.getResolvedBy());
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
      String status,
      long policyCount) {
    static InsurerView from(ClaimInsurer insurer, long policyCount) {
      return new InsurerView(
          String.valueOf(insurer.getId()),
          insurer.getInsurerCode(),
          insurer.getInsurerName(),
          insurer.getInsurerShortName(),
          insurer.getInsurerType(),
          insurer.getCreditCode(),
          insurer.getParentCode(),
          insurer.getStatus(),
          policyCount);
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
