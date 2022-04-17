package com.github.johnqxu.crystalflake;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.LongStream;

import static org.powermock.api.mockito.PowerMockito.when;

@Slf4j
@RunWith(PowerMockRunner.class)
@PrepareForTest(FlakeGenerator.class)
public class FlakeGeneratorTest {

    /**
     * 时间戳mock次数
     */
    public static int tsMockTimes = 0;

    /**
     * 支持生成ID的最小时间戳
     */
    private static long tsUpperBorder;

    /**
     * 支持生成ID的最大时间戳
     */
    private static long tsLowerBorder;

    /**
     * mock序号生成周期起始时间戳
     */
    public static long mockSeqStartTimestamp = 1648724946016L;

    /**
     * 每个生成周期能够成产的最大序号数量
     */
    private static final long maxSeqPerCycle = FlakeGenerator.MAX_SEQ + 1;

    /**
     * seq生成时钟周期
     */
    private static final long seqCycleMillis = ~(-1 << FlakeGenerator.TIME_SEGMENT_SHIFT_BITS) + 1;

    @Before
    public void beforeTest() {
        tsMockTimes = 0;
    }

    @BeforeClass
    public static void init() {
        tsLowerBorder = FlakeGenerator.EPOCH;
        tsUpperBorder = (FlakeGenerator.MAX_TIME_SEGMENT << FlakeGenerator.TIME_SEGMENT_SHIFT_BITS) + FlakeGenerator.EPOCH;
    }

    private static long bitCalc(long source, long bitStart, long bitEnd) {
        if (bitStart > bitEnd || bitEnd < 0 || bitStart > 64 || bitStart < 0) {
            throw new IllegalArgumentException("起止参数错误");
        }
        return (source >> (bitStart - 1)) & (~(-1L << (bitEnd - bitStart + 1)));
    }

    // 获取时钟回拨标识
    public static long getClockBack(long id) {
        return bitCalc(id, FlakeGenerator.CLOCK_BACK_SHIFT + 1, FlakeGenerator.CLOCK_BACK_SHIFT + FlakeGenerator.CLOCK_BACK_BITS);
    }

    // 获取时钟回拨标识
    public static long getSeq(long id) {
        return bitCalc(id, FlakeGenerator.SEQ_SHIFT + 1, FlakeGenerator.SEQ_SHIFT + FlakeGenerator.SEQ_BITS);
    }

    // 获取周期标识
    public static long getSegment(long id) {
        return bitCalc(id, FlakeGenerator.TIME_SEGMENT_SHIFT + 1, 63);
    }

    // 获取workerId
    public static long getWorkerId(long id) {
        return bitCalc(id, FlakeGenerator.WORKER_SHIFT + 1, FlakeGenerator.WORKER_SHIFT + FlakeGenerator.WORKER_ID_BITS);
    }

    // 获取dataCenterId
    public static long getDataCenterId(long id) {
        return bitCalc(id, 1, FlakeGenerator.DATA_CENTER_BITS);
    }

    private static long getMockCurrentMillis(long[] mockTs) {
        if (tsMockTimes < mockTs.length) {
            tsMockTimes++;
        }
        return mockTs[tsMockTimes - 1];
    }

    @Test
    @DisplayName("mock时间戳边界值，应该得到正确的结果")
    public void shouldGetCorrectIdWithTimeSeriesBorderValue() {
        PowerMockito.mockStatic(System.class);
        Set<Long> idSet = new HashSet<>();
        int i = 0;
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        long id;

        // mock非法边下界
        when(System.currentTimeMillis()).thenReturn(tsLowerBorder - 1);
        Exception exception = Assert.assertThrows(FlakeGeneratorException.class, flakeGenerator::nextId);
        Assert.assertTrue(exception.getMessage().contains("当前系统时间不能早于"));

        //mock合法边下界
        when(System.currentTimeMillis()).thenReturn(tsLowerBorder);
        id = flakeGenerator.nextId();
        idSet.add(id);
        Assert.assertEquals(++i, idSet.size());

        // mock任意有效时间
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        id = flakeGenerator.nextId();
        idSet.add(id);
        Assert.assertEquals(++i, idSet.size());

        // mock合法边上界
        when(System.currentTimeMillis()).thenReturn(tsUpperBorder);
        id = flakeGenerator.nextId();
        idSet.add(id);
        Assert.assertEquals(++i, idSet.size());

        // mock非法边上界
        when(System.currentTimeMillis()).thenReturn(tsUpperBorder + seqCycleMillis);
        exception = Assert.assertThrows(FlakeGeneratorException.class, flakeGenerator::nextId);
        Assert.assertTrue(exception.getMessage().contains("序号生成周期超出最大值"));

    }

