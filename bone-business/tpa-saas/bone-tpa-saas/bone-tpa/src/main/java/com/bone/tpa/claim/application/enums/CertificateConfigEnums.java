package com.bone.tpa.claim.application.enums;


import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Map;

/**
 * 单证配置下拉框选项枚举
 */
public interface CertificateConfigEnums {

    /**
     * 适用赔案-下拉框
     */
    @Getter
    enum ClaimAuditTypeEnum {
        ALL(2, "线上&线下"),
        ONLINE(0, "线上"),
        OFFLINE(1, "线下"),
        ;

        private final Integer code;
        private final String desc;

        ClaimAuditTypeEnum(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @JsonValue
        public Object toJson() {
            return Map.of(
                    "code", code,
                    "desc", desc
            );
        }
    }

    /**
     * 选择模版-下拉框
     */
    @Getter
    enum CertificateTemplateEnum {
        ITEM1("0", "永诚_理赔结案通知书"),
        ITEM2("1", "永诚_意健险理赔申请书");

        private final String code;
        private final String desc;

        CertificateTemplateEnum(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @JsonValue
        public Object toJson() {
            return Map.of(
                    "code", code,
                    "desc", desc
            );
        }
    }

    /**
     * 生成节点-下拉框
     */
    @Getter
    enum ClaimProcessNodeEnum {
        ITEM1("42", "审核环节完成"),
        ;

        private final String code;
        private final String desc;

        ClaimProcessNodeEnum(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @JsonValue
        public Object toJson() {
            return Map.of(
                    "code", code,
                    "desc", desc
            );
        }
    }

    /**
     * 生成条件（理赔结论）-下拉框
     */
    @Getter
    enum AdjustmentConclusionEnum {
        ALL("全部", "全部"),
        ITEM1("赔付", "赔付"),
        ITEM2("拒付", "拒付"),
        ITEM3("零赔付", "零赔付"),
        ITEM4("注销", "注销"),
        ;

        private final String code;
        private final String desc;

        AdjustmentConclusionEnum(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @JsonValue
        public Object toJson() {
            return Map.of(
                    "code", code,
                    "desc", desc
            );
        }
    }
}
