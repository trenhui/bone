package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 单据附件
 *
 * @author 梅山源码
 */
@Table("doc_attachment")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment extends TenantAbstractEntity<Long> {
        /**
         * 单据：费用单类型、付款单，对应到哪张表里找数据
         */
    private String docType;
        /**
         * 单据ID
         */
    private Long docId;
        /**
         * 对应的文件信息
         */
    private String fileCode;
        /**
         * 文件名称，冗余存储
         */
    private String fileName;
        /**
         * 文件地址
         */
    private String fileAddress;
}

