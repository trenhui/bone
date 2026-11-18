package com.bone.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.masterdata.application.command.RegisterSupplierCommand;
import com.bone.masterdata.application.query.dto.SupplierRegistrationResultDTO;
import com.bone.masterdata.domain.model.supplier.SupplierState;
import com.bone.masterdata.domain.model.supplier.VendorState;
import com.bone.masterdata.testsupport.goldenrecord.InMemoryEntityKeyMappingAdapter;
import com.bone.masterdata.testsupport.goldenrecord.TestCurrentUserPort;
import com.bone.masterdata.testsupport.supplier.InMemoryLegalEntityRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryPaymentBlockReleaseReview;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierExitFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierOnboardingFacts;
import com.bone.masterdata.testsupport.supplier.InMemorySupplierRepositoryPort;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorGateFacts;
import com.bone.masterdata.testsupport.supplier.InMemoryVendorRepositoryPort;
import com.bone.masterdata.testsupport.supplier.TestSupplierExitGuard;
import com.bone.masterdata.testsupport.supplier.TestSupplierOnboardingGuard;
import com.bone.masterdata.testsupport.supplier.TestVendorActivationGuard;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 供应商合并装配：调用既有 Golden Record 内核，<b>不复制</b>其裁决逻辑。
 *
 * <p>核心断言是「非 APPLIED 一律零副作用」—— 转人工/拒绝时不得落库供应商、不得建立 Vendor 角色、不得登记 xref 映射。 这条口径与 Golden Record
 * 编排层一致（`GoldenRecordApplicationService:118-129`），否则会出现「映射已建但主体未成立」的半成品。
 *
 * <p><b>逐字段 lineage 不在本类断言</b>：那是 {@code GoldenRecord} 构造期的不变量，由 {@code
 * GoldenRecordLineageInvariantTest} 覆盖。本类只断言装配层真正负责的事 —— 不裁字段、不重复登记 xref、版本单调递增。
 */
@DisplayName("供应商合并装配：只有 APPLIED 才产出视图与落库")
class SupplierMergeAssemblyTest {

  private static final long TENANT = 7L;
  private static final String USCC = "91310000MA1FL0Q23X";
  private static final Instant AT = Instant.parse("2026-11-16T02:00:00Z");

  private InMemorySupplierRepositoryPort supplierPort;
  private InMemoryVendorRepositoryPort vendorPort;
  private InMemoryLegalEntityRepositoryPort legalEntityPort;
  private InMemoryEntityKeyMappingAdapter keyMappingPort;
  private TestCurrentUserPort currentUserPort;
  private SupplierApplicationService service;

  @BeforeEach
  void setUp() {
    supplierPort = new InMemorySupplierRepositoryPort();
    vendorPort = new InMemoryVendorRepositoryPort();
    legalEntityPort = new InMemoryLegalEntityRepositoryPort();
    keyMappingPort = new InMemoryEntityKeyMappingAdapter();
    currentUserPort = new TestCurrentUserPort();
    currentUserPort.setTenantId(TENANT);
    service =
        new SupplierApplicationService(
            supplierPort,
            vendorPort,
            legalEntityPort,
            keyMappingPort,
            currentUserPort,
            new InMemorySupplierExitFacts(),
            new TestSupplierExitGuard(),
            new InMemorySupplierOnboardingFacts(),
            new TestSupplierOnboardingGuard(),
            new InMemoryVendorGateFacts(),
            new TestVendorActivationGuard(),
            new InMemoryPaymentBlockReleaseReview());
  }