    @Test
    @DisplayName("mock workerId边界值，应该得到正确的结果")
    public void shouldGetCorrectIdWhenMockWorkerId() {
        PowerMockito.mockStatic(System.class);
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        // workerId下界非法
        Exception exception = Assert.assertThrows(FlakeGeneratorException.class
                , () -> new FlakeGenerator(-1, 1).nextId()
        );
        Assert.assertTrue(exception.getMessage().contains("当前的workerId"));

        //workerId合法下界
        FlakeGenerator flakeMaker = new FlakeGenerator(1, 1);
        long id = flakeMaker.nextId();
        Assert.assertEquals(getWorkerId(id), 1);

        //workerId合法上界
        flakeMaker = new FlakeGenerator(FlakeGenerator.MAX_WORKER_ID, 1);
        id = flakeMaker.nextId();
        Assert.assertEquals(FlakeGenerator.MAX_WORKER_ID, getWorkerId(id));

        //workerId非法上界
        exception = Assert.assertThrows(FlakeGeneratorException.class
                , () -> new FlakeGenerator(FlakeGenerator.MAX_WORKER_ID + 1, 1).nextId()
        );
        Assert.assertTrue(exception.getMessage().contains("当前的workerId"));
    }

    @Test
    @DisplayName("mock dataCenterId边界值，应该得到正确的结果")
    public void shouldGetCorrectIdWhenMockDataCenterId() {
        PowerMockito.mockStatic(System.class);
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        // dataCenterId下界非法
        Exception exception = Assert.assertThrows(FlakeGeneratorException.class
                , () -> new FlakeGenerator(1, -1).nextId()
        );
        Assert.assertTrue(exception.getMessage().contains("当前的dataCenterId"));

        // dataCenterId合法下界
        FlakeGenerator flakeMaker = new FlakeGenerator(1, 1);
        long id = flakeMaker.nextId();
        Assert.assertEquals(1, getDataCenterId(id));

        // dataCenterId合法上界
        flakeMaker = new FlakeGenerator(1, FlakeGenerator.MAX_DATA_CENTER_ID);
        id = flakeMaker.nextId();
        Assert.assertEquals(FlakeGenerator.MAX_DATA_CENTER_ID, getDataCenterId(id));

        // dataCenterId非法上界
        exception = Assert.assertThrows(FlakeGeneratorException.class
                , () -> new FlakeGenerator(1, FlakeGenerator.MAX_DATA_CENTER_ID + 1).nextId()
        );
        Assert.assertTrue(exception.getMessage().contains("当前的dataCenterId"));
    }

    @Test
    @DisplayName("在一个序号生成周期内的一毫秒内，能够生成不重复的足够的id")
    public void shouldCreateCorrectIdInOneTimeCycleWithOneMillis() {
        Set<Long> idSet = new HashSet<>();
        PowerMockito.mockStatic(System.class);
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        FlakeGenerator flakeMaker = new FlakeGenerator(1, 1);
        for (int i = 0; i < maxSeqPerCycle; i++) {
            long id = flakeMaker.nextId();
            idSet.add(id);
        }
        Assert.assertEquals(idSet.size(), maxSeqPerCycle);
    }

    @Test
    @DisplayName("在一个序号生成周期内的多个毫秒内，能够生成不重复的足够的id")
    public void shouldCreateCorrectIdInOneTimeCycleWithDifferentMillis() {
        Set<Long> idSet = new HashSet<>();
        Set<Long> segmentSet = new HashSet<>();
        PowerMockito.mockStatic(System.class);

        long[] tsForOneCycle = LongStream.range(mockSeqStartTimestamp, mockSeqStartTimestamp + seqCycleMillis - 1).toArray();

        FlakeGenerator flakeMaker = new FlakeGenerator(1, 1);
        when(System.currentTimeMillis()).thenAnswer(x -> getMockCurrentMillis(tsForOneCycle));
        for (int i = 0; i < maxSeqPerCycle; i++) {
            long id = flakeMaker.nextId();
            idSet.add(id);
            segmentSet.add(getSegment(id));
        }
        Assert.assertEquals(idSet.size(), maxSeqPerCycle);
        Assert.assertEquals(segmentSet.size(), 1);
    }

    @Test
    @DisplayName("在一个seq生成周期内超出最大请求数后，会等待到下一周期")
    public void shouldGetCorrectIdInNextCycleWhenCurrentCycleOverFlow() {
        Set<Long> idSet = new HashSet<>();
        Set<Long> segmentSet = new HashSet<>();
        long[] ts = LongStream.range(1, maxSeqPerCycle * 6).map(x -> (long) (mockSeqStartTimestamp + seqCycleMillis * Math.floor((double) x / maxSeqPerCycle / 2))).toArray();
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        when(System.currentTimeMillis()).thenAnswer(x -> getMockCurrentMillis(ts));

        for (int i = 0; i < maxSeqPerCycle * 2; i++) {
            long id = flakeGenerator.nextId();
            idSet.add(id);
            segmentSet.add(getSegment(id));
        }
        Assert.assertEquals(idSet.size(), maxSeqPerCycle * 2);
        Assert.assertEquals(segmentSet.size(), 2);
    }

