package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.LoginReq;
import com.bone.iam.adapter.web.dto.resp.LoginResp;
import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.domain.account.Account;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthWebConverter {
    public LoginCmd toLoginCmd(LoginReq req) {
        LoginCmd cmd = new LoginCmd();
        cmd.setUsername(req.getUsername());
        cmd.setPassword(req.getPassword());
        return cmd;
    }

    public LoginResp toLoginResp(Map<String, Object> result) {
        LoginResp resp = new LoginResp();
        resp.setToken((String) result.get("token"));
        resp.setRefreshToken((String) result.get("refreshToken"));

        Account account = (Account) result.get("account");
        LoginResp.AccountInfo accountInfo = new LoginResp.AccountInfo();
        accountInfo.setId(account.getId());
        accountInfo.setUsername(account.getUsername().value());
        accountInfo.setEmail(account.getEmail().value());
        accountInfo.setPhone(account.getPhone());
        accountInfo.setRealName(account.getRealName());
        accountInfo.setAvatarUrl(account.getAvatarUrl());
        accountInfo.setStatus(account.getStatus().getCode());
        accountInfo.setIsAdmin(account.isAdmin());
        accountInfo.setLastLoginAt(account.getLastLoginAt());
        accountInfo.setCreatedAt(account.getCreatedAt());
        resp.setAccount(accountInfo);

        return resp;
    }
}
