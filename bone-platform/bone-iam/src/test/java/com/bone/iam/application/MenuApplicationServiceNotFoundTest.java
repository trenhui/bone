package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.DeleteMenuCommand;
import com.bone.iam.application.command.cmd.UpdateMenuCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MenuApplicationService 的「菜单不存在」契约测试。
 *
 * <p>原先 update / delete 抛裸 {@code IllegalArgumentException}，落 {@code Exception} 兜底报成
 * <strong>500</strong>；现统一为 {@code IAM_MENU_NOT_FOUND} + <strong>404</strong>。
 */
@ExtendWith(MockitoExtension.class)
class MenuApplicationServiceNotFoundTest {

  @Mock private MenuRepository menuRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private MenuApplicationService menuApplicationService;

  @Test
  void updateThrows404WhenMenuMissing() {
    UpdateMenuCommand cmd = new UpdateMenuCommand();
    cmd.setId(99L);
    when(menuRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> menuApplicationService.update(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.MENU_NOT_FOUND);
  }

  @Test
  void deleteThrows404WhenMenuMissing() {
    when(menuRepository.findById(99L)).thenReturn(null);

    assertThatThrownBy(() -> menuApplicationService.delete(new DeleteMenuCommand(99L)))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.MENU_NOT_FOUND);
    verify(menuRepository, never()).deleteById(99L);
  }
}
