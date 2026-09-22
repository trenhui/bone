package com.bone.studio.generator.domain.model.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.DomainException;
import org.junit.jupiter.api.Test;

/** {@link DataSource} 纯单测：默认启用、参数校验、更新与测试结果记录（无容器）。 */
class DataSourceTest {

  private DataSource createDataSource() {
    return DataSource.create(1L, 1L, "订单库", "MySql", "localhost", 3306, "bone", "root", "enc-pwd");
  }

  @Test
  void testCreateDefaultsToEnabledAndLowerCasesDbType() {
    DataSource ds = createDataSource();

    assertTrue(ds.isEnabled());
    assertEquals("mysql", ds.getDbType());
    assertEquals("订单库", ds.getName());
    assertEquals(3306, ds.getPort());
    assertFalse(ds.isDeleted());
    assertEquals(0, ds.getVersion());
  }

  @Test
  void testCreateRejectsInvalidArguments() {
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, " ", "mysql", "h", 3306, "db", "u", "p"));
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, "x", null, "h", 3306, "db", "u", "p"));
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, "x", "mysql", " ", 3306, "db", "u", "p"));
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, "x", "mysql", "h", 0, "db", "u", "p"));
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, "x", "mysql", "h", 3306, "", "u", "p"));
    assertThrows(
        DomainException.class,
        () -> DataSource.create(1L, 1L, "x", "mysql", "h", 3306, "db", null, "p"));
  }

  @Test
  void testUpdateOverridesFieldsAndKeepsBlankPassword() {
    DataSource ds = createDataSource();

    ds.update("新订单库", "postgresql", "10.0.0.1", 5432, "order_db", "app", null);

    assertEquals("新订单库", ds.getName());
    assertEquals("postgresql", ds.getDbType());
    assertEquals(5432, ds.getPort());
    // 密码为空时不覆盖已加密密码
    assertEquals("enc-pwd", ds.getPasswordEncrypted());

    ds.update("新订单库", "postgresql", "10.0.0.1", 5432, "order_db", "app", "enc-new");
    assertEquals("enc-new", ds.getPasswordEncrypted());
  }

  @Test
  void testRecordTestResultMarksOutcomeAndEnablesOnSuccess() {
    DataSource ds = createDataSource();

    ds.recordTestResult(true, "连接成功");

    assertEquals("SUCCESS", ds.getLastTestResult());
    assertTrue(ds.isEnabled());
    assertNotNull(ds.getLastTestAt());

    ds.recordTestResult(false, "连接超时");

    assertEquals("FAILED", ds.getLastTestResult());
    assertEquals("连接超时", ds.getLastTestMessage());
  }
}
