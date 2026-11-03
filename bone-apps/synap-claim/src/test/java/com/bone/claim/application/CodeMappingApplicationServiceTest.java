package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.CodeMappingApplicationService.MappingView;
import com.bone.claim.application.CodeMappingApplicationService.MissView;
import com.bone.claim.application.CodeMappingApplicationService.PublishCommand;
import com.bone.claim.application.CodeMappingApplicationService.ResolveCommand;
import com.bone.claim.application.CodeMappingApplicationService.ResolveView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.mapping.ClaimInsurer;
import com.bone.claim.domain.model.mapping.CodeMapping;
import com.bone.claim.domain.model.mapping.MappingMiss;
import com.bone.claim.domain.model.mapping.MappingSource;
import com.bone.claim.domain.model.mapping.MappingType;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.MatchRule;
import com.bone.claim.domain.model.mapping.MissStatus;
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
import com.bone.core.exception.DomainException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 多保司主数据映射应用服务测试。
 *
 * <p>核心是设计 §15 的<b>双向判据</b>：只写"删掉映射必须转红"不够——后人把实现改成"永远拒绝"也能全绿。 必须同时有正向钉子：映射存在且唯一时<b>必须</b>命中并落下标准码。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CodeMappingApplicationServiceTest {

  private static final long TENANT = 1L;
  private static final String INSURER = "YCBX";
  private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");

  @Mock private CodeMappingRepository codeMappingRepository;
  @Mock private MappingMissRepository mappingMissRepository;
  @Mock private StdCodeAliasRepository stdCodeAliasRepository;
  @Mock private StdCodeSplitRepository stdCodeSplitRepository;
  @Mock private ClaimInsurerRepository claimInsurerRepository;
  @Mock private InsurerCodeItemRepository insurerCodeItemRepository;
  @Mock private FieldBindingRepository fieldBindingRepository;
  @Mock private RefHospitalRepository refHospitalRepository;
  @Mock private RefDrugRepository refDrugRepository;
  @Mock private RefDiseaseRepository refDiseaseRepository;
  @Mock private RefFeeCategoryRepository refFeeCategoryRepository;
  @Mock private com.bone.claim.domain.repository.ClaimPolicyRepository claimPolicyRepository;
  @Mock private TenantPort tenantPort;
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

  private CodeMappingApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new CodeMappingApplicationService(
            codeMappingRepository,
            mappingMissRepository,
            stdCodeAliasRepository,
            stdCodeSplitRepository,
            claimInsurerRepository,
            insurerCodeItemRepository,
            fieldBindingRepository,
            refHospitalRepository,
            refDrugRepository,
            refDiseaseRepository,
            refFeeCategoryRepository,
            claimPolicyRepository,
            tenantPort,
            eventPublisher);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    // 默认：保司可用、标准医院码存在（个别用例自行覆盖）
    when(claimInsurerRepository.findByInsurerCode(anyLong(), anyString()))
        .thenReturn(activeInsurer());
    when(refHospitalRepository.findByHospitalCode(anyLong(), anyString()))
        .thenReturn(mock(com.bone.claim.domain.model.reference.RefHospital.class));
  }

  private ClaimInsurer activeInsurer() {
    return ClaimInsurer.of(
        900L, TENANT, INSURER, "永诚财产保险股份有限公司", "永诚", "DIRECT", "91310000MA1FL1000X", "", NOW);
  }

  private CodeMapping activeMapping(String raw, String std) {
    return CodeMapping.of(
        101L,
        TENANT,
        INSURER,
        MasterDataDomain.HOSPITAL,
        raw,
        "",
        std,
        MappingType.EXACT,
        MatchRule.MANUAL,
        null,
        null,
        MappingSource.MANUAL,
        "R1",
        NOW,
        NOW);
  }

  private PublishCommand publish(String raw, String std) {
    return new PublishCommand(
        INSURER,
        "HOSPITAL",
        raw,
        null,
        std,
        MappingType.EXACT,
        MatchRule.MANUAL,
        null,
        null,
        MappingSource.MANUAL,
        "R1",
        null);
  }

  // ===================== 正向钉子：映射存在必须命中 =====================

  @Test
  @DisplayName("映射存在且唯一 → RESOLVED 且标准码非空（反向钉子：防实现被改成永远拒绝）")
  void resolveHitsUniqueMapping() {
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H1"))
        .thenReturn(List.of(activeMapping("H1", "STD-H1")));

    ResolveView view =
        service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H1", null, null, null));

    assertThat(view.status()).isEqualTo("RESOLVED");
    assertThat(view.standardCode()).isEqualTo("STD-H1");
    assertThat(view.mappingId()).isEqualTo(101L);
    assertThat(view.missRecorded()).isFalse();
    verify(mappingMissRepository, never()).save(any());
  }

  @Test
  @DisplayName("批量解析：逐项独立，命中项计数，未命中项落队列且不中断整批")
  void resolveBatchIsolatesFailures() {
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H1"))
        .thenReturn(List.of(activeMapping("H1", "STD-H1")));
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H9"))
        .thenReturn(List.of());

    var result =
        service.resolveBatch(
            INSURER,
            java.util.List.of(
                new CodeMappingApplicationService.BatchResolveItem("HOSPITAL", "H1", "协和", null),
                new CodeMappingApplicationService.BatchResolveItem("HOSPITAL", "H9", "幽灵", null)));

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.resolved()).isEqualTo(1);
    assertThat(result.items().get(0).status()).isEqualTo("RESOLVED");
    assertThat(result.items().get(0).standardCode()).isEqualTo("STD-H1");
    assertThat(result.items().get(1).status()).isEqualTo("MISSING");
    verify(mappingMissRepository).save(any(MappingMiss.class));
  }

  // ===================== 负向：删掉映射必须转人工且落队列 =====================

  @Test
  @DisplayName("无映射 → MISSING、不落标准码、写未命中队列（变异验证：删掉映射查询此用例必红）")
  void resolveMissWithoutMappingGoesToQueue() {
    when(codeMappingRepository.findCandidates(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(List.of());

    ResolveView view =
        service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H9", "某某医院", null, "C1"));

    assertThat(view.status()).isEqualTo("MISSING");
    assertThat(view.standardCode()).isNull();
    assertThat(view.missRecorded()).isTrue();
    verify(mappingMissRepository).save(any(MappingMiss.class));
  }

  @Test
  @DisplayName("重复未命中按 (保司,域,码) 聚合计数，不刷爆队列")
  void repeatedMissAggregatesOccurrence() {
    when(codeMappingRepository.findCandidates(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(List.of());
    MappingMiss existing =
        MappingMiss.firstSeen(
            501L, TENANT, INSURER, MasterDataDomain.HOSPITAL, "H9", "某某医院", "C1", NOW);
    when(mappingMissRepository.findByKey(TENANT, INSURER, "HOSPITAL", "H9")).thenReturn(existing);

    service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H9", null, null, "C2"));

    verify(mappingMissRepository).save(existing);
    assertThat(existing.getOccurCount()).isEqualTo(2);
    assertThat(existing.getStatus()).isEqualTo(MissStatus.OPEN.code());
  }

  @Test
  @DisplayName("命中的标准码是拆分父码 → SPLIT_REQUIRES_MANUAL，标准码不下发且入队")
  void splitParentFailsClosed() {
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H1"))
        .thenReturn(List.of(activeMapping("H1", "STD-H1")));
    when(stdCodeSplitRepository.findByParent(eq(TENANT), eq("HOSPITAL"), eq("STD-H1")))
        .thenReturn(List.of(mock(com.bone.claim.domain.model.mapping.StdCodeSplit.class)));

    ResolveView view =
        service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H1", null, null, null));

    assertThat(view.status()).isEqualTo("SPLIT_REQUIRES_MANUAL");
    assertThat(view.standardCode()).isNull();
    assertThat(view.missRecorded()).isTrue();
  }

  // ===================== 发布：幂等 / 改判 / 幽灵码 =====================

  @Test
  @DisplayName("重复发布同一映射 → 幂等返回，不产生新版本行")
  void publishSameTargetIsIdempotent() {
    CodeMapping current = activeMapping("H1", "STD-H1");
    when(codeMappingRepository.findActive(TENANT, INSURER, "HOSPITAL", "H1", ""))
        .thenReturn(current);

    MappingView view = service.publish(publish("H1", "STD-H1"));

    assertThat(view.id()).isEqualTo(String.valueOf(current.getId()));
    verify(codeMappingRepository, never()).save(any());
  }

  @Test
  @DisplayName("改判映射 → 旧行被替代（activeFlag 置空并留 superseded_by 链），新行落库生效")
  void publishDifferentTargetSupersedesOldRow() {
    CodeMapping current = activeMapping("H1", "STD-OLD");
    when(codeMappingRepository.findActive(TENANT, INSURER, "HOSPITAL", "H1", ""))
        .thenReturn(current);

    MappingView view = service.publish(publish("H1", "STD-H1"));

    assertThat(current.isActive()).isFalse();
    assertThat(current.getSupersededById()).isNotNull();
    verify(codeMappingRepository, org.mockito.Mockito.times(2)).save(any(CodeMapping.class));
    assertThat(view.standardCode()).isEqualTo("STD-H1");
    assertThat(view.active()).isTrue();
  }

  @Test
  @DisplayName("映射指向不存在的标准码 → 拒绝（禁止幽灵码：比未命中更隐蔽）")
  void ghostStandardCodeRejected() {
    when(refHospitalRepository.findByHospitalCode(anyLong(), anyString())).thenReturn(null);

    assertThatThrownBy(() -> service.publish(publish("H1", "GHOST")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("幽灵码");
  }

  @Test
  @DisplayName("保司停用 → 禁止新增映射（映射面随主体冻结）")
  void disabledInsurerRejected() {
    ClaimInsurer disabled = activeInsurer();
    disabled.changeStatus(com.bone.claim.domain.model.mapping.InsurerStatus.DISABLED, NOW);
    when(claimInsurerRepository.findByInsurerCode(TENANT, INSURER)).thenReturn(disabled);

    assertThatThrownBy(() -> service.publish(publish("H1", "STD-H1")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("停用");
  }

  // ===================== 未命中处置闭环 =====================

  @Test
  @DisplayName("处置未命中 → 建映射 + 关队列项在同一事务语义内完成（防『映射建了队列没关』）")
  void resolveMissClosesQueueEntry() {
    MappingMiss miss =
        MappingMiss.firstSeen(
            501L, TENANT, INSURER, MasterDataDomain.HOSPITAL, "H9", "某某医院", "C1", NOW);
    when(mappingMissRepository.findById(501L)).thenReturn(miss);

    MissView view = service.resolveMiss(501L, publish("H9", "STD-H9"), "op-77");

    assertThat(view.status()).isEqualTo(MissStatus.RESOLVED.code());
    assertThat(Long.valueOf(view.resolvedMappingId())).isPositive();
    verify(codeMappingRepository).save(any(CodeMapping.class));
    verify(mappingMissRepository).save(miss);
  }

  @Test
  @DisplayName("解析候选内含别名链 → 经 canonicalize 后仍解析到现行码（MERGE 不丢映射）")
  void aliasRedirectStillResolves() {
    // 旧映射指向已合并的旧码 STD-OLD；别名表声明 STD-OLD → STD-NEW
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H1"))
        .thenReturn(List.of(activeMapping("H1", "STD-OLD")));
    when(stdCodeAliasRepository.findByDomain(eq(TENANT), eq("HOSPITAL")))
        .thenReturn(
            List.of(
                com.bone.claim.domain.model.mapping.StdCodeAlias.of(
                    700L,
                    TENANT,
                    MasterDataDomain.HOSPITAL,
                    "STD-OLD",
                    "STD-NEW",
                    com.bone.claim.domain.model.mapping.AliasRelation.MERGE,
                    NOW)));
    when(stdCodeSplitRepository.findByParent(anyLong(), anyString(), anyString()))
        .thenReturn(List.of());

    ResolveView view =
        service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H1", null, null, null));

    assertThat(view.status()).isEqualTo("RESOLVED");
    assertThat(view.standardCode()).isEqualTo("STD-NEW");
  }

  @Test
  @DisplayName("别名链成环 → ALIAS_CYCLE，不猜、转人工")
  void aliasCycleFailsClosed() {
    when(codeMappingRepository.findCandidates(TENANT, INSURER, "HOSPITAL", "H1"))
        .thenReturn(List.of(activeMapping("H1", "STD-A")));
    when(stdCodeAliasRepository.findByDomain(eq(TENANT), eq("HOSPITAL")))
        .thenReturn(
            List.of(
                com.bone.claim.domain.model.mapping.StdCodeAlias.of(
                    700L,
                    TENANT,
                    MasterDataDomain.HOSPITAL,
                    "STD-A",
                    "STD-B",
                    com.bone.claim.domain.model.mapping.AliasRelation.MERGE,
                    NOW),
                com.bone.claim.domain.model.mapping.StdCodeAlias.of(
                    701L,
                    TENANT,
                    MasterDataDomain.HOSPITAL,
                    "STD-B",
                    "STD-A",
                    com.bone.claim.domain.model.mapping.AliasRelation.MERGE,
                    NOW)));
    when(stdCodeSplitRepository.findByParent(anyLong(), anyString(), anyString()))
        .thenReturn(List.of());

    ResolveView view =
        service.resolve(new ResolveCommand(INSURER, "HOSPITAL", "H1", null, null, null));

    assertThat(view.status()).isEqualTo("ALIAS_CYCLE");
    assertThat(view.standardCode()).isNull();
  }

  // ===================== STP 闸门（§6.9）采集逻辑 =====================

  @Test
  @DisplayName("STP：无未命中/无冲突 → allowAuto=true，命中率与直通率均 100%")
  void stpGateHealthy() {
    when(mappingMissRepository.countOpen(anyLong(), anyString(), anyString())).thenReturn(0L);
    when(mappingMissRepository.countByStatus(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(0L);
    when(mappingMissRepository.countReopenedSince(anyLong(), anyString(), anyString(), any()))
        .thenReturn(0L);
    when(mappingMissRepository.findAll(anyLong(), anyString(), anyString())).thenReturn(List.of());
    when(codeMappingRepository.findActiveByInsurerDomain(anyLong(), anyString(), anyString()))
        .thenReturn(List.of());

    var view = service.stpGate(INSURER, "HOSPITAL");

    assertThat(view.allowAuto()).isTrue();
    assertThat(view.itemHitRate()).isEqualTo(100.0);
    assertThat(view.caseStraightThroughRate()).isEqualTo(100.0);
    assertThat(view.conflictCount()).isZero();
  }

  @Test
  @DisplayName("STP：同保司码出现两个标准码候选（冲突）→ 阻断自动理算")
  void stpGateConflictBlocks() {
    when(mappingMissRepository.countOpen(anyLong(), anyString(), anyString())).thenReturn(0L);
    when(mappingMissRepository.countByStatus(anyLong(), anyString(), anyString(), anyString()))
        .thenReturn(0L);
    when(mappingMissRepository.countReopenedSince(anyLong(), anyString(), anyString(), any()))
        .thenReturn(0L);
    when(mappingMissRepository.findAll(anyLong(), anyString(), anyString())).thenReturn(List.of());
    // 同 raw=H1 但两个不同标准码 ⇒ 冲突数=1（设计 §6.9：冲突该域禁自动理算）
    when(codeMappingRepository.findActiveByInsurerDomain(anyLong(), anyString(), anyString()))
        .thenReturn(List.of(activeMapping("H1", "STD-A"), activeMapping("H1", "STD-B")));

    var view = service.stpGate(INSURER, "HOSPITAL");

    assertThat(view.conflictCount()).isEqualTo(1);
    assertThat(view.allowAuto()).isFalse();
  }
}
