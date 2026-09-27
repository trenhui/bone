package com.bone.studio.generator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.studio.generator.application.command.cmd.RepairLegacyEntityNameCommand;
import com.bone.studio.generator.application.dto.RepairEntityNameResult;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepairLegacyEntityNameApplicationServiceTest {

  @Mock private GenTableMetadataRepository tableMetadataRepo;
  @InjectMocks private RepairLegacyEntityNameApplicationService service;

  private GenTableMetadata row(long id, String ds, String original, String custom) {
    return GenTableMetadata.builder()
        .id(id)
        .dataSourceId(ds)
        .originalTableName(original)
        .customEntityName(custom)
        .deleted(false)
        .build();
  }

  @Test
  void dryRunCountsOnlyWithoutWriting() {
    GenTableMetadata legacy = row(1L, "ds-1", "bone_application", "bone_application");
    GenTableMetadata customized = row(2L, "ds-1", "t_order", "MyOrder");
    GenTableMetadata alreadyRepaired = row(3L, "ds-1", "simple", "Simple");
    when(tableMetadataRepo.findAllActive())
        .thenReturn(List.of(legacy, customized, alreadyRepaired));

    RepairEntityNameResult result = service.handle(RepairLegacyEntityNameCommand.builder().build());

    assertFalse(result.isExecuted());
    assertEquals(1, result.getAffectedCount());
    assertEquals(1, result.getExamples().size());
    assertEquals("BoneApplication", result.getExamples().get(0).getNewEntityName());
    verify(tableMetadataRepo, never()).update(legacy);
  }

  @Test
  void executeWritesOnlyLegacyRows() {
    GenTableMetadata legacy = row(1L, "ds-1", "bone_application", "bone_application");
    GenTableMetadata customized = row(2L, "ds-1", "t_order", "MyOrder");
    when(tableMetadataRepo.findAllActive()).thenReturn(List.of(legacy, customized));

    RepairEntityNameResult result =
        service.handle(RepairLegacyEntityNameCommand.builder().execute(true).build());

    assertTrue(result.isExecuted());
    assertEquals(1, result.getAffectedCount());
    verify(tableMetadataRepo, times(1)).update(legacy);
    verify(tableMetadataRepo, never()).update(customized);
    assertEquals("BoneApplication", legacy.getCustomEntityName());
  }

  @Test
  void filtersByDataSourceId() {
    GenTableMetadata ds1 = row(1L, "ds-1", "bone_application", "bone_application");
    GenTableMetadata ds2 = row(2L, "ds-2", "md_quality_rule", "md_quality_rule");
    when(tableMetadataRepo.findAllActive()).thenReturn(List.of(ds1, ds2));

    RepairEntityNameResult result =
        service.handle(RepairLegacyEntityNameCommand.builder().dataSourceId("ds-1").build());

    assertEquals(1, result.getAffectedCount());
    assertEquals("ds-1", result.getExamples().get(0).getDataSourceId());
  }
}
