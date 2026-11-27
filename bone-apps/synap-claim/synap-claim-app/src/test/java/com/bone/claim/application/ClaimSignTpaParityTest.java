package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.adapter.web.assembler.ClaimSignAssembler;
import com.bone.claim.adapter.web.dto.request.ClaimSignPageReq;
import com.bone.claim.adapter.web.dto.response.SignBatchItemResp;
import com.bone.claim.adapter.web.dto.response.SignBatchResp;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.claim.domain.model.sign.valueobject.SignSource;
import com.bone.claim.domain.model.sign.valueobject.SignType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** TPA 对齐：查询入参、批次字段传递、明细排序与响应装配。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimSignTpaParityTest {

  @Mock private ClaimSignBatchRepository batchRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @InjectMocks private ClaimSignApplicationService service;

  private static ClaimSignBatch batch() {
    ClaimSignBatch batch =
        ClaimSignBatch.create(
            77L,
            10L,
            "SG77",
            SignSource.OFFLINE,
            SignType.MANUAL,
            SignChannel.EXPRESS,
            null,
            null,
            null,
            null,
            null);
    batch.withSigningParties("永诚财险", "河北分公司", "上海核工程", "紧急");
    return batch;
  }

  @Test
  @DisplayName("查询条件全空时默认方法不抛空指针")
  void emptyFiltersDoNotThrow() {
    ClaimSignBatchRepository repository =
        org.mockito.Mockito.mock(
            ClaimSignBatchRepository.class,
            org.mockito.Mockito.withSettings()
                .defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));

    repository.findBatchPage(
        10L, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, 20);
  }

  @Test
  @DisplayName("批次查询拒绝倒置的时间窗")
  void rejectsInvertedTimeWindow() {
    Instant from = Instant.parse("2026-06-10T00:00:00Z");
    Instant to = Instant.parse("2026-06-09T00:00:00Z");
    ClaimSignPageReq req =
        new ClaimSignPageReq(
            "ALL", null, null, null, null, null, null, null, null, from, to, null, null, 1, 20);

    assertThat(req.isSignTimeRangeValid()).isFalse();
    assertThat(req.isCreateTimeRangeValid()).isTrue();
  }

  @Test
  @DisplayName("创建批次时保司三方与紧急程度落到领域对象")
  void createBatchCarriesParityFields() {
    when(tenantPort.currentTenantId()).thenReturn(10L);

    service.createOfflineBatch(
        new ClaimSignApplicationService.CreateOfflineBatchCommand(
            SignChannel.EXPRESS,
            null,
            "顺丰",
            "SF123",
            "张三",
            null,
            "u1",
            "永诚财险",
            "河北分公司",
            "上海核工程",
            "紧急"));

    ArgumentCaptor<ClaimSignBatch> captor = ArgumentCaptor.forClass(ClaimSignBatch.class);
    verify(batchRepository).insert(captor.capture());
    ClaimSignBatch saved = captor.getValue();
    assertThat(saved.getInsuranceCompany()).isEqualTo("永诚财险");
    assertThat(saved.getInsuranceSubsidiary()).isEqualTo("河北分公司");
    assertThat(saved.getInsuringAgency()).isEqualTo("上海核工程");
    assertThat(saved.getEmergency()).isEqualTo("紧急");
  }

  @Test
  @DisplayName("批次明细按赔案号稳定排序")
  void itemsSortedByClaimNo() {
    when(tenantPort.currentTenantId()).thenReturn(10L);
    when(batchRepository.findById(77L)).thenReturn(batch());
    SignBatchItem later =
        SignBatchItem.withCaseElements(
            10L, 77L, "CL002", "bk2", "P1", 0L, "李四", "110101199001011234", null, null, null);
    SignBatchItem earlier =
        SignBatchItem.withCaseElements(
            10L, 77L, "CL001", "bk1", "P1", 0L, "张三", "110101199001011234", null, null, null);
    when(batchRepository.findItemsByBatchId(77L)).thenReturn(List.of(later, earlier));

    assertThat(service.items(77L)).containsExactly(earlier, later);
  }

  @Test
  @DisplayName("响应 DTO 完整暴露 TPA 对齐字段")
  void assemblerExposesParityFields() {
    SignBatchResp resp =
        ClaimSignAssembler.INSTANCE.toResp(batch(), new ClaimSignCaseSummary(0, 0, 0, List.of()));
    assertThat(resp.insuranceCompany()).isEqualTo("永诚财险");
    assertThat(resp.insuranceSubsidiary()).isEqualTo("河北分公司");
    assertThat(resp.insuringAgency()).isEqualTo("上海核工程");
    assertThat(resp.emergency()).isEqualTo("紧急");
    assertThat(resp.createdAt()).isNotNull();

    SignBatchItem item =
        SignBatchItem.withCaseElements(
            10L, 77L, "CL001", "bk1", "P1", 0L, "张三", "110101199001011234", null, null, null);
    item.withImageAudit("身份证", 3, 2, 1, Instant.parse("2026-06-09T10:00:00Z"), "lutao");
    SignBatchItemResp itemResp = ClaimSignAssembler.INSTANCE.toItemResps(List.of(item)).get(0);
    assertThat(itemResp.identityType()).isEqualTo("身份证");
    assertThat(itemResp.countingResult()).isEqualTo(3);
    assertThat(itemResp.imageCount()).isEqualTo(2);
    assertThat(itemResp.imageUploadCount()).isEqualTo(1);
    assertThat(itemResp.imageUploader()).isEqualTo("lutao");
  }
}
