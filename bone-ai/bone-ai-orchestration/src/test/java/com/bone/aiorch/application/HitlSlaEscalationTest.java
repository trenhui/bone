package com.bone.aiorch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.aiorch.domain.hitl.HitlDispositionRepository;
import com.bone.aiorch.domain.hitl.HitlItem;
import com.bone.aiorch.domain.hitl.HitlItemRepository;
import com.bone.aiorch.domain.hitl.HitlStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** HITL SLA 熔断：超时判定（createdAt 起算、只判 PENDING、策略调整对存量一致生效）与批量升级。 */
class HitlSlaEscalationTest {

  private final HitlItemRepository repository = mock(HitlItemRepository.class);
  private final HitlDispositionRepository dispositionRepository =
      mock(HitlDispositionRepository.class);
  private final HitlApplicationService service =
      new HitlApplicationService(repository, dispositionRepository);

  private HitlItem item(LocalDateTime createdAt, HitlStatus status) {
    HitlItem item = HitlItem.create("1", "scene", "suggestion", "value", "LOW", 0.9, "trace-1");
    item.setStatus(status);
    item.setCreatedAt(createdAt);
    return item;
  }

  @Test
  void pending_item_older_than_sla_is_overdue() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    HitlItem item = item(now.minusHours(49), HitlStatus.PENDING);

    assertThat(item.isOverdue(now, Duration.ofHours(48))).isTrue();
  }

  @Test
  void pending_item_within_sla_is_not_overdue() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    assertThat(item(now.minusHours(47), HitlStatus.PENDING).isOverdue(now, Duration.ofHours(48)))
        .isFalse();
    // 恰好在 SLA 边界（等于）不算超时：与「超过才升级」的口径一致，避免边界歧义
    assertThat(item(now.minusHours(48), HitlStatus.PENDING).isOverdue(now, Duration.ofHours(48)))
        .isFalse();
  }

  @Test
  void resolved_item_is_never_overdue() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    // 已裁决项不得升级（升级已裁决项是无意义的状态倒流）
    assertThat(item(now.minusDays(30), HitlStatus.CONFIRMED).isOverdue(now, Duration.ofHours(48)))
        .isFalse();
    assertThat(item(now.minusDays(30), HitlStatus.ESCALATED).isOverdue(now, Duration.ofHours(48)))
        .isFalse();
  }

  @Test
  void missing_created_at_is_conservatively_not_overdue() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    HitlItem item = item(null, HitlStatus.PENDING);
    // 宁可漏升级，不可误升级
    assertThat(item.isOverdue(now, Duration.ofHours(48))).isFalse();
  }

  @Test
  void sla_policy_change_applies_to_existing_items() {
    // 策略由比较时叠加，不落 deadline 列 → 调整 SLA 后对全部存量项一致生效
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    HitlItem item = item(now.minusHours(50), HitlStatus.PENDING);
    assertThat(item.isOverdue(now, Duration.ofHours(48))).isTrue();
    assertThat(item.isOverdue(now, Duration.ofHours(72))).isFalse();
  }

  @Test
  void escalate_overdue_updates_only_overdue_pending_items() {
    LocalDateTime now = LocalDateTime.of(2026, 10, 18, 12, 0);
    HitlItem overdue = item(now.minusHours(72), HitlStatus.PENDING);
    HitlItem fresh = item(now.minusHours(1), HitlStatus.PENDING);
    when(repository.findPending(anyString(), any(), any())).thenReturn(List.of(overdue, fresh));
    when(repository.findPendingById(anyString(), anyString())).thenReturn(Optional.of(overdue));

    List<HitlItem> escalated = service.escalateOverdue("1", Duration.ofHours(48));

    assertThat(escalated).containsExactly(overdue);
    assertThat(overdue.getStatus()).isEqualTo(HitlStatus.ESCALATED);
    verify(repository).save(overdue);
    verify(dispositionRepository).insert(any());
    // 未超时项不被触碰
    verify(repository, never()).save(fresh);
  }
}
