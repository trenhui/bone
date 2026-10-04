package com.bone.masterdata.domain.model.steward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.annotation.Deleted;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** {@link StewardAssignment} 纯单测：指派字段落位 + 撤销时间落位（HC-008 updated_at） + 软删声明。 */
class StewardAssignmentTest {

  @Test
  void testAssignCarriesRoleAndAccount() {
    StewardAssignment a = StewardAssignment.assign(1L, 10L, 20L, "STEWARD");
    assertEquals(20L, a.getAccountId());
    assertEquals("STEWARD", a.getRoleType());
    assertEquals(10L, a.getMasterDataEntityId());
  }

  /** 指派时两个时间戳必须同时落位：{@code updatedAt} 若为 null，则新建行的"最后变更时间"缺失， 审计视角下这条指派看起来"从未确定过生效时刻"。 */
  @Test
  void testAssignStampsBothTimestamps() {
    StewardAssignment a = StewardAssignment.assign(1L, 10L, 20L, "OWNER");

    assertNotNull(a.getCreatedAt());
    assertNotNull(a.getUpdatedAt());
    assertEquals(a.getCreatedAt(), a.getUpdatedAt());
  }

  /**
   * 撤销必须刷新 {@code updatedAt} —— 这是补 {@code updated_at} 列的全部意义： {@code createdAt}
   * 只能回答"何时授的"，撤销后要能回答"何时撤的"。
   */
  @Test
  void testRevokeRefreshesUpdatedAt() throws InterruptedException {
    StewardAssignment a = StewardAssignment.assign(1L, 10L, 20L, "STEWARD");
    LocalDateTime before = a.getUpdatedAt();
    // 数据库列精度为 DATETIME(3)，若不加微小间隔，now() 可能与 createdAt 同一毫秒而测不出差异
    Thread.sleep(5);

    a.revoke();

    assertTrue(
        a.getUpdatedAt().isAfter(before),
        "撤销后 updatedAt 必须晚于指派时刻，实际 before=" + before + " after=" + a.getUpdatedAt());
    // 指派时刻本身不应被撤销行为改写
    assertEquals(before, a.getCreatedAt());
  }

  /**
   * 软删声明必须存在 —— 这是本轮修复的核心缺陷。
   *
   * <p>bone-metadata-sdk 的 {@code TableMetadata#isSoftDeletable()} 判据是<b>实体内是否存在带 {@code @Deleted}
   * 注解的字段</b>，与 DDL 有无 {@code deleted} 列无关。缺这个字段 ⇒ {@code deleteById} 判定"不可软删"从而发出 {@code DELETE
   * FROM} ⇒ 治理指派撤销时整行物理消失， 且调用方拿到 HTTP 200 毫不知情。
   *
   * <p>本断言直接反射检查注解，是防复发的最小契约；全仓同类问题的清单见 {@code
   * doc/architecture/soft-delete-declaration-baseline.json}，门禁见 {@code
   * scripts/check-soft-delete-declaration.py}。
   */
  @Test
  void testDeclaresDeletedFieldSoRepositoryPerformsSoftDelete() {
    Field deletedField =
        Stream.of(StewardAssignment.class.getDeclaredFields())
            .filter(f -> f.isAnnotationPresent(Deleted.class))
            .findFirst()
            .orElse(null);

    assertNotNull(
        deletedField,
        "StewardAssignment 必须声明带 @Deleted 的字段（初始值 false），否则 Repository#deleteById"
            + " 会发出 DELETE FROM 做物理删除，撤销指派后行永久消失且调用方收到 200");

    // 新建指派必须落在"未删除"态：若初值为 true，等于指派一落库就不可见
    assertEquals(Boolean.FALSE, StewardAssignment.assign(1L, 10L, 20L, "STEWARD").getDeleted());
  }
}
