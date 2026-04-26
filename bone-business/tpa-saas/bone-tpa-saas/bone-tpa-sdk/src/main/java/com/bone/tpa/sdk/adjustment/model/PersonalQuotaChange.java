package com.bone.tpa.sdk.adjustment.model;


import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Table("ia_personal_quota_change")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class PersonalQuotaChange extends AbstractEntity<Long> {
    /**
     * 操作批次,文件名+操作人姓名
     */
    private Long batchId;

    /**
     * 保全类型,1:初始化个人金额,2:金额变化,4:减人
     */
    private Byte operationType;

    /**
     * 普康保单号
     */
    private String policyNo;

    /**
     * 被保险人姓名
     */
    private String insuredName;

    /**
     * 被保险人证件类型
     */
    private String insuredCertificateType;

    /**
     * 被保险人证件号
     */
    private String insuredCertificateNumber;

    /**
     * 变动前剩余个人额度
     */
    private BigDecimal quotaBefore;

    /**
     * 变动金额
     */
    private BigDecimal quotaChange;

    /**
     * 变动后剩余个人额度
     */
    private BigDecimal quotaAfter;

    /**
     * 操作人
     */
    private String createPeople;
}
