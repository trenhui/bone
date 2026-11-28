package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.valueobject.SignChannel;
import com.bone.claim.domain.model.sign.valueobject.SignSource;
import com.bone.claim.domain.model.sign.valueobject.SignStatus;
import com.bone.claim.domain.model.sign.valueobject.SignType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimSignBatchRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimSignApplicationServiceTest {

  @Mock private ClaimSignBatchRepository batchRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private TenantPort tenantPort;
  @Mock private DomainEventPublisher domainEventPublisher;
  @InjectMocks private ClaimSignApplicationService service;

  @BeforeEach
  void setUp() {
    when(tenantPort.currentTenantId()).thenReturn(10L);
  }

  @Nested
  @DisplayName("线上自动签收")
  class AutoSign {
    @Test
    @DisplayName("内部报案自动签收：建批次并写轨迹")
    void internalAutoSign() {
      var result =
          service.signOnAcceptance(
              new ClaimSignApplicationService.AutoSignCommand(
                  "claim-1001", SignChannel.INTERNAL_REPORT));
      assertThat(result.created()).isTrue();
      assertThat(result.batchNo()).startsWith("SG");
      verify(batchRepository).insert(any(ClaimSignBatch.class));
      verify(batchRepository)
          .saveTrack(
              anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any());
      verify(domainEventPublisher).publishFrom(any(ClaimSignBatch.class));
    }

    @Test
    @DisplayName("同 businessKey 重复推送幂等：返回首次不重复建批次")
    void duplicateBusinessKeyIdempotent() {
      ClaimSignBatch existing =
          ClaimSignBatch.create(
              1L,
              10L,
              "SG001",
              SignSource.ONLINE,
              SignType.AUTO,
              SignChannel.API_PUSH,
              "claim-2002",
              null,
              null,
              null,
              null);
      when(batchRepository.findByBusinessKey(10L, "claim-2002")).thenReturn(existing);
      var result =
          service.signOnAcceptance(
              new ClaimSignApplicationService.AutoSignCommand("claim-2002", SignChannel.API_PUSH));
      assertThat(result.created()).isFalse();
      assertThat(result.batchNo()).isEqualTo("SG001");
      verify(batchRepository, never()).insert(any());
    }

    @Test
    @DisplayName("顶层赔案金额回补到明细：自动建案金额不再恒为 0")
    void batchAmountPassedToItem() {
      var element =
          new ClaimSignApplicationService.CaseElement(
              "CL-3003",
              "P-3003",
              0L,
              "张三",
              "110101199001011234",
              Instant.parse("2026-01-02T03:04:05Z"),
              "OUTPATIENT",
              "API_PUSH",
              null);
      service.signOnAcceptance(
          new ClaimSignApplicationService.AutoSignCommand(
              "claim-3003", SignChannel.API_PUSH, List.of(element), 12345L));

      ArgumentCaptor<ClaimSignBatch> captor = ArgumentCaptor.forClass(ClaimSignBatch.class);
      verify(batchRepository).insert(captor.capture());
      assertThat(captor.getValue().getItems()).hasSize(1);
      assertThat(captor.getValue().getItems().get(0).getClaimAmount())
          .isEqualByComparingTo("12345");
    }

    @Test
    @DisplayName("businessKey 缺失被拒绝")
    void missingBusinessKeyRejected() {
      assertThatThrownBy(
              () ->
                  service.signOnAcceptance(
                      new ClaimSignApplicationService.AutoSignCommand("  ", SignChannel.API_PUSH)))
          .isInstanceOf(BizException.class);
      verify(batchRepository, never()).insert(any());
    }
  }

  @Nested
  @DisplayName("线下批次签收")
  class OfflineSign {
    @Test
    @DisplayName("名单为空被拒绝")
    void emptyParticipantsRejected() {
      assertThatThrownBy(() -> service.importParticipants(1L, List.of(), "u1"))
          .isInstanceOf(BizException.class);
      verify(batchRepository, never()).save(any());
    }

    @Test
    @DisplayName("确认签收成功后内存态与轨迹同为 FINISHED")
    void confirmSuccessRecordsFinishedTrack() {
      ClaimSignBatch batch =
          ClaimSignBatch.create(
              1L,
              10L,
              "SG004",
              SignSource.OFFLINE,
              SignType.MANUAL,
              SignChannel.EXPRESS,
              null,
              "SF",
              "SF1",
              "张三",
              Instant.now());
      batch.addItem(com.bone.claim.domain.model.sign.SignBatchItem.of(10L, 1L, 100L, "CL001"));
      batch.registerImageReady();
      batch.transitionTo(SignStatus.WAIT_FOR_IMAGE, "x", "u1");
      batch.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "x", "u1");
      when(batchRepository.findById(1L)).thenReturn(batch);
      when(batchRepository.confirmWithCas(
              anyLong(), anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(1);

      service.confirmOffline(1L, "u1", "影像资料齐全，确认完成线下签收");

      assertThat(batch.status()).isEqualTo(SignStatus.FINISHED);
      verify(batchRepository)
          .saveTrack(
              anyLong(),
              anyLong(),
              anyLong(),
              any(),
              eq(SignStatus.FINISHED.code()),
              eq("CONFIRM"),
              eq("线下人工确认签收"),
              any(),
              eq("u1"),
              any());
      verify(domainEventPublisher).publishFrom(batch);
    }

    @Test
    @DisplayName("确认签收 CAS 影响行数 0 抛冲突")
    void confirmCasConflict() {
      ClaimSignBatch batch =
          ClaimSignBatch.create(
              1L,
              10L,
              "SG003",
              SignSource.OFFLINE,
              SignType.MANUAL,
              SignChannel.EXPRESS,
              null,
              "SF",
              "SF1",
              "张三",
              Instant.now());
      batch.addItem(com.bone.claim.domain.model.sign.SignBatchItem.of(10L, 1L, 100L, "CL001"));
      batch.registerImageReady();
      batch.transitionTo(SignStatus.WAIT_FOR_IMAGE, "x", "u1");
      batch.transitionTo(SignStatus.WAIT_FOR_CONFIRMATION, "x", "u1");
      when(batchRepository.findById(1L)).thenReturn(batch);
      when(batchRepository.confirmWithCas(
              anyLong(), anyLong(), anyString(), anyString(), anyString()))
          .thenReturn(0);
      assertThatThrownBy(() -> service.confirmOffline(1L, "u1", "影像资料齐全，确认完成线下签收"))
          .isInstanceOf(BizException.class);
      verify(batchRepository, never())
          .saveTrack(
              anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any());
    }
  }
}
