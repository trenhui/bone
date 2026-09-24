package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.iam.application.command.CreateTenantCommand;
import com.bone.iam.application.command.UpdateTenantCommand;
import com.bone.iam.application.command.UpdateTenantQuotaCommand;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.qry.TenantPageQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.model.tenant.Tenant;
import com.bone.iam.domain.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户应用层统一门面（Application Service First）——租户类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code application.command.handler} / {@code application.query.handler} 下的 8 个租户 Handler
 * 已全量内联进本类 （E-3.11 一次性大爆炸收敛）。适配器只依赖本类，HTTP 契约（URL / DTO / 返回类型）保持不变。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL：编码查重与分页检索下沉 {@link TenantRepository#countByCode} / {@link
 * TenantRepository#findTenantPage}（本聚合读，ADR-0030 / E-4.2）。
 */
/*
/*
* <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（Tenant）当前不发布领域事件，其创建/更新/配额变更均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
*/
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class TenantApplicationService {

  private static final long PLATFORM_TENANT_ID = 0L;

  private final TenantRepository tenantRepository;
  private final TenantDeletionGateway tenantDeletionGateway;

  @Transactional
  public Long create(CreateTenantCommand cmd) {
    Long existing = tenantRepository.countByCode(cmd.getCode());
    if (existing != null && existing > 0) {
      throw IamErrors.of(IamErrorCodes.TENANT_CODE_CONFLICT, "租户编码已存在");
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
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
    }
    tenant.update(cmd.getName(), cmd.getAdminEmail(), cmd.getLevel() != null ? cmd.getLevel() : 0);
    tenantRepository.save(tenant);
  }

  @Transactional
  public void delete(Long id) {
    if (id == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_ID_REQUIRED, "租户 ID 不能为空");
    }
    if (id == PLATFORM_TENANT_ID) {
      throw IamErrors.of(IamErrorCodes.TENANT_DELETE_FORBIDDEN, "禁止删除平台租户");
    }
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
    }
    tenantDeletionGateway.purgeTenantData(id);
    tenantRepository.deleteById(id);
  }

  @Transactional
  public void enable(Long id) {
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
    }
    tenant.enable();
    tenantRepository.save(tenant);
  }

  @Transactional
  public void disable(Long id) {
    Tenant tenant = tenantRepository.findById(id);
    if (tenant == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
    }
    tenant.disable();
    tenantRepository.save(tenant);
  }

  @Transactional
  public void updateQuota(UpdateTenantQuotaCommand cmd) {
    if (cmd == null || cmd.getId() == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_ID_REQUIRED, "租户 ID 不能为空");
    }
    if (cmd.getMaxAccounts() != null && cmd.getMaxAccounts() < 0) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_INVALID, "maxAccounts 不能为负数");
    }
    if (cmd.getMaxRoles() != null && cmd.getMaxRoles() < 0) {
      throw IamErrors.of(IamErrorCodes.TENANT_QUOTA_INVALID, "maxRoles 不能为负数");
    }
    Tenant tenant = tenantRepository.findById(cmd.getId());
    if (tenant == null) {
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
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
      throw IamErrors.of(IamErrorCodes.TENANT_NOT_FOUND, "租户不存在");
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
