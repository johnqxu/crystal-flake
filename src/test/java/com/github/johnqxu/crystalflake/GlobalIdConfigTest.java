package com.github.johnqxu.crystalflake;

import com.github.johnqxu.crystalflake.annotation.EnableGlobalId;
import com.github.johnqxu.crystalflake.annotation.GlobalIdConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GlobalIdConfig.class, FlakeGenerator.class})
public class GlobalIdConfigTest {

    @Test
    public void directlyAnnotatedWithEnableGlobalId() {
        PowerMockito.mockStatic(System.class);
        when(System.getenv(EnableGlobalId.ENV_WORKER_KEY)).thenReturn("1");
        when(System.getenv(EnableGlobalId.ENV_DATA_CENTER_KEY)).thenReturn("1");
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(GlobalIdConfig.class);
        ctx.refresh();
        Assert.assertNotNull(ctx.getBean("flakeGenerator"));

        when(System.currentTimeMillis()).thenReturn(FlakeGeneratorTest.mockSeqStartTimestamp);
        FlakeGenerator flakeGenerator = (FlakeGenerator) ctx.getBean("flakeGenerator");
        long id = flakeGenerator.nextId();
        Assert.assertEquals(FlakeGeneratorTest.getWorkerId(id), 1);
        Assert.assertEquals(FlakeGeneratorTest.getDataCenterId(id), 1);
        Assert.assertEquals(FlakeGeneratorTest.getClockBack(id), 0);
        Assert.assertEquals(FlakeGeneratorTest.getSeq(id), 0);
        Assert.assertEquals(FlakeGeneratorTest.getSegment(id), (FlakeGeneratorTest.mockSeqStartTimestamp - FlakeGenerator.EPOCH) >> FlakeGenerator.TIME_SEGMENT_SHIFT_BITS);

        when(System.getenv(EnableGlobalId.ENV_WORKER_KEY)).thenReturn(null);
        when(System.getenv(EnableGlobalId.ENV_DATA_CENTER_KEY)).thenReturn(null);
        Exception exception = Assert.assertThrows(BeanCreationException.class, () -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(GlobalIdConfig.class);
            context.refresh();
        });
        Assert.assertNotNull(exception);

        when(System.getenv(EnableGlobalId.ENV_WORKER_KEY)).thenReturn("1");
        when(System.getenv(EnableGlobalId.ENV_DATA_CENTER_KEY)).thenReturn("abc");
        exception = Assert.assertThrows(BeanCreationException.class, () -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(GlobalIdConfig.class);
            context.refresh();
        });
        Assert.assertNotNull(exception);

        when(System.getenv(EnableGlobalId.ENV_WORKER_KEY)).thenReturn("abc");
        when(System.getenv(EnableGlobalId.ENV_DATA_CENTER_KEY)).thenReturn("1");
        exception = Assert.assertThrows(BeanCreationException.class, () -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(GlobalIdConfig.class);
            context.refresh();
        });
        Assert.assertNotNull(exception);
    }
}
