package com.bone.procurement.qualification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.qualification.domain.gateway.QualificationRepository;
import com.bone.procurement.qualification.domain.model.qualification.Onboarding;
import com.bone.procurement.qualification.domain.model.qualification.Qualification;
import com.bone.procurement.qualification.domain.model.qualification.VerifyStatus;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** OCR 人工复核接线：具名复核后读回一致、缺租户/跨租户拒绝、重复提交幂等。 */
class QualificationOcrReviewTest {

  @Test
  void 具名复核后可持久化读回() {
    FakeRepo repo = new FakeRepo();
    QualificationApplicationService service = new QualificationApplicationService(repo);
    Long id = repo.save(seed(1L, "s-1"));
    Date now = new Date();

    service.reviewOcrByHuman(1L, id, "alice", true, now);

    Qualification reloaded = repo.findById(1L, id).orElseThrow();
    assertThat(reloaded.status()).isEqualTo(VerifyStatus.VERIFIED);
    assertThat(reloaded.getReviewedBy()).isEqualTo("alice");
  }

  @Test
  void 缺租户时拒绝() {
    FakeRepo repo = new FakeRepo();
    QualificationApplicationService service = new QualificationApplicationService(repo);
    Long id = repo.save(seed(1L, "s-1"));

    assertThatThrownBy(() -> service.reviewOcrByHuman(null, id, "alice", true, new Date()))
        .as("缺租户必须 fail-closed")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 跨租户复核被拒() {
    FakeRepo repo = new FakeRepo();
    QualificationApplicationService service = new QualificationApplicationService(repo);
    Long id = repo.save(seed(1L, "s-1"));

    assertThatThrownBy(() -> service.reviewOcrByHuman(2L, id, "bob", true, new Date()))
        .as("跨租户不得覆写")
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(repo.findById(1L, id).orElseThrow().getReviewedBy()).isNull();
  }

  @Test
  void 重复提交幂等不新增记录() {
    FakeRepo repo = new FakeRepo();
    QualificationApplicationService service = new QualificationApplicationService(repo);
    Long id = repo.save(seed(1L, "s-1"));
    Date now = new Date();

    service.reviewOcrByHuman(1L, id, "alice", true, now);
    service.reviewOcrByHuman(1L, id, "alice", true, now);

    assertThat(repo.saveCount()).as("重复复核不得再次持久化").isEqualTo(2); // 1 submit + 1 review
    assertThat(repo.findById(1L, id).orElseThrow().getReviewedBy()).isEqualTo("alice");
  }

  private static Qualification seed(Long tenantId, String supplierId) {
    return Qualification.submit(
        tenantId,
        supplierId,
        "BUSINESS_LICENSE",
        "****1234",
        new Date(System.currentTimeMillis() + 864000000L),
        "ref://1",
        "{\"ok\":true}");
  }

  /** 轻量内存仓储替身，只实现本测试所需方法。 */
  private static class FakeRepo implements QualificationRepository {
    private final List<Qualification> items = new ArrayList<>();
    private int saveCount = 0;

    @Override
    public Long save(Qualification q) {
      saveCount++;
      if (q.getId() == null) {
        q.setId((long) (items.size() + 1));
        items.add(q);
      }
      return q.getId();
    }

    @Override
    public Long save(Onboarding o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Onboarding> findInReview(Long t, String s) {
      return Optional.empty();
    }

    @Override
    public Optional<Onboarding> findByNo(Long t, String n) {
      return Optional.empty();
    }

    @Override
    public List<Qualification> findExpiring(Long t) {
      return List.of();
    }

    @Override
    public Optional<Qualification> findById(Long tenantId, Long id) {
      return items.stream()
          .filter(q -> id.equals(q.getId()) && tenantId.equals(q.getTenantId()))
          .findFirst();
    }

    int saveCount() {
      return saveCount;
    }
  }
}
