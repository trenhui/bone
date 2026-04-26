package com.bone.tpa.intelligent.adjustment.service;

import com.bone.platform.alert.AlertMessage;
import com.bone.platform.alert.AlertService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service
@Slf4j
public class CompensationService {
    private   AlertService alertService;


    /**
     * 处理额度补偿操作，确保在额度冻结失败时进行补偿。
     *
     * @param idCardNo 用户身份证号
     * @param policyNo 保单号
     * @param claimNo  赔案号
     * @param amount   理赔金额
     */
    public void handleQuotaCompensation(String idCardNo, String policyNo, String claimNo, BigDecimal amount) {
        try {
            if (!isPolicyValid(policyNo)) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "保单无效: " + policyNo);
            }

            // 记录补偿操作的详细日志
            logCompensationDetails(idCardNo, policyNo, amount, claimNo, "开始执行额度补偿");

            // 执行补偿逻辑，根据账户类型选择不同的补偿方式
            if (isPersonalAccount(idCardNo)) {
                compensatePersonalAccount(idCardNo, amount, idCardNo, claimNo);
            } else if (isPublicAccount(idCardNo)) {
                compensatePublicAccount(policyNo, amount, claimNo);
            } else {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "未知账户类型，无法执行补偿操作");
            }

            // 补偿成功，记录成功日志
            logCompensationDetails(idCardNo, policyNo, amount, claimNo, "额度补偿成功");

        } catch (Exception e) {
            // 处理补偿失败的情况
            log.error("补偿操作失败，赔案号: " + claimNo + ", 错误信息: " + e.getMessage());

            // 记录失败的补偿日志
            logCompensationDetails(idCardNo, policyNo, amount, claimNo, "额度补偿失败，错误: " + e.getMessage());

            // 发送警报
            AlertMessage alertMessage =  AlertMessage.builder()
                    .title("补偿操作失败，赔案号: " + claimNo + ", 错误信息: " + e.getMessage())
                    .businessId(claimNo)
                    .content("补偿金额: " + amount )
                    .build();

            alertService.sendAlert(alertMessage);
        }
    }

    /**
     * 校验账户是否有效
     *
     * @param account 账户
     * @return 是否有效
     */
    private boolean isAccountValid(String account) {
        // 账户校验逻辑，假设账户由字母和数字组成
        return account != null && account.matches("^[a-zA-Z0-9]{5,}$");
    }

    /**
     * 校验保单是否有效
     *
     * @param policyNo 保单号
     * @return 是否有效
     */
    private boolean isPolicyValid(String policyNo) {
        // 保单校验逻辑，假设有效保单号是10位
        return policyNo != null && policyNo.length() == 10;
    }

    /**
     * 判断是否为个人账户
     *
     * @param account 账户
     * @return 是否是个人账户
     */
    private boolean isPersonalAccount(String account) {
        return account.startsWith("P");  // 假设个人账户以 "P" 开头
    }

    /**
     * 判断是否为公共账户
     *
     * @param account 账户
     * @return 是否是公共账户
     */
    private boolean isPublicAccount(String account) {
        return account.startsWith("C");  // 假设公共账户以 "C" 开头
    }

    /**
     * 对个人账户进行补偿
     *
     * @param account 账户
     * @param amount  补偿金额
     * @param idCard  用户身份证号
     * @param claimNo 赔案号
     */
    private void compensatePersonalAccount(String account, BigDecimal amount, String idCard, String claimNo) {
        // 个人账户的补偿逻辑（如冻结额度、调整账户余额等）
        System.out.println("对个人账户进行补偿，账户: " + account + ", 金额: " + amount + ", 身份证号: " + idCard + ", 赔案号: " + claimNo);
        // TODO: 执行补偿逻辑
    }

    /**
     * 对公共账户进行补偿
     *
     * @param policyNo 保单号
     * @param amount   补偿金额
     * @param claimNo  赔案号
     */
    private void compensatePublicAccount(String policyNo, BigDecimal amount, String claimNo) {
        // 公共账户的补偿逻辑（如通知相关部门、调整公共额度等）
        log.info("对公共账户进行补偿，保单号: " + policyNo + ", 金额: " + amount + ", 赔案号: " + claimNo);
        // TODO: 执行补偿逻辑
    }

    /**
     * 记录补偿操作的详细日志
     *
     * @param idCard  账户
     * @param policyNo 保单号
     * @param amount   补偿金额
     * @param claimNo  赔案号
     * @param message  补偿操作的状态消息
     */
    private void logCompensationDetails(String idCard, String policyNo, BigDecimal amount, String claimNo, String message) {
        String logMessage = String.format(
                "补偿操作：账户: %s, 保单号: %s, 补偿金额: %s, 赔案号: %s, 状态: %s, 时间: %s",
                idCard, policyNo, amount, claimNo, message, new Date()
        );
        // 使用日志框架记录补偿操作的详细信息
        log.info(logMessage); // 使用实际的日志框架
    }
}
