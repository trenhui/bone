package com.bone.tpa.sdk.claim.exception;

import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import lombok.Getter;

@Getter
public class TpaBizException extends RuntimeException {

    //业务层错误码
    private BizErrorCode errorCode;

    /**
     * 空构造方法，避免反序列化问题
     */
    public TpaBizException() {
    }

    /**
     * 构造函数
     * @param errorCode
     */
    public TpaBizException(BizErrorCode errorCode) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
    }

    public TpaBizException(Throwable e) {
        super(String.format(BizErrorCode.UNKNOWN_ERROR.getDescription(), e.getMessage()));
        this.errorCode = BizErrorCode.UNKNOWN_ERROR;
    }

    public TpaBizException(String msg) {
        super(String.format(BizErrorCode.UNKNOWN_ERROR.getDescription(), msg));
        this.errorCode = BizErrorCode.UNKNOWN_ERROR;
    }

    public TpaBizException(BizErrorCode resultCode, String... args) {
        super(String.format(resultCode.getDescription(), args));
        this.errorCode = resultCode;
    }

}
