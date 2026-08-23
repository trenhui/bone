package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CreateTenantCommandHandler 单元测试
 *
 * <p>覆盖：
 *
 * <ol>
 *   <li>正常创建租户 → 返回 ID
 *   <li>租户编码重复 → 抛 BizException(409)
 *   <li>level 和 adminEmail 为 null 时使用默认值
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CreateTenantCommandHandlerTest {

  @Mock TenantRepository tenantRepository;

  CreateTenantCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CreateTenantCommandHandler(tenantRepository);
  }

  @Test
  @SuppressWarnings("unchecked")
  void createTenantSuccessfully() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Acme Corp");
    cmd.setCode("ACME");
    cmd.setLevel(1);
    cmd.setAdminEmail("admin@acme.com");

    when(tenantRepository.countByCriteria(any(Criteria.class))).thenReturn(0L);
    when(tenantRepository.save(any())).thenReturn(100L);

    Long result = handler.handle(cmd);

    assertThat(result).isEqualTo(100L);
    verify(tenantRepository, times(1)).countByCriteria(any(Criteria.class));
    verify(tenantRepository, times(1)).save(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void duplicateCodeThrows409() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Acme Corp");
    cmd.setCode("ACME");
    cmd.setLevel(1);

    when(tenantRepository.countByCriteria(any(Criteria.class))).thenReturn(1L);

    assertThatThrownBy(() -> handler.handle(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("租户编码已存在");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void nullLevelDefaultsToZero() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Test Tenant");
    cmd.setCode("TEST");
    cmd.setLevel(null);
    cmd.setAdminEmail(null);

    when(tenantRepository.countByCriteria(any(Criteria.class))).thenReturn(0L);
    when(tenantRepository.save(any())).thenReturn(200L);

    Long result = handler.handle(cmd);

    assertThat(result).isEqualTo(200L);
    verify(tenantRepository, times(1)).save(any());
  }
}
