package com.bone.studio.generator.domain.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** {@link GenTableMetadata} 纯单测：由数据库表快照创建元数据（无容器）。 */
class GenTableMetadataTest {

  private DatabaseTable orderTable() {
    return DatabaseTable.builder().tableName("ic_order").tableComment("订单主表").build();
  }

  @Test
  void testCreateSnapshotsTableProfileAsSynced() {
    GenTableMetadata metadata = GenTableMetadata.create(1L, 1L, "ds-order", orderTable());

    assertEquals("ds-order", metadata.getDataSourceId());
    assertEquals("ic_order", metadata.getOriginalTableName());
    assertEquals("ic_order", metadata.getCustomEntityName());
    assertEquals("订单主表", metadata.getTableComment());
    assertEquals("SYNCED", metadata.getSyncStatus());
    assertNotNull(metadata.getLastSyncAt());
    assertFalse(metadata.isDeleted());
    assertEquals(0, metadata.getVersion());
  }
}
