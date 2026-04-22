package com.bone.iam.adapter.web.converter;

import com.bone.iam.adapter.web.dto.req.LoginReq;
import com.bone.iam.adapter.web.dto.resp.LoginResp;
import com.bone.iam.application.command.cmd.LoginCmd;
import com.bone.iam.domain.model.user.User;
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
        
        User user = (User) result.get("user");
        LoginResp.UserInfo userInfo = new LoginResp.UserInfo();
        userInfo.setId(user.getId().getValue());
        userInfo.setUsername(user.getUsername().value());
        userInfo.setEmail(user.getEmail().value());
        resp.setUser(userInfo);
        
        return resp;
    }
}