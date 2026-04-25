package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.application.query.qry.LoadTablesQry;
import com.bone.studio.generator.application.query.handler.LoadTablesHandler;
import com.bone.studio.generator.application.usecase.standard.GenerateCodeUseCase;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.data.DatabaseTable;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/code-generator")
public class CodeGeneratorController {

    private final GenerateCodeUseCase generateCodeUseCase;
    private final LoadTablesHandler loadTablesHandler;

    public CodeGeneratorController(GenerateCodeUseCase generateCodeUseCase, 
                                LoadTablesHandler loadTablesHandler) {
        this.generateCodeUseCase = generateCodeUseCase;
        this.loadTablesHandler = loadTablesHandler;
    }

    @PostMapping("/generate")
    public ApiResponse<CodeGenerationResponse> generateCode(@RequestBody GenerateCodeCommand command) {
        CodeGenerationResponse response = generateCodeUseCase.execute(command);
        return ApiResponse.success(response);
    }

    @GetMapping("/tables/{dataSourceId}")
    public ApiResponse<List<DatabaseTable>> loadTables(@PathVariable String dataSourceId) {
        LoadTablesQry query = LoadTablesQry.builder()
                .dataSourceId(dataSourceId)
                .build();
        List<DatabaseTable> tables = loadTablesHandler.handle(query);
        return ApiResponse.success(tables);
    }
}