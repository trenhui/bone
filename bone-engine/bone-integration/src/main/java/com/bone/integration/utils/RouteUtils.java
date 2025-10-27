package com.bone.integration.utils;

public class RouteUtils {

    public static String getRouteId(String appCode, String flowKey, String version, boolean isSupportMultiVersion) {
        if (isSupportMultiVersion) {
            return appCode + "/" + flowKey + "/" + version;
        } else {
            return appCode + "/" + flowKey;
        }
    }
}
