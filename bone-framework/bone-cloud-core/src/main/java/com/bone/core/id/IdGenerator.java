package com.bone.core.id;

import com.bone.core.id.SnowFlakeIdGenerator;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.UUID;

/**
 * @author renhui.trh
 */
public class IdGenerator {

    static  SnowFlakeIdGenerator snowFlakeIdGenerator= new SnowFlakeIdGenerator();

    public static String generateUUID() {
        return UUID.randomUUID().toString().toLowerCase();
    }

    public static String generateSnowFlakeID() {
        return snowFlakeIdGenerator.generate();
    }

    public static Long generateLongID() {
         return snowFlakeIdGenerator.nextId();
    }
}

