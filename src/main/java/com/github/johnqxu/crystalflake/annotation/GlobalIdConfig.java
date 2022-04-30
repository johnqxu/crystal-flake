package com.github.johnqxu.crystalflake.annotation;

import com.github.johnqxu.crystalflake.FlakeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author 徐青
 */
@Slf4j
public class GlobalIdConfig implements ApplicationContextAware {
    private long workerId = -1L;
    private long dataCenterId = -1L;

    /**
     * 从环境变量读取worker与dataCenter的配置
     */
    private void loadConfigFromEnv() {
        try {
            workerId = Long.parseLong(System.getenv(EnableGlobalId.ENV_WORKER_KEY));
            dataCenterId = Long.parseLong(System.getenv(EnableGlobalId.ENV_DATA_CENTER_KEY));
        } catch (NumberFormatException nfe) {
            workerId = -1L;
            dataCenterId = -1L;
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ConfigurableApplicationContext cac = (ConfigurableApplicationContext) applicationContext;
        loadConfigFromEnv();
        FlakeGenerator flakeGenerator = new FlakeGenerator(workerId, dataCenterId);
        SingletonBeanRegistry beanRegistry = cac.getBeanFactory();
        beanRegistry.registerSingleton("flakeGenerator",flakeGenerator);
    }
}
