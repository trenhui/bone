package com.bone.tpa.soa.application;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.request.ClaimPushFailRequest;
import com.bone.tpa.api.request.ReturnToManualRequest;
import com.bone.tpa.api.response.ClaimPushFailOperateResponse;
import com.bone.tpa.api.response.ClaimPushFailResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface ClaimPushService {
    ApiResult repush(List<Long> claimNos);
    ApiResult returnToManual(ReturnToManualRequest returnToManualRequest);
    String syncClaimPushStatus(String claimNo, Integer status, String pushBackReason, String errorType);

    /**
     * 分页查询推送失败
     *
     * @param request
     * @return
     */
    PageResult<ClaimPushFailResponse> pageClaimPushFail(ClaimPushFailRequest request);

    /**
     * 导出推送失败
     *
     * @param request
     * @param response
     */
    void exportClaimPushFail(ClaimPushFailRequest request, HttpServletResponse response);


    /**
     * 分页查询推送失败
     *
     * @param request
     * @return
     */
    PageResult<ClaimPushFailOperateResponse> pageClaimPushFailOperate(ClaimPushFailRequest request);


    /**
     * 导出推送失败
     *
     * @param request
     * @param response
     */
    void exportClaimPushFailOperate(ClaimPushFailRequest request, HttpServletResponse response);

}
