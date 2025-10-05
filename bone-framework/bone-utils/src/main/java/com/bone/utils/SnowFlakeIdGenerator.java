package com.bone.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

public class SnowFlakeIdGenerator {
    private static final long START_STMP = 1480166465631L;
    private static final long SEQUENCE_BIT = 12L;
    private static final long MACHINE_BIT = 5L;
    private static final long DATACENTER_BIT = 5L;
    private static final long MAX_DATACENTER_NUM = 31L;
    private static final long MAX_MACHINE_NUM = 31L;
    private static final long MAX_SEQUENCE = 4095L;
    private static final long MACHINE_LEFT = 12L;
    private static final long DATACENTER_LEFT = 17L;
    private static final long TIMESTMP_LEFT = 22L;
    private long datacenterId;
    private long machineId;
    private long sequence = 0L;
    private long lastStmp = -1L;
    private String dateTime;

    public SnowFlakeIdGenerator() {
    }

    public SnowFlakeIdGenerator(long datacenterId, long machineId) {
        if (datacenterId <= 31L && datacenterId >= 0L) {
            if (machineId <= 31L && machineId >= 0L) {
                this.datacenterId = datacenterId;
                this.machineId = machineId;
            } else {
                throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
            }
        } else {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
    }

    public SnowFlakeIdGenerator(long datacenterId, long machineId, long sequence, long lastStmp) {
        this.datacenterId = datacenterId;
        this.machineId = machineId;
        this.sequence = sequence;
        this.lastStmp = lastStmp;
        //DateTime datetime = new DateTime(fromatTime(lastStmp), ISOChronology.getInstanceUTC());

        // 将时间戳转换为Instant对象
        Instant instant = Instant.ofEpochMilli(lastStmp);

        // 将Instant对象转换为LocalDateTime对象
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        this.dateTime = dateTime.toString();
    }

    public synchronized long nextId() {
        long currStmp = this.getNewstmp();
        if (currStmp < this.lastStmp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id");
        } else {
            if (currStmp == this.lastStmp) {
                this.sequence = this.sequence + 1L & 4095L;
                if (this.sequence == 0L) {
                    currStmp = this.getNextMill();
                }
            } else {
                this.sequence = 0L;
            }

            this.lastStmp = currStmp;
            return currStmp - 1480166465631L << 22 | this.datacenterId << 17 | this.machineId << 12 | this.sequence;
        }
    }

    private long getNextMill() {
        long mill;
        for (mill = this.getNewstmp(); mill <= this.lastStmp; mill = this.getNewstmp()) {
        }

        return mill;
    }

    private long getNewstmp() {
        return System.currentTimeMillis();
    }

    public SnowFlakeIdGenerator parse(long id) {
        String sonwFlakeId = Long.toBinaryString(id);
        System.out.println(sonwFlakeId);
        int len = sonwFlakeId.length();
        int sequenceStart = (int) ((long) len < 12L ? 0L : (long) len - 12L);
        int workerStart = (int) ((long) len < 17L ? 0L : (long) len - 17L);
        int timeStart = (int) ((long) len < 22L ? 0L : (long) len - 22L);
        String sequence = sonwFlakeId.substring(sequenceStart, len);
        String workerId = sequenceStart == 0 ? "0" : sonwFlakeId.substring(workerStart, sequenceStart);
        String dataCenterId = workerStart == 0 ? "0" : sonwFlakeId.substring(timeStart, workerStart);
        String time = timeStart == 0 ? "0" : sonwFlakeId.substring(0, timeStart);
        int sequenceInt = Integer.valueOf(sequence, 2);
        int workerIdInt = Integer.valueOf(workerId, 2);
        int dataCenterIdInt = Integer.valueOf(dataCenterId, 2);
        long diffTime = Long.parseLong(time, 2);
        long timeLong = diffTime + 1480166465631L;
        SnowFlakeIdGenerator snowFlakeIdParse = new SnowFlakeIdGenerator((long) dataCenterIdInt, (long) workerIdInt, (long) sequenceInt, timeLong);
        return snowFlakeIdParse;
    }

    private static Date fromatTime(long date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date);
        return calendar.getTime();
    }

    public long getDatacenterId() {
        return this.datacenterId;
    }

    public void setDatacenterId(long datacenterId) {
        this.datacenterId = datacenterId;
    }

    public long getMachineId() {
        return this.machineId;
    }

    public void setMachineId(long machineId) {
        this.machineId = machineId;
    }

    public long getSequence() {
        return this.sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public long getLastStmp() {
        return this.lastStmp;
    }

    public void setLastStmp(long lastStmp) {
        this.lastStmp = lastStmp;
    }

    public String getDateTime() {
        return this.dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public String generate() {
        return String.valueOf(this.nextId());
    }
}
