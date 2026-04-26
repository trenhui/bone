package com.bone.tpa.push.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息实体类
 *
 * @author wangguangwu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MQMessageDTO {

    /**
     * 赔案号
     */
    private String claimCode;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 唯一值
     */
    private String operatorCode;

}
