package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.inspection.ClaimUserAgent;
import com.bone.claim.domain.repository.ClaimUserAgentRepository;
import com.bone.core.exception.BizException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 代理区间判据钉子（批 4 任务 3.1）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAgentServiceTest {

  private static final long TENANT = 42L;
  private static final String PRINCIPAL = "user-A";

  @Mock private ClaimUserAgentRepository repository;
  @Mock private TenantPort tenantPort;

  private UserAgentService service;

  @BeforeEach
  void setUp() {
    service = new UserAgentService(repository, tenantPort);
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
  }

  private static Date d(String iso) {
    return Date.from(Instant.parse(iso));
  }

  @Test
  @DisplayName("3.1 重叠生效代理被拒：写入期事前查重，不落库")
  void overlappingActiveAgentRejected() {
    ClaimUserAgent existing =
        ClaimUserAgent.register(
            1L,
            TENANT,
            PRINCIPAL,
            "user-B",
            d("2026-01-01T00:00:00Z"),
            d("2026-12-31T00:00:00Z"),
            "全年请假",
            new Date());
    when(repository.findByPrincipal(PRINCIPAL)).thenReturn(List.of(existing));

    assertThatThrownBy(
            () ->
                service.register(
                    PRINCIPAL,
                    "user-C",
                    d("2026-06-01T00:00:00Z"),
                    d("2026-07-01T00:00:00Z"),
                    "重叠"))
        .isInstanceOf(BizException.class)
        .extracting(e -> ((BizException) e).getErrorCode())
        .isEqualTo(ClaimErrorCodes.SC_CLAIM_AGENT_CONFLICT);
    verify(repository, never()).insert(any());
  }

  @Test
  @DisplayName("3.1 区间不重叠：允许登记第二条（不同时段）")
  void nonOverlappingAllowed() {
    ClaimUserAgent existing =
        ClaimUserAgent.register(
            1L,
            TENANT,
            PRINCIPAL,
            "user-B",
            d("2026-01-01T00:00:00Z"),
            d("2026-06-01T00:00:00Z"),
            "上半年",
            new Date());
    when(repository.findByPrincipal(PRINCIPAL)).thenReturn(List.of(existing));

    var view =
        service.register(
            PRINCIPAL, "user-C", d("2026-06-01T00:00:00Z"), d("2026-12-01T00:00:00Z"), "下半年");

    assertThat(view.agentId()).isEqualTo("user-C");
    assertThat(view.active()).isTrue();
    verify(repository).insert(any(ClaimUserAgent.class));
  }

  @Test
  @DisplayName("3.1 区间外不生效：effectiveAgent 在区间外返回 null")
  void outsideIntervalNotEffective() {
    ClaimUserAgent agent =
        ClaimUserAgent.register(
            1L,
            TENANT,
            PRINCIPAL,
            "user-B",
            d("2026-01-01T00:00:00Z"),
            d("2026-06-01T00:00:00Z"),
            "上半年",
            new Date());
    when(repository.findActiveByPrincipal(PRINCIPAL)).thenReturn(agent);

    assertThat(service.effectiveAgent(PRINCIPAL, d("2026-07-01T00:00:00Z"))).isNull();
    assertThat(service.effectiveAgent(PRINCIPAL, d("2026-03-01T00:00:00Z"))).isEqualTo("user-B");
  }
}
