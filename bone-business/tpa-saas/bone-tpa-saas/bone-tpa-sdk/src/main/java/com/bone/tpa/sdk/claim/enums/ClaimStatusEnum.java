package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ClaimStatusEnum {

    DRAFT("DRAFT", "初始化",ClaimStageEnum.INIT,""),
    SIGNED("7","已签收", ClaimStageEnum.SIGNING,""),
    WAITING_PRE_ADUIT("10", "等待初审-未分配",ClaimStageEnum.PRE_EXAM,""),
    PRE_ADUITING("11", "初审中",ClaimStageEnum.PRE_EXAM,""),
    ROBOT_PRE_ADUIT("13", "自动化初审中",ClaimStageEnum.PRE_EXAM,""),
    COMPLETE_PRE_ADUIT("12", "已初审",ClaimStageEnum.PRE_EXAM,""),

    ADDING_INPUT("21","等待录入",ClaimStageEnum.SUBMITTING,""),
    ORC_INPUTING("17", "OCR录入中",ClaimStageEnum.SUBMITTING,""),
    PUKANG_INPUTING("22", "普康录入中",ClaimStageEnum.SUBMITTING,"1"),
    WAIBAO_INPUTING("22", "外包录入中",ClaimStageEnum.SUBMITTING,"2"),
    INPUT_COMPLETE("23", "已录入",ClaimStageEnum.SUBMITTING,""),


    WAITING_INSPECTION("30", "等待质检",ClaimStageEnum.INSPECTION,""),
    INSPECTIONING("31", "质检中",ClaimStageEnum.INSPECTION,""),
    COMPLETE_INSPECTION("32", "质检完成",ClaimStageEnum.INSPECTION,""),

    WaitAudit("40", "等待审核",ClaimStageEnum.AUDITING,""),
    Auditing("41", "审核中",ClaimStageEnum.AUDITING,""),

    AuditingReviewIng("44", "复核中",ClaimStageEnum.REVIEWING, ""),
    COMPLETE_AUDIT("42", "已审核(终态）", ClaimStageEnum.AUDITING, ""),

    Cancel("45", "已撤销", ClaimStageEnum.FINISH, "P3"),
    Finish("50", "已结案", ClaimStageEnum.FINISH, ""),

    AuditingToReviewWait("75","审核完成到复核中间态",ClaimStageEnum.REVIEWING,""),
    ;

    private final String code;
    private final String value;
    private final ClaimStageEnum stage;

    private String subStatus;

    ClaimStatusEnum(String code, String value,ClaimStageEnum stage,String subStatus) {
        this.code = code;
        this.value = value;
        this.stage = stage;
        this.subStatus = subStatus;
    }

    public static ClaimStatusEnum getByCode(String code,String subStatus) {
        if( subStatus == null){
            subStatus = "";
        }
        for (ClaimStatusEnum e : ClaimStatusEnum.values()) {
            if (e.getCode().equals(code) && e.getSubStatus().equals(subStatus)) {
                return e;
            }
        }
        return null;
    }

    public static List<String> getExceptStatus(ClaimStatusEnum ...exceptStatus) {
        if (exceptStatus == null || exceptStatus.length == 0) {
            return Arrays.stream(ClaimStatusEnum.values()).map(i -> i.getCode()).collect(Collectors.toList());
        }
        List<ClaimStatusEnum> stages = List.of(exceptStatus);
        List<String> strings = stages.stream().map(i -> i.getCode()).collect(Collectors.toList());
        return Arrays.stream(ClaimStatusEnum.values()).map(i -> i.getCode()).filter(code -> !strings.contains(code)).collect(Collectors.toList());
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public ClaimStageEnum getStage() {
        return stage;
    }

    public String getSubStatus() {
        return subStatus;
    }
}
