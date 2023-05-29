package com.bone.core.result;

import io.micrometer.common.util.StringUtils;
import lombok.Data;

/**
 * @author renhui.trh
 */
@Data
public class Result<T> {

    public static final Integer DEFAULT_SUCCESS_CODE = 200;

    public static final Integer DEFAULT_ERROR_CODE = 500;

    public static final String DEFAULT_SUCCESS_MSG = "操作成功";

    public static final String DEFAULT_ERROR_MSG = "操作出错";

    private static final long serialVersionUID = 1L;
    /**
     * 请求是否成功
     */
    private Boolean success;

    /**
     * 响应状态码
     */
    private Integer code;

    /**
     * 响应信息
     */
    private String message;

    /**
     * 响应对象
     */
    private T data;

    public Result(Boolean success) {
        this.success = success;
        if (success) {
            code = ResultCode.SUCCESS.getCode();
            message = ResultCode.SUCCESS.getMsg();
        } else {
            code = ResultCode.SERVER_ERROR.getCode();
            message = ResultCode.SERVER_ERROR.getMsg();
        }
    }

    public static <T> Result<T> error(String msg) {
        return error(ResultCode.SERVER_ERROR.getCode(), msg);
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>(false);
        r.code = code;
        r.message = msg;
        return r;
    }

    public static <T> Result<T> ok(T value) {
        return ok(null, value);
    }

    public static <T> Result<T>  ok(String msg, T value) {
        Result<T> r = new Result<>(true);
        if (StringUtils.isNotBlank(msg)) {
            r.message = msg;
        }
        r.data = value;
        return r;
    }

    public static <T> Result<T> ok() {
        return new Result<>(true);
    }
}
