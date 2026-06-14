package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.iam.application.query.dto.TenantDTO;
import com.bone.iam.application.query.qry.TenantPageQuery;
import com.bone.iam.domain.tenant.Tenant;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TenantPageQueryHandler {

  @Transactional(readOnly = true)
  public PageResult<TenantDTO> handle(TenantPageQuery qry) {
    FluentQuery<Tenant> query = QueryBuilder.from(Tenant.class);
    if (qry.getCode() != null && !qry.getCode().isBlank()) {
      query.where(Tenant::getCode).like("%" + qry.getCode() + "%");
    }
    if (qry.getName() != null && !qry.getName().isBlank()) {
      query.where(Tenant::getName).like("%" + qry.getName() + "%");
    }
    int page = qry.getPage() != null ? qry.getPage() : 1;
    int size = qry.getSize() != null ? qry.getSize() : 10;
    PageResult<Tenant> result = query.orderByDesc(Tenant::getCreatedAt).page(page, size);
    List<TenantDTO> records = result.getRecords().stream().map(this::toDto).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  private TenantDTO toDto(Tenant tenant) {
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
