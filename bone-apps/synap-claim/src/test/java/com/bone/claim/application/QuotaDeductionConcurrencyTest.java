package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.quota.QuotaAccount;
import com.bone.claim.domain.repository.QuotaAccountRepository;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 同账户并发扣减的线程安全回归测试（P0-1：单写者串行队列 / striped lock）。
 *
 * <p><b>为什么存在</b>：额度扣减是「读账户 → 改三值余额 → 保存」的高冲突写。此前依赖 {@code @Version} 乐观锁，同一被保人账户被多笔案件并发占额时，落后方会因
 * {@code OptimisticLockException} 回滚并重试， 峰值下易撞「重试≤3 次转人工」阈值。P0-1 在应用层对同一账户加 striped 锁串行化，把单副本内同账户并发
 * 退化为串行，消除该冲突风暴。
 *
 * <p><b>本测试验证什么</b>：N 个<b>不同案件</b>（各自独立幂等键）并发对同一共享账户调用 {@code reserve}， 锁保证每笔占用完整落账——最终 {@code
 * frozen = N × amount}（无丢失更新），且 {@code available ≥ 0}（无超赔）。 若有人移除账户锁，本测试会红：多现场同时读到过期余额、互相覆盖，{@code
 * frozen} 远小于 {@code N × amount}。
 *
 * <p><b>边界</b>：本测试用共享内存账户 + mock 仓储，验证的是<b>锁对应用层共享状态的串行化</b>； 真正的多副本 DB 级并发正确性（跨 JVM 行锁/死锁/吞吐）由
 * golive.12（G16）压测报告覆盖，不在本单元测试范围。
 */
@ExtendWith(MockitoExtension.class)
class QuotaDeductionConcurrencyTest {

  @Mock QuotaAccountRepository quotaAccountRepository;

  @InjectMocks QuotaApplicationService service;

  @Test
  void concurrentReserveOnSameAccount_noLostUpdate_noOverOccupancy() throws Exception {
    // 同一被保人账户，初始额度 100000；50 笔独立案件各占额 1000。
    QuotaAccount account =
        QuotaAccount.open(
            1L, 1L, "hash-conc", "POL-CONC", 10L, 2026, "PERSONAL", "", new BigDecimal("100000"));
    when(quotaAccountRepository.findAccount(
            anyString(), anyString(), anyLong(), anyInt(), anyString(), anyString()))
        .thenReturn(account);
    when(quotaAccountRepository.findTransactions(anyLong())).thenReturn(Collections.emptyList());

    int n = 50;
    BigDecimal amount = new BigDecimal("1000");
    int threads = 16;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(n);
    AtomicInteger failures = new AtomicInteger();
    for (int i = 0; i < n; i++) {
      final int idx = i;
      pool.submit(
          () -> {
            try {
              start.await();
              service.reserve(
                  1L,
                  "hash-conc",
                  "POL-CONC",
                  10L,
                  2026,
                  1L,
                  "CLAIM-" + idx,
                  amount,
                  "SET-" + idx,
                  1);
            } catch (Exception e) {
              failures.incrementAndGet();
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();
    assertThat(failures.get()).isZero();

    // 无丢失更新：N 笔独立占额全部落账。
    assertThat(account.getQuotaFrozen())
        .isEqualByComparingTo(amount.multiply(BigDecimal.valueOf(n)));
    // 无超赔：可用额度恒非负。
    assertThat(account.available()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
  }
}
