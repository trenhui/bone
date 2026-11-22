package com.bone.claim.domain.model.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 报案号生成器单测：格式契约 + 与赔案号不冲突 + 双射零碰撞 + 校验位 + 并发安全。 */
class ReportNoGeneratorTest {

  private static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 22);

  @Test
  @DisplayName("格式契约：RP + yyyyMMdd + 13位Base32 + 1位校验 = 24字符")
  void formatContract() {
    String reportNo = ReportNoGenerator.generate(76102367333449728L, FIXED_DATE);

    assertThat(reportNo).hasSize(ReportNoGenerator.TOTAL_LEN);
    assertThat(ReportNoGenerator.TOTAL_LEN).isEqualTo(24);
    assertThat(reportNo).startsWith("RP20261122");
    assertThat(reportNo.substring(2, 10)).isEqualTo("20261122");
    assertThat(reportNo).matches("RP[0-9]{8}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{14}");
    assertThat(ReportNoGenerator.isValid(reportNo)).isTrue();
  }

  @Test
  @DisplayName("号码空间隔离：同雪花 ID 同日期，报案号(RP)与赔案号(CL)不冲突，且互不被对方 isValid 接受")
  void distinctFromClaimNo() {
    long id = 76102367333449728L;
    String reportNo = ReportNoGenerator.generate(id, FIXED_DATE);
    String claimNo = ClaimNoGenerator.generate(id, FIXED_DATE);

    assertThat(reportNo).isNotEqualTo(claimNo);
    assertThat(reportNo).startsWith("RP");
    assertThat(claimNo).startsWith("CL");
    assertThat(ClaimNoGenerator.isValid(reportNo)).isFalse();
    assertThat(ReportNoGenerator.isValid(claimNo)).isFalse();
  }

  @Test
  @DisplayName("双射 round-trip：任意雪花 ID → 报案号 → 还原报案 ID（10000 样本）")
  void bijectionRoundTrip() {
    long[] samples =
        LongStream.concat(
                LongStream.of(1L, 4095L, 4096L, Long.MAX_VALUE),
                LongStream.rangeClosed(1, 9996).map(i -> i * 7919L))
            .toArray();
    for (long id : samples) {
      String reportNo = ReportNoGenerator.generate(id, FIXED_DATE);
      assertThat(ReportNoGenerator.extractSnowflakeId(reportNo))
          .as("id=%d 的 round-trip 必须还原", id)
          .isEqualTo(id);
    }
  }

  @Test
  @DisplayName("全局唯一：连续 50000 个 ID 生成 50000 个不同报案号（零碰撞）")
  void noCollisionInBulk() {
    Set<String> numbers = new HashSet<>();
    for (long id = 1; id <= 50_000; id++) {
      numbers.add(ReportNoGenerator.generate(id, FIXED_DATE));
    }
    assertThat(numbers).hasSize(50_000);
  }

  @Test
  @DisplayName("校验位检出单字符篡改：任改一位 → isValid=false")
  void detectsSingleCharCorruption() {
    String valid = ReportNoGenerator.generate(987654321L, FIXED_DATE);
    for (int i = 0; i < valid.length(); i++) {
      char original = valid.charAt(i);
      char tampered = original == 'Z' ? 'Y' : 'Z';
      String bad = valid.substring(0, i) + tampered + valid.substring(i + 1);
      assertThat(ReportNoGenerator.isValid(bad)).as("第 %d 位被篡改应判非法：%s", i, bad).isFalse();
    }
  }

  @Test
  @DisplayName("非法输入拒绝：非正数 ID / null 日期 / 非法号码")
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> ReportNoGenerator.generate(0L, FIXED_DATE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReportNoGenerator.generate(-1L, FIXED_DATE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReportNoGenerator.generate(1L, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(ReportNoGenerator.isValid(null)).isFalse();
    assertThat(ReportNoGenerator.isValid("RP20261122")).isFalse();
    assertThatThrownBy(() -> ReportNoGenerator.extractSnowflakeId("RP20261122BADCODE1X"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("并发安全：16 线程 × 3000 个 ID，零重号且全部可还原")
  void concurrentGenerationIsUnique() throws Exception {
    int threads = 16;
    int perThread = 3000;
    AtomicLong idSource = new AtomicLong(1);
    Set<String> all = ConcurrentHashMap.newKeySet();
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    Throwable[] failure = new Throwable[1];

    for (int t = 0; t < threads; t++) {
      pool.submit(
          () -> {
            try {
              start.await();
              for (int i = 0; i < perThread; i++) {
                long id = idSource.getAndIncrement();
                all.add(ReportNoGenerator.generate(id, FIXED_DATE));
              }
            } catch (Throwable e) {
              synchronized (failure) {
                failure[0] = e;
              }
            }
          });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    assertThat(failure[0]).isNull();
    assertThat(all).hasSize(threads * perThread);
  }
}
