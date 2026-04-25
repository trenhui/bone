package com.bone.iam.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.iam.adapter.web.converter.AccountWebConverter;
import com.bone.iam.adapter.web.dto.req.CreateAccountReq;
import com.bone.iam.adapter.web.dto.req.UpdateAccountReq;
import com.bone.iam.adapter.web.dto.resp.AccountDetailResp;
import com.bone.iam.application.command.cmd.DisableAccountCmd;
import com.bone.iam.application.command.cmd.EnableAccountCmd;
import com.bone.iam.application.command.cmd.ResetPasswordCmd;
import com.bone.iam.application.usecase.standard.CreateAccountUseCase;
import com.bone.iam.application.usecase.standard.UpdateAccountUseCase;
import com.bone.iam.application.usecase.standard.EnableAccountUseCase;
import com.bone.iam.application.usecase.standard.DisableAccountUseCase;
import com.bone.iam.application.usecase.standard.ResetPasswordUseCase;
import com.bone.iam.application.usecase.standard.AccountPageQueryUseCase;
import com.bone.iam.application.usecase.standard.DeleteAccountUseCase;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.qry.AccountPageQry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/iam/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountUseCase updateAccountUseCase;
    private final EnableAccountUseCase enableAccountUseCase;
    private final DisableAccountUseCase disableAccountUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final AccountPageQueryUseCase accountPageQueryUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final AccountWebConverter accountWebConverter;

    @PostMapping
    public ApiResponse<Long> create(@RequestBody CreateAccountReq req) {
        Long id = createAccountUseCase.execute(accountWebConverter.toCreateAccountCmd(req));
        return ApiResponse.success(id);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody UpdateAccountReq req) {
        updateAccountUseCase.execute(accountWebConverter.toUpdateAccountCmd(id, req));
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deleteAccountUseCase.execute(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        EnableAccountCmd cmd = new EnableAccountCmd();
        cmd.setId(id);
        enableAccountUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        DisableAccountCmd cmd = new DisableAccountCmd();
        cmd.setId(id);
        disableAccountUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordCmd cmd) {
        cmd.setId(id);
        resetPasswordUseCase.execute(cmd);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<AccountDetailResp> detail(@PathVariable Long id) {
        return ApiResponse.success(new AccountDetailResp());
    }

    @GetMapping
    public ApiResponse<PageResult<AccountDTO>> page(AccountPageQry qry) {
        PageResult<AccountDTO> result = accountPageQueryUseCase.execute(qry);
        return ApiResponse.success(result);
    }

    /**
     * 导入用户
     * 支持批量导入用户，用于系统初始化或数据迁移
     */
    @PostMapping("/import")
    public ApiResponse<Integer> importAccounts(@RequestBody java.util.List<CreateAccountReq> list) {
        int count = 0;
        for (CreateAccountReq req : list) {
            try {
                createAccountUseCase.execute(accountWebConverter.toCreateAccountCmd(req));
                count++;
            } catch (Exception e) {
                // 记录导入失败，继续处理其他
            }
        }
        return ApiResponse.success(count);
    }

    /**
     * 导出用户
     * 导出所有用户信息，支持过滤条件
     */
    @GetMapping("/export")
    public ApiResponse<java.util.List<AccountDTO>> export(AccountPageQry qry) {
        qry.setPageSize(10000); // 导出全部
        PageResult<AccountDTO> result = accountPageQueryUseCase.execute(qry);
        return ApiResponse.success(result.getList());
    }
}