    @Test
    @DisplayName("测试时钟回拨保护")
    public void shouldEnableCorrectClockBackTimes() {
        Set<Long> idSet = new HashSet<>();
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        for (int i = 0; i < FlakeGenerator.MAX_CLOCK_BACK_TIMES + 1; i++) {
            when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - seqCycleMillis * i);
            long id = flakeGenerator.nextId();
            idSet.add(id);
            Assert.assertEquals(getClockBack(id), i);
        }
        Assert.assertEquals(idSet.size(), FlakeGenerator.MAX_CLOCK_BACK_TIMES + 1);
    }

    @Test
    @DisplayName("超出最大连续回拨次数后，抛出异常")
    public void shouldTrowExceptionWhenClockBackTimesOverflow() {
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        Exception exception = Assert.assertThrows(FlakeGeneratorException.class, () -> {
            int i;
            for (i = 0; i < FlakeGenerator.MAX_CLOCK_BACK_TIMES + 2; i++) {
                when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - seqCycleMillis * i);
                flakeGenerator.nextId();
            }
        });
        Assert.assertTrue(exception.getMessage().contains("时钟回拨超出最大次数"));
    }

    @Test
    @DisplayName("反复回拨-回正正常")
    public void shouldGetCorrectIdWhenClockBackTimesOverAndOver() {
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        Set<Long> idSet = new HashSet<>();
        long id;
        int idCounts = 0;

        for (int i = 0; i < maxSeqPerCycle; i++) {
            when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
            id = flakeGenerator.nextId();
            idSet.add(id);
            idCounts++;
            Assert.assertEquals(0, getClockBack(id));
        }

        for (int i = 0; i < FlakeGenerator.MAX_CLOCK_BACK_TIMES; i++) {
            //回拨
            long clockBackMillis = mockSeqStartTimestamp - seqCycleMillis * (i + 1);
            when(System.currentTimeMillis()).thenReturn(clockBackMillis);
            for (int k = 0; k < maxSeqPerCycle; k++) {
                id = flakeGenerator.nextId();
                idSet.add(id);
                idCounts++;
                Assert.assertEquals(i + 1, getClockBack(id));
            }

            //回正
            for (int j = 0; j < i + 1; j++) {
                when(System.currentTimeMillis()).thenReturn(clockBackMillis + (j + 1) * seqCycleMillis);
                for (int k = 0; k < maxSeqPerCycle; k++) {
                    id = flakeGenerator.nextId();
                    idSet.add(id);
                    idCounts++;
                    Assert.assertEquals(i + 1, getClockBack(id));
                }
            }
        }
        Assert.assertEquals(idCounts, idSet.size());
    }

    @Test
    @DisplayName("时钟达到恢复阈值后，生成的id中时钟回拨标识复位0")
    public void shouldResetClockBackWhenClockExceedsRecoveryThreshold() {
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        long id = flakeGenerator.nextId();
        Assert.assertEquals(0, FlakeGeneratorTest.getClockBack(id));
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - seqCycleMillis);
        id = flakeGenerator.nextId();
        Assert.assertEquals(1, FlakeGeneratorTest.getClockBack(id));
        // 回正
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        id = flakeGenerator.nextId();
        Assert.assertEquals(1, FlakeGeneratorTest.getClockBack(id));
        // 达到阈值
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp + seqCycleMillis * (FlakeGenerator.MAX_CLOCK_BACK_SEGMENTS + 1));
        id = flakeGenerator.nextId();
        Assert.assertEquals(0, FlakeGeneratorTest.getClockBack(id));
    }

    @Test
    @DisplayName("超出最大回拨忍耐值时，抛出异常")
    public void shouldThrowExceptionWhenClockBackTooLong() {
        PowerMockito.mockStatic(System.class);
        FlakeGenerator flakeGenerator = new FlakeGenerator(1, 1);
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp);
        long id = flakeGenerator.nextId();
        Assert.assertEquals(0, FlakeGeneratorTest.getClockBack(id));

        // 回拨
        Exception exception = Assert.assertThrows(FlakeGeneratorException.class, () -> {
            when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - FlakeGenerator.MAX_CLOCK_BACK_SEGMENTS * seqCycleMillis);
            flakeGenerator.nextId();
        });
        Assert.assertTrue(exception.getMessage().contains("时钟回拨超出最大忍耐值"));

        // 回拨
        when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - seqCycleMillis);
        id = flakeGenerator.nextId();
        Assert.assertEquals(1, FlakeGeneratorTest.getClockBack(id));

        // 超出最大忍耐值
        exception = Assert.assertThrows(FlakeGeneratorException.class, () -> {
            when(System.currentTimeMillis()).thenReturn(mockSeqStartTimestamp - (FlakeGenerator.MAX_CLOCK_BACK_SEGMENTS + 1) * seqCycleMillis);
            flakeGenerator.nextId();
        });
        Assert.assertTrue(exception.getMessage().contains("时钟回拨超出最大忍耐值"));
    }

}