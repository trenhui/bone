package com.bone.tpa.sdk.adjustment.model;

import com.alibaba.fastjson.JSON;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.tpa.sdk.adjustment.response.CertificateConfig;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 保单信息
 */
@Table("ia_policy")
@Data
public class Policy extends TenantAbstractEntity<Long> {

    /**
     * 普康保单号
     */
    private String policyNo;

    /**
     * 父保单号 业务虚拟保单使用
     */
    private String parentPolicyNo;

    /**
     * 投保公司
     */
    private String insureName;

    /**
     * 投保公司Guid
     */
    private String insureId;

    /**
     * 保险公司
     */
    private String insuranceName;

    /**
     * 保险公司Guid
     */
    private String carrierId;

    /**
     * 保司保单号
     */
    private String externalPolicyNo;

    /**
     * 保单责任配置状态，启用中未启用停用中
     */
    private String configStatus;

    /**
     * 状态，见枚举 PolicyStatus
     */
    private Integer status;

    /**
     * 保单生效日
     */
    private Date effdate;

    /**
     * 保单失效日
     */
    private Date expdate;

    /**
     * 保单类型（-1：未设置 0：正式保单 1：测试保单 2：演示保单 3：体验保单)
     */
    private Integer policyType;

    /**
     * 购买方式（-1：未设置 0：新保单 1：续保)
     */
    private Integer policyBuyType;

    /**
     * 其他公司
     */
    private String otherCompany;

    /**
     * 其他公司Guid
     */
    private String otherCompanyId;

    /**
     * 项目类型
     */
    public Integer itemType;

    /**
     * 0：未设置属性（默认）1：基金业务；2：团险业务
     */
    private Integer policyAttribute;

    /**
     * 保单状态(0-未启用 1-启用)
     */
    private Integer policyFlag;

    /**
     * 理算顺序,0:理算后,1:理算前
     */
    private Integer adjustmentOrder;

    /**
     * 单证配置
     */
    private String certificateConfig;

    /**
     * 获取单证配置
     */
    public CertificateConfig queryCertificateConfig() {
        if (!StringUtils.hasText(certificateConfig)) {
            return null;
        }
        return JSON.parseObject(certificateConfig, CertificateConfig.class);
    }

    /**
     * 设置单证配置
     */
    public void updateCertificateConfig(CertificateConfig config) {
        if (config == null) {
            return;
        }
        certificateConfig = JSON.toJSONString(config);
    }
}
