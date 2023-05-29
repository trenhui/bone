package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import java.util.Date;
import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 事件
 *
 * @author 梅山源码
 */
@Table("project_event")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEvent extends TenantAbstractEntity<Long> {
        /**
         * 事件编号
         */
    private String eventNumber;
        /**
         * 事件名称
         */
    private String eventName;
        /**
         * 预算金额
         */
    private BigDecimal bizAmount;
        /**
         * 业务币种
         */
    private String bizCurrencyCode;
        /**
         * 事件开始时间
         */
    private Date eventStartTime;
        /**
         * 事件结束时间
         */
    private Date eventEndTime;
        /**
         * 删除标识:0未删除，1删除
         */
    private String delFlag;
        /**
         * 依附子项目id
         */
    private Long projectId;
}

