package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.processPage.UpdateProcessDetailPageDTO;
import com.bone.lowcode.infra.application.dto.processPage.UpdateProcessPageDTO;
import com.bone.lowcode.infra.application.service.ProcessPageApplicationService;
import com.bone.lowcode.infra.application.vo.page.pageJson.NewPageVO;
import com.bone.lowcode.infra.application.vo.processPage.FirstAuditDetailPageVO;
import com.bone.lowcode.infra.application.vo.processPage.ProcessDetailPageVO;
import com.bone.lowcode.infra.application.vo.processPage.ProcessListPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/processPage")
@Validated
public class ProcessPageController {

    @Autowired
    private ProcessPageApplicationService processPageApplicationService;

    /**
     * 流程列表页 查询
     */
    @GetMapping("/getProcessListPage")
    public Result getProcessListPage(@RequestParam("code") String code,
                                     @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        ProcessListPageVO vo = processPageApplicationService.getProcessListPage(code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 流程列表页 更新
     */
    @PostMapping("/updateProcessListPage")
    public Result updateProcessListPage(@RequestBody UpdateProcessPageDTO dto) {
        boolean flag = processPageApplicationService.updateProcessListPage(dto);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }

    /**
     * 流程详情页(签收环节) 查询
     */
    @GetMapping("/getProcessDetailPage")
    public Result getProcessDetailPage(@RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        ProcessDetailPageVO vo = processPageApplicationService.getProcessDetailPage(bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 流程详情页(新批次签收) 查询
     */
    @GetMapping("/getNewSignPage")
    public Result getNewSignPage(@RequestParam("displayMode") String displayMode,
                                 @RequestParam("code") String code,
                                 @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        NewPageVO vo = processPageApplicationService.getNewSignPage(displayMode, code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 初审详情页 查询
     */
    @GetMapping("/getFirstAuditDetailPage")
    public Result getFirstAuditDetailPage(@RequestParam("code") String code,
                                          @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        FirstAuditDetailPageVO vo = processPageApplicationService.getFirstAuditDetailPage(code, bizIdentityCode);
        return Result.ok(vo);
    }

    /**
     * 初审详情页 更新
     */
    @PostMapping("/updateFirstAuditDetailPage")
    public Result updateFirstAuditDetailPage(@RequestBody UpdateProcessDetailPageDTO dto) {
        boolean flag = processPageApplicationService.updateFirstAuditDetailPage(dto);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }
}
