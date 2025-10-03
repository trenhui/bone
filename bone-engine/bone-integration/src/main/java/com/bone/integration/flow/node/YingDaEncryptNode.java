package com.bone.integration.flow.node;

import com.bone.integration.enums.EnAndDeEnum;
import com.bone.integration.enums.EncodingTypeEnum;
import com.bone.integration.enums.SM4EncryptTypeEnum;
import com.bone.integration.flow.visitor.INodeVisitor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class YingDaEncryptNode extends GraphNode {
    /**
     * sm2密钥
     */
    private String sm2PrivateKey;
    /**
     * sm2公钥
     */
    private String sm2PublicKey;
    /**
     * 加密数据格式：HEX、Base64
     * @see EncodingTypeEnum
     */
    private String encodingType;
    /**
     * 加密类型：ECB、CBC
     * @see SM4EncryptTypeEnum
     */
    private String sm4EncryptType;

    /**
     * 加密还是解密
     * @see EnAndDeEnum
     */
    private String encryptMode;

    private String ak;

    private String sk;

    /**
     * 用于加签
     */
    private String path;


    @Override
    public void accept(INodeVisitor visitor) {
        visitor.visit(this); // 调用访问者方法
    }
}
