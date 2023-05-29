package com.bone.base.sms.core.client.impl.debug;

import com.bone.base.core.exception.ErrorCode;
import com.bone.base.core.exception.enums.GlobalErrorCodeConstants;
import com.bone.base.sms.core.client.SmsCodeMapping;
import com.bone.base.sms.core.enums.SmsFrameworkErrorCodeConstants;

import java.util.Objects;

/**
 * 钉钉的 SmsCodeMapping 实现类
 *
 * @author 芋道源码
 */
public class DebugDingTalkCodeMapping implements SmsCodeMapping {

    @Override
    public ErrorCode apply(String apiCode) {
        return Objects.equals(apiCode, "0") ? GlobalErrorCodeConstants.SUCCESS : SmsFrameworkErrorCodeConstants.SMS_UNKNOWN;
    }

}
