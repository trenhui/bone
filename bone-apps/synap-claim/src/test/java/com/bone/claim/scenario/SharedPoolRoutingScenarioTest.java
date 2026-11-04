package com.bone.claim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.claim.application.QuotaApplicationService;
import com.bone.claim.domain.model.quota.QuotaAccount;
import java.math.BigDecimal;
import java.time.Year;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 共享保额（共保代码）路由端到端：两个独立责任共享同一额度池。
 *
 * <p><b>核心设计约束「能配的必须能算」</b>：责任带 {@code sharedCode} 时，其责任账必须路由到 {@code SHARED_POOL} 维度（共保代码为分行键，复用
 * {@code liability_uuid} 列），而非各自独立的 {@code LIABILITY_CAP}。 否则「配置了共享却仍各算各的额度」会静默超赔——这正是本类要钉死的契约。
 *
 * <p><b>钉死的三类缺陷</b>：
 *
 * <ul>
 *   <li>路由错（走到 {@code LIABILITY_CAP}）⇒ 两责任各占各的池、互不看见占用 ⇒ 超赔；
 *   <li>读侧与写侧键不一致（{@code capSubKey} 推导不同）⇒ 占用记在 A 键、读成 B 键 ⇒ 已用恒 0 ⇒ 超赔；
 *   <li>释放越池（按池余额而非本单自有量）⇒ 释放 A 单却抽干 B 单的合法占用。
 * </ul>
 */
@DisplayName("共享保额路由（SHARED_POOL 共保代码）")
class SharedPoolRoutingScenarioTest {

  private static final String POLICY = "POL-SHARED-001";
  private static final long PLAN = 9201L;
  private static final String INSURED = "ins-shared";
  private static final String SHARED = "POOL-1";
  private static final String LIAB_A = "LIAB-A";
  private static final String LIAB_B = "LIAB-B";

  private ScenarioHarness h;
  private QuotaApplicationService quotaSvc;
  private int year;

  @BeforeEach
  void setUp() {
    h = new ScenarioHarness();
    quotaSvc = h.quotaSvc;
    year = Year.now().getValue();
    // 共享池账户：共保代码为分行键（复用 liability_uuid 列承载共保代码）
    h.quotaRepo.insert(
        QuotaAccount.open(
            4L,
            h.tenantId,
            INSURED,
            POLICY,
            PLAN,
            year,
            QuotaAccount.QUOTA_TYPE_SHARED_POOL,
            SHARED,
            new BigDecimal("20000")));
  }

  private QuotaAccount sharedPool() {
    return h.quotaRepo.findAccount(
        INSURED, POLICY, PLAN, year, QuotaAccount.QUOTA_TYPE_SHARED_POOL, SHARED);
  }

  private QuotaAccount liabilityCap(String liabilityUuid) {
    return h.quotaRepo.findAccount(
        INSURED, POLICY, PLAN, year, QuotaAccount.QUOTA_TYPE_LIABILITY_CAP, liabilityUuid);
  }

  @Test
  @DisplayName("两个独立责任共享同一共保池：占用必须叠加到同一 SHARED_POOL 账户，且不各自开 LIABILITY_CAP")
  void twoLiabilitiesShareSamePool() {
    quotaSvc.reserveLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        year,
        LIAB_A,
        SHARED,
        1L,
        "C1",
        new BigDecimal("6000"),
        BigDecimal.ZERO,
        "S1",
        1,
        null);
    quotaSvc.reserveLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        year,
        LIAB_B,
        SHARED,
        2L,
        "C2",
        new BigDecimal("6000"),
        BigDecimal.ZERO,
        "S2",
        1,
        null);

    QuotaAccount pool = sharedPool();
    assertNotNull(pool, "共保池账户必须存在");
    assertEquals(
        0,
        pool.getQuotaFrozen().compareTo(new BigDecimal("12000")),
        "两责任占用必须叠加到同一池（得到 6000 = 各算各的 LIABILITY_CAP / 路由错）");
    assertNull(liabilityCap(LIAB_A), "共享责任不得各自开 LIABILITY_CAP 账户");
    assertNull(liabilityCap(LIAB_B), "共享责任不得各自开 LIABILITY_CAP 账户");
  }

  @Test
  @DisplayName("释放其中一单：只归还本单自有量，共享池剩余占用不变为另一单的量")
  void releaseOneRestoresOnlyOwned() {
    quotaSvc.reserveLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        year,
        LIAB_A,
        SHARED,
        1L,
        "C1",
        new BigDecimal("6000"),
        BigDecimal.ZERO,
        "S1",
        1,
        null);
    quotaSvc.reserveLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        year,
        LIAB_B,
        SHARED,
        2L,
        "C2",
        new BigDecimal("6000"),
        BigDecimal.ZERO,
        "S2",
        1,
        null);

    // 撤销 C1（支付前撤销 ⇒ 释放预占），只还 C1 自有量
    quotaSvc.releaseLiability(
        h.tenantId,
        INSURED,
        POLICY,
        PLAN,
        year,
        LIAB_A,
        SHARED,
        1L,
        "C1",
        new BigDecimal("6000"),
        BigDecimal.ZERO,
        "S1",
        1);

    QuotaAccount pool = sharedPool();
    assertEquals(
        0,
        pool.getQuotaFrozen().compareTo(new BigDecimal("6000")),
        "释放 C1 后池内只剩 C2 的占用；回到 12000 = 释放越池（按池余额而非本单自有量）");
  }
}
