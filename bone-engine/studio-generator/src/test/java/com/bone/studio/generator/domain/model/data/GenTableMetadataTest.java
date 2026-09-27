package com.bone.studio.generator.domain.model.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    assertEquals("IcOrder", metadata.getCustomEntityName());
    assertEquals("订单主表", metadata.getTableComment());
    assertEquals("SYNCED", metadata.getSyncStatus());
    assertNotNull(metadata.getLastSyncAt());
    assertFalse(metadata.isDeleted());
    assertEquals(0, metadata.getVersion());
  }

  /** 实体名必须是合法 Java 类名：剥掉 {@code t_} 技术前缀并转大驼峰（实测 t_order 曾生成 class t_order）。 */
  @Test
  void entityNameStripsTechnicalPrefixAndCamelCases() {
    GenTableMetadata metadata =
        GenTableMetadata.create(
            1L, 1L, "ds-order", DatabaseTable.builder().tableName("t_order").build());

    assertEquals("t_order", metadata.getOriginalTableName());
    assertEquals("Order", metadata.getCustomEntityName());
  }

  @Test
  void toEntityNamePublicCamelCases() {
    assertEquals("BoneApplication", GenTableMetadata.toEntityName("bone_application"));
    assertEquals("MdQualityRule", GenTableMetadata.toEntityName("md_quality_rule"));
    assertEquals("Order", GenTableMetadata.toEntityName("t_order"));
    assertEquals("ORDER", GenTableMetadata.toEntityName("T_ORDER"));
    assertEquals("Simple", GenTableMetadata.toEntityName("simple"));
    assertNull(GenTableMetadata.toEntityName(null));
  }

  @Test
  void needsAndRepairLegacyEntityName() {
    GenTableMetadata legacy =
        GenTableMetadata.builder()
            .originalTableName("bone_application")
            .customEntityName("bone_application")
            .build();
    assertTrue(legacy.needsLegacyEntityNameRepair());
    legacy.repairEntityName();
    assertEquals("BoneApplication", legacy.getCustomEntityName());

    GenTableMetadata customized =
        GenTableMetadata.builder().originalTableName("t_order").customEntityName("MyOrder").build();
    assertFalse(customized.needsLegacyEntityNameRepair());

    GenTableMetadata alreadyRepaired =
        GenTableMetadata.builder().originalTableName("simple").customEntityName("Simple").build();
    assertFalse(alreadyRepaired.needsLegacyEntityNameRepair());
  }
}
