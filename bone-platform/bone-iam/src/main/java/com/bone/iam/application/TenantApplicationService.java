package com.bone.iam.application;

import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.application.command.cmd.CreateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantCommand;
import com.bone.iam.application.command.cmd.UpdateTenantQuotaCommand;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.qry.TenantPageQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.repository.TenantRepository;
import com.bone.iam.domain.tenant.Tenant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户应用层统一门面（Application Service First）——租户类用例的唯一入口。
 *
 * <p>原 {@code application.command.handler} / {@code application.query.handler} 下的 8 个租户 Handler
 * 已全量内联进本类 （E-3.11 一次性大爆炸收敛）。适配器只依赖本类，HTTP 契约（URL / DTO / 返回类型）保持不变。
 *
 * <p>本类不出现读侧 DSL：编码查重与分页检索下沉 {@link TenantRepository#countByCode} / {@link
 * TenantRepository#findTenantPage}（本聚合读，ADR-0030 / E-4.2）。
 */
@Service
@RequiredArgsConstructor
public class TenantApplicationService {

  private static final long PLATFORM_TENANT_ID = 0L;

  private final TenantRepository tenantRepository;
  private final TenantDeletionGateway tenantDeletionGateway;

  @Transactional
  public Long create(CreateTenantCommand cmd) {
    Long existing = tenantRepository.countByCode(cmd.getCode());
    if (existing != null && existing > 0) {
      throw BizException.of(409, "租户编码已存在");
    }
    Tenant tenant =
        Tenant.create(
            DistributedIdGenerator.generateLongId(),
            cmd.getName(),
            cmd.getCode(),
            cmd.getLevel() != null ? cmd.getLevel() : 0,
            cmd.getAdminEmail() != null ? cmd.getAdminEmail() : "");
    return tenantRepository.save(tenant);
  }

  @Transactional
  public void update(UpdateTenantCommand cmd) {
    Tenant tenant = tenantRepository.findById(cmd.getId());
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    tenant.update(cmd.getName(), cmd.getAdminEmail(), cmd.getLevel() != null ? cmd.getLevel() : 0);
    tenantRepository.save(tenant);
  }

  @Transactional
  public void delete(Long id) {
    if (id == null) {
      throw new IllegalArgumentException("租户 ID 不能为空");
    }
    if (id == PLATFORM_TENANT_ID) {
      throw BizException.of(400, IamErrorCodes.TENANT_DELETE_FORBIDDEN + ": 禁止删除平台租户");
    }
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    tenantDeletionGateway.purgeTenantData(id);
    tenantRepository.deleteById(id);
  }

  @Transactional
  public void enable(Long id) {
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    tenant.enable();
    tenantRepository.save(tenant);
  }

  @Transactional
  public void disable(Long id) {
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    tenant.disable();
    tenantRepository.save(tenant);
  }

  @Transactional
  public void updateQuota(UpdateTenantQuotaCommand cmd) {
    if (cmd == null || cmd.getId() == null) {
      throw new IllegalArgumentException("租户 ID 不能为空");
    }
    if (cmd.getMaxAccounts() != null && cmd.getMaxAccounts() < 0) {
      throw new IllegalArgumentException("maxAccounts 不能为负数");
    }
    if (cmd.getMaxRoles() != null && cmd.getMaxRoles() < 0) {
      throw new IllegalArgumentException("maxRoles 不能为负数");
    }
    Tenant tenant = tenantRepository.findById(cmd.getId());
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    tenant.updateQuota(cmd.getMaxAccounts(), cmd.getMaxRoles());
    tenantRepository.save(tenant);
  }

  @Transactional(readOnly = true)
  public PageResult<TenantDTO> page(TenantPageQuery qry) {
    int pageNo = qry.getPage() != null ? qry.getPage() : 1;
    int pageSize = qry.getSize() != null ? qry.getSize() : 10;
    PageResult<Tenant> result =
        tenantRepository.findTenantPage(qry.getCode(), qry.getName(), pageNo, pageSize);
    List<TenantDTO> records =
        result.getRecords().stream().map(TenantApplicationService::toDto).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public TenantDTO detail(Long id) {
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw NotFoundException.of("租户不存在");
    }
    return toDto(tenant);
  }

  private static TenantDTO toDto(Tenant tenant) {
    TenantDTO dto = new TenantDTO();
    dto.setId(tenant.getId());
    dto.setName(tenant.getName());
    dto.setCode(tenant.getCode());
    dto.setLevel(tenant.getLevel());
    dto.setStatus(tenant.getStatus());
    dto.setAdminEmail(tenant.getAdminEmail());
    dto.setMaxAccounts(tenant.getMaxAccounts());
    dto.setMaxRoles(tenant.getMaxRoles());
    return dto;
  }
}
