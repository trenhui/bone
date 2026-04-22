package com.bone.blueprint.adapter.web.controller.dashboard;

import com.bone.blueprint.application.command.cmd.dashboard.CreateWidgetCmd;
import com.bone.blueprint.application.command.cmd.dashboard.UpdateWidgetCmd;
import com.bone.blueprint.application.command.handler.dashboard.CreateWidgetHandler;
import com.bone.blueprint.application.command.handler.dashboard.UpdateWidgetHandler;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class WidgetController {
    private final CreateWidgetHandler createWidgetHandler;
    private final UpdateWidgetHandler updateWidgetHandler;
    
    @PostMapping("/{dashboardId}/widgets")
    public ApiResponse<Long> createWidget(@PathVariable Long dashboardId, @RequestBody CreateWidgetCmd cmd) {
        cmd.setDashboardId(dashboardId);
        Long id = createWidgetHandler.handle(cmd);
        return ApiResponse.success(id);
    }
    
    @PutMapping("/widgets/{id}")
    public ApiResponse<Void> updateWidget(@PathVariable Long id, @RequestBody UpdateWidgetCmd cmd) {
        cmd.setId(id);
        updateWidgetHandler.handle(cmd);
        return ApiResponse.success();
    }
}
