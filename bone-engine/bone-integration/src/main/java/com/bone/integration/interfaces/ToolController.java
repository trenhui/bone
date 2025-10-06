package com.bone.integration.interfaces;

import com.bone.core.model.ApiResponse;
import com.bone.integration.application.dto.FreemarkerDTO;
import com.bone.integration.application.service.IFreemarkerToolService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("tool")
@Slf4j
public class ToolController {

    @Resource
    private IFreemarkerToolService freemarkerToolService;

    @PostMapping("freemarkerExecute")
    @ResponseBody
    public ApiResponse<String> freemarkerExecute(@RequestBody FreemarkerDTO freemarkerDTO) {
        try {
            return ApiResponse.success(freemarkerToolService.execute(freemarkerDTO));
        } catch (Throwable e) {
            log.error("freemarker execute error", e);
            return ApiResponse.error(e.getMessage());
        }
    }
}
