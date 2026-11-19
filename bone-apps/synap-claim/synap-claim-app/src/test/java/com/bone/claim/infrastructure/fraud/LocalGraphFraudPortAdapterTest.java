package com.bone.claim.infrastructure.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.GraphFraudPort.FraudResult;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 本地图计算反欺诈适配器的判别力测试。
 *
 * <p>目的：证明这是一个<b>真实执行筛查</b>的实现，而非又一个恒返回 {@code (false,null,false)} 的桩。锁定的不变量：
 *
 * <ol>
 *   <li>有仓储 → {@code available=true}（本次真的查了图），桩构造 → {@code available=false}（诚实降级）；
 *   <li>发票跨案 / 同证件高频两个图信号各自能独立命中，且命中时给出确定性、假名化的 {@code clusterId}；
 *   <li>排除本案自身（否则 100% 误报）、时间窗外不计入（变异：去掉窗口过滤必须被抓红）；
 *   <li>确定性：同样输入两次 {@code clusterId} 必须一致，且不含明文锚点；
 *   <li>命中只表达为风险信号，适配器本身不触碰金额（P1「AI 不碰钱」由签名与断言共同钉住）。
 * </ol>
 */
class LocalGraphFraudPortAdapterTest {

  private static final long TENANT = 7L;
  private static final long SELF_ID = 1001L;
  private static final Instant NOW = Instant.parse("2026-11-19T02:00:00Z");

  private static ClaimContextPort.ClaimContextView ctx() {
    return new ClaimContextPort.ClaimContextView(
        SELF_ID,
        "OUTPATIENT",
        new BigDecimal("800.00"),
        true,
        true,
        new BigDecimal("0.95"),
        true,
        List.of());
  }

  /**
   * 造一个只有图锚点字段的赔案（mock 适配器实际读取的 getter，不碰领域构造不变量）。
   *
   * <p>外层仓储 stub 一律用 {@code doReturn().when()}：本方法内部含 {@code when()} 调用， 若嵌在 {@code
   * thenReturn(claim(...))} 里会触发 Mockito 的 UnfinishedStubbing 守卫。
   */
  private static ClaimCase claim(
      long id, String insuredHash, String invCode, String invNo, Instant createdAt) {
    ClaimCase c = mock(ClaimCase.class);
    when(c.getId()).thenReturn(id);
    when(c.getTenantId()).thenReturn(TENANT);
    when(c.getInsuredIdHash()).thenReturn(insuredHash);
    when(c.getInvoiceCode()).thenReturn(invCode);
    when(c.getInvoiceNo()).thenReturn(invNo);
    when(c.getCreatedAt()).thenReturn(createdAt);
    return c;
  }

  @Nested
  @DisplayName("可用性语义")
  class Availability {

    @Test
    @DisplayName("桩构造：无仓储 → available=false，绝不伪装无风险")
    void stubConstructionIsUnavailable() {
      FraudResult r = new LocalGraphFraudPortAdapter().screen(ctx());
      assertThat(r.available()).isFalse();
      assertThat(r.hit()).isFalse();
      assertThat(r.clusterId()).isNull();
    }

