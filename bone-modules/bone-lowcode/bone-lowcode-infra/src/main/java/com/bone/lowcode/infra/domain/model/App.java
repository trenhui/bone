package com.bone.lowcode.infra.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 系统应用
 *
 * @author 梅山源码
 */
@Table("system_app")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class App extends TenantAbstractEntity<Long> {
    /**
     * 名称
     */
    private String name;
    /**
     * 编码
     */
    private String code;
    /**
     * 状态（字典  0 正常 1 待上线 2 下线）
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
