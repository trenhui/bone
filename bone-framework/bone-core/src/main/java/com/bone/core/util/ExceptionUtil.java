package com.bone.core.util;

public class ExceptionUtil {

    /**
     * 获取异常链中根异常的消息
     *
     * @param throwable 要处理的异常
     * @return 根异常的消息
     */
    public static String getRootCauseMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable rootCause = getRootCause(throwable);
        String message = rootCause.getMessage();
        return message != null ? message : rootCause.toString();
    }

    /**
     * 获取异常链中根异常
     *
     * @param throwable 要处理的异常
     * @return 根异常
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }

        return rootCause;
    }
}
