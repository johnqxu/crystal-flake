package com.github.johnqxu.crystalflake;


import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 参考snowflake与sonyflake，按照32ms周期生成全局唯一ID
 * sony flake plus会按照时间有序生成，数据结构如下：<br/>
 *
 * <pre>
 * 0-000000000000000000000000000000000000-00000000-00-0000000000000-0000
 * </pre>
 * <p>
 * 第64位为保留位
 * 第28~63为时间戳位，32ms为单位保存时间信息，能够保存69.7年
 * 第20~27为序号，每32ms一个节点最大产生256个id，1秒产生8000个id
 * 第18~19为时间回拨标识位，发生时钟回拨时加1，最多支持连续三次时钟回拨
 * 第5~17为机器节点，一个dc支持8192台机器，共计131072个计算节点
 * 第1~4为datacenter标识，共计支持16个虚拟dc
 *
 * @author 徐青
 */
@Slf4j
public class FlakeGenerator {

    /**
     * 开始时间为2005年1月1日0时0分0秒
     **/
    public final static long EPOCH = 1104508800000L;

    /**
     * 生产周期位数
     */
    public final static long TIME_SEGMENT_BITS = 36;

    /**
     * 最大生产周期
     */
    public final static long MAX_TIME_SEGMENT = ~(-1L << TIME_SEGMENT_BITS);

    /**
     * 机器节点位数13位，总计容纳2^13的节点数，共计8192
     */
    public final static long WORKER_ID_BITS = 13L;

    /**
     * 单个data center中最大节点数，8192
     */
    public final static long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 数据中心位数4位，支持16个数据中心
     */
    public final static long DATA_CENTER_BITS = 4L;

    /**
     * 数据中心ID最大值
     */
    public final static long MAX_DATA_CENTER_ID = ~(-1 << DATA_CENTER_BITS);


    /**
     * 时钟回拨标识位，两位支持连续3次时钟回拨
     */
    public final static long CLOCK_BACK_BITS = 2L;

    /**
     * 最大时钟连续回拨次数
     */
    public final static long MAX_CLOCK_BACK_TIMES = ~(-1L << CLOCK_BACK_BITS);


    /**
     * 单位序号位数，8位支持2^8，共计128个序号，每8ms产生128个序号
     */
    public final static long SEQ_BITS = 8L;

    /**
     * 最大单位序号，超过需要等待下一序号生产周期
     */
    public final static long MAX_SEQ = ~(-1L << SEQ_BITS);

    /**
     * 时间戳精度5bit，右移5bit为了每32ms作为一个序号生产周期
     */
    public final static int TIME_SEGMENT_SHIFT_BITS = 5;

    /**
     * worker左移位数
     */
    public final static long WORKER_SHIFT = DATA_CENTER_BITS;

    /**
     * 时间回拨左移位数
     */
    public final static long CLOCK_BACK_SHIFT = WORKER_SHIFT + WORKER_ID_BITS;

    /**
     * 能够忍耐的最大时钟回拨周期，回拨时间超过阈值，ID生成抛出异常
     */
    public final static long MAX_CLOCK_BACK_SEGMENTS = 8;

    /**
     * 流水号左移位数
     */
    public final static long SEQ_SHIFT = CLOCK_BACK_SHIFT + CLOCK_BACK_BITS;

    /**
     * 时间戳左移位数
     */
    public final static long TIME_SEGMENT_SHIFT = SEQ_SHIFT + SEQ_BITS;

    private final long workerId;
    private final long dataCenterId;
    private long seq = 0L;
    private long clockBack = 0L;
    private long lastTimeSegment = -1L;
    private long clockBackSegment = 0L;
    private long nextMilliSecond;

    public FlakeGenerator(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new FlakeGeneratorException(String.format("当前的workerId[%d]超出范围[%d,%d]", workerId, 0, MAX_WORKER_ID));
        }
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new FlakeGeneratorException(String.format("当前的dataCenterId[%d]超出范围[%d,%d]", dataCenterId, 0, MAX_DATA_CENTER_ID));
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 生成全局ID
     *
     * @return 全局ID
     */
    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        long currentTimeSegment = (now - EPOCH) >> TIME_SEGMENT_SHIFT_BITS;

        if (currentTimeSegment < 0) {
            throw new FlakeGeneratorException("当前系统时间不能早于" + new Date(EPOCH));
        }
        if (currentTimeSegment > MAX_TIME_SEGMENT) {
            throw new FlakeGeneratorException("序号生成周期超出最大值");
        }

        if (currentTimeSegment < this.lastTimeSegment) {
            if (this.lastTimeSegment - currentTimeSegment >= MAX_CLOCK_BACK_SEGMENTS) {
                throw new FlakeGeneratorException("时钟回拨超出最大忍耐值");
            }
            if (currentTimeSegment > clockBackSegment) {
                clockBackSegment = lastTimeSegment;
            }

            // 时钟回拨保护
            this.clockBack = (this.clockBack + 1) & MAX_CLOCK_BACK_TIMES;
            // 超出最大时钟回拨保护次数
            if (this.clockBack == 0L) {
                throw new FlakeGeneratorException("时钟回拨超出最大次数");
            }
        } else {
            // 时钟未回拨
            if (currentTimeSegment > clockBackSegment + MAX_CLOCK_BACK_SEGMENTS) {
                clockBackSegment = 0L;
                this.clockBack = 0L;
            }
        }
        if (currentTimeSegment == lastTimeSegment) {
            this.seq = (this.seq + 1L) & MAX_SEQ;
            if (this.seq == 0L) {
                //当前序号生成周期内，序号溢出
                currentTimeSegment = nextTimeSegment();
            }
        } else {
            // 开始下一生产周期
            // 序号复位
            this.seq = 0L;
            // 计算下周期开始的时间戳，用于在序号生产周期序号溢出后等待下一周期判断
            setNextMilliSecond(now);
        }
        this.lastTimeSegment = currentTimeSegment;
        return currentTimeSegment << TIME_SEGMENT_SHIFT | seq << SEQ_SHIFT | clockBack << CLOCK_BACK_SHIFT | workerId << WORKER_SHIFT | dataCenterId;
    }

    // 设置下一周期的起始毫秒数
    private void setNextMilliSecond(long now) {
        this.nextMilliSecond = ((now >> TIME_SEGMENT_SHIFT_BITS) + 1) << TIME_SEGMENT_SHIFT_BITS;
    }

    /**
     * 如果一个seq生成周期内，sequence溢出，则需要等待下一seq生成周期，并返回下一周期时间戳segment
     *
     * @return 下一周期时间戳segment
     */
    private long nextTimeSegment() {
        long timestamp = 0L;
        while (timestamp < this.nextMilliSecond) {
            timestamp = System.currentTimeMillis();
        }
        setNextMilliSecond(timestamp);
        return (timestamp - EPOCH) >> TIME_SEGMENT_SHIFT_BITS;
    }
}
