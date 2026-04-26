package com.bone.tpa.sdk.adjustment.model;


import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Table("ia_personal_quota")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class PersonalQuota extends AbstractEntity<Long> {

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
     * 初始个人额度
     */
    private BigDecimal quotaInitial;

    /**
     * 已用理赔额度
     */
    private BigDecimal quotaClaim;

    /**
     * 剩余个人额度
     */
    private BigDecimal quotaRemaining;

    /**
     * 当前冻结额度
     */
    private BigDecimal quotaFrozen;

    /**
     * 当前可用额度
     */
    private BigDecimal quotaAvailable;

    /**
     * 被保状态,0:正常,1:已减人
     */
    private Byte insuredState;

    /**
     * 创建人员
     */
    private String createPeople;

    /**
     * 最近保全人员
     */
    private String updatePeople;
}
