package com.bone.tpa.core.util.kuaitong;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.exception.ServiceException;
import com.bone.tpa.api.enums.InvoicePaperTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
public class KTResponseHandle {

    //电子发票的typeList
    private static final List<String> eInvoiceTypeList = Arrays.asList("10108", "10506", "30100", "30101", "30102", "30103", "30104", "30105", "30106", "10106", "30109", "30121", "30122", "10102", "10107");

    //纸制发票的typeList
    private static final List<String> pInvoiceTypeList = Arrays.asList("10105", "10104", "10101", "10100", "30108", "30120", "10900", "10103");

    /**
     * 根据快瞳返回结果的type字段判断是电子票还是纸质票
     */
    public static InvoicePaperTypeEnum getInvoiceType(String type) {
        if (eInvoiceTypeList.contains(type)) {
            return InvoicePaperTypeEnum.ELECTRONIC;
        } else if (pInvoiceTypeList.contains(type)) {
            return InvoicePaperTypeEnum.PAPER;
        } else {
            return null;
        }
    }

    /**
     * 获取快瞳token
     */
    public static String getToken(String re) {
        if (!StringUtils.hasText(re)) {
            return null;
        }

        try {
            JSONObject jsonObject = JSON.parseObject(re);
            JSONObject data = jsonObject.getJSONObject("data");
            if (data != null) {
                return data.getString("access_token");
            }
            return null;
        } catch (Exception e) {
            log.info("解析快瞳token发生异常,参数:" + re, e);
            return null;
        }
    }

    /**
     * 通过快瞳返回结果判断token是否有效
     */
    public static boolean tokenInvalid(String response) {
        if (!StringUtils.hasText(response)) {
            throw new ServiceException(500, "快瞳响应内容不能为空");
        }

        JSONObject jsonObject = JSON.parseObject(response);
        String status = jsonObject.getString("status");
        String message = jsonObject.getString("message");
        return "401".equals(status) && StringUtils.hasText(message) && message.contains("token");
    }

    /**
     * 判断快瞳是否成功处理
     */
    public static boolean successCall(String re) {
        if (!StringUtils.hasText(re)) {
            return false;
        }
        try {
            JSONObject jsonObj = JSON.parseObject(re);
            String status = jsonObj.getString("status");
            String code = jsonObj.getString("code");
            return ("200".equals(status) && "10000".equals(code));
        } catch (Exception e) {
            log.info("判断快瞳是否成功处理发生异常,参数:" + re, e);
            return false;
        }
    }
}
