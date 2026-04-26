package com.bone.tpa.intelligent.adjustment.util;

import cn.hutool.core.date.DateUtil;
import com.bone.tpa.intelligent.adjustment.enums.TimeUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdjustUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    final private String versionDateFormate = "yyyyMMdd";


    /**
     * 根据redis产生中心化的id
     * @param key
     * @return
     */
    public Long getIdIncreaseByRedis(String key){
        Long id = stringRedisTemplate.opsForValue().increment("icr:" + key);
        return  id;
    }

    public Long getIdIncreaseWithDateByRedis(String key){
        String prefix = DateUtil.format(new Date(), versionDateFormate);
        String redisKey = "icr:" + key + ":"+prefix;
        Long id = stringRedisTemplate.opsForValue().increment(redisKey);
        return Long.valueOf(prefix+String.format("%02d",id));
    }

    /**
     * 计算年龄
     * 周岁
     */
    public static Integer calculateAge(String identityCode) {
        if (identityCode == null) {
            return 0;
        }

        // 提取出生日期 (第7到14位)
        String birthDateStr = identityCode.substring(6, 14);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate birthDate = LocalDate.parse(birthDateStr, formatter);

        // 计算年龄
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * 计算日期间隔
     * 可以精细到年月日
     */
    public static long calculateDateDifference(Date date1, Date date2, TimeUnit unit) {
        // 转换为 LocalDate
        LocalDate localDate1 = new java.sql.Date(date1.getTime()).toLocalDate();
        LocalDate localDate2 = new java.sql.Date(date2.getTime()).toLocalDate();

        return switch (unit) {
            case DAYS -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(localDate1, localDate2));
            case MONTHS -> Math.abs(Period.between(localDate1, localDate2).toTotalMonths());
            case YEARS -> Math.abs(Period.between(localDate1, localDate2).getYears());
        };
    }

    /**
     * 检查ListA是否含有ListB中的元素/外的元素
     *
     * 默认是黑名单
     */
    public static boolean checkElementSection(List<?> checkList, List<?> list, boolean whiteList) {
        if (whiteList) {
            for (Object element : list) {
                if (checkList.contains(element)) {
                    return true;
                }
            }
        } else {
            for (Object element : checkList) {
                if (!list.contains(element)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 多个数字比较最小值
     *
     * @return
     */
    public static BigDecimal findMin(BigDecimal... values) {
        return Arrays.stream(values)
                .min(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("至少需要一个参数"));
    }



    // Map 转 JSON 字符串
    public static String mapToString(Map<String, BigDecimal> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("Map 转换 JSON 失败: {}", map);
            return null;
        }
    }


    // JSON 字符串转 Map
    public static Map<String, BigDecimal> stringToMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, BigDecimal.class));
        } catch (Exception e) {
            log.error("JSON 解析 Map 失败: {}", json);
            return new HashMap<>();
        }
    }

    public static Map<String, BigDecimal> aggregateMaps(List<Map<String, BigDecimal>> maps) {
        return maps.stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Map.Entry::getValue,
                                BigDecimal::add
                        )
                ));
    }


    /**
     * 合并多个JSON Map字符串为单个Map
     *
     * @param jsonList JSON字符串列表
     * @return 合并后的Map（相同Key的值累加）
     */
    public static Map<String, BigDecimal> mergeJsonMaps(List<String> jsonList) {
        Map<String, BigDecimal> resultMap = new HashMap<>();

        if (jsonList == null || jsonList.isEmpty()) {
            return resultMap;
        }

        for (String json : jsonList) {
            if (json == null || json.trim().isEmpty()) {
                continue; // 跳过空字符串
            }

            // 解析JSON字符串为Map
            Map<String, BigDecimal> currentMap = AdjustUtil.stringToMap(json);

            // 合并到结果Map（值累加）
            currentMap.forEach((key, value) -> {
                if (value == null) {
                    return; // 跳过null值
                }
                resultMap.merge(key, value, BigDecimal::add);
            });
        }

        return resultMap;
    }




    // 辅助方法：List<String> 转字符串
    public String listToString(List<String> list) {
        return String.join(";\n", list);
    }

    // 辅助方法：字符串转 List<String>
    public List<String> getTags(String string) {
        return Arrays.asList(string.split(";\n"));
    }

}
