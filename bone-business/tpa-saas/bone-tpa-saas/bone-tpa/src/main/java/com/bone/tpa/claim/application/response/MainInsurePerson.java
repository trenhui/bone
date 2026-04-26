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
 * 主被保人信息
 * 业务对象
 */
@Data
@Schema(name = "mainInsurePerson", description = "主被保人信息")
@PersonHead(moduleCode = FieldModelDefine.主被保险人, personType = PersonTypeEnum.MAIN_INSURE)
public class MainInsurePerson extends ExtensibleObject<ClaimStakeholderDTO,Long> {

//    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "主被保人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String mainInsureName;
    @Schema(description = "主被保人性别", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.gender)
    private String mainInsureGender;
    @Schema(description = "主被保人出生年月")
    @PersonFieldMapping(PersonHolderConstants.birthday)
    private String mainInsureBirthday;

    @Schema(description = "主被保人证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String mainInsureIdentityType;
    @Schema(description = "主被保人证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String mainInsureIdentityTypeCn;


    @Schema(description = "主被保人证件号")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String mainInsureIdentityNo;
    @Schema(description = "主被保人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String mainInsureIdentityDatePeriod;

    @Schema(description = "主被保人职业", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.occupation)
    private String mainInsureOccupation;

    @Schema(description = "主被保人职业_中文")
    @PersonFieldMapping(PersonHolderConstants.occupationCn)
    private String mainInsureOccupationCn;

    @Schema(description = "主被保人国籍", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.nationality)
    private String mainInsureNationality;

    @Schema(description = "主被保人国籍_中文")
    @PersonFieldMapping(PersonHolderConstants.nationalityCn)
    private String mainInsureNationalityCn;

    @Schema(description = "主被保人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String mainInsurePhone;


    @Schema(description = "主被保人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String mainInsureContactRegion;


    @Schema(description = "主被保人详细地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String mainInsureContactAddress;


    @Schema(description = "主被保人与出险人的关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsure)
    private String mainInsureRelationToOutInsure;

    @Schema(description = "主被保人与出险人的关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsureCn)
    private String mainInsureRelationToOutInsureCn;


    private  Map<String,Object>  extraStore;


    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
