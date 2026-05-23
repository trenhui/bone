package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.result.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.DeleteCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.PublishCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.UpdateCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeTemplateHandler;
import com.bone.studio.generator.application.command.handler.DeleteCodeTemplateHandler;
import com.bone.studio.generator.application.command.handler.PublishCodeTemplateHandler;
import com.bone.studio.generator.application.command.handler.UpdateCodeTemplateHandler;
import com.bone.studio.generator.application.query.handler.GetCodeTemplateListQueryHandler;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQuery;
import com.bone.studio.generator.common.GeneratorApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GeneratorApiPaths.TEMPLATES)
@RequiredArgsConstructor
public class CodeTemplateController {

    private final CreateCodeTemplateHandler createCodeTemplateHandler;
    private final UpdateCodeTemplateHandler updateCodeTemplateHandler;
    private final DeleteCodeTemplateHandler deleteCodeTemplateHandler;
    private final PublishCodeTemplateHandler publishCodeTemplateHandler;
    private final GetCodeTemplateListQueryHandler queryHandler;

    @PostMapping
    public ApiResponse<Long> createCodeTemplate(@RequestBody CreateCodeTemplateCommand command) {
        return ApiResponse.success(createCodeTemplateHandler.handle(command));
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
        return ApiResponse.success(updateCodeTemplateHandler.handle(command));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteCodeTemplate(@PathVariable Long id) {
        DeleteCodeTemplateCommand command = DeleteCodeTemplateCommand.builder()
                .id(id)
                .build();
        return ApiResponse.success(deleteCodeTemplateHandler.handle(command));
    }

    @PostMapping("/{id}:publish")
    public ApiResponse<Long> publishCodeTemplate(@PathVariable Long id) {
        PublishCodeTemplateCommand command = PublishCodeTemplateCommand.builder()
                .id(id)
                .build();
        return ApiResponse.success(publishCodeTemplateHandler.handle(command));
    }

    @GetMapping
    public ApiResponse<PageResult<?>> getCodeTemplateList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        GetCodeTemplateListQuery qry = GetCodeTemplateListQuery.builder()
                .page(page)
                .size(size)
                .type(type)
                .status(status)
                .build();
        return ApiResponse.success(queryHandler.handle(qry));
    }
}
