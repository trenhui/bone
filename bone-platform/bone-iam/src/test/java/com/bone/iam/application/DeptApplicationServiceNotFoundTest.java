package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.DeleteDeptCommand;
import com.bone.iam.application.command.cmd.UpdateDeptCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.DeptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * DeptApplicationService 的「部门不存在」契约测试。
 *
 * <p>原先 update / delete 抛裸 {@code IllegalArgumentException}，落 {@code Exception} 兜底报成
 * <strong>500</strong>；现统一为 {@code IAM_DEPT_NOT_FOUND} + <strong>404</strong>。
 */
@ExtendWith(MockitoExtension.class)
class DeptApplicationServiceNotFoundTest {

  @Mock private DeptRepository deptRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private DeptApplicationService deptApplicationService;

  @Test
  void updateThrows404WhenDeptMissing() {
    UpdateDeptCommand cmd = new UpdateDeptCommand();
    cmd.setId(99L);
    when(deptRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> deptApplicationService.update(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.DEPT_NOT_FOUND);
  }

  @Test
  void deleteThrows404WhenDeptMissing() {
    when(deptRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> deptApplicationService.delete(new DeleteDeptCommand(99L)))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.DEPT_NOT_FOUND);
    verify(deptRepository, never()).deleteById(99L);
  }
}
