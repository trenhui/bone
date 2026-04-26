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
 * 受益人信息
 * 业务对象
 */
@Data
@Schema(name = "benefitPerson", description = "受益人信息")
@PersonHead(moduleCode = FieldModelDefine.受益人, personType = PersonTypeEnum.BENEFIT)
public class BenefitPerson extends ExtensibleObject<ClaimStakeholderDTO,Long> {

//    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "受益人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String benefitName;

    @Schema(description = "受益人性别", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.gender)
    private String benefitGender;

    @Schema(description = "受益人出生年月")
    @PersonFieldMapping(PersonHolderConstants.birthday)
    private String benefitBirthday;

    @Schema(description = "受益人证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String benefitIdentityType;

    @Schema(description = "受益人证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String benefitIdentityTypeCn;

    @Schema(description = "受益人证件号")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String benefitIdentityNo;


    @Schema(description = "受益人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String benefitIdentityDatePeriod;

    @Schema(description = "受益人职业", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.occupation)
    private String benefitOccupation;

    @Schema(description = "受益人职业_中文")
    @PersonFieldMapping(PersonHolderConstants.occupationCn)
    private String benefitOccupationCn;


    @Schema(description = "受益人国籍", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.nationality)
    private String benefitNationality;

    @Schema(description = "受益人国籍_中文")
    @PersonFieldMapping(PersonHolderConstants.nationalityCn)
    private String benefitNationalityCn;


    @Schema(description = "受益人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String benefitPhone;


    @Schema(description = "收益人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String benefitContactRegion;


    @Schema(description = "受益人联系地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String benefitContactAddress;

    @Schema(description = "受益人与出险人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsure)
    private String benefitRelationToOutInsure;

    @Schema(description = "受益人与出险人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsureCn)
    private String benefitRelationToOutInsureCn;


    @Schema(description = "受益人与被保人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsure)
    private String benefitRelationToMainInsure;

    @Schema(description = "受益人与被保人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsureCn)
    private String benefitRelationToMainInsureCn;


    @Schema(description = "受益比例")
    @PersonFieldMapping(PersonHolderConstants.benefitPercentage)
    private String benefitPercentage;


    @Schema(description = "受益人专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);


    private Map<String,Object> extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
