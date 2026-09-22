package com.bone.masterdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.masterdata.application.command.CreateDataStandardCommand;
import com.bone.masterdata.application.command.UpdateDataStandardCommand;
import com.bone.masterdata.domain.repository.DataStandardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardApplicationServiceTest {

  @Mock private DataStandardRepository dataStandardRepository;

  @InjectMocks private StandardApplicationService service;

  @Test
  void createRejectsDuplicateWith409() {
    when(dataStandardRepository.countByEntityCodeAndFieldCode(any(), any())).thenReturn(1L);

    CreateDataStandardCommand cmd = new CreateDataStandardCommand();
    cmd.setEntityCode("customer");
    cmd.setFieldCode("phone");
    cmd.setRuleType(1);

    BizException ex = assertThrows(BizException.class, () -> service.create(cmd));
    assertEquals(409, ex.getCode());
  }

  @Test
  void updateRequiresExistingStandard() {
    when(dataStandardRepository.findById(1L)).thenReturn(null);

    UpdateDataStandardCommand cmd = new UpdateDataStandardCommand();
    cmd.setId(1L);
    cmd.setRuleType(1);

    assertThrows(NotFoundException.class, () -> service.update(cmd));
  }
}
