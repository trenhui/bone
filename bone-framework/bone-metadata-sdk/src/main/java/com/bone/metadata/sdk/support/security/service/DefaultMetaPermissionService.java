package com.bone.metadata.sdk.support.security.service;

/**
 * todo more
 */
public class DefaultMetaPermissionService implements MetaPermissionService {
    /**
     * 判断指定用户对指定元数据名称是否拥有某种动作权限（如 "READ","WRITE"）。
     *
     * @param username
     * @param metaName
     * @param permission
     */
    @Override
    public boolean hasPermission(String username, String metaName, String permission) {
        return false;
    }

    /**
     * 判断指定用户对指定元数据 ID 是否拥有某种动作权限。
     *
     * @param username
     * @param metaId
     * @param permission
     */
    @Override
    public boolean hasPermission(String username, Long metaId, String permission) {
        return false;
    }

    /**
     * 默认永远返回 true，表示通过权限校验。
     */
    @Override
    public boolean hasPermission(String resource, String action) {
        return true;
    }

    /**
     * 判断当前登陆用户对指定元数据资源 ，是否拥有权限。
     *
     * @param resource
     */
    @Override
    public boolean hasPermission(String resource) {
        return false;
    }
}
