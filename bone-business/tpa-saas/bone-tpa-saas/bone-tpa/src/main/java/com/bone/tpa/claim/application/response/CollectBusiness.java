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
 * 领款单位信息
 * 业务对象
 */
@Data
@Schema(name = "collectBusiness", description = "领款人(企业)信息")
@PersonHead(moduleCode = FieldModelDefine.领款人信息, personType = PersonTypeEnum.COLLECT_BUSINESS)
public class CollectBusiness extends ExtensibleObject<ClaimStakeholderDTO,Long> {

//    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "领款单位名称")
    @PersonFieldMapping(PersonHolderConstants.businessName)
    private String businessName;
    @Schema(description = "单位证件描述")
    @PersonFieldMapping(PersonHolderConstants.businessIdentityDisc)
    private String businessIdentityDisc;

    @Schema(description = "单位证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.businessIdentityType)
    private String businessIdentityType;

    @Schema(description = "单位证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.businessIdentityTypeCn)
    private String businessIdentityTypeCn;


    @Schema(description = "单位证件号码")
    @PersonFieldMapping(PersonHolderConstants.businessIdentityNo)
    private String businessIdentityNo;

    @Schema(description = "单位证件有效期")
    @PersonFieldMapping(PersonHolderConstants.businessIdentityDatePeriod)
    private String businessIdentityDatePeriod;

    @Schema(description = "单位经营场所")
    @PersonFieldMapping(PersonHolderConstants.businessPlace)
    private String businessPlace;

    @Schema(description = "单位经营范围")
    @PersonFieldMapping(PersonHolderConstants.businessRange)
    private String businessRange;

    @Schema(description = "法人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String legalName;

    @Schema(description = "法人证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String legalIdentityType;

    @Schema(description = "法人证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String legalIdentityTypeCn;


    @Schema(description = "法人证件号")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String legalIdentityNo;

    @Schema(description = "法人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String legalIdentityDatePeriod;

    @Schema(description = "法人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String legalPhone;


    @Schema(description = "法人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String legalContactRegion;


    @Schema(description = "法人详细地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String legalContactAddress;


    @Schema(description = "领款单位与出险人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsure)
    private String businessRelationToOutInsure;

    @Schema(description = "领款单位与出险人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsureCn)
    private String businessRelationToOutInsureCn;


    @Schema(description = "领款单位与被保人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsure)
    private String businessRelationToMainInsure;


    @Schema(description = "领款单位与被保人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsureCn)
    private String businessRelationToMainInsureCn;


    @Schema(description = "领款单位与受益人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToBenefit)
    private String businessRelationToBenefit;

    @Schema(description = "领款单位与受益人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToBenefitCn)
    private String businessRelationToBenefitCn;


    @Schema(description = "给付方式", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.paymentMethodType)
    private String businessPaymentMethodType;


    @Schema(description = "给付方式_中文")
    @PersonFieldMapping(PersonHolderConstants.paymentMethodTypeCn)
    private String businessPaymentMethodTypeCn;



    @Schema(description = "银行账号")
    @PersonFieldMapping(PersonHolderConstants.accountNo)
    private String businessAccountNo;

    @Schema(description = "开户行", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.bankCode)
    private String businessBankCode;

    @Schema(description = "开户行_中文")
    @PersonFieldMapping(PersonHolderConstants.bankCodeCn)
    private String businessBankCodeCn;


    @Schema(description = "开户行分行", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.branchCode)
    private String businessBranchCode;


    @Schema(description = "开户行分行_中文")
    @PersonFieldMapping(PersonHolderConstants.branchCodeCn)
    private String businessBranchCodeCn;

    @Schema(description = "开户行地址")
    @PersonFieldMapping(PersonHolderConstants.bankAddress)
    private String businessBankAddress;


    @Schema(description = "银行省市", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.bankRegion)
    private String businessBankRegion;


    @Schema(description = "领款单位专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);
    private  Map<String,Object>  extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
