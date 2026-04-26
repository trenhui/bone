package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class TpaPersonClaim implements Serializable {
    @TableId(value = "pclaim_id")
    private Integer pclaimId;

    
    private String pclaimCode;

    
    private String pclaimCardcode;

    
    private String pclaimPersoncode;

    
    private Date pclaimUpdatetime;

    
    private String pclaimTerminal;

    /**
     * -1.申请有误，-2.暂不赔付，-3暂不赔付，0.待受理，1.已受理，2.审核中，3.已审核，4.已完成
     */
    @Schema(description = "-1.申请有误，-2.暂不赔付，-3暂不赔付，0.待受理，1.已受理，2.审核中，3.已审核，4.已完成")
    private Integer pclaimStatus;

    
    private String pclaimClaimcode;

    
    private String pclaimPersonname;

    
    private Date pclaimUploadtime;

    
    private Date pclaimCreatetime;

    
    private String pclaimDeleted;

    
    private Date pclaimUpdateuser;

    
    private String pclaimCreateuser;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String pclaimMemo;

    /**
     * 出险日期
     */
    @Schema(description = "出险日期")
    private Date pclaimOuttime;

    /**
     * 保单号
     */
    @Schema(description = "保单号")
    private String pclaimSlipcode;

    /**
     * 批次号
     */
    @Schema(description = "批次号")
    private String pclaimBatchcode;

    /**
     * 证件号
     */
    @Schema(description = "证件号")
    private String pclaimPersoncertno;

    /**
     * 证件类型
     */
    @Schema(description = "证件类型")
    private String pclaimPersoncerttype;

    /**
     * 实际可理赔金额
     */
    @Schema(description = "实际可理赔金额")
    private BigDecimal pclaimActualamt;

    /**
     * 有效发票总金额
     */
    @Schema(description = "有效发票总金额")
    private BigDecimal pclaimTotalamt;

    /**
     * 理赔锁定金额
     */
    @Schema(description = "理赔锁定金额")
    private BigDecimal pclaimLockedamt;

    /**
     * 理赔实际扣款金额
     */
    @Schema(description = "理赔实际扣款金额")
    private BigDecimal pclaimDeductamt;

    /**
     * 理赔实际扣减免赔额
     */
    @Schema(description = "理赔实际扣减免赔额")
    private BigDecimal pclaimDeductabtamt;

    /**
     * 保险公司回复确认金额
     */
    @Schema(description = "保险公司回复确认金额")
    private BigDecimal pclaimApprovedamt;

    /**
     * 参考编号
     */
    @Schema(description = "参考编号")
    private String pclaimRefcode;

    
    private BigDecimal pclaimEffectiveamt;

    /**
     * 统筹
     */
    @Schema(description = "统筹")
    private BigDecimal pclaimRatios;

    /**
     * 赔付比率
     */
    @Schema(description = "赔付比率")
    private BigDecimal pclaimRuleClaimratio;

    /**
     * 发票数
     */
    @Schema(description = "发票数")
    private Integer pclaimInvoicenum;

    /**
     * 个人承担金额
     */
    @Schema(description = "个人承担金额")
    private BigDecimal pclaimPersonageamt;

    /**
     * 未赔付金额
     */
    @Schema(description = "未赔付金额")
    private BigDecimal pclaimNotclaimamt;

    /**
     * 对应TPA赔案号
     */
    @Schema(description = "对应TPA赔案号")
    private String pclaimTpaClaimCode;

    /**
     * TPA中的分支机构编码
     */
    @Schema(description = "TPA中的分支机构编码")
    private String pclaimTpaBranchCode;

    /**
     * 理赔类别
     */
    @Schema(description = "理赔类别")
    private Integer pclaimType;

    /**
     * 就诊人id
     */
    @Schema(description = "就诊人id")
    private Integer pclaimRecognizeeid;

    /**
     * 领款人id
     */
    @Schema(description = "领款人id")
    private Integer pclaimPayeeid;

    /**
     * 结案时间(TPA质检时间)
     */
    @Schema(description = "结案时间(TPA质检时间)")
    private Date pclaimEndtime;

    /**
     * 理赔结论，【给付】或【拒赔】，文字说明
     */
    @Schema(description = "理赔结论，【给付】或【拒赔】，文字说明")
    private String pclaimConclusion;

    /**
     * 理赔账户类型 0 仅个人账户 1 仅公共账户 2 个人账户 3 理赔账户
     */
    @Schema(description = "理赔账户类型 0 仅个人账户 1 仅公共账户 2 个人账户 3 理赔账户")
    private Integer pclaimAccounttype;

    /**
     * 推送状态(未推送;已推送)
     */
    @Schema(description = "推送状态(未推送;已推送)")
    private String pushStatus;

    private static final long serialVersionUID = 1L;
}
