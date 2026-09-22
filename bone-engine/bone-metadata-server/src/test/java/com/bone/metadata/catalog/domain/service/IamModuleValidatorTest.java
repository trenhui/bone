package com.bone.metadata.catalog.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.iam.IamModuleRef;
import com.bone.metadata.catalog.domain.repository.IamModuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IamModuleValidatorTest {

  @Mock private IamModuleRepository iamModuleRepository;
  @InjectMocks private IamModuleValidator validator;

  @Test
  void requireExists_shouldPass_whenModuleFound() {
    when(iamModuleRepository.findById(1L)).thenReturn(new IamModuleRef());
    assertDoesNotThrow(() -> validator.requireExists(1L));
  }

  @Test
  void requireExists_shouldThrow_whenModuleMissing() {
    when(iamModuleRepository.findById(999L)).thenReturn(null);
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(999L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("所属模块不存在");
  }

  @Test
  void requireExists_shouldThrow_whenModuleIdNull() {
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(null));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("模块ID不能为空");
  }
}
