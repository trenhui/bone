package com.bone.tpa.api.response;

import com.alibaba.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ClaimPushFailOperateResponse {

    /**
     * 错误归档
     */
    @ExcelProperty("错误归类")
    private String errorType;

    /**
     * 赔案等级
     */
    @ExcelProperty("赔案等级")
    private String vipSign;

    /**
     * 保单号
     */
    @ExcelProperty("保单号")
    private String policyNo;

    /**
     * 批次号
     */
    @ExcelProperty("批次号")
    private String batchNo;

    /**
     * 赔案号
     */
    @ExcelProperty("赔案号")
    private String claimNo;

    /**
     * 失败时间，去赔案表上的更新时间
     */
    @ExcelProperty("失败时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date failTime;

    /**
     * 退回操作人员
     */
    @ExcelProperty("退回操作人员")
    private String createUser;

    /**
     * 退回处理人员
     */
    @ExcelProperty("退回处理人员")
    private String backAuditUser;

    /**
     * 退回操作时间
     */
    @ExcelProperty("退回操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 退回环节
     */
    @ExcelProperty("退回环节")
    private String backNode;

    /**
     * 失败原因
     */
    @ExcelProperty("失败原因")
    private String pushBackReason;

    /**
     * 出险人姓名
     */
    @ExcelProperty("出险人姓名")
    private String outInsureName;

    /**
     * 出险人证件类型
     */
    @ExcelProperty("出险人证件类型")
    private String outInsureIdentityTypeCn;

    /**
     * 出险人证件号码
     */
    @ExcelProperty("出险人证件号码")
    private String outInsureIdentityNo;

    /**
     * 主被姓名
     */
    @ExcelProperty("主被姓名")
    private String mainInsureName;

    /**
     * 主被证件类型
     */
    @ExcelProperty("主被证件类型")
    private String mainInsureIdentityTypeCn;

    /**
     * 主被证件号码
     */
    @ExcelProperty("主被证件号码")
    private String mainInsureIdentityNo;

    /**
     * 投保公司
     */
    @ExcelProperty("投保公司")
    private String insureName;

    /**
     * 保险公司
     */
    @ExcelProperty("保险公司")
    private String insuranceName;

    /**
     * 保险分公司
     */
    @ExcelProperty("保险分公司")
    private String branchName;

    /**
     * 审核人员
     */
    @ExcelProperty("审核人")
    private String auditingOperatorName;

}
