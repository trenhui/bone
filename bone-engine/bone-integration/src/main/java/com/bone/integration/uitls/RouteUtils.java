package com.bone.integration.uitls;

public class RouteUtils {

    public static String getRouteId(String appCode, String flowKey, String version, boolean isSupportMultiVersion) {
        if (isSupportMultiVersion) {
            return appCode + "/" + flowKey + "/" + version;
        } else {
            return appCode + "/" + flowKey;
        }
    }
}
