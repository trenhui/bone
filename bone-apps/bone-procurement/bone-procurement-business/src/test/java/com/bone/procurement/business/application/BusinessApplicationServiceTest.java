package com.bone.procurement.business.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.InMemoryBusinessRepository;
import com.bone.procurement.business.domain.gateway.BusinessIdentityPort;
import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessApplicationServiceTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("匿名操作人不得授标")
  void anonymousAwardRejected() {
    BusinessApplicationService service =
        new BusinessApplicationService(new InMemoryBusinessRepository(), () -> null);

    assertThatThrownBy(() -> service.award(1L, "RFX-1", "SUP-1", "理由", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("登录态");
  }

  @Test
  @DisplayName("AI 建议身份与请求体冒名均不得授标")
  void aiAndImpersonationRejected() {
    BusinessApplicationService aiService =
        new BusinessApplicationService(
            new InMemoryBusinessRepository(),
            () -> new BusinessIdentityPort.Actor(1L, "AI_SUGGESTION"));

    assertThatThrownBy(() -> aiService.award(1L, "RFX-1", "SUP-1", "理由", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("AI");

    BusinessApplicationService service =
        new BusinessApplicationService(
            new InMemoryBusinessRepository(), () -> new BusinessIdentityPort.Actor(1L, "alice"));
    assertThatThrownBy(() -> service.award(2L, "RFX-1", "SUP-1", "理由", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("跨租户");
  }

  @Test
  @DisplayName("具名人工授标成功并只按登录态租户读回")
  void namedAwardSucceedsAndIsTenantScoped() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    BusinessApplicationService service =
        new BusinessApplicationService(repo, () -> new BusinessIdentityPort.Actor(1L, "alice"));

    Award award = service.award(1L, "RFX-1", "SUP-1", "综合最优", NOW);

    assertThat(award.confirmedBy()).isEqualTo("alice");
    assertThat(service.findAward(1L, "RFX-1")).contains(award);
    assertThat(service.findAward(1L, "RFX-OTHER")).isEmpty();
  }

  @Test
  @DisplayName("相同来源引用重复提交复用既有投标，不新增事实")
  void sourceRefSubmissionIsIdempotent() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    BusinessApplicationService service =
        new BusinessApplicationService(repo, () -> new BusinessIdentityPort.Actor(1L, "alice"));

    Bid first = service.submitBid(1L, "BID-1", "RFX-1", "SUP-1", "SRC-SAME", plusDay(), NOW);
    Bid second = service.submitBid(1L, "BID-2", "RFX-1", "SUP-1", "SRC-SAME", plusDay(), NOW);

    assertThat(second.bidId()).isEqualTo(first.bidId());
    assertThat(repo.findBids(1L, "RFX-1")).hasSize(1);
  }

  @Test
  @DisplayName("服务层拒绝已提交投标以新来源引用复活或改写")
  void submittedBidCannotBeResubmittedWithNewSource() {
    BusinessApplicationService service =
        new BusinessApplicationService(
            new InMemoryBusinessRepository(), () -> new BusinessIdentityPort.Actor(1L, "alice"));
    service.submitBid(1L, "BID-LOCK", "RFX-1", "SUP-1", "SRC-OLD", plusDay(), NOW);

    assertThatThrownBy(
            () -> service.submitBid(1L, "BID-LOCK", "RFX-1", "SUP-1", "SRC-NEW", plusDay(), NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("终态");
  }

  @Test
  @DisplayName("关闭 CAPA 使用登录态操作人，不消费请求体身份")
  void closeCapaUsesLoginIdentity() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    BusinessApplicationService service =
        new BusinessApplicationService(repo, () -> new BusinessIdentityPort.Actor(1L, "alice"));
    service.createCapa(1L, "CAPA-1", "SUP-1", "交付延误", "owner", plusDay());

    assertThatThrownBy(() -> service.closeCapa(1L, "CAPA-1", "", NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("证据");
    assertThat(service.closeCapa(1L, "CAPA-1", "整改附件引用", NOW).closedBy()).isEqualTo("alice");
  }

  private static Date plusDay() {
    return Date.from(NOW.toInstant().plusSeconds(86_400));
  }
}
