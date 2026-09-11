package com.bone.studio.generator.domain.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.Types;
import org.junit.jupiter.api.Test;

/** {@link GenColumnMetadata} 纯单测：由 JDBC 列快照创建元数据及 Java 类型映射（无容器）。 */
class GenColumnMetadataTest {

  private TableColumn varcharColumn() {
    return TableColumn.builder()
        .columnName("order_no")
        .jdbcType(Types.VARCHAR)
        .columnType("varchar(64)")
        .columnSize(64)
        .decimalDigits(0)
        .columnComment("订单号")
        .nullable(false)
        .build();
  }

  @Test
  void testCreateSnapshotsColumnProfile() {
    GenColumnMetadata metadata = GenColumnMetadata.create(1L, 1L, 10L, varcharColumn());

    assertEquals(10L, metadata.getTableMetadataId());
    assertEquals("order_no", metadata.getOriginalColumnName());
    assertEquals("order_no", metadata.getCustomFieldName());
    assertEquals(String.valueOf(Types.VARCHAR), metadata.getJdbcType());
    assertEquals("String", metadata.getJavaType());
    assertEquals("varchar(64)", metadata.getColumnType());
    assertEquals(64, metadata.getColumnLength());
    assertEquals("订单号", metadata.getColumnComment());
    assertFalse(metadata.isPrimaryKey());
    assertFalse(metadata.isAutoincrement());
    assertFalse(metadata.isNullable());
    assertNotNull(metadata.getCreatedAt());
  }

  @Test
  void testCreateMapsNumericAndTimestampTypes() {
    TableColumn decimalColumn =
        TableColumn.builder()
            .columnName("amount")
            .jdbcType(Types.DECIMAL)
            .columnType("decimal(10,2)")
            .columnSize(10)
            .decimalDigits(2)
            .build();
    GenColumnMetadata decimal = GenColumnMetadata.create(2L, 1L, 10L, decimalColumn);
    assertEquals("BigDecimal", decimal.getJavaType());

    TableColumn tsColumn =
        TableColumn.builder()
            .columnName("created_at")
            .jdbcType(Types.TIMESTAMP)
            .columnType("timestamp")
            .columnSize(0)
            .decimalDigits(0)
            .build();
    GenColumnMetadata timestamp = GenColumnMetadata.create(3L, 1L, 10L, tsColumn);
    assertEquals("LocalDateTime", timestamp.getJavaType());
    assertFalse(timestamp.isNullable());
  }
}
