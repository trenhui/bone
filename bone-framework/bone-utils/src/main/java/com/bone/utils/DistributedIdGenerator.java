package com.bone.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author renhui.trh
 */
public class DistributedIdGenerator {

    static SnowFlakeDistributedIdGenerator snowFlakeDistributedIdGenerator= new SnowFlakeDistributedIdGenerator();

    private static final AtomicLong sequence = new AtomicLong(0);

    public static String generateUUID() {
        return UUID.randomUUID().toString().toLowerCase();
    }

    public static String generateSnowFlakeID() {
        return snowFlakeDistributedIdGenerator.generate();
    }

    public static Long generateLongID() {
         return snowFlakeDistributedIdGenerator.nextId();
    }

    public static String generateSequenceNo(String prefix, Boolean needDate, Integer length) {
        long uniqueId = sequence.incrementAndGet();
        String uniqueIdStr = String.format("%0" + length + "d", uniqueId);

        if (needDate) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            String dateStr = dateFormat.format(new Date());

            return prefix + dateStr + uniqueIdStr;
        }

        return prefix + uniqueIdStr;
    }
}

