package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService.SubmitClaim;
import com.bone.claim.application.ClaimCaseApplicationService.SubmittedClaim;
import com.bone.claim.application.CodeMappingApplicationService.StpGateView;
import com.bone.claim.application.port.out.ClaimIntakePort;
import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
import com.bone.claim.domain.repository.ClaimItemRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 追加赔案（报案 1:0..N）在<b>建案管线</b>这一侧的行为：1:N 事实、幂等重放、跨报案幂等键冲突、事故上下文继承。
 *
 * <p><b>为什么不并进 {@code ClaimReportApplicationServiceTest}</b>：那一层只做「查报案 → 判可否追加 → 转交」，
 * 幂等与冲突判定真正落在这里。把那层测成绿而这里空白，等于「门禁看着有、其实没接」—— 追加赔案最危险的两件事（同一请求重复建案、把 A 报案的幂等键 复用到 B
 * 报案却静默成功）恰好都发生在本类。
 *
 * <p><b>1:N 与「1:1 时代」的差别只有一处</b>：{@link ClaimCase#getReportId()} 是本类唯一新增的归属判据。因此每个用例都把它当断言主体， 而不是只数
 * insert 次数——只数次数的话，把 reportId 写错成另一个报案照样绿。
 */
@ExtendWith(MockitoExtension.class)
class ClaimCaseAppendCaseTest {

  private static final long TENANT_ID = 42L;
  private static final long REPORT_ID = 76102367333449728L;
  private static final long OTHER_REPORT_ID = 76102367333449999L;
  private static final Instant OUT_INSURE = Instant.parse("2026-03-12T10:00:00Z");

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimReportRepository claimReportRepository;
  @Mock private ClaimItemRepository claimItemRepository;
  @Mock private PolicyQueryPort policyQueryPort;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private CodeMappingApplicationService codeMappingApplicationService;
  @Mock private ClaimInsurerRepository claimInsurerRepository;
  @Mock private ClaimIntakePort claimIntakePort;
  @InjectMocks private ClaimCaseApplicationService service;

  private ClaimReport report;

  @BeforeEach
  void setUp() {
    report =
        ClaimReport.open(
            REPORT_ID, TENANT_ID, "EG20260001", 7L, "hash-abc", "OUTPATIENT", OUT_INSURE, "v3", "");
    org.mockito.Mockito.lenient().when(tenantPort.currentTenantId()).thenReturn(TENANT_ID);
    // STP 闸门默认健康：本类测的是归属与幂等，闸门阻断会额外置标（由 ClaimCaseApplicationServiceTest 覆盖）
    org.mockito.Mockito.lenient()
        .when(codeMappingApplicationService.stpGate(any(), any()))
        .thenReturn(StpGateView.allow());
  }

  /** 追加命令：保单维度全部为空（由 buildClaim 从报案取），只带赔付维度。 */
  private SubmitClaim appendCommand(String idempotencyKey) {
    return new SubmitClaim(
        idempotencyKey,
        null,
        null,
        report.getPlanId(),
        report.getInsuredIdHash(),
        report.getClaimType(),
        "04400001",
        "12345678",
        null,
        null,
        null,
        report.getOutInsureTime(),
        "B1",
        null,
        null,
        List.of(
            new SubmitClaim.ClaimItemDto(
                "诊查费",
                "",
                ItemCategory.SERVICE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                new BigDecimal("10.00"),
                null,
                "")));
  }

  @Nested
  @DisplayName("同一报案下的多笔赔案")
  class OneToMany {

    @Test
    @DisplayName("同一报案追加两次 → 两条赔案，reportId 均指向该报案，赔案号互不相同")
    void appendsSecondCaseUnderSameReport() {
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-1")).thenReturn(null);
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-2")).thenReturn(null);

      SubmittedClaim first = service.appendCase(report, appendCommand("idem-1"));
      SubmittedClaim second = service.appendCase(report, appendCommand("idem-2"));

      ArgumentCaptor<ClaimCase> inserted = ArgumentCaptor.forClass(ClaimCase.class);
      verify(claimCaseRepository, times(2)).insert(inserted.capture());

      assertThat(inserted.getAllValues())
          .as("1:0..N 的事实层判据：两条赔案都挂在同一个报案下")
          .allSatisfy(c -> assertThat(c.getReportId()).isEqualTo(REPORT_ID));
      assertThat(first.claimNo()).isNotEqualTo(second.claimNo());
      assertThat(first.claimId()).isNotEqualTo(second.claimId());
      // 报案号对两笔都相同：客服按报案号能查到这一组赔案
      assertThat(first.reportNo()).isEqualTo(report.getReportNo());
      assertThat(second.reportNo()).isEqualTo(report.getReportNo());
    }

    @Test
    @DisplayName("事故上下文一律继承报案：命令里保单号为空，落库的赔案取报案值")
    void inheritsIncidentContextFromReport() {
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-ctx")).thenReturn(null);

      service.appendCase(report, appendCommand("idem-ctx"));

      ArgumentCaptor<ClaimCase> inserted = ArgumentCaptor.forClass(ClaimCase.class);
      verify(claimCaseRepository).insert(inserted.capture());
      ClaimCase saved = inserted.getValue();

      assertThat(saved.getPolicyNo()).isEqualTo(report.getPolicyNo());
      assertThat(saved.getPlanId()).isEqualTo(report.getPlanId());
      assertThat(saved.getInsuredIdHash()).isEqualTo(report.getInsuredIdHash());
      assertThat(saved.getOutInsureTime()).isEqualTo(report.getOutInsureTime());
      assertThat(saved.getPlanVersion()).isEqualTo(report.getPlanVersion());
      // 赔案号前缀是 CL（与报案号 RP 分属两个命名空间）
      assertThat(saved.getClaimNo()).startsWith("CL");
    }

    @Test
    @DisplayName("追加的赔案走完整建案管线：受理签收 + 发事件（不是裸插入）")
    void appendedCaseGoesThroughIntakePipeline() {
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-pipe")).thenReturn(null);

      service.appendCase(report, appendCommand("idem-pipe"));

      ArgumentCaptor<ClaimCase> inserted = ArgumentCaptor.forClass(ClaimCase.class);
      verify(claimCaseRepository).insert(inserted.capture());
      // 只有走 accept() 才是「已签收」；漏掉这一步的裸插入会让追加案停在草稿态且对外投影错
      assertThat(inserted.getValue().externalStatus()).isEqualTo("PENDING_EVIDENCE");
      verify(domainEventPublisher).publishFrom(inserted.getValue());
    }
  }

  @Nested
  @DisplayName("幂等与冲突")
  class Idempotency {

    @Test
    @DisplayName("幂等键命中同一报案 → 返回既有赔案，不再插入（重放不得产生第二笔赔案）")
    void replayedKeyReturnsExistingCase() {
      ClaimCase existing = mock(ClaimCase.class);
      when(existing.getId()).thenReturn(777L);
      when(existing.getClaimNo()).thenReturn("CL20261122EXISTING01");
      when(existing.getReportId()).thenReturn(REPORT_ID);
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-replay")).thenReturn(existing);

      SubmittedClaim result = service.appendCase(report, appendCommand("idem-replay"));

      assertThat(result.claimId()).isEqualTo(777L);
      assertThat(result.claimNo()).isEqualTo("CL20261122EXISTING01");
      assertThat(result.reportNo()).isEqualTo(report.getReportNo());
      verify(claimCaseRepository, never()).insert(any());
      verify(domainEventPublisher, never()).publishFrom(any());
    }

    @Test
    @DisplayName("幂等键已属于另一个报案 → 400 CONTEXT_MISMATCH（不得静默把赔案挂到当前报案）")
    void rejectsKeyOwnedByAnotherReport() {
      ClaimCase existing = mock(ClaimCase.class);
      when(existing.getReportId()).thenReturn(OTHER_REPORT_ID);
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-stolen")).thenReturn(existing);

      assertThatThrownBy(() -> service.appendCase(report, appendCommand("idem-stolen")))
          .isInstanceOf(BizException.class)
          .hasFieldOrPropertyWithValue("errorCode", ClaimErrorCodes.CLAIM_REPORT_CONTEXT_MISMATCH)
          // 报文带幂等键：调用方能定位到是哪个键被复用了
          .hasMessageContaining("idem-stolen");

      verify(claimCaseRepository, never()).insert(any());
    }
  }

  @Nested
  @DisplayName("跨租户")
  class TenantIsolation {

    @Test
    @DisplayName("幂等键查询恒带当前租户：他租户同键不会命中（防跨租户复用键拿到别人的赔案）")
    void idempotencyLookupIsTenantScoped() {
      when(claimCaseRepository.findByIdempotencyKey(TENANT_ID, "idem-scoped")).thenReturn(null);

      service.appendCase(report, appendCommand("idem-scoped"));

      // 关键判据是「传了 tenantId」，而不是「查了」。SDK 的 Criteria 不会自动加租户条件，
      // 少传一个参数就会变成全租户可见 —— 这类缺陷不会让任何用例转红，只会在多租户上线后串数据。
      verify(claimCaseRepository).findByIdempotencyKey(eq(TENANT_ID), eq("idem-scoped"));
    }
  }
}
