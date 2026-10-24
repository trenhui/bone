package com.bone.workflow.starter.sdk;

import com.bone.core.domain.entity.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code wf_active_instance}（活动实例指针）的 SDK 映射行类。
 *
 * <p><b>标记行类，无字段</b>：该表只写不读——指针的生命周期全部由三条自定义 {@code @Sql} 表达 （{@link
 * WfActiveInstanceSdkRepository#insertActivePointer} / {@link #deleteActivePointer} / {@link
 * #deleteActivePointerByInstance}），没有任何查询需要把整行映射回内存；「一单据一活动实例」的判据是 插入撞主键，读路径从来不需要指针行的内容。
 *
 * <p>保留类型参数只为满足 {@code Repository<WfActiveInstanceRow, String>} 的签名；表本身以 {@code (tenant_id,
 * entity_code, record_id)} 自然主键存在，<b>没有</b> id 列。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WfActiveInstanceRow extends Entity<String> {}
