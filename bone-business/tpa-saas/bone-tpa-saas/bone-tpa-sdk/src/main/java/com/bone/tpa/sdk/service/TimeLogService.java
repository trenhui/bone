package com.bone.tpa.sdk.service;


import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTimeLog;
import com.bone.tpa.sdk.dao.ClaimTimeLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 时效相关服务
 */
@Slf4j
@Service
public class TimeLogService {

    @Autowired
    private ClaimTimeLogRepository claimTimeLogRepository;

    // 常量定义
    private static final BigDecimal MILLISECONDS_PER_HOUR =
            BigDecimal.valueOf(TimeUnit.HOURS.toMillis(1));
    private static final int DEFAULT_SCALE = 2;

    // 定义东八区时区
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");


    /**
     * 插入关键操作时间记录
     */
    public void addTimeLog(Claim claim, OperationTypeEnum operation, String operator) {
        ClaimTimeLog claimTimeLog = new ClaimTimeLog();

        claimTimeLog.setClaimId(String.valueOf(claim.getId()));
        claimTimeLog.setClaimStage(claim.getStage());
        claimTimeLog.setOperationType(operation.getCode());
        claimTimeLog.setOperationTime(new Date());
        claimTimeLog.setOperator(operator);

        claimTimeLogRepository.insert(claimTimeLog);
    }


    /**
     * 获取还未减去挂起时间的时效
     */
    public BigDecimal getLimitHourRaw(Claim claim) {
        Date startTime;
        Date finishTime = new Date();

        //开始时间按签收通过时间来
        if (claim.getSignPassTime() == null) {
            startTime = claim.getCreateTime();
        } else {
            startTime = claim.getSignPassTime();
        }

        //若未结束， 结束时间按newDate
        //若结束，结束时间依次取复核和审核
        if (claim.getStage().equals(ClaimStageEnum.FINISH.getCode())) {
            if (claim.getReviewingPassTime() == null) {
                if (claim.getAuditingPassTime() == null) {
                    log.error("赔案{}未记录审核完成时间，使用最后更新时间代替!", claim.getClaimNo());
                    finishTime = claim.getUpdateTime();
                } else {
                    finishTime = claim.getAuditingPassTime();
                }
            } else {
                finishTime = claim.getReviewingPassTime();
            }
        }

        return calculateWorkingHours(startTime, finishTime);
    }


    /**
     * 获取某两个操作之间的时间差
     */
    public BigDecimal timePeriodCalculator(Claim claim, OperationTypeEnum start, OperationTypeEnum end) {
        List<String> typeList = new ArrayList<>();

        typeList.add(start.getCode());
        typeList.add(end.getCode());

        //获取所有这种类型的操作日志
        List<ClaimTimeLog> timeLogList = getLogListByOperationType(claim.getId(), typeList);

        //然后根据起始和结束做拆分
        int startIndex = -1;
        //先找到第一个起始
        for (ClaimTimeLog timeLog : timeLogList) {
            if (timeLog.getOperationType().equals(start.getCode())) {
                startIndex = timeLogList.indexOf(timeLog);
                break;
            }
        }

        //找不到就说明没有起始，返回0
        if (startIndex == -1) {
            return BigDecimal.ZERO;
        }

        int lastEndIndex = -1;
        BigDecimal totalPeriod = BigDecimal.ZERO;
        while (true) {
            int endIndex = startIndex;
            for (int i = startIndex + 1 ; i < timeLogList.size(); i++) {
                //这里是找到了对应的end
                if (timeLogList.get(i).getOperationType().equals(end.getCode())) {
                    //计算这个区间的时效
                    totalPeriod = totalPeriod.add(calculateWorkingHours(timeLogList.get(startIndex).getCreateTime(), timeLogList.get(i).getCreateTime()));
                    endIndex = i;
                    break;
                }
            }

            //如果这里保持不变说明没找到，加上后面所有的时间，本次就可以结束了
            if (endIndex == startIndex) {
                totalPeriod = totalPeriod.add(calculateWorkingHours(timeLogList.get(startIndex).getCreateTime(), new Date()));
                break;
            }

            //这里要寻找下一个start
            for (int i = endIndex + 1 ; i < timeLogList.size(); i++) {
                //这里找到了start
                if (timeLogList.get(i).getOperationType().equals(start.getCode())) {
                    //计算这个区间的时效
                    startIndex = i;
                    break;
                }
            }

            //如果这里start还是比end小，说明找不到了，可以结束了
            if (startIndex < endIndex) {
                break;
            }
        }

        return totalPeriod;
    }


    /**
     * 根据操作类型获取日志记录
     */
    private List<ClaimTimeLog> getLogListByOperationType(Long claimId, List<String> typeList) {
        Criteria<ClaimTimeLog> criteria= new Criteria<>();
        criteria.eq(ClaimTimeLog::getClaimId, claimId);
        criteria.in(ClaimTimeLog::getOperationType, typeList);

        List<ClaimTimeLog> claimTimeLogList = claimTimeLogRepository.findByCriteria(criteria);

        return claimTimeLogList;
    }

    /**
     * 计算两个 Date 之间的工作日总小时数
     */
    public static BigDecimal calculateWorkingHours(Date startDate, Date endDate) {
        BigDecimal totalHours = calculateTotalHours(startDate, endDate);
        BigDecimal nonWorkingHours = calculateNonWorkingHours(startDate, endDate);
        return totalHours.subtract(nonWorkingHours);
    }


    /**
     * 计算两个 Date 之间的总小时数
     */
    public static BigDecimal calculateTotalHours(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("日期参数不能为null");
        }

        long millisecondsDifference = endDate.getTime() - startDate.getTime();
        BigDecimal milliseconds = BigDecimal.valueOf(millisecondsDifference);

        return milliseconds.divide(MILLISECONDS_PER_HOUR, 10, RoundingMode.HALF_UP)
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
    }


    /**
     * 计算两个 Date 之间的非工作日总小时数（周六和周日）
     */
    public static BigDecimal calculateNonWorkingHours(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("日期参数不能为null");
        }

        // 确保开始日期在结束日期之前
        if (startDate.after(endDate)) {
            return calculateNonWorkingHours(endDate, startDate);
        }

        // 转换为 LocalDateTime
        LocalDateTime start = convertToLocalDateTime(startDate);
        LocalDateTime end = convertToLocalDateTime(endDate);

        BigDecimal totalNonWorkingHours = BigDecimal.ZERO;
        LocalDateTime current = start;

        // 遍历每一天
        while (current.isBefore(end)) {
            LocalDateTime dayStart = current.toLocalDate().atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            // 调整第一天和最后一天的边界
            if (dayStart.isBefore(start)) {
                dayStart = start;
            }
            if (dayEnd.isAfter(end)) {
                dayEnd = end;
            }

            // 如果是周末，计算这一天的小时数
            if (isWeekend(current.toLocalDate())) {
                Duration duration = Duration.between(dayStart, dayEnd);
                BigDecimal hours = BigDecimal.valueOf(duration.toMinutes())
                        .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP);
                totalNonWorkingHours = totalNonWorkingHours.add(hours);
            }

            // 移动到下一天
            current = dayEnd;
        }

        return totalNonWorkingHours.setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 判断日期是否是周末（周六或周日）
     */
    private static boolean isWeekend(java.time.LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    /**
     * 将 Date 转换为 LocalDateTime
     */
    private static LocalDateTime convertToLocalDateTime(Date date) {
        return date.toInstant().atZone(ZONE_ID).toLocalDateTime();
    }

}
