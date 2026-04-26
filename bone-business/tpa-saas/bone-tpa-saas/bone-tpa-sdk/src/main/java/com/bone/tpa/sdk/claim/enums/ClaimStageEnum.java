package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ClaimStageEnum {
    INIT("INIT", "初始化", 10),

    SIGNING("SIGNING", "签收", 20),

    PRE_EXAM("PRE_EXAM", "初审", 30),

    SUBMITTING("SUBMITTING", "录入", 40),

    INSPECTION("INSPECTION", "质检", 50),

    AUDITING("AUDITING", "审核", 60),

    REVIEWING("REVIEWING", "复核", 70),

    FINISH("FINISH", "完成",80),

    ;

    private final String code;
    private final String value;
    private final Integer order;

    ClaimStageEnum(String code, String value, Integer order) {
        this.code = code;
        this.value = value;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public Integer getOrder() {
        return order;
    }

    public static ClaimStageEnum getByCode(String code) {
        for (ClaimStageEnum e : ClaimStageEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public static ClaimStageEnum getByValue(String value) {
        for (ClaimStageEnum e : ClaimStageEnum.values()) {
            if (e.getValue().equals(value)) {
                return e;
            }
        }
        return null;
    }

    public static List<String> getExceptStage(ClaimStageEnum ...exceptStages) {
        if (exceptStages == null || exceptStages.length == 0) {
            return Arrays.stream(ClaimStageEnum.values()).map(ClaimStageEnum::getCode).collect(Collectors.toList());
        }
        List<ClaimStageEnum> stages = List.of(exceptStages);
        List<String> strings = stages.stream().map(ClaimStageEnum::getCode).collect(Collectors.toList());
        return Arrays.stream(ClaimStageEnum.values()).map(ClaimStageEnum::getCode).filter(code -> !strings.contains(code)).collect(Collectors.toList());
    }
}
