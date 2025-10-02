package com.bone.lowcode.integration.interfaces;

import com.bone.core.result.Result;
import com.bone.lowcode.integration.application.service.ICamelRouteService;
import com.bone.lowcode.integration.enums.EnvEnum;
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
    public Result<Void> buildNode(@RequestParam("id") Long id) throws Exception {
        try {
            iCamelRouteService.buildRouteByFlowId(id, EnvEnum.DEV.name());
            return Result.ok();
        } catch (Throwable e) {
            log.error("buildNode error, flowId: {}", id, e);
            return Result.error(e.getCause().getMessage());
        }
    }

    @RequestMapping("deployFlowForPod")
    @ResponseBody
    public Result<Void> deployFlowForPod(@RequestParam("flowVersionId") Long flowVersionId) throws Exception {
        try {
            iCamelRouteService.buildRouteByFlowVersionId(flowVersionId);
            return Result.ok();
        } catch (Throwable e) {
            log.error("deployFlowForPod error, flowId: {}", flowVersionId, e);
            return Result.error(e.getMessage());
        }
    }

    @RequestMapping("stopFlowForPod")
    @ResponseBody
    public Result<Void> stopFlowForPod(@RequestParam("flowVersionId") Long flowVersionId) throws Exception {
        try {
            iCamelRouteService.stopRouteByFlowVersionId(flowVersionId);
            return Result.ok();
        } catch (Throwable e) {
            log.error("deployFlowForPod error, flowId: {}", flowVersionId, e);
            return Result.error(e.getMessage());
        }
    }
}