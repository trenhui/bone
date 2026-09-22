package com.bone.studio.generator.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.studio.generator.application.command.cmd.CreateCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.DeleteCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.PublishCodeTemplateCommand;
import com.bone.studio.generator.application.command.cmd.UpdateCodeTemplateCommand;
import com.bone.studio.generator.application.command.handler.CreateCodeTemplateApplicationService;
import com.bone.studio.generator.application.command.handler.DeleteCodeTemplateApplicationService;
import com.bone.studio.generator.application.command.handler.PublishCodeTemplateApplicationService;
import com.bone.studio.generator.application.command.handler.UpdateCodeTemplateApplicationService;
import com.bone.studio.generator.application.query.handler.GetCodeTemplateDetailQueryApplicationService;
import com.bone.studio.generator.application.query.handler.GetCodeTemplateListQueryApplicationService;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateDetailQuery;
import com.bone.studio.generator.application.query.qry.GetCodeTemplateListQuery;
import com.bone.studio.generator.common.GeneratorApiPaths;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
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

  private final CreateCodeTemplateApplicationService createCodeTemplateHandler;
  private final UpdateCodeTemplateApplicationService updateCodeTemplateHandler;
  private final DeleteCodeTemplateApplicationService deleteCodeTemplateHandler;
  private final PublishCodeTemplateApplicationService publishCodeTemplateHandler;
  private final GetCodeTemplateListQueryApplicationService queryHandler;
  private final GetCodeTemplateDetailQueryApplicationService detailQueryHandler;

  @PostMapping
  public ApiResponse<Long> createCodeTemplate(@RequestBody CreateCodeTemplateCommand command) {
    return ApiResponse.success(createCodeTemplateHandler.handle(command));
  }

  @PutMapping("/{id}")
  public ApiResponse<Long> updateCodeTemplate(
      @PathVariable Long id, @RequestBody UpdateCodeTemplateCommand command) {
    command =
        UpdateCodeTemplateCommand.builder()
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
    DeleteCodeTemplateCommand command = DeleteCodeTemplateCommand.builder().id(id).build();
    return ApiResponse.success(deleteCodeTemplateHandler.handle(command));
  }

  @GetMapping("/{id}")
  public ApiResponse<CodeTemplate> getCodeTemplate(@PathVariable Long id) {
    GetCodeTemplateDetailQuery qry = GetCodeTemplateDetailQuery.builder().id(id).build();
    return ApiResponse.success(detailQueryHandler.handle(qry));
  }

  @PostMapping("/{id}:publish")
  public ApiResponse<Long> publishCodeTemplate(@PathVariable Long id) {
    PublishCodeTemplateCommand command = PublishCodeTemplateCommand.builder().id(id).build();
    return ApiResponse.success(publishCodeTemplateHandler.handle(command));
  }

  @GetMapping
  public ApiResponse<PageResult<?>> getCodeTemplateList(
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "10") Integer size,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status) {
    GetCodeTemplateListQuery qry =
        GetCodeTemplateListQuery.builder().page(page).size(size).type(type).status(status).build();
    return ApiResponse.success(queryHandler.handle(qry));
  }
}
