package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.CreateModuleCommand;
import com.bone.iam.application.command.UpdateModuleCommand;
import com.bone.iam.application.query.dto.ModuleDTO;
import com.bone.iam.application.query.qry.ModuleListQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.model.app.BoneModule;
import com.bone.iam.domain.repository.BoneModuleRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块应用层统一门面（Application Service First）——模块类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code application.app.command.handler.*Module*Handler} 与 {@code
 * application.app.query.handler.ModuleListQueryHandler} 已全量内联进本类（Q5：撤销平行 CQRS 树）。适配器只依赖本类，HTTP
 * 契约保持不变。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL：模块分页与按 id 取数下沉 {@link BoneModuleRepository#findModulePage} / {@link
 * BoneModuleRepository#findModuleById}（本聚合读，ADR-0030 / E-4.2）。
 */
/*
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（BoneModule）当前不发布领域事件，其创建/更新均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class ModuleApplicationService {

  private final BoneModuleRepository boneModuleRepository;

  @Transactional
  public Long createModule(CreateModuleCommand cmd) {
    BoneModule mod =
        BoneModule.create(cmd.getAppId(), cmd.getName(), cmd.getCode(), cmd.getDescription(), 0L);
    return boneModuleRepository.save(mod);
  }

  @Transactional
  public void updateModule(UpdateModuleCommand cmd) {
    BoneModule mod = boneModuleRepository.findById(cmd.getId());
    if (mod == null) {
      throw IamErrors.of(IamErrorCodes.MODULE_NOT_FOUND, "模块不存在");
    }
    mod.update(cmd.getName(), cmd.getDescription(), cmd.getStatus());
    boneModuleRepository.save(mod);
  }

  @Transactional
  public void deleteModule(Long id) {
    boneModuleRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public PageResult<ModuleDTO> listModules(ModuleListQuery qry) {
    PageResult<BoneModule> result =
        boneModuleRepository.findModulePage(
            qry.getAppId(),
            qry.getPage() != null ? qry.getPage() : 1,
            qry.getSize() != null ? qry.getSize() : 100);
    List<ModuleDTO> list =
        result.getRecords().stream()
            .map(ModuleApplicationService::toDto)
            .collect(Collectors.toList());
    return PageResult.of(list, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public ModuleDTO moduleDetail(Long id) {
    BoneModule entity = boneModuleRepository.findModuleById(id);
    if (entity == null) {
      throw IamErrors.of(IamErrorCodes.MODULE_NOT_FOUND, "模块不存在");
    }
    return toDto(entity);
  }

  private static ModuleDTO toDto(BoneModule entity) {
    ModuleDTO dto = new ModuleDTO();
    dto.setId(entity.getId());
    dto.setAppId(entity.getAppId());
    dto.setName(entity.getName());
    dto.setCode(entity.getCode());
    dto.setDescription(entity.getDescription());
    dto.setStatus(entity.getStatus());
    dto.setEntityCount(0);
    dto.setFieldCount(0);
    dto.setSortOrder(entity.getSortOrder());
    dto.setCreatedAt(entity.getCreatedAt());
    return dto;
  }
}