    @Test
    @DisplayName("本案查不到 → available=false（不可用≠无团伙）")
    void missingSelfIsUnavailable() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(null).when(repo).findById(SELF_ID);
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.available()).isFalse();
      assertThat(r.hit()).isFalse();
    }

    @Test
    @DisplayName("有仓储且无命中 → available=true、hit=false（真实执行了筛查）")
    void cleanCaseIsAvailableAndNotHit() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-self", "14403", "00000001", NOW)).when(repo).findById(SELF_ID);
      doReturn(List.of()).when(repo).findByInvoice(anyLong(), anyString(), anyString());
      doReturn(List.of()).when(repo).findByInsuredIdHash(anyLong(), anyString());
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.available()).isTrue();
      assertThat(r.hit()).isFalse();
      assertThat(r.clusterId()).isNull();
    }
  }

  @Nested
  @DisplayName("图信号判别力")
  class Signals {

    @Test
    @DisplayName("发票跨案共用（R-FRAUD-07）→ hit，clusterId 以 INV- 开头")
    void invoiceSharedAcrossCasesHits() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-self", "14403", "00000001", NOW)).when(repo).findById(SELF_ID);
      doReturn(List.of(claim(2002L, "ins-other", "14403", "00000001", NOW)))
          .when(repo)
          .findByInvoice(TENANT, "14403", "00000001");
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.available()).isTrue();
      assertThat(r.hit()).isTrue();
      assertThat(r.clusterId()).startsWith("INV-");
    }

    @Test
    @DisplayName("同证件短时高频（R-FRAUD-22）达到阈值 → hit，clusterId 以 INS- 开头")
    void sameInsuredHighFrequencyHits() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-freq", null, null, NOW)).when(repo).findById(SELF_ID);
      doReturn(List.of()).when(repo).findByInvoice(anyLong(), anyString(), anyString());
      doReturn(
              List.of(
                  claim(2002L, "ins-freq", null, null, NOW.minus(5, ChronoUnit.DAYS)),
                  claim(2003L, "ins-freq", null, null, NOW.minus(10, ChronoUnit.DAYS))))
          .when(repo)
          .findByInsuredIdHash(TENANT, "ins-freq");
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.available()).isTrue();
      assertThat(r.hit()).isTrue();
      assertThat(r.clusterId()).startsWith("INS-");
    }

    @Test
    @DisplayName("仅一起关联案件（未达阈值）→ 不命中")
    void singleRelatedCaseDoesNotHit() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-freq", null, null, NOW)).when(repo).findById(SELF_ID);
      doReturn(List.of(claim(2002L, "ins-freq", null, null, NOW.minus(5, ChronoUnit.DAYS))))
          .when(repo)
          .findByInsuredIdHash(TENANT, "ins-freq");
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.hit()).isFalse();
    }

    @Test
    @DisplayName("排除本案自身：反查结果只有本案 → 不命中（否则 100% 误报）")
    void excludesSelfFromPeerCount() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      ClaimCase self = claim(SELF_ID, "ins-self", "14403", "00000001", NOW);
      doReturn(self).when(repo).findById(SELF_ID);
      doReturn(List.of(self)).when(repo).findByInvoice(TENANT, "14403", "00000001");
      doReturn(List.of(self)).when(repo).findByInsuredIdHash(TENANT, "ins-self");
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.hit()).isFalse();
    }

    @Test
    @DisplayName("时间窗外的同证件案件不计入（变异：删除窗口过滤本用例必红）")
    void outsideWindowDoesNotCount() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-old", null, null, NOW)).when(repo).findById(SELF_ID);
      doReturn(
              List.of(
                  claim(2002L, "ins-old", null, null, NOW.minus(200, ChronoUnit.DAYS)),
                  claim(2003L, "ins-old", null, null, NOW.minus(300, ChronoUnit.DAYS))))
          .when(repo)
          .findByInsuredIdHash(TENANT, "ins-old");
      FraudResult r = new LocalGraphFraudPortAdapter(repo).screen(ctx());
      assertThat(r.hit()).isFalse();
    }
  }

  @Nested
  @DisplayName("团伙标识与红线")
  class ClusterAndRedline {

    @Test
    @DisplayName("clusterId 确定性可复现，且不含明文锚点")
    void clusterIdIsDeterministicAndPseudonymized() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      doReturn(claim(SELF_ID, "ins-self", "14403", "00000001", NOW)).when(repo).findById(SELF_ID);
      doReturn(List.of(claim(2002L, "ins-other", "14403", "00000001", NOW)))
          .when(repo)
          .findByInvoice(TENANT, "14403", "00000001");
      LocalGraphFraudPortAdapter adapter = new LocalGraphFraudPortAdapter(repo);
      String first = adapter.screen(ctx()).clusterId();
      String second = adapter.screen(ctx()).clusterId();
      assertThat(first).isEqualTo(second);
      assertThat(first).doesNotContain("14403").doesNotContain("00000001");
    }

    @Test
    @DisplayName("部署态声明：有仓储报本地能力 READY，桩构造回落 UNKNOWN（不冒充 E2 平台图库）")
    void declarationHonest() {
      ClaimCaseRepository repo = mock(ClaimCaseRepository.class);
      assertThat(new LocalGraphFraudPortAdapter(repo).declaration().real()).isTrue();
      assertThat(new LocalGraphFraudPortAdapter(repo).declaration().capabilityId())
          .isEqualTo(LocalGraphFraudPortAdapter.CAPABILITY_ID);
      assertThat(new LocalGraphFraudPortAdapter().declaration().real()).isFalse();
    }
  }
}
