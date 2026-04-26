package com.bone.tpa.push.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AmountNameEnum {
    发票总费用("发票总费用", "TotalAmount"),
    基金总支付("基金总支付", "TotalMedicalFundPayment"),
    基本统筹金额("基本统筹金额", "BasicPoolingAmount"),
    统筹起付线("统筹起付线", "PoolingThreshold"),
    统筹赔付比例("统筹赔付比例", "PoolingReimbursementRate"),
    自付一("自付一", "SelfPayPart1Amount"),
    自付二("自付二", "SelfPayPart2Amount"),
    总自费("总自费", "TotalSelfPayAmount"),
    丙类自费("丙类自费", "ClassCSelfPayAmount"),
    超限价自付("超限价自付", "ExcessLimitSelfPayAmount"),
    三方已赔("三方已赔", "ThirdPartyPaidAmount"),
    不合理金额("不合理金额", "InvalidAmount"),
    合理金额("合理金额", "InvalidAmount"),
    日限额("日限额", "allowancePerDay"),
    ;
        
    private String name;
    private String desc;
}
