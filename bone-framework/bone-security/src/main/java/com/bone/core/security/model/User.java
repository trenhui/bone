package com.bone.core.security.model;

import com.bone.core.security.enums.UserType;
import java.util.List;

/**
 * @author renhui.trh 2023-10-30
 */
public class User {

    /**
     * 获取当前登陆用户id
     */
    private Long id;

    /**
     * 当前登陆用户名
     */
    private String loginName;

    /**
     * 当前登陆用户租户id
     */
    private Long tenantId;

    /**
     * 当前登陆用户租户Code
     */
    private String tenantCode;

    /**
     * 当前登陆用户bizIdentityCode
     */
    private String bizIdentityCode;


    /**
     * 当前登陆用户类型
     */
    private UserType userType;

    /**
     * 授权范围
     */
    private List<String> scopes;
}
