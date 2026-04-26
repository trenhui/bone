package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("bank_dict")
public class BankDict {
    @TableId
    private Long id ;

    private String bankCode;

    private String bankName;

}
