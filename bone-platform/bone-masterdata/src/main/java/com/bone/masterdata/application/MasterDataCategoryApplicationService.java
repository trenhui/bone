package com.bone.masterdata.application;

import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.AssignRecordCategoryCommand;
import com.bone.masterdata.application.command.CreateMasterDataCategoryCommand;
import com.bone.masterdata.application.command.UpdateMasterDataCategoryCommand;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.category.MasterDataCategory;
import com.bone.masterdata.domain.model.category.RecordCategoryLink;
import com.bone.masterdata.domain.repository.MasterDataCategoryRepository;
import com.bone.masterdata.domain.repository.RecordCategoryLinkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 分类体系应用服务（G4，UC-T3）：实体内树形分类维护 + 记录归类。 */
@Service
@RequiredArgsConstructor
public class MasterDataCategoryApplicationService {

  private final MasterDataCategoryRepository categoryRepository;
  private final RecordCategoryLinkRepository linkRepository;

  @Transactional
  public Long create(CreateMasterDataCategoryCommand cmd) {
    if (categoryRepository.countByEntityIdAndCode(cmd.getMasterDataEntityId(), cmd.getCode()) > 0) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.CATEGORY_CODE_DUPLICATE, "分类编码已存在: " + cmd.getCode());
    }
    Integer parentLevel = null;
    if (cmd.getParentCategoryId() != null) {
      MasterDataCategory parent = requireCategory(cmd.getParentCategoryId());
      parentLevel = parent.getLevel();
    }
    MasterDataCategory category =
        MasterDataCategory.create(
            DistributedIdGenerator.generateLongId(),
            cmd.getMasterDataEntityId(),
            cmd.getCode(),
            cmd.getName(),
            cmd.getDescription(),
            cmd.getParentCategoryId(),
            parentLevel,
            cmd.getSortOrder());
    category.ensureNotSelfParent(cmd.getParentCategoryId());
    return categoryRepository.insert(category);
  }

  @Transactional
  public void update(UpdateMasterDataCategoryCommand cmd) {
    MasterDataCategory category = requireCategory(cmd.getId());
    category.update(cmd.getName(), cmd.getDescription(), cmd.getSortOrder());
    categoryRepository.update(category);
  }

  @Transactional
  public void delete(Long id) {
    MasterDataCategory category = requireCategory(id);
    if (categoryRepository.countByParentId(id) > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.CATEGORY_STATE_INVALID, "存在子分类，不能删除");
    }
    categoryRepository.deleteById(id);
  }

  @Transactional
  public void assignRecord(AssignRecordCategoryCommand cmd) {
    if (linkRepository.countByRecordIdAndCategoryId(cmd.getRecordId(), cmd.getCategoryId()) == 0) {
      linkRepository.insert(
          RecordCategoryLink.create(
              DistributedIdGenerator.generateLongId(), cmd.getRecordId(), cmd.getCategoryId()));
    }
  }

  @Transactional
  public void unassignRecord(Long recordId, Long categoryId) {
    linkRepository.findByRecordId(recordId).stream()
        .filter(l -> l.getCategoryId().equals(categoryId))
        .forEach(l -> linkRepository.deleteById(l.getId()));
  }

  @Transactional(readOnly = true)
  public List<MasterDataCategory> tree(Long masterDataEntityId) {
    return categoryRepository.findByEntityId(masterDataEntityId);
  }

  @Transactional(readOnly = true)
  public List<Long> recordCategoryIds(Long recordId) {
    return linkRepository.findByRecordId(recordId).stream()
        .map(RecordCategoryLink::getCategoryId)
        .toList();
  }

  private MasterDataCategory requireCategory(Long id) {
    MasterDataCategory category = categoryRepository.findById(id);
    if (category == null) {
      throw NotFoundException.of("主数据分类不存在");
    }
    return category;
  }
}
