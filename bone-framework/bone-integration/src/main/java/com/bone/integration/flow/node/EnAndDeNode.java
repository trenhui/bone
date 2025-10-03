package com.bone.integration.flow.node;

import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EnAndDeNode extends GraphNode {
    private String encrypt_type;//加密类型
    private String decrypt_type;//解密类型
    private String en_decrypt;//加解密
    private String public_key;//公钥
    private String private_key;//私钥
    private String countersign;//是否加签
    private String encrypt_code;//加密字段
    private String decrypt_code;//解密字段
    private String sign_code;//加签字段
    private String secret;//密钥
    private String checkSign;//是否验签
    private String checkSignCode;//验签字段


    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
