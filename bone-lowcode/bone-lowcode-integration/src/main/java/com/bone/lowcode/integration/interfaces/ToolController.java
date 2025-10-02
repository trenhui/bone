package com.bone.lowcode.integration.interfaces;

import com.bone.core.result.Result;
import com.bone.lowcode.integration.application.dto.FreemarkerDTO;
import com.bone.lowcode.integration.application.service.IFreemarkerToolService;
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
    public Result<String> freemarkerExecute(@RequestBody FreemarkerDTO freemarkerDTO) {
        try {
            return Result.ok(freemarkerToolService.execute(freemarkerDTO));
        } catch (Throwable e) {
            log.error("freemarker execute error", e);
            return Result.error(e.getMessage());
        }
    }
}
