package com.bone.engine.extension.studio.common;

/** Studio API 稳定 errorCode，登记见 doc/architecture/Bone-错误码登记.md §EXT_。 */
public final class StudioErrorCodes {

    public static final String RESOURCE_NOT_FOUND = "EXT_RESOURCE_NOT_FOUND";
    public static final String STATE_INVALID = "EXT_STATE_INVALID";
    public static final String VALIDATION_FAILED = "COMMON_VALIDATION_FAILED";
    public static final String FORBIDDEN = "COMMON_FORBIDDEN";
    public static final String INTERNAL_ERROR = "COMMON_INTERNAL_ERROR";
    public static final String IDEMPOTENCY_CONFLICT = "COMMON_IDEMPOTENCY_CONFLICT";
    public static final String PRECONDITION_FAILED = "COMMON_PRECONDITION_FAILED";

    private StudioErrorCodes() {}
}