  @Test
  @DisplayName("合并成立：落供应商、建立 Vendor 角色并登记 xref，返回双角色状态")
  void appliesMergeAndCreatesBothRoles() {
    SupplierRegistrationResultDTO result =
        service.register(command("MERGE-1", "PARTY-1", sources()));

    assertThat(result.getOutcome()).isEqualTo("APPLIED");
    assertThat(result.getPartyId()).isEqualTo("PARTY-1");
    assertThat(result.getSupplierState()).isEqualTo(SupplierState.PROSPECT.name());
    assertThat(result.getVendorState()).isEqualTo(VendorState.PENDING_VENDOR.name());
    assertThat(result.getFieldCount()).isPositive();

    assertThat(supplierPort.countByTenant(TENANT)).isEqualTo(1L);
    assertThat(vendorPort.findCurrent(TENANT, "PARTY-1")).isPresent();
    assertThat(keyMappingPort.findByLocalKey(TENANT, "SRM", "V-1001")).hasSize(1);
    assertThat(supplierPort.saveCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("身份主键冲突转人工：不落库、不建 Vendor、不登记 xref")
  void manualReviewOnIdentityConflictHasNoSideEffects() {
    List<RegisterSupplierCommand.SourceRecordSpec> conflicting =
        List.of(
            spec("R1", "SRM", Map.of("unified_social_credit_code", USCC, "supplier_name", "ACME")),
            spec(
                "R2",
                "ERP",
                Map.of("unified_social_credit_code", "91310000MA1FL0Q23Y", "tax_id", "TAX-2")));

    SupplierRegistrationResultDTO result =
        service.register(command("MERGE-2", "PARTY-1", conflicting));

    assertThat(result.getOutcome()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertNoSideEffects();
  }

  @Test
  @DisplayName("无候选源记录直接拒绝：零副作用")
  void rejectsEmptySourcesWithoutSideEffects() {
    SupplierRegistrationResultDTO result =
        service.register(command("MERGE-3", "PARTY-1", List.of()));

    assertThat(result.getOutcome()).isEqualTo("REJECTED");
    assertNoSideEffects();
  }

  @Test
  @DisplayName("具名裁决缺失：整笔转人工，零副作用（不部分应用任何字段胜出结果）")
  void manualReviewWhenStewardOverrideLacksNamedAccount() {
    RegisterSupplierCommand cmd = command("MERGE-4", "PARTY-1", sources());
    cmd.getStrategies().put("supplier_name", "STEWARD_OVERRIDE");
    // 刻意不提供 stewardAccountId 与 stewardOverrides：具名裁决缺失必须整笔转人工。

    SupplierRegistrationResultDTO result = service.register(cmd);

    assertThat(result.getOutcome()).isEqualTo("MANUAL_REVIEW_REQUIRED");
    assertThat(result.getReason()).contains("具名裁决");
    assertNoSideEffects();
  }

  @Test
  @DisplayName("装配层不得静默裁字段：两个源记录的三个字段全部进入 Golden Record")
  void appliedMergeKeepsEverySourcedField() {
    Map<String, String> first = new LinkedHashMap<>();
    first.put("unified_social_credit_code", USCC);
    Map<String, String> second = new LinkedHashMap<>();
    second.put("tax_id", "TAX-9");
    second.put("supplier_name", "ACME");

    SupplierRegistrationResultDTO result =
        service.register(
            command(
                "MERGE-5",
                "PARTY-1",
                List.of(spec("R1", "SRM", first), spec("R2", "ERP", second))));

    assertThat(result.getOutcome()).isEqualTo("APPLIED");
    // 3 = uscc + tax_id + supplier_name；少任何一个都说明装配层裁掉字段或策略吞了字段。
    assertThat(result.getFieldCount()).isEqualTo(3);
    // 身份主键与供应商视图同步成立：字段进了 Golden Record，也进了法人身份与供应商落库。
    assertThat(legalEntityPort.findByUnifiedSocialCreditCode(TENANT, USCC)).isPresent();
    assertThat(supplierPort.findCurrent(TENANT, "PARTY-1")).isPresent();
  }

  @Test
  @DisplayName("重复登记同一本地编码：xref 不重复登记，供应商版本单调递增")
  void reregisterSameLocalIdBumpsVersionWithoutDuplicatingXref() {
    service.register(command("MERGE-6", "PARTY-1", sources()));

    SupplierRegistrationResultDTO second =
        service.register(command("MERGE-7", "PARTY-1", sources()));

    assertThat(second.getOutcome()).isEqualTo("APPLIED");
    assertThat(second.getSupplierVersion()).isEqualTo(2L);
    assertThat(keyMappingPort.findByLocalKey(TENANT, "SRM", "V-1001")).hasSize(1);
    assertThat(supplierPort.countByTenant(TENANT)).isEqualTo(1L);
  }

  private void assertNoSideEffects() {
    assertThat(supplierPort.saveCount()).isZero();
    assertThat(supplierPort.countByTenant(TENANT)).isZero();
    assertThat(vendorPort.saveCount()).isZero();
    assertThat(legalEntityPort.saveCount()).isZero();
    assertThat(keyMappingPort.findByLocalKey(TENANT, "SRM", "V-1001")).isEmpty();
  }

  private RegisterSupplierCommand command(
      String mergeId, String partyId, List<RegisterSupplierCommand.SourceRecordSpec> sources) {
    RegisterSupplierCommand cmd = new RegisterSupplierCommand();
    cmd.setTenantId(TENANT);
    cmd.setMergeId(mergeId);
    cmd.setPartyId(partyId);
    cmd.setSourceSystem("SRM");
    cmd.setLocalId("V-1001");
    cmd.setSources(sources);
    cmd.setMergedAt(AT);
    return cmd;
  }

  private List<RegisterSupplierCommand.SourceRecordSpec> sources() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("unified_social_credit_code", USCC);
    values.put("supplier_name", "ACME");
    return List.of(spec("R1", "SRM", values));
  }

  private RegisterSupplierCommand.SourceRecordSpec spec(
      String recordId, String sourceSystem, Map<String, String> values) {
    RegisterSupplierCommand.SourceRecordSpec spec = new RegisterSupplierCommand.SourceRecordSpec();
    spec.setRecordId(recordId);
    spec.setSourceSystem(sourceSystem);
    spec.setValues(new LinkedHashMap<>(values));
    spec.setObservedAt(AT);
    return spec;
  }
}
