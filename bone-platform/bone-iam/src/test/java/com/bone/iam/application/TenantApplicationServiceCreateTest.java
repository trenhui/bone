package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.CreateTenantCommand;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TenantApplicationService#create 单元测试（原 CreateTenantCommandHandler 已内联）
 *
 * <p>覆盖：
 *
 * <ol>
 *   <li>正常创建租户 → 返回 ID
 *   <li>租户编码重复 → 抛 BizException(409)
 *   <li>level 和 adminEmail 为 null 时使用默认值
 * </ol>
 *
 * <p>编码查重的 DSL 已下沉至 {@link TenantRepository#countByCode}（本聚合读，ADR-0030），故断言落在该仓储方法上。
 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceCreateTest {

  @Mock TenantRepository tenantRepository;

  @Mock TenantDeletionGateway tenantDeletionGateway;

  @InjectMocks TenantApplicationService tenantApplicationService;

  @Test
  void createTenantSuccessfully() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Acme Corp");
    cmd.setCode("ACME");
    cmd.setLevel(1);
    cmd.setAdminEmail("admin@acme.com");

    when(tenantRepository.countByCode("ACME")).thenReturn(0L);
    when(tenantRepository.save(any())).thenReturn(100L);

    Long result = tenantApplicationService.create(cmd);

    assertThat(result).isEqualTo(100L);
    verify(tenantRepository, times(1)).countByCode("ACME");
    verify(tenantRepository, times(1)).save(any());
  }

  @Test
  void duplicateCodeThrows409() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Acme Corp");
    cmd.setCode("ACME");
    cmd.setLevel(1);

    when(tenantRepository.countByCode("ACME")).thenReturn(1L);

    assertThatThrownBy(() -> tenantApplicationService.create(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("租户编码已存在");

    verify(tenantRepository, never()).save(any());
  }

  @Test
  void nullLevelDefaultsToZero() {
    CreateTenantCommand cmd = new CreateTenantCommand();
    cmd.setName("Test Tenant");
    cmd.setCode("TEST");
    cmd.setLevel(null);
    cmd.setAdminEmail(null);

    when(tenantRepository.countByCode("TEST")).thenReturn(0L);
    when(tenantRepository.save(any())).thenReturn(200L);

    Long result = tenantApplicationService.create(cmd);

    assertThat(result).isEqualTo(200L);
    verify(tenantRepository, times(1)).save(any());
  }
}
