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
 * 出险人信息
 * 业务对象
 */
@Data
@Schema(name = "outInsurePerson", description = "出险人信息")
@PersonHead(moduleCode = FieldModelDefine.出险人, personType = PersonTypeEnum.OUT_INSURE)
public class OutInsurePerson extends ExtensibleObject<ClaimStakeholderDTO,Long> {

//    @Schema(description = "关联赔案id")
    @PersonFieldMapping(PersonHolderConstants.relatedId)
    private Long relatedId;

    @Schema(description = "出险人姓名")
    @PersonFieldMapping(PersonHolderConstants.name)
    private String outInsureName;
    @Schema(description = "出险人性别", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.gender)
    private String outInsureGender;
    @Schema(description = "出险人出生年月")
    @PersonFieldMapping(PersonHolderConstants.birthday)
    private String outInsureBirthday;

    @Schema(description = "出险人证件类型", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.identityType)
    private String outInsureIdentityType;

    @Schema(description = "出险人证件类型_中文")
    @PersonFieldMapping(PersonHolderConstants.identityTypeCn)
    private String outInsureIdentityTypeCn;


    @Schema(description = "出险人证件号")
    @PersonFieldMapping(PersonHolderConstants.identityNo)
    private String outInsureIdentityNo;


    @Schema(description = "出险人证件有效期")
    @PersonFieldMapping(PersonHolderConstants.identityDatePeriod)
    private String outInsureIdentityDatePeriod;

    @Schema(description = "出险人职业", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.occupation)
    private String outInsureOccupation;

    @Schema(description = "出险人职业_中文")
    @PersonFieldMapping(PersonHolderConstants.occupationCn)
    private String outInsureOccupationCn;

    @Schema(description = "出险人国籍", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.nationality)
    private String outInsureNationality;

    @Schema(description = "出险人国籍_中文")
    @PersonFieldMapping(PersonHolderConstants.nationalityCn)
    private String outInsureNationalityCn;


    @Schema(description = "出险人联系方式")
    @PersonFieldMapping(PersonHolderConstants.phone)
    private String outInsurePhone;


    @Schema(description = "出险人省市区", format = "SelectCtrl")
    @PersonFieldMapping(PersonHolderConstants.contactRegion)
    private String outInsureContactRegion;


    @Schema(description = "出险人详细地址")
    @PersonFieldMapping(PersonHolderConstants.contactAddress)
    private String outInsureContactAddress;


    @Schema(description = "和主被保险人关系", format = "SelectDrop")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsure)
    private String outRelationToMainInsure;


    @Schema(description = "和主被保险人关系_中文")
    @PersonFieldMapping(PersonHolderConstants.relationToMainInsureCn)
    private String outRelationToMainInsureCn;

  /*  @Schema(description = "出险人专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);
*/
  private  Map<String,Object>  extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
