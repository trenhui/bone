package com.bone.tpa.claim.domain.ext;

import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.sdk.claim.model.Claim;

import java.util.List;

public interface ExtensionStrategy {

    /**
     * 获取该策略对应的保司名称
     */
    String getInsuranceName();


    /**
     * 赔案提交时的规则检测
     */
    void claimSubmitValidate(Claim claim);

}
