package com.bone.iam.application.query.mapper;

import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.domain.account.Account;
import org.springframework.stereotype.Component;

/** 领域账户与查询 DTO 映射 */
@Component
public class AccountDtoMapper {

  public AccountDTO toDto(Account account) {
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
    dto.setPasswordUpdatedAt(account.getPasswordUpdatedAt());
    dto.setCreatedAt(account.getCreatedAt());
    dto.setUpdatedAt(account.getUpdatedAt());
    return dto;
  }
}
