package com.bone.lowcode.infra.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.Date;

/**
* 租户DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "租户DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TenantDTO extends TenantAbstractDTO<Long> {

        /**
         * 名称
         */
         @Schema(name = "名称")
        private String name;
        /**
         * 编码
         */
         @Schema(name = "编码")
        private String code;
        /**
         * 生效时间
         */
         @Schema(name = "生效时间")
        private Date startDate;
        /**
         * 结束时间
         */
         @Schema(name = "结束时间")
        private Date endDate;
        /**
         * 状态1正常0冻结
         */
         @Schema(name = "状态1正常0冻结")
        private Byte status;
        /**
         * 所属行业
         */
         @Schema(name = "所属行业")
        private String trade;
        /**
         * 公司规模
         */
         @Schema(name = "公司规模")
        private String companySize;
        /**
         * 公司地址
         */
         @Schema(name = "公司地址")
        private String companyAddress;
        /**
         * 公司logo
         */
         @Schema(name = "公司logo")
        private String companyLogo;
        /**
         * 门牌号
         */
         @Schema(name = "门牌号")
        private String houseNumber;
        /**
         * 工作地点
         */
         @Schema(name = "工作地点")
        private String workPlace;
        /**
         * 二级域名
         */
         @Schema(name = "二级域名")
        private String subDomain;
        /**
         * 登录背景图片
         */
         @Schema(name = "登录背景图片")
        private String loginImg;
}
