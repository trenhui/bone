package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.entity.AbstractEntity;
import lombok.*;

@Table("ia_personal_quota_change_batch")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PersonalQuotaChangeBatch extends AbstractEntity<Long> {

    /**
     * 普康保单号
     */
    private String policyNo;

    /**
     * 保全类型,1:初始化个人金额,2:金额变化,4:减人
     */
    private Byte operationType;

    /**
     * 操作批次名,文件名+操作人姓名
     */
    private String batchName;

    /**
     * 操作人
     */
    private String createPeople;
}
