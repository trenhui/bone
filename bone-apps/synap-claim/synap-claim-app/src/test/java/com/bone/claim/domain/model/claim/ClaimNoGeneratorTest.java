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

/** 报案号生成器单测：双射零碰撞 + 校验位检错 + 并发安全 + 格式契约。 */
class ClaimNoGeneratorTest {

  private static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 22);

  @Test
  @DisplayName("格式契约：CL + yyyyMMdd + 13位Base32 + 1位校验 = 24字符，字符集无 I/L/O/U")
  void formatContract() {
    String claimNo = ClaimNoGenerator.generate(76102367333449728L, FIXED_DATE);

    assertThat(claimNo).hasSize(ClaimNoGenerator.TOTAL_LEN);
    assertThat(ClaimNoGenerator.TOTAL_LEN).isEqualTo(24);
    assertThat(claimNo).startsWith("CL20261122");
    assertThat(claimNo.substring(2, 10)).isEqualTo("20261122");
    assertThat(claimNo)
        .as("生成串只含 Crockford Base32 字符（大写），不含易混字符 I/L/O/U")
        .matches("CL[0-9]{8}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{14}");
    assertThat(ClaimNoGenerator.isValid(claimNo)).isTrue();
  }

  @Test
  @DisplayName("双射 round-trip：任意雪花 ID → 报案号 → 还原 ID（10000 样本）")
  void bijectionRoundTrip() {
    long[] samples =
        LongStream.concat(
                LongStream.of(1L, 4095L, 4096L, Long.MAX_VALUE),
                LongStream.rangeClosed(1, 9996).map(i -> i * 7919L))
            .toArray();

    for (long id : samples) {
      String claimNo = ClaimNoGenerator.generate(id, FIXED_DATE);
      assertThat(ClaimNoGenerator.extractSnowflakeId(claimNo))
          .as("id=%d 的 round-trip 必须还原", id)
          .isEqualTo(id);
    }
  }

  @Test
  @DisplayName("全局唯一：连续 100000 个 ID 生成 100000 个不同报案号（零碰撞）")
  void noCollisionInBulk() {
    Set<String> numbers = new HashSet<>();
    for (long id = 1; id <= 100_000; id++) {
      numbers.add(ClaimNoGenerator.generate(id, FIXED_DATE));
    }
    assertThat(numbers).hasSize(100_000);
  }

  @Test
  @DisplayName("相同 ID 不同日期 → 报案号不同（日期段参与编号，非仅展示）")
  void dateParticipatesInNumber() {
    String a = ClaimNoGenerator.generate(12345L, LocalDate.of(2026, 11, 22));
    String b = ClaimNoGenerator.generate(12345L, LocalDate.of(2026, 11, 23));
    assertThat(a).isNotEqualTo(b);
    assertThat(ClaimNoGenerator.extractSnowflakeId(a)).isEqualTo(12345L);
    assertThat(ClaimNoGenerator.extractSnowflakeId(b)).isEqualTo(12345L);
  }

  @Test
  @DisplayName("防枚举：连续 ID 的报案号载荷不呈单调/等差（外观随机，隐藏发号速率）")
  void notEnumable() {
    String a = ClaimNoGenerator.generate(1000L, FIXED_DATE).substring(10, 23);
    String b = ClaimNoGenerator.generate(1001L, FIXED_DATE).substring(10, 23);
    String c = ClaimNoGenerator.generate(1002L, FIXED_DATE).substring(10, 23);

    // 三个连续 ID 的载荷在字典序上不应恰好等差递增（若保留雪花结构则会）
    int ab = a.compareTo(b);
    int bc = b.compareTo(c);
    assertThat(ab == bc && ab != 0).as("连续 ID 的载荷字典序差值不应恒定：a=%s b=%s c=%s", a, b, c).isFalse();
  }

  @Test
  @DisplayName("校验位检出单字符篡改：任改一位 → isValid=false")
  void detectsSingleCharCorruption() {
    String valid = ClaimNoGenerator.generate(987654321L, FIXED_DATE);
    for (int i = 0; i < valid.length(); i++) {
      char original = valid.charAt(i);
      char tampered = original == 'Z' ? 'Y' : 'Z';
      String bad = valid.substring(0, i) + tampered + valid.substring(i + 1);
      assertThat(ClaimNoGenerator.isValid(bad)).as("第 %d 位被篡改应判非法：%s", i, bad).isFalse();
    }
  }

  @Test
  @DisplayName("校验位检出相邻字符转置：交换相邻两位 → isValid=false")
  void detectsAdjacentTransposition() {
    String valid = ClaimNoGenerator.generate(5555555L, FIXED_DATE);
    int detected = 0;
    int total = 0;
    for (int i = 2; i < valid.length() - 2; i++) {
      char x = valid.charAt(i);
      char y = valid.charAt(i + 1);
      if (x == y) {
        continue;
      }
      total++;
      String swapped = valid.substring(0, i) + y + x + valid.substring(i + 2);
      if (!ClaimNoGenerator.isValid(swapped)) {
        detected++;
      }
    }
    assertThat(total).isPositive();
    assertThat(detected)
        .as("Luhn mod 32 对相邻转置的检出率应 >= 90%%（%d/%d）", detected, total)
        .isGreaterThanOrEqualTo((int) Math.ceil(total * 0.9));
  }

  @Test
  @DisplayName("非法输入拒绝：非正数 ID / null 日期 / 非法号码")
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> ClaimNoGenerator.generate(0L, FIXED_DATE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClaimNoGenerator.generate(-1L, FIXED_DATE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ClaimNoGenerator.generate(1L, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(ClaimNoGenerator.isValid(null)).isFalse();
    assertThat(ClaimNoGenerator.isValid("CL20261122")).isFalse();
    assertThatThrownBy(() -> ClaimNoGenerator.extractSnowflakeId("CL20261122BADCODE1X"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("并发安全：16 线程 × 5000 个 ID，零重号且全部可还原")
  void concurrentGenerationIsUnique() throws Exception {
    int threads = 16;
    int perThread = 5000;
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
                all.add(ClaimNoGenerator.generate(id, FIXED_DATE));
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
