package com.bone.claim.infrastructure.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.adjustment.Accumulation;
import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 累计读侧适配器（⑩）测试：验证它读的是<b>真实余额</b>而非配置常量。
 *
 * <p>本类针对最容易写成"假绿"的一类实现——适配器永远返回 {@code (0,0,true)}：所有单案测试都绿，生产次案一律超赔。 因此每个用例都断言"账本变了，读出必须随之变"，
 * 而不是只断言返回对象非空。
 */
@DisplayName("累计读侧适配器（QuotaDrivenAccumulationPort）")
class QuotaDrivenAccumulationPortAdapterTest {

  private static final long TENANT = 42L;
  private static final String INSURED = "h-insured";
  private static final String POLICY = "P-1";
  private static final long PLAN = 7L;
  private static final int YEAR = 2026;
  private static final String LIABILITY = "LIAB-UUID-1";

  private QuotaAccountRepository repo;
  private QuotaDrivenAccumulationPortAdapter adapter;

  @BeforeEach
  void setUp() {
    repo = mock(QuotaAccountRepository.class);
    adapter = new QuotaDrivenAccumulationPortAdapter(repo);
  }

  @Test
  @DisplayName("未开责任账 → MISSING（不得返回『已用 0、可用』，否则次案按满额赔）")
  void missingAccountYieldsMissing() {
    when(repo.findAccount(anyString(), anyString(), eq(PLAN), eq(YEAR), anyString(), anyString()))
        .thenReturn(null);

    Accumulation acc = load();

    assertThat(acc.available()).isFalse();
    assertThat(acc.deductibleUsed()).isEqualByComparingTo("0.00");
    assertThat(acc.limitUsed()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("按 LIABILITY_CAP + liability_uuid 维度查（查错维度即读写两本账）")
  void queriesByLiabilityDimension() {
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", LIABILITY))
        .thenReturn(account("LIABILITY_CAP", LIABILITY, new BigDecimal("100000.00")));

    assertThat(load().available()).isTrue();
  }

  @Test
  @DisplayName("责任限额已占用 = claimed + frozen（只算 claimed 会让两案先后理算都看到『零占用』）")
  void limitUsedIncludesFrozen() {
    QuotaAccount account = account("LIABILITY_CAP", LIABILITY, new BigDecimal("100000.00"));
    account.reserveSoft(1L, new BigDecimal("3000.00"), 1001L, "SO1", "CL001:LIABILITY_SOFT:1");
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", LIABILITY))
        .thenReturn(account);

    assertThat(load().limitUsed())
        .as("软占 3000 必须立刻计入已占用，否则并发的第二案看不到占用")
        .isEqualByComparingTo("3000.00");

    account.confirm(2L, new BigDecimal("3000.00"), 1001L, "SO1", "CL001:LIABILITY_PAID:1");

    assertThat(load().limitUsed())
        .as("frozen→claimed 是内部转移，总额不得变化（变了就是双记）")
        .isEqualByComparingTo("3000.00");
  }

  @Test
  @DisplayName("年度免赔已消抵取自账户投影（A7 回写后次案读得到）")
  void deductibleUsedComesFromAccount() {
    QuotaAccount account = account("LIABILITY_CAP", LIABILITY, new BigDecimal("100000.00"));
    account.accumulateDeductible(1L, new BigDecimal("8000.00"), 1001L, "SO1", "CL001:DED:1");
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", LIABILITY))
        .thenReturn(account);

    Accumulation acc = load();

    assertThat(acc.deductibleUsed()).isEqualByComparingTo("8000.00");
    assertThat(acc.remainingDeductible(new BigDecimal("10000.00")))
        .as("剩余免赔 = 配置 − 已消抵")
        .isEqualByComparingTo("2000.00");
  }

  @Test
  @DisplayName("不同责任分行互不干扰（A 责任用满不影响 B 责任）")
  void liabilityRowsAreIsolated() {
    QuotaAccount a = account("LIABILITY_CAP", "LIAB-A", new BigDecimal("100000.00"));
    a.reserveSoft(1L, new BigDecimal("10000.00"), 1001L, "SO1", "CL001:LIABILITY_SOFT:1");
    QuotaAccount b = account("LIABILITY_CAP", "LIAB-B", new BigDecimal("100000.00"));
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", "LIAB-A")).thenReturn(a);
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", "LIAB-B")).thenReturn(b);

    assertThat(adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, "LIAB-A", null).limitUsed())
        .isEqualByComparingTo("10000.00");
    assertThat(adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, "LIAB-B", null).limitUsed())
        .isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("liabilityUuid 为 null 时按空串定位（与写侧 loadLiability 同口径，避免读写两本账）")
  void nullLiabilityUuidFallsBackToEmptyString() {
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", ""))
        .thenReturn(account("LIABILITY_CAP", "", new BigDecimal("100000.00")));

    assertThat(adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, null, null).available()).isTrue();
  }

