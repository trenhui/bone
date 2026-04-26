package com.bone.tpa.claim.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 个人信息对象
 */
@Data
public class ClaimStakeholderDTO extends ExtensibleObject<ClaimStakeholderDTO,Long> {

    @Schema(description = "相关人类型")
    private String personType;

    @Schema(description = "姓名")
    private String name;
    @Schema(description = "性别")
    private String gender;
    @Schema(description = "出生年月")
    private String birthday;

    @Schema(description = "证件类型")
    private String identityType;
    @Schema(description = "证件号")
    private String identityNo;
    @Schema(description = "证件有效期")
    private String identityDatePeriod;

    @Schema(description = "职业")
    private String occupation;
    @Schema(description = "国籍")
    private String nationality;
    @Schema(description = "联系方式")
    private String phone;
    @Schema(description = "联系地址")
    private String contactAddress;

    @Schema(description = "与出险人的关系")
    private String relationToOutInsure;
    @Schema(description = "与被保人的关系")
    private String relationToMainInsure;
    @Schema(description = "与受益人的关系")
    private String relationToBenefit;

    @Schema(description = "收益比例")
    private String benefitPercentage;

    @Schema(description = "转账方式")
    private String transferMethodType;
    @Schema(description = "给付方式")
    private String paymentMethodType;
    @Schema(description = "银行账号")
    private String accountNo;
    @Schema(description = "开户行")
    private String bankCode;
    @Schema(description = "开户行分行")
    private String branchCode;

    private Map<String,Object> extraStore;
}
