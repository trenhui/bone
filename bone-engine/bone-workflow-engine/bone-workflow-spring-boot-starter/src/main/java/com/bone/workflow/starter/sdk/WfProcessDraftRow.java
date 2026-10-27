package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_process_draft}（流程定义草稿）的 SDK 映射行类。
 *
 * <p><b>为什么草稿单独一张表</b>（设计文档 §5.1）：{@code wf_process_def} 是<b>不可变快照</b>——在途实例按启动时钉住的 {@code
 * def_version} 装载定义，版本行一旦可改，历史单据的审批依据就随之漂移。草稿可变，因此不能混进同一张表； 更硬的理由是读取侧：{@code
 * WfProcessDefSdkRepository.findByVersionRow} 的谓词只判 {@code deleted=0}、不判 {@code status}，
 * 草稿若与已发布版本共用版本号，同一 {@code (def_key, def_version)} 会读出两行，异常将在<b>在途实例推进</b>时以 {@code
 * IncorrectResultSizeDataAccessException} 现形——错误现场与根因完全脱节。
 *
 * <p>{@code definitionJson} 是含 {@code ui} 段的<b>原文</b>：草稿阶段坐标也要保存，否则每次打开设计器版式跳变。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfProcessDraftRow extends Entity<String> {

  private String defKey;
  private String defName;
  private String definitionJson;

  /** 乐观锁版本：前端带旧值提交，SQL 以 {@code AND revision = ?} 判定是否被他人覆盖。 */
  private int revision;

  private String updatedBy;
}
