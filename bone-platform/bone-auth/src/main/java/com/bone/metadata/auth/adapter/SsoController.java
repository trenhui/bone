//package com.bone.cloud.auth.controller;
//
//import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
//import cn.dev33.satoken.sso.template.SaSsoUtil;
//import cn.dev33.satoken.sso.util.SaSsoConsts;
//import cn.dev33.satoken.stp.StpUtil;
//import cn.dev33.satoken.util.SaFoxUtil;
//import cn.dev33.satoken.util.SaResult;
//import com.bone.cloud.auth.domain.service.AdminAuthService;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@Slf4j
//public class SsoController {
//    @Resource
//    AdminAuthService adminAuthService;
//
//    // SSO-Server：统一认证地址
//    @RequestMapping("/sso/auth")
//    public Object ssoAuth() {
//        return SaSsoServerProcessor.instance.ssoAuth();
//    }
//
//    // SSO-Server：RestAPI 登录接口
//    @RequestMapping("/sso/doLogin")
//    public Object ssoDoLogin() {
//        return SaSsoServerProcessor.instance.ssoDoLogin();
//    }
//
//    // SSO-Server：校验ticket 获取账号id
//    @RequestMapping("/sso/checkTicket")
//    public Object ssoCheckTicket() {
//        return SaSsoServerProcessor.instance.ssoCheckTicket();
//    }
//
//    // SSO-Server：单点注销
//    @RequestMapping("/sso/signout")
//    public Object ssoSignout() {
//        return SaSsoServerProcessor.instance.ssoSignout();
//    }
//
//    /**
//     * 获取 redirectUrl
//     */
//    @RequestMapping("/sso/getRedirectUrl")
//    public SaResult getRedirectUrl(@RequestParam(value = "redirect")String redirect,@RequestParam(value = "mode") String mode,@RequestParam(value = "client") String client) {
//        // 未登录情况下，返回 code=401
//        if(StpUtil.isLogin() == false) {
//            return SaResult.code(401);
//        }
//        // 已登录情况下，构建 redirectUrl
//        redirect = SaFoxUtil.decoderUrl(redirect);
//        if(SaSsoConsts.MODE_SIMPLE.equals(mode)) {
//            // 模式一
//            SaSsoUtil.checkRedirectUrl(redirect);
//            return SaResult.data(redirect);
//        } else {
//            // 模式二或模式三
//            String redirectUrl = SaSsoUtil.buildRedirectUrl(StpUtil.getLoginId(), client, redirect);
//            return SaResult.data(redirectUrl);
//        }
//    }
//
//}
