package com.bone.blueprint.adapter.web.controller.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.CreateDashboardCmd;
import com.bone.blueprint.application.command.cmd.dashboard.UpdateDashboardCmd;
import com.bone.blueprint.application.command.handler.dashboard.CreateDashboardHandler;
import com.bone.blueprint.application.command.handler.dashboard.UpdateDashboardHandler;
import com.bone.blueprint.application.query.qry.dashboard.DashboardPageQry;
import com.bone.blueprint.application.query.handler.dashboard.DashboardPageQueryHandler;
import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.blueprint.application.query.dto.dashboard.DashboardDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final CreateDashboardHandler createDashboardHandler;
    private final UpdateDashboardHandler updateDashboardHandler;
    private final DashboardPageQueryHandler dashboardPageQueryHandler;
    
    @GetMapping
    public ApiResponse<PageResult<DashboardDTO>> getDashboards(@RequestParam Long userId, 
                                                               @RequestParam int pageNum, 
                                                               @RequestParam int pageSize) {
        DashboardPageQry qry = new DashboardPageQry();
        qry.setUserId(userId);
        qry.setPageNum(pageNum);
        qry.setPageSize(pageSize);
        
        PageResult<DashboardDTO> result = dashboardPageQueryHandler.handle(qry);
        return ApiResponse.success(result);
    }
    
    @PostMapping
    public ApiResponse<Long> createDashboard(@RequestBody CreateDashboardCmd cmd) {
        Long id = createDashboardHandler.handle(cmd);
        return ApiResponse.success(id);
    }
    
    @PutMapping("/{id}")
    public ApiResponse<Void> updateDashboard(@PathVariable Long id, @RequestBody UpdateDashboardCmd cmd) {
        cmd.setId(id);
        updateDashboardHandler.handle(cmd);
        return ApiResponse.success();
    }
}
