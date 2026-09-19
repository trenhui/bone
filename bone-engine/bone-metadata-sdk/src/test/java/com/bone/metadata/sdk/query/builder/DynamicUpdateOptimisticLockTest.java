package com.bone.metadata.sdk.query.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.domain.entity.Entity;
import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import lombok.Data;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseRepository#update(Object)} / {@link BaseRepository#insert(Object)} 的乐观锁契约（ADR-0031
 * D1）。
 *
 * <p>用 mock {@link SqlExecutor} 隔离执行，不依赖真实数据库。覆盖：version 表 0 行 → 抛 {@link
 * OptimisticLockingFailureException}；成功 → 实体 version 自增回写；insert → 实体 version 补 0；非 version 表 0
 * 行仍返回 false；Integer 版字段回写不抛异常。
 */
class DynamicUpdateOptimisticLockTest {

  @Test
  void versionedTable_zeroRows_throwsOptimisticLockConflict() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.update(any())).thenReturn(0);

    Account account = new Account(1L, 3L);
    assertThrows(
        OptimisticLockingFailureException.class,
        () -> repository(Account.class, se).update(account));
  }

  @Test
  void versionedTable_success_incrementsEntityVersion() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.update(any())).thenReturn(1);

    Account account = new Account(1L, 3L);
    assertTrue(repository(Account.class, se).update(account));
    assertEquals(4L, account.getVersion());
  }

  @Test
  void versionedTable_insert_initializesVersionToZero() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.generateId(any(), any())).thenReturn(100L);
    when(se.batchUpdate(any())).thenReturn(new int[] {1});

    Account account = new Account(null, null);
    repository(Account.class, se).insert(account);
    assertEquals(0L, account.getVersion());
  }

  @Test
  void nonVersionedTable_zeroRows_returnsFalseNotThrow() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.update(any())).thenReturn(0);

    PlainAccount account = new PlainAccount(1L);
    assertFalse(repository(PlainAccount.class, se).update(account));
  }

  @Test
  void primitiveVersionTypes_areAcceptedByMetadataResolver() {
    // 反例回归：原始类型不是 Number 子类，早期判定用 Number.class.isAssignableFrom(long.class) 会误拒 @Version long。
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.update(any())).thenReturn(1);

    LongAccount account = new LongAccount(1L, 3L);
    assertTrue(repository(LongAccount.class, se).update(account));
    assertEquals(4L, account.getVersion());
  }

  @Test
  void integerVersion_update_incrementsAndWritesBackAsInteger() {
    SqlExecutor se = mock(SqlExecutor.class);
    when(se.update(any())).thenReturn(1);

    IntAccount account = new IntAccount(1L, 3);
    assertTrue(repository(IntAccount.class, se).update(account));
    assertEquals(4, account.getVersion());
  }

  private <ID, E extends Entity<ID>> BaseRepository<E, ID> repository(
      Class<E> cls, SqlExecutor se) {
    SqlBuilder sqlBuilder =
        new SqlBuilder(mock(MetadataService.class), mock(DatabaseDialect.class));
    sqlBuilder.init();
    return new TestRepository<>(sqlBuilder, se, cls, mock(ExtensionCoordinator.class));
  }

  static class TestRepository<E extends Entity<ID>, ID> extends BaseRepository<E, ID> {
    TestRepository(
        SqlBuilder sqlBuilder,
        SqlExecutor sqlExecutor,
        Class<E> entityClass,
        ExtensionCoordinator ec) {
      super(sqlBuilder, sqlExecutor, entityClass, ec);
    }
  }

  @Data
  @Table("t_account")
  static class Account extends Entity<Long> {
    @Version private Long version;

    Account(Long id, Long version) {
      setId(id);
      this.version = version;
    }
  }

  @Data
  @Table("t_plain")
  static class PlainAccount extends Entity<Long> {
    private String name;

    PlainAccount(Long id) {
      setId(id);
    }
  }

  @Data
  @Table("t_int_account")
  static class IntAccount extends Entity<Long> {
    @Version private Integer version;

    IntAccount(Long id, Integer version) {
      setId(id);
      this.version = version;
    }
  }

  /** 原始类型 {@code long} 的 @Version：验证解析期不再误拒（原始类型非 Number 子类）。 */
  @Data
  @Table("t_long_account")
  static class LongAccount extends Entity<Long> {
    @Version private long version;

    LongAccount(Long id, long version) {
      setId(id);
      this.version = version;
    }
  }
}
