package com.bone.lowcode.codegen.infrastructure.util;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContextUtil {
    // 线程上下文持有者
    private static final TransmittableThreadLocal<String> PACKAGE_PATH_HOLDER = new TransmittableThreadLocal<>();

    /**
     * 设置当前线程的包路径
     *
     * @param packageName 包名（如 com.bone.tpa.policy）
     */
    public static void setPackagePath(String packageName) {
        if (packageName != null) {
            PACKAGE_PATH_HOLDER.set(packageName.replace('.', '/'));
        }
    }

    /**
     * 清除当前线程的包路径
     */
    public static void clearPackagePath() {
        PACKAGE_PATH_HOLDER.remove();
    }

    /**
     * 获取当前线程的包路径
     *
     * @return 转换后的包路径（如 com/bone/tpa/policy）
     */
    public static String getCurrentPackagePath() {
        String path = PACKAGE_PATH_HOLDER.get();
        return path != null ? path : "${table.packgeName}"; // 安全回退
    }
}