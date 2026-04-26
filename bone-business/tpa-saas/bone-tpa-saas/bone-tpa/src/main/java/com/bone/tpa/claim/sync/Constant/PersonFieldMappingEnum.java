/*
package com.bone.tpa.claim.sync.Constant;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.claim.application.transfer.PersonFieldMapping;
import lombok.Getter;

import java.lang.reflect.Field;

@Getter
public enum PersonFieldMappingEnum {
    mainInsureIdentityType("mainInsureIdentityType", PersonFieldConstants.identityType, FieldModelDefine.主被保险人, "主被保人证件类型"),
    outInsureIdentityType("outInsureIdentityType", PersonFieldConstants.identityType, FieldModelDefine.出险人, "出险人证件类型"),
    benefitIdentityType("benefitIdentityType", PersonFieldConstants.identityType, FieldModelDefine.受益人, "收益人证件类型"),
    collectIdentityType("collectIdentityType", PersonFieldConstants.identityType, FieldModelDefine.领款人信息, "领款人证件类型"),
    businessIdentityType("businessIdentityType", PersonFieldConstants.identityType, FieldModelDefine.领款企业信息, "领款单位证件类型"),
    legalIdentityType("legalIdentityType", PersonFieldConstants.identityType, FieldModelDefine.领款企业信息, "领款单位法人证件类型"),

    mainInsureIdentityTypeCn("mainInsureIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.主被保险人, "主被保人证件类型中文"),
    outInsureIdentityTypeCn("outInsureIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.出险人, "出险人证件类型中文"),
    benefitIdentityTypeCn("benefitIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.受益人, "收益人证件类型中文"),
    collectIdentityTypeCn("collectIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.领款人信息, "领款人证件类型中文"),
    businessIdentityTypeCn("businessIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.领款企业信息, "领款单位证件类型中文"),
    legalIdentityTypeCn("legalIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.领款企业信息, "领款单位法人证件类型中文"),


    //职业
    mainInsureOccupation("mainInsureOccupation", PersonFieldConstants.occupation, FieldModelDefine.主被保险人, "主被保人职业"),
    outInsureOccupation("outInsureOccupation", PersonFieldConstants.occupation, FieldModelDefine.出险人, "出险人职业"),
    benefitOccupation("benefitOccupation", PersonFieldConstants.occupation, FieldModelDefine.受益人, "受益人职业"),
    collectOccupation("collectOccupation", PersonFieldConstants.occupation, FieldModelDefine.领款人信息, "领款人职业"),


    mainInsureOccupationCn("mainInsureOccupationCn", PersonFieldConstants.occupationCn, FieldModelDefine.主被保险人, "主被保人职业中文"),
    outInsureOccupationCn("outInsureOccupationCn", PersonFieldConstants.occupationCn, FieldModelDefine.出险人, "出险人职业中文"),
    benefitOccupationCn("benefitOccupationCn", PersonFieldConstants.occupationCn, FieldModelDefine.受益人, "受益人职业中文"),
    collectOccupationCn("collectOccupationCn", PersonFieldConstants.occupationCn, FieldModelDefine.领款人信息, "领款人职业中文"),


    mainInsureNationality("mainInsureNationality", PersonFieldConstants.nationality, FieldModelDefine.主被保险人, "主被保人国籍"),
    outInsureNationality("outInsureNationality", PersonFieldConstants.nationality, FieldModelDefine.出险人, "出险人国籍"),
    benefitNationality("benefitNationality", PersonFieldConstants.nationality, FieldModelDefine.受益人, "受益人国籍"),
    collectNationality("collectNationality", PersonFieldConstants.nationality, FieldModelDefine.领款人信息, "领款人国籍"),

    mainInsureNationalityCn("mainInsureNationalityCn", PersonFieldConstants.nationalityCn, FieldModelDefine.主被保险人, "主被保人国籍中文"),
    outInsureNationalityCn("outInsureNationalityCn", PersonFieldConstants.nationalityCn, FieldModelDefine.出险人, "出险人国籍中文"),
    benefitNationalityCn("benefitNationalityCn", PersonFieldConstants.nationalityCn, FieldModelDefine.受益人, "受益人国籍中文"),
    collectNationalityCn("collectNationalityCn", PersonFieldConstants.nationalityCn, FieldModelDefine.领款人信息, "领款人国籍中文"),



    mainInsureContactProvice("mainInsureContactProvice", PersonFieldConstants.contactProvince, FieldModelDefine.主被保险人, "主被保人省份"),
    mainInsureContactCity("mainInsureContactCity", PersonFieldConstants.contactCity, FieldModelDefine.主被保险人, "主被保人城市"),
    mainInsureContactArea("mainInsureContactArea", PersonFieldConstants.contactArea, FieldModelDefine.主被保险人, "主被保人区域"),

    mainInsureContactProviceName("mainInsureContactProviceName", PersonFieldConstants.contactProvinceName, FieldModelDefine.主被保险人, "主被保人省份中文"),
    mainInsureContactCityName("mainInsureContactCityName", PersonFieldConstants.contactCityName, FieldModelDefine.主被保险人, "主被保人城市中文"),
    mainInsureContactAreaName("mainInsureContactAreaName", PersonFieldConstants.contactAreaName, FieldModelDefine.主被保险人, "主被保人区域中文"),



    outInsureContactProvice("outInsureContactProvice", PersonFieldConstants.contactProvince, FieldModelDefine.出险人, "出险人省份"),
    outInsureContactCity("outInsureContactCity", PersonFieldConstants.contactCity, FieldModelDefine.出险人, "出险人城市"),
    outInsureContactArea("outInsureContactArea", PersonFieldConstants.contactArea, FieldModelDefine.出险人, "出险人区域"),
    outInsureContactProviceName("outInsureContactProviceName", PersonFieldConstants.contactProvinceName, FieldModelDefine.出险人, "出险人省份名称"),
    outInsureContactCityName("outInsureContactCityName", PersonFieldConstants.contactCityName, FieldModelDefine.出险人, "出险人城市名称"),
    outInsureContactAreaName("outInsureContactAreaName", PersonFieldConstants.contactAreaName, FieldModelDefine.出险人, "出险人区域名称"),




    benefitContactProvice("benefitContactProvice", PersonFieldConstants.contactProvince, FieldModelDefine.受益人, "收益人省份"),
    benefitContactCity("benefitContactCity", PersonFieldConstants.contactCity, FieldModelDefine.受益人, "收益人城市"),
    benefitContactArea("benefitContactArea", PersonFieldConstants.contactArea, FieldModelDefine.受益人, "收益人区域"),
    benefitContactProviceName("benefitContactProviceName", PersonFieldConstants.contactProvinceName, FieldModelDefine.受益人, "收益人省份名称"),
    benefitContactCityName("benefitContactCityName", PersonFieldConstants.contactCityName, FieldModelDefine.受益人, "收益人城市名称"),
    benefitContactAreaName("benefitContactAreaName", PersonFieldConstants.contactAreaName, FieldModelDefine.受益人, "收益人区域名称"),



    collectContactProvice("collectContactProvice", PersonFieldConstants.contactProvince, FieldModelDefine.领款人信息, "领款人省份"),
    collectContactCity("collectContactCity", PersonFieldConstants.contactCity, FieldModelDefine.领款人信息, "领款人城市"),
    collectContactArea("collectContactArea", PersonFieldConstants.contactArea, FieldModelDefine.领款人信息, "领款人区域"),
    collectContactProviceName("collectContactProviceName", PersonFieldConstants.contactProvinceName, FieldModelDefine.领款人信息, "领款人省份名称"),
    collectContactCityName("collectContactCityName", PersonFieldConstants.contactCityName, FieldModelDefine.领款人信息, "领款人城市名称"),
    collectContactAreaName("collectContactAreaName", PersonFieldConstants.contactAreaName, FieldModelDefine.领款人信息, "领款人区域名称"),



    outRelationToMainInsure("outRelationToMainInsure", PersonFieldConstants.relationToMainInsure, FieldModelDefine.出险人, "出险人和主被保人关系"),
    outRelationToMainInsureCn("outRelationToMainInsureCn", PersonFieldConstants.relationToMainInsureCn, FieldModelDefine.出险人, "出险人和主被保人关系中文"),
    benefitRelationToOutInsure("benefitRelationToOutInsure", PersonFieldConstants.relationToOutInsure, FieldModelDefine.受益人, "受益人和出险人关系"),
    benefitRelationToOutInsureCn("benefitRelationToOutInsureCn", PersonFieldConstants.relationToOutInsureCn, FieldModelDefine.受益人, "受益人和出险人关系中文"),

    collectRelationToOutInsure("collectRelationToOutInsure", PersonFieldConstants.relationToOutInsure, FieldModelDefine.领款人信息, "领款人和出险人关系"),
    collectRelationToOutInsureCn("collectRelationToOutInsureCn", PersonFieldConstants.relationToOutInsureCn, FieldModelDefine.领款人信息, "领款人和出险人关系中文"),
    collectRelationToMainInsure("collectRelationToMainInsure", PersonFieldConstants.relationToMainInsure, FieldModelDefine.领款人信息, "领款人和主被保人关系"),
    collectRelationToMainInsureCn("collectRelationToMainInsureCn", PersonFieldConstants.relationToMainInsureCn, FieldModelDefine.领款人信息, "领款人和主被保人关系中文"),

    collectRelationToBenefit("collectRelationToBenefit", PersonFieldConstants.relationToBenefit, FieldModelDefine.领款人信息, "领款人和受益人关系"),
    collectRelationToBenefitCn("collectRelationToBenefitCn", PersonFieldConstants.relationToBenefitCn, FieldModelDefine.领款人信息, "领款人和受益人关系中文"),


    businessRelationToOutInsure("businessRelationToOutInsure", PersonFieldConstants.relationToOutInsure, FieldModelDefine.领款企业信息, "领款单位与出险人的关系"),
    businessRelationToMainInsure("businessRelationToMainInsure", PersonFieldConstants.relationToMainInsure, FieldModelDefine.领款企业信息, "领款单位与主被保人的关系"),
    businessRelationToBenefit("businessRelationToBenefit", PersonFieldConstants.relationToBenefit, FieldModelDefine.领款企业信息, "领款单位与受益人的关系"),

    //申请人模块
    applyRelationToOutInsure("applyRelationToOutInsure", PersonFieldConstants.relationToOutInsure, FieldModelDefine.申请人, "申请人和出险人关系"),
    applyRelationToOutInsureCn("applyRelationToOutInsureCn", PersonFieldConstants.relationToOutInsureCn, FieldModelDefine.申请人, "申请人和出险人关系中文"),
    applyIdentityTypeCn("applyIdentityTypeCn", PersonFieldConstants.identityTypeCn, FieldModelDefine.申请人, "申请人证件类型中文"),
    applyIdentityType("applyIdentityType", PersonFieldConstants.identityType, FieldModelDefine.申请人, "申请人证件类型"),
    applyIdentityNo("applyIdentityNo", PersonFieldConstants.identityNo, FieldModelDefine.申请人, "申请人证件号码"),
    ;



    private String pageFieldCode ;

    private String fieldType;

    private FieldModelDefine modelDefine;

    private String desc;

    PersonFieldMappingEnum(String pageFieldCode, String fieldType, FieldModelDefine modelDefine, String desc) {

        this.pageFieldCode = pageFieldCode;
        this.fieldType = fieldType;
        this.modelDefine = modelDefine;
        this.desc = desc;

    }

    */
/*

    public static String  getByPageFieldCode( String fieldType, FieldModelDefine modelDefine) {
        for (PersonFieldMappingEnum personFieldMappingEnum : PersonFieldMappingEnum.values()) {
            if (personFieldMappingEnum.getFieldType().equals(fieldType)
                    && personFieldMappingEnum.getModelDefine().equals(modelDefine)) {
                return personFieldMappingEnum.getPageFieldCode();
            }
        }
       throw null;
    }*//*


}
*/
