package com.bone.core.domain.entity;

/**
 * @author renhui.trh
 */
public interface User {

    /**
     *获取当前登陆用户id
     * @return user id
     */
    Long  getId();

    /**
     * 获取当前登陆用户名
     * @return login name
     */
    //String  getLoginName();

    /**
     *获取当前登陆用户租户id
     * @return user tenantId
     */
    Long  getTenantId();

    /**
     *获取当前登陆用户租户id
     * @return user UserType
     */
    Integer  getUserType();
}
