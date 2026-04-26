package com.bone.tpa.api.vo;

import lombok.Data;

/**
 * 赔案的配置信息
 */
@Data
public class ClaimConfig {
    /**
     * 赔案编号
     */
    private Long claimNumber;
    /**
     * 02半流程
     * 01全流程
     * 04录审分离
     */
    private  String clmProcess;

    //是否需要影像分类 1是，0否
    private Integer imageClassify;

    // 是否需要初审 1是，0否
    private Integer caseClaimAudit;

    //初审赔案类型 0-线上，1-线下 2-线上&线下
    private Integer caseClaimAuditType;

    private Integer bizType;// 作业类型，1：新tpa，2：SAAS版仅录入',

    private Integer needVerify;//  电票需验真,1：需要，0：不需要',
    private Integer bizInputType ;// '业务录入类型，--1:发票层，2：发票层和项目层',
    private Integer canBeAutomation;// 是否自动化作业，1：自动化作业，2：非自动化作业'


}
