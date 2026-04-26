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

/**
 * 暂未使用
 */
@Data
@Schema(name = "applyPerson", description = "申请人信息")
@PersonHead(moduleCode = FieldModelDefine.申请人, personType = PersonTypeEnum.APPLY)
public class ApplyPerson  extends ExtensibleObject<ClaimStakeholderDTO,Long> {

    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "申请人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String applyName; // 申请人身份类型

    @Schema(description = "申请人身份类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String applyIdentityType; // 申请人身份类型

    @Schema(description = "申请人身份类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String applyIdentityTypeCn ; //申请人身份类型中文


    @Schema(description = "申请人和出险人关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsure)
    private String applyRelationToOutInsure ;// 申请人和出险人关系

    @Schema(description = "申请人和出险人关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToOutInsureCn)
    private String applyRelationToOutInsureCn ;// 申请人和出险人关系中文

    @Schema(description = "申请人证件号码")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String applyIdentityNo ;// 申请人证件号码

    @Schema(description = "申请人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String applyIdentityDatePeriod ;// 申请人证件有效期


    @Schema(description = "申请人性别", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.gender)
    private String applyGender;// 申请人性别

    @Schema(description = "申请人出生日期")
    @PersonFieldMapping(PersonHolderConstants.birthday)
    private String applyBirthday;// 申请人出生日期

    @Schema(description = "申请人职业", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.occupation)
    private String applyOccupation ;

    @Schema(description = "申请人职业_中文")
    @PersonFieldMapping(PersonHolderConstants.occupationCn)
    private String applyOccupationCn ;


    @Schema(description = "申请人国籍", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.nationality)
    private String applyNationality ;

    @Schema(description = "申请人国籍_中文")
    @PersonFieldMapping(PersonHolderConstants.nationalityCn)
    private String applyNationalityCn ;

    @Schema(description = "申请人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String applyPhone;

    @Schema(description = "申请人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String applyContactRegion ;


    @Schema(description = "申请人联系地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String applyContactAddress;


    @Schema(description = "申请类型")
    private String applyType;

    @Schema(description = "申请时间")
    private String applyTime;
}
