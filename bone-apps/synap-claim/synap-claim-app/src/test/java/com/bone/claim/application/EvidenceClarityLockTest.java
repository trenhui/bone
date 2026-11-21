package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.DocumentParsePort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.evidence.Evidence;
import com.bone.claim.domain.model.evidence.valueobject.DocType;
import com.bone.claim.domain.model.evidence.valueobject.ImageClarity;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.EvidenceRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 锁定时影像清晰度 fail-closed 守卫（P0-A）。
 *
 * <p>变异验证：删掉 lockAll 的前置校验 ⇒ 非 CLEAR 阻断用例红；把判定改成 {@code == CLEAR} 才阻断 ⇒ 全 CLEAR 用例红。
 */
class EvidenceClarityLockTest {

  private static final long CLAIM_ID = 1001L;
  private static final String OPERATOR = "op-evidence";

  private EvidenceRepository evidenceRepository;
  private DomainEventPublisher domainEventPublisher;
  private EvidenceApplicationService service;

  @BeforeEach
  void setUp() {
    evidenceRepository = mock(EvidenceRepository.class);
    domainEventPublisher = mock(DomainEventPublisher.class);
    service =
        new EvidenceApplicationService(
            evidenceRepository,
            mock(TenantPort.class),
            domainEventPublisher,
            mock(ClaimCaseRepository.class),
            mock(DocumentParsePort.class));
  }

  private static Evidence evidence(long id, ImageClarity clarity) {
    Evidence e =
        Evidence.register(
            id, 42L, CLAIM_ID, DocType.INVOICE, "oss://ref/" + id, "hash-" + id, "PASS");
    e.markClarity(clarity, Instant.parse("2026-11-21T10:00:00Z"));
    return e;
  }

  @Test
  @DisplayName("非 CLEAR 影像阻断锁定：422 + 错误码 + detail 含证据ID，且一条都不落库/发事件")
  void nonClearEvidenceBlocksLock() {
    when(evidenceRepository.findByClaimId(CLAIM_ID))
        .thenReturn(List.of(evidence(7L, ImageClarity.CLEAR), evidence(8L, ImageClarity.UNCLEAR)));

    assertThatThrownBy(() -> service.lockAll(CLAIM_ID, OPERATOR))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex -> {
              BizException biz = (BizException) ex;
              assertThat(biz.getErrorCode())
                  .isEqualTo(ClaimErrorCodes.SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK);
              assertThat(biz.getCode()).isEqualTo(422);
              assertThat(biz.getMessage()).contains("证据ID", "8");
            });

    verify(evidenceRepository, never()).save(any(Evidence.class));
    verify(domainEventPublisher, never()).publishFrom(any());
  }

  @Test
  @DisplayName("未知受控码同样阻断：库内脏码不得被静默当成 CLEAR 放行")
  void unknownCodeAlsoBlocksLock() {
    Evidence corrupted = mock(Evidence.class);
    when(corrupted.getId()).thenReturn(9L);
    when(corrupted.getClearType()).thenReturn("BLURRY");
    when(evidenceRepository.findByClaimId(CLAIM_ID))
        .thenReturn(List.of(evidence(7L, ImageClarity.CLEAR), corrupted));

    assertThatThrownBy(() -> service.lockAll(CLAIM_ID, OPERATOR))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK));

    verify(evidenceRepository, never()).save(any(Evidence.class));
  }

  @Test
  @DisplayName("全 CLEAR 正向钉子：正常锁定并逐份保存，不误伤")
  void allClearEvidenceLocks() {
    List<Evidence> evidences =
        List.of(evidence(7L, ImageClarity.CLEAR), evidence(8L, ImageClarity.CLEAR));
    when(evidenceRepository.findByClaimId(CLAIM_ID)).thenReturn(evidences);

    assertThat(service.lockAll(CLAIM_ID, OPERATOR)).isEqualTo(2);

    verify(evidenceRepository, times(2)).save(any(Evidence.class));
    verify(domainEventPublisher, times(2)).publishFrom(any());
    assertThat(evidences).allSatisfy(e -> assertThat(e.isLocked()).isTrue());
  }
}
