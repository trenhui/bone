package com.bone.core.tenant.context;

/**
 * 当前业务身份上下文信息
 * @author renhui.trh
 */
public class BizIdentityContext {

    /**
     * 支持父子线程之间的数据传递
     */
    private static final ThreadLocal<String>  Biz_Identity_CONTEXT  = new ThreadLocal<>();

    /**
     * 设置当前业务身份信息
     *
     * @param bizIdentityCode  业务身份
     */
    public static void setBizIdentityCoder(String bizIdentityCode ) {
        Biz_Identity_CONTEXT.set(bizIdentityCode);
    }

    /**
     * 获取当前登陆用户信息
     *
     * @return user 用户信息
     */
    public  static String getBizIdentityCode() {
        return Biz_Identity_CONTEXT.get();
    }

    /**
     * 清除用户信息
     */
    public static void clear() {
        Biz_Identity_CONTEXT.remove();
    }
}
