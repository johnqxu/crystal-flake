package com.github.johnqxu.crystalflake.annotation;

import com.github.johnqxu.crystalflake.FlakeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;

/**
 * @author 徐青
 */
@Slf4j
public class GlobalIdConfig implements ApplicationContextAware {
    private ApplicationContext ac;
    private long workerId = -1L;
    private long dataCenterId = -1L;

    @Bean("flakeGenerator")
    FlakeGenerator initGenerator() {
        loadConfigFromEnv();
        return new FlakeGenerator(workerId, dataCenterId);
    }

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
        this.ac = applicationContext;
    }
}
