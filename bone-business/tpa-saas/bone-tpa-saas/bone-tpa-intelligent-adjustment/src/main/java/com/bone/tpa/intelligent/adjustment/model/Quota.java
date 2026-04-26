package com.bone.tpa.intelligent.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.sdk.adjustment.exception.InsufficientQuotaException;
import lombok.*;

import java.math.BigDecimal;

@Table("ia_quota")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Quota extends ExtensibleObject<Quota,Long> {
    private String policyNo; // 保单号
    private Long accountId; // 账户ID
    private String identityNo;// 主被保险人证件号
    private BigDecimal totalAmount; // 总额度
    private BigDecimal available; // 可用额度
    private BigDecimal frozen; // 冻结额度
    private Integer version;     // 乐观锁版本号

    // 冻结额度
    public void freeze(BigDecimal amount) {
        if (available.compareTo(amount) < 0) {
            throw new InsufficientQuotaException("额度不足，无法冻结");
        }
        available = available.subtract(amount);
        frozen = frozen.add(amount);
    }

    // 解冻额度
    public void unfreeze(BigDecimal amount) {
        if (frozen.compareTo(amount) < 0) {
            throw new InsufficientQuotaException("冻结额度不足，无法解冻");
        }
        available = available.add(amount);
        frozen = frozen.subtract(amount);
    }
}
