package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.result.PageResult;
import com.bone.studio.generator.application.command.cmd.*;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQry;
import com.bone.studio.generator.application.usecase.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/code-templates")
@RequiredArgsConstructor
public class CodeTemplateController {

    private final CreateCodeTemplateUseCase createCodeTemplateUseCase;
    private final UpdateCodeTemplateUseCase updateCodeTemplateUseCase;
    private final DeleteCodeTemplateUseCase deleteCodeTemplateUseCase;
    private final PublishCodeTemplateUseCase publishCodeTemplateUseCase;
    private final GetCodeTemplateListUseCase getCodeTemplateListUseCase;

    @PostMapping
    public ApiResponse<Long> createCodeTemplate(@RequestBody CreateCodeTemplateCommand command) {
        return ApiResponse.success(createCodeTemplateUseCase.execute(command));
    }

    @PutMapping("/{id}")
    public ApiResponse<Long> updateCodeTemplate(@PathVariable Long id, @RequestBody UpdateCodeTemplateCommand command) {
        command = UpdateCodeTemplateCommand.builder()
                .id(id)
                .name(command.getName())
                .code(command.getCode())
                .description(command.getDescription())
                .type(command.getType())
                .content(command.getContent())
                .build();
        return ApiResponse.success(updateCodeTemplateUseCase.execute(command));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteCodeTemplate(@PathVariable Long id) {
        DeleteCodeTemplateCommand command = DeleteCodeTemplateCommand.builder()
                .id(id)
                .build();
        return ApiResponse.success(deleteCodeTemplateUseCase.execute(command));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Long> publishCodeTemplate(@PathVariable Long id) {
        PublishCodeTemplateCommand command = PublishCodeTemplateCommand.builder()
                .id(id)
                .build();
        return ApiResponse.success(publishCodeTemplateUseCase.execute(command));
    }

    @GetMapping
    public ApiResponse<PageResult<?>> getCodeTemplateList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        GetCodeTemplateListQry qry = GetCodeTemplateListQry.builder()
                .page(page)
                .size(size)
                .type(type)
                .status(status)
                .build();
        return ApiResponse.success(getCodeTemplateListUseCase.execute(qry));
    }
}
