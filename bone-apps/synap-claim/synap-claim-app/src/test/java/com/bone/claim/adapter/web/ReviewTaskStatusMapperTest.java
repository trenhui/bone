package com.bone.claim.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.claim.adapter.web.ReviewTaskStatusMapper.TaskStatus;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 任务维 → 案件状态映射测试。
 *
 * <p>为什么值得单独测：映射写错时的表现是「工作台待办列表少了几类案件」或「已结案案件混进待办」，两者都不会报错， 只会被用户当成为什么我的单不见了。这里把 4 个任务维的落点钉死，改一行即红。
 */
class ReviewTaskStatusMapperTest {

  @Test
  @DisplayName("WAITING（待领取）只映射待核赔 40")
  void waiting() {
    assertThat(ReviewTaskStatusMapper.toClaimStatuses(TaskStatus.WAITING))
        .containsExactly(ClaimStatus.PENDING_ADJUST);
  }

  @Test
  @DisplayName("ASSIGNED/PROCESSING 合并映射核赔中 41（案件模型无独立「已分配未处理」态）")
  void assignedAndProcessingShareAdjusting() {
    assertThat(ReviewTaskStatusMapper.toClaimStatuses(TaskStatus.ASSIGNED))
        .containsExactly(ClaimStatus.ADJUSTING);
    assertThat(ReviewTaskStatusMapper.toClaimStatuses(TaskStatus.PROCESSING))
        .containsExactly(ClaimStatus.ADJUSTING);
  }

  @Test
  @DisplayName("DONE 覆盖已处理全谱（42/44/50/45）")
  void done() {
    assertThat(ReviewTaskStatusMapper.toClaimStatuses(TaskStatus.DONE))
        .containsExactly(
            ClaimStatus.ADJUSTED,
            ClaimStatus.REVIEWING,
            ClaimStatus.FINISHED,
            ClaimStatus.CLOSED_REJECTED);
  }

  @Test
  @DisplayName("不传 status → 默认待办（40+41），不含终态")
  void defaultTodo() {
    List<ClaimStatus> statuses = ReviewTaskStatusMapper.toClaimStatuses(null);
    assertThat(statuses).containsExactly(ClaimStatus.PENDING_ADJUST, ClaimStatus.ADJUSTING);
    assertThat(statuses).noneMatch(ClaimStatus::terminal);
  }
}
