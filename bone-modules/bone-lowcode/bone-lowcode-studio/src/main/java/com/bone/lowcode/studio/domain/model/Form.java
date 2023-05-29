package com.bone.lowcode.studio.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 表单
 *
 * @author 梅山源码
 */
@Table("lc_form")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Form extends TenantAbstractEntity<Long> {
        /**
         * 名称
         */
    private String name;
        /**
         * 编码
         */
    private String code;
        /**
         * 内容
         */
    private String content;
        /**
         * 所属应用
         */
    private Long appId;
        /**
         * 备注
         */
    private String remark;
        /**
         * 状态（0New1Runing2Offline）
         */
    private Integer status;
}

