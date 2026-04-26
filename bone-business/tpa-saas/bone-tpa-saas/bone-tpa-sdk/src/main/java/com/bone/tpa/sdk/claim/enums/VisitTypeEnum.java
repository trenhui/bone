package com.bone.tpa.sdk.claim.enums;


import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 就诊类型
 */
public enum VisitTypeEnum {

    /**
     * 门急诊
     */
    OUTPATIENT_EMERGENCY("OUTPATIENT_EMERGENCY", "门/急诊", "门诊和急诊服务，用于处理非住院患者的医疗需求"),

    /**
     * 住院
     */
    INPATIENT("INPATIENT", "住院", "患者需要在医院过夜或长时间接受治疗的服务"),

    /**
     * 药房
     */
    PHARMACY("PHARMACY", "药房", ""),

    /**
     * 药房
     */
    SPECIAL_CLINIC("SPECIAL_CLINIC", "门诊-慢特病", ""),

    /**
     * 意外
     */
    ACCIDENT("ACCIDENT", "意外", "由于突发的、非故意的事件导致的伤害或疾病相关的就诊"),

    /**
     * 其他
     */
    OTHER("OTHER", "其他", ""),

    ;

    private final String code;
    private final String value;
    private final String description;

    VisitTypeEnum(String code, String value, String description) {
        this.code = code;
        this.value = value;
        this.description = description;
    }

    @Override
    public String toString() {
        return getCode();
    }

    /**
     * 根据 code 获取对应的 VisitType 枚举值
     *
     * @param code 枚举的 code 值
     * @return 对应的 VisitType 枚举值
     * @throws IllegalArgumentException 如果找不到对应的枚举值
     */
    public static VisitTypeEnum fromCode(String code) {
        for (VisitTypeEnum visitType : VisitTypeEnum.values()) {
            if (visitType.getCode().equals(code)) {
                return visitType;
            }
        }
        throw new IllegalArgumentException("No VisitType found for code: " + code);
    }

    /**
     * 根据code获取对应的value
     */
    public static String getValueByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }

        for (VisitTypeEnum item : VisitTypeEnum.values()) {
            if (code.equals(item.getCode())) {
                return item.getValue();
            }
        }
        return null;
    }


    public static VisitTypeEnum getByValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        for (VisitTypeEnum item : VisitTypeEnum.values()) {
            if (value.equals(item.getValue())) {
                return item;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
