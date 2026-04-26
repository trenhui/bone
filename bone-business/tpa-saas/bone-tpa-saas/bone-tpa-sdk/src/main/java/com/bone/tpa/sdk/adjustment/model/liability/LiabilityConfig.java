package com.bone.tpa.sdk.adjustment.model.liability;

import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Date;
import java.util.List;


/**
 * 责任配置类，包含责任ID、名称、类型、赔付比例等信息
 * 用于记录每个责任的赔付规则与限制。
 */
@Builder(toBuilder = true)
@Data
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class LiabilityConfig  {
    private Long id ;
    private Long tenantId ;
    private Date createTime;
    private Long createBy;
    private Date updateTime;
    private Long updateBy;
    /**
     * uuid
     */
    private String uuid;

    /**
     * 内部保单号
     */
    @NotNull(message = "内部保单号不能为空")
    private String policyNo;

    /**
     * 计划ID
     */
    @NotNull(message = "计划ID不能为空")
    private Long planId;

    /**
     * 险种ID
     */
    @NotNull(message = "险种ID不能为空")
    private Long coverageId;

    /**
     * 责任编码
     */
    private String liabilityCode;

    /**
     * 责任名称
     */
    @NotNull(message = "责任名称不能为空")
    @Size(max = 80)
    private String liabilityName;

    /**
     * 责任类型
     */
    @NotNull(message = "责任类型不能为空")
    private LiabilityTypeEnum liabilityType;

    /**
     * 版本
     */
    @NotNull(message = "版本不能为空")
    private String version;

    /**
     * 后付责任UUId
     */
    private String nextLiabilityUuid;

    /**
     * 后付类型
     */
    private String nextLiabilityType;

    /**
     * 能否被设置为发票关联责任
     */
    private Boolean invoiceRelateAble;

    /**
     * 给付依据（定额给付使用
     */
    private String paymentBasis;

    /**
     * 津贴细则（津贴给付使用
     */
    private AllowanceDetail allowanceDetail;

    /**
     * 适用对象
     */
    @NotNull(message = "适用对象不能为空")
    private RestrictObject restrictObject;

    /**
     * 适用限定(限定范围）
     */
    @NotNull(message = "适用限定不能为空")
    private List<RestrictScope> restrictScope;

    /**
     * 适用出险
     */
    @NotNull(message = "适用出险不能为空")
    private RestrictOutInsure restrictOutInsure;

    /**
     * 等待期
     */
    private Integer waitingPeriod;

    /**
     * 赔付比例
     */
    @NotNull(message = "赔付比例不能为空")
    private PayPercent payPercent;

    /**
     * 责任免赔
     */
    private LiabilityDeduct liabilityDeduct;

    /**
     * 次日限额
     */
    private TimesLimit timesLimit;

    /**
     * 控额方
     */
    @NotNull(message = "控额方不能为空")
    private QuotaController quotaController;

    /**
     * 责任账户类型（个账，公账）
     */
    private String accountType;

    /**
     * 保额类型
     */
    @NotNull(message = "保额类型不能为空")
    private String insuranceQuotaType;

    /**
     * 责任额度
     */
    @NotNull(message = "责任额度不能为空")
    private LiabilityLimit liabilityLimit;

    /**
     * 理算规则
     */
    @NotNull(message = "理算信息不能为空")
    private List<AdjustmentDetail> adjustmentDetail;

    /**
     * 理算公式
     */
    @NotNull(message = "理算公式不能为空")
    private String formula;

    /**
     * 额外记录
     */
    private String remark;

}
