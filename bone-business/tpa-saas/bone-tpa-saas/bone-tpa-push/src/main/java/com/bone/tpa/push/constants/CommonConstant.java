package com.bone.tpa.push.constants;

import java.util.Set;

/**
 * @Author feihaiming
 * @create 2025/10/20 15:01
 */
public interface CommonConstant {
    String YONGCHENG_MAIN_HANDLER_CODE = "新永诚财产保险股份有限公司";
    String RUITAI_MAIN_HANDLER_CODE = "瑞泰人寿保险有限公司";
    String STANDARD_HANDLER_CODE = "标准";

    // 扩展的字段名称
    String CLAIM_CONCLUSION_TREATMENT_TYPE = "treatmentType";

    String  EXT_FIELD_PREFIX = "EX_";
    String  PK_FIELD_PREFIX = "PK_";
    String  CERTIFICATETYPE = "CertificateType";
    String  RELATIONSHIP = "Relationship";
    String  YONGCHENG = "_YongCheng1";

    // 证件类型扩展列名
    //主被保险人证件类型扩展列名
    String MAININSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME="主被保险人映射1";
    String MAININSURE_IDENTITY_TYPE_SECOND_MAPPING_NAME="主被保险人映射2";
    //出险人证件类型扩展列名
    String OUTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME="出险人映射1";
    //领款人证件类型扩展列名
    String COLLECTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME="领款人映射1";
    // 身份关系扩展列名
    String COLLECTINSURE_WITH_MAININSURE_RELATION_MAPPING_NAME="领款人和主被人关系映射1";
    String BENEINSURE_WITH_OUTINSURE_RELATION_MAPPING_NAME="受益人和出险人关系映射1";

    ///////////////扩展字段，永诚的先写死 start
    //发票上的
    String YC_ACCIDENTTYPE = "accidenttype";
    //赔案上的
    String YC_TRANSFERPAYMENTMETHOD = "transferpaymentmethod";
    String YC_COLLECTPAYTYPE = "collectpaytype";
    String YC_COLLECTRELATION = "collectrelation";
    String YC_INSURTHIRDPARTYRELATION = "insurThirdPartyRelation";
    String YC_PAYTHIRDPARTYREMARK = "payThirdPartyRemark";
    String YC_CLAIMACCIDENTREASON = "claimAccidentReason";
    String YC_AGENTIDENTITY = "agentIdentity";
    String YC_ISAGENTPROCEDURES = "isAgentProcedures";
    String YC_ISAGENTVALID = "isAgentValid";

    // 签收案件时的出险类型，适用ychgj
    String YC_CLAIM_ACCIDENTTYPE = "claimaccidenttype";
    //end

    String HOSPITAL_CODE_NOT_EXISTS = "9999999";
    String HOSPITAL_NAME_NOT_EXISTS = "本代码表中不存在的其他医院";

    Set<String> SPECIAL_INSURANCE_NAMES = Set.of(
            "中国人民人寿保险股份有限公司",
            "申能财产保险股份有限公司",
            "中邮人寿保险股份有限公司"
    );

    Set<String> SPECIAL_BRANCH_NAMES = Set.of(
            "中国太平洋财产保险股份有限公司广西分公司",
            "中国太平洋财产保险股份有限公司济南中心支公司",
            "中国太平洋财产保险股份有限公司上海分公司",
            "中国太平洋人寿保险股份有限公司上海分公司",
            "上海誉好数据技术有限公司"
    );

    String RENSHOU = "中国太平洋人寿保险股份有限公司";
    String ZHONGYIN = "中银保险有限公司";
    String SIMPLE_DINGHE = "鼎和";

    String MAGIC_CODE = "a";
    String MAGIC_CODE_YCHGJ = "ycclassify";
    String MAGIC_USER_NAME = "system";
}
