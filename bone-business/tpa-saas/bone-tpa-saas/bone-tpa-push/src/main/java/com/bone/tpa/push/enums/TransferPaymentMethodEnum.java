package com.bone.tpa.push.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * @Author feihaiming
 *
 * @create 2025/5/8 21:31
 */
@Getter
@AllArgsConstructor
public enum TransferPaymentMethodEnum {
    TO_PRIVATE("对私", 1),
    TO_PUBLIC("对公", 0);


    private String name;
    private Integer value;

    public static Integer getValue(String name) {
        for (TransferPaymentMethodEnum c : TransferPaymentMethodEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }
}
