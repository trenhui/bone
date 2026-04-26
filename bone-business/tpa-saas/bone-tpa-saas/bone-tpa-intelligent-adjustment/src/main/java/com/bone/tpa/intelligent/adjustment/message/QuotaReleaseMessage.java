package com.bone.tpa.intelligent.adjustment.message;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QuotaReleaseMessage 用于表示额度释放的消息对象
 */
@Data
@NoArgsConstructor
public class QuotaReleaseMessage {

    /**
     * 唯一标识符（比如事务 ID），用于标识这次额度释放操作
     */
    private Long xid;

    /**
     * 其他可以扩展的属性，例如额度信息、保单号等
     * 可根据实际需求扩展
     */
    private String policyNo;
    private String releaseReason;

    // 构造函数，可以根据需要修改
    public QuotaReleaseMessage(Long xid) {
        this.xid = xid;
    }

    public QuotaReleaseMessage(Long xid, String policyNo, String releaseReason) {
        this.xid = xid;
        this.policyNo = policyNo;
        this.releaseReason = releaseReason;
    }

    // 你也可以根据需要添加更多的字段、验证或者方法
}
