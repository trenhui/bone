package com.bone.metadata.auth.domain.service;


import com.bone.metadata.auth.application.vo.auth.AuthLoginReqVO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import jakarta.validation.Valid;

/**
 * 管理后台的认证 Service 接口
 *
 * 提供用户的登录、登出的能力
 *
 * @author 
 */
public interface AdminAuthService {

    /**
     * 验证账号 + 密码。如果通过，则返回用户
     *
     * @param username 账号
     * @param password 密码
     * @return 用户
     */
    AdminUserDO authenticate(String username, String password);

    /**
     * 账号登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    String login(@Valid AuthLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param logType 登出类型
     */
    void logout( Integer logType);

//    /**
//     * 短信验证码发送
//     *
//     * @param reqVO 发送请求
//     */
//    void sendSmsCode(AuthSmsSendReqVO reqVO);
//
//    /**
//     * 短信登录
//     *
//     * @param reqVO 登录信息
//     * @return 登录结果
//     */
//    AuthLoginRespVO smsLogin(AuthSmsLoginReqVO reqVO) ;
//
//    /**
//     * 社交快捷登录，使用 code 授权码
//     *
//     * @param reqVO 登录信息
//     * @return 登录结果
//     */
//    AuthLoginRespVO socialLogin(@Valid AuthSocialLoginReqVO reqVO);
//
//    /**
//     * 刷新访问令牌
//     *
//     * @param refreshToken 刷新令牌
//     * @return 登录结果
//     */
//    AuthLoginRespVO refreshToken(String refreshToken);

}
