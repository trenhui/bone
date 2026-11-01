package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.bone.claim.application.QuotaConfigApplicationService.AdjustQuota;
import com.bone.claim.application.QuotaConfigApplicationService.QuotaAccountView;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import com.bone.claim.domain.repository.QuotaChangeLogRepository;
import com.bone.core.exception.BizException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 额度配置面（调额/减人/恢复）的乐观锁冲突检测（复用 {@link ClaimConfigApplicationService#assertEditableVersion}）。
 *
 * <p><b>为什么是这三类用例</b>：
 *
 * <ul>
 *   <li>{@link #staleVersion_throwsConcurrentConflict_onAdjust()}——提交版本落后于当前版本，必须抛出 {@code
 *       CLAIM_CONCURRENT_CONFLICT}（409）；删掉守护这一行即转红，是真正的变异杀。
 *   <li>{@link #matchingVersion_appliesAdjust()}——版本一致，调额正常落盘。
 *   <li>{@link #nullSubmittedVersion_rejected()}——额度账户属资金口径，<b>不带版本必须被拒</b>（不允许放弃乐观锁覆盖他人变更）；
 *       改为放行即转红，是真正的变异杀。
 * </ul>
 *
 * <p><b>版本种子的取法</b>：{@link QuotaAccount#open} 出来的聚合 {@code version} 为 null（由 SDK 在首次落库时置值）， 而守护比较要求
 * {@code currentVersion} 非空，故用反射把被测实体的版本置成 5，再让提交方带上 4（过期）或 5（一致）。
 */
@ExtendWith(MockitoExtension.class)
class QuotaConfigOptimisticLockTest {

  @Mock QuotaAccountRepository quotaAccountRepository;
  @Mock QuotaChangeLogRepository quotaChangeLogRepository;
  @Mock TenantPort tenantPort;

  @InjectMocks QuotaConfigApplicationService service;

  private QuotaAccount savedAccount(long version) {
    QuotaAccount a =
        QuotaAccount.open(
            1L, 1L, "hash-001", "POL-OPTLOCK-Q", 10L, 2026, "PERSONAL", "", new BigDecimal("1000"));
    try {
      Field f = QuotaAccount.class.getDeclaredField("version");
      f.setAccessible(true);
      f.set(a, version);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return a;
  }

  private AdjustQuota adjustCmd(Long submittedVersion) {
    return new AdjustQuota(1L, new BigDecimal("800"), "op", "op", "E2E", submittedVersion);
  }

  @Test
  void staleVersion_throwsConcurrentConflict_onAdjust() {
    when(quotaAccountRepository.findById(1L)).thenReturn(savedAccount(5L));

    assertThatThrownBy(() -> service.adjustInitial(adjustCmd(4L)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT));
  }

  @Test
  void matchingVersion_appliesAdjust() {
    when(quotaAccountRepository.findById(1L)).thenReturn(savedAccount(5L));

    QuotaAccountView view = service.adjustInitial(adjustCmd(5L));
    assertThat(view.initial()).isEqualByComparingTo("800");
    assertThat(view.version()).isEqualTo(5L);
  }

  @Test
  void nullSubmittedVersion_rejected() {
    when(quotaAccountRepository.findById(1L)).thenReturn(savedAccount(5L));

    assertThatThrownBy(() -> service.adjustInitial(adjustCmd(null)))
        .isInstanceOf(BizException.class)
        .satisfies(
            ex ->
                assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT));
  }
}
