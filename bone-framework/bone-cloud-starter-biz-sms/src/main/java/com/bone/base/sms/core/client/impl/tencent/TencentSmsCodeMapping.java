package com.bone.base.sms.core.client.impl.tencent;

import com.bone.base.core.exception.ErrorCode;
import com.bone.base.core.exception.enums.GlobalErrorCodeConstants;
import com.bone.base.sms.core.client.SmsCodeMapping;
import com.bone.base.sms.core.enums.SmsFrameworkErrorCodeConstants;

/**
 * 腾讯云的 SmsCodeMapping 实现类
 *
 * 参见 https://cloud.tencent.com/document/api/382/52075#.E5.85.AC.E5.85.B1.E9.94.99.E8.AF.AF.E7.A0.81
 *
 * @author : shiwp
 */
public class TencentSmsCodeMapping implements SmsCodeMapping {

    @Override
    public ErrorCode apply(String apiCode) {
        switch (apiCode) {
            case TencentSmsClient.API_SUCCESS_CODE: return GlobalErrorCodeConstants.SUCCESS;
            case "FailedOperation.ContainSensitiveWord": return SmsFrameworkErrorCodeConstants.SMS_SEND_CONTENT_INVALID;
            case "FailedOperation.JsonParseFail":
            case "MissingParameter.EmptyPhoneNumberSet":
            case "LimitExceeded.PhoneNumberCountLimit":
            case "FailedOperation.FailResolvePacket": return GlobalErrorCodeConstants.BAD_REQUEST;
            case "FailedOperation.InsufficientBalanceInSmsPackage": return SmsFrameworkErrorCodeConstants.SMS_ACCOUNT_MONEY_NOT_ENOUGH;
            case "FailedOperation.MarketingSendTimeConstraint": return SmsFrameworkErrorCodeConstants.SMS_SEND_MARKET_LIMIT_CONTROL;
            case "FailedOperation.PhoneNumberInBlacklist": return SmsFrameworkErrorCodeConstants.SMS_MOBILE_BLACK;
            case "FailedOperation.SignatureIncorrectOrUnapproved": return SmsFrameworkErrorCodeConstants.SMS_SIGN_INVALID;
            case "FailedOperation.MissingTemplateToModify":
            case "FailedOperation.TemplateIncorrectOrUnapproved": return SmsFrameworkErrorCodeConstants.SMS_TEMPLATE_INVALID;
            case "InvalidParameterValue.IncorrectPhoneNumber": return SmsFrameworkErrorCodeConstants.SMS_MOBILE_INVALID;
            case "InvalidParameterValue.SdkAppIdNotExist": return SmsFrameworkErrorCodeConstants.SMS_APP_ID_INVALID;
            case "InvalidParameterValue.TemplateParameterLengthLimit":
            case "InvalidParameterValue.TemplateParameterFormatError": return SmsFrameworkErrorCodeConstants.SMS_TEMPLATE_PARAM_ERROR;
            case "LimitExceeded.PhoneNumberDailyLimit": return SmsFrameworkErrorCodeConstants.SMS_SEND_DAY_LIMIT_CONTROL;
            case "LimitExceeded.PhoneNumberThirtySecondLimit":
            case "LimitExceeded.PhoneNumberOneHourLimit": return SmsFrameworkErrorCodeConstants.SMS_SEND_BUSINESS_LIMIT_CONTROL;
            case "UnauthorizedOperation.RequestPermissionDeny":
            case "FailedOperation.ForbidAddMarketingTemplates":
            case "FailedOperation.NotEnterpriseCertification":
            case "UnauthorizedOperation.IndividualUserMarketingSmsPermissionDeny": return SmsFrameworkErrorCodeConstants.SMS_PERMISSION_DENY;
            case "UnauthorizedOperation.RequestIpNotInWhitelist": return SmsFrameworkErrorCodeConstants.SMS_IP_DENY;
            case "AuthFailure.SecretIdNotFound": return SmsFrameworkErrorCodeConstants.SMS_ACCOUNT_INVALID;
        }
        return SmsFrameworkErrorCodeConstants.SMS_UNKNOWN;
    }
}