  @Test
  @DisplayName("日用量按业务日聚合 DAILY 流水（Q-05）：同日加、跨日不串、回退减")
  void dailyUsageAggregatesByBizDate() {
    QuotaAccount account = account("LIABILITY_CAP", LIABILITY, new BigDecimal("100000.00"));
    java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 1);
    java.time.LocalDate tomorrow = java.time.LocalDate.of(2026, 10, 2);
    account.accumulateDaily(1L, new BigDecimal("120.00"), today, 1001L, "SO1", "CL1:DAILY:1");
    account.accumulateDaily(2L, new BigDecimal("80.00"), today, 1002L, "SO2", "CL2:DAILY:1");
    account.accumulateDaily(3L, new BigDecimal("500.00"), tomorrow, 1003L, "SO3", "CL3:DAILY:1");
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", LIABILITY))
        .thenReturn(account);
    when(repo.findTransactions(2L)).thenReturn(account.getTransactions());

    Accumulation todayAcc = adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, LIABILITY, today);
    Accumulation tomorrowAcc =
        adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, LIABILITY, tomorrow);

    assertThat(todayAcc.dailyUsed())
        .as("同一天两笔必须相加：120 + 80 = 200；若为 120 说明只取了第一笔")
        .isEqualByComparingTo("200.00");
    assertThat(todayAcc.dailyWindow()).isEqualTo(today);
    assertThat(tomorrowAcc.dailyUsed())
        .as("跨日必须归零重算：10-01 的 200 不得算进 10-02")
        .isEqualByComparingTo("500.00");
    assertThat(todayAcc.remainingDaily(new BigDecimal("300.00")))
        .as("当日剩余 = 日限额 − 当日已用 = 300 − 200")
        .isEqualByComparingTo("100.00");
  }

  @Test
  @DisplayName("日用量回退后必须减掉（撤销/退款的当日额度要还回去，否则当天后续赔案被误拦）")
  void dailyRollbackReducesUsage() {
    QuotaAccount account = account("LIABILITY_CAP", LIABILITY, new BigDecimal("100000.00"));
    java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 1);
    account.accumulateDaily(1L, new BigDecimal("200.00"), today, 1001L, "SO1", "CL1:DAILY:1");
    account.reduceDaily(2L, new BigDecimal("200.00"), today, 1001L, "SO1", "CL1:DAILY_ROLLBACK:1");
    when(repo.findAccount(INSURED, POLICY, PLAN, YEAR, "LIABILITY_CAP", LIABILITY))
        .thenReturn(account);
    when(repo.findTransactions(2L)).thenReturn(account.getTransactions());

    assertThat(adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, LIABILITY, today).dailyUsed())
        .as("占用 200 后回退 200 ⇒ 当日已用回到 0")
        .isEqualByComparingTo("0.00");
  }

  private Accumulation load() {
    return adapter.load(TENANT, INSURED, POLICY, PLAN, YEAR, LIABILITY, null);
  }

  private static QuotaAccount account(String type, String liabilityUuid, BigDecimal initial) {
    return QuotaAccount.open(2L, TENANT, INSURED, POLICY, PLAN, YEAR, type, liabilityUuid, initial);
  }
}
