package com.bone.lowcode.integration.processor;

import cn.hutool.core.date.DateUtil;
import com.bone.lowcode.integration.enums.EnAndDeEnum;
import com.bone.lowcode.integration.enums.EncodingTypeEnum;
import com.bone.lowcode.integration.enums.SM4EncryptTypeEnum;
import com.bone.lowcode.integration.flow.node.YingDaEncryptNode;
import com.bone.lowcode.integration.uitls.encrypt.YingDaEncryptUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;


@Slf4j
@Data
public class YingDaEncrypteProcessor implements Processor {
    private static final String SAFE_MODE = "safe-mode";
    private static final String ENCRYPT_KEY = "encrypt-key";

    private YingDaEncryptNode node;

    public YingDaEncrypteProcessor(YingDaEncryptNode node) {
        this.node = node;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String content = exchange.getIn().getBody(String.class);
        EnAndDeEnum encryptType = EnAndDeEnum.fromValue(node.getEncryptMode());
        if (encryptType == EnAndDeEnum.ENCRYPT) {
            encrypt(content, exchange);
        } else if (encryptType == EnAndDeEnum.DECRYPT) {
            decrypt(content, exchange);
        } else {
            throw new IllegalArgumentException("Unsupported encrypt mode: " + encryptType);
        }
        exchange.getIn().getHeaders().forEach((k, v) -> {
            log.info("Header: " + k + " -> " + v);
        });

    }

    private void encrypt(String content, Exchange exchange) {
        String sm4PrivateKey = exchange.getIn().getHeader("_randomKey_", String.class);
        if (!StringUtils.hasText(sm4PrivateKey)) {
            sm4PrivateKey = YingDaEncryptUtils.createSM4PrivateKey();
        } else {
            // 密钥不能放在header往下进行传递，获取之后进行清除
//            exchange.getIn().removeHeader("_randomKey_");
        }

        String cipherContentString = YingDaEncryptUtils.sm4Encrypt(content, sm4PrivateKey, SM4EncryptTypeEnum.valueOf(node.getSm4EncryptType()),
                EncodingTypeEnum.valueOf(node.getEncodingType()));
        String cipherPrivateKeyString = YingDaEncryptUtils.sm2Encrypt(sm4PrivateKey, node.getSm2PublicKey());
        exchange.getIn().setBody(cipherContentString);

        String safeMode = "1";
        String ak = node.getAk();
        String requestTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        String nonce = UUID.randomUUID().toString();
        String validTime = "";
        String sk = node.getSk();
        exchange.getIn().setHeader("Content-type", "application/json");
        exchange.getIn().setHeader("clientid", ak);
        exchange.getIn().setHeader("valid-time", validTime);
        exchange.getIn().setHeader("request-time", requestTime);
        exchange.getIn().setHeader("nonce", nonce);
        exchange.getIn().setHeader("valid-time", validTime);
        exchange.getIn().setHeader(SAFE_MODE, safeMode);
        exchange.getIn().setHeader(ENCRYPT_KEY, cipherPrivateKeyString);



        String signatureTxt = ak + ";" +
                requestTime + ";" +
                nonce + ";" +
                validTime + ";" +
                safeMode + ";" +
                cipherPrivateKeyString + ";" +
                cipherContentString +
                sk;

        String signature = YingDaEncryptUtils.calculateSignature(signatureTxt);

        String authTxt = "POST" + ";" + node.getPath() + ";" + ak + ";" + requestTime + ";" + nonce + ";" + sk;
        String authorization = YingDaEncryptUtils.sm3Encrypt(authTxt);
        

        exchange.getIn().setHeader("signature", signature);
        exchange.getIn().setHeader("auth-mode", "AK-SK");
        exchange.getIn().setHeader("authorization", "AK-SK-V1/" + authorization);
    }

    private void decrypt(String content, Exchange exchange) {
        String cipherPrivateKeyString = exchange.getIn().getHeader(ENCRYPT_KEY, String.class);
        Assert.hasText(cipherPrivateKeyString, "SM4解密: header中encrypt-key不能为空");
        String sm4PrivateKey = YingDaEncryptUtils.sm2Decrypt(cipherPrivateKeyString, node.getSm2PrivateKey());
        String plainString = YingDaEncryptUtils.sm4Decrypt(content, sm4PrivateKey, SM4EncryptTypeEnum.valueOf(node.getSm4EncryptType()),
                EncodingTypeEnum.valueOf(node.getEncodingType()));
        exchange.getIn().setBody(plainString);
    }
}