package com.bone.lowcode.infra.domain.model;

import com.bone.core.domain.entity.AbstractEntity;
import lombok.*;
import java.util.*;
import java.util.Date;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 租户
 *
 * @author 梅山源码
 */
@Table("system_tenant")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant extends AbstractEntity<Long> {
        /**
         * 名称
         */
    private String name;
        /**
         * 编码
         */
    private String code;
        /**
         * 生效时间
         */
    private Date startDate;
        /**
         * 结束时间
         */
    private Date endDate;
        /**
         * 状态1正常0冻结
         */
    private Integer status;
        /**
         * 所属行业
         */
    private String trade;
        /**
         * 公司规模
         */
    private String companySize;
        /**
         * 公司地址
         */
    private String companyAddress;
        /**
         * 公司logo
         */
    private String companyLogo;
        /**
         * 门牌号
         */
    private String houseNumber;
        /**
         * 工作地点
         */
    private String workPlace;
        /**
         * 二级域名
         */
    private String subDomain;
        /**
         * 登录背景图片
         */
    private String loginImg;
}

