package com.bone.iam.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.core.tenant.context.TenantContext;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.qry.AccountPageQuery;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountPageQueryHandler {
  @Transactional(readOnly = true)
  public PageResult<AccountDTO> handle(AccountPageQuery qry) {
    FluentQuery<Account> query = QueryBuilder.from(Account.class);

    if (qry.getKeyword() != null && !qry.getKeyword().isEmpty()) {
      query
          .where(Account::getUsername)
          .like(qry.getKeyword())
          .or(Account::getEmail)
          .like(qry.getKeyword())
          .or(Account::getRealName)
          .like(qry.getKeyword());
    }

    if (qry.getStatus() != null) {
      query.where(Account::getStatus).eq(AccountStatus.of(qry.getStatus()));
    }

    // 租户隔离：JWT 中的 tenantId 优先（非平台租户强制按其过滤），
    // 平台租户（0）或无上下文时回退到查询参数（兼容平台超管 / 单元测试）。详设 §3.4 / §4.8。
    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    if (effectiveTenant != null) {
      query.where(Account::getTenantId).eq(effectiveTenant);
    }

    PageResult<Account> result =
        query.orderByDesc(Account::getCreatedAt).page(qry.getPage(), qry.getSize());

    List<AccountDTO> dtoList =
        result.getRecords().stream().map(this::convertToDto).collect(Collectors.toList());

    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  /**
   * 解析有效租户过滤：
   *
   * <ul>
   *   <li>JWT/TenantContext 中非平台租户（&gt; 0）→ 强制按其过滤，忽略调用方传参；
   *   <li>平台租户（0L）→ 允许调用方传 {@code qry.tenantId} 显式选择，未传则不加过滤；
   *   <li>无 TenantContext（未走 JWT 链路，例如单元测试）→ 回退到 {@code qry.tenantId}。
   * </ul>
   */
  static Long resolveTenantFilter(Long fromQuery) {
    Long fromContext = TenantContext.getTenantId();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }

  private AccountDTO convertToDto(Account account) {
    AccountDTO dto = new AccountDTO();
    dto.setId(account.getId());
    dto.setUsername(account.getUsername().value());
    dto.setEmail(account.getEmail().value());
    dto.setPhone(account.getPhone());
    dto.setRealName(account.getRealName());
    dto.setAvatarUrl(account.getAvatarUrl());
    dto.setStatus(account.getStatus() != null ? account.getStatus().getCode() : null);
    dto.setIsAdmin(account.isAdmin());
    dto.setTenantId(account.getTenantId());
    dto.setLastLoginAt(account.getLastLoginAt());
    dto.setLastLoginIp(account.getLastLoginIp());
    dto.setCreatedAt(account.getCreatedAt());
    dto.setUpdatedAt(account.getUpdatedAt());
    return dto;
  }
}
