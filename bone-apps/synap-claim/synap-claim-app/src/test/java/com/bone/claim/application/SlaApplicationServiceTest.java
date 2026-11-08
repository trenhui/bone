package com.bone.claim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimCaseTrack;
import com.bone.claim.domain.model.sla.ClaimCaseSla;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimCaseSlaRepository;
import com.bone.claim.domain.repository.ClaimCaseTrackRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SLA 物化测试：从 track 派生阶段停留/挂起占用，按 §6.6 计算 remaining_work_hours。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlaApplicationServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final long TENANT = 7L;
  private static final long CLAIM = 11L;

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private ClaimCaseTrackRepository trackRepository;
  @Mock private ClaimCaseSlaRepository slaRepository;
  @Mock private TenantPort tenantPort;

  private SlaApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new SlaApplicationService(claimCaseRepository, trackRepository, slaRepository, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    ClaimCase base = claim(false, TENANT);
    when(claimCaseRepository.findById(CLAIM)).thenReturn(base);
    when(slaRepository.findByClaimId(TENANT, CLAIM)).thenReturn(null);
  }

  @Test
  void materializesRemainingWithoutHang() {
    // 周五 09:00 进入 AUDITING（基线 48h），now=周五 15:00 → 已耗 6h → 剩余 42h
    when(trackRepository.findByClaimId(TENANT, CLAIM)).thenReturn(List.of());

    service.refreshForClaimAt(CLAIM, at(2026, 10, 23, 15));

    ArgumentCaptor<ClaimCaseSla> captor = ArgumentCaptor.forClass(ClaimCaseSla.class);
    verify(slaRepository).save(captor.capture());
    ClaimCaseSla sla = captor.getValue();
    assertEquals(TENANT, sla.getTenantId());
    assertEquals(CLAIM, sla.getClaimId());
    assertEquals("AUDITING", sla.getCurrentStage());
    assertEquals(new BigDecimal("42.00"), sla.getRemainingWorkHours());
    assertEquals(0L, sla.getHangAccumSeconds());
  }

  @Test
  void subtractsHangOccupancyInWorkHours() {
    ClaimCaseTrack hangUp = track("HANG_UP", at(2026, 10, 23, 13));
    ClaimCaseTrack resume = track("RESUME", at(2026, 10, 23, 14));
    when(trackRepository.findByClaimId(TENANT, CLAIM)).thenReturn(List.of(hangUp, resume));

    service.refreshForClaimAt(CLAIM, at(2026, 10, 23, 15));

    ArgumentCaptor<ClaimCaseSla> captor = ArgumentCaptor.forClass(ClaimCaseSla.class);
    verify(slaRepository).save(captor.capture());
    ClaimCaseSla sla = captor.getValue();
    // stageEnteredAt = 14:00（RESUME），elapsed 1h；挂起 13-14 共 1h → 48-1-1 = 46
    assertEquals(new BigDecimal("46.00"), sla.getRemainingWorkHours());
    assertEquals(3600L, sla.getHangAccumSeconds());
  }

  @Test
  void skipsWhenTenantMismatch() {
    ClaimCase other = claim(false, 8L);
    when(claimCaseRepository.findById(CLAIM)).thenReturn(other);

    service.refreshForClaimAt(CLAIM, at(2026, 10, 23, 15));

    verify(slaRepository, never()).save(any());
  }

  @Test
  void skipsTerminalClaim() {
    ClaimCase done = claim(true, TENANT);
    when(claimCaseRepository.findById(CLAIM)).thenReturn(done);

    service.refreshForClaimAt(CLAIM, at(2026, 10, 23, 15));

    verify(slaRepository, never()).save(any());
  }

  private static ClaimCase claim(boolean finished, long tenantId) {
    ClaimCase claim = mock(ClaimCase.class);
    when(claim.getTenantId()).thenReturn(tenantId);
    when(claim.getStatus()).thenReturn("ADJUSTING");
    when(claim.getStage()).thenReturn("AUDITING");
    when(claim.getCreatedAt()).thenReturn(at(2026, 10, 23, 9));
    when(claim.finished()).thenReturn(finished);
    return claim;
  }

  private static ClaimCaseTrack track(String actionCode, Instant createdAt) {
    ClaimCaseTrack track = mock(ClaimCaseTrack.class);
    when(track.getActionCode()).thenReturn(actionCode);
    when(track.getCreatedAt()).thenReturn(Date.from(createdAt));
    return track;
  }

  private static Instant at(int year, int month, int day, int hour) {
    return ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZONE).toInstant();
  }
}
