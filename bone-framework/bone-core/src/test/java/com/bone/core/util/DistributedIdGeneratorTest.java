package com.bone.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DistributedIdGenerator} 测试：ID 唯一性、流水号格式与入参校验。
 *
 * <p>ID 生成是全局基础能力，重复或格式错误会直接导致主键冲突 / 对账失败，故固化格式与唯一性。
 */
class DistributedIdGeneratorTest {

  @Test
  void generateUuid_isCompactLowerCaseWithoutDashes() {
    String uuid = DistributedIdGenerator.generateUuid();

    assertThat(uuid).hasSize(32).doesNotContain("-").isEqualTo(uuid.toLowerCase());
    assertThat(uuid).matches("[0-9a-f]{32}");
  }

  @Test
  void generateUuid_producesUniqueValues() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      ids.add(DistributedIdGenerator.generateUuid());
    }

    assertThat(ids).hasSize(1000);
  }

  /** 雪花 ID：正数、单调递增、不重复。 */
  @Test
  void generateLongId_isPositiveMonotonicAndUnique() {
    long first = DistributedIdGenerator.generateLongId();
    long second = DistributedIdGenerator.generateLongId();

    assertThat(first).isPositive();
    assertThat(second).isGreaterThanOrEqualTo(first);

    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      ids.add(DistributedIdGenerator.generateLongId());
    }
    assertThat(ids).hasSize(1000);
  }

  @Test
  void generateSnowflakeId_isNumericString() {
    assertThat(DistributedIdGenerator.generateSnowflakeId()).matches("\\d+");
  }

  /** 业务流水号：前缀 + yyyyMMdd + 6 位序列。 */
  @Test
  void generateBusinessNumber_matchesContract() {
    assertThat(DistributedIdGenerator.generateBusinessNumber()).matches("^BONE\\d{8}\\d{6}$");
    assertThat(DistributedIdGenerator.generateBusinessNumber("ORD"))
        .matches("^ORD\\d{8}\\d{6}$")
        .startsWith("ORD");
  }

  /** 订单号：前缀 + yyyyMMddHHmmss + 4 位序列。 */
  @Test
  void generateOrderNumber_matchesContract() {
    assertThat(DistributedIdGenerator.generateOrderNumber()).matches("^BONE\\d{14}\\d{4}$");
    assertThat(DistributedIdGenerator.generateOrderNumber("PAY")).matches("^PAY\\d{14}\\d{4}$");
  }

  @Test
  void sequenceNumbersIncreaseWithinSameCall() {
    String first = DistributedIdGenerator.generateBusinessNumber("SEQ");
    String second = DistributedIdGenerator.generateBusinessNumber("SEQ");

    long seq1 = Long.parseLong(first.substring(first.length() - 6));
    long seq2 = Long.parseLong(second.substring(second.length() - 6));

    assertThat(seq2).isGreaterThan(seq1);
  }

  @Test
  void nullPrefixRejected() {
    assertThatThrownBy(() -> DistributedIdGenerator.generateBusinessNumber(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DistributedIdGenerator.generateOrderNumber(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * 固化既有不一致：{@code generateBusinessNumber} 用 {@code Assert.notNull}（空串放行）， 而 {@code
   * generateOrderNumber} 用 {@code Assert.hasText}（空串拒绝）。两者校验强度不同，统一前勿依赖空串行为。
   */
  @Test
  void blankPrefixHandlingDiffersBetweenApis() {
    assertThat(DistributedIdGenerator.generateBusinessNumber("")).matches("^\\d{8}\\d{6}$");
    assertThatThrownBy(() -> DistributedIdGenerator.generateOrderNumber(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 重复初始化必须被忽略（避免运行期重置 workerId 造成 ID 冲突）。 */
  @Test
  void repeatedInitializationIsIgnored() {
    DistributedIdGenerator.initialize(1L, 1L, 1609459200000L);
    DistributedIdGenerator.initialize(2L, 2L, 1609459200000L);

    assertThat(DistributedIdGenerator.generateLongId()).isPositive();
  }

  @Test
  void utilityClassCannotBeInstantiated() throws Exception {
    Constructor<DistributedIdGenerator> ctor =
        DistributedIdGenerator.class.getDeclaredConstructor();
    ctor.setAccessible(true);

    InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);

    assertThat(ex.getCause()).isInstanceOf(UnsupportedOperationException.class);
  }
}
