package com.bone.core.integration;

import java.util.HashMap;
import java.util.Map;

public class RequestContext {

    // 使用 ThreadLocal 存储每个线程的上下文
    private static final ThreadLocal<Map<String, Object>> CONTEXT = ThreadLocal.withInitial(HashMap::new);

    /**
     * 设置上下文值
     *
     * @param key   键
     * @param value 值
     */
    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    /**
     * 获取上下文值
     *
     * @param key 键
     * @return 值
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) CONTEXT.get().get(key);
    }

    /**
     * 移除上下文中的一个键值对
     *
     * @param key 键
     */
    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /**
     * 清理当前线程的上下文
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前线程上下文是否为空
     *
     * @return 是否为空
     */
    public static boolean isEmpty() {
        return CONTEXT.get().isEmpty();
    }

    /**
     * 示例方法：设置和获取 partnerCode
     */
    public static void setPartnerCode(String partnerCode) {
        set("partnerCode", partnerCode);
    }

    public static String getPartnerCode() {
        return get("partnerCode");
    }

    public static String getRequestId() {
        return get("requestId");
    }
}
