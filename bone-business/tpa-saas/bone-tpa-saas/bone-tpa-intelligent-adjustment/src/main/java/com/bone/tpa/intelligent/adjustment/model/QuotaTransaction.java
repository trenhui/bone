package com.bone.tpa.intelligent.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.sdk.adjustment.enums.QuotaStatusEnum;
import lombok.*;

import java.math.BigDecimal;

@Table("ia_quota_transaction")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class QuotaTransaction extends ExtensibleObject<QuotaTransaction,Long> {
    private String xid;
    private String policyNo;
    private BigDecimal amount;

    private QuotaStatusEnum status;
}
