package com.bone.tpa.sdk.adjustment.model;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 理算裁定结果
 */
@Table("ia_adjustment_result")
public class AdjustmentResult extends ExtensibleObject<Long> {

    /**
     * 关联赔案ID
     */
    private Long claimId;

    /**
     * 理算结果状态
     */
    private String resultStatus;

    /**
     * 赔付金额
     */
    private BigDecimal payoutAmount = BigDecimal.ZERO;

    /**
     * 公账赔付金额
     */
    private BigDecimal publicAmount = BigDecimal.ZERO;

    /**
     * 个账赔付金额
     */
    private BigDecimal individualAmount = BigDecimal.ZERO;

    /**
     * 理算结论,如：{"code":["D4","D4:03"],"desc":["零赔付","事故损失小于免赔额"]}
     */
    private String result;

    /**
     * 结论明细
     */
    private String resultDetail;

    /**
     * 获取理算结论code列表,如：[D4, D4:03]
     */
    public List<String> getResultCode() {
        if (StringUtils.hasText(result)) {
            try {
                JSONObject jsonObject = JSON.parseObject(result);
                return jsonObject.getJSONArray("code").toJavaList(String.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return List.of();
    }

    /**
     * 获取理算结论desc列表,如：[零赔付, 事故损失小于免赔额]
     */
    public List<String> getResultDesc() {
        if (StringUtils.hasText(result)) {
            try {
                JSONObject jsonObject = JSON.parseObject(result);
                return jsonObject.getJSONArray("desc").toJavaList(String.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return List.of();
    }

    // Getters and Setters
    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public BigDecimal getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(BigDecimal payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

    public BigDecimal getPublicAmount() {
        return publicAmount;
    }

    public void setPublicAmount(BigDecimal publicAmount) {
        this.publicAmount = publicAmount;
    }

    public BigDecimal getIndividualAmount() {
        return individualAmount;
    }

    public void setIndividualAmount(BigDecimal individualAmount) {
        this.individualAmount = individualAmount;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getResultDetail() {
        return resultDetail;
    }

    public void setResultDetail(String resultDetail) {
        this.resultDetail = resultDetail;
    }
}
