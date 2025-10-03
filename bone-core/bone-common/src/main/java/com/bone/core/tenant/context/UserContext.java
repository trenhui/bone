package com.bone.core.tenant.context;

import com.bone.core.auth.User;

/**
 * 当前用户上下文信息
 * @author renhui.trh
 */
public class UserContext {

    /**
     * 支持父子线程之间的数据传递
     */
    private static final ThreadLocal<User> THREAD_LOCAL_CURRENT_USER = new ThreadLocal<>();

    /**
     * 设置当前登陆用户信息
     *
     * @param user 用户信息
     */
    public static void setCurrentUser(User user) {
        THREAD_LOCAL_CURRENT_USER.set(user);
    }

    /**
     * 获取当前登陆用户信息
     *
     * @return user 用户信息
     */
    public  static User getCurrentUser() {
        return THREAD_LOCAL_CURRENT_USER.get();
    }

    /**
     * 清除用户信息
     */
    public static void clear() {
        THREAD_LOCAL_CURRENT_USER.remove();
    }
}
