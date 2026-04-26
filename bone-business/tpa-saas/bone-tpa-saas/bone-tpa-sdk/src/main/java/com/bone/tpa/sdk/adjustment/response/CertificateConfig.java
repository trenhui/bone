package com.bone.tpa.sdk.adjustment.response;

import lombok.Data;

/**
 * 单证配置
 */
@Data
public class CertificateConfig {

    /**
     * 是否需生成理赔申请书
     */
    private Boolean createApplication;

    /**
     * 理赔申请书配置
     */
    private ConfigNode applicationConfig;


    /**
     * 是否需生成理赔通知书
     */
    private Boolean createNotification;

    /**
     * 理赔通知书配置
     */
    private ConfigNode notificationConfig;

    @Data
    public static class ConfigNode {
        /**
         * 适用赔案-下拉框
         */
        private Integer claimAuditType;

        /**
         * 选择模版-下拉框
         */
        private String certificateTemplate;

        /**
         * 生成节点-下拉框
         */
        private String claimProcessNode;

        /**
         * 生成条件（理赔结论）-下拉框
         */
        private String adjustmentConclusion;


        /**
         * 所属影像分类-下拉框(保司分类)
         */
        private String imageType;

        /**
         * 所属影像分类-下拉框(普康分类)
         */
        private String imageTypePK;
    }
}
