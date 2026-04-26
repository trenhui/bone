package com.bone.tpa.task.service.vo;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ImageOcrChangeResult {

    private   ClaimInvoice invoice;

    /**
     * 发票的报错提示
     */
    private Map<String,String> invoiceHint = new HashMap<>();

    /**
     * 费用信息
     */
    private List<InvoiceProjectItem> itemList = PkListUtil.newArrayList();

    /**
     * 和 itemList 的长度一样 ，按照顺序存储
     */
    private List<Map<String,String>> itemHintList = PkListUtil.newArrayList();
}
