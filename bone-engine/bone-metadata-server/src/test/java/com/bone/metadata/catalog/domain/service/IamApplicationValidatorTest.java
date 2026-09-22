package com.bone.metadata.catalog.domain.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.domain.model.iam.IamApplicationRef;
import com.bone.metadata.catalog.domain.repository.IamApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IamApplicationValidatorTest {

  @Mock private IamApplicationRepository iamApplicationRepository;
  @InjectMocks private IamApplicationValidator validator;

  @Test
  void requireExists_shouldPass_whenAppFound() {
    when(iamApplicationRepository.findById(1L)).thenReturn(new IamApplicationRef());
    assertDoesNotThrow(() -> validator.requireExists(1L));
  }

  @Test
  void requireExists_shouldThrow_whenAppMissing() {
    when(iamApplicationRepository.findById(999L)).thenReturn(null);
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(999L));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("所属应用不存在");
  }

  @Test
  void requireExists_shouldThrow_whenAppIdNull() {
    BizException ex = assertThrows(BizException.class, () -> validator.requireExists(null));
    org.assertj.core.api.Assertions.assertThat(ex.getMessage()).contains("应用ID不能为空");
  }
}
