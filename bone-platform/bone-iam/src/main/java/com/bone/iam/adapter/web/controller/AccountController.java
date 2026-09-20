package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import com.bone.iam.adapter.web.converter.AccountWebConverter;
import com.bone.iam.adapter.web.dto.request.CreateAccountReq;
import com.bone.iam.adapter.web.dto.request.ResetPasswordReq;
import com.bone.iam.adapter.web.dto.request.UpdateAccountReq;
import com.bone.iam.adapter.web.dto.response.AccountDetailResp;
import com.bone.iam.application.AccountApplicationService;
import com.bone.iam.application.command.cmd.DisableAccountCommand;
import com.bone.iam.application.command.cmd.EnableAccountCommand;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.qry.AccountPageQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping(PlatformApiPaths.IAM_V1 + "/accounts")
@RequiredArgsConstructor
public class AccountController {

  /** 单次导出最多返回的账号数（避免一次性把整张表拉到内存）。 */
  private static final int EXPORT_MAX_SIZE = 10000;

  private final AccountApplicationService accountApplicationService;
  private final AccountWebConverter accountWebConverter;

  @PostMapping
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Long> create(@RequestBody CreateAccountReq req) {
    Long id = accountApplicationService.create(accountWebConverter.toCreateAccountCommand(req));
    return ApiResponse.success(id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateAccountReq req) {
    accountApplicationService.update(accountWebConverter.toUpdateAccountCommand(id, req));
    return ApiResponse.success();
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Void> delete(@PathVariable Long id) {
    accountApplicationService.delete(id);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/enable")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Void> enable(@PathVariable Long id) {
    EnableAccountCommand cmd = new EnableAccountCommand();
    cmd.setId(id);
    accountApplicationService.enable(cmd);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/disable")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Void> disable(@PathVariable Long id) {
    DisableAccountCommand cmd = new DisableAccountCommand();
    cmd.setId(id);
    accountApplicationService.disable(cmd);
    return ApiResponse.success();
  }

  @PostMapping("/{id}/reset-password")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordReq req) {
    accountApplicationService.resetPassword(accountWebConverter.toResetPasswordCommand(id, req));
    return ApiResponse.success();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('iam:accounts:read')")
  public ApiResponse<AccountDetailResp> detail(@PathVariable Long id) {
    AccountDetailResp resp =
        accountApplicationService
            .detail(id)
            .map(accountWebConverter::toDetailResp)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在"));
    return ApiResponse.success(resp);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('iam:accounts:read')")
  public ApiResponse<PageResult<AccountDTO>> page(AccountPageQuery qry) {
    PageResult<AccountDTO> result = accountApplicationService.page(qry);
    return ApiResponse.success(result);
  }

  /** 批量导入账号；失败项跳过并写日志，返回成功数量。 */
  @PostMapping("/import")
  @PreAuthorize("hasAuthority('iam:accounts:write')")
  public ApiResponse<Integer> importAccounts(@RequestBody List<CreateAccountReq> list) {
    if (list == null || list.isEmpty()) {
      return ApiResponse.success(0);
    }
    List<String> failures = new ArrayList<>();
    int success = 0;
    for (CreateAccountReq req : list) {
      try {
        accountApplicationService.create(accountWebConverter.toCreateAccountCommand(req));
        success++;
      } catch (RuntimeException e) {
        failures.add(req.getUsername() + ": " + e.getMessage());
        log.warn("import account failed: username={}", req.getUsername(), e);
      }
    }
    if (!failures.isEmpty()) {
      log.warn("import accounts finished: success={}, failed={}", success, failures.size());
    }
    return ApiResponse.success(success);
  }

  /** 导出账号；最大返回 {@value #EXPORT_MAX_SIZE} 条，超出请用过滤条件分批导出。 */
  @GetMapping("/export")
  @PreAuthorize("hasAuthority('iam:accounts:read')")
  public ApiResponse<List<AccountDTO>> export(AccountPageQuery qry) {
    qry.setSize(EXPORT_MAX_SIZE);
    PageResult<AccountDTO> result = accountApplicationService.page(qry);
    if (result.getTotal() > EXPORT_MAX_SIZE) {
      log.warn(
          "account export truncated: total={}, returned={}",
          result.getTotal(),
          result.getRecords().size());
    }
    return ApiResponse.success(result.getRecords());
  }
}
