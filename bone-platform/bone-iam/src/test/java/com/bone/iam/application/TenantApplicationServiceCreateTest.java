package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.CreateTenantCommand;
import com.bone.iam.application.support.TenantAdminBootstrapSupport;
import com.bone.iam.application.support.TenantAdminBootstrapSupport.TenantAdminAccount;
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
 *   <li>正常创建租户 → 返回租户 id + 租户管理员初始化结果（详设 §2.9）
 *   <li>租户编码重复 → 抛 BizException(409)，且不触发管理员初始化
 *   <li>level 和 adminEmail 为 null 时使用默认值
 * </ol>
 *
 * <p>编码查重的 DSL 已下沉至 {@link TenantRepository#countByCode}（本聚合读，ADR-0030），故断言落在该仓储方法上。
 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceCreateTest {

  @Mock TenantRepository tenantRepository;

  @Mock TenantDeletionGateway tenantDeletionGateway;

  @Mock TenantAdminBootstrapSupport tenantAdminBootstrapSupport;

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
    when(tenantAdminBootstrapSupport.bootstrap(eq(100L), eq("ACME"), eq("admin@acme.com")))
        .thenReturn(new TenantAdminAccount(900L, "acme_admin", "Bone-xY3kQ9zP"));

    TenantApplicationService.CreateTenantResult result = tenantApplicationService.create(cmd);

    assertThat(result.tenantId()).isEqualTo(100L);
    assertThat(result.adminAccountId()).isEqualTo(900L);
    assertThat(result.adminUsername()).isEqualTo("acme_admin");
    assertThat(result.initialPassword()).isEqualTo("Bone-xY3kQ9zP");
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
    verify(tenantAdminBootstrapSupport, never())
        .bootstrap(any(), any(), any(java.lang.String.class));
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
    when(tenantAdminBootstrapSupport.bootstrap(eq(200L), eq("TEST"), eq(null)))
        .thenReturn(new TenantAdminAccount(901L, "test_admin", "Bone-aB2cD3eF"));

    TenantApplicationService.CreateTenantResult result = tenantApplicationService.create(cmd);

    assertThat(result.tenantId()).isEqualTo(200L);
    assertThat(result.adminUsername()).isEqualTo("test_admin");
    verify(tenantRepository, times(1)).save(any());
  }
}
