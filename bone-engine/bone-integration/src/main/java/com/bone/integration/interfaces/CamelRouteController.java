package com.bone.integration.interfaces;

import com.bone.core.model.ApiResponse;
import com.bone.integration.application.service.ICamelRouteService;
import com.bone.integration.enums.EnvEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

@Controller
@RequestMapping("router")
@Slf4j
public class CamelRouteController {

    @Resource
    private ICamelRouteService iCamelRouteService;

    @RequestMapping("buildNode")
    @ResponseBody
    public ApiResponse<Void> buildNode(@RequestParam("id") Long id) throws Exception {
        try {
            iCamelRouteService.buildRouteByFlowId(id, EnvEnum.DEV.name());
            return ApiResponse.success();
        } catch (Throwable e) {
            log.error("buildNode error, flowId: {}", id, e);
            return ApiResponse.error(e.getCause().getMessage());
        }
    }

    @RequestMapping("deployFlowForPod")
    @ResponseBody
    public ApiResponse<Void> deployFlowForPod(@RequestParam("flowVersionId") Long flowVersionId) throws Exception {
        try {
            iCamelRouteService.buildRouteByFlowVersionId(flowVersionId);
            return ApiResponse.success();
        } catch (Throwable e) {
            log.error("deployFlowForPod error, flowId: {}", flowVersionId, e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @RequestMapping("stopFlowForPod")
    @ResponseBody
    public ApiResponse<Void> stopFlowForPod(@RequestParam("flowVersionId") Long flowVersionId) throws Exception {
        try {
            iCamelRouteService.stopRouteByFlowVersionId(flowVersionId);
            return ApiResponse.success();
        } catch (Throwable e) {
            log.error("deployFlowForPod error, flowId: {}", flowVersionId, e);
            return ApiResponse.error(e.getMessage());
        }
    }
}