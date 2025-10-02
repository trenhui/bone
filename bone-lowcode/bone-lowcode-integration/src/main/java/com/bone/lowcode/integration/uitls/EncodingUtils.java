package com.bone.lowcode.integration.uitls;

import com.bone.lowcode.integration.enums.EncodingTypeEnum;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncodingUtils {

    public static String encode(byte[] content, EncodingTypeEnum encodingTypeEnum) {
        switch (encodingTypeEnum) {
            case TEXT -> {
                return new String(content, StandardCharsets.UTF_8);
            }
            case HEX -> {
                return  Hex.toHexString(content);
            }
            case BASE64 -> {
                return new String(Base64.getEncoder().encode(content), StandardCharsets.UTF_8);
            }
            default -> throw new IllegalArgumentException("Unsupported encodingTypeEnum: " + encodingTypeEnum);
        }
    }

    public static byte[] decode(String content, EncodingTypeEnum encodingTypeEnum) {
        switch (encodingTypeEnum) {
            case TEXT -> {
                return content.getBytes(StandardCharsets.UTF_8);
            }
            case HEX -> {
                return  Hex.decode(content);
            }
            case BASE64 -> {
                return Base64.getDecoder().decode(content);
            }
            default -> throw new IllegalArgumentException("Unsupported encodingTypeEnum: " + encodingTypeEnum);
        }
    }
}
