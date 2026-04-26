package com.bone.tpa.claim.application.response;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.claim.application.dto.ClaimStakeholderDTO;
import com.bone.tpa.claim.application.transfer.PersonFieldMapping;
import com.bone.tpa.claim.application.transfer.PersonHead;
import com.bone.tpa.claim.application.transfer.PersonHolderConstants;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 领款人信息
 * 业务对象
 */
@Data
@Schema(name = "collectPerson", description = "领款人(个人)信息")
@PersonHead(moduleCode = FieldModelDefine.领款人信息, personType = PersonTypeEnum.COLLECT)
public class CollectPerson extends ExtensibleObject<ClaimStakeholderDTO,Long> {

//    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "领款人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String collectName;
    @Schema(description = "领款人性别", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.gender)
    private String collectGender;
    @Schema(description = "领款人出生年月")
    @PersonFieldMapping(PersonHolderConstants.birthday)
    private String collectBirthday;

    @Schema(description = "领款人证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String collectIdentityType;

    @Schema(description = "领款人证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String collectIdentityTypeCn;


    @Schema(description = "领款人证件号")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String collectIdentityNo;


    @Schema(description = "领款人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String collectIdentityDatePeriod;

    @Schema(description = "领款人职业", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.occupation)
    private String collectOccupation;

    @Schema(description = "领款人职业_名称")
    @PersonFieldMapping(PersonHolderConstants.occupationCn)
    private String collectOccupationCn;


    @Schema(description = "领款人国籍", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.nationality)
    private String collectNationality;


    @Schema(description = "领款人国籍_中文")
    @PersonFieldMapping(PersonHolderConstants.nationalityCn)
    private String collectNationalityCn;

    @Schema(description = "领款人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String collectPhone;


    @Schema(description = "领款人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String collectContactRegion;


    @Schema(description = "领款人详细地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String collectContactAddress;


    @Schema(description = "领款人与出险人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsure)
    private String collectRelationToOutInsure;


    @Schema(description = "领款人与出险人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsureCn)
    private String collectRelationToOutInsureCn;


    @Schema(description = "领款人与被保人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsure)
    private String collectRelationToMainInsure;


    @Schema(description = "领款人与被保人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsureCn)
    private String collectRelationToMainInsureCn;


    @Schema(description = "领款人与受益人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToBenefit)
    private String collectRelationToBenefit;


    @Schema(description = "领款人与受益人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToBenefitCn)
    private String collectRelationToBenefitCn;


    @Schema(description = "给付方式", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.paymentMethodType)
    private String personPaymentMethodType;


    @Schema(description = "给付方式_中文")
    @PersonFieldMapping(PersonHolderConstants.paymentMethodTypeCn)
    private String personPaymentMethodTypeCn;


    @Schema(description = "银行账号")
    @PersonFieldMapping(PersonHolderConstants.accountNo)
    private String personAccountNo;


    @Schema(description = "开户行", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.bankCode)
    private String personBankCode;

    @Schema(description = "开户行_中文")
    @PersonFieldMapping(PersonHolderConstants.bankCodeCn)
    private String personBankCodeCn;

    @Schema(description = "开户行分行", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.branchCode)
    private String personBranchCode;

    @Schema(description = "开户行分行_中文")
    @PersonFieldMapping(PersonHolderConstants.branchCodeCn)
    private String personBranchCodeCn;


    @Schema(description = "开户行地址")
    @PersonFieldMapping(PersonHolderConstants.bankAddress)
    private String personBankAddress;


    @Schema(description = "银行省市", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.bankRegion)
    private String personBankRegion;


    @Schema(description = "领款人专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);
    private  Map<String,Object>  extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
