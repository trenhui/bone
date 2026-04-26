package com.bone.tpa.soa.adapter;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.api.request.ClaimPushFailRequest;
import com.bone.tpa.api.request.ReturnToManualRequest;
import com.bone.tpa.api.response.ClaimPushFailOperateResponse;
import com.bone.tpa.api.response.ClaimPushFailResponse;
import com.bone.tpa.soa.application.ClaimPushService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Author feihaiming
 */
@RestController
@RequestMapping("/claim/push")
public class ClaimPushController {

    @Autowired
    private ClaimPushService claimPushService;

    @PostMapping("/return")
    public ApiResult returnToManual(@RequestBody ReturnToManualRequest returnToManualRequest) {
        return claimPushService.returnToManual(returnToManualRequest);
    }

    @PostMapping("/repush")
    public ApiResult repush(@RequestBody List<Long> claimNos) {
        return claimPushService.repush(claimNos);
    }

    @PostMapping("/pageClaimPushFail")
    public ApiResult<PageResult<ClaimPushFailResponse>> pageClaimPushFail(@RequestBody ClaimPushFailRequest request) {
        PageResult<ClaimPushFailResponse> pageResult = claimPushService.pageClaimPushFail(request);
        ApiResult apiResult = new ApiResult();
        apiResult.setData(pageResult);
        return apiResult;
    }

    @PostMapping("/exportClaimPushFail")
    public void exportClaimPushFail(@RequestBody ClaimPushFailRequest request, HttpServletResponse response) {
        claimPushService.exportClaimPushFail(request, response);
    }

    @PostMapping("/pageClaimPushFailOperate")
    public ApiResult<PageResult<ClaimPushFailOperateResponse>> pageClaimPushFailOperate(@RequestBody ClaimPushFailRequest request) {
        PageResult<ClaimPushFailOperateResponse> pageResult = claimPushService.pageClaimPushFailOperate(request);
        ApiResult apiResult = new ApiResult();
        apiResult.setData(pageResult);
        return apiResult;
    }

    @PostMapping("/exportClaimPushFailOperate")
    public void exportClaimPushFailOperate(@RequestBody ClaimPushFailRequest request, HttpServletResponse response) {
        claimPushService.exportClaimPushFailOperate(request, response);
    }

}
