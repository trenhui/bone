package com.bone.tpa.api.enums;

import lombok.Getter;

/**
 * 电票验真结果
 * 2-真票；3-无法验真；4-红冲；5-换开；6-假票；7-未验真；8-查无此票
 */

@Getter
public enum EInvoiceValidResult {
    真票("2", "真票"),
    无法验真("3", "无法验真"),
    红冲("4", "红冲"),
    换开("5", "换开"),
    假票("6", "假票"),
    未验真("7", "未验真"),
    查无此票("8", "查无此票")
    ;
    private String code;
    private String desc;

    EInvoiceValidResult(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static EInvoiceValidResult getEnumByDesc(String desc) {
        for (EInvoiceValidResult e : EInvoiceValidResult.values()) {
            if (e.getDesc().equals(desc)) {
                return e;
            }
        }
        return null;
    }

    public static EInvoiceValidResult getEnumByCode(String code) {
        for (EInvoiceValidResult e : EInvoiceValidResult.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
