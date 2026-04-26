package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;


/**
 * ss_claim DO
 *
 * @author 0
 */
@Table("ss_file_upload_record")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRecord extends TenantAbstractEntity<Long> {

    /**
     * 上传场景
     */
    private String uploadScene;
    /**
     * 关联模型名
     */
    private String relatedModel;
    /**
     * 关联表id
     */
    private Long relatedId;
    /**
     * 文件类型
     */
    private String fileType;
    /**
     * 文件路径
     */
    private String filePath;
    /**
     * 文件名称
     */
    private String fileName;
    /**
     * 是否成功上传
     */
    private Integer success;
    /**
     * 是否成功上传
     */
    private String status;
    /**
     * 导入方式
     */
    private String importType;
    /**
     * 校验方式
     */
    private String checkType;
    /**
     * 同名文件处理规则
     */
    private String sameFileRule;
    /**
     * 备注
     */
    private String remark;
    /**
     * 操作者
     */
    private String operator;
}
