//package com.pkh.cloud.auth.infrastructure.config;
//
//import cn.dev33.satoken.sso.config.SaSsoServerConfig;
//import cn.dev33.satoken.util.SaResult;
//import com.pkh.cloud.auth.application.vo.auth.AuthLoginReqVO;
//import com.pkh.cloud.auth.domain.service.AdminAuthService;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@Slf4j
//public class SsoLoginConfig {
//    @Resource
//    AdminAuthService adminAuthService;
//
//    @Autowired
//    private void configSso(SaSsoServerConfig ssoServer) {
//        // 配置：登录处理函数
//        ssoServer.doLoginHandle = (name, pwd) -> {
//            log.info("走登录逻辑");
//            String token = adminAuthService.login(AuthLoginReqVO.builder().username(name).password(pwd).build());
//            return SaResult.ok("登录成功").setData(token);
//        };
//
//        // 配置：Ticket校验函数
//        ssoServer.checkTicketAppendData = (loginId, result) -> {
//            System.out.println("-------- 追加返回信息到 sso-client --------");
//            // 在校验 ticket 后，给 sso-client 端追加返回信息的函数
//            //todo 设计用户信息
//
////            SysUser user = sysUserMapper.getById(loginId);
////            result.set("email", user.getEmail());
//            // result.set("user", user);  // 你也可以将整个user 对象的信息都返回到 sso-client，自由决定
//            return result;
//        };
//    }
//
//}
//
//
