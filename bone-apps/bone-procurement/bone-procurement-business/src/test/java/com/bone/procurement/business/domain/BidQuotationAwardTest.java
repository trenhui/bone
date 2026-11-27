package com.bone.procurement.business.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.procurement.business.InMemoryBusinessRepository;
import com.bone.procurement.business.domain.model.business.Award;
import com.bone.procurement.business.domain.model.business.Bid;
import com.bone.procurement.business.domain.model.business.BidStatus;
import com.bone.procurement.business.domain.model.business.Quotation;
import com.bone.procurement.business.domain.model.business.QuotationComparison;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BidQuotationAwardTest {

  private static final Date NOW = Date.from(Instant.parse("2026-11-26T00:00:00Z"));

  @Test
  @DisplayName("到期后提交被拒，投标保持 DRAFT")
  void submitAfterDeadlineRejected() {
    Bid bid =
        Bid.draft(
            1L,
            "BID-1",
            "RFX-1",
            "SUP-1",
            "SRC-1",
            Date.from(Instant.parse("2026-11-25T23:59:59Z")));

    assertThatThrownBy(() -> bid.submit(NOW)).isInstanceOf(IllegalStateException.class);
    assertThat(bid.status()).isEqualTo(BidStatus.DRAFT);
  }

  @Test
  @DisplayName("同一租户同一 RFx 下重复供应商投标被拒")
  void duplicateSupplierRejected() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    Bid first = Bid.draft(1L, "BID-1", "RFX-1", "SUP-1", "SRC-1", plusDays(1)).submit(NOW);
    repo.saveBid(first);

    Bid second = Bid.draft(1L, "BID-2", "RFX-1", "SUP-1", "SRC-2", plusDays(1));
    assertThatThrownBy(() -> repo.saveBid(second)).isInstanceOf(IllegalArgumentException.class);
    assertThat(repo.findBid(1L, "RFX-1", "SUP-1")).contains(first);
  }

  @Test
  @DisplayName("同一来源引用重复投递幂等，不新增投标事实")
  void sourceRefDuplicateIdempotent() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    Bid first = Bid.draft(1L, "BID-1", "RFX-1", "SUP-1", "SRC-SAME", plusDays(1)).submit(NOW);
    repo.saveBid(first);

    Bid repeated = Bid.draft(1L, "BID-2", "RFX-1", "SUP-1", "SRC-SAME", plusDays(1)).submit(NOW);
    assertThat(repo.findBidBySourceRef(1L, "SRC-SAME")).contains(first);
    assertThat(repo.findBids(1L, "RFX-1")).hasSize(1);
    assertThat(repeated.bidId()).isEqualTo("BID-2");
  }

  @Test
  @DisplayName("撤回态不得恢复提交")
  void withdrawnCannotResubmit() {
    Bid bid = Bid.draft(1L, "BID-3", "RFX-1", "SUP-2", "SRC-3", plusDays(1)).withdraw();

    assertThatThrownBy(() -> bid.submit(NOW)).isInstanceOf(IllegalStateException.class);
    assertThat(bid.status()).isEqualTo(BidStatus.WITHDRAWN);
  }

  @Test
  @DisplayName("过期报价与币种不一致报价标记为不可比，AI 推荐仅作建议")
  void quotationComparabilityAndAiBoundary() {
    List<Quotation> quotes =
        List.of(
            new Quotation(
                1L,
                "Q-1",
                "RFX-1",
                "SUP-1",
                new BigDecimal("100"),
                "CNY",
                minusDays(1),
                new BigDecimal("0.99"),
                "FIRST"),
            new Quotation(
                1L,
                "Q-2",
                "RFX-1",
                "SUP-2",
                new BigDecimal("90"),
                "USD",
                plusDays(1),
                new BigDecimal("0.95"),
                "SECOND"),
            new Quotation(
                1L,
                "Q-3",
                "RFX-1",
                "SUP-3",
                new BigDecimal("120"),
                "CNY",
                plusDays(1),
                new BigDecimal("0.60"),
                null));

    List<QuotationComparison> comparisons = QuotationComparison.compare(quotes, "CNY", NOW);

    assertThat(comparisons).hasSize(3);
    assertThat(comparisons)
        .filteredOn(c -> c.quoteId().equals("Q-1"))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.comparable()).isFalse();
              assertThat(c.reasonCode()).isEqualTo("EXPIRED");
            });
    assertThat(comparisons)
        .filteredOn(c -> c.quoteId().equals("Q-2"))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.comparable()).isFalse();
              assertThat(c.reasonCode()).isEqualTo("CURRENCY_MISMATCH");
            });
    assertThat(comparisons)
        .filteredOn(c -> c.quoteId().equals("Q-3"))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.comparable()).isTrue();
              assertThat(c.aiRecommendation()).isNull();
            });
  }

  @Test
  @DisplayName("匿名、空白操作人与 AI 建议都不得授标")
  void aiAndAnonymousAwardRejected() {
    assertThatThrownBy(() -> Award.decide(1L, "RFX-1", "SUP-1", null, "reason", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Award.decide(1L, "RFX-1", "SUP-1", "  ", "reason", NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Award.decide(1L, "RFX-1", "SUP-1", "AI_SUGGESTION", "reason", NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("具名人工授标留痕且终态不可回写")
  void namedAwardRecordedAndTerminal() {
    InMemoryBusinessRepository repo = new InMemoryBusinessRepository();
    Award award = Award.decide(1L, "RFX-1", "SUP-1", "alice", "价格与资质综合最佳", NOW);
    repo.saveAward(award);

    assertThat(award.confirmedBy()).isEqualTo("alice");
    assertThat(award.reason()).isEqualTo("价格与资质综合最佳");
    assertThat(award.confirmedAt()).isEqualTo(NOW);
    Award rewritten = Award.decide(1L, "RFX-1", "SUP-2", "bob", "改授他人", NOW);
    assertThatThrownBy(() -> repo.saveAward(rewritten))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("终态");
    assertThatThrownBy(award::assertNotRewritable).isInstanceOf(IllegalStateException.class);
    assertThat(repo.findAward(1L, "RFX-1")).contains(award);
  }

  private static Date plusDays(int days) {
    return Date.from(NOW.toInstant().plusSeconds(86_400L * days));
  }

  private static Date minusDays(int days) {
    return Date.from(NOW.toInstant().minusSeconds(86_400L * days));
  }
}
